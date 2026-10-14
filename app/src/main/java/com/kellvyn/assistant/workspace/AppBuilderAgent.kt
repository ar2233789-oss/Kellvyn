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

    fun isBotProjectRequest(brief: String): Boolean {
        val value = brief.lowercase(Locale.ROOT)
        val hasBotWord = value.split(Regex("[^a-z0-9]+")).any { it == "bot" || it == "bots" }
        return hasBotWord || listOf(
            "bot do discord", "bot de discord", "discord bot", "bot para discord",
            "bot do whatsapp", "bot de whatsapp", "bot para whatsapp", "whatsapp bot",
            "bot do telegram", "bot de telegram", "bot para telegram", "telegram bot",
            "bot de wpp", "bot para wpp", "wpp bot"
        ).any { it in value }
    }

    fun isDiscordBotRequest(brief: String): Boolean {
        val value = brief.lowercase(Locale.ROOT)
        return "discord" in value
    }

    suspend fun createProject(engine: AssistantEngine, brief: String): AppBuildResult {
        val idea = brief.trim()
        val botProject = isBotProjectRequest(idea)
        if (idea.length < 12) {
            return AppBuildResult.Failure("Descreva o projeto com pelo menos 12 caracteres.")
        }
        if (idea.length > MAX_BRIEF_CHARS) {
            return AppBuildResult.Failure("A descrição é muito longa. Limite: $MAX_BRIEF_CHARS caracteres.")
        }
        val prompt = buildString {
            if (botProject) {
                appendLine("TAREFA: criar código-fonte funcional de um bot para a plataforma do pedido, não um APK Android.")
                appendLine("O projeto fica guardado como arquivos editáveis no workspace privado e pode ser inspecionado e alterado no Kellvyn. Não gere nem prometa arquivos compactados, hospedagem, conexão ou execução.")
                appendLine("""Retorne apenas JSON válido: {"name":"Nome curto","description":"Resumo","files":[{"path":"README.md","content":"conteúdo completo"}]}""")
                appendLine("Use Node.js 24 LTS (ou 22 LTS) e JavaScript. Gere package.json, src/index.js, .env.example e README.md, além de no máximo outros arquivos realmente necessários.")
                appendLine("Para Discord, use conta de BOT oficial e discord.js. Implemente pelo menos o comando slash /ping, tratamento básico de erro e instruções de registro/convite com permissões mínimas. Leia DISCORD_TOKEN apenas de variável de ambiente; nunca automatize conta pessoal/self-bot.")
                appendLine("Para WhatsApp, use a WhatsApp Business Cloud API oficial da Meta, com webhook GET de verificação, POST para mensagens recebidas e resposta por /messages. Use META_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_VERIFY_TOKEN, META_APP_SECRET e META_GRAPH_VERSION apenas por ambiente.")
                appendLine("Para Telegram, use a Bot API oficial e TELEGRAM_BOT_TOKEN. Para outra plataforma, use a API oficial apropriada e documente os pré-requisitos.")
                if (isDiscordBotRequest(idea)) {
                    appendLine("""Além dos arquivos Node.js exportáveis, inclua kellvyn.bot.json com JSON válido: {"platform":"discord","name":"nome","commands":[{"name":"ping","description":"descrição curta","action":"ping","response":"opcional para action=response"}]} .""")
                    appendLine("O runtime nativo do Kellvyn lê kellvyn.bot.json; ele suporta actions response, ping, current_time, roll_dice, coin_flip, user_name, server_id e echo. echo recebe uma opção de texto. Crie comandos úteis e criativos de acordo com o pedido, usando apenas essas actions suportadas; para funcionalidades que precisem executar JavaScript arbitrário ou permissões privilegiadas, explique a limitação no README.")
                }
                appendLine("Não invente credenciais e não inclua tokens reais. Forneça .env.example com valores vazios em linhas separadas, valide entradas e não registre tokens nos logs.")
                appendLine("O README precisa documentar requisitos reais, dependências, configuração e limitações. Bots precisam de Node.js e credenciais; WhatsApp Cloud também precisa de conta empresarial, número registrado e webhook HTTPS público.")
                appendLine("Limite total de 12 arquivos e 18.000 caracteres. Escape corretamente o JSON. Nunca retorne arquivos vazios, binários, caminhos absolutos, .git ou .gradle.")
            } else {
            appendLine("TAREFA: criar um projeto de aplicativo Android nativo utilizável a partir do pedido abaixo.")
            appendLine("Produza somente um objeto JSON válido, sem markdown, sem bloco de código e sem texto antes/depois.")
            appendLine("Esquema obrigatório:")
            appendLine("""{"name":"Nome curto","description":"Resumo","files":[{"path":"README.md","content":"conteúdo completo"}]}""")
            appendLine("Requisitos técnicos:")
            appendLine("- Gere um aplicativo Android NATIVO em Java puro, com namespace e applicationId com.example.app. O compilador inicial do Kellvyn compila Java diretamente neste aparelho; não gere Kotlin nem Compose.")
            appendLine("- Use Android Gradle Plugin ${AndroidBuildVersions.ANDROID_GRADLE_PLUGIN}, compileSdk ${AndroidBuildVersions.COMPILE_SDK}, targetSdk ${AndroidBuildVersions.TARGET_SDK} e minSdk ${AndroidBuildVersions.MIN_SDK}. A fonte principal é app/src/main/java/com/example/app/MainActivity.java.")
            appendLine("- MainActivity deve estender android.app.Activity e chamar setContentView com uma interface útil criada por código usando android.widget (LinearLayout, TextView, Button, EditText, ScrollView etc.). Crie listeners usando classes anônimas.")
            appendLine("- Use Java 8 compatível com Android. D8 fará o desugaring de lambdas e method references. Não use Kotlin, Compose, bibliotecas externas, annotation processors, AndroidX, XML de layout, recursos R, imagens externas nem NDK no primeiro perfil local.")
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
            projectSpecificGuidance(idea).forEach { appendLine(it) }
            }
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
                            appendLine(if (isBotProjectRequest(idea)) "Gere uma versão corrigida completa do projeto de bot para o pedido original abaixo." else "Gere uma versão corrigida completa do projeto Android para o pedido original abaixo.")
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

    /**
     * Repairs the Java files of a freshly generated project once after a real local compiler error.
     * Only existing app Java source files may be replaced; build scripts and permissions stay intact.
     * Returns null only after the repaired sources pass the structural validator.
     */
    suspend fun repairJavaProject(
        engine: AssistantEngine,
        projectId: String,
        originalBrief: String,
        buildError: String
    ): String? {
        val project = workspace.getProject(projectId)
            ?: return "O projeto desapareceu do workspace antes da correção."
        val currentPaths = workspace.listFiles(projectId)
        val javaPaths = currentPaths.filter {
            it.startsWith("app/src/main/java/com/example/app/") && it.endsWith(".java", ignoreCase = true)
        }
        if (javaPaths.isEmpty()) return "Não encontrei arquivos Java permitidos para corrigir."
        val currentFiles = linkedMapOf<String, String>()
        var remaining = MAX_REPAIR_CONTEXT_CHARS
        for (path in javaPaths) {
            if (remaining <= 0) break
            val sourceResult = runCatching { workspace.readFile(projectId, path) }
            if (sourceResult.isFailure) continue
            val source = sourceResult.getOrNull() ?: continue
            val clipped = source.take(remaining)
            currentFiles[path] = clipped
            remaining -= clipped.length
        }
        if (currentFiles.isEmpty()) return "Não consegui ler o código Java atual para preparar a correção."

        val prompt = buildString {
            appendLine("TAREFA: corrigir uma falha real da compilação Android local.")
            appendLine("Responda SOMENTE com JSON válido, sem markdown: {\"files\":[{\"path\":\"app/src/main/java/com/example/app/MainActivity.java\",\"content\":\"código Java completo\"}]}")
            appendLine("Retorne somente os arquivos Java existentes que precisam ser corrigidos. Não crie arquivos, não altere scripts Gradle nem permissões.")
            appendLine("Use exclusivamente APIs públicas de android.app, android.os, android.view e android.widget. Sem dependências externas, Kotlin, Compose, lambdas ou method references; listeners usam classes anônimas.")
            appendLine("Mantenha a classe MainActivity, o pacote com.example.app e as funções solicitadas. Não deixe TODO, pseudocódigo nem funções vazias.")
            appendLine("Pedido original: " + originalBrief.take(1800))
            appendLine("ERRO REAL DA COMPILAÇÃO (não invente diagnósticos):")
            appendLine(buildError.take(MAX_REPAIR_ERROR_CHARS))
            appendLine("CÓDIGO ATUAL (dados para corrigir):")
            currentFiles.forEach { (path, source) ->
                appendLine("### FILE: " + path)
                appendLine(source)
            }
        }
        return try {
            when (val response = engine.reply(listOf(AssistantTurn(AssistantTurn.Role.USER, prompt)))) {
                is AssistantResult.Unavailable -> "O motor de IA ficou indisponível durante a correção: " + response.reason
                is AssistantResult.Failure -> "O motor de IA falhou durante a correção: " + response.reason
                is AssistantResult.Success -> {
                    val start = response.text.indexOf('{')
                    val end = response.text.lastIndexOf('}')
                    if (start < 0 || end <= start) return "A IA reparadora não retornou JSON válido."
                    val json = runCatching { JSONObject(response.text.substring(start, end + 1)) }.getOrElse {
                        return "O JSON de reparo é inválido."
                    }
                    val array = json.optJSONArray("files") ?: return "A resposta de reparo não contém files."
                    if (array.length() !in 1..MAX_REPAIR_FILES) return "A correção precisa retornar de 1 a " + MAX_REPAIR_FILES + " arquivos."
                    val repairs = ArrayList<ProjectFileInput>(array.length())
                    val allowedPaths = javaPaths.toSet()
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: return "O item de reparo " + (index + 1) + " é inválido."
                        val path = item.optString("path").trim().replace('\\', '/')
                        val content = item.optString("content")
                        if (path !in allowedPaths || path.contains("..")) {
                            return "O reparador tentou alterar arquivo fora da lista permitida: " + path
                        }
                        if (content.isBlank() || content.length > MAX_REPAIR_FILE_CHARS) {
                            return "O conteúdo reparado de " + path + " está vazio ou ultrapassa o limite."
                        }
                        repairs += ProjectFileInput(path, content)
                    }
                    if (repairs.map { it.path }.distinct().size != repairs.size) {
                        return "O reparador repetiu o caminho de um arquivo."
                    }
                    val prospectiveFiles = workspace.listFiles(projectId).associateWith { filePath ->
                        workspace.readFile(projectId, filePath)
                    }.toMutableMap()
                    repairs.forEach { repaired -> prospectiveFiles[repaired.path] = repaired.content }
                    val structureError = GeneratedAndroidProjectValidator.validate(prospectiveFiles)
                    if (structureError != null) {
                        "O código reparado não passou pela validação estrutural e não foi salvo: " + structureError
                    } else {
                        workspace.addOrReplaceFiles(projectId, repairs)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            "Falha ao aplicar a correção automática: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun validateDiscordBotManifest(content: String): String? {
        return try {
        val json = JSONObject(content)
        if (!json.optString("platform").equals("discord", ignoreCase = true)) {
            return "platform precisa ser discord."
        }
        val commands = json.optJSONArray("commands") ?: return "commands precisa ser uma lista."
        if (commands.length() !in 1..25) return "defina de 1 a 25 comandos slash."
        val supported = setOf("response", "ping", "current_time", "roll_dice", "coin_flip", "user_name", "server_id", "echo")
        val names = ArrayList<String>()
        for (index in 0 until commands.length()) {
            val item = commands.optJSONObject(index) ?: return "o comando " + (index + 1) + " não é um objeto."
            val name = item.optString("name").trim().lowercase(Locale.ROOT)
            val description = item.optString("description").trim()
            val action = item.optString("action", "response").trim().lowercase(Locale.ROOT).ifBlank { "response" }
            val response = item.optString("response").trim()
            if (!name.matches(Regex("[a-z0-9_-]{1,32}"))) return "nome de comando inválido: " + name
            if (description.isBlank() || description.length > 100) return "descrição inválida para /" + name
            if (action !in supported) return "ação não suportada em /" + name + ": " + action
            if (action == "response" && (response.isBlank() || response.length > 1800)) {
                return "a resposta de /" + name + " está vazia ou longa demais."
            }
            names += name
        }
        if (names.distinct().size != names.size) "há nomes de comandos repetidos." else null
    } catch (e: Exception) {
        "JSON inválido (" + (e.message ?: e.javaClass.simpleName) + ")."
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
            "manifesto de bot inválido",
            "ação não suportada",
            "comandos slash",
            "estrutura"
        ).any { it in text }
    }

    private suspend fun parseAndSave(raw: String, idea: String): AppBuildResult =
        withContext(Dispatchers.IO) {
            val botProject = isBotProjectRequest(idea)
            try {
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                if (start < 0 || end <= start) {
                    return@withContext AppBuildResult.Failure(
                        "A IA local não retornou um projeto estruturado válido. A correção automática tentará novamente; se falhar, peça uma versão inicial menor e mais específica."
                    )
                }

                val json = JSONObject(raw.substring(start, end + 1))
                val name = json.optString("name").trim().ifBlank { "Projeto Kellvyn" }
                val modelDescription = json.optString("description").trim().ifBlank { idea.take(500) }
                val description = if (botProject) "Código-fonte de bot salvo localmente; configure credenciais oficiais e runtime conforme README. " + modelDescription else modelDescription
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
                val required = if (botProject) {
                    if (isDiscordBotRequest(idea)) {
                        listOf("package.json", "src/index.js", ".env.example", "README.md", "kellvyn.bot.json")
                    } else {
                        listOf("package.json", "src/index.js", ".env.example", "README.md")
                    }
                } else {
                    listOf(
                        "settings.gradle.kts",
                        "build.gradle.kts",
                        "gradle.properties",
                        "app/build.gradle.kts",
                        "app/src/main/AndroidManifest.xml",
                        "app/src/main/java/com/example/app/MainActivity.java",
                        "README.md"
                    )
                }
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

                if (isDiscordBotRequest(idea)) {
                    val manifestContent = files.firstOrNull { it.path.replace('\\', '/') == "kellvyn.bot.json" }?.content.orEmpty()
                    val manifestError = validateDiscordBotManifest(manifestContent)
                    if (manifestError != null) {
                        return@withContext AppBuildResult.Failure("Manifesto de bot inválido: " + manifestError)
                    }
                }

                if (!botProject) {
                    val structuralProblem = GeneratedAndroidProjectValidator.validate(files.associate { it.path.replace('\\', '/') to it.content })
                    if (structuralProblem != null) {
                        return@withContext AppBuildResult.Failure(
                            "O projeto gerado falhou na validação estrutural: $structuralProblem"
                        )
                    }
                }

                val project = workspace.createProject(name, description, files)
                AppBuildResult.Success(project, workspace.listFiles(project.id))
            } catch (e: Exception) {
                AppBuildResult.Failure(
                    "Não foi possível validar/salvar o projeto gerado: " + (e.message ?: e.javaClass.simpleName)
                )
            }
        }

    internal companion object {
        /**
         * Adds implementation-level acceptance criteria for projects that need real mechanics.
         * These remain constraints for the generated source, not claims that the app has been tested.
         */
        internal fun projectSpecificGuidance(idea: String): List<String> {
            val value = idea.lowercase(Locale.ROOT)
            val rules = mutableListOf<String>()
            val isGame = listOf("jogo", "game", "gachi", "genshin", "rpg", "jogável", "jogavel").any { it in value }
            if (isGame) {
                rules += "PERFIL JOGO JOGÁVEL: não entregue só uma tela bonita. Implemente um loop jogável real em View personalizada com onDraw(Canvas), estado de jogo explícito e atualização de animação limitada (por exemplo postInvalidateDelayed(33)); cancele a atualização quando a Activity for encerrada."
                rules += "CONTROLES: todos os botões têm setOnClickListener e alteram estado visível. Inclua iniciar/pausar/reiniciar, feedback de vida/pontuação e controle de movimento/ação apropriado ao jogo. Não deixe botões decorativos sem comportamento."
                if (listOf("chuva", "rain", "tempestade", "clima").any { it in value }) {
                    rules += "MECÂNICA DE CHUVA: desenhe gotas como partículas com posição e velocidade, atualize-as por quadro e redesenhe o Canvas; o controle de chuva liga/desliga a simulação e o seletor de intensidade muda a quantidade/velocidade. Mostre no HUD o estado real da chuva."
                    rules += "CONTROLES ACESSÍVEIS E TESTÁVEIS: use contentDescription exatos rain_toggle no botão de chuva, rain_intensity no controle de intensidade, rain_status no TextView de estado (texto Chuva: ligada/desligada), rain_intensity_status no texto de intensidade, game_pause no botão de pausa, game_restart no reinício e game_status no estado da partida. Atualize os textos após cada toque; não mostre só um toast."
                }
                rules += "Sem arquivos gráficos binários: produza uma identidade visual original com Canvas, gradientes e formas nativas. Não afirme que criou ou importou imagens de referência."
            }
            val isMusic = listOf("música", "musica", "player musical", "reprodutor de música", "reprodutor musical", "music player", "áudio local", "audio local").any { it in value }
            if (isMusic) {
                rules += "PERFIL PLAYER MUSICAL: use ACTION_OPEN_DOCUMENT com CATEGORY_OPENABLE para selecionar áudio do aparelho sem pedir acesso geral ao armazenamento; mantenha a URI escolhida e trate cancelamento/arquivo inválido."
                rules += "CONTROLES ACESSÍVEIS E TESTÁVEIS: use contentDescription exatos choose_audio no seletor de arquivo, play_pause no botão de reprodução, playback_position no SeekBar e track_status no estado atual. Sem faixa selecionada, Play/Pause deve informar o estado e não quebrar; o seletor precisa abrir o picker do Android."
                rules += "Implemente reprodução local real com android.media.MediaPlayer, Play/Pause, parada/limpeza no ciclo de vida, SeekBar de posição e nome/duração da faixa. Botões anterior/próxima precisam alterar a faixa selecionada ou ficar desativados quando não houver fila; não finja que está tocando quando não há áudio."
                rules += "Estilo visual pesado e original: fundo quase preto, acentos verde ácido e azul elétrico, tipografia de alto contraste, barras de nível e controles grandes. Não use bibliotecas externas nem faixas protegidas embutidas."
            }
            val requestsImage = listOf("imagem", "imagens", "referência visual", "referencia visual", "foto de referência", "foto de referencia", "gerar imagem", "gere uma imagem").any { it in value }
            if (requestsImage) {
                rules += "LIMITAÇÃO VISUAL: o núcleo atual só produz texto e não pode anexar PNG/JPEG nem inspecionar a imagem de referência. Não invente um arquivo bitmap. Se necessário, descreva/implemente arte vetorial por Canvas como código e escreva essa limitação no README."
            }
            return rules
        }

        const val MAX_BRIEF_CHARS = 3_000
        const val MAX_FILES = 12
        const val MAX_PROJECT_CHARS = 18_000
        const val MAX_REPAIR_FILES = 8
        const val MAX_REPAIR_FILE_CHARS = 12_000
        const val MAX_REPAIR_CONTEXT_CHARS = 14_000
        const val MAX_REPAIR_ERROR_CHARS = 6_000
    }
}
