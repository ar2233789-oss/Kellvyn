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
import androidx.compose.foundation.animateScrollTo
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ConsoleNight = Color(0xFF020806)
private val ConsolePanel = Color(0xFF071713)
private val ConsoleNeon = Color(0xFF38E6BC)
private val ConsoleMuted = Color(0xFF91B8AD)
private val ConsoleBorder = Color(0xFF164638)
private const val MAX_OUTPUT_CHARS = 90_000

/**
 * Native UI for issuing commands through the installed Termux app's RUN_COMMAND service.
 * This is not Android's restricted /system/bin/sh. Opening the full Termux app is also available.
 * AI commands are handled by the chat agent and require explicit user approval before execution.
 */
@Composable
fun WorkspaceConsoleDialog(
    termux: TermuxBridge,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var workingDirectory by remember { mutableStateOf(TermuxBridge.TERMUX_HOME) }
    var terminalBusy by remember { mutableStateOf(false) }
    val outputScrollState = rememberScrollState()
    var output by remember(termux) {
        mutableStateOf(
            if (termux.isInstalled()) {
                "KELLVYN TERMINAL  •  TERMUX REAL\nInstalação Termux encontrada.\nPasta inicial: " + TermuxBridge.TERMUX_HOME +
                    "\nOs comandos são executados dentro do ambiente Termux instalado.\n"
            } else {
                "KELLVYN TERMINAL  •  TERMUX REAL\nTermux não está instalado.\nUse “Abrir Termux” para acessar a página oficial de versões e instalar.\n"
            }
        )
    }

    LaunchedEffect(output) {
        outputScrollState.animateScrollTo(outputScrollState.maxValue)
    }

    fun appendOutput(value: String) {
        output = (output + "\n\n" + value).takeLast(MAX_OUTPUT_CHARS)
    }

    fun execute(raw: String) {
        val line = raw.trim()
        if (line.isBlank() || terminalBusy) return
        command = ""
        if (line.equals("clear", ignoreCase = true)) {
            output = ""
            return
        }

        terminalBusy = true
        appendOutput("$ " + line + "\nExecutando no Termux…")
        scope.launch {
            try {
                val isDirectoryChange = line == "cd" ||
                    line.startsWith("cd ", ignoreCase = true) ||
                    line.startsWith("cd\t", ignoreCase = true)
                val executable = if (isDirectoryChange) line + " && pwd" else line
                val result = withContext(Dispatchers.IO) {
                    termux.runCommand(executable, workingDirectory)
                }
                appendOutput(result.formattedOutput())
                if (isDirectoryChange && result.succeeded) {
                    val reportedPath = result.stdout.lineSequence()
                        .map { it.trim() }
                        .lastOrNull { it.isNotBlank() }
                    if (!reportedPath.isNullOrBlank() &&
                        (reportedPath.startsWith("/") || reportedPath.startsWith("~/"))
                    ) {
                        workingDirectory = reportedPath
                    }
                }
            } catch (e: Exception) {
                appendOutput("ERRO: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                terminalBusy = false
            }
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
                        Text("TERMINAL TERMUX", color = ConsoleNeon, fontSize = 14.sp)
                        Text(workingDirectory, color = ConsoleMuted, fontSize = 10.sp, maxLines = 1)
                    }
                    TextButton(
                        onClick = {
                            appendOutput(termux.openFullTerminal())
                        }
                    ) { Text("Abrir Termux", color = ConsoleNeon, fontSize = 10.sp) }
                    TextButton(onClick = { output = "" }) { Text("Limpar", color = ConsoleMuted, fontSize = 10.sp) }
                    TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White, fontSize = 10.sp) }
                }
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = ConsolePanel,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(outputScrollState).padding(10.dp)
                    ) {
                        Text(
                            output.ifBlank { "Saída do Termux aparecerá aqui." },
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
                        onValueChange = { command = it.take(TermuxBridge.MAX_COMMAND_CHARS) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Comando Termux") },
                        placeholder = { Text("pkg, python, git, bash…") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { execute(command) })
                    )
                    Button(
                        onClick = { execute(command) },
                        enabled = !terminalBusy && command.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D604A),
                            contentColor = Color.White
                        )
                    ) { Text(if (terminalBusy) "…" else "▶") }
                }
                Text(
                    "Este painel executa comandos no Termux instalado. Para programas interativos, toque em Abrir Termux. Para liberar a ponte, conceda RUN_COMMAND ao Kellvyn em Permissões do Android e defina allow-external-apps=true em ~/.termux/termux.properties; depois execute termux-reload-settings. A IA só executa após pedido explícito no chat principal e confirmação individual.",
                    color = ConsoleMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
