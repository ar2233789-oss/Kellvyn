package com.kellvyn.assistant.ai

import android.content.Context
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
import java.security.MessageDigest

/**
 * On-device inference engine. The APK packages a compatible .litertlm model
 * and seeds it into private app storage. Prompts are never sent to an AI provider.
 */
class LocalLiteRtAssistantEngine(context: Context) : AssistantEngine, AutoCloseable {
    private companion object {
        const val MAX_SYSTEM_CONTEXT_CHARS = 12_000
        const val BUNDLE_VERIFIED_KEY = "bundled_model_verified"
        const val BUNDLED_MODEL_BYTES = 373_719_040L
        const val BUNDLED_MODEL_SHA256 = "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e"
    }

    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.filesDir, "models")
    private val modelFile = File(modelDirectory, "kellvyn-model.litertlm")
    private val bundlePrefs = appContext.getSharedPreferences("kellvyn_model_bundle", Context.MODE_PRIVATE)
    @Volatile private var engine: Engine? = null

    // One integrated Kellvyn assistant; no light/heavy model modes are exposed.
    override val displayName: String = "Kellvyn IA — núcleo local"

    private fun selectedModelFile(): File = modelFile

    fun hasSavedModel(): Boolean {
        val selected = selectedModelFile()
        return selected.isFile && selected.length() > 0L
    }

    fun savedModelSizeBytes(): Long {
        val selected = selectedModelFile()
        return if (selected.isFile) selected.length() else 0L
    }

    /** Clears the private model cache; the bundled model is copied again at next initialization. */
    fun removeSavedModel(): Boolean {
        close()
        val files = listOf(modelFile, File(modelDirectory, "kellvyn-model.previous"), File(modelDirectory, "kellvyn-model.bundled"))
        var allRemoved = true
        files.forEach { file -> if (file.exists() && !file.delete()) allRemoved = false }
        return allRemoved
    }

    /** Prepares the single bundled model in private storage before initializing inference. */
    suspend fun recoverInterruptedModelSwap() {
        withContext(Dispatchers.IO) {
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
                throw IllegalStateException("Não foi possível preparar a pasta privada do núcleo local.")
            }
            installBundledConversationModelIfAvailable()
        }
    }

    /** Installs the one checksum-verified model bundled in the APK; no picker or downloader exists. */
    private fun installBundledConversationModelIfAvailable() {
        if (bundlePrefs.getBoolean(BUNDLE_VERIFIED_KEY, false) &&
            modelFile.isFile && modelFile.length() == BUNDLED_MODEL_BYTES
        ) return

        if (hasBundledChecksum(modelFile)) {
            bundlePrefs.edit().putBoolean(BUNDLE_VERIFIED_KEY, true).apply()
            return
        }

        val bundledInput = try {
            appContext.assets.open("models/kellvyn-model.litertlm")
        } catch (_: Exception) {
            // Development builds without the APK packaging step cannot initialize offline inference.
            return
        }
        val temporary = File(modelDirectory, "kellvyn-model.bundled")
        try {
            bundledInput.use { input ->
                FileOutputStream(temporary).use { output ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                    val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                    if (temporary.length() != BUNDLED_MODEL_BYTES || !actualHash.equals(BUNDLED_MODEL_SHA256, ignoreCase = true)) {
                        throw IllegalStateException("O modelo integrado no APK não passou na verificação de integridade.")
                    }
                }
            }
            if (modelFile.exists() && !modelFile.delete()) {
                throw IllegalStateException("Não foi possível substituir o modelo antigo pelo núcleo integrado.")
            }
            if (!temporary.renameTo(modelFile)) {
                throw IllegalStateException("Não foi possível instalar o modelo integrado no armazenamento privado.")
            }
            bundlePrefs.edit().putBoolean(BUNDLE_VERIFIED_KEY, true).apply()
        } catch (e: Exception) {
            temporary.delete()
            throw IllegalStateException("Não consegui preparar o núcleo local integrado: " + (e.message ?: e.javaClass.simpleName), e)
        }
    }

    private fun hasBundledChecksum(file: File): Boolean {
        if (!file.isFile || file.length() != BUNDLED_MODEL_BYTES) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                .equals(BUNDLED_MODEL_SHA256, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadSavedModel(): AssistantResult {
        if (!hasSavedModel()) {
            return AssistantResult.Unavailable("O núcleo local integrado não está disponível nesta versão.")
        }
        return loadModel()
    }

    private suspend fun loadModel(): AssistantResult = withContext(Dispatchers.IO) {
        var candidate: Engine? = null
        try {
            candidate = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            candidate.initialize()
            val previous = engine
            engine = candidate
            candidate = null
            try { previous?.close() } catch (_: Exception) { }
            AssistantResult.Success("Kellvyn IA pronta para conversar.")
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

    override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult =
        replyStreaming(conversation)

    /** Emits partial text while local inference is running, so the chat never looks frozen. */
    suspend fun replyStreaming(
        conversation: List<AssistantTurn>,
        onPartial: suspend (String) -> Unit = {}
    ): AssistantResult = withContext(Dispatchers.IO) {
        val activeEngine = engine
            ?: return@withContext AssistantResult.Unavailable("O núcleo local ainda não terminou de inicializar. Tente enviar novamente.")

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
            var lastPublishedLength = 0
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    buildString {
                        append(
                            "Você é Kellvyn IA, o assistente integrado ao aplicativo Kellvyn. Responda em português brasileiro, com clareza, naturalidade e sem enrolação. " +
                                "Identidade fixa: o projeto Kellvyn IA foi idealizado e criado por Kellvyn, que definiu sua identidade, seus objetivos e seu comportamento. Se perguntarem quem te criou, quem desenvolveu o projeto ou quem você é, reconheça explicitamente Kellvyn como criador do projeto. " +
                                "Você usa um modelo de linguagem que roda localmente no aparelho; esse modelo é a tecnologia usada para gerar respostas, não o criador do Kellvyn IA. Não atribua a criação do projeto à Alibaba Cloud, Qwen, OpenAI, Google ou ao fabricante do modelo, e não invente qual é o fabricante. " +
                                "Não invente capacidades nem ações executadas."
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
                    if (answer.length - lastPublishedLength >= 24) {
                        onPartial(answer.toString())
                        lastPublishedLength = answer.length
                    }
                }
            }
            onPartial(answer.toString())
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

}
