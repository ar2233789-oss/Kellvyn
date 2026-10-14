package com.kellvyn.assistant

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.kellvyn.assistant.workspace.AppBuilderAgent
import com.kellvyn.assistant.workspace.EditorSyncPolicy
import com.kellvyn.assistant.workspace.AppBuildResult
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.WorkspaceProject
import com.kellvyn.assistant.workspace.LocalAndroidCompiler
import com.kellvyn.assistant.workspace.LocalApkBuildResult
import com.kellvyn.assistant.runtime.DiscordBotCredentialsStore
import com.kellvyn.assistant.runtime.DiscordBotRuntimeService
import com.kellvyn.assistant.runtime.TelegramBotCredentialsStore
import com.kellvyn.assistant.runtime.TelegramBotRuntimeService
import com.kellvyn.assistant.runtime.GeneratedProjectClassifier
import com.kellvyn.assistant.runtime.GeneratedProjectKind
import com.kellvyn.assistant.runtime.GeneratedProjectRuntimeStore
import java.io.ByteArrayOutputStream
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
private const val MAX_IMPORTED_FILE_BYTES = 256 * 1024
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

@Composable
private fun KellvynMark(diameter: androidx.compose.ui.unit.Dp = 28.dp, accent: Color = Neon) {
    Canvas(modifier = Modifier.size(diameter)) {
        val d = this.size.minDimension
        val r = d * 0.46f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = Color(0xFF04110D), radius = r, center = center)
        drawCircle(color = accent.copy(alpha = 0.72f), radius = r * 0.94f, center = center, style = Stroke(width = 1.2.dp.toPx()))

        val hood = Path().apply {
            moveTo(center.x, center.y - r * 0.78f)
            cubicTo(center.x - r * 0.67f, center.y - r * 0.45f, center.x - r * 0.58f, center.y + r * 0.33f, center.x - r * 0.25f, center.y + r * 0.58f)
            quadraticTo(center.x, center.y + r * 0.76f, center.x + r * 0.25f, center.y + r * 0.58f)
            cubicTo(center.x + r * 0.58f, center.y + r * 0.33f, center.x + r * 0.67f, center.y - r * 0.45f, center.x, center.y - r * 0.78f)
            close()
        }
        drawPath(path = hood, color = Color(0xFF0D4939))
        val face = Path().apply {
            moveTo(center.x, center.y - r * 0.37f)
            lineTo(center.x - r * 0.28f, center.y - r * 0.03f)
            lineTo(center.x - r * 0.19f, center.y + r * 0.28f)
            quadraticTo(center.x, center.y + r * 0.40f, center.x + r * 0.19f, center.y + r * 0.28f)
            lineTo(center.x + r * 0.28f, center.y - r * 0.03f)
            close()
        }
        drawPath(path = face, color = Night)
        drawLine(color = accent, start = Offset(center.x - r * 0.19f, center.y + r * 0.02f), end = Offset(center.x - r * 0.055f, center.y + r * 0.06f), strokeWidth = 1.3.dp.toPx())
        drawLine(color = accent, start = Offset(center.x + r * 0.055f, center.y + r * 0.06f), end = Offset(center.x + r * 0.19f, center.y + r * 0.02f), strokeWidth = 1.3.dp.toPx())
        drawCircle(color = accent.copy(alpha = 0.12f), radius = r * 0.12f, center = Offset(center.x, center.y + r * 0.30f))
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
    val appBuilderAgent = remember(workspaceStore) { AppBuilderAgent(workspaceStore) }
    val localAndroidCompiler = remember(context, workspaceStore) { LocalAndroidCompiler(context, workspaceStore) }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var streamingReply by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Kellvyn pronto. Converse pelo chat principal.") }
    var modelReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showConversationsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val uiPreferences = remember(context) { context.getSharedPreferences("kellvyn_ui", Context.MODE_PRIVATE) }
    var accentHex by remember(uiPreferences) { mutableStateOf(uiPreferences.getString("accent_hex", "#38E6BC") ?: "#38E6BC") }
    val accentColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var pendingAgentApproval by remember { mutableStateOf<String?>(null) }
    var agentApprovalGate by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
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
    val discordCredentialsStore = remember(context) { DiscordBotCredentialsStore(context) }
    val telegramCredentialsStore = remember(context) { TelegramBotCredentialsStore(context) }
    val projectRuntimeStore = remember(context) { GeneratedProjectRuntimeStore(context) }
    val activeRuntimeProjects = remember(projectRuntimeStore) {
        mutableStateListOf<String>().apply { addAll(projectRuntimeStore.activeProjectIds()) }
    }
    var showDiscordConfigDialog by remember { mutableStateOf(false) }
    var discordConfigProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var discordTokenDraft by remember { mutableStateOf("") }
    var discordApplicationIdDraft by remember { mutableStateOf("") }
    var discordSetupError by remember { mutableStateOf("") }
    var pendingDiscordStartId by remember { mutableStateOf<String?>(null) }
    var showTelegramConfigDialog by remember { mutableStateOf(false) }
    var telegramConfigProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var telegramTokenDraft by remember { mutableStateOf("") }
    var telegramSetupError by remember { mutableStateOf("") }
    var pendingTelegramStartId by remember { mutableStateOf<String?>(null) }
    var pendingFileExportContent by remember { mutableStateOf<String?>(null) }
    var pendingFileExportName by remember { mutableStateOf("") }
    var pendingApkExport by remember { mutableStateOf<File?>(null) }
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

    fun startTelegramRuntimeNow(projectId: String) {
        try {
            TelegramBotRuntimeService.startTelegram(context, projectId)
            if (!activeRuntimeProjects.contains(projectId)) activeRuntimeProjects.add(projectId)
            status = "Solicitei ao Android para manter o bot Telegram ativo. A notificação mostrará o estado real."
        } catch (e: Exception) {
            status = "Não consegui iniciar o runtime Telegram: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun startDiscordRuntimeNow(projectId: String) {
        try {
            DiscordBotRuntimeService.startDiscord(context, projectId)
            if (!activeRuntimeProjects.contains(projectId)) activeRuntimeProjects.add(projectId)
            status = "Solicitei ao Android que mantenha o bot ativo. A notificação mostrará a conexão real."
        } catch (e: Exception) {
            status = "Não consegui iniciar o runtime do bot: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val projectId = pendingDiscordStartId
        pendingDiscordStartId = null
        if (granted && projectId != null) {
            startDiscordRuntimeNow(projectId)
        } else if (projectId != null) {
            status = "A permissão de notificação foi negada; o bot não foi iniciado para não ocultar seu estado."
        }
    }

    fun requestDiscordRuntimeStart(projectId: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDiscordStartId = projectId
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startDiscordRuntimeNow(projectId)
        }
    }

    val telegramNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val projectId = pendingTelegramStartId
        pendingTelegramStartId = null
        if (granted && projectId != null) {
            startTelegramRuntimeNow(projectId)
        } else if (projectId != null) {
            status = "A permissão de notificação foi negada; o bot Telegram não foi iniciado."
        }
    }

    fun requestTelegramRuntimeStart(projectId: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTelegramStartId = projectId
            telegramNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTelegramRuntimeNow(projectId)
        }
    }

    val runtimeStateReceiver = remember(context, projectRuntimeStore) {
        object : BroadcastReceiver() {
            override fun onReceive(receivedContext: android.content.Context?, intent: Intent?) {
                val ids = intent?.getStringArrayListExtra(DiscordBotRuntimeService.EXTRA_ACTIVE_PROJECT_IDS)
                    ?: ArrayList(projectRuntimeStore.activeProjectIds())
                activeRuntimeProjects.clear()
                activeRuntimeProjects.addAll(ids)
            }
        }
    }

    DisposableEffect(context, runtimeStateReceiver) {
        val filter = IntentFilter(DiscordBotRuntimeService.ACTION_RUNTIME_STATE_CHANGED)
        ContextCompat.registerReceiver(context, runtimeStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(runtimeStateReceiver) } }
    }

    LaunchedEffect(showAppsDialog) {
        if (showAppsDialog) {
            activeRuntimeProjects.clear()
            activeRuntimeProjects.addAll(projectRuntimeStore.activeProjectIds())
        }
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
                    "Kellvyn IA está pronto. Peça tarefas diretamente pelo chat principal; os códigos gerados ficam guardados na área de códigos.",
                    false
                ))
            } else {
                addAll(saved.map { ChatMessage(it.text, it.fromUser) })
            }
        }
    }

    val assistantAgent = remember(workspaceStore, localAndroidCompiler) {
        AssistantAgent(
            AgentToolRouter(
                workspace = workspaceStore,
                localCompiler = localAndroidCompiler,
                onLicenseRequired = { projectId ->
                    pendingLicenseProjectId = projectId
                    pendingLicenseRepairBrief = null
                    showSdkLicenseDialog = true
                    status = "O agente quer compilar um projeto. Leia a licença oficial do Android SDK antes do primeiro download."
                }
            )
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
                            "Use o cartão de APK aqui no chat para salvar ou instalar o arquivo.",
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

    fun shareAssistantDocument(messageText: String) {
        scope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, "shared").apply {
                        if (!exists() && !mkdirs()) throw IllegalStateException("Não consegui preparar a pasta temporária.")
                    }
                    File(directory, "resposta-kellvyn.txt").apply {
                        writeText(messageText, Charsets.UTF_8)
                    }
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", document)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Resposta do Kellvyn IA")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newUri(context.contentResolver, "Resposta do Kellvyn IA", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Enviar documento do Kellvyn").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                status = "Abri o compartilhamento Android para enviar a resposta como arquivo .txt."
            } catch (e: Exception) {
                status = "Não consegui compartilhar a resposta: " + (e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun installApkFile(file: File?) {
        val apk = file?.takeIf { it.isFile && it.length() > 0L }
        if (apk == null) {
            status = "Ainda não há APK gerado para este projeto."
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
            status = "Abrindo o instalador do Android para " + apk.name + "."
        } catch (e: Exception) {
            status = "Não consegui abrir o instalador: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun installLatestApk() = installApkFile(latestBuiltApk)

    fun submitDraft() {
        val text = draft.trim()
        if (text.isEmpty() || isGenerating) return
        if (KellvynIdentity.isIdentityQuestion(text)) {
            messages.add(ChatMessage(text, true))
            messages.add(ChatMessage(KellvynIdentity.answer(), false))
            draft = ""
            saveHistory()
            status = "Kellvyn IA — identidade do assistente"
            return
        }
        val existingBuildOnly = ChatRequestRouter.shouldCompileExistingProject(text)
        if (!modelReady && !existingBuildOnly) {
            messages.add(ChatMessage(text, true))
            messages.add(ChatMessage("O núcleo de IA ainda não iniciou. Confira o estado exibido na tela e tente novamente quando o carregamento terminar.", false))
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
            ChatRequestRouter.shouldBuild(text)
        val wantsCoder = wantsBuild || wantsExistingBuild || ChatRequestRouter.shouldPreferCoder(text) || ChatRequestRouter.shouldUseOnlineTools(text)
        isGenerating = true
        streamingReply = ""
        status = if (wantsBuild) {
            "Planejando e gerando o projeto com o núcleo local…"
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
                            if (appBuilderAgent.isDiscordBotRequest(text)) {
                                messages.add(
                                    ChatMessage(
                                        "O bot \"" + project.name + "\" foi salvo no catálogo Apps. Abra Apps e toque em Manter ativo para configurar o token e o Application ID do Discord. O runtime nativo executa os comandos declarados em kellvyn.bot.json e mostra o estado real numa notificação persistente; o código Node.js continua guardado como fonte.",
                                        false
                                    )
                                )
                                status = "Bot Discord salvo em Apps; ainda precisa configurar as credenciais para conectar."
                            } else if (appBuilderAgent.isTelegramBotRequest(text)) {
                                messages.add(
                                    ChatMessage(
                                        "O bot Telegram \"" + project.name + "\" foi salvo em Apps. Para ativá-lo, abra Apps, toque em Manter ativo e informe o token do BotFather na configuração protegida. O runtime nativo fica neste aparelho e processa os comandos declarados em kellvyn.telegram.json.",
                                        false
                                    )
                                )
                                status = "Bot Telegram salvo em Apps; falta configurar o token para conectar."
                            } else if (appBuilderAgent.isBotProjectRequest(text)) {
                                messages.add(
                                    ChatMessage(
                                        "O projeto de bot \"" + project.name + "\" foi salvo em Apps. A geração do código não significa que ele esteja online. Nesta versão, a execução nativa está disponível para bots compatíveis do Discord e do Telegram; WhatsApp Business e outras plataformas permanecem como código-fonte e exigem o runtime ou serviço próprio da plataforma.",
                                        false
                                    )
                                )
                                status = "Código do bot salvo em Apps; runtime nativo não disponível para esta plataforma."
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
                        val answer = "Este projeto é um bot Node.js, não um app Android. O compilador local de APK não executa package.json. Abra Apps, o runtime para essa plataforma ainda não está integrado; o código permanece salvo no catálogo e não vou fingir que o bot está online."
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
                            internetAllowed = ChatRequestRouter.shouldUseOnlineTools(text),
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
                                else -> "Resposta gerada no aparelho"
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

    val fileExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { destination ->
        val content = pendingFileExportContent
        val name = pendingFileExportName
        pendingFileExportContent = null
        pendingFileExportName = ""
        if (destination != null && content != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use {
                            it.write(content.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("O Android não abriu o destino para salvar o arquivo.")
                    }
                    status = "Arquivo " + name + " baixado para o destino escolhido."
                    editorStatus = status
                } catch (e: Exception) {
                    status = "Falha ao baixar o arquivo: " + (e.message ?: e.javaClass.simpleName)
                    editorStatus = status
                }
            }
        }
    }

    val readFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val (fileName, fileContent) = withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                        }?.takeIf { it.isNotBlank() } ?: "arquivo.txt"

                        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
                        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val textExtensions = setOf(
                            "txt", "md", "markdown", "json", "xml", "csv", "log",
                            "kt", "kts", "java", "js", "mjs", "ts", "tsx", "jsx",
                            "py", "html", "css", "sh", "yml", "yaml", "toml",
                            "gradle", "properties", "sql", "c", "h", "cpp", "hpp",
                            "go", "rs", "rb", "php", "dart", "swift", "ini", "conf"
                        )
                        val recognizedText = mime.startsWith("text/") ||
                            mime in setOf(
                                "application/json",
                                "application/xml",
                                "application/javascript",
                                "application/x-yaml"
                            ) ||
                            extension in textExtensions
                        require(recognizedText) {
                            "Selecione um arquivo de texto ou código (por exemplo TXT, MD, JSON, KT ou JAVA)."
                        }

                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var totalBytes = 0
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                totalBytes += count
                                require(totalBytes <= MAX_IMPORTED_FILE_BYTES) {
                                    "O arquivo ultrapassa o limite de 256 KB para leitura no chat."
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: throw IllegalStateException("O Android não conseguiu abrir o arquivo escolhido.")

                        require(bytes.none { it == 0.toByte() }) {
                            "O arquivo parece binário; escolha um arquivo de texto ou código."
                        }
                        val text = String(bytes, Charsets.UTF_8)
                        require(text.isNotBlank()) { "O arquivo está vazio." }
                        name to text
                    }

                    val currentDraft = draft.trim()
                    val prefix = "Leia e analise o arquivo \"$fileName\":\n\n"
                    val base = if (currentDraft.isBlank()) "" else "$currentDraft\n\n"
                    val room = MAX_INPUT_CHARS - base.length - prefix.length
                    if (room <= 0) {
                        status = "O campo de mensagem está cheio. Envie ou limpe o texto atual antes de ler o arquivo."
                    } else {
                        val excerpt = fileContent.take(room)
                        draft = base + prefix + excerpt
                        status = if (excerpt.length < fileContent.length) {
                            "Arquivo $fileName lido; inseri um trecho dentro do limite de mensagem. Revise antes de enviar."
                        } else {
                            "Arquivo $fileName lido e adicionado ao rascunho. Revise antes de enviar."
                        }
                    }
                } catch (e: Exception) {
                    status = "Não consegui ler o arquivo: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    val apkExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destination ->
        val source = pendingApkExport
        pendingApkExport = null
        if (destination != null && source != null && source.isFile) {
            val fileToCopy = source
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            fileToCopy.inputStream().buffered().use { input -> input.copyTo(output) }
                        } ?: throw IllegalStateException("O Android não abriu o destino para salvar o APK.")
                    }
                    status = "APK salvo: " + fileToCopy.name
                } catch (e: Exception) {
                    status = "Falha ao salvar o APK: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && spokenText.isNotBlank()) {
            draft = listOf(draft.trim(), spokenText).filter { it.isNotBlank() }.joinToString(" ")
            status = "Ditado em português adicionado ao campo de mensagem."
        } else {
            status = "Ditado cancelado ou sem resultado."
        }
    }

    LaunchedEffect(localEngine) {
        isLoadingModel = true
        try {
            localEngine.recoverInterruptedModelSwap { progress -> status = progress }
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

    DisposableEffect(localEngine) {
        onDispose { localEngine.close() }
    }

    DisposableEffect(context) {
        val engineHolder = arrayOfNulls<TextToSpeech>(1)
        val created = TextToSpeech(context) { result ->
            val activeEngine = engineHolder[0]
            if (result == TextToSpeech.SUCCESS && activeEngine != null) {
                val languageResult = activeEngine.setLanguage(Locale("pt", "BR"))
                ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                ttsReady = false
            }
        }
        engineHolder[0] = created
        ttsEngine = created
        onDispose {
            created.stop()
            created.shutdown()
            ttsEngine = null
            ttsReady = false
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Night,
            surface = Panel,
            primary = accentColor,
            secondary = accentColor,
            onPrimary = Night,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showConversationsDialog = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("☰", color = accentColor, fontSize = 19.sp)
                }
                Spacer(Modifier.width(2.dp))
                KellvynMark(26.dp, accentColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kellvyn Assistente", color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (modelReady) "KELLVYN IA • LOCAL" else "KELLVYN IA • INICIALIZANDO",
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
                TextButton(onClick = { showQuickMenu = true }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text("▦", color = accentColor, fontSize = 18.sp)
                }
                TextButton(onClick = { showSettingsDialog = true }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text("⋮", color = accentColor, fontSize = 22.sp)
                }
                DropdownMenu(
                    expanded = showQuickMenu,
                    onDismissRequest = { showQuickMenu = false },
                    modifier = Modifier.background(Panel)
                ) {
                    DropdownMenuItem(
                        text = { Text("Ler arquivo de texto/código") },
                        onClick = {
                            showQuickMenu = false
                            readFileLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Códigos guardados") },
                        onClick = {
                            showQuickMenu = false
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            showProjectsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Apps e APKs") },
                        onClick = {
                            showQuickMenu = false
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            showAppsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Memória") },
                        onClick = {
                            showQuickMenu = false
                            savedMemories.clear()
                            savedMemories.addAll(memoryStore.list())
                            memoryDraft = ""
                            memoryStatus = ""
                            showMemoryDialog = true
                        }
                    )
                }
            }

            Divider(color = Color(0xFF143D33), thickness = 1.dp)
            LazyColumn(
                state = chatListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 6.dp, start = 2.dp, end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    if (message.fromUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                color = Color(0xFF0A2D24),
                                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 5.dp, bottomStart = 18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF14634D)),
                                modifier = Modifier.fillMaxWidth(0.88f)
                            ) {
                                Text(
                                    message.text,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    color = Color(0xFFE1FFF5),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            KellvynMark(26.dp, accentColor)
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Surface(
                                    color = Night,
                                    shape = RoundedCornerShape(topStart = 5.dp, topEnd = 17.dp, bottomEnd = 17.dp, bottomStart = 17.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0B3329)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        message.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        color = Color(0xFF9ED7C8),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
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
                                        Canvas(modifier = Modifier.size(14.dp)) {
                                            val stroke = Stroke(width = 1.3.dp.toPx())
                                            drawRoundRect(
                                                color = accentColor.copy(alpha = 0.65f),
                                                topLeft = Offset(size.width * 0.27f, size.height * 0.08f),
                                                size = Size(size.width * 0.58f, size.height * 0.68f),
                                                cornerRadius = CornerRadius(2.dp.toPx()),
                                                style = stroke
                                            )
                                            drawRoundRect(
                                                color = accentColor,
                                                topLeft = Offset(size.width * 0.12f, size.height * 0.27f),
                                                size = Size(size.width * 0.58f, size.height * 0.65f),
                                                cornerRadius = CornerRadius(2.dp.toPx()),
                                                style = stroke
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copiar", color = accentColor, fontSize = 10.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            val engine = ttsEngine
                                            if (engine != null && ttsReady) {
                                                engine.speak(message.text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "kellvyn-answer")
                                                status = "Lendo resposta em voz alta."
                                            } else {
                                                status = "A leitura em voz alta não está disponível neste aparelho."
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) { Text("Ouvir", color = accentColor, fontSize = 10.sp) }
                                    TextButton(
                                        onClick = {
                                            pendingFileExportName = "resposta-kellvyn.txt"
                                            pendingFileExportContent = message.text
                                            fileExportPicker.launch(pendingFileExportName)
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) { Text("Baixar", color = Muted, fontSize = 10.sp) }
                                    TextButton(
                                        onClick = { shareAssistantDocument(message.text) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) { Text("Enviar", color = accentColor, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
                if (streamingReply.isNotBlank()) {
                    item(key = "kellvyn_streaming_reply") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            KellvynMark(26.dp, accentColor)
                            Spacer(Modifier.width(7.dp))
                            Surface(
                                color = Night,
                                shape = RoundedCornerShape(topStart = 5.dp, topEnd = 17.dp, bottomEnd = 17.dp, bottomStart = 17.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0B3329)),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                Text(
                                    streamingReply + " ▍",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    color = Color(0xFF9ED7C8),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

            }
            if (latestBuiltApk?.isFile == true) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APK pronto", color = accentColor, fontSize = 9.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val apk = latestBuiltApk
                            if (apk != null && apk.isFile) {
                                pendingApkExport = apk
                                apkExportPicker.launch(apk.name)
                            } else {
                                status = "O arquivo APK não está mais disponível."
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                    ) { Text("Salvar APK", color = accentColor, fontSize = 10.sp) }
                    TextButton(
                        onClick = { installLatestApk() },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                    ) { Text("Instalar", color = accentColor, fontSize = 10.sp) }
                }
            }

            if (status.isNotBlank()) {
                Text(
                    status,
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                )
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 88.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitDraft() }),
                        placeholder = { Text("Converse ou peça para criar um aplicativo…", color = Muted, fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 1,
                        maxLines = 3,
                        enabled = !isGenerating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = accentColor
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            if (modelReady) "● IA local" else if (isLoadingModel) "◌ Preparando IA" else "IA local indisponível",
                            color = if (modelReady) accentColor else Muted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )

                        Box(
                            modifier = Modifier.size(34.dp).clickable(enabled = !isGenerating) {
                                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                    status = "O reconhecimento de voz não está disponível neste aparelho."
                                } else {
                                    val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale em português")
                                    }
                                    try {
                                        speechInputLauncher.launch(speechIntent)
                                    } catch (e: Exception) {
                                        status = "Não consegui iniciar o ditado: " + (e.message ?: e.javaClass.simpleName)
                                    }
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) { Text("🎙", fontSize = 15.sp, color = accentColor) }

                        Spacer(Modifier.weight(1f))

                        Button(
                            enabled = !isGenerating,
                            onClick = { submitDraft() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Night)
                        ) { Text(if (isGenerating) "…" else "↑", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    }
                }
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
                                Text("EDITOR DE CÓDIGO", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                                                if (fileText.length > MAX_EDITOR_CHARS) editorStatus = "Arquivo muito grande para editar aqui. O arquivo permanece salvo; use o chat para pedir alterações específicas."
                                                else { activeFilePath = path; editorContent = fileText; editorStatus = "" }
                                            } catch (e: Exception) { editorStatus = "Falha ao abrir: " + (e.message ?: e.javaClass.simpleName) }
                                        }
                                    }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                        Text((if (path == activeFilePath) "▸ " else "") + path, color = if (path == activeFilePath) accentColor else Color.White, fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (activeFilePath.isBlank()) {
                            Text("Escolha um arquivo na lista.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        } else {
                            Text(activeFilePath, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                        if (editorStatus.isNotBlank()) Text(editorStatus, color = if (editorStatus.startsWith("Arquivo salvo")) accentColor else Warning, fontSize = 10.sp, maxLines = 3)
                        if (isSavingFile) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !isSavingFile && activeEditProject != null && activeFilePath.isNotBlank(), onClick = {
                                pendingFileExportName = activeFilePath.substringAfterLast('/')
                                pendingFileExportContent = editorContent
                                fileExportPicker.launch(pendingFileExportName)
                            }) { Text("Baixar arquivo", color = Muted, fontSize = 10.sp) }
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
                            }, colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Night)) { Text("Salvar", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        if (showProjectsDialog) {
            AlertDialog(
                onDismissRequest = { showProjectsDialog = false },
                title = { Text("Códigos guardados") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (existingProjects.isEmpty()) {
                            Text("Os códigos criados pelo chat ficam guardados aqui. Para criar ou modificar um aplicativo, peça diretamente no chat principal.", color = Muted)
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

        if (showConversationsDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showConversationsDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                Surface(Modifier.fillMaxSize().padding(8.dp), color = Night, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CONVERSAS", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showConversationsDialog = false }) { Text("Fechar", color = Color.White) }
                        }
                        Button(onClick = { createNewConversation() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Night)) { Text("＋ Nova conversa") }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (conversations.isEmpty()) Text("Nenhuma conversa salva ainda.", color = Muted, fontSize = 12.sp)
                            else conversations.sortedByDescending { it.updatedAt }.forEach { conversation ->
                                Surface(Modifier.fillMaxWidth(), color = if (conversation.id == activeConversationId) Color(0xFF0A2D24) else Panel, shape = RoundedCornerShape(10.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(conversation.title, color = Color.White, fontSize = 12.sp, maxLines = 2)
                                            Text(conversation.messages.count { it.fromUser }.toString() + " mensagens suas", color = Muted, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = { openConversation(conversation.id) }) { Text(if (conversation.id == activeConversationId) "Atual" else "Abrir", color = accentColor) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Configurações do Kellvyn") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("COR DA INTERFACE", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(
                                "#38E6BC" to "Esmeralda",
                                "#5CB8FF" to "Azul",
                                "#C59BFF" to "Violeta",
                                "#FF8A8A" to "Vermelho"
                            ).forEach { (hex, label) ->
                                TextButton(
                                    onClick = {
                                        accentHex = hex
                                        uiPreferences.edit().putString("accent_hex", hex).apply()
                                        status = "Cor da interface alterada para " + label + "."
                                    },
                                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, color = Color(android.graphics.Color.parseColor(hex)), fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                        Divider(color = Color(0xFF143D33))
                        Text("SEGURANÇA", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Ações destrutivas e operações Git que alteram dados continuam exigindo confirmação. A conversa usa o modelo integrado no aparelho; pesquisas na internet só acontecem quando você pedir explicitamente.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Divider(color = Color(0xFF143D33))
                        Text("CHUVA DO MODELO", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "O efeito original de chuva do modelo não está presente no código atual que consegui verificar. Não coloquei uma animação diferente no lugar nem um controle falso.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Fechar", color = accentColor) }
                }
            )
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
                                Text("APPS, BOTS E PROJETOS", color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Catálogo permanente de tudo que o construtor gerou", color = Muted, fontSize = 11.sp)
                            }
    
                            TextButton(onClick = { showAppsDialog = false }) { Text("Fechar", color = Color.White) }
                        }
                        if (existingProjects.isEmpty()) {
                            Text("Ainda não há aplicativos criados. Peça ao chat principal para criar um; o editor serve só para editar arquivos.", color = Muted, fontSize = 13.sp)
                        } else {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                existingProjects.forEach { project ->
                                    val projectPaths = runCatching { workspaceStore.listFiles(project.id).toSet() }.getOrDefault(emptySet())
                                    val packageText = if ("package.json" in projectPaths) runCatching { workspaceStore.readFile(project.id, "package.json") }.getOrDefault("") else ""
                                    val projectKind = GeneratedProjectClassifier.classify(projectPaths, packageText, project.description, project.name)
                                    val projectApk = File(context.filesDir, "kellvyn_builds/" + project.id + ".apk")
                                    val isRuntimeActive = project.id in activeRuntimeProjects || projectRuntimeStore.isActive(project.id)
                                    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164638))) {
                                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(Modifier.size(50.dp), color = Color(0xFF0A2D24), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(projectIconGlyph(project), color = accentColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(project.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                                Text(projectKind.label + if (isRuntimeActive) " • ATIVO" else "", color = if (isRuntimeActive) accentColor else Muted, fontSize = 10.sp, maxLines = 1)
                                                Text(project.description.ifBlank { project.fileCount.toString() + " arquivos" }, color = Muted, fontSize = 10.sp, maxLines = 2)
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                                    }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("Código", color = accentColor, fontSize = 10.sp) }

                                                    when (projectKind) {
                                                        GeneratedProjectKind.ANDROID_APP -> {
                                                            TextButton(
                                                                enabled = projectApk.isFile && projectApk.length() > 0L,
                                                                onClick = { installApkFile(projectApk) },
                                                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                                                            ) { Text(if (projectApk.isFile) "Instalar APK" else "APK pendente", color = if (projectApk.isFile) accentColor else Muted, fontSize = 10.sp) }
                                                        }
                                                        GeneratedProjectKind.DISCORD_BOT -> {
                                                            TextButton(
                                                                onClick = {
                                                                    if (isRuntimeActive) {
                                                                        projectRuntimeStore.setActive(project.id, false)
                                                                        activeRuntimeProjects.remove(project.id)
                                                                        DiscordBotRuntimeService.stopProject(context, project.id)
                                                                        status = "Pedido para desativar o bot " + project.name + "."
                                                                    } else {
                                                                        val saved = discordCredentialsStore.get(project.id)
                                                                        if (saved == null) {
                                                                            discordConfigProject = project
                                                                            discordTokenDraft = ""
                                                                            discordApplicationIdDraft = ""
                                                                            discordSetupError = ""
                                                                            showDiscordConfigDialog = true
                                                                        } else {
                                                                            requestDiscordRuntimeStart(project.id)
                                                                        }
                                                                    }
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                                                            ) { Text(if (isRuntimeActive) "Parar bot" else "Manter ativo", color = if (isRuntimeActive) Warning else accentColor, fontSize = 10.sp) }
                                                        }
                                                        GeneratedProjectKind.TELEGRAM_BOT -> {
                                                            TextButton(
                                                                onClick = {
                                                                    if (isRuntimeActive) {
                                                                        projectRuntimeStore.setActive(project.id, false)
                                                                        activeRuntimeProjects.remove(project.id)
                                                                        TelegramBotRuntimeService.stopProject(context, project.id)
                                                                        status = "Pedido para desativar o bot Telegram " + project.name + "."
                                                                    } else {
                                                                        val saved = telegramCredentialsStore.get(project.id)
                                                                        if (saved == null) {
                                                                            telegramConfigProject = project
                                                                            telegramTokenDraft = ""
                                                                            telegramSetupError = ""
                                                                            showTelegramConfigDialog = true
                                                                        } else {
                                                                            requestTelegramRuntimeStart(project.id)
                                                                        }
                                                                    }
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                                                            ) { Text(if (isRuntimeActive) "Parar bot" else "Manter ativo", color = if (isRuntimeActive) Warning else accentColor, fontSize = 10.sp) }
                                                        }
                                                        else -> {
                                                            Text("Sem runtime", color = Muted, fontSize = 9.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text("Apps Android mostram o botão de instalação quando o APK existe. Bots compatíveis podem ficar ativos com notificação; outros projetos continuam salvos para editar e baixar arquivos individualmente.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        if (showDiscordConfigDialog && discordConfigProject != null) {
            val project = discordConfigProject!!
            AlertDialog(
                onDismissRequest = {
                    showDiscordConfigDialog = false
                    discordConfigProject = null
                    discordTokenDraft = ""
                    discordApplicationIdDraft = ""
                    discordSetupError = ""
                },
                title = { Text("Ativar bot do Discord") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "O runtime fica neste telefone e mostra uma notificação enquanto estiver ativo. O token é criptografado pelo Android Keystore e não é enviado ao modelo de IA. Você pode parar o bot pelo catálogo Apps ou pela notificação.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = discordApplicationIdDraft,
                            onValueChange = { discordApplicationIdDraft = it.filter(Char::isDigit).take(22) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Application ID") },
                            placeholder = { Text("ID do Discord Developer Portal") }
                        )
                        OutlinedTextField(
                            value = discordTokenDraft,
                            onValueChange = { discordTokenDraft = it.take(300) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("Bot Token") },
                            placeholder = { Text("Token privado do bot") }
                        )
                        if (discordSetupError.isNotBlank()) {
                            Text(discordSetupError, color = Warning, fontSize = 11.sp)
                        }
                        Text(
                            "No Discord Developer Portal, habilite o bot, copie o Application ID e o token do BOT. Nunca cole esse token na conversa. O bot precisa ser convidado para o servidor com os escopos bot e applications.commands.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            discordCredentialsStore.save(project.id, discordTokenDraft, discordApplicationIdDraft)
                            showDiscordConfigDialog = false
                            discordConfigProject = null
                            discordTokenDraft = ""
                            discordApplicationIdDraft = ""
                            discordSetupError = ""
                            requestDiscordRuntimeStart(project.id)
                        } catch (e: Exception) {
                            discordSetupError = e.message ?: "As credenciais não passaram na validação."
                        }
                    }) { Text("Salvar e ativar", color = accentColor) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDiscordConfigDialog = false
                        discordConfigProject = null
                        discordTokenDraft = ""
                        discordApplicationIdDraft = ""
                        discordSetupError = ""
                    }) { Text("Cancelar") }
                }
            )
        }

        if (showTelegramConfigDialog && telegramConfigProject != null) {
            val project = telegramConfigProject!!
            AlertDialog(
                onDismissRequest = {
                    showTelegramConfigDialog = false
                    telegramConfigProject = null
                    telegramTokenDraft = ""
                    telegramSetupError = ""
                },
                title = { Text("Ativar bot do Telegram") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "O runtime funciona neste telefone por conexão direta com a Bot API oficial do Telegram. O token fica criptografado pelo Android Keystore e não é enviado ao modelo de IA. Você pode desativar o bot no catálogo Apps ou na notificação.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = telegramTokenDraft,
                            onValueChange = { telegramTokenDraft = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("Token do BotFather") },
                            placeholder = { Text("Token privado do bot Telegram") }
                        )
                        if (telegramSetupError.isNotBlank()) {
                            Text(telegramSetupError, color = Warning, fontSize = 11.sp)
                        }
                        Text(
                            "Crie ou selecione um bot no @BotFather e cole o token aqui. Nunca cole esse token na conversa. O bot precisa estar iniciado pelo usuário no Telegram para receber mensagens privadas.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            telegramCredentialsStore.save(project.id, telegramTokenDraft)
                            showTelegramConfigDialog = false
                            telegramConfigProject = null
                            telegramTokenDraft = ""
                            telegramSetupError = ""
                            requestTelegramRuntimeStart(project.id)
                        } catch (e: Exception) {
                            telegramSetupError = e.message ?: "O token do Telegram não passou na validação."
                        }
                    }) { Text("Salvar e ativar", color = accentColor) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showTelegramConfigDialog = false
                        telegramConfigProject = null
                        telegramTokenDraft = ""
                        telegramSetupError = ""
                    }) { Text("Cancelar") }
                }
            )
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
                            Text(memoryStatus, color = accentColor, fontSize = 12.sp)
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
                    ) { Text("Li e aceito", color = accentColor) }
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
                    }) { Text("Autorizar", color = accentColor) }
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