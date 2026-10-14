package com.kellvyn.assistant.workspace

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Publishes a verified local APK into Downloads/Kellvyn without requesting broad storage access.
 * On Android 10+, MediaStore owns the public Downloads entry and handles scoped storage.
 */
class ApkDownloadStore(context: Context) {
    private val appContext = context.applicationContext

    fun saveToDownloads(apk: File): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "O salvamento direto em Downloads requer Android 10 ou superior."
        }
        require(apk.isFile && apk.length() > 0L) { "O APK compilado não existe ou está vazio." }
        val resolver = appContext.contentResolver
        val metadata = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeApkName(apk.name))
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Kellvyn"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, metadata)
            ?: throw IOException("O Android não criou o arquivo em Downloads/Kellvyn.")
        try {
            val written = resolver.openOutputStream(destination, "w")?.use { output ->
                apk.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: throw IOException("O Android não abriu o destino do APK.")
            if (written != apk.length()) {
                throw IOException("Download incompleto: $written de ${apk.length()} bytes.")
            }
            val published = resolver.update(
                destination,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            if (published != 1) throw IOException("O Android não publicou o APK em Downloads.")
            return destination
        } catch (error: Exception) {
            runCatching { resolver.delete(destination, null, null) }
            throw error
        }
    }

    private fun safeApkName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
        return if (cleaned.endsWith(".apk", ignoreCase = true)) cleaned else "kellvyn-app.apk"
    }
}
