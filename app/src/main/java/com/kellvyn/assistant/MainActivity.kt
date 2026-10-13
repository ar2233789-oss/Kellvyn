package com.kellvyn.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.kellvyn.assistant.ai.AssistantResult
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
import com.kellvyn.assistant.workspace.AppBuildResult
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import com.kellvyn.assistant.workspace.ProjectFileInput
import com.kellvyn.assistant.workspace.WorkspaceProject
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
private const val MAX_CONTEXT_MESSAGES = 24
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
    val appBuilderAgent = remember(workspaceStore) { AppBuilderAgent(workspaceStore) }
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
    var onlineConfigured by remember { mutableStateOf(initialOnlineSettings.isConfigured) }
    var showOnlineSettings by remember { mutableStateOf(false) }
    var showConsoleDialog by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showNavigatorDialog by remember { mutableStateOf(false) }
    var showTabsDialog by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf("https://developer.android.com") }
    var interactionMode by remember { mutableStateOf("Build") }
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

    fun rememberOpenTab(projectId: String, path: String) {
        val tab = "$projectId::$path"
        openTabs = (openTabs + tab).distinct().takeLast(40)
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
        isGenerating = true
        status = if (interactionMode == "Build") {
            "Planejando e gerando arquivos do projeto com o motor selecionado…"
        } else if (onlineSelected) {
            "Enviando ao endpoint online escolhido…"
        } else {
            "Gerando resposta no aparelho…"
        }

        val selectedEngine: AssistantEngine = if (onlineSelected) onlineEngine else localEngine
        scope.launch {
            try {
                if (interactionMode == "Build") {
                    when (val result = appBuilderAgent.createProject(selectedEngine, text)) {
                        is AppBuildResult.Success -> {
                            val project = result.project
                            existingProjects.clear()
                            existingProjects.addAll(workspaceStore.listProjects())
                            builtProject = project
                            messages.add(
                                ChatMessage(
                                    "Projeto \"${project.name}\" criado e validado com ${project.fileCount} arquivos. Os arquivos foram guardados neste aparelho. Ainda não é um APK: a compilação Android local precisa ser integrada e testada. Use Editor para revisar o código, Git para versionar e ZIP como exportação secundária.",
                                    false
                                )
                            )
                            status = "Arquivos de projeto criados localmente. Nenhum APK foi compilado."
                        }
                        is AppBuildResult.Failure -> {
                            messages.add(ChatMessage("Não consegui concluir a geração do projeto: ${result.reason}", false))
                            status = result.reason
                        }
                    }
                } else {
                    val memoryContext = memoryStore.promptContext()
                    val turns = buildList {
                        if (memoryContext.isNotBlank()) add(
                            AssistantTurn(role = AssistantTurn.Role.SYSTEM, content = memoryContext)
                        )
                        addAll(messages.takeLast(MAX_CONTEXT_MESSAGES).mapNotNull { message ->
                            if (!message.fromUser && message.text.startsWith("Kellvyn IA está pronto.")) null
                            else AssistantTurn(
                                role = if (message.fromUser) AssistantTurn.Role.USER else AssistantTurn.Role.ASSISTANT,
                                content = message.text
                            )
                        })
                    }
                    when (val result = selectedEngine.reply(turns)) {
                        is AssistantResult.Success -> {
                            messages.add(ChatMessage(result.text, false))
                            status = if (onlineSelected) "Resposta recebida do provedor online"
                                else "Resposta gerada localmente • nenhuma mensagem enviada a servidor"
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
        if (localEngine.hasSavedModel()) {
            isLoadingModel = true
            if (!onlineMode) status = "Modelo local encontrado; inicializando…"
            try {
                updateModelResult(localEngine.loadSavedModel())
            } finally {
                isLoadingModel = false
            }
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
                    Text(if (onlineMode) "PROVEDOR ONLINE OPCIONAL" else "NÚCLEO LOCAL • OFFLINE", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
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
                        text = { Text("Carregar modelo local (.litertlm)") },
                        onClick = { showQuickMenu = false; modelPicker.launch(arrayOf("*/*")) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (localEngine.hasSavedModel()) "Modelo local instalado" else "Baixar modelo offline (~374 MB)") },
                        enabled = !localEngine.hasSavedModel() && !isDownloadingModel && !isLoadingModel,
                        onClick = {
                            showQuickMenu = false
                            isDownloadingModel = true
                            modelDownloadPercent = 0
                            status = "Conectando à fonte pública para baixar o modelo local…"
                            scope.launch {
                                try {
                                    val result = localEngine.downloadRecommendedModel { bytes, total ->
                                        val percent = if (total > 0L) ((bytes * 100L) / total).toInt().coerceIn(0, 100) else 0
                                        scope.launch {
                                            modelDownloadPercent = percent
                                            status = "Baixando modelo offline: " + percent + "% (" + (bytes / (1024L * 1024L)) + " MB)"
                                        }
                                    }
                                    updateModelResult(result)
                                    if (result is AssistantResult.Success) status = "Modelo local baixado e inicializado."
                                } catch (e: Exception) {
                                    status = "Falha ao baixar modelo: " + (e.message ?: e.javaClass.simpleName)
                                } finally {
                                    isDownloadingModel = false
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Apagar modelo local salvo") },
                        enabled = localEngine.hasSavedModel() && !isGenerating && !isDownloadingModel && !isLoadingModel && !isRemovingModel,
                        onClick = {
                            showQuickMenu = false
                            isRemovingModel = true
                            status = "Removendo o modelo local…"
                            scope.launch {
                                try {
                                    val removed = withContext(Dispatchers.IO) {
                                        localEngine.removeSavedModel()
                                    }
                                    modelReady = false
                                    status = if (removed) {
                                        "Modelo removido do aparelho e espaço liberado."
                                    } else {
                                        "Não foi possível apagar o arquivo do modelo."
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
                                    Text("$interactionMode · $modelLabel ⌄", color = Neon, fontSize = 10.sp, maxLines = 1)
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
                            Text("Arquivos guardados localmente. Isto ainda não é um APK: a compilação local precisa ser integrada. ZIP é apenas uma exportação opcional.", color = Muted, fontSize = 12.sp)
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
                                                builderMessage = "Projeto criado: ${result.project.name}"
                                                existingProjects.clear()
                                                existingProjects.addAll(workspaceStore.listProjects())
                                                status = "Construtor criou ${result.project.fileCount} arquivos. Revise o código antes de compilar."
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
                onDismiss = { showConsoleDialog = false }
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
