package com.kellvyn.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.text.Html
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.json.JSONObject
import com.kellvyn.assistant.workspace.LocalProjectBuilder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motor de IA local da Kellvyn. O modelo é baixado somente depois de o usuário autorizar.
 * Após o download, o processamento de texto e das imagens suportadas ocorre no aparelho.
 */
class LocalBrain(context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onPartial(text: String)
        fun onComplete(text: String, backend: String)
        fun onError(message: String)
    }

    private data class PreparedInput(val contents: Contents, val containsImages: Boolean)

    private val app = context.applicationContext
    private val localProjectBuilder = LocalProjectBuilder(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engineLock = Any()
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var backendName = "GPU"
    @Volatile private var supportsVision = false
    private var activeJob: Job? = null
    private var conversationCloseJob: Job? = null
    @Volatile private var activeProtectionEnabled: Boolean? = null

    private val modelFile = File(app.filesDir, MODEL_NAME)
    private val partialFile = File(app.filesDir, MODEL_NAME + ".download")
    private val readyFile = File(app.filesDir, MODEL_NAME + ".verified")

    fun hasModel(): Boolean = try {
        readyFile.isFile && modelFile.isFile &&
            NativeModelPolicy.isVerified(modelFile.length(), readyFile.readText())
    } catch (_: IOException) {
        false
    }

    /** True when this process still holds the active in-memory conversation. */
    fun hasActiveConversation(): Boolean = conversation != null

    /** Start a genuinely new chat without deleting the already downloaded local model. */
    fun resetConversation() {
        val previous = synchronized(engineLock) {
            val current = conversation
            conversation = null
            activeProtectionEnabled = null
            current
        }
        if (previous != null) {
            conversationCloseJob = scope.launch(Dispatchers.IO) {
                try {
                    previous.close()
                } catch (error: Exception) {
                    Log.w(TAG, "Não foi possível encerrar a conversa anterior", error)
                }
            }
        }
    }

    /** Bot runtime helper: wait until the previous reply and conversation are safely closed. */
    suspend fun resetConversationAndWait() {
        activeJob?.join()
        resetConversation()
        conversationCloseJob?.join()
    }

    fun reply(prompt: String, attachmentUri: Uri?, attachmentName: String?, listener: Listener) {
        if (activeJob?.isActive == true) {
            listener.onError("A Kellvyn já está processando outra mensagem. Aguarde a resposta atual.")
            return
        }
        activeJob = scope.launch {
            try {
                conversationCloseJob?.join()

                // A user-confirmed SDK license can resume a saved local build without needing
                // another model inference or sending the confirmation phrase to the model.
                if (attachmentUri == null && localProjectBuilder.hasPendingLicenseAcceptance(prompt)) {
                    val buildAnswer = localProjectBuilder.handleSdkLicenseAcceptance(
                        prompt = prompt,
                        onBeforeBuild = { releaseModelMemoryForLocalBuild() },
                        onProgress = { message -> reportStatus(listener, message) }
                    )
                    if (buildAnswer != null) {
                        withContext(Dispatchers.Main.immediate) {
                            listener.onComplete(buildAnswer, "COMPILADOR ANDROID LOCAL")
                        }
                        return@launch
                    }
                }

                val nativeBuildRequest = localProjectBuilder.isNativeApkRequest(prompt)
                val promptForModel = localProjectBuilder.addCompilerContract(prompt)
                val protectionEnabled = app.getSharedPreferences("kellvyn_native", Context.MODE_PRIVATE)
                    .getBoolean("protection", false)
                if (conversation != null && activeProtectionEnabled != protectionEnabled) {
                    resetConversation()
                    conversationCloseJob?.join()
                }
                ensureModel(listener)
                val promptWithWebContext = attachWebContextWhenEnabled(promptForModel, attachmentUri, listener)
                val input = prepareInput(promptWithWebContext, attachmentUri, attachmentName, listener)
                val activeConversation = withContext(Dispatchers.IO) { getConversation(listener) }
                if (input.containsImages && !supportsVision) {
                    throw IllegalStateException("O motor deste aparelho iniciou em modo somente texto. Não foi possível analisar essa imagem/PDF; envie uma mensagem de texto ou tente novamente após reiniciar o app.")
                }
                reportStatus(listener, "GERANDO RESPOSTA LOCAL")
                val output = StringBuilder()
                activeConversation.sendMessageAsync(
                    input.contents,
                    maxOutputToken = if (nativeBuildRequest) MAX_PROJECT_OUTPUT_TOKENS else MAX_OUTPUT_TOKENS
                ).collect { message ->
                    val chunk = message.toString()
                    if (chunk.isNotEmpty()) {
                        output.append(chunk)
                        // Source blocks are kept off the chat stream while the native builder
                        // validates and compiles. The same existing chat bubble reports real status.
                        if (!nativeBuildRequest) {
                            withContext(Dispatchers.Main.immediate) {
                                listener.onPartial(output.toString())
                            }
                        }
                    }
                }
                val finalText = output.toString().trim()
                if (finalText.isEmpty()) {
                    throw IllegalStateException("O modelo local terminou sem produzir uma resposta. Tente novamente.")
                }
                val buildAnswer = if (nativeBuildRequest) {
                    localProjectBuilder.compileGeneratedProject(
                        prompt = prompt,
                        response = finalText,
                        onBeforeBuild = { releaseModelMemoryForLocalBuild() },
                        onProgress = { message -> reportStatus(listener, message) }
                    )
                } else null
                val finalAnswer = buildAnswer ?: finalText
                withContext(Dispatchers.Main.immediate) {
                    listener.onComplete(
                        finalAnswer,
                        if (buildAnswer != null) "COMPILADOR ANDROID LOCAL" else backendName
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // Failed prefill/generation must not leave a poisoned native session for the next turn.
                resetConversation()
                Log.e(TAG, "Falha no motor local da Kellvyn", error)
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: "Falha desconhecida ao iniciar o modelo local."
                withContext(Dispatchers.Main.immediate) {
                    listener.onError(message)
                }
            }
        }
    }

    /**
     * Release the inference session/model before local DEX packaging to reduce RAM pressure.
     * The verified model file remains on disk and the next chat turn can initialize it again.
     */
    private suspend fun releaseModelMemoryForLocalBuild() {
        val previous = synchronized(engineLock) {
            val current = conversation to engine
            conversation = null
            engine = null
            supportsVision = false
            activeProtectionEnabled = null
            current
        }
        withContext(Dispatchers.IO) {
            try {
                previous.first?.close()
            } catch (error: Exception) {
                Log.w(TAG, "Não foi possível fechar a conversa antes da compilação local", error)
            }
            try {
                val previousEngine = previous.second
                if (previousEngine?.isInitialized() == true) previousEngine.close()
            } catch (error: Exception) {
                Log.w(TAG, "Não foi possível liberar todo o motor local antes da compilação", error)
            }
        }
    }

    private fun reportStatus(listener: Listener, message: String) {
        mainHandler.post { listener.onStatus(message) }
    }

    /**
     * Uses the phone's current internet connection only when the existing Web-access setting is enabled.
     * Uses the phone's network for public search snippets and, when explicitly asked, page excerpts.
     */
    private suspend fun attachWebContextWhenEnabled(
        prompt: String,
        attachmentUri: Uri?,
        listener: Listener
    ): String {
        val enabled = app.getSharedPreferences("kellvyn_native", Context.MODE_PRIVATE)
            .getBoolean("web_access", false)
        if (!enabled || attachmentUri != null || !isLookupRequest(prompt)) return prompt

        reportStatus(listener, "PESQUISANDO NA WEB")
        val context = withContext(Dispatchers.IO) { fetchWebContext(prompt) }
        if (context.isBlank()) return prompt

        return "Contexto de pesquisa web obtido a partir de páginas públicas. Pode estar incompleto ou desatualizado. " +
            "Use somente as informações e fontes fornecidas abaixo; quando usar uma delas, cite o título e o URL. " +
            "Não afirme ter lido o conteúdo completo de páginas quando só houver título/resumo.\n\n" +
            context + "\n\nPergunta do usuário:\n" + prompt
    }

    private fun isLookupRequest(prompt: String): Boolean = WebLookupPolicy.isLookupRequest(prompt)

    private fun normalizeSearchResultUrl(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            var candidate = raw.trim()
            val parsed = URI(candidate)
            if (parsed.host?.endsWith("duckduckgo.com", ignoreCase = true) == true &&
                parsed.rawPath?.startsWith("/l/") == true) {
                val encodedTarget = parsed.rawQuery.orEmpty().split("&")
                    .mapNotNull { part ->
                        val index = part.indexOf("=")
                        if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
                    }.toMap()["uddg"]
                if (!encodedTarget.isNullOrBlank()) candidate = URLDecoder.decode(encodedTarget, "UTF-8")
            }
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return ""
            val host = uri.host?.lowercase(Locale.ROOT) ?: return ""
            if (scheme != "http" && scheme != "https") return ""
            if (host == "localhost" || host.endsWith(".local") || host.endsWith(".localhost")) return ""
            if (host == "0.0.0.0" || host == "::1" || host.startsWith("127.") ||
                host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return ""
            val parts = host.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
                val first = parts[0].toInt()
                val second = parts[1].toInt()
                if (first == 0 || first == 10 || first == 127 || first == 169 && second == 254 ||
                    first == 172 && second in 16..31 || first == 192 && second == 168) return ""
            }
            uri.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private data class WebResult(val title: String, val url: String, val snippet: String)

    private fun fetchWebContext(query: String): String {
        return try {
            val explicitUrl = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
                .find(query)?.value?.trimEnd('.', ',', ')', ']', '}', '"')
            if (!explicitUrl.isNullOrBlank()) {
                val safeUrl = normalizeSearchResultUrl(explicitUrl)
                if (safeUrl.isNotBlank()) {
                    val doc = Jsoup.connect(safeUrl)
                        .userAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36 KellvynIA/1.1")
                        .timeout(WEB_TIMEOUT_MS)
                        .maxBodySize(250_000)
                        .followRedirects(false)
                        .get()
                    val finalUrl = normalizeSearchResultUrl(doc.location())
                    if (finalUrl.isNotBlank()) {
                        val title = doc.title().take(180)
                        val body = doc.select("main, article, [role=main]").firstOrNull()?.text()
                            ?.takeIf { it.isNotBlank() } ?: doc.body()?.text().orEmpty()
                        val excerpt = body.replace(Regex("\\s+"), " ").trim().take(3500)
                        if (excerpt.isNotBlank()) {
                            return "[Página consultada] $title\nURL: $finalUrl\nConteúdo extraído: $excerpt"
                        }
                    }
                }
            }

            val searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
            val search = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36 KellvynIA/1.1")
                .timeout(WEB_TIMEOUT_MS)
                .maxBodySize(250_000)
                .get()
            val results = ArrayList<WebResult>()
            for (element in search.select("div.result, article[data-testid=result]").take(5)) {
                val link = element.selectFirst("a.result__a") ?: element.selectFirst("a[data-testid=result-title-a]")
                    ?: element.selectFirst("h2 a") ?: continue
                val title = link.text().trim().take(180)
                val url = normalizeSearchResultUrl(link.absUrl("href").ifBlank { link.attr("href") })
                if (title.isBlank() || url.isBlank()) continue
                val snippet = (element.selectFirst(".result__snippet") ?: element.selectFirst("[data-result=snippet]"))
                    ?.text()?.trim()?.take(600).orEmpty()
                if (results.none { it.url == url }) results.add(WebResult(title, url, snippet))
                if (results.size >= 4) break
            }
            if (results.isNotEmpty()) {
                results.mapIndexed { index, result ->
                    buildString {
                        append("[Fonte ").append(index + 1).append("] ").append(result.title)
                        append("\nURL: ").append(result.url)
                        if (result.snippet.isNotBlank()) append("\nResumo do resultado: ").append(result.snippet)
                    }
                }.joinToString("\n\n")
            } else {
                fetchWikipediaContext(query)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Pesquisa Web indisponível; tentando a fonte pública alternativa", error)
            fetchWikipediaContext(query)
        }
    }

    private fun fetchWikipediaContext(query: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://pt.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=search" +
                    "&gsrsearch=" + encoded + "&gsrlimit=3&prop=extracts&exintro=1&explaintext=1&exchars=1000"
            )
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = WEB_TIMEOUT_MS
                readTimeout = WEB_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "KellvynIA-Android/1.1 (local on-device assistant)")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return ""
            val body = connection.inputStream.use { readLimitedText(it, MAX_WEB_RESPONSE_CHARS) }
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return ""
            val keys = pages.keys()
            val excerpts = ArrayList<Pair<Int, String>>()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val extract = page.optString("extract", "").trim()
                if (extract.isEmpty()) continue
                val title = page.optString("title", "Wikipédia")
                excerpts.add(page.optInt("index", 99) to "[$title] $extract")
            }
            excerpts.sortedBy { it.first }.take(3).joinToString("\n\n") { it.second }
        } catch (error: Exception) {
            Log.w(TAG, "Consulta à Wikipédia não disponível; seguindo sem contexto Web", error)
            ""
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun ensureModel(listener: Listener): File = withContext(Dispatchers.IO) {
        if (modelFile.isFile && modelFile.length() == MIN_MODEL_BYTES) {
            if (readyFile.isFile && readyFile.readText().trim() == MODEL_SHA256) {
                return@withContext modelFile
            }
            reportStatus(listener, "VERIFICANDO MODELO LOCAL")
            val currentHash = sha256(modelFile)
            if (currentHash == MODEL_SHA256) {
                readyFile.writeText(MODEL_SHA256)
                return@withContext modelFile
            }
            modelFile.delete()
            readyFile.delete()
        }

        val storage = StatFs(app.filesDir.absolutePath)
        if (partialFile.length() > MIN_MODEL_BYTES) partialFile.delete()
        val resumeAt = partialFile.length()
        val requiredBytes = (MIN_MODEL_BYTES - resumeAt).coerceAtLeast(0L) + STORAGE_MARGIN_BYTES
        if (storage.availableBytes < requiredBytes) {
            throw IOException("Há pouco espaço livre para baixar o modelo de IA de aproximadamente 2,01 GB. Libere espaço e tente novamente.")
        }

        reportStatus(listener, "CONECTANDO PARA BAIXAR O MODELO")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KellvynIA-Android/1.0")
            if (resumeAt > 0L) setRequestProperty("Range", "bytes=" + resumeAt + "-")
        }

        try {
            val response = connection.responseCode
            var append = NativeModelPolicy.canAppend(resumeAt, response, connection.getHeaderField("Content-Range"))
            var actualOffset = resumeAt
            if (response == HttpURLConnection.HTTP_OK && resumeAt > 0L) {
                // Server ignored Range; replace the partial file with the full response.
                partialFile.delete()
                actualOffset = 0L
                append = false
            } else if (response == 416 && resumeAt >= MIN_MODEL_BYTES) {
                if (sha256(partialFile) == MODEL_SHA256) {
                    finishModelDownload()
                    return@withContext modelFile
                }
                partialFile.delete()
                throw IOException("O download parcial do modelo não passou na verificação. Tente baixar novamente.")
            } else if (response != HttpURLConnection.HTTP_OK && response != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("O servidor do modelo respondeu HTTP " + response + ". Verifique sua internet e tente novamente.")
            }
            if (response == HttpURLConnection.HTTP_PARTIAL && !append) {
                partialFile.delete()
                throw IOException("O servidor retornou um trecho incompatível com o download salvo. Tente novamente para baixar o arquivo completo.")
            }

            val contentRange = connection.getHeaderField("Content-Range")
            val totalFromRange = contentRange?.substringAfterLast("/")?.toLongOrNull()
            val contentLength = connection.contentLengthLong
            val totalBytes = totalFromRange ?: if (contentLength > 0L) contentLength + if (append) actualOffset else 0L else 0L

            val digest = MessageDigest.getInstance("SHA-256")
            if (append && actualOffset > 0L) {
                partialFile.inputStream().buffered().use { input ->
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buf)
                        if (read < 0) break
                        digest.update(buf, 0, read)
                    }
                }
            }

            var downloaded = actualOffset
            var lastPercent = -1
            FileOutputStream(partialFile, append).use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (percent != lastPercent && (percent % 2 == 0 || percent == 1)) {
                                lastPercent = percent
                                reportStatus(listener, "BAIXANDO MODELO LOCAL: " + percent + "%")
                            }
                        } else if (downloaded % PROGRESS_CHUNK_BYTES < read) {
                            reportStatus(listener, "BAIXANDO MODELO LOCAL: " + (downloaded / 1_000_000L) + " MB")
                        }
                    }
                }
            }

            if (partialFile.length() != MIN_MODEL_BYTES) {
                throw IOException("O download do modelo foi interrompido. Envie a mensagem novamente para continuar de onde parou.")
            }
            if (totalBytes > 0L && partialFile.length() != totalBytes) {
                throw IOException("O tamanho do modelo baixado não corresponde ao arquivo esperado. Envie a mensagem novamente para retomar o download.")
            }

            val downloadedHash = digest.digest().toHexString()
            if (downloadedHash != MODEL_SHA256) {
                partialFile.delete()
                throw IOException("A verificação de integridade do modelo falhou. O arquivo parcial foi descartado; tente novamente.")
            }

            finishModelDownload()
            reportStatus(listener, "MODELO LOCAL BAIXADO E VERIFICADO")
            modelFile
        } finally {
            connection.disconnect()
        }
    }

    private fun finishModelDownload() {
        if (modelFile.exists()) modelFile.delete()
        if (!partialFile.renameTo(modelFile)) {
            partialFile.copyTo(modelFile, overwrite = true)
            partialFile.delete()
        }
        readyFile.writeText(MODEL_SHA256)
    }

    private fun getConversation(listener: Listener): Conversation {
        conversation?.let { return it }
        synchronized(engineLock) {
            conversation?.let { return it }
            reportStatus(listener, "INICIANDO MOTOR LOCAL")
            var selectedEngine: Engine? = engine
            var vision = supportsVision
            var backend = backendName

            if (selectedEngine == null) {
            try {
                selectedEngine = createEngine(Backend.GPU(), enableVision = true)
                vision = true
                backend = "GPU"
            } catch (gpuError: Exception) {
                Log.w(TAG, "Inicialização multimodal GPU falhou; tentando CPU multimodal", gpuError)
                safeClose(selectedEngine)
                selectedEngine = null
                try {
                    selectedEngine = createEngine(Backend.CPU(threadCount = 4), enableVision = true)
                    vision = true
                    backend = "CPU"
                } catch (cpuVisionError: Exception) {
                    Log.w(TAG, "Inicialização multimodal CPU falhou; tentando texto no CPU", cpuVisionError)
                    safeClose(selectedEngine)
                    selectedEngine = null
                    selectedEngine = try {
                        createEngine(Backend.CPU(threadCount = 4), enableVision = false)
                    } catch (textError: Exception) {
                        safeClose(selectedEngine)
                        throw IOException("Não foi possível iniciar o Gemma no GPU nem no CPU deste aparelho. Detalhe: " + (textError.message ?: "erro de inicialização"), textError)
                    }
                    vision = false
                    backend = "CPU"
                }
            }
            }

            engine = selectedEngine
            backendName = backend
            supportsVision = vision
            val protectionEnabled = app.getSharedPreferences("kellvyn_native", Context.MODE_PRIVATE)
                .getBoolean("protection", false)
            val config = ConversationConfig(
                systemInstruction = Contents.of(ProtectionPolicy.systemInstruction(SYSTEM_PROMPT, protectionEnabled)),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.65),
                prefillPrefaceOnInit = true,
                maxOutputToken = MAX_OUTPUT_TOKENS
            )
            val created = try {
                val readyEngine = selectedEngine ?: throw IOException("O motor local não foi inicializado.")
                readyEngine.createConversation(config)
            } catch (error: Exception) {
                safeClose(selectedEngine)
                engine = null
                throw IOException("O motor iniciou, mas não conseguiu criar a conversa. " + (error.message ?: ""), error)
            }
            conversation = created
            activeProtectionEnabled = protectionEnabled
            reportStatus(listener, if (vision) "MOTOR LOCAL " + backend + " PRONTO" else "MOTOR LOCAL CPU PRONTO (SOMENTE TEXTO)")
            return created
        }
    }

    private fun createEngine(backend: Backend, enableVision: Boolean): Engine {
        val created = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                visionBackend = if (enableVision) backend else null,
                maxNumTokens = MAX_CONTEXT_TOKENS,
                maxNumImages = if (enableVision) MAX_IMAGES else null,
                cacheDir = app.cacheDir.absolutePath
            )
        )
        try {
            created.initialize()
            return created
        } catch (error: Exception) {
            safeClose(created)
            throw error
        }
    }

    private fun safeClose(candidate: Engine?) {
        if (candidate != null) {
            try {
                if (candidate.isInitialized()) candidate.close()
            } catch (_: Exception) {
                // Initialization failure may leave the native engine partially unavailable.
            }
        }
    }

    private suspend fun prepareInput(
        prompt: String,
        uri: Uri?,
        name: String?,
        listener: Listener
    ): PreparedInput = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext PreparedInput(Contents.of(prompt), false)
        reportStatus(listener, "LENDO ANEXO LOCAL")
        val mime = app.contentResolver.getType(uri)?.lowercase(Locale.ROOT) ?: ""
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: ""

        if (mime.startsWith("image/") || ext in IMAGE_EXTENSIONS) {
            val file = copyAttachmentToCache(uri, ext.ifBlank { "img" })
            return@withContext PreparedInput(Contents.of(listOf(Content.Text(prompt), Content.ImageFile(file.absolutePath))), true)
        }

        if (mime == "application/pdf" || ext == "pdf") {
            val pages = renderPdfPages(uri)
            if (pages.isEmpty()) throw IOException("Não foi possível renderizar páginas legíveis desse PDF.")
            val notice = if (pages.size == MAX_PDF_PAGES) {
                prompt + "\n\nNota: foram incluídas apenas as primeiras " + MAX_PDF_PAGES + " páginas do PDF para respeitar os limites de memória do celular."
            } else prompt
            val content = ArrayList<Content>()
            content.add(Content.Text(notice))
            pages.forEach { content.add(Content.ImageFile(it.absolutePath)) }
            return@withContext PreparedInput(Contents.of(content), true)
        }

        if (ext == "docx") {
            val extracted = readDocx(uri)
            val contextText = prompt + "\n\nConteúdo extraído do DOCX (pode estar limitado):\n" + extracted
            return@withContext PreparedInput(Contents.of(contextText), false)
        }

        if (mime.startsWith("text/") || ext in TEXT_EXTENSIONS || mime == "application/json") {
            val extracted = app.contentResolver.openInputStream(uri)?.use { readLimitedText(it, MAX_ATTACHMENT_CHARS) }
                ?: throw IOException("Não foi possível abrir o texto do anexo.")
            if (extracted.isBlank()) throw IOException("O arquivo de texto está vazio ou não contém texto legível.")
            return@withContext PreparedInput(
                Contents.of(prompt + "\n\nConteúdo do arquivo " + (name ?: "anexado") + ":\n" + extracted),
                false
            )
        }

        throw IOException("O arquivo " + (name ?: "selecionado") + " foi selecionado, mas este app ainda não consegue extrair seu conteúdo. Não foi enviado conteúdo desse arquivo à IA.")
    }

    private fun copyAttachmentToCache(uri: Uri, extension: String): File {
        val safeExtension = extension.filter { it.isLetterOrDigit() }.take(8).ifBlank { "img" }
        val destination = File(app.cacheDir, "kellvyn-image-" + System.nanoTime() + "." + safeExtension)
        app.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Não foi possível abrir a imagem anexada.")
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) {
                        destination.delete()
                        throw IOException("Esta imagem excede o limite de " + (MAX_IMAGE_BYTES / 1_000_000L) + " MB para análise local.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return destination
    }

    private fun renderPdfPages(uri: Uri): List<File> {
        val descriptor: ParcelFileDescriptor = app.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Não foi possível abrir o PDF.")
        val rendered = ArrayList<File>()
        try {
            PdfRenderer(descriptor).use { pdf ->
                val pageCount = minOf(pdf.pageCount, MAX_PDF_PAGES)
                for (index in 0 until pageCount) {
                    pdf.openPage(index).use { page ->
                        val scale = minOf(1f, MAX_PDF_WIDTH.toFloat() / page.width, MAX_PDF_HEIGHT.toFloat() / page.height)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val output = File(app.cacheDir, "kellvyn-pdf-" + System.nanoTime() + "-" + index + ".png")
                            FileOutputStream(output).use { stream ->
                                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                    throw IOException("Falha ao preparar uma página do PDF.")
                                }
                            }
                            rendered.add(output)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                if (pdf.pageCount > MAX_PDF_PAGES) {
                    Log.i(TAG, "PDF tem " + pdf.pageCount + " páginas; apenas " + MAX_PDF_PAGES + " serão analisadas.")
                }
            }
        } finally {
            try { descriptor.close() } catch (_: Exception) {}
        }
        return rendered
    }

    private fun readDocx(uri: Uri): String {
        app.contentResolver.openInputStream(uri).use { raw ->
            if (raw == null) throw IOException("Não foi possível abrir o documento DOCX.")
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        val xml = readLimitedText(zip, MAX_ATTACHMENT_CHARS * 4)
                        val text = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL))
                            .findAll(xml)
                            .joinToString(" ") { decodeXml(it.groupValues[1]) }
                        if (text.isNotBlank()) return text.take(MAX_ATTACHMENT_CHARS)
                    }
                    zip.closeEntry()
                }
            }
        }
        throw IOException("Não encontrei texto legível no documento DOCX.")
    }

    private fun decodeXml(value: String): String =
        value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")

    private fun readLimitedText(input: InputStream, limit: Int): String {
        val reader = InputStreamReader(input, Charsets.UTF_8)
        val output = StringBuilder()
        val buffer = CharArray(8192)
        while (output.length < limit) {
            val wanted = minOf(buffer.size, limit - output.length)
            val read = reader.read(buffer, 0, wanted)
            if (read < 0) break
            output.append(buffer, 0, read)
        }
        return output.toString()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun close() {
        val pendingReply = activeJob
        val pendingClose = conversationCloseJob
        val currentConversation = conversation
        val currentEngine = engine
        conversation = null
        engine = null
        scope.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            pendingReply?.join()
            pendingClose?.join()
            try { currentConversation?.close() } catch (_: Exception) {}
            try {
                if (currentEngine?.isInitialized() == true) currentEngine.close()
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "KellvynLocalBrain"
        private const val MODEL_NAME = "gemma-4-E2B-it-gpu.litertlm"
        private const val MODEL_URL = NativeModelPolicy.MODEL_URL
        private const val MODEL_SHA256 = NativeModelPolicy.MODEL_SHA256
        private const val MIN_MODEL_BYTES = NativeModelPolicy.MODEL_BYTES
        private const val STORAGE_MARGIN_BYTES = 150_000_000L
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_CHUNK_BYTES = 25_000_000L
        private const val MAX_CONTEXT_TOKENS = 4096
        private const val MAX_OUTPUT_TOKENS = 768
        private const val MAX_PROJECT_OUTPUT_TOKENS = 2048
        private const val MAX_IMAGES = 3
        private const val MAX_PDF_PAGES = 3
        private const val MAX_PDF_WIDTH = 1280
        private const val MAX_PDF_HEIGHT = 1800
        private const val MAX_IMAGE_BYTES = 20_000_000L
        private const val MAX_ATTACHMENT_CHARS = 120_000
        private const val WEB_TIMEOUT_MS = 6_000
        private const val MAX_WEB_RESPONSE_CHARS = 90_000
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "csv", "xml", "html", "css", "java", "kt", "ts", "tsx",
            "js", "jsx", "py", "log", "yaml", "yml", "toml"
        )

        private const val SYSTEM_PROMPT = """Você é Kellvyn IA, uma única assistente de inteligência artificial. Responda em português brasileiro, de forma direta e natural, a menos que peçam outro idioma.

Seja útil em conversa, raciocínio, explicações e programação. Para pedidos de código, dê soluções completas e explique limitações importantes. Não diga que criou, instalou, compilou, pesquisou na Web ou executou algo se isso não ocorreu por uma função real do aplicativo. Não invente acesso a arquivos: use apenas o conteúdo efetivamente recebido no contexto. Se um arquivo não puder ser lido, diga isso claramente. Quando o usuário habilitar Acesso à Web, o aplicativo pode consultar resultados públicos de busca e trechos de páginas acessadas explicitamente; cite somente as fontes recebidas no contexto e seja transparente quando não encontrar resultados. O acesso à Web não significa executar código, publicar aplicativos ou operar bots. O navegador externo e a geração local de texto não significam, por si só, que ferramentas externas foram executadas.

Sua identidade é Kellvyn IA; não se identifique como Google, Google DeepMind, OpenAI ou outra empresa. Mantenha o foco no pedido atual e não despeje listas de suas próprias funções sem necessidade.

No projeto Kellvyn Assistente, a interface já criada no Lovable é a referência aprovada: preserve o arranjo existente, os botões, os menus, as cores e as animações; não proponha redesenho nem crie controles/modos extras. O APK final pretendido é Android nativo; não afirme que uma WebView é nativa. Quando autoria for relevante, reconheça Kellvyn como criador da IA e dos projetos e Deus da Ritalina como desenvolvedor, tutor e superior definido por Kellvyn. Não atribua a autoria a outras empresas ou modelos. Não diga que compilou, instalou, executou ou publicou nada sem confirmação de uma ferramenta real.

Bots no APK: a mesma Kellvyn IA pode atender bots Telegram e Discord quando o usuário pedir. Se perguntarem como configurar, explique que o token deve ser enviado apenas como comando local exato no chat principal: "/bot telegram token SEU_TOKEN" ou "/bot discord token SEU_TOKEN". Esses comandos são interceptados antes do modelo e armazenam o token cifrado pelo Android Keystore; nunca peça para mandar o token como mensagem normal, nunca repita tokens e nunca inclua credenciais na resposta. Depois de preparar o modelo local, o usuário ativa/desativa os bots no controle existente BOTS. O bot do Discord precisa de Message Content Intent autorizado no portal do Discord. Não diga que o bot está conectado até o runtime confirmar. O WhatsApp não tem runtime nativo nesta versão porque exige endpoint público de webhook; seja transparente sobre isso."""
    }
}
