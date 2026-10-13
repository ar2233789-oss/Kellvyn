package com.kellvyn.assistant.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kellvyn.assistant.data.UserMemoryStore
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore

private val ConsoleNight = Color(0xFF080B12)
private val ConsolePanel = Color(0xFF121925)
private val ConsoleNeon = Color(0xFF4DDBFF)
private val ConsoleMuted = Color(0xFF9AA8BA)

/**
 * Real commands over Kellvyn's own project workspace. This is intentionally not
 * advertised as a system shell and cannot execute arbitrary operating-system commands.
 */
@Composable
fun WorkspaceConsoleDialog(
    workspace: ProjectWorkspaceStore,
    memories: UserMemoryStore,
    localModelReady: Boolean,
    onlineMode: Boolean,
    onlineConfigured: Boolean,
    onDismiss: () -> Unit
) {
    var command by remember { mutableStateOf("help") }
    var output by remember {
        mutableStateOf(
            "KELLVYN WORKSPACE CONSOLE v1\nComandos disponíveis: help, status, projects, memories, files <id>, read <id> <path>\n" +
                "Este console opera apenas no espaço de projetos do Kellvyn; não executa comandos do Android."
        )
    }

    fun execute(raw: String) {
        val line = raw.trim()
        if (line.isBlank()) return
        val parts = line.split(Regex("\\s+"), limit = 3)
        val result = try {
            when (parts.first().lowercase()) {
                "help" -> """
                    help — lista os comandos
                    status — status dos motores configurados
                    projects — lista projetos locais
                    memories — lista as memórias salvas por você
                    files <id> — lista os arquivos de um projeto
                    read <id> <path> — lê um arquivo textual do projeto
                    clear — limpa o histórico do console
                    """.trimIndent()
                "status" -> buildString {
                    appendLine("Motor local: " + if (localModelReady) "carregado" else "sem modelo carregado")
                    appendLine("Modo selecionado: " + if (onlineMode) "ONLINE" else "LOCAL")
                    appendLine("Provedor online: " + if (onlineConfigured) "configurado" else "não configurado")
                    appendLine("Projetos no espaço local: ${workspace.listProjects().size}")
                    appendLine("Memórias explícitas salvas: ${memories.list().size}")
                    appendLine("Compilação de APK de projetos gerados dentro do aparelho: ainda não implementada")
                }
                "projects" -> {
                    val projects = workspace.listProjects()
                    if (projects.isEmpty()) "Nenhum projeto local."
                    else projects.joinToString("\n") { "${it.id} — ${it.name} (${it.fileCount} arquivos)" }
                }
                "memories" -> {
                    val facts = memories.list()
                    if (facts.isEmpty()) "Nenhuma memória salva."
                    else facts.mapIndexed { index, fact -> "${index + 1}. ${fact.text}" }.joinToString("\n")
                }
                "files" -> {
                    if (parts.size < 2) "Uso: files <id>"
                    else workspace.listFiles(parts[1]).joinToString("\n").ifBlank { "O projeto não tem arquivos." }
                }
                "read" -> {
                    if (parts.size < 3) {
                        "Uso: read <id> <path>"
                    } else {
                        val filePath = parts[2]
                        workspace.readFile(parts[1], filePath).take(MAX_READ_CHARS)
                    }
                }
                "clear" -> {
                    output = ""
                    return
                }
                else -> "Comando não reconhecido. Digite help. Este console não aceita comandos arbitrários do sistema."
            }
        } catch (e: Exception) {
            "ERRO: " + (e.message ?: e.javaClass.simpleName)
        }
        output = (output + "\n\n> " + line + "\n" + result).takeLast(MAX_OUTPUT_CHARS)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            color = ConsoleNight,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(ConsoleNight).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CONSOLE DO PROJETO", color = ConsoleNeon, fontSize = 14.sp)
                        Text("Comandos reais sobre os projetos locais", color = ConsoleMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Comando") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { execute(command) })
                    )
                    Button(onClick = { execute(command) }) { Text("Executar") }
                }
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = ConsolePanel,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        Text(output, color = Color(0xFFE1F5FF), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                Text(
                    "Limite de segurança: sem acesso a shell, arquivos fora do workspace ou execução de código gerado.",
                    color = ConsoleMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private const val MAX_READ_CHARS = 12_000
private const val MAX_OUTPUT_CHARS = 30_000
