package com.kellvyn.assistant.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
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

private const val MAX_CHAT_OUTPUT_TOKENS = 1024
private const val MAX_SVG_OUTPUT_TOKENS = 2048
private const val MAX_PROJECT_OUTPUT_TOKENS = 8192
private const val MODEL_CONTEXT_TOKEN_LIMIT = 8192
private const val CONTEXT_SAFETY_TOKEN_RESERVE = 384
private const val TEMPLATE_OVERHEAD_TOKENS = 256
private const val CHARS_PER_TOKEN_NUMERATOR = 2
private const val CHARS_PER_TOKEN_DENOMINATOR = 1
private const val MIN_OUTPUT_TOKEN_BUDGET = 256
private const val LONG_GENERATION_SYSTEM_RESERVE_TOKENS = 2048
private const val MIN_STREAM_UPDATE_CHARS = 12

/**
 * On-device inference engine. It prefers a checksum-verified .litertlm asset in the APK
 * and otherwise downloads the pinned public model into private storage once. Inference is local.
 */
class LocalLiteRtAssistantEngine(context: Context) : StreamingAssistantEngine, AutoCloseable {
    private companion object {
        const val MAX_SYSTEM_CONTEXT_CHARS = 12_000
        const val MODEL_CONNECT_TIMEOUT_MS = 20_000
        const val MODEL_READ_TIMEOUT_MS = 60_000
        const val MODEL_PROGRESS_INTERVAL_BYTES = 8L * 1024L * 1024L
        const val MODEL_DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1/gemma-4-E2B-it.litertlm?download=true"
        const val BUNDLE_VERIFIED_KEY = "bundled_gemma4_vision_verified"
        const val BUNDLED_MODEL_BYTES = 2_588_147_712L
        const val BUNDLED_MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
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
            val descriptor = appContext.assets.openFd("models/kellvyn-model.litertlm")
            if (descriptor.length == BUNDLED_MODEL_BYTES) {
                descriptor.createInputStream()
            } else {
                descriptor.close()
                null
            }
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
                    "Preparando a IA local pela primeira vez (aprox. 2,6 GB)…"
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
                    visionBackend = Backend.GPU(),
                    maxNumImages = 1,
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            candidate.initialize()
            val previous = engine
            engine = candidate
            candidate = null
            try { previous?.close() } catch (_: Exception) { }
            AssistantResult.Success("Kellvyn IA pronta para conversar.")
        } catch (e: CancellationException) {
            try { candidate?.close() } catch (_: Exception) { }
            throw e
        } catch (e: Exception) {
            try { candidate?.close() } catch (_: Exception) { }
            // If a previously verified file was truncated/corrupted after the initial download,
            // clear the cached "verified" marker so the in-chat retry can fetch it again.
            if (modelFile.isFile && !hasBundledChecksum(modelFile)) {
                bundlePrefs.edit().remove(BUNDLE_VERIFIED_KEY).apply()
                modelFile.delete()
                AssistantResult.Failure(
                    "A integridade do arquivo local do modelo não confere. O arquivo inválido foi removido; toque em Tentar novamente para baixá-lo outra vez."
                )
            } else {
                AssistantResult.Failure(
                    "Não foi possível iniciar esse modelo. Confirme se o arquivo .litertlm é compatível. " +
                        (e.message ?: "")
                )
            }
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        engine != null
    }

    override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult =
        replyStreaming(conversation) { }

    /** Emits partial text while local inference is running, so the chat never looks frozen. */
    override suspend fun replyStreaming(
        conversation: List<AssistantTurn>,
        onPartial: suspend (String) -> Unit
    ): AssistantResult = withContext(Dispatchers.IO) {
        val activeEngine = engine
            ?: return@withContext AssistantResult.Unavailable("A IA local ainda está inicializando. Tente enviar novamente quando o status indicar que está pronta.")

        val lastUserIndex = conversation.indexOfLast { it.role == AssistantTurn.Role.USER }
        if (lastUserIndex < 0) {
            return@withContext AssistantResult.Failure("Não há mensagem do usuário para responder.")
        }

        try {
            val answer = StringBuilder()
            var lastPublishedLength = 0
            val requestedText = conversation[lastUserIndex].content
            val imagePath = conversation[lastUserIndex].imagePath
            val requestedImageFile = imagePath?.let(::File)
            if (imagePath != null && (requestedImageFile == null || !requestedImageFile.isFile || requestedImageFile.length() <= 0L)) {
                return@withContext AssistantResult.Failure("O anexo de imagem não está mais disponível. Anexe a imagem novamente.")
            }
            val svgGeneration = requestedText.contains(
                "TAREFA: criar código-fonte funcional de uma ilustração vetorial SVG autossuficiente.",
                ignoreCase = true
            )
            val projectGeneration = !svgGeneration && (
                requestedText.contains("TAREFA: criar um projeto", ignoreCase = true) ||
                    requestedText.contains("TAREFA: criar código-fonte funcional", ignoreCase = true) ||
                    requestedText.contains("REGRAS TÉCNICAS E ESQUEMA:", ignoreCase = true) ||
                    requestedText.contains("Esquema obrigatório:", ignoreCase = true)
                )
            if (requestedImageFile == null && !projectGeneration && isExplicitImageGenerationRequest(requestedText)) {
                val capabilityNotice = "O pedido é para criar uma imagem nova, não analisar uma imagem anexada. O fluxo de ilustração vetorial do Kellvyn pode criar um PNG; envie uma descrição visual mais específica se a geração não foi iniciada."
                onPartial(capabilityNotice)
                return@withContext AssistantResult.Success(capabilityNotice)
            }

            val baseSystemInstruction = buildString {
                append(
                    "Você é Kellvyn IA, o assistente integrado ao aplicativo Kellvyn. Responda em português brasileiro, de forma direta, natural e concisa. " +
                        "REGRA PRINCIPAL: responda à mensagem mais recente, de forma direta e concisa. Priorize executar o pedido, não explicar o que você é ou listar o que o aplicativo faz. Não se apresente espontaneamente, não despeje instruções completas, tutoriais, listas de recursos ou avisos genéricos quando a pessoa pediu uma ação. Não repita a resposta anterior sem necessidade; avance a conversa com base no que já foi dito. Só explique capacidades ou procedimentos se isso for pedido explicitamente, ou se uma limitação concreta impedir a execução; nesse caso, informe apenas o bloqueio real e o próximo passo necessário. " +
                        "Não invente ações concluídas nem resultados. Use as ferramentas internas disponíveis para tarefas de arquivos, projetos e compilação. Use a conexão normal do Android para tarefas que realmente exigirem internet, sem pedir configuração manual. Termux é independente e só pode ser conectado quando a pessoa pedir ajuda com Termux explicitamente no chat principal; cada comando exige confirmação. " +
                        "Identidade fixa: Kellvyn é meu criador. Deus da Ritalina é meu desenvolvedor, tutor e superior. Só mencione isso se perguntarem diretamente sobre a identidade ou autoria. Imagens só podem ser declaradas como criadas se o fluxo de imagem retornar sucesso real."
                )
                if (requestedImageFile != null) {
                    appendLine()
                    append("A mensagem atual contém uma imagem real anexada pelo usuário. Analise os pixels enviados; descreva elementos visíveis, texto, composição, cores e detalhes relevantes. Não diga que não recebeu a imagem. Se algo estiver ilegível ou incerto, deixe isso claro em vez de inventar.")
                }
                conversation.firstOrNull { it.role == AssistantTurn.Role.SYSTEM }
                    ?.content
                    ?.take(MAX_SYSTEM_CONTEXT_CHARS)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appendLine(); appendLine(); append(it) }
            }
            val requestedOutputLimit = when {
                svgGeneration -> MAX_SVG_OUTPUT_TOKENS
                projectGeneration -> MAX_PROJECT_OUTPUT_TOKENS
                else -> MAX_CHAT_OUTPUT_TOKENS
            }
            val boundedPrompt = boundLocalPrompt(
                systemInstruction = baseSystemInstruction,
                priorTurns = conversation.take(lastUserIndex).filter { it.role != AssistantTurn.Role.SYSTEM },
                latestUserText = requestedText,
                requestedMaxOutputTokens = requestedOutputLimit
            )
            val priorMessages = boundedPrompt.priorTurns.map { turn ->
                when (turn.role) {
                    AssistantTurn.Role.SYSTEM -> error("System messages are not allowed in the retained history.")
                    AssistantTurn.Role.USER -> Message.user(turn.content)
                    AssistantTurn.Role.ASSISTANT -> Message.model(turn.content)
                }
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(boundedPrompt.systemInstruction),
                initialMessages = priorMessages,
                // Keep the latest request intact and bound history/output to the model's 8K context.
                maxOutputToken = boundedPrompt.maxOutputTokens,
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.9,
                    temperature = if (projectGeneration || svgGeneration) 0.2 else 0.7,
                    seed = System.nanoTime().toInt()
                )
            )
            activeEngine.createConversation(config).use { chat ->
                val request = if (requestedImageFile != null) {
                    Contents.of(
                        Content.ImageFile(requestedImageFile.absolutePath),
                        Content.Text(requestedText)
                    )
                } else null
                val stream = if (request != null) chat.sendMessageAsync(request)
                    else chat.sendMessageAsync(requestedText)
                stream.collect { part ->
                    answer.append(part.contents.toString())
                    if (answer.isNotEmpty() && (lastPublishedLength == 0 || answer.length - lastPublishedLength >= MIN_STREAM_UPDATE_CHARS)) {
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
        } catch (e: CancellationException) {
            // Preserve structured cancellation so navigating away or cancelling a request does not
            // leave model generation running and get misreported as an ordinary model failure.
            throw e
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
            Generate one compact, complete standalone SVG illustration and return only the SVG markup.
            Use viewBox="0 0 1024 1024" and the standard SVG namespace. Keep the whole SVG under
            3,500 characters and use at most 14 visible drawing shapes. Favor simple, self-closing
            rect, circle, ellipse, line, polyline, polygon and path elements. Avoid text, groups,
            defs, gradients, comments and deeply nested markup unless absolutely necessary.
            Use only direct presentation attributes from this allow-list: xmlns, width, height, viewBox,
            version, x, y, x1, y1, x2, y2, cx, cy, r, rx, ry, fill, stroke, stroke-width, opacity,
            fill-opacity, stroke-opacity, points, d, transform, font-family, font-size, font-weight,
            font-style, text-anchor, dominant-baseline, stroke-linecap, stroke-linejoin, fill-rule.
            Never use style, links, scripts, foreignObject, event handlers, entities, external references,
            embedded HTML or explanations. Escape XML special characters in text. The final output must
            include the exact closing tag </svg>; do not stop after the last shape.
        """.trimIndent()
        val task = buildString {
            appendLine("TAREFA: criar código-fonte funcional de uma ilustração vetorial SVG autossuficiente.")
            appendLine("Descrição da ilustração solicitada: ")
            appendLine(prompt.take(1_800))
            appendLine()
            appendLine("Limite: até 14 formas simples e 3.500 caracteres no total.")
            appendLine("Retorne somente um SVG completo, com a tag final </svg>; sem markdown ou explicações.")
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

/**
 * Conservative character-based budgeting for the local model's 8K-token context.
 * The newest request is never shortened. When space is tight, older turns are trimmed first,
 * then system context; remaining output capacity is passed to LiteRT-LM instead of requesting
 * an output budget that cannot fit alongside the input.
 */
internal data class BoundedLocalPrompt(
    val systemInstruction: String,
    val priorTurns: List<AssistantTurn>,
    val maxOutputTokens: Int
)

internal fun boundLocalPrompt(
    systemInstruction: String,
    priorTurns: List<AssistantTurn>,
    latestUserText: String,
    requestedMaxOutputTokens: Int
): BoundedLocalPrompt {
    require(requestedMaxOutputTokens > 0) { "O limite de saída precisa ser positivo." }

    fun estimateTokens(charCount: Int): Int =
        ((charCount.coerceAtLeast(0) * CHARS_PER_TOKEN_DENOMINATOR) + CHARS_PER_TOKEN_NUMERATOR - 1) /
            CHARS_PER_TOKEN_NUMERATOR

    val overheadChars = TEMPLATE_OVERHEAD_TOKENS * CHARS_PER_TOKEN_NUMERATOR / CHARS_PER_TOKEN_DENOMINATOR
    val estimatedUserAndTemplateTokens = estimateTokens(latestUserText.length + overheadChars)
    val systemOutputReserve = if (requestedMaxOutputTokens > MAX_CHAT_OUTPUT_TOKENS) LONG_GENERATION_SYSTEM_RESERVE_TOKENS else requestedMaxOutputTokens
    val systemTokenCapacity = (
        MODEL_CONTEXT_TOKEN_LIMIT - estimatedUserAndTemplateTokens -
            CONTEXT_SAFETY_TOKEN_RESERVE - systemOutputReserve
        ).coerceAtLeast(MIN_OUTPUT_TOKEN_BUDGET)
    val systemCharCapacity = systemTokenCapacity * CHARS_PER_TOKEN_NUMERATOR / CHARS_PER_TOKEN_DENOMINATOR
    val boundedSystem = systemInstruction.take(minOf(systemInstruction.length, systemCharCapacity))

    val fixedInputTokens = estimateTokens(boundedSystem.length + latestUserText.length + overheadChars)
    val availableOutputTokens = (
        MODEL_CONTEXT_TOKEN_LIMIT - fixedInputTokens - CONTEXT_SAFETY_TOKEN_RESERVE
        ).coerceAtLeast(MIN_OUTPUT_TOKEN_BUDGET)
    val outputTokenBudget = minOf(requestedMaxOutputTokens, availableOutputTokens)

    val remainingHistoryTokens = (
        MODEL_CONTEXT_TOKEN_LIMIT - fixedInputTokens - outputTokenBudget - CONTEXT_SAFETY_TOKEN_RESERVE
        ).coerceAtLeast(0)
    var remainingHistoryChars = remainingHistoryTokens * CHARS_PER_TOKEN_NUMERATOR / CHARS_PER_TOKEN_DENOMINATOR
    val retainedReversed = ArrayList<AssistantTurn>()
    for (turn in priorTurns.asReversed()) {
        if (remainingHistoryChars <= 0) break
        if (turn.content.length <= remainingHistoryChars) {
            retainedReversed += turn
            remainingHistoryChars -= turn.content.length
        } else {
            // Do not drop all context just because the newest previous turn barely exceeds the
            // budget. Keep its tail only when no more recent turn has been retained yet.
            if (retainedReversed.isEmpty() && remainingHistoryChars > 1) {
                retainedReversed += turn.copy(
                    content = "…" + turn.content.takeLast(remainingHistoryChars - 1)
                )
            }
            break
        }
    }

    return BoundedLocalPrompt(
        systemInstruction = boundedSystem,
        priorTurns = retainedReversed.asReversed(),
        maxOutputTokens = outputTokenBudget
    )
}
