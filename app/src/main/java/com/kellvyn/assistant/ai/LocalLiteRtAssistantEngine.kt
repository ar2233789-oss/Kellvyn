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
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * Offline inference engine. The user supplies a compatible .litertlm model file;
 * this class stores it in app-private storage and never sends prompts to a server.
 */
class LocalLiteRtAssistantEngine(context: Context) : AssistantEngine, AutoCloseable {
    private companion object {
        const val MAX_SYSTEM_CONTEXT_CHARS = 12_000
        const val RECOMMENDED_MODEL_URL = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/main/SmolLM2_360M_instruct.litertlm?download=true"
        const val RECOMMENDED_MODEL_BYTES = 373_719_040L
        const val MAX_RECOMMENDED_MODEL_BYTES = 500_000_000L
        const val MAX_IMPORTED_MODEL_BYTES = 750_000_000L
        const val MIN_IMPORTED_MODEL_BYTES = 1_000_000L
        const val RECOMMENDED_MODEL_SHA256 = "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 90_000
        const val PROGRESS_UPDATE_BYTES = 8L * 1024L * 1024L
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
        val directory = modelFile.parentFile
            ?: return AssistantResult.Failure("Pasta privada de modelos indisponível.")
        val temporary = File(directory, "kellvyn-model.import")
        val backup = File(directory, "kellvyn-model.previous")

        return try {
            withContext(Dispatchers.IO) {
                val name = getDisplayName(uri)
                require(name.endsWith(".litertlm", ignoreCase = true)) {
                    "Selecione um arquivo de modelo com extensão .litertlm."
                }
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Não foi possível criar a pasta privada do modelo.")
                }
                if (temporary.exists() && !temporary.delete()) {
                    throw IllegalStateException("Não foi possível limpar uma importação anterior interrompida.")
                }

                var copied = 0L
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("O Android não conseguiu abrir o arquivo selecionado.")
                input.use { source ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            copied += count
                            require(copied <= MAX_IMPORTED_MODEL_BYTES) {
                                "O modelo ultrapassa o limite de importação de ${MAX_IMPORTED_MODEL_BYTES / 1_000_000} MB."
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(copied >= MIN_IMPORTED_MODEL_BYTES) {
                    "O arquivo parece incompleto ou não é um modelo válido."
                }
                require(temporary.length() == copied) {
                    "A cópia do modelo não foi concluída corretamente."
                }
            }

            withContext(Dispatchers.IO) {
                if (backup.exists() && !backup.delete()) {
                    throw IllegalStateException("Não foi possível limpar o backup de modelo anterior.")
                }
                if (modelFile.exists() && !modelFile.renameTo(backup)) {
                    throw IllegalStateException("Não foi possível preservar o modelo anterior.")
                }
                if (!temporary.renameTo(modelFile)) {
                    if (backup.exists()) backup.renameTo(modelFile)
                    throw IllegalStateException("Não foi possível finalizar a cópia do novo modelo.")
                }
            }

            val result = loadModel(modelFile)
            if (result is AssistantResult.Success) {
                withContext(Dispatchers.IO) {
                    if (backup.exists()) backup.delete()
                }
                result
            } else {
                restorePreviousModel(backup)
                result
            }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                temporary.delete()
                if (backup.exists()) {
                    if (modelFile.exists()) modelFile.delete()
                    if (!backup.renameTo(modelFile)) {
                        throw IllegalStateException("A importação falhou e o backup não pôde ser restaurado.")
                    }
                } else if (!hasActiveModel() && modelFile.exists()) {
                    modelFile.delete()
                }
            }
            AssistantResult.Failure(e.message ?: "Falha ao importar o modelo local.")
        }
    }

    private fun hasActiveModel(): Boolean = engine != null

    private suspend fun restorePreviousModel(backup: File) {
        withContext(Dispatchers.IO) {
            if (modelFile.exists() && !modelFile.delete()) {
                throw IllegalStateException("O novo modelo falhou e não foi possível removê-lo para restaurar o anterior.")
            }
            if (backup.exists() && !backup.renameTo(modelFile)) {
                throw IllegalStateException("O novo modelo falhou e o backup não pôde ser restaurado.")
            }
        }
    }

    /**
     * Downloads a public, Apache-2.0 LiteRT-LM model from the official Hugging Face
     * model repository. The model is verified by size and SHA-256 before it replaces
     * the saved file. Progress callbacks are invoked from the IO dispatcher.
     */
    suspend fun downloadRecommendedModel(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): AssistantResult {
        val directory = modelFile.parentFile
            ?: return AssistantResult.Failure("Pasta privada de modelos indisponível.")
        val temporary = File(directory, "kellvyn-model.download")

        return try {
            withContext(Dispatchers.IO) {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Não foi possível criar a pasta privada do modelo.")
                }

                temporary.delete()
                val connection = (URL(RECOMMENDED_MODEL_URL).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("User-Agent", "Kellvyn-IA-Android")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                try {
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        throw IllegalStateException("A fonte do modelo respondeu HTTP $responseCode.")
                    }

                    val declaredLength = connection.contentLengthLong
                    if (declaredLength > MAX_RECOMMENDED_MODEL_BYTES) {
                        throw IllegalStateException("O servidor informou um arquivo maior que o limite permitido.")
                    }

                    val totalForProgress = if (declaredLength > 0L) declaredLength else RECOMMENDED_MODEL_BYTES
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    var lastProgress = 0L

                    connection.inputStream.buffered().use { input ->
                        FileOutputStream(temporary).buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue

                                downloaded += count
                                if (downloaded > MAX_RECOMMENDED_MODEL_BYTES) {
                                    throw IllegalStateException("O download excedeu o limite de tamanho e foi interrompido.")
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)

                                if (downloaded - lastProgress >= PROGRESS_UPDATE_BYTES) {
                                    onProgress(downloaded, totalForProgress)
                                    lastProgress = downloaded
                                }
                            }
                        }
                    }

                    val actualHash = digest.digest().joinToString("") { byte ->
                        "%02x".format(byte)
                    }
                    if (downloaded != RECOMMENDED_MODEL_BYTES) {
                        throw IllegalStateException(
                            "Download incompleto: recebidos $downloaded bytes; esperados $RECOMMENDED_MODEL_BYTES."
                        )
                    }
                    if (!actualHash.equals(RECOMMENDED_MODEL_SHA256, ignoreCase = true)) {
                        throw IllegalStateException("A verificação de integridade falhou. O modelo baixado foi descartado.")
                    }

                    onProgress(downloaded, RECOMMENDED_MODEL_BYTES)

                    // Keep the previous file until the new download has passed verification.
                    val backup = File(directory, "kellvyn-model.previous")
                    if (backup.exists() && !backup.delete()) {
                        throw IllegalStateException("Não foi possível limpar o backup temporário do modelo.")
                    }
                    val hadPrevious = modelFile.exists()
                    if (hadPrevious && !modelFile.renameTo(backup)) {
                        throw IllegalStateException("Não foi possível proteger o modelo já salvo.")
                    }
                    if (!temporary.renameTo(modelFile)) {
                        if (hadPrevious) backup.renameTo(modelFile)
                        throw IllegalStateException("Não foi possível finalizar o arquivo baixado.")
                    }
                    if (backup.exists()) backup.delete()
                    modelFile
                } finally {
                    connection.disconnect()
                }
            }.let { downloadedModel ->
                loadModel(downloadedModel)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { temporary.delete() }
            AssistantResult.Failure(e.message ?: "Falha ao baixar o modelo local.")
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
