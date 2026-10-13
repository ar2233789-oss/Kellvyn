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
        if ("8.7.3" !in rootBuild || "org.jetbrains.kotlin.android" !in rootBuild ||
            "org.jetbrains.kotlin.plugin.compose" !in rootBuild || "2.0.21" !in rootBuild
        ) {
            problems += "build.gradle.kts precisa declarar AGP 8.7.3 e os plugins Kotlin/Compose 2.0.21 compatíveis"
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
        if (!Regex("""compileSdk\s*=\s*35\b""").containsMatchIn(appBuild) ||
            !Regex("""targetSdk\s*=\s*35\b""").containsMatchIn(appBuild) ||
            !Regex("""minSdk\s*=\s*26\b""").containsMatchIn(appBuild)
        ) {
            problems += "app/build.gradle.kts precisa usar compileSdk/targetSdk 35 e minSdk 26"
        }
        if ("compose-bom:2024.12.01" !in appBuild ||
            "activity-compose:1.10.0" !in appBuild ||
            "org.jetbrains.kotlin.plugin.compose" !in appBuild
        ) {
            problems += "faltam dependências/plugins básicos de Jetpack Compose"
        }
        if (!properties.contains("org.gradle.parallel=false") ||
            !Regex("""org\.gradle\.workers\.max\s*=\s*2""").containsMatchIn(properties)
        ) {
            problems += "gradle.properties precisa manter a configuração econômica para build no celular"
        }
        if (!manifest.contains("<manifest") || !manifest.contains("<application") ||
            !Regex("""android:name\s*=\s*["'](?:\.MainActivity|com\.example\.app\.MainActivity)["']""")
                .containsMatchIn(manifest) ||
            !Regex("""android:exported\s*=\s*["']true["']""").containsMatchIn(manifest)
        ) {
            problems += "AndroidManifest.xml precisa declarar a MainActivity exportada como launcher do app"
        }
        if (!Regex("""(?m)^\s*package\s+com\.example\.app\s*$""").containsMatchIn(activity) ||
            !Regex("""\bclass\s+MainActivity\b""").containsMatchIn(activity) ||
            "setContent" !in activity
        ) {
            problems += "MainActivity.kt precisa declarar o pacote correto, a classe MainActivity e uma UI Compose via setContent"
        }
        if (readme.length < 80) {
            problems += "README.md precisa explicar como configurar e executar o projeto"
        }

        val placeholder = Regex(
            """(?i)\b(TODO|placeholder|not implemented|will be implemented later|coming soon|não implementado|nao implementado|ainda não implementado|ainda nao implementado|implementar depois)\b"""
        )
        val hasObviousPlaceholder = files.any { (path, text) ->
            val sourceFile = path.endsWith(".kt", true) || path.endsWith(".java", true) ||
                path.endsWith(".xml", true) || path.endsWith(".kts", true)
            sourceFile && placeholder.containsMatchIn(text)
        }
        if (hasObviousPlaceholder) {
            problems += "há marcadores de recurso pendente/placeholder no código gerado"
        }

        return problems.take(MAX_VALIDATION_ERRORS).takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
    }

    private const val MAX_VALIDATION_ERRORS = 5
}
