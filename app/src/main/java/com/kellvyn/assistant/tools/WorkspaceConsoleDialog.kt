package com.kellvyn.assistant.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ConsoleNight = Color(0xFF020806)
private val ConsolePanel = Color(0xFF071713)
private val ConsoleNeon = Color(0xFF38E6BC)
private val ConsoleMuted = Color(0xFF91B8AD)
private val ConsoleBorder = Color(0xFF164638)
private const val MAX_OUTPUT_CHARS = 48_000

/**
 * Native Android shell terminal. Commands typed here run as the app UID, inside its private
 * workspace only. The terminal has no AI agent; the main chat may explicitly request a command.
 */
@Composable
fun WorkspaceConsoleDialog(
    workspace: ProjectWorkspaceStore,
    terminal: WorkspaceTerminal,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val manualShell = remember(workspace) { ManualShellExecutor(workspace.workspaceDirectory()) }
    var command by remember { mutableStateOf("") }
    var terminalBusy by remember { mutableStateOf(false) }
    var output by remember(terminal) {
        mutableStateOf(
            terminal.storedOutput().ifBlank {
                "KELLVYN TERMINAL  •  ANDROID SHELL\n" +
                    "Pasta: ${terminal.pathLabel()}\n" +
                    "Digite help para ver os comandos. Comandos comuns: pwd, ls, cat, mkdir, touch, grep, find, git.\n"
            }
        )
    }

    fun appendOutput(value: String) {
        output = (output + "\n\n" + value).takeLast(MAX_OUTPUT_CHARS)
        terminal.saveOutput(output)
    }

    fun execute(raw: String) {
        val line = raw.trim()
        if (line.isBlank() || terminalBusy) return
        command = ""
        if (line.equals("clear", ignoreCase = true)) {
            output = ""
            terminal.saveOutput("")
            return
        }
        terminalBusy = true
        appendOutput("$ ${line}\nExecutando…")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        line.equals("help", ignoreCase = true) -> terminal.execute("help") +
                            "\n\nShell Android: comandos são executados em /workspace, sem root e sem acesso fora da área privada do app."
                        line.equals("status", ignoreCase = true) -> buildString {
                            appendLine("Diretório: ${terminal.pathLabel()}")
                            appendLine("Projetos: ${workspace.listProjects().size}")
                            appendLine("Shell: /system/bin/sh")
                            append("Permissão root: não disponível")
                        }
                        line.equals("projects", ignoreCase = true) -> {
                            val projects = workspace.listProjects()
                            if (projects.isEmpty()) "Nenhum projeto salvo."
                            else projects.joinToString("\n") { "${it.id} — ${it.name} (${it.fileCount} arquivos)" }
                        }
                        line == "cd" || line.startsWith("cd ") -> terminal.execute(line)
                        else -> manualShell.execute(line, terminal.currentDirectoryFile())
                    }
                }.getOrElse { "ERRO: ${it.message ?: it.javaClass.simpleName}" }
            }
            appendOutput(result)
            terminalBusy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(5.dp),
            color = ConsoleNight,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(ConsoleNight).padding(10.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TERMINAL", color = ConsoleNeon, fontSize = 15.sp)
                        Text(terminal.pathLabel(), color = ConsoleMuted, fontSize = 10.sp, maxLines = 1)
                    }
                    TextButton(onClick = { execute("clear") }) { Text("Limpar", color = ConsoleMuted) }
                    TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White) }
                }
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = ConsolePanel,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it.take(2_000) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Comando") },
                        placeholder = { Text("ls, pwd, mkdir, git status…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { execute(command) })
                    )
                    Button(
                        onClick = { execute(command) },
                        enabled = !terminalBusy && command.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D604A), contentColor = Color.White)
                    ) { Text(if (terminalBusy) "…" else "▶") }
                }
                Text(
                    "Shell Android real, limitado ao espaço privado do Kellvyn. Não é root nem um Termux completo do sistema.",
                    color = ConsoleMuted,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
