package com.kellvyn.assistant.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import com.kellvyn.assistant.workspace.WorkspaceGitService
import com.kellvyn.assistant.workspace.WorkspaceProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GitNight = Color(0xFF020806)
private val GitPanel = Color(0xFF071713)
private val GitNeon = Color(0xFF38E6BC)
private val GitMuted = Color(0xFF91B8AD)
private val GitBorder = Color(0xFF164638)
private val GitWarning = Color(0xFFFFC857)

/**
 * Native, local-only Git controls for a saved project.
 * This panel is separate from Terminal and never creates a remote or uploads code.
 */
@Composable
fun WorkspaceGitDialog(
    workspace: ProjectWorkspaceStore,
    projects: List<WorkspaceProject>,
    onDismiss: () -> Unit
) {
    val git = remember(workspace) { WorkspaceGitService(workspace) }
    val scope = rememberCoroutineScope()
    var selectedProjectId by remember(projects) {
        mutableStateOf(projects.firstOrNull()?.id.orEmpty())
    }
    var output by remember { mutableStateOf("Selecione um projeto para consultar o Git local.") }
    var isBusy by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("Atualização local") }

    fun runGitAction(label: String, action: (String) -> String) {
        val projectId = selectedProjectId
        if (projectId.isBlank()) {
            output = "Nenhum projeto selecionado. Crie um aplicativo pelo chat primeiro."
            return
        }
        if (isBusy) return
        isBusy = true
        output = "$label…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { action(projectId) }
            }
            output = result.fold(
                onSuccess = { it },
                onFailure = { "Falha na operação Git: " + (it.message ?: it.javaClass.simpleName) }
            )
            isBusy = false
        }
    }

    LaunchedEffect(selectedProjectId) {
        val id = selectedProjectId
        if (id.isBlank()) {
            output = "Ainda não há projetos salvos. Peça ao Kellvyn pelo chat para criar um aplicativo."
        } else {
            isBusy = true
            val result = withContext(Dispatchers.IO) { runCatching { git.status(id) } }
            output = result.fold(
                onSuccess = { it },
                onFailure = { "Falha ao consultar Git: " + (it.message ?: it.javaClass.simpleName) }
            )
            isBusy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            color = GitNight,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GitBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(GitNight).padding(12.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GIT LOCAL", color = GitNeon, fontSize = 15.sp)
                        Text(
                            "Versionamento no aparelho • sem publicar código",
                            color = GitMuted,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Fechar", color = Color.White) }
                }

                Text("PROJETO", color = GitMuted, fontSize = 10.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 92.dp),
                    color = GitPanel,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GitBorder)
                ) {
                    if (projects.isEmpty()) {
                        Text(
                            "Nenhum projeto salvo. Peça à IA para criar um aplicativo no chat.",
                            color = GitMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(4.dp)
                        ) {
                            projects.forEach { project ->
                                TextButton(
                                    onClick = { selectedProjectId = project.id },
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        (if (project.id == selectedProjectId) "● " else "○ ") + project.name,
                                        color = if (project.id == selectedProjectId) GitNeon else Color.White,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(
                        onClick = { runGitAction("Consultando status") { git.status(it) } },
                        enabled = !isBusy && selectedProjectId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GitPanel, contentColor = GitNeon)
                    ) { Text("Status", fontSize = 10.sp) }
                    Button(
                        onClick = { runGitAction("Lendo diferenças") { git.diff(it) } },
                        enabled = !isBusy && selectedProjectId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GitPanel, contentColor = GitNeon)
                    ) { Text("Diff", fontSize = 10.sp) }
                    Button(
                        onClick = { runGitAction("Lendo histórico") { git.log(it) } },
                        enabled = !isBusy && selectedProjectId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GitPanel, contentColor = GitNeon)
                    ) { Text("Commits", fontSize = 10.sp) }
                    Button(
                        onClick = { runGitAction("Lendo branches") { git.branches(it) } },
                        enabled = !isBusy && selectedProjectId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GitPanel, contentColor = GitNeon)
                    ) { Text("Branches", fontSize = 10.sp) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { runGitAction("Inicializando Git local") { git.init(it) } },
                        enabled = !isBusy && selectedProjectId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GitPanel, contentColor = GitNeon)
                    ) { Text("Inicializar Git", fontSize = 10.sp) }
                    Button(
                        onClick = { runGitAction("Preparando alterações locais") { git.stageAll(it) } },
                        enabled = !isBusy && selectedProjectId.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GitPanel, contentColor = GitNeon)
                    ) { Text("Preparar alterações", fontSize = 10.sp) }
                }

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Mensagem do commit") },
                    placeholder = { Text("Descreva as alterações locais") },
                    enabled = !isBusy
                )
                Button(
                    onClick = { runGitAction("Criando commit local") { git.commit(it, commitMessage) } },
                    enabled = !isBusy && selectedProjectId.isNotBlank() && commitMessage.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D604A), contentColor = Color.White)
                ) { Text(if (isBusy) "Executando…" else "Criar commit local") }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = GitPanel,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GitBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)
                    ) {
                        Text(
                            output,
                            color = if (output.startsWith("Falha")) GitWarning else Color(0xFFC9F6E8),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    "As operações acima ficam no armazenamento privado do celular. Nenhum remoto é configurado.",
                    color = GitMuted,
                    fontSize = 9.sp
                )
                Spacer(Modifier.heightIn(min = 0.dp))
            }
        }
    }
}
