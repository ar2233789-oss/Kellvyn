package com.kellvyn.assistant.workspace

import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.AssistantTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import org.json.JSONObject

sealed interface AppBuildResult {
    data class Success(val project: WorkspaceProject, val files: List<String>) : AppBuildResult
    data class Failure(val reason: String) : AppBuildResult
}

/**
 * Converts a user's application brief into a proposed source-code project.
 * The model's output is treated as untrusted text: it is parsed as JSON,
 * path/size-limited by ProjectWorkspaceStore and saved as data, never executed.
 */
class AppBuilderAgent(private val workspace: ProjectWorkspaceStore) {

    suspend fun createProject(engine: AssistantEngine, brief: String): AppBuildResult {
        val idea = brief.trim()
        if (idea.length < 12) {
            return AppBuildResult.Failure("Descreva o aplicativo com pelo menos 12 caracteres.")
        }
        if (idea.length > MAX_BRIEF_CHARS) {
            return AppBuildResult.Failure("A descrição é muito longa. Limite: $MAX_BRIEF_CHARS caracteres.")
        }

        val prompt = buildString {
            appendLine("TAREFA: criar um projeto de aplicativo Android nativo utilizável a partir do pedido abaixo.")
            appendLine("Produza somente um objeto JSON válido, sem markdown, sem bloco de código e sem texto antes/depois.")
            appendLine("Esquema obrigatório:")
            appendLine("""{"name":"Nome curto","description":"Resumo","files":[{"path":"README.md","content":"conteúdo completo"}]}""")
            appendLine("Requisitos técnicos:")
            appendLine("- Gere um aplicativo Android NATIVO em Java puro, com namespace e applicationId com.example.app. O compilador inicial do Kellvyn compila Java diretamente neste aparelho; não gere Kotlin nem Compose.")
            appendLine("- Use Android Gradle Plugin ${AndroidBuildVersions.ANDROID_GRADLE_PLUGIN}, compileSdk ${AndroidBuildVersions.COMPILE_SDK}, targetSdk ${AndroidBuildVersions.TARGET_SDK} e minSdk ${AndroidBuildVersions.MIN_SDK}. A fonte principal é app/src/main/java/com/example/app/MainActivity.java.")
            appendLine("- MainActivity deve estender android.app.Activity e chamar setContentView com uma interface útil criada por código usando android.widget (LinearLayout, TextView, Button, EditText, ScrollView etc.). Crie listeners usando classes anônimas.")
            appendLine("- Evite lambdas, method references, invokedynamic, Kotlin, Compose, bibliotecas externas, annotation processors, AndroidX, XML de layout, recursos R, imagens externas e NDK. Use Java compatível com o Android e APIs públicas do framework.")
            appendLine("- Não declare bibliotecas externas em app/build.gradle.kts. A configuração Gradle fica para exportação futura; a build local inicial compila fontes Java e não executa scripts Gradle.")
            appendLine("- Inclua settings.gradle.kts, build.gradle.kts, gradle.properties, app/build.gradle.kts, app/src/main/AndroidManifest.xml, app/src/main/java/com/example/app/MainActivity.java e README.md.")
            appendLine("- O manifesto declara .MainActivity, android:exported=\"true\" e intent-filter MAIN/LAUNCHER. Inclua permissões apenas quando realmente necessárias.")
            appendLine("- Crie uma aplicação funcional com controles, validação de entradas e ações reais, não apenas pseudocódigo ou placeholders.")
            appendLine("- Não invente tokens, chaves, APIs privadas, caminhos absolutos nem dependências inexistentes.")
            appendLine("- Não crie WebView. Não use compilação remota, GitHub Actions ou serviços externos de build.")
            appendLine("- Não inclua binários, gradlew, .git, .gradle ou qualquer caminho absoluto.")
            appendLine("- Cada item de files deve conter path e content como strings. Escape corretamente quebras de linha, aspas e barras para formar JSON válido.")
            appendLine("- Prefira no máximo 8 arquivos e aprox. 12.000 caracteres; nunca ultrapasse 12 arquivos e 18.000 caracteres.")
            appendLine("- Em um pedido grande, implemente uma primeira versão útil e documente as funções no README.")
            appendLine()
            appendLine("PEDIDO DO USUÁRIO:")
            appendLine(idea)
        }

        return try {
            when (val response = engine.reply(listOf(AssistantTurn(AssistantTurn.Role.USER, prompt)))) {
                is AssistantResult.Unavailable -> AppBuildResult.Failure(response.reason)
                is AssistantResult.Failure -> AppBuildResult.Failure(response.reason)
                is AssistantResult.Success -> {
                    val firstAttempt = parseAndSave(response.text, idea)
                    if (firstAttempt !is AppBuildResult.Failure || !isRecoverableGenerationFailure(firstAttempt.reason)) {
                        firstAttempt
                    } else {
                        val repairPrompt = buildString {
                            appendLine("A geração anterior não passou pela validação automática do construtor.")
                            appendLine("Erro detectado: ${firstAttempt.reason}")
                            appendLine("Gere uma versão corrigida completa do projeto Android para o pedido original abaixo.")
                            appendLine("Retorne SOMENTE JSON válido no esquema definido, sem markdown. Inclua todos os arquivos obrigatórios.")
                            appendLine("Não descreva o que faria; entregue o conteúdo de cada arquivo.")
                            appendLine()
                            appendLine("REGRAS TÉCNICAS E ESQUEMA:")
                            appendLine(prompt.substringBefore("PEDIDO DO USUÁRIO:"))
                            appendLine("PEDIDO ORIGINAL DO USUÁRIO:")
                            appendLine(idea)
                        }
                        when (val retry = engine.reply(listOf(AssistantTurn(AssistantTurn.Role.USER, repairPrompt)))) {
                            is AssistantResult.Unavailable -> AppBuildResult.Failure(
                                "A primeira geração falhou na validação (${firstAttempt.reason}) e o modelo ficou indisponível na tentativa de correção: ${retry.reason}"
                            )
                            is AssistantResult.Failure -> AppBuildResult.Failure(
                                "A primeira geração falhou na validação (${firstAttempt.reason}); a tentativa automática de correção também falhou: ${retry.reason}"
                            )
                            is AssistantResult.Success -> {
                                val corrected = parseAndSave(retry.text, idea)
                                if (corrected is AppBuildResult.Failure) {
                                    AppBuildResult.Failure(
                                        "O construtor tentou corrigir automaticamente, mas o projeto ainda não passou pela validação. Primeiro erro: ${firstAttempt.reason}. Segunda tentativa: ${corrected.reason}"
                                    )
                                } else corrected
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppBuildResult.Failure("O construtor falhou: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun isRecoverableGenerationFailure(reason: String): Boolean {
        val text = reason.lowercase()
        return listOf(
            "não retornou um projeto estruturado",
            "não contém uma lista de arquivos",
            "formato inválido",
            "está sem caminho ou conteúdo",
            "precisa retornar entre",
            "projeto gerado está incompleto",
            "falhou na validação estrutural",
            "caminho inválido ou reservado",
            "repetiu caminhos de arquivo",
            "ultrapassou o limite seguro",
            "json",
            "estrutura"
        ).any { it in text }
    }

    private suspend fun parseAndSave(raw: String, idea: String): AppBuildResult =
        withContext(Dispatchers.IO) {
            try {
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start < 0 || end <= start) {
                    return@withContext AppBuildResult.Failure(
                        "O modelo não retornou um projeto estruturado. Tente uma descrição menor ou um provedor mais capaz."
                    )
                }

                val json = JSONObject(raw.substring(start, end + 1))
                val name = json.optString("name").trim().ifBlank { "Projeto Kellvyn" }
                val description = json.optString("description").trim().ifBlank { idea.take(500) }
                val array = json.optJSONArray("files")
                    ?: return@withContext AppBuildResult.Failure("O plano não contém uma lista de arquivos.")

                if (array.length() !in 1..MAX_FILES) {
                    return@withContext AppBuildResult.Failure("O modelo precisa retornar entre 1 e $MAX_FILES arquivos.")
                }

                val files = ArrayList<ProjectFileInput>(array.length())
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                        ?: return@withContext AppBuildResult.Failure("O arquivo número ${index + 1} está em formato inválido.")
                    val path = item.optString("path").trim()
                    val content = item.optString("content")
                    if (path.isBlank() || content.isBlank()) {
                        return@withContext AppBuildResult.Failure("O arquivo número ${index + 1} está sem caminho ou conteúdo.")
                    }
                    files.add(ProjectFileInput(path, content))
                }

                // Require the core app sources to avoid saving a misleading "complete" Android app.
                val invalidPaths = files.filter { file ->
                    val path = file.path.trim()
                    val segments = path.split("/")
                    path.isBlank() || path.startsWith("/") || path.any { it.code == 92 } ||
                        segments.any { segment ->
                            segment.isBlank() || segment == "." || segment == ".." ||
                                segment.contains(":") || segment.any { it.code < 32 } ||
                                segment.equals(".git", ignoreCase = true) ||
                                segment.equals(".gradle", ignoreCase = true) ||
                                segment.equals(".kellvyn-project.json", ignoreCase = true)
                        } ||
                        (segments.size == 1 && path.equals("KELLVYN_PROJECT.md", ignoreCase = true))
                }
                if (invalidPaths.isNotEmpty()) {
                    return@withContext AppBuildResult.Failure(
                        "O projeto gerado contém caminho inválido ou reservado: ${invalidPaths.take(3).joinToString { it.path }}."
                    )
                }

                val normalizedPathList = files.map { it.path.replace('\\', '/') }
                val duplicatePaths = normalizedPathList.groupingBy { it.lowercase(Locale.ROOT) }
                    .eachCount().filterValues { it > 1 }.keys
                if (duplicatePaths.isNotEmpty()) {
                    return@withContext AppBuildResult.Failure(
                        "O modelo repetiu caminhos de arquivo: ${duplicatePaths.take(4).joinToString(", ")}. A geração será tentada novamente sem duplicatas."
                    )
                }
                val normalizedPaths = normalizedPathList.toSet()
                val required = listOf(
                    "settings.gradle.kts",
                    "build.gradle.kts",
                    "gradle.properties",
                    "app/build.gradle.kts",
                    "app/src/main/AndroidManifest.xml",
                    "app/src/main/java/com/example/app/MainActivity.java",
                    "README.md"
                )
                val missing = required.filterNot { it in normalizedPaths }
                if (missing.isNotEmpty()) {
                    return@withContext AppBuildResult.Failure(
                        "O projeto gerado está incompleto. Faltam: ${missing.joinToString(", ")}. Tente novamente com uma instrução mais específica."
                    )
                }
                if (files.sumOf { it.content.length } > MAX_PROJECT_CHARS) {
                    return@withContext AppBuildResult.Failure(
                        "A resposta de código ultrapassou o limite seguro. Peça uma versão inicial menor."
                    )
                }

                val structuralProblem = GeneratedAndroidProjectValidator.validate(files.associate { it.path.replace('\\', '/') to it.content })
                if (structuralProblem != null) {
                    return@withContext AppBuildResult.Failure(
                        "O projeto gerado falhou na validação estrutural: $structuralProblem"
                    )
                }

                val project = workspace.createProject(name, description, files)
                AppBuildResult.Success(project, workspace.listFiles(project.id))
            } catch (e: Exception) {
                AppBuildResult.Failure(
                    "Não foi possível validar/salvar o projeto gerado: " + (e.message ?: e.javaClass.simpleName)
                )
            }
        }

    private companion object {
        const val MAX_BRIEF_CHARS = 3_000
        const val MAX_FILES = 12
        const val MAX_PROJECT_CHARS = 18_000
    }
}
