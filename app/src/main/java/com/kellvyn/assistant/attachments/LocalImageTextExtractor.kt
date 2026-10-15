package com.kellvyn.assistant.attachments

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline bundled Latin OCR for user-selected images. It extracts visible text only; it is not
 * image captioning or general visual understanding, and selected images are never uploaded.
 */
object LocalImageTextExtractor {
    suspend fun extract(context: Context, uri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val closed = AtomicBoolean(false)
            fun closeOnce() {
                if (closed.compareAndSet(false, true)) runCatching { recognizer.close() }
            }
            continuation.invokeOnCancellation { closeOnce() }
            try {
                val image = InputImage.fromFilePath(context.applicationContext, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        closeOnce()
                        if (!continuation.isActive) return@addOnSuccessListener
                        val text = result.text.trim()
                        continuation.resume(
                            if (text.isBlank()) {
                                "Nenhum texto legível foi detectado pela leitura OCR local. A imagem não foi interpretada visualmente."
                            } else text.take(MAX_TEXT_CHARS)
                        )
                    }
                    .addOnFailureListener { error ->
                        closeOnce()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("O OCR local não conseguiu ler esta imagem: " + (error.message ?: error.javaClass.simpleName), error)
                            )
                        }
                    }
            } catch (error: Exception) {
                closeOnce()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    private const val MAX_TEXT_CHARS = 60_000
}
