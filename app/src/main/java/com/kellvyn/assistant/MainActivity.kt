package com.kellvyn.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val Night = Color(0xFF080B12)
private val Panel = Color(0xFF121925)
private val Neon = Color(0xFF4DDBFF)
private val Muted = Color(0xFF9AA8BA)
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
    var onlineMode by remember { mutableStateOf(false) }
    var onlineConfigured by remember { mutableStateOf(initialOnlineSettings.isConfigured) }
    var showOnlineSettings by remember { mutableStateOf(false) }
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
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Night).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).background(Panel, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("K", color = Neon, fontSize = 25.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("KELLVYN IA", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("ASSISTENTE • NÚCLEO HÍBRIDO", fontSize = 10.sp, color = Neon)
                }
                TextButton(onClick = {
                    historyStore.clear()
                    messages.clear()
                    status = "Histórico local apagado"
                }) {
                    Text("Limpar", color = Muted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onlineMode = false
                        status = if (modelReady) "Motor local pronto no aparelho" else "Selecione um modelo .litertlm para ativar a IA local"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!onlineMode) Neon else Panel,
                        contentColor = if (!onlineMode) Night else Color.White
                    )
                ) { Text("LOCAL", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        onlineMode = true
                        status = if (onlineConfigured) {
                            "Modo online selecionado. As mensagens serão enviadas ao endpoint configurado."
                        } else {
                            "Configure seu endpoint HTTPS e modelo antes de enviar mensagens."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (onlineMode) Neon else Panel,
                        contentColor = if (onlineMode) Night else Color.White
                    )
                ) { Text("ONLINE", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        builderBrief = ""
                        builderMessage = ""
                        builtProject = null
                        showBuilderDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("CONSTRUTOR", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = {
                        existingProjects.clear()
                        existingProjects.addAll(workspaceStore.listProjects())
                        showProjectsDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("PROJETOS", fontSize = 10.sp) }
                OutlinedButton(
                    onClick = {
                        savedMemories.clear()
                        savedMemories.addAll(memoryStore.list())
                        memoryDraft = ""
                        memoryStatus = ""
                        showMemoryDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("MEMÓRIA", fontSize = 10.sp) }
            }

            Spacer(Modifier.height(12.dp))
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("PAINEL DO NÚCLEO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Interface Android", color = Color.White, modifier = Modifier.weight(1f))
                        Text("PRONTA", color = Neon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Motor local", color = Color.White, modifier = Modifier.weight(1f))
                        Text(if (modelReady) "CARREGADO" else "SEM MODELO", color = if (modelReady) Neon else Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Provedor online", color = Color.White, modifier = Modifier.weight(1f))
                        Text(if (onlineConfigured) "CONFIGURADO" else "OPCIONAL", color = if (onlineConfigured) Neon else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))

                    if (!onlineMode) {
                        Button(
                            enabled = !isGenerating && !isLoadingModel && !isRemovingModel,
                            onClick = { modelPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                when {
                                    isLoadingModel -> "Carregando modelo…"
                                    modelReady -> "Trocar modelo local (.litertlm)"
                                    else -> "Selecionar modelo local (.litertlm)"
                                }
                            )
                        }
                        if (localEngine.hasSavedModel()) {
                            Text(
                                "Modelo armazenado: ${localEngine.savedModelSizeBytes() / (1024L * 1024L)} MB",
                                color = Muted,
                                fontSize = 10.sp
                            )
                            TextButton(
                                enabled = !isGenerating && !isLoadingModel && !isRemovingModel,
                                onClick = {
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
                            ) {
                                Text(if (isRemovingModel) "Removendo…" else "Apagar modelo salvo", color = Warning, fontSize = 11.sp)
                            }
                        }
                        Text(
                            "O modelo fica no armazenamento privado do aparelho. A inferência local não envia mensagens a um servidor.",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    } else {
                        Button(
                            onClick = {
                                val saved = onlineSettingsStore.load()
                                endpointInput = saved.endpoint
                                modelInput = saved.model
                                apiKeyInput = saved.apiKey
                                settingsError = ""
                                showOnlineSettings = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Configurar provedor online") }
                        Text(
                            "As mensagens e o contexto da conversa serão enviados ao endpoint escolhido. O uso pode ter custos definidos pelo provedor.",
                            color = Warning,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("CONVERSA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = chatListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = if (message.fromUser) Color(0xFF123044) else Panel,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                message.text,
                                modifier = Modifier.padding(14.dp),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        if (!message.fromUser) {
                            val clipboard = LocalClipboardManager.current
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (!speechOutputReady) {
                                            status = "A saída de voz em português não está disponível neste aparelho."
                                        } else {
                                            speechOutput.stop()
                                            val result = speechOutput.speak(
                                                message.text,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                UUID.randomUUID().toString()
                                            )
                                            status = if (result == TextToSpeech.SUCCESS) {
                                                "Lendo a resposta em voz alta…"
                                            } else {
                                                "O serviço de voz não conseguiu ler esta resposta."
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Ouvir", color = if (speechOutputReady) Neon else Muted, fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(message.text))
                                        status = "Resposta copiada para a área de transferência"
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(17.dp)) {
                                        val line = 1.5.dp.toPx()
                                        drawRoundRect(
                                            color = Neon.copy(alpha = 0.65f),
                                            topLeft = Offset(size.width * 0.27f, size.height * 0.08f),
                                            size = Size(size.width * 0.58f, size.height * 0.68f),
                                            cornerRadius = CornerRadius(2.dp.toPx()),
                                            style = Stroke(width = line)
                                        )
                                        drawRoundRect(
                                            color = Neon,
                                            topLeft = Offset(size.width * 0.12f, size.height * 0.27f),
                                            size = Size(size.width * 0.58f, size.height * 0.65f),
                                            cornerRadius = CornerRadius(2.dp.toPx()),
                                            style = Stroke(width = line)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copiar", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            Text(status, color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MAX_INPUT_CHARS) draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escreva uma mensagem…", color = Muted) },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 4,
                    enabled = !isGenerating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Neon,
                        unfocusedBorderColor = Color(0xFF293648),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Neon
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = !isGenerating,
                    onClick = {
                        try {
                            speechLauncher.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem para o Kellvyn IA")
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                }
                            )
                        } catch (_: ActivityNotFoundException) {
                            status = "Nenhum reconhecedor de voz está instalado ou disponível."
                        } catch (e: Exception) {
                            status = "Não foi possível abrir o microfone: " + (e.message ?: "erro")
                        }
                    }
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val stroke = Stroke(width = 1.8.dp.toPx())
                        val micWidth = size.width * 0.28f
                        drawRoundRect(
                            color = Neon,
                            topLeft = Offset((size.width - micWidth) / 2f, size.height * 0.08f),
                            size = Size(micWidth, size.height * 0.52f),
                            cornerRadius = CornerRadius(micWidth / 2f)
                        )
                        drawArc(
                            color = Neon,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.24f, size.height * 0.22f),
                            size = Size(size.width * 0.52f, size.height * 0.54f),
                            style = stroke
                        )
                        drawLine(
                            color = Neon,
                            start = Offset(size.width * 0.5f, size.height * 0.75f),
                            end = Offset(size.width * 0.5f, size.height * 0.92f),
                            strokeWidth = 1.8.dp.toPx()
                        )
                        drawLine(
                            color = Neon,
                            start = Offset(size.width * 0.34f, size.height * 0.92f),
                            end = Offset(size.width * 0.66f, size.height * 0.92f),
                            strokeWidth = 1.8.dp.toPx()
                        )
                    }
                }
                Button(
                    enabled = !isGenerating,
                    onClick = {
                        val text = draft.trim()
                        val onlineSelected = onlineMode
                        if (text.isNotEmpty()) {
                            if (onlineSelected && !onlineConfigured) {
                                status = "Configure o provedor online antes de enviar mensagens."
                            } else if (!onlineSelected && !modelReady) {
                                status = "Carregue um modelo .litertlm primeiro. A mensagem não foi enviada."
                            } else {
                                messages.add(ChatMessage(text, true))
                                saveHistory()
                                draft = ""
                                isGenerating = true
                                status = if (onlineSelected) "Enviando ao endpoint online escolhido…" else "Gerando resposta no aparelho…"
                                val selectedEngine: AssistantEngine = if (onlineSelected) onlineEngine else localEngine
                                val memoryContext = memoryStore.promptContext()
                                val turns = buildList {
                                    if (memoryContext.isNotBlank()) {
                                        add(
                                            AssistantTurn(
                                                role = AssistantTurn.Role.SYSTEM,
                                                content = memoryContext
                                            )
                                        )
                                    }
                                    addAll(
                                        messages.takeLast(MAX_CONTEXT_MESSAGES).mapNotNull { message ->
                                            if (!message.fromUser && message.text.startsWith("Kellvyn IA está pronto.")) {
                                                null
                                            } else {
                                                AssistantTurn(
                                                    role = if (message.fromUser) AssistantTurn.Role.USER else AssistantTurn.Role.ASSISTANT,
                                                    content = message.text
                                                )
                                            }
                                        }
                                    )
                                }
                                scope.launch {
                                    try {
                                        when (val result = selectedEngine.reply(turns)) {
                                            is AssistantResult.Success -> {
                                                messages.add(ChatMessage(result.text, false))
                                                saveHistory()
                                                status = if (onlineSelected) {
                                                    "Resposta recebida do provedor online"
                                                } else {
                                                    "Resposta gerada localmente • nenhuma mensagem enviada a servidor"
                                                }
                                            }
                                            is AssistantResult.Unavailable -> status = result.reason
                                            is AssistantResult.Failure -> status = result.reason
                                        }
                                    } catch (e: Exception) {
                                        status = "Falha inesperada: " + (e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) { Text(if (isGenerating) "…" else "➤") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Versão 0.3.0 • IA local + provedor online opcional", color = Muted, fontSize = 10.sp)
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
                            "Descreva o app. O agente vai pedir ao modelo ativo um projeto Android, validar os caminhos e guardar os arquivos localmente. O código gerado não é executado dentro do Kellvyn.",
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
                            Text("Projeto guardado no armazenamento privado do aparelho. Exporte o ZIP para usar os arquivos fora do Kellvyn.", color = Muted, fontSize = 12.sp)
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
                                        editorStatus = "Salvo localmente. Exporte o ZIP para compilar e testar."
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
