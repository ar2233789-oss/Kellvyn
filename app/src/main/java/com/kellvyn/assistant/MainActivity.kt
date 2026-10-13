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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantAgent
import com.kellvyn.assistant.ai.AgentToolRouter
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.reasonOrText
import com.kellvyn.assistant.ai.AssistantTurn
import com.kellvyn.assistant.ai.LocalLiteRtAssistantEngine
import com.kellvyn.assistant.ai.OpenAiCompatibleAssistantEngine
import com.kellvyn.assistant.data.ChatHistoryStore
import com.kellvyn.assistant.data.OnlineProviderSettingsStore
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

data class ChatMessage(val text: String, val fromUser: Boolean)

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
    val memoryStore = remember(context) { UserMemoryStore(context) }
    val onlineSettingsStore = remember(context) { OnlineProviderSettingsStore(context) }
    val localEngine = remember(context) { LocalLiteRtAssistantEngine(context) }
    val onlineEngine = remember(onlineSettingsStore) { OpenAiCompatibleAssistantEngine(onlineSettingsStore) }
    val workspaceStore = remember(context) { ProjectWorkspaceStore(context) }
    val workspaceTerminal = remember(workspaceStore) { WorkspaceTerminal(workspaceStore) }
    val assistantAgent = remember(workspaceStore) { AssistantAgent(AgentToolRouter(workspaceStore)) }
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
    val initialOnlineSettings = remember(onlineSettingsStore) { onlineSettingsStore.load() }

    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Selecione um modelo local ou configure um provedor online") }
    var modelReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var isRemovingModel by remember { mutableStateOf(false) }
    var isDownloadingModel by remember { mutableStateOf(false) }
    var modelDownloadPercent by remember { mutableStateOf(0) }
    var onlineMode by remember { mutableStateOf(false) }
    var internetToolsEnabled by remember { mutableStateOf(false) }
    var onlineConfigured by remember { mutableStateOf(initialOnlineSettings.isConfigured) }
    var showOnlineSettings by remember { mutableStateOf(false) }
    var showConsoleDialog by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showNavigatorDialog by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf("https://developer.android.com") }
    var interactionMode by remember { mutableStateOf("Build") }
    var pendingAgentApproval by remember { mutableStateOf<String?>(null) }
    var agentApprovalGate by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var selectedNav by remember { mutableStateOf("AI") }
    var openTabs by remember { mutableStateOf(emptyList<String>()) }
    var endpointInput by remember { mutableStateOf(initialOnlineSettings.endpoint) }
    var modelInput by remember { mutableStateOf(initialOnlineSettings.model) }
    var apiKeyInput by remember { mutableStateOf(initialOnlineSettings.apiKey) }
    var settingsError by remember { mutableStateOf("") }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var memoryDraft by remember { mutableStateOf("") }
    var memoryStatus by remember { mutableStateOf("") }
    val savedMemories = remember(memoryStore) { mutableStateListOf<MemoryFact>().apply { addAll(memoryStore.list()) } }
    var showBuilderDialog by remember { mutableStateOf(false) }
    var showProjectsDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<WorkspaceProject?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var activeEditProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var editablePaths by remember { mutableStateOf(emptyList<String>()) }
    var activeFilePath by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var editorStatus by remember { mutableStateOf("") }
    var isSavingFile by remember { mutableStateOf(false) }
    var builderBrief by remember { mutableStateOf("") }
    var isBuildingProject by remember { mutableStateOf(false) }
    var builderMessage by remember { mutableStateOf("") }
    var builtProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var pendingExportProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var latestBuiltApk by remember { mutableStateOf<File?>(null) }
    var pendingLicenseProjectId by remember { mutableStateOf<String?>(null) }
    var showSdkLicenseDialog by remember { mutableStateOf(false) }
    val sdkLicenseText = remember(context) {
        runCatching { context.resources.openRawResource(com.kellvyn.assistant.R.raw.android_sdk_license).bufferedReader().use { it.readText() } }
            .getOrDefault("Texto da licença não pôde ser carregado. Não aceite sem ler o contrato oficial do Android SDK.")
    }
    val existingProjects = remember(workspaceStore) {
        mutableStateListOf<WorkspaceProject>().apply { addAll(workspaceStore.listProjects()) }
    }

    val messages = remember(historyStore) {
        mutableStateListOf<ChatMessage>().apply {
            val saved = historyStore.load()
            if (saved.isEmpty()) {
                add(
                    ChatMessage(
                        "Kellvyn IA está pronto. No modo local, selecione um modelo .litertlm compatível. No modo online opcional, configure seu próprio endpoint HTTPS. Nenhum dos modos finge estar ativo antes de ser configurado.",
                        false
                    )
                )
            } else {
                addAll(saved.map { ChatMessage(it.text, it.fromUser) })
            }
        }
    }

    fun saveHistory() {
        historyStore.save(messages.map { ChatHistoryStore.SavedMessage(it.text, it.fromUser) })
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

    fun rememberOpenTab(projectId: String, path: String) {
        val tab = "$projectId::$path"
        openTabs = (openTabs + tab).distinct().takeLast(40)
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

    suspend fun buildProjectAndReport(project: WorkspaceProject) {
        when (val result = localAndroidCompiler.build(project.id) { progress -> status = progress }) {
            is LocalApkBuildResult.Success -> {
                latestBuiltApk = result.apk
                messages.add(
                    ChatMessage(
                        "APK criado e verificado no próprio celular. Pacote: ${result.applicationId}. " +
                            "Tempo: ${result.durationMs / 1000.0}s. Arquivo privado: ${result.apk.name}. " +
                            "Use o menu superior para instalar o APK.",
                        false
                    )
                )
                status = "APK gerado, assinado e verificado localmente."
                saveHistory()
            }
            is LocalApkBuildResult.Failure -> {
                messages.add(ChatMessage("A build local retornou um erro real:\n${result.reason}", false))
                status = "A build local falhou; diagnóstico guardado no chat."
                saveHistory()
            }
            LocalApkBuildResult.LicenseRequired -> {
                pendingLicenseProjectId = project.id
                showSdkLicenseDialog = true
                messages.add(
                    ChatMessage(
                        "O Android SDK exige que você leia e aceite os termos oficiais antes de baixar a plataforma necessária à compilação local. Nenhum download foi iniciado.",
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
        val onlineSelected = onlineMode
        if (onlineSelected && !onlineConfigured) {
            status = "Configure o provedor online antes de enviar mensagens."
            return
        }
        if (!onlineSelected && !modelReady) {
            status = "Carregue um modelo .litertlm primeiro. A mensagem não foi enviada."
            showQuickMenu = true
            return
        }

        messages.add(ChatMessage(text, true))
        saveHistory()
        draft = ""
        val wantsBuild = ChatRequestRouter.shouldBuild(text, buildModeSelected = interactionMode == "Build")
        val wantsCoder = wantsBuild || ChatRequestRouter.shouldPreferCoder(text)
        isGenerating = true
        status = if (wantsBuild) {
            "Planejando e gerando arquivos do projeto com o motor selecionado…"
        } else if (onlineSelected) {
            "Enviando ao endpoint online escolhido…"
        } else {
            "Gerando resposta no aparelho…"
        }

        val selectedEngine: AssistantEngine = if (onlineSelected) onlineEngine else localEngine
        scope.launch {
            var restoreConversationModel = false
            try {
                // For coding/build work, automatically use Qwen only if the user already installed it.
                // The selected conversation model is restored afterwards; nothing is downloaded silently.
                if (
                    !onlineSelected &&
                    wantsCoder &&
                    localEngine.hasSavedCoderModel() &&
                    !localEngine.displayName.contains("Qwen", ignoreCase = true)
                ) {
                    status = "Qwen Coder local já instalado: ativando temporariamente para esta tarefa…"
                    restoreConversationModel = when (val coderResult = localEngine.loadCoderModel()) {
                        is AssistantResult.Success -> true
                        is AssistantResult.Unavailable -> {
                            status = "Qwen Coder não pôde ser ativado (${coderResult.reason}); vou usar o modelo selecionado."
                            false
                        }
                        is AssistantResult.Failure -> {
                            status = "Qwen Coder não pôde ser ativado (${coderResult.reason}); vou usar o modelo selecionado."
                            false
                        }
                    }
                }

                if (wantsBuild) {
                    when (val result = appBuilderAgent.createProject(selectedEngine, text)) {
                        is AppBuildResult.Success -> {
                            val project = result.project
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            builtProject = project
                            messages.add(
                                ChatMessage(
                                    "Projeto \"${project.name}\" criado e validado estruturalmente com ${project.fileCount} arquivos. Vou iniciar a compilação local em segundo plano.",
                                    false
                                )
                            )
                            status = "Projeto salvo. Iniciando o compilador local…"
                            buildProjectAndReport(project)
                        }
                        is AppBuildResult.Failure -> {
                            messages.add(ChatMessage("Não consegui concluir a geração do projeto: ${result.reason}", false))
                            status = result.reason
                        }
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
                    val agentResult = assistantAgent.respond(
                        engine = selectedEngine,
                        conversation = turns,
                        internetAllowed = internetToolsEnabled,
                        onToolStatus = { toolStatus -> status = toolStatus },
                        requestApproval = { summary -> requestAgentApproval(summary) }
                    )
                    val refreshedProjects = withContext(Dispatchers.IO) { workspaceStore.listProjects() }
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
                                onlineSelected && internetToolsEnabled -> "Resposta online; pesquisa web autorizada quando solicitada"
                                onlineSelected -> "Resposta recebida do provedor online; pesquisa web desligada"
                                internetToolsEnabled -> "Resposta local; pesquisa web autorizada quando solicitada"
                                else -> "Resposta gerada localmente • sem acesso à internet"
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
                if (restoreConversationModel) {
                    val taskStatus = status
                    status = "Restaurando o modelo de conversa selecionado…"
                    when (val restoreResult = localEngine.loadConversationModel()) {
                        is AssistantResult.Success -> status = taskStatus
                        is AssistantResult.Unavailable -> {
                            val warning = "$taskStatus O modelo de conversa não foi restaurado: ${restoreResult.reason}. Qwen Coder continua ativo."
                            messages.add(ChatMessage(warning, false))
                            status = warning
                            saveHistory()
                        }
                        is AssistantResult.Failure -> {
                            val warning = "$taskStatus O modelo de conversa não foi restaurado: ${restoreResult.reason}. Qwen Coder continua ativo."
                            messages.add(ChatMessage(warning, false))
                            status = warning
                            saveHistory()
                        }
                    }
                }
                isGenerating = false
            }
        }
    }

    fun updateModelResult(result: AssistantResult) {
        when (result) {
            is AssistantResult.Success -> {
                modelReady = true
                if (!onlineMode) status = result.text
            }
            is AssistantResult.Unavailable -> {
                modelReady = false
                if (!onlineMode) status = result.reason
            }
            is AssistantResult.Failure -> {
                modelReady = false
                if (!onlineMode) status = result.reason
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

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isLoadingModel = true
                if (!onlineMode) status = "Copiando e inicializando o modelo local…"
                try {
                    updateModelResult(localEngine.importAndLoad(uri))
                } finally {
                    isLoadingModel = false
                }
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
                if (!onlineMode) status = "Modelo local encontrado; inicializando…"
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
                    val activeModelLabel = if (localEngine.displayName.contains("Qwen", ignoreCase = true)) {
                        "QWEN CODER"
                    } else {
                        "MODELO LEVE"
                    }
                    Text(
                        if (onlineMode) {
                            "PROVEDOR ONLINE OPCIONAL"
                        } else if (modelReady) {
                            "NÚCLEO LOCAL • $activeModelLabel" + if (internetToolsEnabled) " • WEB" else ""
                        } else if (internetToolsEnabled) {
                            "NÚCLEO LOCAL • INTERNET ATIVA"
                        } else {
                            "NÚCLEO LOCAL • OFFLINE"
                        },
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }
                TextButton(onClick = { showQuickMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("⋮", color = Neon, fontSize = 23.sp)
                }
                DropdownMenu(
                    expanded = showQuickMenu,
                    onDismissRequest = { showQuickMenu = false },
                    modifier = Modifier.background(Panel)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (onlineMode) "Mudar para IA local" else "Mudar para IA online") },
                        onClick = {
                            showQuickMenu = false
                            onlineMode = !onlineMode
                            status = if (onlineMode) {
                                if (onlineConfigured) "Modo online selecionado; mensagens irão ao provedor configurado."
                                else "Modo online selecionado. Configure um endpoint HTTPS antes de enviar mensagens."
                            } else {
                                if (modelReady) "Modo local ativo neste aparelho."
                                else "Modo local selecionado. Instale ou carregue um modelo para conversar."
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Instalar APK gerado") },
                        enabled = latestBuiltApk?.isFile == true,
                        onClick = { showQuickMenu = false; installLatestApk() }
                    )
                    DropdownMenuItem(
                        text = { Text("Pasta do APK gerado") },
                        enabled = latestBuiltApk?.isFile == true,
                        onClick = {
                            showQuickMenu = false
                            status = "APK salvo com segurança no armazenamento privado do Kellvyn. Use 'Instalar APK gerado' para abrir o instalador do Android."
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Carregar modelo local (.litertlm)") },
                        onClick = { showQuickMenu = false; modelPicker.launch(arrayOf("*/*")) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (localEngine.hasSavedConversationModel()) {
                                    "Usar modelo leve de conversa (≈374 MB)"
                                } else {
                                    "Baixar modelo leve de conversa (≈374 MB)"
                                }
                            )
                        },
                        enabled = !isDownloadingModel && !isLoadingModel && !isGenerating,
                        onClick = {
                            showQuickMenu = false
                            if (localEngine.hasSavedConversationModel()) {
                                isLoadingModel = true
                                status = "Ativando o modelo leve de conversa…"
                                scope.launch {
                                    try {
                                        val result = localEngine.loadConversationModel()
                                        updateModelResult(result)
                                        if (result is AssistantResult.Success) {
                                            status = "Modelo leve de conversa ativo. O modelo Qwen programador, se instalado, continua guardado."
                                        }
                                    } catch (e: Exception) {
                                        status = "Falha ao ativar o modelo leve: " + (e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        isLoadingModel = false
                                    }
                                }
                            } else {
                                isDownloadingModel = true
                                modelDownloadPercent = 0
                                status = "Baixando modelo leve de conversa…"
                                scope.launch {
                                    try {
                                        val result = localEngine.downloadRecommendedModel { bytes, total ->
                                            val percent = if (total > 0L) ((bytes * 100L) / total).toInt().coerceIn(0, 100) else 0
                                            scope.launch {
                                                modelDownloadPercent = percent
                                                status = "Baixando modelo leve: " + percent + "% (" + (bytes / (1024L * 1024L)) + " MB)"
                                            }
                                        }
                                        updateModelResult(result)
                                        if (result is AssistantResult.Success) status = "Modelo leve baixado e inicializado."
                                    } catch (e: Exception) {
                                        status = "Falha ao baixar o modelo leve: " + (e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        isDownloadingModel = false
                                    }
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (localEngine.hasSavedCoderModel()) {
                                    "Usar Qwen Coder local (≈1,12 GB)"
                                } else {
                                    "Baixar Qwen Coder local (≈1,12 GB)"
                                }
                            )
                        },
                        enabled = !isDownloadingModel && !isLoadingModel && !isGenerating,
                        onClick = {
                            showQuickMenu = false
                            if (localEngine.hasSavedCoderModel()) {
                                isLoadingModel = true
                                status = "Ativando Qwen Coder local…"
                                scope.launch {
                                    try {
                                        val result = localEngine.loadCoderModel()
                                        updateModelResult(result)
                                        if (result is AssistantResult.Success) {
                                            status = "Qwen Coder ativo localmente. O modelo leve de conversa continua guardado para alternar depois."
                                        }
                                    } catch (e: Exception) {
                                        status = "Falha ao ativar Qwen Coder: " + (e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        isLoadingModel = false
                                    }
                                }
                            } else {
                                isDownloadingModel = true
                                modelDownloadPercent = 0
                                status = "Baixando Qwen Coder (arquivo ≈1,12 GB)…"
                                scope.launch {
                                    try {
                                        val result = localEngine.downloadCoderModel { bytes, total ->
                                            val percent = if (total > 0L) ((bytes * 100L) / total).toInt().coerceIn(0, 100) else 0
                                            scope.launch {
                                                modelDownloadPercent = percent
                                                status = "Baixando Qwen Coder: " + percent + "% (" + (bytes / (1024L * 1024L)) + " MB)"
                                            }
                                        }
                                        updateModelResult(result)
                                        if (result is AssistantResult.Success) {
                                            status = "Qwen Coder foi verificado por SHA-256, carregado e selecionado como modelo ativo."
                                        }
                                    } catch (e: Exception) {
                                        status = "Falha ao baixar Qwen Coder: " + (e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        isDownloadingModel = false
                                    }
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Apagar modelo local ativo") },
                        enabled = localEngine.hasSavedModel() && !isGenerating && !isDownloadingModel && !isLoadingModel && !isRemovingModel,
                        onClick = {
                            showQuickMenu = false
                            isRemovingModel = true
                            status = "Removendo o modelo local ativo…"
                            scope.launch {
                                try {
                                    val removed = withContext(Dispatchers.IO) {
                                        localEngine.removeSavedModel()
                                    }
                                    if (removed && localEngine.hasSavedModel()) {
                                        val result = localEngine.loadSavedModel()
                                        updateModelResult(result)
                                        status = if (result is AssistantResult.Success) {
                                            "Modelo ativo removido; o outro modelo local instalado foi reativado."
                                        } else {
                                            "O modelo ativo foi removido, mas o outro modelo instalado não pôde ser ativado: ${result.reasonOrText()}"
                                        }
                                    } else {
                                        modelReady = false
                                        status = if (removed) {
                                            "Modelo local removido do aparelho e espaço liberado."
                                        } else {
                                            "Não foi possível apagar todos os arquivos do modelo ativo."
                                        }
                                    }
                                } catch (e: Exception) {
                                    status = "Falha ao remover o modelo: " + (e.message ?: e.javaClass.simpleName)
                                } finally {
                                    isRemovingModel = false
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Configurar provedor online") },
                        onClick = {
                            showQuickMenu = false
                            val saved = onlineSettingsStore.load()
                            endpointInput = saved.endpoint
                            modelInput = saved.model
                            apiKeyInput = saved.apiKey
                            settingsError = ""
                            showOnlineSettings = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Memória do assistente") },
                        onClick = {
                            showQuickMenu = false
                            savedMemories.clear()
                            savedMemories.addAll(memoryStore.list())
                            memoryDraft = ""
                            memoryStatus = ""
                            showMemoryDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Limpar conversa") },
                        onClick = {
                            showQuickMenu = false
                            historyStore.clear()
                            messages.clear()
                            status = "Histórico local apagado."
                        }
                    )
                }
            }

            Divider(color = Color(0xFF143D33), thickness = 1.dp)
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("◉", color = Neon, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (isGenerating && interactionMode == "Build") "Planejando projeto"
                    else if (isGenerating) "Pensando"
                    else if (isLoadingModel) "Inicializando núcleo"
                    else "Núcleo",
                    color = Neon,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = Muted, fontSize = 18.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("◎", color = Neon.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        isDownloadingModel -> "Instalando modelo local · " + modelDownloadPercent + "%"
                        isGenerating && interactionMode == "Build" && onlineMode -> "Gerando arquivos do projeto · modo online"
                        isGenerating && interactionMode == "Build" -> "Gerando arquivos do projeto · modo local"
                        isGenerating && onlineMode -> "Resposta em andamento · modo online"
                        isGenerating -> "Resposta em andamento · modo local"
                        onlineMode && onlineConfigured -> "Provedor online configurado"
                        onlineMode -> "Modo online precisa de configuração"
                        modelReady -> "Modelo local carregado"
                        else -> "Aguardando modelo local"
                    },
                    color = Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = Muted, fontSize = 16.sp)
            }
            if (isDownloadingModel) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = modelDownloadPercent / 100f,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                    color = Neon,
                    trackColor = Panel
                )
            }

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
                                        if (!speechOutputReady) status = "A saída de voz em português não está disponível neste aparelho."
                                        else {
                                            speechOutput.stop()
                                            val result = speechOutput.speak(message.text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                                            status = if (result == TextToSpeech.SUCCESS) "Lendo a resposta em voz alta…" else "O serviço de voz não conseguiu ler esta resposta."
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) { Text("Ouvir", color = Muted, fontSize = 10.sp) }
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
                        placeholder = { Text("Pergunte qualquer coisa…", color = Muted, fontSize = 13.sp) },
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
                        TextButton(
                            onClick = { showQuickMenu = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("+", color = Neon, fontSize = 20.sp) }

                        Box {
                            Surface(
                                color = Color(0xFF061510),
                                shape = RoundedCornerShape(9.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF164638))
                            ) {
                                TextButton(
                                    onClick = { showModeMenu = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    val modelLabel = if (onlineMode) {
                                        modelInput.trim().ifBlank { "IA online" }.take(20)
                                    } else "IA local"
                                    Text("$interactionMode · $modelLabel" + if (internetToolsEnabled) " · web ⌄" else " ⌄", color = Neon, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false },
                                modifier = Modifier.background(Panel)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Conversa normal") },
                                    onClick = { interactionMode = "Conversa"; showModeMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Build · criar aplicativo") },
                                    onClick = { interactionMode = "Build"; showModeMenu = false }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Núcleo local · offline") },
                                    onClick = {
                                        onlineMode = false
                                        showModeMenu = false
                                        status = if (modelReady) "Núcleo local selecionado." else "Núcleo local selecionado; carregue um modelo antes de conversar."
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (onlineConfigured) "Usar provedor online" else "Configurar provedor online") },
                                    onClick = {
                                        showModeMenu = false
                                        if (onlineConfigured) {
                                            onlineMode = true
                                            status = "Modo online selecionado."
                                        } else {
                                            val saved = onlineSettingsStore.load()
                                            endpointInput = saved.endpoint
                                            modelInput = saved.model
                                            apiKeyInput = saved.apiKey
                                            settingsError = ""
                                            showOnlineSettings = true
                                        }
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text(if (internetToolsEnabled) "Desativar ferramentas de internet" else "Permitir pesquisa na internet") },
                                    onClick = {
                                        showModeMenu = false
                                        internetToolsEnabled = !internetToolsEnabled
                                        status = if (internetToolsEnabled) {
                                            "Internet autorizada para ferramentas: buscas e páginas acessadas serão enviadas aos sites externos; o motor local continua local."
                                        } else {
                                            "Acesso externo das ferramentas desligado."
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        TextButton(
                            enabled = !isGenerating,
                            onClick = {
                                try {
                                    speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem para o Kellvyn Assistente")
                                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                    })
                                } catch (_: ActivityNotFoundException) {
                                    status = "Nenhum reconhecedor de voz está instalado ou disponível."
                                } catch (e: Exception) {
                                    status = "Não foi possível abrir o microfone: " + (e.message ?: e.javaClass.simpleName)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("♩", color = Neon, fontSize = 17.sp) }

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

            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { selectedNav = "AI" },
                        contentPadding = PaddingValues(1.dp)
                    ) { Text("✧", color = if (selectedNav == "AI") Neon else Muted, fontSize = 20.sp) }
                    Text("AI", color = if (selectedNav == "AI") Neon else Muted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { selectedNav = "Navegador"; showNavigatorDialog = true },
                        contentPadding = PaddingValues(1.dp)
                    ) { Text("◎", color = if (selectedNav == "Navegador") Neon else Muted, fontSize = 19.sp) }
                    Text("Navegador", color = if (selectedNav == "Navegador") Neon else Muted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = {
                            selectedNav = "Editor"
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            if (existingProjects.isEmpty()) {
                                builderBrief = ""
                                builderMessage = "Crie um projeto antes de abrir o editor."
                                builtProject = null
                                showBuilderDialog = true
                            } else showProjectsDialog = true
                        },
                        contentPadding = PaddingValues(1.dp)
                    ) { Text("</>", color = if (selectedNav == "Editor") Neon else Muted, fontSize = 14.sp) }
                    Text("Editor", color = if (selectedNav == "Editor") Neon else Muted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = {
                            selectedNav = "Terminal"
                            status = "Terminal local: use projects, cd <id>, ls, cat, write, git status e git commit."
                            showConsoleDialog = true
                        },
                        contentPadding = PaddingValues(1.dp)
                    ) { Text(">_", color = if (selectedNav == "Terminal") Neon else Muted, fontSize = 17.sp) }
                    Text("Terminal", color = if (selectedNav == "Terminal") Neon else Muted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = {
                            selectedNav = "Git"
                            status = "Git local: abra Terminal, use projects, cd <id>, git init, git status, git add . e git commit <mensagem>. Nada é enviado a um servidor."
                            showConsoleDialog = true
                        },
                        contentPadding = PaddingValues(1.dp)
                    ) { Text("⑂", color = if (selectedNav == "Git") Neon else Muted, fontSize = 19.sp) }
                    Text("Git", color = if (selectedNav == "Git") Neon else Muted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { selectedNav = "Abas"; showTabsDialog = true },
                        contentPadding = PaddingValues(1.dp)
                    ) { Text("▦", color = if (selectedNav == "Abas") Neon else Muted, fontSize = 19.sp) }
                    Text("Abas", color = if (selectedNav == "Abas") Neon else Muted, fontSize = 8.sp)
                }
            }
        }

        if (showBuilderDialog) {
            AlertDialog(
                onDismissRequest = { if (!isBuildingProject) showBuilderDialog = false },
                title = { Text("Construtor de aplicativos") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Descreva o app. O agente pede ao modelo ativo um projeto Android, valida os caminhos e guarda os arquivos localmente. A compilação local ainda não está integrada: a saída é código, não um APK.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Text(
                            if (onlineMode) "Motor selecionado: ONLINE" else "Motor selecionado: LOCAL",
                            color = Neon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = builderBrief,
                            onValueChange = {
                                if (it.length <= 3000) {
                                    builderBrief = it
                                    builderMessage = ""
                                    builtProject = null
                                }
                            },
                            label = { Text("O que o aplicativo deve fazer?") },
                            placeholder = { Text("Ex.: crie um gerenciador de tarefas com categorias, busca e salvamento local…") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8
                        )
                        if (isBuildingProject) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Gerando e validando os arquivos. Não feche o app.", color = Muted, fontSize = 12.sp)
                        }
                        if (builderMessage.isNotBlank()) {
                            Text(builderMessage, color = if (builtProject != null) Neon else Warning, fontSize = 12.sp)
                        }
                        builtProject?.let { project ->
                            Text("Arquivos: ${project.fileCount}", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Após gerar, o Kellvyn tenta compilar o APK neste aparelho. O primeiro build pode pedir consentimento para baixar a plataforma Android. ZIP continua opcional.", color = Muted, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            enabled = !isBuildingProject && builderBrief.trim().length >= 12,
                            onClick = {
                                val engine: AssistantEngine = if (onlineMode) onlineEngine else localEngine
                                val ready = if (onlineMode) onlineConfigured else modelReady
                                if (!ready) {
                                    builderMessage = if (onlineMode) {
                                        "Configure o provedor online ou selecione LOCAL e carregue um modelo."
                                    } else {
                                        "Carregue um modelo local ou mude para ONLINE e configure um provedor."
                                    }
                                } else {
                                    isBuildingProject = true
                                    builtProject = null
                                    builderMessage = "O agente está preparando o projeto…"
                                    scope.launch {
                                        when (val result = appBuilderAgent.createProject(engine, builderBrief)) {
                                            is AppBuildResult.Success -> {
                                                builtProject = result.project
                                                builderMessage = "Projeto criado: ${result.project.name}. Compilando APK localmente…"
                                                existingProjects.clear()
                                                existingProjects.addAll(workspaceStore.listProjects())
                                                status = "Construtor criou ${result.project.fileCount} arquivos; compilação local iniciada."
                                                buildProjectAndReport(result.project)
                                                builderMessage = latestBuiltApk?.let { "APK gerado: ${it.name}" } ?: status
                                            }
                                            is AppBuildResult.Failure -> {
                                                builderMessage = result.reason
                                            }
                                        }
                                        isBuildingProject = false
                                    }
                                }
                            }
                        ) { Text("Gerar projeto") }
                        builtProject?.let { project ->
                            TextButton(
                                onClick = {
                                    pendingExportProject = project
                                    zipExportPicker.launch("${project.id}.zip")
                                }
                            ) { Text("Exportar ZIP") }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isBuildingProject,
                        onClick = { showBuilderDialog = false }
                    ) { Text("Fechar") }
                }
            )
        }

        if (showEditorDialog) {
            AlertDialog(
                onDismissRequest = { if (!isSavingFile) showEditorDialog = false },
                title = { Text(activeEditProject?.name ?: "Editor de código") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Editor local de arquivos. Salvar não executa nem compila o código.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Text("ARQUIVOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        editablePaths.forEach { path ->
                            TextButton(
                                onClick = {
                                    val project = activeEditProject
                                    if (project != null) {
                                        try {
                                            val fileText = workspaceStore.readFile(project.id, path)
                                            if (fileText.length > MAX_EDITOR_CHARS) {
                                                editorStatus = "Arquivo grande demais para editar nesta tela. Exporte o ZIP para editá-lo fora do app."
                                            } else {
                                                activeFilePath = path
                                                rememberOpenTab(project.id, path)
                                                editorContent = fileText
                                                editorStatus = ""
                                            }
                                        } catch (e: Exception) {
                                            editorStatus = "Falha ao abrir arquivo: " + (e.message ?: e.javaClass.simpleName)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    (if (path == activeFilePath) "▸ " else "") + path,
                                    color = if (path == activeFilePath) Neon else Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (activeFilePath.isNotBlank()) {
                            Text(activeFilePath, color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editorContent,
                                onValueChange = { if (it.length <= MAX_EDITOR_CHARS) editorContent = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 8,
                                maxLines = 16,
                                label = { Text("Conteúdo do arquivo") },
                                placeholder = { Text("Código do arquivo…") }
                            )
                        }
                        if (editorStatus.isNotBlank()) {
                            Text(editorStatus, color = if (editorStatus.startsWith("Salvo")) Neon else Warning, fontSize = 12.sp)
                        }
                        if (isSavingFile) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Salvando arquivo…", color = Muted, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isSavingFile && activeEditProject != null && activeFilePath.isNotBlank(),
                        onClick = {
                            val project = activeEditProject
                            if (project != null) {
                                isSavingFile = true
                                editorStatus = "Salvando…"
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            workspaceStore.addOrReplaceFiles(
                                                project.id,
                                                listOf(ProjectFileInput(activeFilePath, editorContent))
                                            )
                                        }
                                        existingProjects.clear()
                                        existingProjects.addAll(workspaceStore.listProjects())
                                        activeEditProject = workspaceStore.getProject(project.id)
                                        editorStatus = "Salvo no workspace local. O compilador local ainda não está integrado, então este arquivo não foi compilado."
                                    } catch (e: Exception) {
                                        editorStatus = "Falha ao salvar: " + (e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        isSavingFile = false
                                    }
                                }
                            }
                        }
                    ) { Text("Salvar arquivo") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            enabled = !isSavingFile,
                            onClick = {
                                val project = activeEditProject
                                if (project != null) {
                                    pendingExportProject = project
                                    zipExportPicker.launch("${project.id}.zip")
                                }
                            }
                        ) { Text("Exportar ZIP") }
                        TextButton(
                            enabled = !isSavingFile,
                            onClick = { showEditorDialog = false }
                        ) { Text("Fechar") }
                    }
                }
            )
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
                            Text("Ainda não existem projetos. Use CONSTRUTOR para criar o primeiro.", color = Muted)
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
                                                    if (activeFilePath.isNotBlank()) rememberOpenTab(project.id, activeFilePath)
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
                        builderBrief = ""
                        builderMessage = ""
                        builtProject = null
                        showBuilderDialog = true
                    }) { Text("Criar projeto") }
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
                title = { Text("Navegador do Android") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Para manter o Kellvyn nativo e sem WebView, os links são abertos no navegador instalado no Android.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = browserUrl,
                            onValueChange = { browserUrl = it.take(2_000) },
                            label = { Text("Endereço HTTPS") },
                            placeholder = { Text("https://exemplo.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val typed = browserUrl.trim()
                        val url = if (typed.startsWith("https://", true) || typed.startsWith("http://", true)) typed else "https://$typed"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            status = "Link entregue ao navegador do Android."
                            showNavigatorDialog = false
                        } catch (e: Exception) {
                            status = "Não há navegador disponível ou o endereço é inválido: " + (e.message ?: e.javaClass.simpleName)
                        }
                    }) { Text("Abrir") }
                },
                dismissButton = { TextButton(onClick = { showNavigatorDialog = false }) { Text("Fechar") } }
            )
        }

        if (showTabsDialog) {
            AlertDialog(
                onDismissRequest = { showTabsDialog = false },
                title = { Text("Abas abertas") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (openTabs.isEmpty()) {
                            Text("Nenhuma aba de arquivo aberta ainda. Entre em Editor e abra um arquivo de projeto.", color = Muted, fontSize = 12.sp)
                        } else {
                            openTabs.forEach { entry ->
                                val parts = entry.split("::", limit = 2)
                                val projectId = parts.firstOrNull().orEmpty()
                                val filePath = parts.getOrNull(1).orEmpty()
                                val project = workspaceStore.getProject(projectId)
                                if (project != null && filePath.isNotBlank()) {
                                    TextButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            try {
                                                activeEditProject = project
                                                editablePaths = workspaceStore.listFiles(project.id)
                                                activeFilePath = filePath
                                                editorContent = workspaceStore.readFile(project.id, filePath).take(MAX_EDITOR_CHARS)
                                                editorStatus = ""
                                                showTabsDialog = false
                                                showEditorDialog = true
                                            } catch (e: Exception) {
                                                status = "Não foi possível reabrir a aba: " + (e.message ?: e.javaClass.simpleName)
                                            }
                                        }
                                    ) {
                                        Text("${project.name} / ${filePath}", color = if (activeEditProject?.id == projectId && activeFilePath == filePath) Neon else Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { openTabs = emptyList(); status = "Lista de abas limpa."; showTabsDialog = false }) { Text("Fechar todas") } },
                dismissButton = { TextButton(onClick = { showTabsDialog = false }) { Text("Fechar") } }
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
                            "Salve apenas fatos que você quer que o assistente lembre. Nada é extraído automaticamente. As memórias ficam neste aparelho; no modo ONLINE, o conteúdo relevante será enviado junto com a conversa ao provedor escolhido.",
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
                memories = memoryStore,
                localModelReady = modelReady,
                onlineMode = onlineMode,
                onlineConfigured = onlineConfigured,
                onDismiss = { showConsoleDialog = false },
                onAskAgent = { request, requestApproval ->
                    if (isGenerating) {
                        "O agente principal já está processando outra solicitação. Aguarde terminar antes de pedir ajuda pelo terminal."
                    } else if (onlineMode && !onlineConfigured) {
                        "O provedor online não está configurado. Configure-o no menu antes de pedir ajuda ao agente."
                    } else if (!onlineMode && !modelReady) {
                        "Nenhum modelo local está carregado. Carregue um modelo .litertlm ou selecione o provedor online para usar o agente."
                    } else {
                        messages.add(ChatMessage(request, true))
                        saveHistory()
                        isGenerating = true
                        status = "O agente está analisando o terminal sem fechar esta tela…"
                        try {
                            val turns = buildConversationTurns()
                            when (
                                val result = assistantAgent.respond(
                                    engine = if (onlineMode) onlineEngine else localEngine,
                                    conversation = turns,
                                    internetAllowed = internetToolsEnabled,
                                    onToolStatus = { toolStatus -> status = toolStatus },
                                    requestApproval = requestApproval
                                )
                            ) {
                                is AssistantResult.Success -> {
                                    messages.add(ChatMessage(result.text, false))
                                    saveHistory()
                                    status = "O agente respondeu na tela Terminal."
                                    result.text
                                }
                                is AssistantResult.Unavailable -> {
                                    val answer = "IA indisponível: ${result.reason}"
                                    messages.add(ChatMessage(answer, false))
                                    saveHistory()
                                    status = result.reason
                                    answer
                                }
                                is AssistantResult.Failure -> {
                                    val answer = "Falha do agente: ${result.reason}"
                                    messages.add(ChatMessage(answer, false))
                                    saveHistory()
                                    status = result.reason
                                    answer
                                }
                            }
                        } catch (e: Exception) {
                            val answer = "Falha inesperada do agente: ${e.message ?: e.javaClass.simpleName}"
                            messages.add(ChatMessage(answer, false))
                            saveHistory()
                            status = answer
                            answer
                        } finally {
                            isGenerating = false
                        }
                    }
                }
            )
        }

        if (showSdkLicenseDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSdkLicenseDialog = false
                    pendingLicenseProjectId = null
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
                        onClick = {
                            val projectId = pendingLicenseProjectId
                            localAndroidCompiler.acceptSdkLicense()
                            showSdkLicenseDialog = false
                            pendingLicenseProjectId = null
                            val project = projectId?.let { workspaceStore.getProject(it) }
                            if (project == null) {
                                status = "O projeto que aguardava a licença não foi encontrado."
                            } else {
                                scope.launch {
                                    isGenerating = true
                                    try {
                                        buildProjectAndReport(project)
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

        if (showOnlineSettings) {
            AlertDialog(
                onDismissRequest = { showOnlineSettings = false },
                title = { Text("Provedor online") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Use um endpoint compatível com a API Chat Completions e HTTPS. Kellvyn não fornece chave nem assinatura. A chave será criptografada com Android Keystore.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = { endpointInput = it },
                            label = { Text("Endpoint HTTPS") },
                            placeholder = { Text("https://seu-provedor.example/v1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("Nome do modelo") },
                            placeholder = { Text("modelo-do-provedor") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Chave API (opcional)") },
                            placeholder = { Text("Obrigatória para alguns provedores") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (settingsError.isNotBlank()) {
                            Text(settingsError, color = Warning, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val endpoint = endpointInput.trim()
                        val model = modelInput.trim()
                        if (!endpoint.startsWith("https://", ignoreCase = true) || model.isBlank()) {
                            settingsError = "Informe uma URL HTTPS válida e o nome do modelo."
                        } else {
                            try {
                                onlineSettingsStore.save(endpoint, model, apiKeyInput)
                                onlineConfigured = onlineSettingsStore.load().isConfigured
                                showOnlineSettings = false
                                status = "Provedor online salvo. Mensagens só serão enviadas quando o modo ONLINE estiver selecionado."
                            } catch (e: Exception) {
                                settingsError = "Não foi possível salvar as credenciais com segurança: " +
                                    (e.message ?: e.javaClass.simpleName)
                            }
                        }
                    }) { Text("Salvar") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            onlineSettingsStore.clear()
                            endpointInput = ""
                            modelInput = ""
                            apiKeyInput = ""
                            onlineConfigured = false
                            settingsError = ""
                            showOnlineSettings = false
                            status = "Configuração online removida"
                        }) { Text("Remover") }
                        TextButton(onClick = { showOnlineSettings = false }) { Text("Cancelar") }
                    }
                }
            )
        }
    }
}
