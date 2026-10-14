package com.kellvyn.assistant.workspace

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Publishes a locally generated PNG to Pictures/Kellvyn using scoped storage. */
class ImageDownloadStore(context: Context) {
    private val appContext = context.applicationContext

    fun saveToPictures(image: File): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Salvamento direto em Imagens requer Android 10 ou superior."
        }
        require(image.isFile && image.length() > 0L) { "A imagem gerada não existe ou está vazia." }
        val resolver = appContext.contentResolver
        val targetName = image.name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, if (targetName.endsWith(".png", true)) targetName else "kellvyn-art.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kellvyn")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("O Android não criou o destino Pictures/Kellvyn.")
        try {
            val bytes = resolver.openOutputStream(uri, "w")?.use { output ->
                image.inputStream().buffered().use { it.copyTo(output) }
            } ?: throw IOException("O Android não abriu o arquivo da imagem.")
            if (bytes != image.length()) throw IOException("A cópia da imagem ficou incompleta.")
            if (resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) != 1
            ) throw IOException("O Android não publicou a imagem na galeria.")
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
