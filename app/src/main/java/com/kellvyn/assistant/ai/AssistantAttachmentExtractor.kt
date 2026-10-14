package com.kellvyn.assistant.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractedAttachment(
    val displayName: String,
    val mimeType: String,
    val extractedText: String,
    val method: String,
    val notice: String = ""
)

/**
 * Reads user-selected documents locally. PDFs use PDFBox text extraction; images use bundled
 * on-device OCR. OCR is not general visual understanding and must never be described as such.
 */
class AssistantAttachmentExtractor(context: Context) {
    private val appContext = context.applicationContext

    suspend fun extract(uri: Uri, displayName: String, declaredMime: String?): ExtractedAttachment =
        withContext(Dispatchers.IO) {
            val mime = declaredMime
                ?: appContext.contentResolver.getType(uri)
                ?: guessMime(displayName)
            when {
                mime.equals("application/pdf", ignoreCase = true) ||
                    displayName.endsWith(".pdf", ignoreCase = true) -> extractPdf(uri, displayName, mime)
                mime.startsWith("image/", ignoreCase = true) ||
                    listOf(".png", ".jpg", ".jpeg", ".webp", ".bmp").any { displayName.endsWith(it, true) } ->
                    extractImageText(uri, displayName, mime)
                mime.startsWith("text/", ignoreCase = true) ||
                    listOf(".txt", ".md", ".json", ".csv", ".xml", ".log").any { displayName.endsWith(it, true) } ->
                    extractPlainText(uri, displayName, mime)
                else -> throw IllegalArgumentException("Formato não suportado. Selecione PDF, imagem ou arquivo de texto.")
            }
        }

    private fun extractPdf(uri: Uri, displayName: String, mime: String): ExtractedAttachment {
        PDFBoxResourceLoader.init(appContext)
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não consegui abrir o PDF selecionado.")
        stream.use { input ->
            PDDocument.load(input).use { document ->
                if (document.numberOfPages == 0) {
                    throw IllegalArgumentException("O PDF não contém páginas.")
                }
                val pagesToRead = document.numberOfPages.coerceAtMost(MAX_PDF_PAGES)
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = pagesToRead
                    sortByPosition = true
                }
                val text = stripper.getText(document).trim().take(MAX_EXTRACTED_CHARS)
                if (text.isBlank()) {
                    throw IllegalArgumentException(
                        "Não encontrei texto selecionável no PDF. Ele pode ser uma digitalização; neste build a análise de PDF depende de texto extraível."
                    )
                }
                val notice = if (document.numberOfPages > pagesToRead || stripper.getText(document).length > MAX_EXTRACTED_CHARS) {
                    "O documento foi limitado a $pagesToRead páginas e $MAX_EXTRACTED_CHARS caracteres por segurança."
                } else ""
                return ExtractedAttachment(
                    displayName = displayName,
                    mimeType = mime,
                    extractedText = text,
                    method = "extração local de texto PDF",
                    notice = notice
                )
            }
        }
    }

    private suspend fun extractImageText(uri: Uri, displayName: String, mime: String): ExtractedAttachment {
        val image = InputImage.fromFilePath(appContext, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
        ExtractedAttachment(
            displayName = displayName,
            mimeType = mime,
            extractedText = text.trim().take(MAX_EXTRACTED_CHARS),
            method = "OCR local",
            notice = if (text.isBlank()) {
                "O OCR não encontrou texto legível. Esta versão não possui um modelo multimodal para compreender o conteúdo visual da foto."
            } else {
                "Extraí apenas texto legível por OCR; isso não equivale a compreender todos os objetos ou detalhes visuais da imagem."
            }
        )
    }

    private fun extractPlainText(uri: Uri, displayName: String, mime: String): ExtractedAttachment {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não consegui abrir o arquivo selecionado.")
        val text = stream.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4096)
                while (output.length < MAX_EXTRACTED_CHARS) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, MAX_EXTRACTED_CHARS - output.length))
                    if (count < 0) break
                    output.append(buffer, 0, count)
                }
                output.toString()
            }
        }.trim()
        if (text.isBlank()) throw IllegalArgumentException("O arquivo está vazio.")
        return ExtractedAttachment(displayName, mime, text, "leitura local de texto")
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".json", true) -> "application/json"
        name.endsWith(".md", true) -> "text/markdown"
        name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".csv", true) -> "text/csv"
        name.endsWith(".xml", true) -> "application/xml"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_PDF_PAGES = 60
        const val MAX_EXTRACTED_CHARS = 12_000
    }
}
