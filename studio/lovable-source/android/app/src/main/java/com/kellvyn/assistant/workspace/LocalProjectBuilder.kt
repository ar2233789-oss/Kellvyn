package com.kellvyn.assistant.workspace

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Coordinates generated Java/Android source with the on-device compiler.
 * All project files remain app-private until the user explicitly asks for a build.
 */
internal class LocalProjectBuilder(context: Context) {
    private val app = context.applicationContext
    private val workspace = ProjectWorkspaceStore(app)
    private val compiler = LocalAndroidCompiler(app, workspace)
    private val preferences = app.getSharedPreferences("kellvyn_native", Context.MODE_PRIVATE)

    fun isNativeApkRequest(prompt: String): Boolean =
        GeneratedProjectOutputParser.isNativeApkRequest(prompt)

    fun addCompilerContract(prompt: String): String =
        if (isNativeApkRequest(prompt)) prompt + "\n\n" + GeneratedProjectOutputParser.nativeBuildInstruction() else prompt

    fun hasPendingLicenseAcceptance(prompt: String): Boolean =
        GeneratedProjectOutputParser.isExactSdkLicenseAcceptance(prompt) &&
            !preferences.getString(PENDING_PROJECT_ID, null).isNullOrBlank()

    suspend fun handleSdkLicenseAcceptance(
        prompt: String,
        onBeforeBuild: suspend () -> Unit,
        onProgress: (String) -> Unit
    ): String? {
        if (!GeneratedProjectOutputParser.isExactSdkLicenseAcceptance(prompt)) return null
        val projectId = preferences.getString(PENDING_PROJECT_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        compiler.acceptSdkLicense()
        return buildProject(projectId, onBeforeBuild, onProgress)
    }

    suspend fun compileGeneratedProject(
        prompt: String,
        response: String,
        onBeforeBuild: suspend () -> Unit,
        onProgress: (String) -> Unit
    ): String? {
        if (!isNativeApkRequest(prompt)) return null
        val files = try {
            GeneratedProjectOutputParser.parse(response)
        } catch (error: IllegalArgumentException) {
            return response + "\n\nNão compilei o APK: os arquivos gerados não passaram pela validação de caminhos/tamanho. " +
                (error.message ?: "Verifique a saída e tente novamente.")
        }
        if (files.isEmpty()) {
            return response + "\n\nNão compilei o APK: a resposta não trouxe blocos completos no formato 'Arquivo: caminho' com código cercado. " +
                "Peça a geração dos arquivos completos e tente novamente."
        }

        val fileMap = files.associate { it.path to it.content }
        val validation = GeneratedAndroidProjectValidator.validate(fileMap)
        if (validation != null) {
            return response + "\n\nNão compilei o APK. O projeto foi recusado pelo validador do compilador nativo Java: " +
                validation + "\nEste primeiro compilador aceita somente projetos Android Java com interface criada por código, sem Kotlin/Compose, " +
                "bibliotecas externas nem recursos adicionais."
        }

        val request = GeneratedProjectOutputParser.currentUserRequest(prompt)
        val projectName = extractProjectName(request)
        val project = try {
            workspace.createProject(
                name = projectName,
                description = "Projeto Android Java gerado pelo chat principal da Kellvyn IA.",
                files = files
            )
        } catch (error: Exception) {
            return response + "\n\nNão compilei o APK: não consegui guardar os arquivos no workspace privado. " +
                (error.message ?: error.javaClass.simpleName)
        }

        preferences.edit()
            .putString(PENDING_PROJECT_ID, project.id)
            .putString(PENDING_PROJECT_NAME, project.name)
            .apply()

        if (!compiler.hasAcceptedSdkLicense()) {
            return "O projeto Android foi validado estruturalmente e guardado no workspace privado (" +
                files.size + " arquivos). Ainda não baixei a plataforma de compilação nem criei um APK. " +
                "Para continuar, é necessário autorizar o download da plataforma Android (~64 MB) e aceitar a licença do SDK. " +
                "Leia os termos oficiais do Android SDK antes de aceitar. Se concordar, responda exatamente: " +
                "ACEITO A LICENÇA DO ANDROID SDK"
        }
        return buildProject(project.id, onBeforeBuild, onProgress)
    }

    private suspend fun buildProject(
        projectId: String,
        onBeforeBuild: suspend () -> Unit,
        onProgress: (String) -> Unit
    ): String {
        onBeforeBuild()
        return when (val result = compiler.build(projectId, onProgress)) {
            is LocalApkBuildResult.Success -> {
                val project = workspace.getProject(projectId)
                val destination = try {
                    publishToDownloads(result.apk, projectId)
                } catch (error: Exception) {
                    "o APK foi verificado, mas não consegui exportar para Downloads (" +
                        (error.message ?: error.javaClass.simpleName) + "). Cópia privada: " + result.apk.absolutePath
                }
                preferences.edit()
                    .remove(PENDING_PROJECT_ID)
                    .remove(PENDING_PROJECT_NAME)
                    .putString(LAST_BUILT_PROJECT_ID, projectId)
                    .putString(LAST_BUILT_APK_PATH, result.apk.absolutePath)
                    .apply()
                "APK Android nativo compilado localmente, assinado e verificado.\n" +
                    "Projeto: " + (project?.name ?: projectId) + "\n" +
                    "Pacote Android: " + result.applicationId + "\n" +
                    "Arquivo: " + destination + "\n" +
                    "Tempo de compilação: " + (result.durationMs / 1000.0) + " s.\n" +
                    "Escopo validado: Java nativo sem dependências externas. A verificação do APK e da assinatura passou; " +
                    "ainda não confirmei instalação nem execução no aparelho."
            }
            is LocalApkBuildResult.Failure ->
                "A compilação local falhou; não vou apresentar um APK como pronto.\n" +
                    result.reason.take(MAX_ERROR_CHARS) + "\nOs arquivos do projeto permanecem guardados para diagnóstico."
            LocalApkBuildResult.LicenseRequired ->
                "O compilador exige aceite explícito da licença do Android SDK antes de baixar a plataforma. " +
                    "Leia os termos oficiais; para continuar, responda: ACEITO A LICENÇA DO ANDROID SDK"
        }
    }

    private fun publishToDownloads(apk: File, projectId: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("a exportação gerenciada para Downloads exige Android 10 ou superior.")
        }
        if (!apk.isFile || apk.length() <= 0L) throw IOException("o APK de origem não existe ou está vazio.")
        val displayName = "Kellvyn-" + safeFileName(projectId) + "-" + System.currentTimeMillis() + ".apk"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kellvyn")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("o Android não permitiu criar o arquivo em Downloads/Kellvyn.")
        try {
            val output = resolver.openOutputStream(uri, "w")
                ?: throw IOException("não consegui abrir o arquivo de destino em Downloads.")
            output.use { destination ->
                apk.inputStream().buffered().use { source -> source.copyTo(destination, 64 * 1024) }
            }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) != 1) {
                throw IOException("o Android não confirmou a publicação do arquivo em Downloads.")
            }
            return "Downloads/Kellvyn/" + displayName
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun extractProjectName(request: String): String {
        val quoted = Regex("""(?iu)\b(?:chamado|chamada|nomeado|nomeada)\s+["'“]([^"'”]{2,48})["'”]""")
            .find(request)?.groupValues?.getOrNull(1)?.trim()
        val named = quoted?.takeIf { it.isNotBlank() }
        return (named ?: "App Android Kellvyn").take(48)
    }

    private fun safeFileName(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9-]+"), "-").trim('-').take(48).ifBlank { "app" }

    companion object {
        private const val PENDING_PROJECT_ID = "local_builder_pending_project_id"
        private const val PENDING_PROJECT_NAME = "local_builder_pending_project_name"
        private const val LAST_BUILT_PROJECT_ID = "local_builder_last_project_id"
        private const val LAST_BUILT_APK_PATH = "local_builder_last_apk_path"
        private const val MAX_ERROR_CHARS = 6000
    }
}
