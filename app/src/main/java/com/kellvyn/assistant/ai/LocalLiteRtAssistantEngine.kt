package com.kellvyn.assistant.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline inference engine. The user supplies a compatible .litertlm model file;
 * this class stores it in app-private storage and never sends prompts to a server.
 */
class LocalLiteRtAssistantEngine(context: Context) : AssistantEngine, AutoCloseable {
    private companion object {
        const val MAX_SYSTEM_CONTEXT_CHARS = 12_000
    }

    private val appContext = context.applicationContext
    private val modelFile = File(File(appContext.filesDir, "models"), "kellvyn-model.litertlm")
    @Volatile private var engine: Engine? = null

    override val displayName: String = "LiteRT-LM (local)"

    fun hasSavedModel(): Boolean = modelFile.isFile && modelFile.length() > 0L

    fun savedModelSizeBytes(): Long = if (hasSavedModel()) modelFile.length() else 0L

    /** Closes native inference and removes the locally stored model to release disk space. */
    fun removeSavedModel(): Boolean {
        close()
        return !modelFile.exists() || modelFile.delete()
    }

    suspend fun importAndLoad(uri: Uri): AssistantResult {
        return try {
            val copiedFile = withContext(Dispatchers.IO) {
                val name = getDisplayName(uri)
                if (!name.endsWith(".litertlm", ignoreCase = true)) {
                    throw IllegalArgumentException("Selecione um arquivo de modelo com extensão .litertlm.")
                }

                val directory = modelFile.parentFile
                    ?: throw IllegalStateException("Não foi possível criar a pasta privada do modelo.")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Não foi possível criar a pasta privada do modelo.")
                }

                val temporary = File(directory, "kellvyn-model.part")
                appContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "O Android não conseguiu abrir o arquivo selecionado." }
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                if (temporary.length() < 1_000_000L) {
                    temporary.delete()
                    throw IllegalArgumentException("O arquivo parece incompleto ou não é um modelo válido.")
                }
                if (modelFile.exists() && !modelFile.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Não foi possível substituir o modelo anterior.")
                }
                if (!temporary.renameTo(modelFile)) {
                    temporary.delete()
                    throw IllegalStateException("Não foi possível finalizar a cópia do modelo.")
                }
                modelFile
            }
            loadModel(copiedFile)
        } catch (e: Exception) {
            AssistantResult.Failure(e.message ?: "Falha ao importar o modelo local.")
        }
    }

    suspend fun loadSavedModel(): AssistantResult {
        if (!hasSavedModel()) {
            return AssistantResult.Unavailable("Nenhum modelo local foi selecionado.")
        }
        return loadModel(modelFile)
    }

    private suspend fun loadModel(file: File): AssistantResult = withContext(Dispatchers.IO) {
        var candidate: Engine? = null
        try {
            candidate = Engine(
                EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            candidate.initialize()
            val previous = engine
            engine = candidate
            candidate = null
            try { previous?.close() } catch (_: Exception) { }
            AssistantResult.Success("Modelo local carregado e pronto para responder.")
        } catch (e: Exception) {
            try { candidate?.close() } catch (_: Exception) { }
            AssistantResult.Failure(
                "Não foi possível iniciar esse modelo. Confirme se o arquivo .litertlm é compatível. " +
                    (e.message ?: "")
            )
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        engine != null
    }

    override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult = withContext(Dispatchers.IO) {
        val activeEngine = engine
            ?: return@withContext AssistantResult.Unavailable("Carregue um modelo local antes de enviar mensagens.")

        val lastUserIndex = conversation.indexOfLast { it.role == AssistantTurn.Role.USER }
        if (lastUserIndex < 0) {
            return@withContext AssistantResult.Failure("Não há mensagem do usuário para responder.")
        }

        val priorMessages = conversation.take(lastUserIndex).mapNotNull { turn ->
            when (turn.role) {
                AssistantTurn.Role.SYSTEM -> null
                AssistantTurn.Role.USER -> Message.user(turn.content)
                AssistantTurn.Role.ASSISTANT -> Message.model(turn.content)
            }
        }

        try {
            val answer = StringBuilder()
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    buildString {
                        append(
                            "Você é Kellvyn IA, um assistente útil, direto e honesto. Responda em português brasileiro por padrão. " +
                                "Não invente capacidades, não afirme ter realizado ações que não realizou e explique claramente limitações."
                        )
                        conversation.firstOrNull { it.role == AssistantTurn.Role.SYSTEM }
                            ?.content
                            ?.take(MAX_SYSTEM_CONTEXT_CHARS)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { appendLine(); appendLine(); append(it) }
                    }
                ),
                initialMessages = priorMessages
            )
            activeEngine.createConversation(config).use { chat ->
                chat.sendMessageAsync(conversation[lastUserIndex].content).collect { part ->
                    answer.append(part.contents.toString())
                }
            }
            val text = answer.toString().trim()
            if (text.isBlank()) {
                AssistantResult.Failure("O modelo terminou sem produzir texto.")
            } else {
                AssistantResult.Success(text)
            }
        } catch (e: Exception) {
            AssistantResult.Failure(
                "Falha durante a geração local: " + (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    override fun close() {
        try { engine?.close() } catch (_: Exception) { }
        engine = null
    }

    private fun getDisplayName(uri: Uri): String {
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
