package com.kellvyn.assistant.tools

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs commands explicitly typed by the person in the manual terminal.
 *
 * This path is intentionally NOT exposed to the chat agent. The agent uses WorkspaceTerminal's
 * fixed command allowlist instead. Each manual command runs as the app's Android UID in the
 * current app-private workspace directory; it is not root and does not have a persistent shell.
 */
class ManualShellExecutor(private val workspaceRoot: File) {
    fun execute(command: String, workingDirectory: File): String {
        val line = command.trim()
        require(line.isNotBlank()) { "Digite um comando depois de !." }
        require(line.length <= MAX_COMMAND_CHARS) { "O comando passou do limite de $MAX_COMMAND_CHARS caracteres." }
        require(line.none { it == '\u0000' }) { "O comando contém um caractere inválido." }
        require(workingDirectory.isDirectory) { "A pasta atual do terminal não existe mais." }
        val root = workspaceRoot.canonicalFile
        val working = workingDirectory.canonicalFile
        require(working == root || working.path.startsWith(root.path + File.separator)) {
            "O shell manual só pode ser executado dentro da pasta privada /workspace."
        }

        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", line)
                .directory(working)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return "Falha ao iniciar o shell do Android: ${e.message ?: e.javaClass.simpleName}"
        }

        val captured = ByteArrayOutputStream()
        val reader = thread(start = true, isDaemon = true, name = "kellvyn-manual-terminal-output") {
            runCatching {
                process.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        synchronized(captured) {
                            val remaining = MAX_OUTPUT_BYTES - captured.size()
                            if (remaining > 0) captured.write(buffer, 0, minOf(count, remaining))
                        }
                        // Continue draining even when output is capped so the process won't block.
                    }
                }
            }
        }

        val completed = try {
            process.waitFor(MAX_RUNTIME_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (!completed) {
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.waitFor(500, TimeUnit.MILLISECONDS) }
        }
        runCatching { reader.join(1_000) }

        val output = synchronized(captured) { String(captured.toByteArray(), StandardCharsets.UTF_8) }
        val status = if (completed) {
            "exit code: ${runCatching { process.exitValue() }.getOrDefault(-1)}"
        } else {
            "interrompido: limite de ${MAX_RUNTIME_SECONDS}s atingido"
        }
        val clipped = if (captured.size() >= MAX_OUTPUT_BYTES) "\n[saída limitada a 64 KB]" else ""
        return (output.trimEnd() + clipped + "\n" + status).trim()
    }

    private companion object {
        const val MAX_COMMAND_CHARS = 2_000
        const val MAX_RUNTIME_SECONDS = 15L
        const val MAX_OUTPUT_BYTES = 64 * 1024
    }
}
