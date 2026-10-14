package com.kellvyn.assistant.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

enum class ApkRiskLevel { LOW, REVIEW, HIGH }

data class ApkAnalysisReport(
    val fileName: String,
    val sha256: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val requestedPermissions: List<String>,
    val exportedComponents: List<String>,
    val nativeLibraries: List<String>,
    val suspiciousIndicators: List<String>,
    val riskLevel: ApkRiskLevel,
    val summary: String
) {
    fun asPortugueseText(): String = buildString {
        appendLine("Análise local de APK: $fileName")
        appendLine("Pacote: " + packageName.ifBlank { "não identificado" })
        appendLine("Versão: $versionName ($versionCode)")
        appendLine("SHA-256: $sha256")
        appendLine("Nível heurístico: " + when (riskLevel) {
            ApkRiskLevel.LOW -> "baixo sinal de risco detectado"
            ApkRiskLevel.REVIEW -> "revisão recomendada"
            ApkRiskLevel.HIGH -> "sinais de risco elevado"
        })
        appendLine()
        appendLine(summary)
        appendLine()
        appendLine("Permissões solicitadas:")
        if (requestedPermissions.isEmpty()) appendLine("- nenhuma extraída")
        else requestedPermissions.forEach { appendLine("- " + it) }
        appendLine()
        appendLine("Componentes exportados:")
        if (exportedComponents.isEmpty()) appendLine("- nenhum detectado")
        else exportedComponents.forEach { appendLine("- " + it) }
        if (nativeLibraries.isNotEmpty()) {
            appendLine()
            appendLine("Bibliotecas nativas:")
            nativeLibraries.forEach { appendLine("- " + it) }
        }
        if (suspiciousIndicators.isNotEmpty()) {
            appendLine()
            appendLine("Indicadores para revisão:")
            suspiciousIndicators.forEach { appendLine("- " + it) }
        }
        appendLine()
        appendLine("Importante: inspeção estática local, não antivírus com assinatura em nuvem. Um resultado baixo não garante que o APK seja seguro; comportamento durante a execução e código ofuscado podem escapar.")
    }
}

/** Static APK triage performed locally. The report never claims that a package is malware-free. */
class ApkSafetyAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    fun analyze(uri: Uri, displayName: String): ApkAnalysisReport {
        val staged = File(appContext.cacheDir, "apk-analysis-" + System.nanoTime() + ".apk")
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Não consegui abrir o APK selecionado.")
            input.use { stream -> staged.outputStream().buffered().use { out -> stream.copyTo(out) } }
            return analyze(staged, displayName)
        } finally {
            staged.delete()
        }
    }

    fun analyze(file: File, displayName: String = file.name): ApkAnalysisReport {
        require(file.isFile && file.length() > 0L) { "O arquivo APK está vazio ou não existe." }
        require(file.length() <= MAX_APK_BYTES) { "O APK excede o limite de análise local." }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val info = packageInfo(file)
        val pkg = info?.packageName.orEmpty()
        val versionName = info?.versionName ?: "desconhecida"
        val versionCode = info?.let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L
        val permissions = info?.requestedPermissions.orEmpty()
            .map { it.substringAfterLast('.') }
            .distinct()
            .sorted()
        val exported = buildList {
            info?.activities.orEmpty().filter { it.exported }.forEach { add("Activity: " + it.name) }
            info?.services.orEmpty().filter { it.exported }.forEach { add("Service: " + it.name) }
            info?.receivers.orEmpty().filter { it.exported }.forEach { add("Receiver: " + it.name) }
            info?.providers.orEmpty().filter { it.exported }.forEach { add("Provider: " + it.authority) }
        }.distinct().take(MAX_REPORTED_COMPONENTS)
        val nativeLibraries = mutableListOf<String>()
        val indicators = mutableListOf<String>()

        ZipFile(file).use { zip ->
            if (zip.getEntry("AndroidManifest.xml") == null) {
                indicators += "AndroidManifest.xml ausente: arquivo não parece um APK válido."
            }
            val dexEntries = zip.entries().asSequence().filter {
                it.name.matches(Regex("classes(\\d*)\\.dex"))
            }.count()
            if (dexEntries == 0) indicators += "Nenhum arquivo DEX encontrado."
            if (dexEntries > 5) indicators += "Muitos arquivos DEX ($dexEntries); revisar estrutura."
            zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .take(MAX_REPORTED_COMPONENTS).forEach { nativeLibraries += it.name }
            zip.entries().asSequence().filter {
                it.name.endsWith(".js", true) || it.name.endsWith(".sh", true)
            }.take(MAX_ARCHIVE_ENTRIES_INSPECTED).forEach { entry ->
                if (entry.isDirectory || entry.size <= 0L || entry.size > MAX_ENTRY_SCAN_BYTES) return@forEach
                val text = runCatching {
                    zip.getInputStream(entry).bufferedReader().use { it.readText().take(MAX_ENTRY_SCAN_BYTES.toInt()) }
                }.getOrDefault("")
                if (Regex("(?i)(curl|wget)\\s+https?://|Runtime\\.getRuntime\\(\\)\\.exec|su\\s+-c|DexClassLoader").containsMatchIn(text)) {
                    indicators += "Script contém padrão de execução dinâmica ou comando de sistema: ${entry.name}."
                }
            }
        }

        val riskyPermissions = permissions.filter { it in HIGH_RISK_PERMISSIONS }
        if (riskyPermissions.isNotEmpty()) indicators += "Permissões sensíveis: " + riskyPermissions.joinToString()
        if (permissions.contains("REQUEST_INSTALL_PACKAGES")) indicators += "Permissão para pedir instalação de outros apps."
        if (permissions.contains("SYSTEM_ALERT_WINDOW")) indicators += "Permissão para sobrepor outras aplicações."
        if (exported.size > 20) indicators += "Mais de 20 componentes exportados; revise a superfície de entrada."
        if (pkg.isBlank()) indicators += "PackageManager não reconheceu o pacote como APK instalável."

        val risk = when {
            indicators.any { it.contains("não parece um APK válido") || it.contains("Nenhum arquivo DEX") } ||
                riskyPermissions.size >= 3 ||
                indicators.any { it.contains("padrão de execução dinâmica") } -> ApkRiskLevel.HIGH
            indicators.isNotEmpty() || riskyPermissions.isNotEmpty() || exported.size > 10 -> ApkRiskLevel.REVIEW
            else -> ApkRiskLevel.LOW
        }
        val summary = when (risk) {
            ApkRiskLevel.LOW -> "A inspeção não encontrou um indicador forte nas verificações implementadas. Isso não prova que o arquivo está livre de malware."
            ApkRiskLevel.REVIEW -> "Encontrei permissões ou características que merecem revisão antes de instalar. Confira se fazem sentido para a função do app."
            ApkRiskLevel.HIGH -> "Encontrei sinais de risco ou inconsistência. Não recomendo instalar sem investigação adicional."
        }
        return ApkAnalysisReport(
            fileName = displayName.take(160),
            sha256 = sha256,
            packageName = pkg,
            versionName = versionName,
            versionCode = versionCode,
            requestedPermissions = permissions,
            exportedComponents = exported,
            nativeLibraries = nativeLibraries,
            suspiciousIndicators = indicators.distinct().take(MAX_REPORTED_COMPONENTS),
            riskLevel = risk,
            summary = summary
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(file: File): PackageInfo? {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        val parsed = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            appContext.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        } ?: return null
        parsed.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        return parsed
    }

    private companion object {
        const val MAX_APK_BYTES = 1_610_612_736L
        const val MAX_ENTRY_SCAN_BYTES = 512 * 1024L
        const val MAX_ARCHIVE_ENTRIES_INSPECTED = 250
        const val MAX_REPORTED_COMPONENTS = 100
        val HIGH_RISK_PERMISSIONS = setOf(
            "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "READ_CALL_LOG", "WRITE_CALL_LOG",
            "PROCESS_OUTGOING_CALLS", "READ_CONTACTS", "WRITE_CONTACTS",
            "READ_PHONE_NUMBERS", "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION",
            "ACCESS_BACKGROUND_LOCATION", "QUERY_ALL_PACKAGES"
        )
    }
}
