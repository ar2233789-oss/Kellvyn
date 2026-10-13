package com.kellvyn.assistant.workspace

import org.eclipse.jgit.api.Git
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Direct, workspace-aware Git operations for the autonomous chat agent.
 *
 * This service does not use a terminal, invoke a shell, or access a remote. Every
 * operation is confined to a registered project directory inside app-private storage.
 */
class WorkspaceGitService(private val workspace: ProjectWorkspaceStore) {

    fun status(projectId: String): String {
        val directory = projectDirectory(projectId)
        if (!File(directory, ".git").exists()) {
            return "Este projeto ainda não possui Git local. Peça para inicializar o repositório."
        }
        return withGit(projectId) { git ->
            val state = git.status().call()
            buildString {
                appendLine("branch: ${git.repository.branch ?: "(sem branch)"}")
                appendLine(if (state.isClean) "árvore limpa" else "alterações locais:")
                if (state.added.isNotEmpty()) appendLine("  adicionados: ${state.added.sorted().joinToString()}")
                if (state.changed.isNotEmpty()) appendLine("  modificados: ${state.changed.sorted().joinToString()}")
                if (state.removed.isNotEmpty()) appendLine("  removidos: ${state.removed.sorted().joinToString()}")
                if (state.untracked.isNotEmpty()) appendLine("  não rastreados: ${state.untracked.sorted().joinToString()}")
                if (state.missing.isNotEmpty()) appendLine("  ausentes: ${state.missing.sorted().joinToString()}")
            }.trimEnd()
        }
    }

    fun diff(projectId: String): String = withGit(projectId) { git ->
        val bytes = ByteArrayOutputStream()
        git.diff().setOutputStream(bytes).call()
        bytes.toString(Charsets.UTF_8.name()).take(MAX_RESULT_CHARS)
            .ifBlank { "Sem diferenças no índice de trabalho." }
    }

    fun log(projectId: String): String = withGit(projectId) { git ->
        val commits = git.log().setMaxCount(20).call().toList()
        if (commits.isEmpty()) "(ainda não há commits locais)"
        else commits.joinToString("\n") { "${it.name.take(8)}  ${it.shortMessage}" }
            .take(MAX_RESULT_CHARS)
    }

    fun branches(projectId: String): String = withGit(projectId) { git ->
        git.branchList().call().joinToString("\n") {
            (if (it.name == git.repository.fullBranch) "* " else "  ") +
                it.name.removePrefix("refs/heads/")
        }.ifBlank { "(nenhuma branch criada)" }.take(MAX_RESULT_CHARS)
    }

    fun init(projectId: String): String {
        val directory = projectDirectory(projectId)
        require(!File(directory, ".git").exists()) {
            "Este projeto já possui um repositório Git local."
        }
        Git.init().setDirectory(directory).call().use { git ->
            git.repository.config.setString("user", null, "name", "Kellvyn IA")
            git.repository.config.setString("user", null, "email", "kellvyn@local")
            git.repository.config.save()
        }
        return "Repositório Git local inicializado para $projectId. Nenhuma conexão remota foi criada."
    }

    fun stageAll(projectId: String): String = withGit(projectId) { git ->
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call()
        "Arquivos locais preparados para commit em $projectId."
    }

    fun commit(projectId: String, message: String): String {
        val cleanMessage = message.trim()
        require(cleanMessage.isNotBlank() && cleanMessage.length <= MAX_COMMIT_MESSAGE_CHARS) {
            "A mensagem do commit precisa ter de 1 a $MAX_COMMIT_MESSAGE_CHARS caracteres."
        }
        return withGit(projectId) { git ->
            val state = git.status().call()
            require(!state.isClean) {
                "Não há alterações preparadas ou arquivos modificados para salvar. Execute a preparação local primeiro."
            }
            val commit = git.commit().setMessage(cleanMessage)
                .setAuthor("Kellvyn IA", "kellvyn@local")
                .setCommitter("Kellvyn IA", "kellvyn@local")
                .call()
            "Commit local criado: ${commit.name.take(8)} — ${commit.shortMessage}"
        }
    }

    private fun projectDirectory(projectId: String): File = workspace.projectDirectory(projectId).canonicalFile

    private fun <T> withGit(projectId: String, action: (Git) -> T): T {
        val directory = projectDirectory(projectId)
        require(File(directory, ".git").exists()) {
            "Nenhum repositório Git local para $projectId. Inicialize o Git primeiro."
        }
        return Git.open(directory).use(action)
    }

    private companion object {
        const val MAX_RESULT_CHARS = 12_000
        const val MAX_COMMIT_MESSAGE_CHARS = 300
    }
}
