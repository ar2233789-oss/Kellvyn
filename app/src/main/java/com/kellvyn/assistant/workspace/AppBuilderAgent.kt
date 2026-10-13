package com.kellvyn.assistant.workspace

import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.AssistantTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            appendLine("- Use Kotlin, Jetpack Compose, Android Gradle Kotlin DSL, namespace com.example.app.")
            appendLine("- Use Android Gradle Plugin 8.7.3, Kotlin Android plugin 2.0.21 e Compose compiler plugin 2.0.21; as versões do plugin Kotlin e do plugin Compose precisam ser iguais.")
            appendLine("- Use compileSdk 35, targetSdk 35 e minSdk 26; use Compose BOM 2024.12.01 e activity-compose 1.10.0, salvo se o pedido exigir algo específico.")
            appendLine("- Use gradle.properties econômico para build local em celular: org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8, org.gradle.parallel=false, org.gradle.workers.max=2 e kotlin.compiler.execution.strategy=in-process.")
            appendLine("- Inclua settings.gradle.kts, build.gradle.kts, gradle.properties, app/build.gradle.kts, app/src/main/AndroidManifest.xml, app/src/main/java/com/example/app/MainActivity.kt e README.md.")
            appendLine("- O projeto deve ser coerente: namespace, applicationId e caminho/pacote Kotlin precisam concordar.")
            appendLine("- Crie uma aplicação pequena mas funcional, não apenas pseudocódigo, placeholders ou textos dizendo que a função será feita depois.")
            appendLine("- Não invente tokens, chaves, APIs privadas nem dependências inexistentes.")
            appendLine("- Não crie WebView para a interface; use componentes Android nativos com Jetpack Compose.")
            appendLine("- Não use compilação remota, serviços de build, GitHub Actions ou tokens de terceiros; o build pretendido será local no aparelho.")
            appendLine("- Não inclua binários, gradlew, .git, .gradle ou qualquer caminho absoluto.")
            appendLine("- Cada item de files deve conter exatamente path e content como strings. Escape corretamente quebras de linha, aspas e barras para formar JSON válido.")
            appendLine("- Prefira no máximo 8 arquivos e aprox. 12.000 caracteres de código/texto para manter o primeiro build mais leve em um telefone; nunca ultrapasse 12 arquivos e 18.000 caracteres.")
            appendLine("- Se o pedido for grande, priorize uma primeira versão executável e documente claramente os limites no README.")
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
                val normalizedPathList = files.map { it.path.replace('\\', '/') }
                val duplicatePaths = normalizedPathList.groupingBy { it }.eachCount()
                    .filterValues { it > 1 }.keys
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
                    "app/src/main/java/com/example/app/MainActivity.kt",
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
        const val MAX_VALIDATION_ERRORS = 5
    }
}
