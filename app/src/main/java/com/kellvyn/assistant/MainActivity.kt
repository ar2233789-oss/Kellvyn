package com.kellvyn.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.AssistantTurn
import com.kellvyn.assistant.ai.LocalLiteRtAssistantEngine
import com.kellvyn.assistant.data.ChatHistoryStore
import kotlinx.coroutines.launch

private val Night = Color(0xFF080B12)
private val Panel = Color(0xFF121925)
private val Neon = Color(0xFF4DDBFF)
private val Muted = Color(0xFF9AA8BA)

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
    val localEngine = remember(context) { LocalLiteRtAssistantEngine(context) }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Selecione um modelo .litertlm para ativar a IA local") }
    var modelReady by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    val messages = remember(historyStore) {
        mutableStateListOf<ChatMessage>().apply {
            val saved = historyStore.load()
            if (saved.isEmpty()) {
                add(
                    ChatMessage(
                        "Kellvyn IA — interface carregada. As respostas serão geradas por um modelo local somente depois que você selecionar um arquivo compatível.",
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

    fun applyModelResult(result: AssistantResult) {
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

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                modelReady = false
                status = "Copiando e inicializando o modelo local…"
                applyModelResult(localEngine.importAndLoad(uri))
            }
        }
    }

    LaunchedEffect(localEngine) {
        if (localEngine.hasSavedModel()) {
            status = "Modelo local encontrado; inicializando…"
            applyModelResult(localEngine.loadSavedModel())
        }
    }

    DisposableEffect(localEngine) {
        onDispose { localEngine.close() }
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
                    Text("ASSISTENTE • MOTOR LOCAL", fontSize = 10.sp, color = Neon)
                }
                TextButton(onClick = {
                    historyStore.clear()
                    messages.clear()
                    status = if (modelReady) "Histórico apagado; modelo local continua carregado" else "Histórico local apagado"
                }) {
                    Text("Limpar", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("PAINEL DO NÚCLEO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Interface Android", color = Color.White, modifier = Modifier.weight(1f))
                        Text("IMPLEMENTADA", color = Neon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Inferência local", color = Color.White, modifier = Modifier.weight(1f))
                        Text(if (modelReady) "ATIVA" else "SEM MODELO", color = if (modelReady) Neon else Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Histórico no aparelho", color = Color.White, modifier = Modifier.weight(1f))
                        Text("ATIVO", color = Neon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (modelReady) "Trocar modelo local (.litertlm)" else "Selecionar modelo local (.litertlm)")
                    }
                    Text(
                        "O arquivo do modelo fica no armazenamento privado do app. Modelos podem ocupar centenas de MB.",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("CONVERSA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
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
                }
            }
            Text(status, color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
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
                Button(
                    enabled = !isGenerating,
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            if (!modelReady) {
                                status = "Carregue um modelo .litertlm primeiro. A mensagem não foi enviada."
                            } else {
                                messages.add(ChatMessage(text, true))
                                saveHistory()
                                draft = ""
                                isGenerating = true
                                status = "Gerando resposta no aparelho…"
                                scope.launch {
                                    val turns = messages.mapNotNull { message ->
                                        if (!message.fromUser && message.text.startsWith("Kellvyn IA — interface carregada.")) {
                                            null
                                        } else {
                                            AssistantTurn(
                                                role = if (message.fromUser) AssistantTurn.Role.USER else AssistantTurn.Role.ASSISTANT,
                                                content = message.text
                                            )
                                        }
                                    }
                                    when (val result = localEngine.reply(turns)) {
                                        is AssistantResult.Success -> {
                                            messages.add(ChatMessage(result.text, false))
                                            saveHistory()
                                            status = "Resposta gerada localmente • nenhuma mensagem foi enviada para um servidor"
                                        }
                                        is AssistantResult.Unavailable -> status = result.reason
                                        is AssistantResult.Failure -> status = result.reason
                                    }
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(if (isGenerating) "…" else "➤")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Versão 0.2.0 • motor local LiteRT-LM • modelo fornecido pelo usuário",
                color = Muted,
                fontSize = 10.sp
            )
        }
    }
}
