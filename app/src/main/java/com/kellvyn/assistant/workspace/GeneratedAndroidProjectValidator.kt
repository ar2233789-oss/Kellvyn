package com.kellvyn.assistant.workspace

/**
 * Static sanity checks for a generated Android/Compose project.
 *
 * Passing these checks is not a successful build and never implies an APK exists.
 * A real local Android compilation is still required to prove buildability.
 */
internal object GeneratedAndroidProjectValidator {
    fun validate(files: Map<String, String>): String? {
        fun content(path: String) = files[path.replace('\\', '/')].orEmpty()

        val rootBuild = content("build.gradle.kts")
        val settings = content("settings.gradle.kts")
        val properties = content("gradle.properties")
        val appBuild = content("app/build.gradle.kts")
        val manifest = content("app/src/main/AndroidManifest.xml")
        val activity = content("app/src/main/java/com/example/app/MainActivity.kt")
        val readme = content("README.md")

        val problems = mutableListOf<String>()
        if (!Regex("""include\s*\(\s*["']:app["']\s*\)""").containsMatchIn(settings)) {
            problems += "settings.gradle.kts precisa incluir o módulo :app"
        }

        fun rootPluginVersion(pluginId: String): String? {
            val pattern = """id\(\s*["']${Regex.escape(pluginId)}["']\s*\)\s*version\s*["']([^"']+)["']"""
            return Regex(pattern).find(rootBuild)?.groupValues?.getOrNull(1)
        }
        if (rootPluginVersion("com.android.application") != AndroidBuildVersions.ANDROID_GRADLE_PLUGIN ||
            rootPluginVersion("org.jetbrains.kotlin.android") != AndroidBuildVersions.KOTLIN ||
            rootPluginVersion("org.jetbrains.kotlin.plugin.compose") != AndroidBuildVersions.KOTLIN
        ) {
            problems += "build.gradle.kts precisa usar AGP ${AndroidBuildVersions.ANDROID_GRADLE_PLUGIN} e Kotlin/Compose ${AndroidBuildVersions.KOTLIN} com versões compatíveis e iguais entre si"
        }

        val namespace = Regex("""namespace\s*=\s*["']([^"']+)["']""")
            .find(appBuild)?.groupValues?.getOrNull(1)
        val applicationId = Regex("""applicationId\s*=\s*["']([^"']+)["']""")
            .find(appBuild)?.groupValues?.getOrNull(1)
        if (namespace != "com.example.app") {
            problems += "namespace do app precisa coincidir com com.example.app"
        }
        if (applicationId != "com.example.app") {
            problems += "applicationId precisa ser com.example.app"
        }
        if (!Regex("""compileSdk\s*=\s*${AndroidBuildVersions.COMPILE_SDK}\b""").containsMatchIn(appBuild) ||
            !Regex("""targetSdk\s*=\s*${AndroidBuildVersions.TARGET_SDK}\b""").containsMatchIn(appBuild) ||
            !Regex("""minSdk\s*=\s*${AndroidBuildVersions.MIN_SDK}\b""").containsMatchIn(appBuild)
        ) {
            problems += "app/build.gradle.kts precisa usar compileSdk/targetSdk ${AndroidBuildVersions.COMPILE_SDK} e minSdk ${AndroidBuildVersions.MIN_SDK}"
        }
        if ("compose-bom:${AndroidBuildVersions.COMPOSE_BOM}" !in appBuild ||
            "activity-compose:${AndroidBuildVersions.ACTIVITY_COMPOSE}" !in appBuild ||
            "org.jetbrains.kotlin.plugin.compose" !in appBuild
        ) {
            problems += "faltam dependências/plugins básicos de Jetpack Compose"
        }
        if (!appBuild.contains("sourceCompatibility = JavaVersion.VERSION_17") ||
            !appBuild.contains("targetCompatibility = JavaVersion.VERSION_17") ||
            !appBuild.contains("JvmTarget.JVM_17")
        ) {
            problems += "o projeto precisa compilar Java e Kotlin com alvo JVM 17"
        }
        if (!properties.contains("org.gradle.parallel=false") ||
            !Regex("""org\.gradle\.workers\.max\s*=\s*2""").containsMatchIn(properties)
        ) {
            problems += "gradle.properties precisa manter a configuração econômica para build no celular"
        }
        val mainActivityTag = Regex(
            """(?is)<activity\b(?=[^>]*android:name\s*=\s*["'](?:\.MainActivity|com\.example\.app\.MainActivity)["'])[^>]*>"""
        ).find(manifest)?.value.orEmpty()
        val hasLauncherIntent = "android.intent.action.MAIN" in manifest &&
            "android.intent.category.LAUNCHER" in manifest
        if (!manifest.contains("<manifest") || !manifest.contains("<application") ||
            mainActivityTag.isBlank() ||
            !Regex("""android:exported\s*=\s*["']true["']""").containsMatchIn(mainActivityTag) ||
            !hasLauncherIntent
        ) {
            problems += "AndroidManifest.xml precisa declarar a MainActivity como exportada e com intent-filter MAIN/LAUNCHER"
        }
        if (!Regex("""(?m)^\s*package\s+com\.example\.app\s*$""").containsMatchIn(activity) ||
            !Regex("""\bclass\s+MainActivity\b""").containsMatchIn(activity) ||
            "setContent" !in activity
        ) {
            problems += "MainActivity.kt precisa declarar o pacote correto, a classe MainActivity e uma UI Compose via setContent"
        }
        if (Regex("""(?s)setContent\s*\{\s*\}""").containsMatchIn(activity)) {
            problems += "MainActivity.kt não pode ter setContent vazio; inclua conteúdo real na UI Compose"
        }
        if (readme.length < 80) {
            problems += "README.md precisa explicar como configurar e executar o projeto"
        }

        // Match explicit unfinished-code markers, not legitimate UI APIs such as
        // Compose's `placeholder = { Text(...) }` parameter or variables named placeholder.
        val unfinishedComment = Regex(
            """(?im)^\s*(?://|/\*+|\*|#|<!--)\s*.*\b(TODO|FIXME|XXX|HACK|placeholder|stub|not implemented|will be implemented later|coming soon|não implementado|nao implementado|ainda não implementado|ainda nao implementado|implementar depois)\b.*$"""
        )
        val unfinishedCode = Regex(
            """(?i)\b(?:TODO|FIXME)\s*\(|\b(?:NotImplementedError|NotImplementedException)\b"""
        )
        val hasObviousPlaceholder = files.any { (path, text) ->
            val sourceFile = path.endsWith(".kt", true) || path.endsWith(".java", true) ||
                path.endsWith(".xml", true) || path.endsWith(".kts", true)
            sourceFile && (unfinishedComment.containsMatchIn(text) || unfinishedCode.containsMatchIn(text))
        }
        if (hasObviousPlaceholder) {
            problems += "há marcadores de recurso pendente/placeholder no código gerado"
        }

        return problems.take(MAX_VALIDATION_ERRORS).takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
    }

    private const val MAX_VALIDATION_ERRORS = 5
}
