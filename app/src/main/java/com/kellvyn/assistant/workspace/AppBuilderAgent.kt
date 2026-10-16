package com.kellvyn.assistant.workspace

import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.AssistantTurn
import com.kellvyn.assistant.ai.StreamingAssistantEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import org.json.JSONObject

sealed interface AppBuildResult {
    data class Success(val project: WorkspaceProject, val files: List<String>) : AppBuildResult
    data class Failure(val reason: String) : AppBuildResult
}

/** Unicode-aware whole-word check: Portuguese "botões" must never be interpreted as "bot". */
internal fun containsStandaloneBotKeyword(text: String): Boolean =
    Regex("(?<![\\p{L}\\p{N}_])bots?(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
        .containsMatchIn(text)

/** Visible, task-specific plan shown inside the main chat while the builder works. */
internal fun visibleBuilderPlan(brief: String, botProject: Boolean): String {
    val value = brief.lowercase(Locale.ROOT)
    if (botProject) {
        val platform = when {
            "discord" in value -> "Discord"
            "telegram" in value -> "Telegram"
            "whatsapp" in value || "wpp" in value -> "WhatsApp Business"
            else -> "a plataforma solicitada"
        }
        return "Plano do cérebro: preparar o projeto para $platform; gerar os arquivos e a configuração exigida; validar os comandos/manifesto; salvar o código no catálogo. A conexão real exige as credenciais oficiais."
    }

    val featureSignals = listOf(
        listOf("login", "entrar", "autenticação", "autenticacao") to "login",
        listOf("cadastro", "registrar", "registro") to "cadastro",
        listOf("busca", "pesquisa", "pesquisar", "filtrar") to "busca/filtragem",
        listOf("histórico", "historico") to "histórico",
        listOf("salvar", "salvamento", "guardar", "persistência", "persistencia", "banco de dados") to "salvamento de dados",
        listOf("notificação", "notificacao", "alerta") to "notificações",
        listOf("offline", "sem internet") to "uso offline",
        listOf("música", "musica", "áudio", "audio", "player") to "áudio/reprodução",
        listOf("chuva", "tempestade", "clima") to "simulação/controles de chuva",
        listOf("imagem", "imagens", "galeria", "foto") to "imagens",
        listOf("estoque", "produto", "inventário", "inventario") to "gestão de itens",
        listOf("mapa", "localização", "localizacao", "rota") to "mapa/localização",
        listOf("agenda", "calendário", "calendario", "evento") to "agenda"
    )
    val requested = featureSignals
        .filter { (signals, _) -> signals.any { it in value } }
        .map { it.second }
        .distinct()
        .take(6)
    val features = requested.joinToString(", ").ifBlank { "os recursos descritos no pedido" }
    return "Plano do cérebro: implementar $features; criar a interface e a lógica em Android nativo/Java; validar MainActivity e os arquivos; salvar o projeto; tentar compilar, assinar e verificar o APK. O resultado final será informado conforme os testes reais."
}

/**
 * Converts a user's application brief into a proposed source-code project.
 * The model's output is treated as untrusted text: it is parsed as JSON,
 * path/size-limited by ProjectWorkspaceStore and saved as data, never executed.
 */
class AppBuilderAgent(private val workspace: ProjectWorkspaceStore) {

    fun isBotProjectRequest(brief: String): Boolean {
        val value = brief.lowercase(Locale.ROOT)
        val hasBotWord = containsStandaloneBotKeyword(value)
        return hasBotWord || listOf(
            "bot do discord", "bot de discord", "discord bot", "bot para discord",
            "bot do whatsapp", "bot de whatsapp", "bot para whatsapp", "whatsapp bot",
            "bot do telegram", "bot de telegram", "bot para telegram", "telegram bot",
            "bot de wpp", "bot para wpp", "wpp bot"
        ).any { it in value }
    }

    fun isDiscordBotRequest(brief: String): Boolean =
        BotPlatformDetector.isDiscord(brief)

    fun isTelegramBotRequest(brief: String): Boolean =
        BotPlatformDetector.isTelegram(brief)

    suspend fun createProject(
        engine: AssistantEngine,
        brief: String,
        onProgress: (String) -> Unit = {},
        onOutputPreview: (String) -> Unit = {}
    ): AppBuildResult {
        val idea = brief.trim()
        val botProject = isBotProjectRequest(idea)
        onProgress("Cérebro: entendendo o pedido e organizando o plano.")
        if (idea.length < 12) {
            return AppBuildResult.Failure("Descreva o projeto com pelo menos 12 caracteres.")
        }
        if (idea.length > MAX_BRIEF_CHARS) {
            return AppBuildResult.Failure("A descrição é muito longa. Limite: $MAX_BRIEF_CHARS caracteres.")
        }
        onProgress(visibleBuilderPlan(idea, botProject))
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
                    appendLine("""Inclua kellvyn.bot.json válido para o runtime nativo do Discord: {"platform":"discord","name":"nome","commands":[{"name":"ping","description":"Verifica se o bot está ativo","action":"ping","response":""}]} .""")
                    appendLine("O runtime nativo do Discord suporta response, ping, current_time, roll_dice, coin_flip, user_name, server_id e echo. Crie comandos úteis com essas ações suportadas; documente no README qualquer função que precise de executar JavaScript arbitrário.")
                } else if (isTelegramBotRequest(idea)) {
                    appendLine("""Inclua kellvyn.telegram.json válido para o runtime nativo do Telegram: {"platform":"telegram","name":"nome","commands":[{"name":"start","description":"Iniciar o bot","action":"response","response":"Olá, {user}! Use /ping para testar."},{"name":"ping","description":"Verificar se estou ativo","action":"ping","response":""}]} .""")
                    appendLine("O runtime nativo do Telegram fica no telefone e suporta response, ping, current_time, roll_dice, coin_flip, user_name e echo. Use nomes de comandos minúsculos, iniciando com letra e contendo somente letras, números e sublinhados. O comando echo repete o texto que vier depois do comando. Documente no README as limitações das ações nativas; não afirme que JavaScript arbitrário executa dentro do app.")
                }
                appendLine("Não invente credenciais e não inclua tokens reais. Forneça .env.example com valores vazios em linhas separadas, valide entradas e não registre tokens nos logs.")
                appendLine("O README precisa documentar requisitos reais, dependências, configuração e limitações. Bots precisam de Node.js e credenciais; WhatsApp Cloud também precisa de conta empresarial, número registrado e webhook HTTPS público.")
                appendLine("Limite total de 12 arquivos e 18.000 caracteres. Escape corretamente o JSON. Nunca retorne arquivos vazios, binários, caminhos absolutos, .git ou .gradle.")
            } else {
                appendLine("TAREFA: criar um projeto Android nativo funcional em Java usando uma Activity principal e configuração local.")
                appendLine("Retorne SOMENTE o código-fonte Java de MainActivity.java. Sem JSON, markdown, cercas de código ou explicações.")
                appendLine("O arquivo deve usar package com.example.app; declarar public class MainActivity extends Activity; implementar onCreate e chamar setContentView com uma interface nativa funcional.")
                appendLine("Use Java 8 e APIs nativas públicas disponíveis no android.jar do compilador local, incluindo android.app, android.os, android.content, android.view, android.widget, android.media, android.net, android.provider e android.graphics. Importe as classes utilizadas.")
                appendLine("Não use Kotlin, Compose, bibliotecas externas, WebView, resources R, imagens binárias, lambdas, method references, TODOs, placeholders ou funções vazias.")
                appendLine("Implemente o comportamento real pedido, com estados e listeners reais. Não descreva o que faria.")
                projectSpecificGuidance(idea).forEach { appendLine(it) }
            }
            appendLine()
            appendLine("PEDIDO DO USUÁRIO:")
            appendLine(idea)
        }

        onProgress("Gerando os arquivos com o modelo local…")
        return try {
            when (val response = generateProjectOutput(engine, prompt, onProgress, "Gerando o código do projeto", onOutputPreview)) {
                is AssistantResult.Unavailable -> AppBuildResult.Failure(response.reason)
                is AssistantResult.Failure -> AppBuildResult.Failure(response.reason)
                is AssistantResult.Success -> {
                    onProgress("Resposta recebida. Validando os arquivos e a estrutura do projeto…")
                    val firstAttempt = if (botProject) parseAndSave(response.text, idea) else parseAndSaveAndroidProject(response.text, idea)
                    onProgress(if (firstAttempt is AppBuildResult.Success) "Estrutura válida; projeto salvo no catálogo local." else "A validação inicial encontrou um problema.")
                    if (firstAttempt !is AppBuildResult.Failure || !isRecoverableGenerationFailure(firstAttempt.reason)) {
                        firstAttempt
                    } else {
                        onProgress("Preparando uma única correção automática limitada pelo erro encontrado…")
                        val repairPrompt = if (botProject) buildString {
                            appendLine("TAREFA: criar código-fonte funcional corrigido do projeto de bot solicitado.")
                            appendLine("A tentativa anterior falhou nesta validação: ${firstAttempt.reason}")
                            appendLine("Não repita a resposta inválida. Corrija o problema e retorne o projeto completo em JSON válido, sem markdown.")
                            appendLine(prompt.substringBefore("PEDIDO DO USUÁRIO:"))
                            appendLine("RESPOSTA ANTERIOR PARA CORRIGIR (pode estar incompleta):")
                            appendLine(response.text.take(4_500))
                            appendLine("PEDIDO ORIGINAL DO USUÁRIO:")
                            appendLine(idea)
                        } else buildString {
                            appendLine("TAREFA: corrigir uma falha real da compilação Android local e retornar uma Activity válida.")
                            appendLine("A tentativa anterior foi rejeitada pelo construtor: ${firstAttempt.reason}")
                            appendLine("Não copie a versão incompleta. Gere uma nova versão completa e curta, em até 180 linhas, sem comentários longos.")
                            appendLine("Retorne somente Java, sem JSON, markdown, cercas ou explicações.")
                            appendLine("Obrigatório: package com.example.app; imports válidos; public class MainActivity extends Activity; onCreate com super.onCreate; pelo menos um controle android.widget real e setContentView.")
                            appendLine("Use Java 8, listeners funcionais com classes anônimas, sem Kotlin, Compose, WebView, dependências externas, TODO ou métodos vazios.")
                            projectSpecificGuidance(idea).forEach { appendLine(it) }
                            appendLine("SAÍDA ANTERIOR REPROVADA — corrija os problemas em vez de repetir o erro:")
                            appendLine(response.text.take(4_500))
                            appendLine("DIAGNÓSTICO DA VALIDAÇÃO: ${firstAttempt.reason}")
                            appendLine("PEDIDO ORIGINAL:")
                            appendLine(idea)
                        }
                        onProgress("A primeira versão falhou. O cérebro está corrigindo a saída com base no erro e na resposta anterior…")
                        when (val retry = generateProjectOutput(engine, repairPrompt, onProgress, "Gerando a versão corrigida", onOutputPreview)) {
                            is AssistantResult.Unavailable -> AppBuildResult.Failure(
                                "A primeira geração falhou na validação (${firstAttempt.reason}) e o modelo ficou indisponível na tentativa de correção: ${retry.reason}"
                            )
                            is AssistantResult.Failure -> AppBuildResult.Failure(
                                "A primeira geração falhou na validação (${firstAttempt.reason}); a tentativa automática de correção também falhou: ${retry.reason}"
                            )
                            is AssistantResult.Success -> {
                                onProgress("Verificando a versão corrigida…")
                                val corrected = if (botProject) parseAndSave(retry.text, idea) else parseAndSaveAndroidProject(retry.text, idea)
                                onProgress(if (corrected is AppBuildResult.Success) "Correção validada; projeto salvo no catálogo local." else "A segunda validação também encontrou um problema.")
                                if (corrected is AppBuildResult.Failure) {
                                    AppBuildResult.Failure(
                                        "A geração automática não passou pela validação e a correção também falhou. Primeiro diagnóstico: ${firstAttempt.reason}. Segundo diagnóstico: ${corrected.reason}"
                                    )
                                } else corrected
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppBuildResult.Failure("Não foi possível gerar e validar o projeto: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Streams model progress into the chat without exposing private reasoning or raw intermediate tool data. */
    private suspend fun generateProjectOutput(
        engine: AssistantEngine,
        prompt: String,
        onProgress: (String) -> Unit,
        phase: String,
        onOutputPreview: (String) -> Unit
    ): AssistantResult {
        val turns = listOf(AssistantTurn(AssistantTurn.Role.USER, prompt))
        val streaming = engine as? StreamingAssistantEngine ?: return engine.reply(turns)
        var lastReportedChars = 0
        var lastPreviewLength = 0
        return streaming.replyStreaming(turns) { partial ->
            val receivedChars = partial.length
            if (receivedChars > 0 && (
                    lastPreviewLength == 0 ||
                        receivedChars - lastPreviewLength >= 360 ||
                        receivedChars < lastPreviewLength
                    )
            ) {
                lastPreviewLength = receivedChars
                onOutputPreview(partial.takeLast(900))
            }
            if (receivedChars > 0 && (
                    lastReportedChars == 0 ||
                        receivedChars - lastReportedChars >= 180 ||
                        receivedChars < lastReportedChars
                    )
            ) {
                lastReportedChars = receivedChars
                onProgress("$phase — $receivedChars caracteres recebidos do modelo local.")
            }
        }
    }

    /**
     * Saves a model-generated Android Activity together with deterministic project scaffolding.
     * This keeps the local model's output focused on the requested app rather than seven verbose files.
     */
    private suspend fun parseAndSaveAndroidProject(raw: String, idea: String): AppBuildResult =
        withContext(Dispatchers.IO) {
            try {
                val json = runCatching {
                    val start = raw.indexOf('{')
                    val end = raw.lastIndexOf('}')
                    if (start >= 0 && end > start) JSONObject(raw.substring(start, end + 1)) else null
                }.getOrNull()
                val responseFiles = json?.optJSONArray("files")
                val entries = responseFiles?.let { array ->
                    (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
                }.orEmpty()
                val sourceFromFiles = entries.firstOrNull {
                    it.optString("path").trim().replace('\\', '/')
                        .substringAfterLast('/').equals("MainActivity.java", ignoreCase = true)
                }?.optString("content").orEmpty()
                val candidateSource = sourceFromFiles.ifBlank { raw }
                val name = json?.optString("name").orEmpty().trim().take(60)
                    .ifBlank { defaultAndroidProjectName(idea) }
                val description = json?.optString("description").orEmpty().trim().take(500)
                    .ifBlank { idea.take(500) }
                val readme = entries.firstOrNull {
                    it.optString("path").trim().substringAfterLast('/').equals("README.md", ignoreCase = true)
                }?.optString("content")

                val files = createAndroidProjectFiles(name, idea, candidateSource, readme)
                    ?: return@withContext AppBuildResult.Failure(
                        "A IA local não conseguiu gerar um código válido para a tela inicial do aplicativo (MainActivity.java). Diagnóstico técnico: " +
                            diagnoseAndroidActivity(candidateSource) +
                            " O cérebro tentará corrigir o problema usando esse diagnóstico e a resposta anterior."
                    )
                val totalChars = files.sumOf { it.content.length }
                if (totalChars > MAX_PROJECT_CHARS) {
                    return@withContext AppBuildResult.Failure(
                        "O código Android gerado ultrapassou o limite local de tamanho; peça uma primeira versão menor."
                    )
                }
                val structuralProblem = GeneratedAndroidProjectValidator.validate(
                    files.associate { it.path.replace('\\', '/') to it.content }
                )
                if (structuralProblem != null) {
                    return@withContext AppBuildResult.Failure(
                        "A Activity Java não passou pela validação estrutural: $structuralProblem"
                    )
                }
                val project = workspace.createProject(name, description, files)
                AppBuildResult.Success(project, workspace.listFiles(project.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppBuildResult.Failure(
                    "Não foi possível validar/salvar o aplicativo Android gerado: " +
                        (e.message ?: e.javaClass.simpleName)
                )
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
            appendLine("Use exclusivamente APIs nativas públicas disponíveis no android.jar do compilador local, incluindo android.app, android.os, android.content, android.view, android.widget, android.media, android.net, android.provider e android.graphics quando necessárias. Sem dependências externas, Kotlin, Compose, WebView, lambdas ou method references; listeners usam classes anônimas.")
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
        } catch (e: CancellationException) {
            throw e
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
            "não conseguiu gerar um código válido para a tela inicial do aplicativo",
            "não retornou uma activity android válida",
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
            "manifesto de bot telegram inválido",
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
                    when {
                        isDiscordBotRequest(idea) ->
                            listOf("package.json", "src/index.js", ".env.example", "README.md", "kellvyn.bot.json")
                        isTelegramBotRequest(idea) ->
                            listOf("package.json", "src/index.js", ".env.example", "README.md", "kellvyn.telegram.json")
                        else ->
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
                } else if (isTelegramBotRequest(idea)) {
                    val manifestContent = files.firstOrNull { it.path.replace('\\', '/') == "kellvyn.telegram.json" }?.content.orEmpty()
                    val manifestError = TelegramBotManifestValidator.validate(manifestContent)
                    if (manifestError != null) {
                        return@withContext AppBuildResult.Failure("Manifesto de bot Telegram inválido: " + manifestError)
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
            } catch (e: CancellationException) {
                throw e
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
        /** Explains structural failures so the next generation repairs the actual problem. */
        internal fun diagnoseAndroidActivity(raw: String): String {
            if (raw.isBlank()) return "a resposta do modelo veio vazia."
            if (raw.length > MAX_REPAIR_FILE_CHARS) return "a resposta ultrapassou o limite seguro de caracteres."
            if (!Regex("""\bclass\s+MainActivity\b""").containsMatchIn(raw)) {
                return "não encontrei a declaração da classe MainActivity."
            }
            if (!Regex("""\bpublic\s+class\s+MainActivity\b""").containsMatchIn(raw)) {
                return "MainActivity não foi declarada como classe pública."
            }
            val activityDeclaration = Regex("""\bclass\s+MainActivity\b[\s\S]{0,300}?\bextends\s+Activity\b""")
                .find(raw)
            if (activityDeclaration == null) return "MainActivity não estende android.app.Activity como o compilador local exige."
            if (!Regex("""\bonCreate\s*\(""").containsMatchIn(raw)) {
                return "faltou implementar onCreate(Bundle) para iniciar a tela."
            }
            if (!raw.contains("setContentView(")) return "faltou montar a interface com setContentView(...)."
            val nativeControls = Regex("""\bnew\s+(?:TextView|Button|EditText|LinearLayout|FrameLayout|ScrollView|ImageView|CheckBox|Switch|RadioButton|ProgressBar|SeekBar|Spinner|ListView|RecyclerView)\s*\(""")
                .containsMatchIn(raw)
            if (!nativeControls) return "não encontrei nenhum controle nativo visível criado por código."
            val openingBrace = raw.indexOf('{', activityDeclaration.range.last + 1)
            if (openingBrace < 0 || findMatchingJavaBrace(raw, openingBrace) == null) {
                return "o bloco da classe está incompleto, provavelmente porque a geração foi interrompida antes das chaves finais."
            }
            return "a classe parece conter os elementos básicos, mas não passou pela extração segura; gere uma Activity curta e completa."
        }

        /**
         * Accepts raw Java or JSON/file-list output, but only returns a complete native Activity.
         * Braces inside Java strings and comments do not count toward class closure.
         */
        internal fun extractCompleteAndroidActivity(raw: String): String? {
            if (raw.isBlank() || raw.length > MAX_REPAIR_FILE_CHARS) return null
            var normalizedRaw = raw
            var classMatch = Regex("""\bclass\s+MainActivity\b[\s\S]{0,300}?\bextends\s+Activity\b""")
                .find(normalizedRaw) ?: return null
            if (!Regex("""\bpublic\s+class\s+MainActivity\b""").containsMatchIn(normalizedRaw.substring(0, classMatch.range.last + 1))) {
                // The output is already the designated MainActivity.java file. Promote a package-private
                // top-level Activity to public instead of rejecting an otherwise usable local-model result.
                val declaration = Regex("""(?m)^([ \t]*)(?:final[ \t]+)?class[ \t]+MainActivity(?=[ \t]+extends[ \t]+Activity\b)""")
                val declarationMatch = declaration.find(normalizedRaw) ?: return null
                normalizedRaw = normalizedRaw.replaceRange(
                    declarationMatch.range,
                    declarationMatch.groupValues[1] + "public class MainActivity"
                )
                classMatch = Regex("""\bpublic\s+class\s+MainActivity\b[\s\S]{0,300}?\bextends\s+Activity\b""")
                    .find(normalizedRaw) ?: return null
            }
            val packagePattern = Regex("""(?m)^[ \t]*package[ \t]+[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*[ \t]*;?[ \t]*$""")
            val packageMatch = packagePattern.find(normalizedRaw)?.takeIf { it.range.first < classMatch.range.first }
            val importPattern = Regex("""(?m)^[ \t]*import[ \t]+(?:static[ \t]+)?[A-Za-z_$][A-Za-z0-9_$.]*(?:\.\*)?[ \t]*;[ \t]*$""")
            val firstImport = importPattern.findAll(normalizedRaw).firstOrNull { it.range.first < classMatch.range.first }
            val sourceStart = packageMatch?.range?.first ?: firstImport?.range?.first ?: classMatch.range.first
            val openingBrace = normalizedRaw.indexOf('{', classMatch.range.last + 1)
            if (openingBrace < 0) return null
            val closingBrace = findMatchingJavaBrace(normalizedRaw, openingBrace) ?: return null
            var source = normalizedRaw.substring(sourceStart, closingBrace + 1).trim()
            if (packageMatch == null) {
                source = "package com.example.app;\n" + source
            } else {
                val localPackagePattern = Regex("""(?m)^[ \t]*package[ \t]+[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*[ \t]*;?[ \t]*$""")
                source = localPackagePattern.replaceFirst(source, "package com.example.app;")
            }
            if (!Regex("""(?m)^\s*package\s+com\.example\.app\s*;""").containsMatchIn(source) ||
                !Regex("""\bpublic\s+class\s+MainActivity\s+extends\s+Activity\b""").containsMatchIn(source) ||
                !source.contains("setContentView(") ||
                source.length > MAX_REPAIR_FILE_CHARS
            ) return null
            return source
        }

        /**
         * Creates the five fixed build/manifest files around real model-generated Activity source.
         * These are standard configuration files, not substitute app behavior.
         */
        internal fun createAndroidProjectFiles(
            name: String,
            idea: String,
            rawActivity: String,
            generatedReadme: String? = null
        ): List<ProjectFileInput>? {
            val activity = extractCompleteAndroidActivity(rawActivity) ?: return null
            val cleanName = name.trim().take(60).ifBlank { defaultAndroidProjectName(idea) }
            val gradleName = cleanName.replace(Regex("""[^A-Za-z0-9_-]"""), "").take(40).ifBlank { "KellvynApp" }
            val xmlLabel = cleanName.take(48)
                .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            val readme = generatedReadme?.trim()?.takeIf { it.length in 60..2_500 } ?: """
                # $cleanName

                Aplicativo Android nativo gerado a partir do pedido fornecido ao Kellvyn.

                ## Pedido original
                ${idea.take(2_500)}

                ## Código principal
                `app/src/main/java/com/example/app/MainActivity.java`

                O projeto usa uma Activity Java e controles nativos. A configuração Gradle e o manifesto
                padrão são gerados localmente pelo construtor para evitar configuração incompatível.
                Esta anotação não afirma que o APK foi compilado, instalado ou testado.
            """.trimIndent()
            return listOf(
                ProjectFileInput("settings.gradle.kts", """
                    pluginManagement {
                        repositories { google(); mavenCentral(); gradlePluginPortal() }
                    }
                    dependencyResolutionManagement {
                        repositories { google(); mavenCentral() }
                    }
                    rootProject.name = "$gradleName"
                    include(":app")
                """.trimIndent()),
                ProjectFileInput("build.gradle.kts", """
                    plugins {
                        id("com.android.application") version "${AndroidBuildVersions.ANDROID_GRADLE_PLUGIN}" apply false
                    }
                """.trimIndent()),
                ProjectFileInput("gradle.properties", """
                    org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
                    org.gradle.parallel=false
                    org.gradle.workers.max=2
                """.trimIndent()),
                ProjectFileInput("app/build.gradle.kts", """
                    plugins {
                        id("com.android.application")
                    }
                    android {
                        namespace = "com.example.app"
                        compileSdk = ${AndroidBuildVersions.COMPILE_SDK}
                        defaultConfig {
                            applicationId = "com.example.app"
                            minSdk = ${AndroidBuildVersions.MIN_SDK}
                            targetSdk = ${AndroidBuildVersions.TARGET_SDK}
                            versionCode = 1
                            versionName = "1.0"
                        }
                    }
                """.trimIndent()),
                ProjectFileInput("app/src/main/AndroidManifest.xml", """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application android:label="$xmlLabel">
                            <activity android:name=".MainActivity" android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                """.trimIndent()),
                ProjectFileInput("app/src/main/java/com/example/app/MainActivity.java", activity),
                ProjectFileInput("README.md", readme)
            )
        }

        internal fun defaultAndroidProjectName(idea: String): String {
            val cleaned = idea.trim()
                .replace(Regex("""(?i)^\s*(?:crie|criar|faça|faca|desenvolva|desenvolver|construa|construir|gere|gerar|monte|montar)\s+"""), "")
                .replace(Regex("""(?i)^(?:um|uma|o|a)\s+"""), "")
                .replace(Regex("""(?i)^(?:app|aplicativo)\s+"""), "")
                .replace(Regex("""[^\p{L}\p{N}\s_-]"""), " ")
                .split(Regex("""\s+""")).filter { it.isNotBlank() }.take(6).joinToString(" ")
                .ifBlank { "Projeto Android" }.take(48).trim()
            return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        private fun findMatchingJavaBrace(source: String, openingBrace: Int): Int? {
            var depth = 0
            var state = 0 // 0=code, 1=line comment, 2=block comment, 3=string, 4=char
            var escaped = false
            var index = openingBrace
            while (index < source.length) {
                val ch = source[index]
                val next = source.getOrNull(index + 1)
                when (state) {
                    0 -> when {
                        ch == '/' && next == '/' -> { state = 1; index += 2; continue }
                        ch == '/' && next == '*' -> { state = 2; index += 2; continue }
                        ch == '"' -> { state = 3; escaped = false }
                        ch == '\'' -> { state = 4; escaped = false }
                        ch == '{' -> depth++
                        ch == '}' -> {
                            depth--
                            if (depth == 0) return index
                            if (depth < 0) return null
                        }
                    }
                    1 -> if (ch == '\n') state = 0
                    2 -> if (ch == '*' && next == '/') { state = 0; index += 2; continue }
                    3, 4 -> {
                        val closing = if (state == 3) '"' else '\''
                        if (escaped) escaped = false
                        else if (ch == '\\') escaped = true
                        else if (ch == closing) state = 0
                    }
                }
                index++
            }
            return null
        }

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
                rules += "ESCOPO DE RECURSOS VISUAIS: este construtor gera código e arquivos textuais e ainda não incorpora automaticamente arquivos PNG/JPEG binários ao projeto. Não afirme que uma imagem foi adicionada ao aplicativo sem confirmar que o arquivo foi realmente salvo no workspace. Para elementos visuais dentro do app, implemente arte vetorial com Canvas e controles nativos. A IA principal pode gerar ilustrações PNG localmente, mas isso não significa que o arquivo foi anexado ao projeto gerado."
            }
            return rules
        }

        const val MAX_BRIEF_CHARS = 3_000
        const val MAX_FILES = 12
        const val MAX_PROJECT_CHARS = 22_000
        const val MAX_REPAIR_FILES = 8
        const val MAX_REPAIR_FILE_CHARS = 16_000
        const val MAX_REPAIR_CONTEXT_CHARS = 14_000
        const val MAX_REPAIR_ERROR_CHARS = 6_000
    }
}
