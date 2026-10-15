package com.kellvyn.assistant.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * On-device inference engine. It prefers a checksum-verified .litertlm asset in the APK
 * and otherwise downloads the pinned public model into private storage once. Inference is local.
 */
class LocalLiteRtAssistantEngine(context: Context) : AssistantEngine, AutoCloseable {
    private companion object {
        const val MAX_SYSTEM_CONTEXT_CHARS = 12_000
        const val MODEL_CONNECT_TIMEOUT_MS = 20_000
        const val MODEL_READ_TIMEOUT_MS = 60_000
        const val MODEL_PROGRESS_INTERVAL_BYTES = 8L * 1024L * 1024L
        const val MODEL_DOWNLOAD_URL = "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/507c99cfe6541ba2bcd84818786f7b025935e5e1/SmolLM2_360M_instruct.litertlm?download=true"
        const val BUNDLE_VERIFIED_KEY = "bundled_model_verified"
        const val BUNDLED_MODEL_BYTES = 373_719_040L
        const val BUNDLED_MODEL_SHA256 = "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e"
        const val MAX_CHAT_OUTPUT_TOKENS = 1024
        const val MAX_PROJECT_OUTPUT_TOKENS = 8192
    }

    private val appContext = context.applicationContext
    private val modelDirectory = File(appContext.filesDir, "models")
    private val modelFile = File(modelDirectory, "kellvyn-model.litertlm")
    private val bundlePrefs = appContext.getSharedPreferences("kellvyn_model_bundle", Context.MODE_PRIVATE)
    @Volatile private var engine: Engine? = null

    // One integrated Kellvyn assistant; no light/heavy model modes are exposed.
    override val displayName: String = "Kellvyn IA"

    fun hasSavedModel(): Boolean = modelFile.isFile && modelFile.length() > 0L

    /**
     * Prepare the one local model. Prefer a model bundled in the APK; when it is absent,
     * download the pinned, checksum-verified public model into private storage once.
     * Inference stays on-device after the download completes.
     */
    suspend fun prepareLocalModel(onProgress: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            if (!modelDirectory.exists() && !modelDirectory.mkdirs()) {
                throw IllegalStateException("Não foi possível preparar o armazenamento privado da IA local.")
            }
            installConversationModelIfAvailable(onProgress)
        }
    }

    private suspend fun installConversationModelIfAvailable(onProgress: (String) -> Unit) {
        if (bundlePrefs.getBoolean(BUNDLE_VERIFIED_KEY, false) &&
            modelFile.isFile && modelFile.length() == BUNDLED_MODEL_BYTES
        ) return

        if (hasBundledChecksum(modelFile)) {
            bundlePrefs.edit().putBoolean(BUNDLE_VERIFIED_KEY, true).apply()
            return
        }

        val temporary = File(modelDirectory, "kellvyn-model.download.part")
        val bundledInput = try {
            appContext.assets.open("models/kellvyn-model.litertlm")
        } catch (_: Exception) {
            null
        }

        try {
            if (bundledInput != null) {
                reportModelProgress(onProgress, "Preparando o modelo local incluído no APK…")
                copyAndVerifyModel(bundledInput, temporary, onProgress, null)
            } else {
                reportModelProgress(
                    onProgress,
                    "Preparando a IA local pela primeira vez (aprox. 374 MB)…"
                )
                downloadPinnedModel(temporary, onProgress)
            }

            if (modelFile.exists() && !modelFile.delete()) {
                throw IllegalStateException("Não foi possível finalizar a preparação do modelo local verificado.")
            }
            if (!temporary.renameTo(modelFile)) {
                throw IllegalStateException("Não foi possível instalar o modelo no armazenamento privado.")
            }
            bundlePrefs.edit().putBoolean(BUNDLE_VERIFIED_KEY, true).apply()
            reportModelProgress(onProgress, "Modelo local verificado e guardado no aparelho.")
        } catch (e: CancellationException) {
            temporary.delete()
            bundledInput?.let { runCatching { it.close() } }
            throw e
        } catch (e: Exception) {
            temporary.delete()
            bundledInput?.let { runCatching { it.close() } }
            val detail = e.message ?: e.javaClass.simpleName
            throw IllegalStateException(
                "Não consegui preparar o modelo de IA local. Se ele não estiver incluído no APK, é necessária uma conexão para baixá-lo uma vez. Detalhe: " + detail,
                e
            )
        }
    }

    private suspend fun downloadPinnedModel(destination: File, onProgress: (String) -> Unit) {
        val connection = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = MODEL_CONNECT_TIMEOUT_MS
            readTimeout = MODEL_READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "Kellvyn-IA-OnDevice-Model")
        }
        try {
            connection.connect()
            val responseCode = connection.responseCode
            require(responseCode in 200..299) {
                "O servidor do modelo recusou o download (HTTP $responseCode)."
            }
            val responseBytes = connection.getHeaderFieldLong("Content-Length", -1L)
            require(responseBytes < 0L || responseBytes == BUNDLED_MODEL_BYTES) {
                "O tamanho informado pelo servidor não corresponde à versão verificada do modelo."
            }
            connection.inputStream.use { input ->
                copyAndVerifyModel(input, destination, onProgress, "Preparando a IA local")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyAndVerifyModel(
        input: java.io.InputStream,
        destination: File,
        onProgress: (String) -> Unit,
        progressPrefix: String?
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            FileOutputStream(destination).use { output ->
                input.use { source ->
                    val buffer = ByteArray(128 * 1024)
                    var total = 0L
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        require(total <= BUNDLED_MODEL_BYTES) {
                            "O arquivo do modelo ultrapassou o tamanho esperado; o download foi interrompido."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (progressPrefix != null && total - lastReport >= MODEL_PROGRESS_INTERVAL_BYTES) {
                            val percent = ((total * 100L) / BUNDLED_MODEL_BYTES).coerceIn(0L, 100L)
                            reportModelProgress(onProgress, "$progressPrefix: ${percent}% (${total / 1_000_000L} de ${BUNDLED_MODEL_BYTES / 1_000_000L} MB)")
                            lastReport = total
                        }
                    }
                    output.fd.sync()
                    require(total == BUNDLED_MODEL_BYTES) {
                        "O modelo ficou incompleto: $total de $BUNDLED_MODEL_BYTES bytes."
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            require(actualHash.equals(BUNDLED_MODEL_SHA256, ignoreCase = true)) {
                "A verificação SHA-256 do modelo falhou. O arquivo não será usado."
            }
        } catch (e: CancellationException) {
            destination.delete()
            throw e
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    private suspend fun reportModelProgress(callback: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main.immediate) { callback(message) }
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
            return AssistantResult.Unavailable("O modelo local integrado não está disponível nesta versão.")
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
            ?: return@withContext AssistantResult.Unavailable("A IA local ainda está inicializando. Tente enviar novamente quando o status indicar que está pronta.")

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
            val requestedText = conversation[lastUserIndex].content
            val projectGeneration = requestedText.contains("TAREFA: criar um projeto", ignoreCase = true) ||
                requestedText.contains("TAREFA: criar código-fonte funcional", ignoreCase = true) ||
                requestedText.contains("REGRAS TÉCNICAS E ESQUEMA:", ignoreCase = true) ||
                requestedText.contains("Esquema obrigatório:", ignoreCase = true)
            if (!projectGeneration && isExplicitImageGenerationRequest(requestedText)) {
                val capabilityNotice = "Esta versão do Kellvyn IA usa um modelo local somente de texto. Não consigo criar ou anexar uma imagem PNG/JPEG nem verificar uma referência visual nesta conversa. Posso ajudar a gerar código de arte vetorial/Canvas dentro de um app, mas isso não é um arquivo de imagem gerado."
                onPartial(capabilityNotice)
                return@withContext AssistantResult.Success(capabilityNotice)
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    buildString {
                        append(
                            "Você é Kellvyn IA, o assistente integrado ao aplicativo Kellvyn. Responda em português brasileiro, com clareza, naturalidade e sem enrolação. " +
                                "Identidade fixa: o projeto Kellvyn IA foi idealizado e criado por Kellvyn, que definiu sua identidade, seus objetivos e seu comportamento. Se perguntarem quem te criou, quem desenvolveu o projeto ou quem você é, reconheça explicitamente Kellvyn como criador do projeto. " +
                                "O modelo de linguagem é a tecnologia usada para gerar respostas; Kellvyn é o criador do aplicativo. Não confunda o modelo integrado com o aplicativo que o utiliza. " +
                                "Não invente capacidades nem ações executadas. Terminal/Termux é independente do chat e não participa de tarefas de programação. Para criar, editar ou compilar projetos, use as ferramentas internas do aplicativo; nunca sugira nem encaminhe essas tarefas para terminal/Termux. O editor serve apenas para guardar e consultar códigos, não é um agente independente. Para tarefas que precisarem de internet, use a conexão já disponível no Android sem pedir configuração manual. A conversa usa um modelo textual local; um fluxo especializado pode pedir ao mesmo modelo uma ilustração vetorial SVG, validar o código e renderizá-lo como PNG localmente. Isso não é geração por difusão/fotorrealista. Imagens anexadas são analisadas separadamente no aparelho por rótulos visuais aproximados e OCR; os pixels não são enviados ao modelo textual e os rótulos não equivalem a uma descrição visual completa. Nunca afirme ter gerado arquivo sem o fluxo de imagem retornar sucesso real."
                        )
                        conversation.firstOrNull { it.role == AssistantTurn.Role.SYSTEM }
                            ?.content
                            ?.take(MAX_SYSTEM_CONTEXT_CHARS)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { appendLine(); appendLine(); append(it) }
                    }
                ),
                initialMessages = priorMessages,
                // Bound output length so malformed model output cannot stream indefinitely.
                // Code generation gets a larger budget because it must return a complete JSON project.
                maxOutputToken = if (projectGeneration) MAX_PROJECT_OUTPUT_TOKENS else MAX_CHAT_OUTPUT_TOKENS,
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.9,
                    temperature = if (projectGeneration) 0.2 else 0.7,
                    seed = System.nanoTime().toInt()
                )
            )
            activeEngine.createConversation(config).use { chat ->
                chat.sendMessageAsync(conversation[lastUserIndex].content).collect { part ->
                    answer.append(part.contents.toString())
                    if (answer.isNotEmpty() && (lastPublishedLength == 0 || answer.length - lastPublishedLength >= 4)) {
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

    /**
     * Requests original vector markup from the one local language model. The caller validates
     * and rasterizes the markup before exposing it as an image file.
     */
    suspend fun generateSvgSource(
        prompt: String,
        onPartial: suspend (String) -> Unit = {}
    ): AssistantResult {
        val system = """
            You are generating a local vector illustration. Return only one complete standalone SVG document.
            This is a constrained SVG drawing, not photorealistic diffusion. Use viewBox="0 0 1024 1024".
            Allowed elements only: svg, g, defs, linearGradient, radialGradient, stop, rect, circle, ellipse,
            line, polyline, polygon, path, text, tspan. Use direct presentation attributes, never style attributes.
            Allowed attributes only: xmlns, width, height, viewBox, version, id, x, y, x1, y1, x2, y2, cx, cy,
            r, rx, ry, fill, stroke, stroke-width, opacity, fill-opacity, stroke-opacity, points, d, transform,
            offset, stop-color, stop-opacity, font-family, font-size, font-weight, font-style, text-anchor,
            dominant-baseline, stroke-linecap, stroke-linejoin, stroke-miterlimit, fill-rule, clip-rule,
            gradientUnits, gradientTransform, spreadMethod.
            Use only gradients referenced as url(#localId). No links, image tags, CSS, scripts, foreignObject,
            event handlers, entities, external references, or explanations. Produce an attractive, balanced
            vector illustration with layered shapes, deliberate colors, and clear composition. Make sure XML
            special characters in text nodes are escaped. Use transparent background only if the design needs it.
        """.trimIndent()
        val task = buildString {
            appendLine("TAREFA: criar código-fonte funcional de uma ilustração vetorial SVG autossuficiente.")
            appendLine("Descrição da ilustração solicitada: ")
            appendLine(prompt.take(4_000))
            appendLine()
            appendLine("Devolva somente o SVG completo. O aplicativo irá validá-lo e renderizá-lo como PNG localmente.")
        }
        return replyStreaming(
            listOf(
                AssistantTurn(AssistantTurn.Role.SYSTEM, system),
                AssistantTurn(AssistantTurn.Role.USER, task)
            ),
            onPartial
        )
    }

    override fun close() {
        try { engine?.close() } catch (_: Exception) { }
        engine = null
    }

}


/** True only for direct requests to create an image, not for discussion or image analysis. */
internal fun isExplicitImageGenerationRequest(prompt: String): Boolean {
    val value = prompt.lowercase(Locale.ROOT)
    return listOf(
        "gere uma imagem", "gerar uma imagem", "crie uma imagem", "criar uma imagem",
        "desenhe uma imagem", "desenhar uma imagem", "faça uma imagem", "faca uma imagem",
        "gere imagem", "gerar imagem", "crie imagem", "criar imagem",
        "gere uma foto", "gerar uma foto", "crie uma foto", "gere uma ilustração",
        "gere uma ilustracao", "crie uma ilustração", "crie uma ilustracao",
        "generate an image", "create an image", "draw an image"
    ).any { it in value }
}
