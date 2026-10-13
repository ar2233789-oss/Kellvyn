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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kellvyn.assistant.data.UserMemoryStore
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ConsoleNight = Color(0xFF020806)
private val ConsolePanel = Color(0xFF071713)
private val ConsoleNeon = Color(0xFF38E6BC)
private val ConsoleMuted = Color(0xFF91B8AD)
private val ConsoleBorder = Color(0xFF164638)

/**
 * A workspace terminal for local project files and local Git operations. Commands are parsed
 * by WorkspaceTerminal; arbitrary shell code is never passed to Android's shell.
 */
@Composable
fun WorkspaceConsoleDialog(
    workspace: ProjectWorkspaceStore,
    terminal: WorkspaceTerminal,
    memories: UserMemoryStore,
    localModelReady: Boolean,
    onlineMode: Boolean,
    onlineConfigured: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val manualShell = remember { ManualShellExecutor() }
    var command by remember { mutableStateOf("help") }
    var terminalBusy by remember { mutableStateOf(false) }
    var pendingShellCommand by remember { mutableStateOf<String?>(null) }
    var output by remember(terminal) {
        mutableStateOf(
            terminal.storedOutput().ifBlank {
                "KELLVYN WORKSPACE TERMINAL v2\\n" +
                    "Pasta inicial: /workspace\\n" +
                    "Digite help; use ! comando para executar manualmente após confirmação. Para um projeto: projects e cd <id>."
            }
        )
    }

    fun executeManualShell(raw: String) {
        val shellLine = raw.trim()
        if (shellLine.isBlank() || terminalBusy) return
        pendingShellCommand = null
        command = ""
        terminalBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (shellLine == "cd" || shellLine.startsWith("cd ")) {
                        terminal.execute(shellLine)
                    } else {
                        manualShell.execute(shellLine, terminal.currentDirectoryFile())
                    }
                }.getOrElse { "ERRO no shell manual: ${it.message ?: it.javaClass.simpleName}" }
            }
            output = (output + "\\n\\n! ${shellLine}\\n${result}").takeLast(MAX_OUTPUT_CHARS)
            terminal.saveOutput(output)
            terminalBusy = false
        }
    }

    fun execute(raw: String) {
        val line = raw.trim()
        if (line.isBlank() || terminalBusy) return
        if (line.startsWith("!")) {
            val shellLine = line.removePrefix("!").trim()
            if (shellLine.isBlank()) {
                output = (output + "\\n\\nDigite ! seguido do comando que deseja executar manualmente.").takeLast(MAX_OUTPUT_CHARS)
                terminal.saveOutput(output)
                command = ""
            } else {
                pendingShellCommand = shellLine
                command = ""
            }
            return
        }
        if (line.equals("clear", ignoreCase = true)) {
            output = ""
            terminal.saveOutput("")
            command = ""
            return
        }
        command = ""
        terminalBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val parts = line.split(Regex("\\s+"), limit = 3)
                    when (parts.first().lowercase()) {
                        "help" -> terminal.execute("help")
                        "status" -> buildString {
                            appendLine("Motor local: " + if (localModelReady) "modelo pronto" else "sem modelo carregado")
                            appendLine("Modo selecionado: " + if (onlineMode) "ONLINE" else "LOCAL")
                            appendLine("Provedor online: " + if (onlineConfigured) "configurado" else "não configurado")
                            appendLine("Projetos salvos: ${workspace.listProjects().size}")
                            appendLine("Memórias explícitas: ${memories.list().size}")
                            appendLine("Pasta do terminal: ${terminal.pathLabel()}")
                            appendLine("Compilador Android local: pendente; nenhum APK é gerado pelo terminal ainda")
                        }.trimEnd()
                        "projects" -> {
                            val projects = workspace.listProjects()
                            if (projects.isEmpty()) "Nenhum projeto local."
                            else projects.joinToString("\\n") { "${it.id} — ${it.name} (${it.fileCount} arquivos)" }
                        }
                        "memories" -> {
                            val facts = memories.list()
                            if (facts.isEmpty()) "Nenhuma memória salva."
                            else facts.mapIndexed { index, fact -> "${index + 1}. ${fact.text}" }.joinToString("\\n")
                        }
                        "files" -> {
                            if (parts.size < 2) "Uso: files <id>"
                            else workspace.listFiles(parts[1]).joinToString("\\n").ifBlank { "O projeto não tem arquivos." }
                        }
                        "read" -> {
                            if (parts.size < 3) "Uso: read <id> <path>"
                            else workspace.readFile(parts[1], parts[2]).take(MAX_READ_CHARS)
                        }
                        else -> terminal.execute(line)
                    }
                } catch (e: Exception) {
                    "ERRO: " + (e.message ?: e.javaClass.simpleName)
                }
            }
            output = (output + "\\n\\n> ${line}\\n${result}").takeLast(MAX_OUTPUT_CHARS)
            terminal.saveOutput(output)
            terminalBusy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            color = ConsoleNight,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(ConsoleNight).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TERMINAL LOCAL", color = ConsoleNeon, fontSize = 14.sp)
                        Text(terminal.pathLabel(), color = ConsoleMuted, fontSize = 11.sp)
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
                        placeholder = { Text("help, ls, cd, git status…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { execute(command) })
                    )
                    Button(
                        onClick = { execute(command) },
                        enabled = !terminalBusy && command.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D604A), contentColor = Color.White)
                    ) { Text(if (terminalBusy) "Rodando…" else "Executar") }
                }
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = ConsolePanel,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        Text(
                            output,
                            color = Color(0xFFC9F6E8),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    "Comandos normais usam a lista segura do workspace. Para executar manualmente um comando do Android, digite ! comando; o terminal pedirá confirmação. O shell não é root e o compilador APK local ainda está pendente.",
                    color = ConsoleMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(1.dp))
            }
        }
    }

    if (pendingShellCommand != null) {
        AlertDialog(
            onDismissRequest = { pendingShellCommand = null },
            title = { Text("Executar comando manual?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Este comando será executado pelo shell do Android como o usuário do aplicativo, na pasta atual do workspace. Não é root; ainda assim, um comando manual pode alterar ou apagar arquivos que o próprio app consegue acessar.",
                        color = ConsoleMuted,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = ConsolePanel,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder)
                    ) {
                        Text(
                            pendingShellCommand.orEmpty(),
                            color = Color(0xFFC9F6E8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        )
                    }
                    Text(
                        "O agente automático não pode chamar este shell manual; ele continua restrito à lista de comandos permitidos.",
                        color = ConsoleMuted,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { executeManualShell(pendingShellCommand.orEmpty()) },
                    enabled = !terminalBusy
                ) { Text("Executar", color = ConsoleNeon) }
            },
            dismissButton = {
                TextButton(onClick = { pendingShellCommand = null }) { Text("Cancelar") }
            }
        )
    }
}

private const val MAX_READ_CHARS = 12_000
private const val MAX_OUTPUT_CHARS = 30_000
