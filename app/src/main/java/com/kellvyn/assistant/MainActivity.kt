package com.kellvyn.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantAgent
import com.kellvyn.assistant.ai.AgentToolRouter
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.reasonOrText
import com.kellvyn.assistant.ai.AssistantTurn
import com.kellvyn.assistant.ai.LocalLiteRtAssistantEngine
import com.kellvyn.assistant.data.ChatHistoryStore
import com.kellvyn.assistant.data.ChatConversationStore
import com.kellvyn.assistant.data.SavedConversation
import com.kellvyn.assistant.data.MemoryFact
import com.kellvyn.assistant.data.UserMemoryStore
import com.kellvyn.assistant.tools.WorkspaceConsoleDialog
import com.kellvyn.assistant.tools.WorkspaceTerminal
import com.kellvyn.assistant.workspace.AppBuilderAgent
import com.kellvyn.assistant.workspace.EditorSyncPolicy
import com.kellvyn.assistant.workspace.AppBuildResult
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.WorkspaceProject
import com.kellvyn.assistant.workspace.LocalAndroidCompiler
import com.kellvyn.assistant.workspace.LocalApkBuildResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

private val Night = Color(0xFF020806)
private val Panel = Color(0xFF071713)
private val Neon = Color(0xFF38E6BC)
private val Muted = Color(0xFF91B8AD)
private val Warning = Color(0xFFFFC857)
private const val MAX_CONTEXT_MESSAGES = 8
private const val MAX_HISTORY_MESSAGE_CHARS = 800
private const val MAX_INPUT_CHARS = 12_000
private const val MAX_EDITOR_CHARS = 80_000

/** Only compiler diagnostics tied to Java/D8 source are eligible for the automatic code-repair pass. */
private fun isRepairableSourceBuildFailure(reason: String): Boolean {
    val value = reason.lowercase(Locale.ROOT)
    return value.contains("erro real da compilação java") ||
        value.contains("com.android.tools.r8") ||
        value.contains("compilationfailedexception") ||
        value.contains("d8 compilation failed") ||
        value.contains("cannot fit requested classes in a single dex file")
}

data class ChatMessage(val text: String, val fromUser: Boolean)

private fun isKellvynIdentityQuestion(text: String): Boolean {
    val value = text.lowercase(Locale.ROOT)
    return listOf(
        "quem te criou", "quem criou você", "quem criou voce", "quem desenvolveu você", "quem desenvolveu voce",
        "quem é você", "quem e voce", "quem é voce", "quem e você", "quem é a ia", "quem e a ia",
        "qual é seu nome", "qual e seu nome", "você é da alibaba", "voce e da alibaba", "criado pela alibaba", "criada pela alibaba", "feito pela alibaba", "feito por alibaba",
        "você é o qwen", "voce e o qwen", "você é o chatgpt", "voce e o chatgpt", "quem é o modelo", "quem e o modelo"
    ).any { it in value }
}

private fun kellvynIdentityAnswer(): String =
    "Sou o Kellvyn IA, o assistente integrado a este aplicativo. " +
        "Minhas respostas são processadas pelo núcleo local incluído no app, sem enviar sua conversa a um provedor de IA externo. " +
        "Não sou a Alibaba Cloud nem o Qwen; esses nomes não são a identidade deste assistente."


private fun projectIconGlyph(project: WorkspaceProject): String {
    val description = (project.name + " " + project.description).lowercase(Locale.ROOT)
    return when {
        listOf("jogo", "game", "arcade", "rpg").any { it in description } -> "🎮"
        listOf("música", "musica", "áudio", "audio", "player").any { it in description } -> "🎵"
        listOf("foto", "câmera", "camera", "imagem", "galeria").any { it in description } -> "📷"
        listOf("nota", "texto", "escrita", "diário", "diario").any { it in description } -> "📝"
        listOf("tarefa", "todo", "lista", "produtividade").any { it in description } -> "✅"
        listOf("agenda", "calendário", "calendario", "evento").any { it in description } -> "📅"
        listOf("chat", "mensagem", "conversa", "assistente", "ia ").any { it in description } -> "💬"
        listOf("loja", "compra", "produto", "venda").any { it in description } -> "🛍️"
        listOf("mapa", "rota", "localização", "localizacao").any { it in description } -> "🗺️"
        listOf("dinheiro", "finança", "financa", "banco", "gasto", "orçamento", "orcamento").any { it in description } -> "💰"
        listOf("calculadora", "cálculo", "calculo", "matemática", "matematica").any { it in description } -> "🧮"
        listOf("saúde", "saude", "treino", "fitness", "exercício", "exercicio").any { it in description } -> "💠"
        listOf("bot", "discord", "whatsapp", "telegram", "wpp").any { it in description } -> "🤖"
        else -> "▣"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KellvynApp() }
    }
}

@Composable
fun KellvynApp() {
    val context = LocalContext.current.applicationContext
    val historyStore = remember(context) { ChatHistoryStore(context) }
    val conversationStore = remember(context) { ChatConversationStore(context) }
    val memoryStore = remember(context) { UserMemoryStore(context) }
    val localEngine = remember(context) { LocalLiteRtAssistantEngine(context) }
    val workspaceStore = remember(context) { ProjectWorkspaceStore(context) }
    val workspaceTerminal = remember(workspaceStore) { WorkspaceTerminal(workspaceStore) }
    val appBuilderAgent = remember(workspaceStore) { AppBuilderAgent(workspaceStore) }
    val localAndroidCompiler = remember(context, workspaceStore) { LocalAndroidCompiler(context, workspaceStore) }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var speechOutputReady by remember { mutableStateOf(false) }
    val speechOutputHolder = remember(context) { arrayOfNulls<TextToSpeech>(1) }
    val speechOutput = remember(context) {
        TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val languageResult = speechOutputHolder[0]?.setLanguage(Locale("pt", "BR"))
                    ?: TextToSpeech.LANG_NOT_SUPPORTED
                speechOutputReady = languageResult >= TextToSpeech.LANG_AVAILABLE
            } else {
                speechOutputReady = false
            }
        }.also { speechOutputHolder[0] = it }
    }
    var draft by remember { mutableStateOf("") }
    var streamingReply by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Kellvyn pronto. Converse pelo chat principal.") }
    var modelReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var isRemovingModel by remember { mutableStateOf(false) }
    var showConsoleDialog by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showNavigatorDialog by remember { mutableStateOf(false) }
    var showConversationsDialog by remember { mutableStateOf(false) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf("https://developer.android.com") }
    var pendingAgentApproval by remember { mutableStateOf<String?>(null) }
    var agentApprovalGate by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var selectedNav by remember { mutableStateOf("AI") }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var memoryDraft by remember { mutableStateOf("") }
    var memoryStatus by remember { mutableStateOf("") }
    val savedMemories = remember(memoryStore) { mutableStateListOf<MemoryFact>().apply { addAll(memoryStore.list()) } }
    var showProjectsDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<WorkspaceProject?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var activeEditProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var editablePaths by remember { mutableStateOf(emptyList<String>()) }
    var activeFilePath by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var editorStatus by remember { mutableStateOf("") }
    var isSavingFile by remember { mutableStateOf(false) }
    var pendingExportProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var latestBuiltApk by remember(localAndroidCompiler) { mutableStateOf(localAndroidCompiler.mostRecentApk()) }
    var pendingLicenseProjectId by remember { mutableStateOf<String?>(null) }
    var showSdkLicenseDialog by remember { mutableStateOf(false) }
    var pendingLicenseRepairBrief by remember { mutableStateOf<String?>(null) }
    val loadedSdkLicense = remember(context) {
        runCatching {
            context.resources.openRawResource(com.kellvyn.assistant.R.raw.android_sdk_license)
                .bufferedReader().use { it.readText() }
        }.getOrNull()
    }
    val sdkLicenseText = loadedSdkLicense
        ?: "O texto oficial da licença não pôde ser carregado. O aceite fica desativado até o texto estar disponível."

    val existingProjects = remember(workspaceStore) {
        mutableStateListOf<WorkspaceProject>().apply { addAll(workspaceStore.listProjects()) }
    }

    val conversations = remember(conversationStore, historyStore) {
        val stored = conversationStore.load()
        val legacy = historyStore.load()
        val initial = if (stored.isNotEmpty()) {
            stored
        } else if (legacy.isNotEmpty()) {
            val firstPrompt = legacy.firstOrNull { it.fromUser }?.text
            listOf(
                SavedConversation(
                    id = UUID.randomUUID().toString(),
                    title = firstPrompt?.replace('\n', ' ')?.take(48)?.ifBlank { "Conversa anterior" } ?: "Conversa anterior",
                    updatedAt = System.currentTimeMillis(),
                    messages = legacy
                )
            )
        } else emptyList()
        mutableStateListOf<SavedConversation>().apply { addAll(initial) }
    }
    var activeConversationId by remember(conversations) {
        mutableStateOf(conversations.firstOrNull()?.id ?: UUID.randomUUID().toString())
    }
    val messages = remember(activeConversationId) {
        mutableStateListOf<ChatMessage>().apply {
            val selectedConversation = conversations.firstOrNull { it.id == activeConversationId }
            val saved = selectedConversation?.messages ?: if (conversations.isEmpty()) historyStore.load() else emptyList()
            if (saved.isEmpty()) {
                add(ChatMessage(
                    "Kellvyn IA está pronto. O chat principal conversa com o núcleo local; editor e terminal são ferramentas independentes.",
                    false
                ))
            } else {
                addAll(saved.map { ChatMessage(it.text, it.fromUser) })
            }
        }
    }

    val assistantAgent = remember(workspaceStore, localAndroidCompiler) {
        AssistantAgent(
            AgentToolRouter(workspaceStore, localAndroidCompiler) { projectId ->
                pendingLicenseProjectId = projectId
                pendingLicenseRepairBrief = null
                showSdkLicenseDialog = true
                status = "O agente quer compilar um projeto. Leia a licença oficial do Android SDK antes do primeiro download."
            }
        )
    }

    fun saveHistory() {
        val savedMessages = messages.map { ChatHistoryStore.SavedMessage(it.text, it.fromUser) }
        historyStore.save(savedMessages)
        val previous = conversations.firstOrNull { it.id == activeConversationId }
        val firstPrompt = savedMessages.firstOrNull { it.fromUser }?.text
        val title = firstPrompt?.replace('\n', ' ')?.trim()?.take(48)?.ifBlank { null }
            ?: previous?.title ?: "Nova conversa"
        val savedConversation = SavedConversation(
            id = activeConversationId,
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = savedMessages
        )
        conversationStore.save(savedConversation)
        conversations.removeAll { it.id == activeConversationId }
        conversations.add(0, savedConversation)
        while (conversations.size > 30) conversations.removeAt(conversations.lastIndex)
    }

    fun createNewConversation() {
        saveHistory()
        val conversation = SavedConversation(
            id = UUID.randomUUID().toString(),
            title = "Nova conversa",
            updatedAt = System.currentTimeMillis(),
            messages = emptyList()
        )
        conversationStore.save(conversation)
        conversations.removeAll { it.id == conversation.id }
        conversations.add(0, conversation)
        activeConversationId = conversation.id
        showConversationsDialog = false
        status = "Nova conversa criada. As conversas ficam guardadas neste aparelho."
    }

    fun openConversation(id: String) {
        if (id != activeConversationId) saveHistory()
        if (conversations.none { it.id == id }) return
        activeConversationId = id
        showConversationsDialog = false
        status = "Conversa aberta."
    }

    fun buildConversationTurns(): List<AssistantTurn> {
        val recent = messages.takeLast(MAX_CONTEXT_MESSAGES)
        val latestUserIndex = recent.indexOfLast { it.fromUser }
        val memoryContext = memoryStore.promptContext()
        return buildList {
            if (memoryContext.isNotBlank()) {
                add(AssistantTurn(AssistantTurn.Role.SYSTEM, memoryContext))
            }
            recent.forEachIndexed { index, message ->
                if (!message.fromUser && message.text.startsWith("Kellvyn IA está pronto.")) {
                    return@forEachIndexed
                }
                val content = if (index == latestUserIndex) {
                    message.text.take(MAX_INPUT_CHARS)
                } else {
                    message.text.take(MAX_HISTORY_MESSAGE_CHARS)
                }
                add(
                    AssistantTurn(
                        role = if (message.fromUser) AssistantTurn.Role.USER else AssistantTurn.Role.ASSISTANT,
                        content = content
                    )
                )
            }
        }
    }

    suspend fun requestAgentApproval(summary: String): Boolean {
        val gate = CompletableDeferred<Boolean>()
        pendingAgentApproval = summary
        agentApprovalGate = gate
        return try {
            gate.await()
        } finally {
            if (agentApprovalGate === gate) {
                agentApprovalGate = null
                pendingAgentApproval = null
            }
        }
    }

    suspend fun buildProjectAndReport(
        project: WorkspaceProject,
        repairBrief: String? = null,
        repairEngine: AssistantEngine? = null
    ) {
        var result = localAndroidCompiler.build(project.id) { progress ->
            status = progress
        }
        var autoRepaired = false
        if (result is LocalApkBuildResult.Failure &&
            isRepairableSourceBuildFailure(result.reason) &&
            !repairBrief.isNullOrBlank() && repairEngine != null
        ) {
            val firstError = result.reason
            status = "O agente está corrigindo o código a partir dos diagnósticos reais e fará uma única nova build…"
            messages.add(ChatMessage("A primeira compilação falhou. O agente vai analisar os erros e tentar uma correção automática limitada.", false))
            saveHistory()
            val repairFailure = appBuilderAgent.repairJavaProject(
                engine = repairEngine,
                projectId = project.id,
                originalBrief = repairBrief,
                buildError = firstError
            )
            if (repairFailure == null) {
                autoRepaired = true
                result = localAndroidCompiler.build(project.id) { progress ->
                    status = progress
                }
                if (result is LocalApkBuildResult.Failure) {
                    result = LocalApkBuildResult.Failure(
                        "Primeira tentativa:\n" + firstError.take(4_000) +
                            "\n\nApós a correção automática, a nova compilação também falhou:\n" + result.reason.take(8_000)
                    )
                }
            } else {
                result = LocalApkBuildResult.Failure(
                    "Primeira tentativa:\n" + firstError.take(4_000) +
                        "\n\nNão consegui aplicar uma correção segura: " + repairFailure
                )
            }
        }

        when (result) {
            is LocalApkBuildResult.Success -> {
                latestBuiltApk = result.apk
                messages.add(
                    ChatMessage(
                        (if (autoRepaired) "O agente corrigiu o código após o primeiro erro. " else "") +
                            "APK criado, assinado e verificado no próprio celular. Pacote: " + result.applicationId + ". " +
                            "Tempo: " + (result.durationMs / 1000.0) + "s. Arquivo: " + result.apk.name + ". " +
                            "Use o menu superior para instalar o APK.",
                        false
                    )
                )
                status = "APK gerado, assinado e verificado localmente."
                saveHistory()
            }
            is LocalApkBuildResult.Failure -> {
                messages.add(ChatMessage("Diagnóstico real da build local:\n" + result.reason, false))
                status = "A compilação terminou com erro; os diagnósticos estão no chat."
                saveHistory()
            }
            LocalApkBuildResult.LicenseRequired -> {
                pendingLicenseProjectId = project.id
                pendingLicenseRepairBrief = repairBrief
                showSdkLicenseDialog = true
                messages.add(
                    ChatMessage(
                        "Para iniciar o primeiro build, leia os termos oficiais do Android SDK. Nenhum arquivo será baixado antes de sua aceitação.",
                        false
                    )
                )
                status = "Aguardando sua decisão sobre a licença do Android SDK."
                saveHistory()
            }
        }
    }

    fun installLatestApk() {
        val apk = latestBuiltApk?.takeIf { it.isFile }
        if (apk == null) {
            status = "Ainda não há APK gerado para instalar."
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                status = "Autorize a instalação de apps para o Kellvyn e toque em Instalar APK novamente."
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                return
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            status = "Abrindo o instalador do Android para o APK gerado."
        } catch (e: Exception) {
            status = "Não consegui abrir o instalador: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun submitDraft() {
        val text = draft.trim()
        if (text.isEmpty() || isGenerating) return
        if (isKellvynIdentityQuestion(text)) {
            messages.add(ChatMessage(text, true))
            messages.add(ChatMessage(kellvynIdentityAnswer(), false))
            draft = ""
            saveHistory()
            status = "Kellvyn IA — identidade do assistente"
            return
        }
        val explicitTerminalRequest = text.trim().takeIf { it.startsWith("terminal:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.takeIf { it.isNotBlank() && it.length <= 2_000 }
        if (explicitTerminalRequest != null) {
            messages.add(ChatMessage(text, true))
            draft = ""
            isGenerating = true
            status = "Executando o comando pedido no terminal local…"
            saveHistory()
            scope.launch {
                val answer = withContext(Dispatchers.IO) {
                    runCatching {
                        val command = explicitTerminalRequest
                        val result = workspaceTerminal.executeInteractive(command)
                        workspaceTerminal.saveOutput((workspaceTerminal.storedOutput() + "\n\n$ " + command + "\n" + result).takeLast(48_000))
                        result
                    }.getOrElse { "Falha no terminal: " + (it.message ?: it.javaClass.simpleName) }
                }
                messages.add(ChatMessage(answer, false))
                saveHistory()
                status = "Comando concluído no terminal."
                isGenerating = false
            }
            return
        }
        val existingBuildOnly = ChatRequestRouter.shouldCompileExistingProject(text)
        if (!modelReady && !existingBuildOnly) {
            messages.add(ChatMessage(text, true))
            messages.add(ChatMessage("O núcleo de IA local ainda não está inicializado neste aparelho. O chat não depende de provedor externo; para responder de verdade, a build precisa incluir um modelo local compatível.", false))
            saveHistory()
            draft = ""
            status = "Núcleo local não inicializado."
            return
        }

        messages.add(ChatMessage(text, true))
        saveHistory()
        draft = ""
        val wantsExistingBuild = ChatRequestRouter.shouldCompileExistingProject(text)
        val wantsBuild = !wantsExistingBuild &&
            ChatRequestRouter.shouldBuild(text, buildModeSelected = false)
        val wantsCoder = wantsBuild || wantsExistingBuild || ChatRequestRouter.shouldPreferCoder(text)
        isGenerating = true
        streamingReply = ""
        status = if (wantsBuild) {
            "Planejando e gerando arquivos do projeto com o motor selecionado…"
        } else if (wantsExistingBuild) {
            "Selecionando o projeto e iniciando a compilação local…"
        } else {
            "Gerando resposta no aparelho…"
        }

        val selectedEngine: AssistantEngine = localEngine
        scope.launch {
            try {
                if (wantsBuild) {
                    when (val result = appBuilderAgent.createProject(selectedEngine, text)) {
                        is AppBuildResult.Success -> {
                            val project = result.project
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            if (appBuilderAgent.isBotProjectRequest(text)) {
                                messages.add(
                                    ChatMessage(
                                        "O código-fonte do bot \"${project.name}\" foi salvo em Apps. Abra o projeto e exporte o ZIP para executar em um ambiente com Node.js e as credenciais oficiais da plataforma. Este celular ainda não executa Node.js nem mantém o bot conectado em segundo plano; não vou afirmar que o bot está online.",
                                        false
                                    )
                                )
                                status = "Projeto de bot salvo; código-fonte disponível para exportação em Apps."
                            } else {
                                messages.add(
                                    ChatMessage(
                                        "Projeto \"${project.name}\" criado e validado estruturalmente com ${project.fileCount} arquivos. Vou iniciar a compilação local em segundo plano.",
                                        false
                                    )
                                )
                                status = "Projeto salvo. Iniciando o compilador local…"
                                buildProjectAndReport(project, repairBrief = text, repairEngine = selectedEngine)
                            }
                        }
                        is AppBuildResult.Failure -> {
                            messages.add(ChatMessage("Não consegui concluir a geração do projeto: ${result.reason}", false))
                            status = result.reason
                        }
                    }
                } else if (wantsExistingBuild) {
                    val projects = withContext(Dispatchers.IO) { workspaceStore.listProjects() }
                    val lowerRequest = text.lowercase(Locale.ROOT)
                    val named = projects.filter { project ->
                        lowerRequest.contains(project.id.lowercase(Locale.ROOT)) ||
                            (project.name.isNotBlank() && lowerRequest.contains(project.name.lowercase(Locale.ROOT)))
                    }
                    val selectedProject = when {
                        named.size == 1 -> named.first()
                        named.size > 1 -> null
                        projects.size == 1 -> projects.first()
                        else -> null
                    }
                    if (selectedProject == null) {
                        val reply = when {
                            projects.isEmpty() -> "Ainda não há projeto salvo para compilar. Peça ao Kellvyn para criar um aplicativo pelo chat."
                            else -> "Há vários projetos. Escreva o nome ou ID do projeto que deseja compilar, por exemplo: 'compile o APK de " + projects.first().name + "'."
                        }
                        messages.add(ChatMessage(reply, false))
                        status = reply
                    } else if (
                        withContext(Dispatchers.IO) {
                            val paths = workspaceStore.listFiles(selectedProject.id).toSet()
                            "package.json" in paths && "src/index.js" in paths
                        }
                    ) {
                        val answer = "Este projeto é um bot Node.js, não um app Android. O compilador local de APK não executa package.json. Abra Apps, exporte o ZIP e rode o projeto em um ambiente com Node.js e as credenciais oficiais; o Kellvyn não vai fingir que o bot está online."
                        messages.add(ChatMessage(answer, false))
                        status = "Projeto de bot identificado; APK Android não se aplica a esse código-fonte."
                    } else {
                        messages.add(ChatMessage("Encontrei o projeto \"" + selectedProject.name + "\". Iniciando o build local em segundo plano.", false))
                        val repairEngineForBuild = if (
                            modelReady
                        ) selectedEngine else null
                        buildProjectAndReport(
                            selectedProject,
                            repairBrief = text,
                            repairEngine = repairEngineForBuild
                        )
                    }
                } else {
                    val turns = buildConversationTurns()
                    val editorProjectIdBefore = activeEditProject?.id
                    val editorPathBefore = activeFilePath
                    val editorDraftBefore = editorContent
                    val editorPathWasListedBefore = editorPathBefore in editablePaths
                    val savedEditorBeforeResult = if (editorProjectIdBefore != null && editorPathBefore.isNotBlank()) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                workspaceStore.readFile(editorProjectIdBefore, editorPathBefore)
                            }
                        }
                    } else null
                    val savedEditorBefore = savedEditorBeforeResult?.getOrNull()
                    val agentResult = if (wantsCoder) {
                        assistantAgent.respond(
                            engine = selectedEngine,
                            conversation = turns,
                            internetAllowed = false,
                            onToolStatus = { toolStatus -> status = toolStatus },
                            requestApproval = { summary -> requestAgentApproval(summary) }
                        )
                    } else {
                        // Everyday conversation skips the agent JSON/tool loop and calls the local model directly.
                        localEngine.replyStreaming(turns) { partial ->
                            withContext(Dispatchers.Main.immediate) { streamingReply = partial }
                        }
                    }
                    val refreshedProjects = withContext(Dispatchers.IO) { workspaceStore.listProjects() }
                    latestBuiltApk = localAndroidCompiler.mostRecentApk() ?: latestBuiltApk
                    existingProjects.clear()
                    existingProjects.addAll(refreshedProjects)

                    // Refresh the open editor after chat-driven file edits, but never discard a draft.
                    val editorProjectAfter = editorProjectIdBefore?.let { id ->
                        refreshedProjects.firstOrNull { it.id == id }
                    }
                    if (
                        editorProjectAfter != null &&
                        activeEditProject?.id == editorProjectIdBefore &&
                        activeFilePath == editorPathBefore
                    ) {
                        activeEditProject = editorProjectAfter
                        val refreshedEditablePaths = runCatching {
                            withContext(Dispatchers.IO) {
                                workspaceStore.listFiles(editorProjectAfter.id)
                            }
                        }.getOrNull()
                        if (refreshedEditablePaths != null) editablePaths = refreshedEditablePaths
                        val editorPathStillListed = editorPathBefore in (refreshedEditablePaths ?: editablePaths)
                        val savedEditorAfterResult = if (editorPathStillListed) {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    workspaceStore.readFile(editorProjectAfter.id, editorPathBefore)
                                }
                            }
                        } else null
                        val savedEditorAfter = savedEditorAfterResult?.getOrNull()

                        when {
                            editorPathWasListedBefore && savedEditorBeforeResult?.isFailure == true -> {
                                editorStatus = "Não consegui ler com segurança a versão salva antes da resposta; preservei o rascunho e não sincronizei automaticamente."
                            }
                            editorPathStillListed && savedEditorAfterResult?.isFailure == true -> {
                                editorStatus = "Não consegui reler a versão salva após a resposta. Preservei o conteúdo aberto; tente reabrir o arquivo para sincronizar."
                            }
                            refreshedEditablePaths == null && editorPathWasListedBefore && !editorPathStillListed -> {
                                editorStatus = "Não consegui atualizar a lista de arquivos; preservei o editor porque não foi possível confirmar se o arquivo foi removido."
                            }
                            else -> when (
                                EditorSyncPolicy.decide(
                                    savedBefore = savedEditorBefore,
                                    savedAfter = savedEditorAfter,
                                    draftBefore = editorDraftBefore,
                                    draftNow = editorContent
                                )
                            ) {
                                EditorSyncPolicy.Action.NO_CHANGE -> Unit
                                EditorSyncPolicy.Action.REFRESH_SAVED_CONTENT -> {
                                    editorContent = savedEditorAfter.orEmpty().take(MAX_EDITOR_CHARS)
                                    editorStatus = "Arquivo sincronizado após edição pelo chat. O editor mostra a versão salva mais recente."
                                }
                                EditorSyncPolicy.Action.PRESERVE_LOCAL_DRAFT -> {
                                    editorStatus = "A IA alterou a versão salva deste arquivo, mas mantive seu rascunho aberto para não apagar alterações não salvas. Reabra o arquivo para comparar."
                                }
                                EditorSyncPolicy.Action.CLEAR_REMOVED_FILE -> {
                                    activeFilePath = ""
                                    editorContent = ""
                                    editorStatus = "O agente removeu o arquivo salvo após a autorização; selecione outro arquivo na lista."
                                }
                                EditorSyncPolicy.Action.PRESERVE_DRAFT_FOR_REMOVED_FILE -> {
                                    editorStatus = "O agente removeu o arquivo salvo, mas preservei seu rascunho não salvo. Não salve novamente sem conferir se deseja recriar o arquivo."
                                }
                            }
                        }
                    }
                    if (activeEditProject != null && refreshedProjects.none { it.id == activeEditProject?.id }) {
                        showEditorDialog = false
                        activeEditProject = null
                        editablePaths = emptyList()
                        activeFilePath = ""
                        editorContent = ""
                    }
                    when (val result = agentResult) {
                        is AssistantResult.Success -> {
                            messages.add(ChatMessage(result.text, false))
                            status = when {
                                else -> "Resposta gerada localmente • sem envio a provedores externos"
                            }
                        }
                        is AssistantResult.Unavailable -> {
                            messages.add(ChatMessage("IA indisponível: ${result.reason}", false))
                            status = result.reason
                        }
                        is AssistantResult.Failure -> {
                            messages.add(ChatMessage("Falha da IA: ${result.reason}", false))
                            status = result.reason
                        }
                    }
                }
                saveHistory()
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                messages.add(ChatMessage("Falha inesperada: $reason", false))
                saveHistory()
                status = "Falha inesperada: $reason"
            } finally {
                streamingReply = ""
                isGenerating = false
            }
        }
    }

    fun updateModelResult(result: AssistantResult) {
        when (result) {
            is AssistantResult.Success -> {
                modelReady = true
                status = result.text
            }
            is AssistantResult.Unavailable -> {
                modelReady = false
                status = result.reason
            }
            is AssistantResult.Failure -> {
                modelReady = false
                status = result.reason
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                draft = (draft + " " + spoken).trim().take(MAX_INPUT_CHARS)
                status = "Texto reconhecido. Revise antes de enviar."
            } else {
                status = "Nenhuma fala foi reconhecida."
            }
        }
    }

    val zipExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        val project = pendingExportProject
        pendingExportProject = null
        if (destination != null && project != null) {
            scope.launch {
                status = "Exportando ${project.name} para ZIP…"
                try {
                    withContext(Dispatchers.IO) { workspaceStore.exportZip(project.id, destination) }
                    status = "ZIP de ${project.name} exportado. Revise o código antes de executar."
                } catch (e: Exception) {
                    status = "Falha ao exportar projeto: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    LaunchedEffect(localEngine) {
        isLoadingModel = true
        try {
            localEngine.recoverInterruptedModelSwap()
            if (localEngine.hasSavedModel()) {
                status = "Modelo local encontrado; inicializando…"
                updateModelResult(localEngine.loadSavedModel())
            }
        } catch (e: Exception) {
            status = "Não foi possível recuperar/carregar o modelo local: " +
                (e.message ?: e.javaClass.simpleName)
        } finally {
            isLoadingModel = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(streamingReply.length) {
        if (streamingReply.isNotBlank()) {
            chatListState.animateScrollToItem(messages.size)
        }
    }

    DisposableEffect(localEngine, speechOutput) {
        onDispose {
            localEngine.close()
            speechOutput.stop()
            speechOutput.shutdown()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Night,
            surface = Panel,
            primary = Neon,
            secondary = Neon,
            onPrimary = Night,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Night).padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showQuickMenu = true }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                    Text("☰", color = Neon, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Kellvyn Assistente", color = Neon, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (modelReady) "KELLVYN IA • LOCAL" else "KELLVYN IA • INICIALIZANDO",
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = showQuickMenu,
                    onDismissRequest = { showQuickMenu = false },
                    modifier = Modifier.background(Panel)
                ) {
                    DropdownMenuItem(text = { Text("Conversas") }, onClick = { showQuickMenu = false; showConversationsDialog = true })
                }
            }

            Divider(color = Color(0xFF143D33), thickness = 1.dp)
            LazyColumn(
                state = chatListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 11.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = if (message.fromUser) Color(0xFF0A2D24) else Night,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (message.fromUser) Color(0xFF14634D) else Color(0xFF0B3329)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                message.text,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                color = if (message.fromUser) Color(0xFFE1FFF5) else Color(0xFF9ED7C8),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                        if (!message.fromUser) {
                            val clipboard = LocalClipboardManager.current
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(message.text))
                                        status = "Resposta copiada para a área de transferência."
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(15.dp)) {
                                        val stroke = Stroke(width = 1.4.dp.toPx())
                                        drawRoundRect(
                                            color = Neon.copy(alpha = 0.65f),
                                            topLeft = Offset(size.width * 0.27f, size.height * 0.08f),
                                            size = Size(size.width * 0.58f, size.height * 0.68f),
                                            cornerRadius = CornerRadius(2.dp.toPx()),
                                            style = stroke
                                        )
                                        drawRoundRect(
                                            color = Neon,
                                            topLeft = Offset(size.width * 0.12f, size.height * 0.27f),
                                            size = Size(size.width * 0.58f, size.height * 0.65f),
                                            cornerRadius = CornerRadius(2.dp.toPx()),
                                            style = stroke
                                        )
                                    }
                                    Spacer(Modifier.width(5.dp))
                                    Text("Copiar", color = Neon, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                if (streamingReply.isNotBlank()) {
                    item(key = "kellvyn_streaming_reply") {
                        Surface(
                            color = Night,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0B3329)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                streamingReply + " ▍",
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                color = Color(0xFF9ED7C8),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            if (latestBuiltApk?.isFile == true) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APK pronto", color = Neon, fontSize = 9.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { installLatestApk() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("Instalar", color = Neon, fontSize = 10.sp) }
                }
            }

            if (status.isNotBlank()) {
                Text(status, color = Muted, fontSize = 9.sp, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp))
            }

            Surface(
                color = Color(0xFF030D0A),
                shape = RoundedCornerShape(17.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A7660)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_INPUT_CHARS) draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                        placeholder = { Text("Converse ou peça para criar um aplicativo…", color = Muted, fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 1,
                        maxLines = 4,
                        enabled = !isGenerating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Neon
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {


                        Spacer(Modifier.weight(1f))

                        Button(
                            enabled = !isGenerating,
                            onClick = { submitDraft() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Night)
                        ) { Text(if (isGenerating) "…" else "↑", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { selectedNav = "AI" },
                    contentAlignment = Alignment.Center
                ) { Text("✧ IA", color = if (selectedNav == "AI") Neon else Muted, fontSize = 10.sp) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { selectedNav = "Navegador"; showNavigatorDialog = true },
                    contentAlignment = Alignment.Center
                ) { Text("◎ Web", color = if (selectedNav == "Navegador") Neon else Muted, fontSize = 10.sp) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable {
                        selectedNav = "Editor"
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        showProjectsDialog = true
                    },
                    contentAlignment = Alignment.Center
                ) { Text("</> Edit.", color = if (selectedNav == "Editor") Neon else Muted, fontSize = 9.sp) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable {
                        selectedNav = "Terminal"
                        showConsoleDialog = true
                    },
                    contentAlignment = Alignment.Center
                ) { Text(">_ Term.", color = if (selectedNav == "Terminal") Neon else Muted, fontSize = 9.sp) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable {
                        selectedNav = "Git"
                        showConsoleDialog = true
                    },
                    contentAlignment = Alignment.Center
                ) { Text("⑂ Git", color = if (selectedNav == "Git") Neon else Muted, fontSize = 10.sp) }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable {
                        selectedNav = "Apps"
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        showAppsDialog = true
                    },
                    contentAlignment = Alignment.Center
                ) { Text("▦ Apps", color = if (selectedNav == "Apps") Neon else Muted, fontSize = 10.sp) }
            }
        }

        if (showEditorDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!isSavingFile) showEditorDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                Surface(Modifier.fillMaxSize().padding(6.dp), color = Night, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164638))) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("EDITOR DE CÓDIGO", color = Neon, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(activeEditProject?.name ?: "Projeto", color = Muted, fontSize = 10.sp, maxLines = 1)
                            }
                            TextButton(enabled = !isSavingFile, onClick = { showEditorDialog = false }) { Text("Fechar", color = Color.White) }
                        }
                        Text("ARQUIVOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Surface(Modifier.fillMaxWidth().heightIn(max = 100.dp), color = Panel, shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(3.dp)) {
                                editablePaths.forEach { path ->
                                    TextButton(onClick = {
                                        val project = activeEditProject
                                        if (project != null) {
                                            try {
                                                val fileText = workspaceStore.readFile(project.id, path)
                                                if (fileText.length > MAX_EDITOR_CHARS) editorStatus = "Arquivo muito grande para editar aqui. Exporte o ZIP."
                                                else { activeFilePath = path; editorContent = fileText; editorStatus = "" }
                                            } catch (e: Exception) { editorStatus = "Falha ao abrir: " + (e.message ?: e.javaClass.simpleName) }
                                        }
                                    }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                        Text((if (path == activeFilePath) "▸ " else "") + path, color = if (path == activeFilePath) Neon else Color.White, fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (activeFilePath.isBlank()) {
                            Text("Escolha um arquivo na lista.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        } else {
                            Text(activeFilePath, color = Neon, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            OutlinedTextField(
                                value = editorContent,
                                onValueChange = { if (it.length <= MAX_EDITOR_CHARS) editorContent = it },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
                                minLines = 1,
                                maxLines = 50,
                                label = { Text("Código") },
                                placeholder = { Text("Conteúdo do arquivo…") }
                            )
                        }
                        if (editorStatus.isNotBlank()) Text(editorStatus, color = if (editorStatus.startsWith("Arquivo salvo")) Neon else Warning, fontSize = 10.sp, maxLines = 3)
                        if (isSavingFile) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !isSavingFile && activeEditProject != null, onClick = { activeEditProject?.let { project -> pendingExportProject = project; zipExportPicker.launch(project.id + ".zip") } }) { Text("Exportar ZIP", color = Muted, fontSize = 10.sp) }
                            Spacer(Modifier.weight(1f))
                            Button(enabled = !isSavingFile && activeEditProject != null && activeFilePath.isNotBlank(), onClick = {
                                val project = activeEditProject
                                if (project != null) {
                                    isSavingFile = true
                                    editorStatus = "Salvando…"
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { workspaceStore.addOrReplaceFiles(project.id, listOf(ProjectFileInput(activeFilePath, editorContent))) }
                                            existingProjects.clear(); existingProjects.addAll(workspaceStore.listProjects())
                                            activeEditProject = workspaceStore.getProject(project.id)
                                            editorStatus = "Arquivo salvo. Para compilar, peça ao chat principal para gerar ou corrigir o APK."
                                        } catch (e: Exception) { editorStatus = "Falha ao salvar: " + (e.message ?: e.javaClass.simpleName) }
                                        finally { isSavingFile = false }
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Night)) { Text("Salvar", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        if (showProjectsDialog) {
            AlertDialog(
                onDismissRequest = { showProjectsDialog = false },
                title = { Text("Meus projetos") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (existingProjects.isEmpty()) {
                            Text("Nenhum projeto salvo para editar. Peça ao chat principal para criar o app; o editor só altera arquivos existentes.", color = Muted)
                        } else {
                            existingProjects.forEach { project ->
                                Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(project.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("${project.fileCount} arquivos · ${project.id}", color = Muted, fontSize = 10.sp)
                                        if (project.description.isNotBlank()) {
                                            Text(project.description, color = Muted, fontSize = 11.sp, maxLines = 3)
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = {
                                                try {
                                                    val paths = workspaceStore.listFiles(project.id)
                                                    activeEditProject = project
                                                    editablePaths = paths
                                                    activeFilePath = paths.firstOrNull().orEmpty()
                                                    editorContent = if (activeFilePath.isNotBlank()) {
                                                        workspaceStore.readFile(project.id, activeFilePath).take(MAX_EDITOR_CHARS)
                                                    } else ""
                                                    editorStatus = if (activeFilePath.isBlank()) "Projeto sem arquivos editáveis." else ""
                                                    showProjectsDialog = false
                                                    showEditorDialog = true
                                                } catch (e: Exception) {
                                                    status = "Não foi possível abrir o editor: " + (e.message ?: e.javaClass.simpleName)
                                                }
                                            }) { Text("Editor") }
                                            TextButton(onClick = {
                                                pendingExportProject = project
                                                zipExportPicker.launch("${project.id}.zip")
                                            }) { Text("Exportar ZIP") }
                                            TextButton(onClick = { projectToDelete = project }) { Text("Apagar", color = Warning) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProjectsDialog = false }) { Text("Fechar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showProjectsDialog = false
                    }) { Text("Voltar ao chat") }
                }
            )
        }

        if (projectToDelete != null) {
            val targetProject = projectToDelete
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = { Text("Apagar projeto?") },
                text = { Text("O projeto ${targetProject?.name.orEmpty()} e seus arquivos locais serão removidos deste aparelho.") },
                confirmButton = {
                    TextButton(onClick = {
                        val target = projectToDelete
                        if (target != null) {
                            try {
                                workspaceStore.deleteProject(target.id)
                                existingProjects.removeAll { it.id == target.id }
                                if (activeEditProject?.id == target.id) {
                                    showEditorDialog = false
                                    activeEditProject = null
                                    editablePaths = emptyList()
                                    activeFilePath = ""
                                    editorContent = ""
                                }
                                status = "Projeto ${target.name} apagado do armazenamento local."
                            } catch (e: Exception) {
                                status = "Falha ao apagar projeto: " + (e.message ?: e.javaClass.simpleName)
                            }
                        }
                        projectToDelete = null
                    }) { Text("Apagar definitivamente", color = Warning) }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) { Text("Cancelar") }
                }
            )
        }

        if (showNavigatorDialog) {
            AlertDialog(
                onDismissRequest = { showNavigatorDialog = false },
                title = { Text("Navegador e pesquisa") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Abre no navegador padrão do Android (Chrome, Brave ou outro instalado). Digite um endereço ou uma frase para pesquisar.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(
                            value = browserUrl,
                            onValueChange = { browserUrl = it.take(2_000) },
                            label = { Text("Endereço ou pesquisa") },
                            placeholder = { Text("github.com ou como criar um app Android") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val typed = browserUrl.trim()
                        if (typed.isBlank()) {
                            status = "Digite um endereço ou uma pesquisa."
                        } else {
                            val isUrl = typed.startsWith("http://", true) || typed.startsWith("https://", true) || (typed.contains(".") && !typed.any { it.isWhitespace() })
                            val target = if (isUrl) {
                                if (typed.startsWith("http://", true) || typed.startsWith("https://", true)) typed else "https://" + typed
                            } else {
                                "https://www.google.com/search?q=" + Uri.encode(typed)
                            }
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                status = if (isUrl) "Site aberto no navegador padrão." else "Pesquisa aberta no navegador padrão."
                                showNavigatorDialog = false
                            } catch (e: Exception) {
                                status = "Não consegui abrir o navegador: " + (e.message ?: e.javaClass.simpleName)
                            }
                        }
                    }) { Text("Pesquisar / Abrir") }
                },
                dismissButton = { TextButton(onClick = { showNavigatorDialog = false }) { Text("Fechar") } }
            )
        }
        if (showConversationsDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showConversationsDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                Surface(Modifier.fillMaxSize().padding(8.dp), color = Night, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CONVERSAS", color = Neon, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showConversationsDialog = false }) { Text("Fechar", color = Color.White) }
                        }
                        Button(onClick = { createNewConversation() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Night)) { Text("＋ Nova conversa") }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (conversations.isEmpty()) Text("Nenhuma conversa salva ainda.", color = Muted, fontSize = 12.sp)
                            else conversations.sortedByDescending { it.updatedAt }.forEach { conversation ->
                                Surface(Modifier.fillMaxWidth(), color = if (conversation.id == activeConversationId) Color(0xFF0A2D24) else Panel, shape = RoundedCornerShape(10.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(conversation.title, color = Color.White, fontSize = 12.sp, maxLines = 2)
                                            Text(conversation.messages.count { it.fromUser }.toString() + " mensagens suas", color = Muted, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = { openConversation(conversation.id) }) { Text(if (conversation.id == activeConversationId) "Atual" else "Abrir", color = Neon) }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { showConversationsDialog = false; savedMemories.clear(); savedMemories.addAll(memoryStore.list()); memoryDraft = ""; memoryStatus = ""; showMemoryDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Memória", color = Muted) }
                    }
                }
            }
        }
        if (showAppsDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showAppsDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                Surface(Modifier.fillMaxSize().padding(8.dp), color = Night, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("MEUS APPS", color = Neon, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Projetos criados pelo chat principal", color = Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { showAppsDialog = false }) { Text("Fechar", color = Color.White) }
                        }
                        if (existingProjects.isEmpty()) {
                            Text("Ainda não há aplicativos criados. Peça ao chat principal para criar um; o editor serve só para editar arquivos.", color = Muted, fontSize = 13.sp)
                        } else {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                existingProjects.forEach { project ->
                                    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164638))) {
                                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(Modifier.size(50.dp), color = Color(0xFF0A2D24), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Neon.copy(alpha = 0.5f))) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(projectIconGlyph(project), color = Neon, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(project.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                                Text(project.description.ifBlank { project.fileCount.toString() + " arquivos" }, color = Muted, fontSize = 10.sp, maxLines = 2)
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    TextButton(onClick = {
                                                        try {
                                                            val paths = workspaceStore.listFiles(project.id)
                                                            activeEditProject = project
                                                            editablePaths = paths
                                                            activeFilePath = paths.firstOrNull().orEmpty()
                                                            editorContent = if (activeFilePath.isNotBlank()) workspaceStore.readFile(project.id, activeFilePath).take(MAX_EDITOR_CHARS) else ""
                                                            editorStatus = if (activeFilePath.isBlank()) "Projeto sem arquivos editáveis." else ""
                                                            showAppsDialog = false
                                                            showEditorDialog = true
                                                        } catch (e: Exception) { status = "Falha ao abrir o código: " + (e.message ?: e.javaClass.simpleName) }
                                                    }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("Código", color = Neon, fontSize = 10.sp) }
                                                    TextButton(onClick = { pendingExportProject = project; zipExportPicker.launch(project.id + ".zip") }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("ZIP", color = Muted, fontSize = 10.sp) }
                                                }
                                            }
                                            TextButton(enabled = !isGenerating, onClick = {
                                                showAppsDialog = false
                                                scope.launch {
                                                    isGenerating = true
                                                    status = "Compilando " + project.name + " localmente…"
                                                    try { buildProjectAndReport(project) } finally { isGenerating = false }
                                                }
                                            }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("APK", color = Neon, fontSize = 10.sp) }
                                        }
                                    }
                                }
                            }
                        }
                        Text("Os cartões mostram os projetos salvos; a compilação do APK é feita no aparelho.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        if (showMemoryDialog) {
            AlertDialog(
                onDismissRequest = { showMemoryDialog = false },
                title = { Text("Memória do Kellvyn") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Salve apenas fatos que você quer que o assistente lembre. As memórias ficam neste aparelho e são usadas pelo agente local.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = memoryDraft,
                            onValueChange = { if (it.length <= 1000) memoryDraft = it },
                            label = { Text("Fato para lembrar") },
                            placeholder = { Text("Ex.: prefiro respostas em português e diretas.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                        if (memoryStatus.isNotBlank()) {
                            Text(memoryStatus, color = Neon, fontSize = 12.sp)
                        }
                        Text("MEMÓRIAS SALVAS (${savedMemories.size}/80)", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (savedMemories.isEmpty()) {
                            Text("Nenhuma memória salva ainda.", color = Muted, fontSize = 12.sp)
                        } else {
                            savedMemories.forEach { fact ->
                                Surface(color = Panel, shape = RoundedCornerShape(10.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(fact.text, color = Color.White, fontSize = 13.sp)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = {
                                                memoryStore.remove(fact.id)
                                                savedMemories.removeAll { it.id == fact.id }
                                                memoryStatus = "Memória removida."
                                            }) { Text("Remover", color = Warning, fontSize = 11.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = memoryDraft.trim().isNotEmpty(),
                        onClick = {
                            try {
                                val fact = memoryStore.add(memoryDraft)
                                savedMemories.add(fact)
                                memoryDraft = ""
                                memoryStatus = "Memória salva neste aparelho."
                            } catch (e: Exception) {
                                memoryStatus = e.message ?: "Não foi possível salvar a memória."
                            }
                        }
                    ) { Text("Guardar memória") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            memoryStore.clear()
                            savedMemories.clear()
                            memoryStatus = "Todas as memórias foram apagadas."
                        }) { Text("Apagar tudo", color = Warning) }
                        TextButton(onClick = { showMemoryDialog = false }) { Text("Fechar") }
                    }
                }
            )
        }

        if (showConsoleDialog) {
            WorkspaceConsoleDialog(
                workspace = workspaceStore,
                terminal = workspaceTerminal,
                onDismiss = { showConsoleDialog = false }
            )
        }

        if (showSdkLicenseDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSdkLicenseDialog = false
                    pendingLicenseProjectId = null
                    pendingLicenseRepairBrief = null
                    status = "Licença não aceita; o download da plataforma e a compilação foram cancelados."
                },
                title = { Text("Licença oficial do Android SDK") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Leia os termos completos abaixo. O Kellvyn só baixa a plataforma Android se você aceitar explicitamente.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Text(
                            sdkLicenseText,
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !loadedSdkLicense.isNullOrBlank(),
                        onClick = {
                            val projectId = pendingLicenseProjectId
                            val repairBrief = pendingLicenseRepairBrief
                            val repairEngine = localEngine
                            localAndroidCompiler.acceptSdkLicense()
                            showSdkLicenseDialog = false
                            pendingLicenseProjectId = null
                            pendingLicenseRepairBrief = null
                            val project = projectId?.let { workspaceStore.getProject(it) }
                            if (project == null) {
                                status = "O projeto que aguardava a licença não foi encontrado."
                            } else {
                                scope.launch {
                                    isGenerating = true
                                    try {
                                        buildProjectAndReport(project, repairBrief, if (repairBrief != null) repairEngine else null)
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            }
                        }
                    ) { Text("Li e aceito", color = Neon) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSdkLicenseDialog = false
                            pendingLicenseProjectId = null
                            pendingLicenseRepairBrief = null
                            status = "Licença não aceita; nenhum download foi iniciado."
                        }
                    ) { Text("Não aceitar", color = Warning) }
                }
            )
        }

        if (pendingAgentApproval != null) {
            AlertDialog(
                onDismissRequest = {
                    agentApprovalGate?.complete(false)
                },
                title = { Text("Autorizar ação do Kellvyn?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "A IA solicitou uma operação que altera arquivos ou o Git. Ela só será executada no workspace privado deste aplicativo depois da sua confirmação.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Surface(
                            color = Panel,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164638))
                        ) {
                            Text(
                                pendingAgentApproval.orEmpty(),
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                        Text("Não é um shell do Android: comandos arbitrários e encadeamento de comandos são bloqueados.", color = Muted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        agentApprovalGate?.complete(true)
                    }) { Text("Autorizar", color = Neon) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        agentApprovalGate?.complete(false)
                    }) { Text("Cancelar", color = Warning) }
                }
            )
        }

    }
}