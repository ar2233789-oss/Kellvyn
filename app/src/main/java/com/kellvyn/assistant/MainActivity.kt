package com.kellvyn.assistant

import android.app.Activity
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.kellvyn.assistant.accessibility.FloatingAssistantService
import com.kellvyn.assistant.attachments.LocalDocumentTextExtractor
import com.kellvyn.assistant.attachments.LocalImageTextExtractor
import com.kellvyn.assistant.attachments.LocalSvgImageGenerator
import com.kellvyn.assistant.attachments.LocalSvgGenerationResult
import com.kellvyn.assistant.ai.AssistantAgent
import com.kellvyn.assistant.ai.TermuxRequestPolicy
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
import com.kellvyn.assistant.workspace.WorkspaceGitService
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.WorkspaceProject
import com.kellvyn.assistant.workspace.LocalAndroidCompiler
import com.kellvyn.assistant.workspace.LocalApkBuildResult
import com.kellvyn.assistant.tools.WorkspaceConsoleDialog
import com.kellvyn.assistant.tools.TermuxBridge
import com.kellvyn.assistant.runtime.DiscordBotCredentialsStore
import com.kellvyn.assistant.runtime.DiscordBotRuntimeService
import com.kellvyn.assistant.runtime.TelegramBotCredentialsStore
import com.kellvyn.assistant.runtime.TelegramBotRuntimeService
import com.kellvyn.assistant.runtime.GeneratedProjectClassifier
import com.kellvyn.assistant.runtime.GeneratedProjectKind
import com.kellvyn.assistant.runtime.GeneratedProjectRuntimeStore
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException
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
private const val MAX_CONTEXT_MESSAGES = 32
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

private fun projectIconKind(project: WorkspaceProject): String {
    val description = (project.name + " " + project.description).lowercase(Locale.ROOT)
    return when {
        listOf("jogo", "game", "arcade", "rpg").any { it in description } -> "game"
        listOf("música", "musica", "áudio", "audio", "player").any { it in description } -> "music"
        listOf("foto", "câmera", "camera", "imagem", "galeria").any { it in description } -> "image"
        listOf("nota", "texto", "escrita", "diário", "diario").any { it in description } -> "document"
        listOf("tarefa", "todo", "lista", "produtividade").any { it in description } -> "check"
        listOf("agenda", "calendário", "calendario", "evento").any { it in description } -> "calendar"
        listOf("chat", "mensagem", "conversa", "assistente", "ia ").any { it in description } -> "chat"
        listOf("loja", "compra", "produto", "venda").any { it in description } -> "shop"
        listOf("mapa", "rota", "localização", "localizacao").any { it in description } -> "map"
        listOf("dinheiro", "finança", "financa", "banco", "gasto", "orçamento", "orcamento").any { it in description } -> "finance"
        listOf("calculadora", "cálculo", "calculo", "matemática", "matematica").any { it in description } -> "calculator"
        listOf("saúde", "saude", "treino", "fitness", "exercício", "exercicio").any { it in description } -> "health"
        listOf("bot", "discord", "whatsapp", "telegram", "wpp").any { it in description } -> "bot"
        else -> "app"
    }
}

@Composable
private fun KellvynLineIcon(
    kind: String,
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (size.minDimension * 0.075f).coerceAtLeast(1.35.dp.toPx()))
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke.width)
        }
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 0.08f) {
            drawRoundRect(
                tint,
                Offset(l * w, t * h),
                Size((r - l) * w, (b - t) * h),
                CornerRadius(radius * w, radius * w),
                style = stroke
            )
        }
        when (kind) {
            "menu" -> { line(.17f,.27f,.83f,.27f); line(.17f,.5f,.83f,.5f); line(.17f,.73f,.83f,.73f) }
            "more" -> { drawCircle(tint, w*.075f, Offset(w*.5f,h*.22f)); drawCircle(tint,w*.075f,Offset(w*.5f,h*.5f)); drawCircle(tint,w*.075f,Offset(w*.5f,h*.78f)) }
            "tools" -> {
                rect(.14f,.14f,.42f,.42f,.05f); rect(.58f,.14f,.86f,.42f,.05f)
                rect(.14f,.58f,.42f,.86f,.05f); rect(.58f,.58f,.86f,.86f,.05f)
            }
            "apps" -> {
                for (x in listOf(.30f, .70f)) {
                    for (y in listOf(.30f, .70f)) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset((x - .11f) * w, (y - .11f) * h),
                            size = Size(w * .22f, h * .22f),
                            cornerRadius = CornerRadius(w * .035f, w * .035f)
                        )
                    }
                }
            }
            "attach" -> {
                val path = Path().apply {
                    moveTo(w*.68f,h*.12f)
                    cubicTo(w*.89f,h*.29f,w*.86f,h*.43f,w*.70f,h*.59f)
                    lineTo(w*.45f,h*.83f)
                    cubicTo(w*.29f,h*.98f,w*.11f,h*.80f,w*.27f,h*.64f)
                    lineTo(w*.55f,h*.36f)
                    cubicTo(w*.65f,h*.25f,w*.76f,h*.36f,w*.66f,h*.46f)
                    lineTo(w*.41f,h*.72f)
                }
                drawPath(path, tint, style = stroke)
            }
            "mic" -> {
                rect(.36f,.12f,.64f,.60f,.15f)
                line(.23f,.47f,.23f,.53f); line(.23f,.53f,.32f,.66f); line(.32f,.66f,.50f,.72f)
                line(.50f,.72f,.68f,.66f); line(.68f,.66f,.77f,.53f); line(.77f,.47f,.77f,.53f)
                line(.50f,.72f,.50f,.87f); line(.36f,.87f,.64f,.87f)
            }
            "send" -> {
                val path = Path().apply {
                    moveTo(w*.16f,h*.18f); lineTo(w*.88f,h*.49f); lineTo(w*.16f,h*.82f)
                    lineTo(w*.29f,h*.54f); lineTo(w*.62f,h*.49f); lineTo(w*.29f,h*.45f); close()
                }
                drawPath(path, tint)
            }
            "game" -> {
                rect(.12f,.31f,.88f,.72f,.22f)
                line(.27f,.47f,.46f,.47f); line(.365f,.37f,.365f,.57f)
                drawCircle(tint,w*.045f,Offset(w*.68f,h*.44f)); drawCircle(tint,w*.045f,Offset(w*.77f,h*.54f))
            }
            "music" -> {
                line(.63f,.17f,.63f,.66f); line(.63f,.17f,.84f,.12f); line(.84f,.12f,.84f,.58f)
                drawCircle(tint,w*.13f,Offset(w*.44f,h*.70f)); drawCircle(tint,w*.13f,Offset(w*.70f,h*.62f))
            }
            "image" -> {
                rect(.12f,.16f,.88f,.84f,.08f); drawCircle(tint,w*.08f,Offset(w*.67f,h*.34f))
                line(.19f,.72f,.40f,.50f); line(.40f,.50f,.55f,.64f); line(.55f,.64f,.67f,.53f); line(.67f,.53f,.82f,.72f)
            }
            "document" -> {
                val path = Path().apply { moveTo(w*.27f,h*.12f); lineTo(w*.63f,h*.12f); lineTo(w*.78f,h*.28f); lineTo(w*.78f,h*.88f); lineTo(w*.27f,h*.88f); close() }
                drawPath(path,tint,style=stroke); line(.63f,.13f,.63f,.29f); line(.63f,.29f,.77f,.29f)
                line(.36f,.46f,.68f,.46f); line(.36f,.59f,.68f,.59f); line(.36f,.72f,.61f,.72f)
            }
            "check" -> { drawCircle(tint,w*.38f,Offset(w*.5f,h*.5f),style=stroke); line(.29f,.51f,.44f,.65f); line(.44f,.65f,.72f,.35f) }
            "calendar" -> {
                rect(.14f,.2f,.86f,.84f,.08f); line(.14f,.38f,.86f,.38f); line(.32f,.12f,.32f,.29f); line(.68f,.12f,.68f,.29f)
                line(.31f,.52f,.31f,.53f); line(.50f,.52f,.50f,.53f); line(.68f,.52f,.68f,.53f); line(.31f,.68f,.31f,.69f); line(.50f,.68f,.50f,.69f)
            }
            "chat" -> {
                val path = Path().apply { moveTo(w*.16f,h*.2f); lineTo(w*.84f,h*.2f); lineTo(w*.84f,h*.68f); lineTo(w*.48f,h*.68f); lineTo(w*.28f,h*.84f); lineTo(w*.3f,h*.68f); lineTo(w*.16f,h*.68f); close() }
                drawPath(path,tint,style=stroke); line(.29f,.37f,.70f,.37f); line(.29f,.5f,.62f,.5f)
            }
            "shop" -> {
                rect(.2f,.33f,.8f,.85f,.06f); line(.15f,.33f,.26f,.16f); line(.26f,.16f,.74f,.16f); line(.74f,.16f,.85f,.33f)
                line(.39f,.48f,.39f,.85f); line(.39f,.48f,.62f,.48f); line(.62f,.48f,.62f,.85f)
            }
            "map" -> {
                val path = Path().apply { moveTo(w*.12f,h*.25f); lineTo(w*.38f,h*.15f); lineTo(w*.63f,h*.25f); lineTo(w*.88f,h*.15f); lineTo(w*.88f,h*.75f); lineTo(w*.63f,h*.85f); lineTo(w*.38f,h*.75f); lineTo(w*.12f,h*.85f); close() }
                drawPath(path,tint,style=stroke); line(.38f,.15f,.38f,.75f); line(.63f,.25f,.63f,.85f)
            }
            "finance" -> {
                drawCircle(tint,w*.35f,Offset(w*.5f,h*.5f),style=stroke); line(.50f,.27f,.50f,.73f)
                line(.64f,.37f,.40f,.37f); line(.40f,.37f,.36f,.45f); line(.36f,.45f,.60f,.54f); line(.60f,.54f,.62f,.63f); line(.62f,.63f,.38f,.63f)
            }
            "calculator" -> { rect(.18f,.12f,.82f,.88f,.07f); line(.25f,.32f,.75f,.32f); for (x in listOf(.34f,.5f,.66f)) { line(x,.45f,x,.75f) }; line(.27f,.61f,.73f,.61f); line(.27f,.75f,.73f,.75f) }
            "health" -> { rect(.17f,.17f,.83f,.83f,.16f); line(.5f,.30f,.5f,.70f); line(.30f,.5f,.70f,.5f) }
            "bot" -> {
                rect(.18f,.28f,.82f,.78f,.15f); line(.5f,.14f,.5f,.28f); drawCircle(tint,w*.045f,Offset(w*.5f,h*.12f))
                drawCircle(tint,w*.065f,Offset(w*.36f,h*.48f)); drawCircle(tint,w*.065f,Offset(w*.64f,h*.48f)); line(.35f,.66f,.65f,.66f)
            }
            else -> { rect(.16f,.16f,.84f,.84f,.1f); line(.36f,.37f,.64f,.37f); line(.36f,.5f,.64f,.5f); line(.36f,.63f,.64f,.63f) }
        }
    }
}

@Composable
private fun KellvynMark(diameter: androidx.compose.ui.unit.Dp = 28.dp, accent: Color = Neon) {
    Canvas(modifier = Modifier.size(diameter)) {
        val d = this.size.minDimension
        val r = d * 0.46f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = accent.copy(alpha = 0.22f), radius = r * 1.08f, center = center)
        drawCircle(color = accent.copy(alpha = 0.34f), radius = r * 0.99f, center = center)
        drawCircle(color = Color(0xFF03110C), radius = r * 0.93f, center = center)
        drawCircle(color = accent.copy(alpha = 1.0f), radius = r * 0.89f, center = center, style = Stroke(width = 2.0.dp.toPx()))

        val hood = Path().apply {
            moveTo(center.x, center.y - r * 0.78f)
            cubicTo(center.x - r * 0.67f, center.y - r * 0.45f, center.x - r * 0.58f, center.y + r * 0.33f, center.x - r * 0.25f, center.y + r * 0.58f)
            quadraticTo(center.x, center.y + r * 0.76f, center.x + r * 0.25f, center.y + r * 0.58f)
            cubicTo(center.x + r * 0.58f, center.y + r * 0.33f, center.x + r * 0.67f, center.y - r * 0.45f, center.x, center.y - r * 0.78f)
            close()
        }
        drawPath(path = hood, color = accent.copy(alpha = 0.82f))
        drawPath(path = hood, color = accent.copy(alpha = 1.0f), style = Stroke(width = 1.25.dp.toPx()))
        val face = Path().apply {
            moveTo(center.x, center.y - r * 0.37f)
            lineTo(center.x - r * 0.28f, center.y - r * 0.03f)
            lineTo(center.x - r * 0.19f, center.y + r * 0.28f)
            quadraticTo(center.x, center.y + r * 0.40f, center.x + r * 0.19f, center.y + r * 0.28f)
            lineTo(center.x + r * 0.28f, center.y - r * 0.03f)
            close()
        }
        drawPath(path = face, color = Night)
        drawLine(color = Color(0xFFBFFFF1), start = Offset(center.x - r * 0.19f, center.y + r * 0.02f), end = Offset(center.x - r * 0.055f, center.y + r * 0.06f), strokeWidth = 1.8.dp.toPx())
        drawLine(color = Color(0xFFBFFFF1), start = Offset(center.x + r * 0.055f, center.y + r * 0.06f), end = Offset(center.x + r * 0.19f, center.y + r * 0.02f), strokeWidth = 1.8.dp.toPx())
        drawCircle(color = accent.copy(alpha = 0.62f), radius = r * 0.14f, center = Offset(center.x, center.y + r * 0.30f))
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
    val termuxBridge = remember(context) { TermuxBridge(context) }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var streamingReply by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Kellvyn pronto. Converse pelo chat principal.") }
    var modelReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var modelInitializationAttempted by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showConversationsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val uiPreferences = remember(context) { context.getSharedPreferences("kellvyn_ui", Context.MODE_PRIVATE) }
    val securityPreferences = remember(context) { context.getSharedPreferences("kellvyn_security", Context.MODE_PRIVATE) }
    var rainEffectEnabled by remember(uiPreferences) {
        mutableStateOf(uiPreferences.getBoolean("rain_effect_enabled", false))
    }
    val rainOffset = remember { Animatable(0f) }
    LaunchedEffect(rainEffectEnabled) {
        if (rainEffectEnabled) {
            rainOffset.snapTo(0f)
            while (true) {
                rainOffset.animateTo(1f, animationSpec = tween(durationMillis = 5_200, easing = LinearEasing))
                rainOffset.snapTo(0f)
            }
        } else {
            rainOffset.snapTo(0f)
        }
    }
    var reinforcedProtection by remember(securityPreferences) {
        mutableStateOf(securityPreferences.getBoolean("reinforced_protection", false))
    }
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
    var projectToDelete by remember { mutableStateOf<WorkspaceProject?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var showBrowserDialog by remember { mutableStateOf(false) }
    var browserAddressDraft by remember { mutableStateOf("https://www.google.com") }
    var showGitDialog by remember { mutableStateOf(false) }
    var gitProjectId by remember { mutableStateOf<String?>(null) }
    var gitOutput by remember { mutableStateOf("") }
    var gitCommitDraft by remember { mutableStateOf("") }
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
    var pendingFloatingStart by remember { mutableStateOf(false) }
    var pendingFileExportContent by remember { mutableStateOf<String?>(null) }
    var pendingFileExportName by remember { mutableStateOf("") }
    var pendingApkExport by remember { mutableStateOf<File?>(null) }
    var pendingImageExport by remember { mutableStateOf<File?>(null) }
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

    val floatingNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldStart = pendingFloatingStart
        pendingFloatingStart = false
        if (granted && shouldStart) {
            runCatching { FloatingAssistantService.start(context) }
                .onSuccess { status = "Assistente flutuante iniciado. A bolinha verde ficará sobre os outros aplicativos." }
                .onFailure { status = "Não consegui iniciar o assistente flutuante: " + (it.message ?: it.javaClass.simpleName) }
        } else if (shouldStart) {
            status = "A permissão de notificação foi negada; o assistente flutuante não foi iniciado."
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

    fun requestFloatingAssistantStart() {
        if (!Settings.canDrawOverlays(context)) {
            status = "Permita a sobreposição do Kellvyn nas Configurações do Android; depois toque em Assistente flutuante novamente."
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                status = "Não consegui abrir a permissão de sobreposição: " + (e.message ?: e.javaClass.simpleName)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFloatingStart = true
            floatingNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        runCatching { FloatingAssistantService.start(context) }
            .onSuccess { status = "Assistente flutuante iniciado. A bolinha verde ficará sobre os outros aplicativos." }
            .onFailure { status = "Não consegui iniciar o assistente flutuante: " + (it.message ?: it.javaClass.simpleName) }
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

    val assistantAgent = remember(workspaceStore, localAndroidCompiler, termuxBridge) {
        AssistantAgent(
            AgentToolRouter(
                workspace = workspaceStore,
                localCompiler = localAndroidCompiler,
                onLicenseRequired = { projectId ->
                    pendingLicenseProjectId = projectId
                    pendingLicenseRepairBrief = null
                    showSdkLicenseDialog = true
                    status = "O agente quer compilar um projeto. Leia a licença oficial do Android SDK antes do primeiro download."
                },
                termuxBridge = termuxBridge
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
        if (reinforcedProtection && !requestAgentApproval(
                "Compilar e assinar o APK do projeto ${project.name} no armazenamento local do aparelho"
            )
        ) {
            messages.add(ChatMessage("Compilação cancelada pela proteção reforçada. Nenhum novo APK foi criado.", false))
            status = "Build cancelada pela proteção reforçada."
            saveHistory()
            return
        }
        var result = localAndroidCompiler.build(project.id) { progress ->
            status = progress
        }
        var autoRepaired = false
        if (result is LocalApkBuildResult.Failure &&
            isRepairableSourceBuildFailure(result.reason) &&
            !repairBrief.isNullOrBlank() && repairEngine != null
        ) {
            val firstError = result.reason
            val repairAllowed = !reinforcedProtection || requestAgentApproval(
                "Permitir uma correção automática do código de ${project.name} após o erro real de compilação"
            )
            if (!repairAllowed) {
                result = LocalApkBuildResult.Failure(
                    "A primeira compilação falhou. A proteção reforçada bloqueou a correção automática porque ela não foi autorizada."
                )
            } else {
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
        }

        when (result) {
            is LocalApkBuildResult.Success -> {
                latestBuiltApk = result.apk
                messages.add(
                    ChatMessage(
                        (if (autoRepaired) "O agente corrigiu o código após o primeiro erro. " else "") +
                            "APK criado, assinado e verificado no próprio celular. Pacote: " + result.applicationId + ". " +
                            "Tempo: " + (result.durationMs / 1000.0) + "s. Arquivo: " + result.apk.name + ". " +
                            "Os botões Salvar APK e Instalar APK aparecem logo abaixo desta resposta.",
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
            } catch (e: CancellationException) {
                throw e
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
        val wantsImage = ChatRequestRouter.shouldGenerateImage(text)
        if (!modelReady && !existingBuildOnly) {
            messages.add(ChatMessage(text, true))
            messages.add(ChatMessage("A IA local ainda está iniciando. Confira o status na tela e tente novamente quando ela estiver pronta.", false))
            saveHistory()
            draft = ""
            status = "IA local não inicializada."
            return
        }

        messages.add(ChatMessage(text, true))
        saveHistory()
        draft = ""
        val wantsExistingBuild = ChatRequestRouter.shouldCompileExistingProject(text)
        val wantsBuild = !wantsImage && !wantsExistingBuild &&
            ChatRequestRouter.shouldBuild(text)
        val wantsCoder = !wantsImage && (
            wantsBuild || wantsExistingBuild ||
                ChatRequestRouter.shouldPreferCoder(text) ||
                ChatRequestRouter.shouldUseOnlineTools(text) ||
                TermuxRequestPolicy.mayUseTermux(text)
            )
        isGenerating = true
        streamingReply = if (wantsImage) "Preparando ilustração…" else "Pensando…"
        status = if (wantsImage) {
            "Criando uma ilustração vetorial com a IA local…"
        } else if (wantsBuild) {
            "Planejando e gerando o projeto com a IA local…"
        } else if (wantsExistingBuild) {
            "Selecionando o projeto e iniciando a compilação local…"
        } else {
            "Gerando resposta no aparelho…"
        }

        val selectedEngine: AssistantEngine = localEngine
        scope.launch {
            try {
                if (wantsImage) {
                    when (val generated = LocalSvgImageGenerator.generate(context, localEngine, text) { progress ->
                        scope.launch(Dispatchers.Main.immediate) {
                            status = progress
                            streamingReply = progress
                        }
                    }) {
                        is LocalSvgGenerationResult.Success -> {
                            messages.add(ChatMessage(
                                "Ilustração vetorial gerada com a IA local e convertida em PNG neste aparelho.\nIMAGEM_LOCAL_GERADA:" + generated.pngFile.name,
                                false
                            ))
                            status = "Imagem PNG criada localmente. Você pode pré-visualizar e salvar o arquivo abaixo."
                        }
                        is LocalSvgGenerationResult.Failure -> {
                            messages.add(ChatMessage("Não consegui gerar a imagem: " + generated.reason, false))
                            status = generated.reason
                        }
                    }
                } else if (wantsBuild) {
                    if (reinforcedProtection && !requestAgentApproval(
                            "Criar e salvar os arquivos do novo projeto solicitado: " + text.take(180)
                        )
                    ) {
                        messages.add(ChatMessage("Criação cancelada pela proteção reforçada. Nenhum projeto novo foi salvo.", false))
                        status = "Criação cancelada pela proteção reforçada."
                        saveHistory()
                        return@launch
                    }
                    when (val result = appBuilderAgent.createProject(selectedEngine, text)) {
                        is AppBuildResult.Success -> {
                            val project = result.project
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            if (appBuilderAgent.isBotProjectRequest(text) && appBuilderAgent.isDiscordBotRequest(text)) {
                                messages.add(
                                    ChatMessage(
                                        "O bot \"" + project.name + "\" foi salvo no catálogo Apps. Abra Apps e toque em Manter ativo para configurar o token e o Application ID do Discord. O runtime nativo executa os comandos declarados em kellvyn.bot.json e mostra o estado real numa notificação persistente; o código Node.js continua guardado como fonte.",
                                        false
                                    )
                                )
                                status = "Bot Discord salvo em Apps; ainda precisa configurar as credenciais para conectar."
                            } else if (appBuilderAgent.isBotProjectRequest(text) && appBuilderAgent.isTelegramBotRequest(text)) {
                                messages.add(
                                    ChatMessage(
                                        "O bot Telegram \"" + project.name + "\" foi salvo em Apps. Para ativá-lo, abra Apps, toque em Manter ativo e informe o token do BotFather na configuração protegida. O runtime nativo processa apenas as ações compatíveis declaradas em kellvyn.telegram.json; os arquivos JavaScript Node.js ficam salvos como fonte e não são executados pelo runtime Android.",
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
                } else if (wantsExistingBuild && !modelReady) {
                    // When ready, the request reaches AssistantAgent, which invokes build_project_apk through AgentToolRouter.
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
                            onToolStatus = { toolStatus ->
                                scope.launch(Dispatchers.Main.immediate) { status = toolStatus }
                            },
                            requestApproval = { summary -> requestAgentApproval(summary) },
                            reinforcedProtection = reinforcedProtection
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
            } catch (e: CancellationException) {
                throw e
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

    fun retryLocalModelInitialization() {
        if (isLoadingModel || modelReady) return
        scope.launch {
            isLoadingModel = true
            modelInitializationAttempted = true
            status = "Tentando preparar a IA local novamente…"
            try {
                localEngine.prepareLocalModel { progress -> status = progress }
                if (localEngine.hasSavedModel()) {
                    status = "Modelo local encontrado; inicializando…"
                    updateModelResult(localEngine.loadSavedModel())
                } else {
                    updateModelResult(AssistantResult.Unavailable(
                        "O modelo local não ficou disponível. Verifique a conexão e tente novamente."
                    ))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateModelResult(AssistantResult.Failure(
                    "Não foi possível preparar a IA local: " + (e.message ?: e.javaClass.simpleName)
                ))
            } finally {
                isLoadingModel = false
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
                } catch (e: CancellationException) {
                    throw e
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
                    val (fileName, fileContent, attachmentKind) = withContext(Dispatchers.IO) {
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
                        val imageFile = mime.startsWith("image/") ||
                            extension in setOf("png", "jpg", "jpeg", "webp", "bmp", "heic")
                        when {
                            imageFile -> {
                                val extracted = LocalImageTextExtractor.extract(context, uri)
                                Triple(
                                    name,
                                    "[ANÁLISE LOCAL DA IMAGEM — rótulos visuais aproximados e texto OCR; não é uma descrição visual completa]\n\n" + extracted,
                                    "image"
                                )
                            }
                            LocalDocumentTextExtractor.supports(name, mime) -> {
                                Triple(name, LocalDocumentTextExtractor.extract(context, uri, name, mime), "document")
                            }
                            else -> {
                                val textExtensions = setOf(
                                    "txt", "md", "markdown", "json", "xml", "csv", "log",
                                    "kt", "kts", "java", "js", "mjs", "ts", "tsx", "jsx",
                                    "py", "html", "css", "sh", "yml", "yaml", "toml",
                                    "gradle", "properties", "sql", "c", "h", "cpp", "hpp",
                                    "go", "rs", "rb", "php", "dart", "swift", "ini", "conf"
                                )
                                val recognizedText = mime.startsWith("text/") ||
                                    mime in setOf("application/json", "application/xml", "application/javascript", "application/x-yaml") ||
                                    extension in textExtensions
                                require(recognizedText) {
                                    "Formato não suportado. Use texto/código, PDF, DOCX, ODT ou uma imagem para análise local."
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
                                            "O arquivo de texto ultrapassa o limite de 256 KB para leitura no chat."
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                } ?: throw IllegalStateException("O Android não conseguiu abrir o arquivo escolhido.")
                                require(bytes.none { it == 0.toByte() }) { "O arquivo parece binário; escolha texto ou código." }
                                val text = String(bytes, Charsets.UTF_8)
                                require(text.isNotBlank()) { "O arquivo está vazio." }
                                Triple(name, text, "text")
                            }
                        }
                    }

                    val currentDraft = draft.trim()
                    val prefix = when (attachmentKind) {
                        "image" -> "Analise a rotulagem visual aproximada e o texto OCR extraídos localmente da imagem \"$fileName\". Não trate as categorias como descrição completa da cena.\n\n"
                        "document" -> "Leia e analise o documento \"$fileName\":\n\n"
                        else -> "Leia e analise o arquivo \"$fileName\":\n\n"
                    }
                    val base = if (currentDraft.isBlank()) "" else "$currentDraft\n\n"
                    val room = MAX_INPUT_CHARS - base.length - prefix.length
                    if (room <= 0) {
                        status = "O campo de mensagem está cheio. Envie ou limpe o texto atual antes de ler o arquivo."
                    } else {
                        val excerpt = fileContent.take(room)
                        draft = base + prefix + excerpt
                        status = when {
                            excerpt.length < fileContent.length -> "Extraí um trecho de $fileName dentro do limite de mensagem. Revise antes de enviar."
                            attachmentKind == "image" -> "Imagem analisada localmente com rótulos visuais aproximados e OCR. A IA textual recebe a análise extraída, não os pixels nem uma descrição completa da cena."
                            attachmentKind == "document" -> "Documento $fileName extraído localmente e adicionado ao rascunho."
                            else -> "Arquivo $fileName lido e adicionado ao rascunho. Revise antes de enviar."
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = "Falha ao salvar o APK: " + (e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    val imageExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destination ->
        val source = pendingImageExport
        pendingImageExport = null
        if (destination != null && source != null && source.isFile) {
            val fileToCopy = source
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            fileToCopy.inputStream().buffered().use { input -> input.copyTo(output) }
                        } ?: throw IllegalStateException("O Android não abriu o destino para salvar a imagem.")
                    }
                    status = "Imagem salva: " + fileToCopy.name
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = "Falha ao salvar a imagem: " + (e.message ?: e.javaClass.simpleName)
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
            localEngine.prepareLocalModel { progress -> status = progress }
            if (localEngine.hasSavedModel()) {
                status = "IA local encontrada; inicializando…"
                updateModelResult(localEngine.loadSavedModel())
            } else {
                updateModelResult(AssistantResult.Unavailable(
                    "O modelo local não ficou disponível. Verifique a conexão e tente novamente."
                ))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateModelResult(AssistantResult.Failure(
                "Não foi possível preparar a IA local: " + (e.message ?: e.javaClass.simpleName)
            ))
        } finally {
            isLoadingModel = false
            modelInitializationAttempted = true
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
            modifier = Modifier.fillMaxSize().background(Night).drawBehind {
                if (rainEffectEnabled) {
                    val spacing = 36.dp.toPx()
                    val streakHeight = 7.dp.toPx()
                    val xGap = 3.dp.toPx()
                    val columns = (size.width / spacing).toInt()
                    for (column in 0..columns) {
                        val x = column * spacing + (column % 3) * xGap
                        val phase = (rainOffset.value + ((column * 37) % 100) / 100f) % 1f
                        val head = phase * (size.height + 7 * streakHeight) - 7 * streakHeight
                        for (segment in 0..6) {
                            val y = head - segment * streakHeight * 1.65f
                            if (y >= -streakHeight && y <= size.height) {
                                val opacity = (0.10f * (1f - segment / 7f)).coerceAtLeast(0.012f)
                                drawLine(
                                    color = Neon.copy(alpha = opacity),
                                    start = Offset(x, y),
                                    end = Offset(x + (if (column % 2 == 0) 0.6f else -0.6f) * streakHeight, y + streakHeight),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showConversationsDialog = true },
                    modifier = Modifier.size(38.dp).semantics { contentDescription = "Histórico de conversas" }
                ) {
                    KellvynLineIcon("menu", accentColor, Modifier.size(19.dp))
                }
                Spacer(Modifier.width(2.dp))
                KellvynMark(34.dp, accentColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kellvyn Assistente", color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            modelReady -> "KELLVYN IA • LOCAL"
                            isLoadingModel || !modelInitializationAttempted -> "KELLVYN IA • INICIALIZANDO"
                            else -> "KELLVYN IA • INDISPONÍVEL"
                        },
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = {
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        showAppsDialog = true
                    },
                    modifier = Modifier.size(36.dp).semantics { contentDescription = "Apps e APKs" }
                ) {
                    KellvynLineIcon("apps", accentColor, Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(36.dp).semantics { contentDescription = "Configurações" }
                ) {
                    KellvynLineIcon("more", accentColor, Modifier.size(20.dp))
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
                            KellvynMark(30.dp, accentColor)
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                val imageMarker = Regex("""IMAGEM_LOCAL_GERADA:([A-Za-z0-9._-]+)""")
                                    .find(message.text)?.groupValues?.getOrNull(1)
                                val visibleAssistantText = if (imageMarker == null) message.text
                                    else message.text.replace("IMAGEM_LOCAL_GERADA:" + imageMarker, "").trim()
                                Surface(
                                    color = Night,
                                    shape = RoundedCornerShape(topStart = 5.dp, topEnd = 17.dp, bottomEnd = 17.dp, bottomStart = 17.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0B3329)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        visibleAssistantText,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        color = Color(0xFF9ED7C8),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                                val generatedImageFile = imageMarker?.let { name ->
                                    File(File(context.filesDir, "kellvyn_images"), name)
                                        .takeIf { it.isFile && it.length() > 0L }
                                }
                                val generatedImageBitmap = remember(generatedImageFile?.absolutePath) {
                                    generatedImageFile?.let { file ->
                                        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
                                    }
                                }
                                if (generatedImageBitmap != null && generatedImageFile != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        color = Color(0xFF061F17),
                                        shape = RoundedCornerShape(13.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(9.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            Text("Ilustração PNG local", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Image(
                                                bitmap = generatedImageBitmap,
                                                contentDescription = "Ilustração vetorial gerada pelo Kellvyn",
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(
                                                    onClick = {
                                                        if (generatedImageFile.isFile && generatedImageFile.length() > 0L) {
                                                            pendingImageExport = generatedImageFile
                                                            imageExportPicker.launch(generatedImageFile.name)
                                                        } else status = "O arquivo PNG desta resposta não está mais disponível."
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) { Text("Salvar imagem", color = accentColor, fontSize = 11.sp) }
                                            }
                                        }
                                    }
                                }
                                val apkNameFromMessage = if (message.text.contains("APK criado, assinado e verificado no próprio celular.")) {
                                    Regex("""Arquivo:\s+([a-z0-9][a-z0-9-]{0,60}\.apk)\.""", RegexOption.IGNORE_CASE)
                                        .find(message.text)?.groupValues?.getOrNull(1)
                                } else null
                                val apkForThisMessage = apkNameFromMessage?.let { name ->
                                    File(File(context.filesDir, "kellvyn_builds"), name)
                                        .takeIf { it.isFile && it.length() > 0L }
                                }
                                if (apkForThisMessage != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        color = Color(0xFF061F17),
                                        shape = RoundedCornerShape(13.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("APK compilado", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    "%.1f MB".format(Locale.ROOT, apkForThisMessage.length() / (1024.0 * 1024.0)),
                                                    color = Muted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Text(apkForThisMessage.name, color = Color.White, fontSize = 11.sp)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        if (apkForThisMessage.isFile && apkForThisMessage.length() > 0L) {
                                                            pendingApkExport = apkForThisMessage
                                                            apkExportPicker.launch(apkForThisMessage.name)
                                                        } else {
                                                            status = "O arquivo APK desta resposta não está mais disponível."
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) { Text("Salvar APK", color = accentColor, fontSize = 11.sp) }
                                                TextButton(
                                                    onClick = { installApkFile(apkForThisMessage) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                ) { Text("Instalar APK", color = accentColor, fontSize = 11.sp) }
                                            }
                                        }
                                    }
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
                            KellvynMark(28.dp, accentColor)
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
            val currentLatestApk = latestBuiltApk?.takeIf { it.isFile && it.length() > 0L }
            val latestApkHasCardInCurrentConversation = currentLatestApk?.let { apk ->
                messages.any { message ->
                    message.text.contains("APK criado, assinado e verificado no próprio celular.") &&
                        message.text.contains("Arquivo: " + apk.name + ".")
                }
            } == true
            if (currentLatestApk != null && !latestApkHasCardInCurrentConversation) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Último APK", color = accentColor, fontSize = 9.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (currentLatestApk.isFile && currentLatestApk.length() > 0L) {
                                pendingApkExport = currentLatestApk
                                apkExportPicker.launch(currentLatestApk.name)
                            } else {
                                status = "O arquivo APK não está mais disponível."
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                    ) { Text("Salvar APK", color = accentColor, fontSize = 10.sp) }
                    TextButton(
                        onClick = { installApkFile(currentLatestApk) },
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
            if (modelInitializationAttempted && !modelReady && !isLoadingModel) {
                TextButton(
                    onClick = { retryLocalModelInitialization() },
                    modifier = Modifier.align(Alignment.End).heightIn(min = 26.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Tentar novamente", color = accentColor, fontSize = 10.sp)
                }
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
                            when {
                                modelReady -> "● IA local"
                                isLoadingModel || !modelInitializationAttempted -> "◌ Preparando IA"
                                else -> "IA local indisponível"
                            },
                            color = if (modelReady) accentColor else Muted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )

                        Box(
                            modifier = Modifier.size(30.dp).clickable(enabled = !isGenerating) {
                                readFileLauncher.launch(arrayOf("*/*"))
                            },
                            contentAlignment = Alignment.Center
                        ) { KellvynLineIcon("attach", accentColor, Modifier.size(17.dp)) }

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
                        ) { KellvynLineIcon("mic", accentColor, Modifier.size(20.dp)) }

                        Spacer(Modifier.weight(1f))

                        Button(
                            enabled = !isGenerating,
                            onClick = { submitDraft() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Night)
                        ) { if (isGenerating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Night) else KellvynLineIcon("send", Night, Modifier.size(17.dp)) }
                    }
                }
            }

            // Persistent six-destination navigation requested for the main app.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF04110D),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF143D33)),
                shape = RoundedCornerShape(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @Composable
                    fun MainNavItem(label: String, glyph: String, onClick: () -> Unit) {
                        Column(
                            modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(glyph, color = accentColor, fontSize = 17.sp, lineHeight = 19.sp)
                            Text(label, color = Color.White, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                    MainNavItem("IA", "◉") {
                        showEditorDialog = false
                        showTerminalDialog = false
                        showAppsDialog = false
                        status = ""
                    }
                    MainNavItem("Navegador", "◎") {
                        browserAddressDraft = "https://www.google.com"
                        showBrowserDialog = true
                    }
                    MainNavItem("Editor", "⌘") {
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        showEditorDialog = true
                    }
                    MainNavItem("Terminal", ">_") { showTerminalDialog = true }
                    MainNavItem("Git", "⑂") {
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        gitProjectId = existingProjects.firstOrNull()?.id
                        gitOutput = ""
                        showGitDialog = true
                    }
                    MainNavItem("Apps", "▦") {
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        showAppsDialog = true
                    }
                }
            }

        }

        if (showBrowserDialog) {
            AlertDialog(
                onDismissRequest = { showBrowserDialog = false },
                title = { Text("Navegador", color = accentColor) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pesquise ou abra um endereço no navegador do aparelho.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(
                            value = browserAddressDraft,
                            onValueChange = { browserAddressDraft = it.take(2048) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Pesquisa ou endereço") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val raw = browserAddressDraft.trim()
                        if (raw.isNotBlank()) {
                            val target = if (raw.startsWith("https://") || raw.startsWith("http://")) raw
                                else if (raw.contains(".") && !raw.contains(" ")) "https://$raw"
                                else "https://www.google.com/search?q=" + Uri.encode(raw)
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                                .onSuccess { showBrowserDialog = false }
                                .onFailure { status = "Não foi possível abrir a página: " + (it.message ?: it.javaClass.simpleName) }
                        }
                    }) { Text("Pesquisar / abrir", color = accentColor) }
                },
                dismissButton = { TextButton(onClick = { showBrowserDialog = false }) { Text("Cancelar") } },
                containerColor = Panel
            )
        }

        if (showGitDialog) {
            val gitService = remember(workspaceStore) { WorkspaceGitService(workspaceStore) }
            AlertDialog(
                onDismissRequest = { showGitDialog = false },
                title = { Text("Git do workspace", color = accentColor) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (existingProjects.isEmpty()) {
                            Text("Nenhum projeto local disponível. Peça à IA pelo chat para criar ou abrir um projeto.", color = Muted, fontSize = 12.sp)
                        } else {
                            Text("PROJETO", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            existingProjects.forEach { project ->
                                TextButton(
                                    onClick = { gitProjectId = project.id; gitOutput = "" },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text((if (gitProjectId == project.id) "● " else "○ ") + project.name, color = if (gitProjectId == project.id) accentColor else Color.White, maxLines = 1)
                                }
                            }
                            val selectedProject = existingProjects.firstOrNull { it.id == gitProjectId }
                            if (selectedProject != null) {
                                Text("REPOSITÓRIO LOCAL", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        gitOutput = runCatching { gitService.status(selectedProject.id) }.getOrElse { it.message ?: "Falha ao consultar status Git." }
                                    }) { Text("Status", color = accentColor) }
                                    TextButton(onClick = {
                                        gitOutput = runCatching { gitService.branches(selectedProject.id) }.getOrElse { it.message ?: "Falha ao listar branches." }
                                    }) { Text("Branches", color = accentColor) }
                                    TextButton(onClick = {
                                        gitOutput = runCatching { gitService.log(selectedProject.id) }.getOrElse { it.message ?: "Falha ao ler commits." }
                                    }) { Text("Commits", color = accentColor) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        gitOutput = runCatching { gitService.init(selectedProject.id) }.getOrElse { it.message ?: "Falha ao inicializar Git." }
                                    }) { Text("Inicializar", color = accentColor) }
                                    TextButton(onClick = {
                                        gitOutput = runCatching { gitService.stageAll(selectedProject.id) }.getOrElse { it.message ?: "Falha ao preparar alterações." }
                                    }) { Text("Preparar arquivos", color = accentColor) }
                                }
                                OutlinedTextField(
                                    value = gitCommitDraft,
                                    onValueChange = { gitCommitDraft = it.take(300) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Mensagem do commit") }
                                )
                                TextButton(onClick = {
                                    gitOutput = runCatching { gitService.commit(selectedProject.id, gitCommitDraft) }
                                        .getOrElse { it.message ?: "Falha ao criar commit local." }
                                    if (gitOutput.startsWith("Commit local criado:")) gitCommitDraft = ""
                                }) { Text("Criar commit local", color = accentColor) }
                            }
                        }
                        if (gitOutput.isNotBlank()) {
                            Surface(color = Night, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(gitOutput, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(9.dp))
                            }
                        }
                        Text("Operações locais no workspace; não envia commits a um servidor remoto.", color = Muted, fontSize = 10.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showGitDialog = false }) { Text("Fechar", color = accentColor) } },
                containerColor = Panel
            )
        }

        if (showEditorDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { if (!isSavingFile) showEditorDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
            ) {
                Surface(
                    Modifier.fillMaxSize().padding(6.dp),
                    color = Night,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164638))
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("CÓDIGOS SALVOS", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Escolha um projeto, edite um arquivo e salve nesta mesma tela.", color = Muted, fontSize = 10.sp, maxLines = 2)
                            }
                            TextButton(enabled = !isSavingFile, onClick = { showEditorDialog = false }) { Text("Fechar", color = Color.White) }
                        }

                        Text("PROJETOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Surface(Modifier.fillMaxWidth().heightIn(max = 112.dp), color = Panel, shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (existingProjects.isEmpty()) {
                                    Text("Nenhum código salvo ainda. Peça ao chat principal para criar um projeto; ele aparecerá aqui.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                                } else {
                                    existingProjects.forEach { project ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(project.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                Text("${project.fileCount} arquivos · ${project.id}", color = Muted, fontSize = 9.sp, maxLines = 1)
                                            }
                                            TextButton(
                                                onClick = {
                                                    try {
                                                        val paths = workspaceStore.listFiles(project.id)
                                                        val firstPath = paths.firstOrNull().orEmpty()
                                                        val firstContent = if (firstPath.isNotBlank()) workspaceStore.readFile(project.id, firstPath) else ""
                                                        activeEditProject = project
                                                        editablePaths = paths
                                                        if (firstContent.length > MAX_EDITOR_CHARS) {
                                                            activeFilePath = ""
                                                            editorContent = ""
                                                            editorStatus = "O primeiro arquivo é grande demais para editar aqui; ele continua salvo."
                                                        } else {
                                                            activeFilePath = firstPath
                                                            editorContent = firstContent
                                                            editorStatus = if (firstPath.isBlank()) "Projeto sem arquivos editáveis." else ""
                                                        }
                                                    } catch (e: Exception) {
                                                        editorStatus = "Falha ao abrir o código: " + (e.message ?: e.javaClass.simpleName)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                                            ) { Text(if (activeEditProject?.id == project.id) "Aberto" else "Abrir", color = accentColor, fontSize = 10.sp) }
                                            TextButton(
                                                onClick = { projectToDelete = project },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) { Text("Apagar", color = Warning, fontSize = 10.sp) }
                                        }
                                    }
                                }
                            }
                        }

                        if (activeEditProject == null) {
                            Text("Selecione um projeto acima para ver e salvar os códigos.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        } else {
                            Text("ARQUIVOS · ${activeEditProject?.name.orEmpty()}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Surface(Modifier.fillMaxWidth().heightIn(max = 76.dp), color = Panel, shape = RoundedCornerShape(10.dp)) {
                                Column(Modifier.verticalScroll(rememberScrollState()).padding(3.dp)) {
                                    editablePaths.forEach { path ->
                                        TextButton(
                                            onClick = {
                                                val project = activeEditProject
                                                if (project != null) {
                                                    try {
                                                        val fileText = workspaceStore.readFile(project.id, path)
                                                        if (fileText.length > MAX_EDITOR_CHARS) {
                                                            editorStatus = "Arquivo muito grande para editar aqui. O arquivo permanece salvo; use o chat para pedir alterações específicas."
                                                        } else {
                                                            activeFilePath = path
                                                            editorContent = fileText
                                                            editorStatus = ""
                                                        }
                                                    } catch (e: Exception) {
                                                        editorStatus = "Falha ao abrir: " + (e.message ?: e.javaClass.simpleName)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text((if (path == activeFilePath) "▸ " else "") + path, color = if (path == activeFilePath) accentColor else Color.White, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            if (activeFilePath.isBlank()) {
                                Text("Escolha um arquivo na lista para editar.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
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
                            Button(
                                enabled = !isSavingFile && activeEditProject != null && activeFilePath.isNotBlank(),
                                onClick = {
                                    val project = activeEditProject
                                    if (project != null) {
                                        isSavingFile = true
                                        editorStatus = "Salvando…"
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { workspaceStore.addOrReplaceFiles(project.id, listOf(ProjectFileInput(activeFilePath, editorContent))) }
                                                existingProjects.clear()
                                                existingProjects.addAll(workspaceStore.listProjects())
                                                activeEditProject = workspaceStore.getProject(project.id)
                                                editorStatus = "Arquivo salvo."
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                editorStatus = "Falha ao salvar: " + (e.message ?: e.javaClass.simpleName)
                                            } finally {
                                                isSavingFile = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Night)
                            ) { Text("Salvar código", fontSize = 11.sp) }
                        }
                    }
                }
            }
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
        if (showTerminalDialog) {
            WorkspaceConsoleDialog(
                termux = termuxBridge,
                onDismiss = { showTerminalDialog = false }
            )
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
                        Text("EFEITO VISUAL", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Chuva digital", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Efeito discreto verde no fundo do chat.", color = Muted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = rainEffectEnabled,
                                onCheckedChange = { enabled ->
                                    rainEffectEnabled = enabled
                                    uiPreferences.edit().putBoolean("rain_effect_enabled", enabled).apply()
                                }
                            )
                        }
                        Divider(color = Color(0xFF143D33))
                        Text("ATALHOS", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showSettingsDialog = false
                                    savedMemories.clear()
                                    savedMemories.addAll(memoryStore.list())
                                    memoryDraft = ""
                                    memoryStatus = ""
                                    showMemoryDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) { Text("Memória local", color = accentColor, fontSize = 10.sp) }
                            TextButton(
                                onClick = {
                                    showSettingsDialog = false
                                    requestFloatingAssistantStart()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) { Text("Assistente flutuante", color = accentColor, fontSize = 10.sp) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showSettingsDialog = false
                                    showTerminalDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) { Text("Terminal Termux", color = accentColor, fontSize = 10.sp) }
                            TextButton(
                                onClick = {
                                    showSettingsDialog = false
                                    status = "Ative o serviço de leitura de tela do Kellvyn nas Configurações de Acessibilidade. Ele só captura texto quando você aciona a leitura."
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) {
                                        status = "Não consegui abrir Acessibilidade: " + (e.message ?: e.javaClass.simpleName)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                            ) { Text("Leitura de tela", color = accentColor, fontSize = 10.sp) }
                        }
                        Divider(color = Color(0xFF143D33))
                        Text("SEGURANÇA", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Ações destrutivas e operações Git que alteram dados sempre exigem confirmação. Ative a proteção reforçada para também confirmar gravações, correções de arquivos e compilação de APK.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Proteção reforçada da IA", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (reinforcedProtection) "Ativada: pede autorização antes de criar/alterar projetos, corrigir arquivos ou compilar." else "Desativada: edições pedidas no chat continuam automáticas.",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = reinforcedProtection,
                                onCheckedChange = { enabled ->
                                    reinforcedProtection = enabled
                                    securityPreferences.edit().putBoolean("reinforced_protection", enabled).apply()
                                    status = if (enabled) {
                                        "Proteção reforçada ativada. O chat pedirá confirmação antes de gravar, corrigir arquivos ou compilar APK."
                                    } else {
                                        "Proteção reforçada desativada. As confirmações para ações destrutivas continuam ativas."
                                    }
                                }
                            )
                        }
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
                            TextButton(
                                onClick = {
                                    showAppsDialog = false
                                    existingProjects.clear()
                                    existingProjects.addAll(workspaceStore.listProjects())
                                    if (activeEditProject?.id !in existingProjects.map { it.id }) {
                                        activeEditProject = null
                                        editablePaths = emptyList()
                                        activeFilePath = ""
                                        editorContent = ""
                                        editorStatus = ""
                                    }
                                    showEditorDialog = true
                                }
                            ) { Text("Códigos", color = accentColor, fontSize = 11.sp) }
                            TextButton(
                                onClick = {
                                    showAppsDialog = false
                                    showTerminalDialog = true
                                }
                            ) { Text("Terminal", color = accentColor, fontSize = 11.sp) }
                            TextButton(onClick = { showAppsDialog = false }) { Text("Fechar", color = Color.White, fontSize = 11.sp) }
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
                                                    KellvynLineIcon(projectIconKind(project), accentColor, Modifier.size(26.dp))
                                                }
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(project.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                                Text(projectKind.label + if (isRuntimeActive) " • HABILITADO" else "", color = if (isRuntimeActive) accentColor else Muted, fontSize = 10.sp, maxLines = 1)
                                                Text(project.description.ifBlank { project.fileCount.toString() + " arquivos" }, color = Muted, fontSize = 10.sp, maxLines = 2)
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(onClick = {
                                                        try {
                                                            val paths = workspaceStore.listFiles(project.id)
                                                            val firstPath = paths.firstOrNull().orEmpty()
                                                            val firstContent = if (firstPath.isNotBlank()) workspaceStore.readFile(project.id, firstPath) else ""
                                                            activeEditProject = project
                                                            editablePaths = paths
                                                            if (firstContent.length > MAX_EDITOR_CHARS) {
                                                                activeFilePath = ""
                                                                editorContent = ""
                                                                editorStatus = "O primeiro arquivo é grande demais para editar aqui; ele continua salvo."
                                                            } else {
                                                                activeFilePath = firstPath
                                                                editorContent = firstContent
                                                                editorStatus = if (firstPath.isBlank()) "Projeto sem arquivos editáveis." else ""
                                                            }
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
                        Text("Apps Android mostram o botão de instalação quando o APK existe. HABILITADO significa que o serviço tentará manter o bot ativo; o estado real da conexão aparece na notificação. Projetos sem runtime continuam disponíveis para editar o código.", color = Muted, fontSize = 10.sp)
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
                            "O runtime funciona neste telefone por conexão direta com a Bot API oficial do Telegram. O token fica criptografado pelo Android Keystore e não é enviado ao modelo de IA. Só as ações nativas suportadas declaradas em kellvyn.telegram.json são executadas; o JavaScript Node.js permanece como código-fonte. Você pode desativar o bot no catálogo Apps ou na notificação.",
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

        val isTermuxApproval = pendingAgentApproval?.startsWith("Executar no Termux o comando:") == true
        if (pendingAgentApproval != null) {
            AlertDialog(
                onDismissRequest = {
                    agentApprovalGate?.complete(false)
                },
                title = {
                    Text(if (isTermuxApproval) "Autorizar comando no Termux?" else "Autorizar ação do Kellvyn?")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isTermuxApproval) {
                                "A IA pediu para executar este comando no ambiente Termux. Confira o comando inteiro antes de autorizar."
                            } else {
                                "A IA solicitou uma operação que altera arquivos ou o Git. Ela só será executada no workspace privado deste aplicativo depois da sua confirmação."
                            },
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
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                        Text(
                            if (isTermuxApproval) {
                                "Esse comando será executado no Termux e pode alterar arquivos, instalar pacotes ou mudar dados. A autorização vale somente para este comando. Cancele se não reconhecer tudo o que está escrito."
                            } else {
                                "Não é um shell do Android: comandos arbitrários e encadeamento de comandos são bloqueados."
                            },
                            color = Muted,
                            fontSize = 11.sp
                        )
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