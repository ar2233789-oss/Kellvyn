package com.kellvyn.assistant.workspace

/**
 * Structural checks for projects that the first on-device Java compiler accepts.
 * Passing is not a successful APK build; only LocalAndroidCompiler can establish that.
 */
internal object GeneratedAndroidProjectValidator {
    fun validate(files: Map<String, String>): String? {
        fun content(path: String) = files[path.replace('\\', '/')].orEmpty()
        val normalized = files.mapKeys { it.key.replace('\\', '/') }
        val rootBuild = content("build.gradle.kts")
        val settings = content("settings.gradle.kts")
        val properties = content("gradle.properties")
        val appBuild = content("app/build.gradle.kts")
        val manifest = content("app/src/main/AndroidManifest.xml")
        val activity = content("app/src/main/java/com/example/app/MainActivity.java")
        val readme = content("README.md")
        val problems = mutableListOf<String>()

        if (!Regex("""include\s*\(\s*["']:app["']\s*\)""").containsMatchIn(settings)) {
            problems += "settings.gradle.kts precisa incluir :app"
        }
        val versionPattern = Regex("""id\(\s*["']com\.android\.application["']\s*\)\s*version\s*["']([^"']+)["']""")
        if (versionPattern.find(rootBuild)?.groupValues?.getOrNull(1) != AndroidBuildVersions.ANDROID_GRADLE_PLUGIN) {
            problems += "build.gradle.kts precisa fixar o Android Gradle Plugin " + AndroidBuildVersions.ANDROID_GRADLE_PLUGIN
        }
        if (!properties.contains("org.gradle.parallel=false") ||
            !Regex("""org\.gradle\.workers\.max\s*=\s*2""").containsMatchIn(properties)
        ) {
            problems += "gradle.properties precisa manter configuração econômica para o telefone"
        }

        val namespace = Regex("""namespace\s*=\s*["']([^"']+)["']""").find(appBuild)?.groupValues?.getOrNull(1)
        val applicationId = Regex("""applicationId\s*=\s*["']([^"']+)["']""").find(appBuild)?.groupValues?.getOrNull(1)
        if (namespace != "com.example.app" || applicationId != "com.example.app") {
            problems += "namespace e applicationId devem ser com.example.app"
        }
        if (!Regex("compileSdk\\s*=\\s*" + AndroidBuildVersions.COMPILE_SDK + "\\b").containsMatchIn(appBuild) ||
            !Regex("targetSdk\\s*=\\s*" + AndroidBuildVersions.TARGET_SDK + "\\b").containsMatchIn(appBuild) ||
            !Regex("minSdk\\s*=\\s*" + AndroidBuildVersions.MIN_SDK + "\\b").containsMatchIn(appBuild)
        ) {
            problems += "compileSdk/targetSdk/minSdk precisam ser " + AndroidBuildVersions.COMPILE_SDK + "/" +
                AndroidBuildVersions.TARGET_SDK + "/" + AndroidBuildVersions.MIN_SDK
        }
        if ("org.jetbrains.kotlin" in rootBuild || "org.jetbrains.kotlin" in appBuild ||
            "compose" in appBuild.lowercase()
        ) {
            problems += "o primeiro compilador local aceita Java nativo sem Kotlin/Compose"
        }
        if (Regex("""(?m)^\s*(?:api|implementation|compileOnly|runtimeOnly)\s*\(""").containsMatchIn(appBuild)) {
            problems += "a primeira build local não aceita dependências externas; remova-as de app/build.gradle.kts"
        }

        val activityTag = Regex(
            """(?is)<activity\b(?=[^>]*android:name\s*=\s*["'](?:\.MainActivity|com\.example\.app\.MainActivity)["'])[^>]*>"""
        ).find(manifest)?.value.orEmpty()
        if (!manifest.contains("<manifest") || !manifest.contains("<application") || activityTag.isBlank() ||
            !Regex("""android:exported\s*=\s*["']true["']""").containsMatchIn(activityTag) ||
            "android.intent.action.MAIN" !in manifest ||
            "android.intent.category.LAUNCHER" !in manifest
        ) {
            problems += "o manifesto precisa declarar MainActivity exported=true e intent-filter MAIN/LAUNCHER"
        }
        if (!Regex("""(?m)^\s*package\s+com\.example\.app\s*;""").containsMatchIn(activity) ||
            !Regex("""\bclass\s+MainActivity\s+extends\s+Activity\b""").containsMatchIn(activity) ||
            "setContentView(" !in activity
        ) {
            problems += "MainActivity.java precisa estender Activity e montar sua interface com setContentView"
        }
        val allSource = normalized.filterKeys { it.endsWith(".java", true) || it.endsWith(".kt", true) || it.endsWith(".xml", true) }
            .values.joinToString("\n")
        if (Regex("""(?im)^\s*import\s+android\.webkit\.WebView\b|\bnew\s+WebView\s*\(""").containsMatchIn(allSource)) {
            problems += "a interface nativa não pode usar WebView"
        }
        if (normalized.keys.any { it.endsWith(".kt", true) }) {
            problems += "o primeiro compilador local precisa de fontes Java, não Kotlin"
        }
        val extraResources = normalized.keys.filter {
            (it.startsWith("app/src/main/res/") || it.startsWith("app/src/main/assets/")) &&
                !it.endsWith("AndroidManifest.xml", true)
        }
        if (extraResources.isNotEmpty()) {
            problems += "a primeira build local ainda não empacota recursos Android externos; implemente a interface em Java"
        }
        val hasNativeUi = Regex("""\bnew\s+(?:TextView|Button|EditText|LinearLayout|FrameLayout|ScrollView|ImageView|CheckBox|Switch|RadioButton|ProgressBar|SeekBar|Spinner|ListView|RecyclerView)\s*\(""")
            .containsMatchIn(stripCommentsAndStrings(activity))
        if (!hasNativeUi) {
            problems += "MainActivity.java precisa criar controles Android reais por código, não apenas uma Activity vazia"
        }
        val sourceFiles = normalized.filterKeys { it.endsWith(".java", true) }
        val unfinished = Regex(
            """(?im)^\s*(?://|/\*+|\*|#|<!--)\s*(?:(TODO|FIXME|XXX|HACK|PLACEHOLDER|STUB)\b.*|.*\b(not implemented|will be implemented later|coming soon|não implementado|nao implementado)\b.*)$"""
        )
        if (sourceFiles.values.any { unfinished.containsMatchIn(it) || Regex("""\b(?:TODO|FIXME)\s*\(""").containsMatchIn(it) }) {
            problems += "há marcadores de função pendente/placeholder no Java gerado"
        }
        if (readme.length < 60) problems += "README.md precisa explicar as funções do app"
        return problems.take(MAX_ERRORS).takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun stripCommentsAndStrings(value: String): String = value
        .replace(Regex("(?s)/\\*.*?\\*/"), " ")
        .replace(Regex("(?m)//.*$"), " ")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), " ")
        .replace(Regex("'(?:\\\\.|[^'\\\\])*'"), " ")

    private const val MAX_ERRORS = 6
}
