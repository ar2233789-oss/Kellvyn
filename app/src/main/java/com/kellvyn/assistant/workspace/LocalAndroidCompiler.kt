package com.kellvyn.assistant.workspace

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

sealed interface LocalApkBuildResult {
    data class Success(val apk: File, val applicationId: String, val durationMs: Long) : LocalApkBuildResult
    data class Failure(val reason: String) : LocalApkBuildResult
    data object LicenseRequired : LocalApkBuildResult
}

/**
 * Dependency-free Java Android build path: ECJ -> D8 -> binary manifest/package -> apksig.
 * No shell, Gradle daemon, remote builder or generated-script execution is used.
 */
class LocalAndroidCompiler(context: Context, private val workspace: ProjectWorkspaceStore) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("kellvyn_local_builder", Context.MODE_PRIVATE)
    private val platformDir = File(appContext.filesDir, "kellvyn_toolchain/android-35")
    private val androidJar = File(platformDir, "android.jar")
    private val buildRoot = File(appContext.filesDir, "kellvyn_builds")

    fun hasAcceptedSdkLicense(): Boolean = preferences.getBoolean(PREF_SDK_LICENSE_ACCEPTED, false)
    fun acceptSdkLicense() { preferences.edit().putBoolean(PREF_SDK_LICENSE_ACCEPTED, true).apply() }

    fun mostRecentApk(): File? = buildRoot.listFiles()
        ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }

    suspend fun build(projectId: String, onProgress: (String) -> Unit = {}): LocalApkBuildResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.nanoTime()
            try {
                if (Build.VERSION.SDK_INT < 26) {
                    return@withContext LocalApkBuildResult.Failure("O compilador local precisa do Android 8.0 (API 26) ou superior.")
                }
                report(onProgress, "Inspecionando o projeto antes de preparar a toolchain…")
                val projectDir = workspace.projectDirectory(projectId)
                val javaRoot = File(projectDir, "app/src/main/java")
                val sources = if (javaRoot.isDirectory) {
                    javaRoot.walkTopDown().filter { it.isFile && it.extension.equals("java", true) }
                        .take(MAX_SOURCE_FILES + 1).toList()
                } else emptyList()
                if (sources.isEmpty()) {
                    return@withContext LocalApkBuildResult.Failure(
                        "Este projeto não contém fontes Java em app/src/main/java. O construtor deve gerar Java Android sem bibliotecas externas para este motor local."
                    )
                }
                if (sources.size > MAX_SOURCE_FILES) {
                    return@withContext LocalApkBuildResult.Failure("A build local aceita até " + MAX_SOURCE_FILES + " fontes Java por projeto.")
                }
                val mainActivity = sources.firstOrNull {
                    it.name == "MainActivity.java" && Regex("""\bclass\s+MainActivity\b""").containsMatchIn(it.readText())
                } ?: return@withContext LocalApkBuildResult.Failure("Não encontrei uma classe MainActivity em app/src/main/java.")
                val mainText = mainActivity.readText()
                val packageName = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;""")
                    .find(mainText)?.groupValues?.getOrNull(1)
                    ?: return@withContext LocalApkBuildResult.Failure("MainActivity.java precisa declarar o pacote Java.")
                require(isValidPackage(packageName)) { "O pacote Java de MainActivity é inválido." }
                val gradleText = File(projectDir, "app/build.gradle.kts").takeIf { it.isFile }?.readText().orEmpty()
                val applicationId = Regex("""applicationId\s*=\s*["']([^"']+)["']""")
                    .find(gradleText)?.groupValues?.getOrNull(1)?.takeIf(::isValidPackage) ?: packageName
                val activityName = packageName + ".MainActivity"
                val declaredManifest = File(projectDir, "app/src/main/AndroidManifest.xml")
                    .takeIf { it.isFile }?.readText().orEmpty()
                val requestedPermissions = Regex(
                    """(?is)<uses-permission\b[^>]*android:name\s*=\s*["']([^"']+)["'][^>]*>"""
                ).findAll(declaredManifest).map { it.groupValues[1].trim() }.distinct().toList()

                // Validate source structure before requesting license or downloading the Android SDK.
                // Invalid projects should fail quickly without consuming data or storage.
                if (!hasAcceptedSdkLicense()) return@withContext LocalApkBuildResult.LicenseRequired
                report(onProgress, "Preparando as ferramentas locais…")
                val platform = ensureAndroidPlatform(onProgress)

                val jobDir = File(buildRoot, projectId).apply {
                    if (exists()) deleteRecursively()
                    check(mkdirs()) { "Não foi possível preparar a pasta da build." }
                }
                val classesDir = File(jobDir, "classes").apply { mkdirs() }
                val dexDir = File(jobDir, "dex").apply { mkdirs() }
                report(onProgress, "Compilando " + sources.size + " arquivo(s) Java no aparelho…")
                val stdout = StringWriter()
                val stderr = StringWriter()
                val ecjArgs = buildList {
                    add("-source"); add("1.8")
                    add("-target"); add("1.8")
                    add("-proc:none")
                    add("-g")
                    add("-Xemacs")
                    // The Android SDK stubs are the Java/Android boot API, not just an optional dependency.
                    add("-bootclasspath"); add(platform.absolutePath)
                    add("-classpath"); add(platform.absolutePath)
                    add("-d"); add(classesDir.absolutePath)
                    addAll(sources.map { it.absolutePath })
                }
                val compiled = BatchCompiler.compile(ecjArgs.toTypedArray(), PrintWriter(stdout), PrintWriter(stderr), null)
                val diagnostics = (stderr.toString() + "\n" + stdout.toString()).trim()
                if (!compiled || classesDir.walkTopDown().none { it.isFile && it.extension == "class" }) {
                    val detail = diagnostics.ifBlank { "O compilador não produziu classes." }
                    return@withContext LocalApkBuildResult.Failure(("Erro real da compilação Java:\n" + detail).take(MAX_LOG_CHARS))
                }
                report(onProgress, "Convertendo classes para DEX…")
                val classes = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }
                    .map { it.toPath() }.toList()
                D8.run(
                    D8Command.builder()
                        .addProgramFiles(classes)
                        .addLibraryFiles(platform.toPath())
                        .setMinApiLevel(MIN_SDK)
                        .setMode(CompilationMode.DEBUG)
                        .setOutput(dexDir.toPath(), OutputMode.DexIndexed)
                        .build()
                )
                val dexFiles = dexDir.listFiles()
                    ?.filter { it.isFile && Regex("""classes\d*\.dex""").matches(it.name) }
                    ?.sortedBy { if (it.name == "classes.dex") 1 else it.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1 }
                    .orEmpty()
                if (dexFiles.isEmpty() || dexFiles.none { it.name == "classes.dex" }) {
                    return@withContext LocalApkBuildResult.Failure("O D8 não gerou classes.dex.")
                }
                report(onProgress, "Montando APK e manifesto Android…")
                val appLabel = workspace.getProject(projectId)?.name
                    ?.trim()?.take(100)?.ifBlank { APP_LABEL } ?: APP_LABEL
                val unsigned = File(jobDir, "app-unsigned.apk")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(unsigned))).use { zip ->
                    writeEntry(
                        zip,
                        "AndroidManifest.xml",
                        BinaryAndroidManifest.create(applicationId, activityName, appLabel, MIN_SDK, TARGET_SDK, requestedPermissions)
                    )
                    dexFiles.forEach { dex ->
                        zip.putNextEntry(ZipEntry(dex.name))
                        dex.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                report(onProgress, "Assinando e verificando o APK…")
                val output = File(buildRoot, projectId + ".apk")
                val signedTemp = File(buildRoot, projectId + ".apk.tmp")
                val previous = File(buildRoot, projectId + ".apk.previous")
                signedTemp.delete()
                previous.delete()
                signApk(unsigned, signedTemp)
                val verification = ApkVerifier.Builder(signedTemp).build().verify()
                if (!verification.isVerified) {
                    signedTemp.delete()
                    val details = verification.errors.take(8).joinToString("; ")
                    return@withContext LocalApkBuildResult.Failure(("A verificação da assinatura falhou: " + details).take(MAX_LOG_CHARS))
                }
                if (output.exists()) {
                    check(output.renameTo(previous)) { "O APK anterior não pôde ser preservado antes de publicar a nova versão." }
                }
                if (!signedTemp.renameTo(output)) {
                    if (previous.exists()) previous.renameTo(output)
                    signedTemp.delete()
                    error("O APK foi assinado, mas não pôde ser publicado no workspace privado.")
                }
                previous.delete()
                LocalApkBuildResult.Success(output, applicationId, (System.nanoTime() - startedAt) / 1_000_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val details = StringWriter().also { writer ->
                    e.printStackTrace(PrintWriter(writer))
                }.toString()
                val message = e.message ?: e.javaClass.simpleName
                LocalApkBuildResult.Failure(("Build local falhou: " + message + "\n" + details).take(MAX_LOG_CHARS))
            }
        }

    private suspend fun ensureAndroidPlatform(onProgress: (String) -> Unit): File {
        if (isUsableAndroidJar()) return androidJar
        if (!platformDir.exists() && !platformDir.mkdirs()) {
            throw IllegalStateException("Não foi possível preparar a pasta privada da plataforma Android.")
        }

        val archive = File(platformDir, "platform-35.zip")
        val partial = File(platformDir, "platform-35.zip.part")
        val candidateJar = File(platformDir, "android.jar.part")
        val previousJar = File(platformDir, "android.jar.previous")
        File(platformDir, "platform-36.zip.part").delete()
        partial.delete()
        candidateJar.delete()

        // Reuse a complete checksum-verified archive after extraction failures instead of
        // throwing it away and downloading another 64 MB on every retry.
        if (archive.exists() && !isValidPlatformArchive(archive)) archive.delete()
        if (!archive.isFile) {
            report(onProgress, "Preparando a plataforma Android para a compilação local…")
            val connection = (URL(PLATFORM_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "Kellvyn-IA-Local-Builder")
            }
            try {
                connection.connect()
                val code = connection.responseCode
                require(code in 200..299) {
                    "O servidor do Android SDK não aceitou o download (HTTP " + code + ")."
                }
                val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
                require(contentLength < 0L || contentLength == PLATFORM_ARCHIVE_BYTES) {
                    "O tamanho informado para a plataforma Android não corresponde à revisão esperada."
                }
                val digest = MessageDigest.getInstance("SHA-1")
                var total = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partial).use { raw ->
                        val output = BufferedOutputStream(raw)
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            require(total <= PLATFORM_ARCHIVE_BYTES) {
                                "O arquivo do SDK excedeu o tamanho esperado."
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            if (total % (2L * 1024 * 1024) < count) {
                                val percent = ((total * 100) / PLATFORM_ARCHIVE_BYTES).coerceIn(0, 100)
                                report(onProgress, "Preparando a plataforma Android: " + percent + "%")
                            }
                        }
                        output.flush()
                        raw.fd.sync()
                    }
                }
                require(total == PLATFORM_ARCHIVE_BYTES) {
                    "Download incompleto: " + total + " de " + PLATFORM_ARCHIVE_BYTES + " bytes."
                }
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                require(actualSha.equals(PLATFORM_ARCHIVE_SHA1, ignoreCase = true)) {
                    "A verificação de integridade da plataforma Android falhou. O arquivo não será usado."
                }
                check(partial.renameTo(archive)) {
                    "Não foi possível finalizar o arquivo da plataforma Android."
                }
            } finally {
                connection.disconnect()
                if (partial.exists()) partial.delete()
            }
        }

        check(isValidPlatformArchive(archive)) {
            "O arquivo local da plataforma Android não passou pela verificação de integridade."
        }
        report(onProgress, "Extraindo e verificando android.jar…")
        try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry(PLATFORM_JAR_ENTRY)
                    ?: error("A plataforma baixada não contém android.jar.")
                require(entry.size in (MIN_ANDROID_JAR_BYTES + 1)..MAX_ANDROID_JAR_BYTES) {
                    "O tamanho de android.jar é inválido para a plataforma Android."
                }
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(candidateJar).use { raw ->
                        val output = BufferedOutputStream(raw)
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            total += count
                            require(total <= entry.size) { "android.jar ultrapassou o tamanho declarado no arquivo do SDK." }
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                        raw.fd.sync()
                        require(total == entry.size) { "O tamanho extraído de android.jar não confere." }
                    }
                }
            }
            require(isUsableAndroidJarFile(candidateJar)) {
                "android.jar foi extraído, mas não passou pela verificação estrutural."
            }
            previousJar.delete()
            if (androidJar.exists()) {
                check(androidJar.renameTo(previousJar)) {
                    "Não foi possível preservar a plataforma anterior antes da troca."
                }
            }
            if (!candidateJar.renameTo(androidJar)) {
                if (previousJar.exists()) previousJar.renameTo(androidJar)
                error("Não foi possível instalar android.jar no armazenamento privado.")
            }
            if (!isUsableAndroidJar()) {
                androidJar.delete()
                if (previousJar.exists()) previousJar.renameTo(androidJar)
                error("A plataforma instalada falhou na verificação final.")
            }
            previousJar.delete()
            archive.delete()
            return androidJar
        } catch (e: Exception) {
            candidateJar.delete()
            if (!isUsableAndroidJar() && previousJar.exists() && !androidJar.exists()) {
                previousJar.renameTo(androidJar)
            }
            throw e
        }
    }

    private fun isValidPlatformArchive(file: File): Boolean {
        if (!file.isFile || file.length() != PLATFORM_ARCHIVE_BYTES) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
                .equals(PLATFORM_ARCHIVE_SHA1, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun isUsableAndroidJar(): Boolean = isUsableAndroidJarFile(androidJar)

    private fun isUsableAndroidJarFile(file: File): Boolean {
        if (!file.isFile || file.length() <= MIN_ANDROID_JAR_BYTES || file.length() > MAX_ANDROID_JAR_BYTES) return false
        return runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry("android/app/Activity.class") != null &&
                    zip.getEntry("java/lang/Object.class") != null
            }
        }.getOrDefault(false)
    }

    private suspend fun report(callback: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main) { callback(message) }
    }

    private fun signApk(unsigned: File, output: File) {
        val alias = SIGNING_ALIAS
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val before = Calendar.getInstance()
            val after = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=Kellvyn IA Debug"))
                        .setCertificateSerialNumber(java.math.BigInteger.ONE)
                        .setCertificateNotBefore(before.time)
                        .setCertificateNotAfter(after.time)
                        .build()
                )
                generateKeyPair()
            }
        }
        val reloaded = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = reloaded.getKey(alias, null) as java.security.PrivateKey
        val certificate = reloaded.getCertificate(alias) as X509Certificate
        val config = ApkSigner.SignerConfig.Builder("kellvyn-debug", KeyConfig.Jca(privateKey), listOf(certificate)).build()
        output.parentFile?.mkdirs()
        ApkSigner.Builder(listOf(config))
            .setInputApk(unsigned)
            .setOutputApk(output)
            .setMinSdkVersion(MIN_SDK)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
    }

    private fun isValidPackage(value: String): Boolean =
        value.length <= 240 && value.split('.').size >= 2 &&
            value.split('.').all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }

    private companion object {
        const val PREF_SDK_LICENSE_ACCEPTED = "android_sdk_license_accepted"
        const val SIGNING_ALIAS = "kellvyn-generated-debug-key"
        const val APP_LABEL = "Aplicativo criado pelo Kellvyn"
        const val MIN_SDK = 26
        const val TARGET_SDK = 35
        const val MAX_SOURCE_FILES = 80
        const val MAX_LOG_CHARS = 16_000
        const val MIN_ANDROID_JAR_BYTES = 20_000_000L
        const val PLATFORM_URL = "https://dl.google.com/android/repository/platform-35_r02.zip"
        const val PLATFORM_ARCHIVE_SHA1 = "0bb560a90a7a2cbd0dd8348224d518b638fe7949"
        const val PLATFORM_ARCHIVE_BYTES = 64_273_788L
        const val PLATFORM_JAR_ENTRY = "android-35/android.jar"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
