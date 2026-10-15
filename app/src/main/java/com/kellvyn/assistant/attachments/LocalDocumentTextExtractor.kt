package com.kellvyn.assistant.attachments

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/**
 * Extracts searchable text from PDF, DOCX, and ODT locally. PDF pages without usable text are
 * rendered to bounded-size bitmaps and passed through bundled on-device Latin OCR. Size, page,
 * render and output limits keep large or malicious documents from consuming unbounded resources.
 */
object LocalDocumentTextExtractor {
    private const val MAX_DOCUMENT_BYTES = 20 * 1024 * 1024
    private const val MAX_XML_BYTES = 5 * 1024 * 1024
    private const val MAX_ZIP_EXPANDED_BYTES = 50L * 1024L * 1024L
    private const val MAX_EXTRACTED_CHARS = 60_000
    private const val MAX_PDF_PAGES = 50
    private const val MIN_NATIVE_PAGE_TEXT_CHARS = 10
    private const val MAX_PDF_OCR_BITMAP_EDGE = 1_700f
    private const val MAX_PDF_OCR_SCALE = 2.0f
    private const val PDF_OCR_PAGE_TIMEOUT_SECONDS = 15L
    private const val MAX_PDF_OCR_TOTAL_TIMEOUT_SECONDS = 90L

    fun supports(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf("pdf", "docx", "odt") || mimeType.lowercase() in setOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text"
        )
    }

    fun extract(context: Context, uri: Uri, fileName: String, mimeType: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val kind = when {
            extension == "pdf" || mimeType.equals("application/pdf", true) -> "pdf"
            extension == "docx" || mimeType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true
            ) -> "docx"
            extension == "odt" || mimeType.equals("application/vnd.oasis.opendocument.text", true) -> "odt"
            else -> throw IllegalArgumentException("Formato de documento não suportado: $fileName")
        }

        val temp = File(context.cacheDir, "kellvyn-document-" + System.nanoTime() + "." + kind)
        try {
            copyBounded(context, uri, temp)
            val extracted = when (kind) {
                "pdf" -> extractPdf(context, temp)
                "docx" -> extractXmlFromZip(temp, "word/document.xml")
                else -> extractXmlFromZip(temp, "content.xml")
            }.trim()
            require(extracted.isNotBlank()) {
                when (kind) {
                    "pdf" -> "Não consegui detectar texto neste PDF, mesmo após tentar OCR local nas páginas escaneadas. Verifique se o documento está legível ou protegido."
                    else -> "Não encontrei texto legível dentro deste documento."
                }
            }
            return extracted.take(MAX_EXTRACTED_CHARS)
        } finally {
            temp.delete()
        }
    }

    private fun copyBounded(context: Context, uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("O Android não conseguiu abrir o documento escolhido.")
        var total = 0L
        try {
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        require(total <= MAX_DOCUMENT_BYTES) {
                            "O documento ultrapassa o limite local de 20 MB."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    private fun extractPdf(context: Context, file: File): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { document ->
            require(document.numberOfPages > 0) { "O PDF não contém páginas." }
            val pagesToRead = minOf(document.numberOfPages, MAX_PDF_PAGES)
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val renderer = PDFRenderer(document)
            var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
            val output = StringBuilder()
            var ocrFailures = 0
            var ocrBudgetExhausted = false
            val ocrDeadlineNanos = System.nanoTime() +
                TimeUnit.SECONDS.toNanos(MAX_PDF_OCR_TOTAL_TIMEOUT_SECONDS)
            try {
                for (pageIndex in 0 until pagesToRead) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val nativeText = stripper.getText(document).trim()
                    val nativeCharacterCount = nativeText.count { it.isLetterOrDigit() }
                    var pageText = nativeText

                    // Searchable pages stay on the quick text path. Render only scanned pages
                    // (or pages whose text layer is effectively empty) to avoid needless OCR.
                    if (nativeCharacterCount < MIN_NATIVE_PAGE_TEXT_CHARS) {
                        if (System.nanoTime() >= ocrDeadlineNanos) ocrBudgetExhausted = true
                        if (!ocrBudgetExhausted) {
                            var bitmap: Bitmap? = null
                            try {
                            val box = document.getPage(pageIndex).cropBox
                            val longestEdge = maxOf(box.width, box.height).coerceAtLeast(1f)
                            val scale = (MAX_PDF_OCR_BITMAP_EDGE / longestEdge)
                                .coerceAtMost(MAX_PDF_OCR_SCALE)
                                .coerceAtLeast(0.25f)
                            bitmap = renderer.renderImage(pageIndex, scale, ImageType.RGB)
                            val activeRecognizer = recognizer
                                ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also {
                                    recognizer = it
                                }
                            val remainingNanos = ocrDeadlineNanos - System.nanoTime()
                            if (remainingNanos <= 0L) {
                                ocrBudgetExhausted = true
                                ocrFailures++
                            } else {
                                val remainingSeconds = TimeUnit.NANOSECONDS.toSeconds(remainingNanos)
                                    .coerceAtLeast(1L)
                                val timeoutSeconds = minOf(PDF_OCR_PAGE_TIMEOUT_SECONDS, remainingSeconds)
                                val recognizedText = Tasks.await(
                                    activeRecognizer.process(InputImage.fromBitmap(bitmap, 0)),
                                    timeoutSeconds,
                                    TimeUnit.SECONDS
                                ).text.trim()
                                if (recognizedText.isNotBlank()) {
                                    pageText = "[Página ${pageIndex + 1} — OCR local]\n$recognizedText"
                                }
                            }
                            } catch (_: Exception) {
                                ocrFailures++
                                if (System.nanoTime() >= ocrDeadlineNanos) ocrBudgetExhausted = true
                                // Preserve any small native text rather than losing the page on OCR failure.
                            } finally {
                                bitmap?.recycle()
                            }
                        }
                    }

                    if (pageText.isNotBlank()) {
                        if (output.isNotEmpty()) output.append("\n\n")
                        output.append(pageText)
                    }
                    if (output.length >= MAX_EXTRACTED_CHARS) break
                }
            } finally {
                try { recognizer?.close() } catch (_: Exception) { }
            }

            if (document.numberOfPages > MAX_PDF_PAGES) {
                output.append("\n\n[Leitura limitada às primeiras $MAX_PDF_PAGES páginas de ${document.numberOfPages}.]")
            }
            if (ocrFailures > 0 && output.isNotBlank()) {
                output.append("\n\n[Algumas páginas escaneadas não puderam ser reconhecidas por OCR local.]")
            }
            if (ocrBudgetExhausted && output.isNotBlank()) {
                output.append("\n\n[OCR local pausado ao atingir o limite de ${MAX_PDF_OCR_TOTAL_TIMEOUT_SECONDS} segundos; o restante do PDF foi lido apenas pela camada de texto.]")
            }
            return output.toString().take(MAX_EXTRACTED_CHARS)
        }
    }

    private fun extractXmlFromZip(file: File, entryName: String): String {
        var xmlBytes: ByteArray? = null
        var totalExpandedBytes = 0L
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == entryName) {
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        totalExpandedBytes += count
                        require(total <= MAX_XML_BYTES && totalExpandedBytes <= MAX_ZIP_EXPANDED_BYTES) {
                            "O conteúdo descompactado ultrapassou os limites seguros."
                        }
                        output.write(buffer, 0, count)
                    }
                    xmlBytes = output.toByteArray()
                    break
                } else {
                    // ZipInputStream.closeEntry() drains the entry. Drain it manually so a
                    // malicious unrelated entry cannot expand without a total-size bound.
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        totalExpandedBytes += count
                        require(totalExpandedBytes <= MAX_ZIP_EXPANDED_BYTES) {
                            "O documento compactado excede o limite seguro de conteúdo descompactado."
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        val bytes = xmlBytes ?: throw IllegalArgumentException("O documento não contém o arquivo de texto esperado: $entryName")
        val xmlText = String(bytes, Charsets.UTF_8)
        require(!xmlText.contains("<!doctype", ignoreCase = true) && !xmlText.contains("<!entity", ignoreCase = true)) {
            "O documento contém declarações XML externas que não são permitidas."
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            // Different Android XML parsers support different optional feature URIs.
            // The source is also checked above to reject DTD/entity declarations.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        val root = factory.newDocumentBuilder().parse(xmlText.byteInputStream()).documentElement
        val tags = if (entryName == "word/document.xml") setOf("p") else setOf("p", "h")
        val result = StringBuilder()
        appendParagraphs(root, tags, result)
        return result.toString().take(MAX_EXTRACTED_CHARS)
    }

    private fun appendParagraphs(node: Node, paragraphTags: Set<String>, output: StringBuilder) {
        if (output.length >= MAX_EXTRACTED_CHARS) return
        val local = (node.localName ?: node.nodeName.substringAfter(':')).lowercase()
        if (node.nodeType == Node.ELEMENT_NODE && local in paragraphTags) {
            appendTextOnly(node, output)
            if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
            return
        }
        val children: NodeList = node.childNodes
        for (index in 0 until children.length) {
            appendParagraphs(children.item(index), paragraphTags, output)
            if (output.length >= MAX_EXTRACTED_CHARS) break
        }
    }

    private fun appendTextOnly(node: Node, output: StringBuilder) {
        if (output.length >= MAX_EXTRACTED_CHARS) return
        if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            output.append(node.nodeValue.orEmpty())
            return
        }
        val children = node.childNodes
        for (index in 0 until children.length) {
            appendTextOnly(children.item(index), output)
            if (output.length >= MAX_EXTRACTED_CHARS) break
        }
    }
}
