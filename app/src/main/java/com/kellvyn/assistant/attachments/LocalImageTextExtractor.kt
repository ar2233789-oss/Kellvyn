package com.kellvyn.assistant.attachments

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Entirely on-device image inspection: bundled ML Kit general image labels plus bundled Latin OCR.
 * Labels are coarse classifications rather than detailed visual descriptions; source images are
 * not uploaded. The result remains plain text because Kellvyn's language model is text-only.
 */
object LocalImageTextExtractor {
    private const val MAX_TEXT_CHARS = 60_000
    private const val MAX_LABELS = 8

    suspend fun extract(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context.applicationContext, uri)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var labelError: Exception? = null
        var textError: Exception? = null
        try {
            val labels = try {
                labeler.process(image).awaitResult()
            } catch (e: Exception) {
                labelError = e
                emptyList()
            }
            val detectedText = try {
                recognizer.process(image).awaitResult().text.trim()
            } catch (e: Exception) {
                textError = e
                ""
            }
            if (labels.isEmpty() && detectedText.isBlank()) {
                val detail = textError?.message ?: labelError?.message ?: "Nenhum rótulo visual ou texto foi detectado."
                throw IllegalStateException(
                    "A análise visual local não conseguiu processar esta imagem: $detail",
                    textError ?: labelError
                )
            }
            return buildString {
                appendLine("ROTULAGEM VISUAL LOCAL (categorias aproximadas, não uma descrição completa):")
                if (labels.isEmpty()) {
                    appendLine("Nenhuma categoria visual foi reconhecida.")
                } else {
                    labels.asSequence()
                        .filter { it.confidence >= 0.30f }
                        .sortedByDescending { it.confidence }
                        .take(MAX_LABELS)
                        .forEach { label ->
                            appendLine("- " + label.text + " (" + (label.confidence * 100f).toInt() + "%)")
                        }
                }
                appendLine()
                appendLine("TEXTO DETECTADO LOCALMENTE (OCR):")
                append(
                    if (detectedText.isBlank()) {
                        "Nenhum texto legível foi detectado."
                    } else detectedText.take(MAX_TEXT_CHARS)
                )
                if (labelError != null) appendLine("\nNota: a rotulagem visual falhou; somente OCR foi concluído.")
                if (textError != null) appendLine("\nNota: OCR falhou; somente a rotulagem visual foi concluída.")
            }.take(MAX_TEXT_CHARS)
        } finally {
            runCatching { labeler.close() }
            runCatching { recognizer.close() }
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("A análise local foi cancelada."))
                }
            }
        }
}
