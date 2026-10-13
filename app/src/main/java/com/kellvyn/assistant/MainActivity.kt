package com.kellvyn.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    var draft by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Kellvyn IA — estrutura inicial carregada. O motor de IA ainda precisa ser conectado; não vou fingir que esta interface já pensa ou responde com um modelo real.", false)
        )
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
                    Text("ASSISTENTE • NÚCLEO EM CONSTRUÇÃO", fontSize = 10.sp, color = Neon)
                }
                Text("●", color = Color(0xFFFFC857), fontSize = 12.sp)
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
                        Text("Modelo de IA", color = Color.White, modifier = Modifier.weight(1f))
                        Text("PENDENTE", color = Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Build APK", color = Color.White, modifier = Modifier.weight(1f))
                        Text("NÃO TESTADA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
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
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escreva uma mensagem…", color = Muted) },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 4,
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
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            messages.add(ChatMessage(text, true))
                            messages.add(ChatMessage("Mensagem registrada na interface. O mecanismo de resposta inteligente ainda não está conectado.", false))
                            draft = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) { Text("➤") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Versão 0.1.0 • respostas inteligentes ainda não ativadas", color = Muted, fontSize = 10.sp)
        }
    }
}
