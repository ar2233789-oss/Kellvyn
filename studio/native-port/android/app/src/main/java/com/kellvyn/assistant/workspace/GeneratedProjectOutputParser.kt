package com.kellvyn.assistant.workspace

import java.text.Normalizer
import java.util.Locale

/** Parses generated source as inert data. No generated code is executed by this parser. */
internal object GeneratedProjectOutputParser {
    private val fileBlock = Regex("""(?ms)^Arquivo:\s*([^\r\n]+)\s*\r?\n\s*```[^\r\n]*\r?\n(.*?)\r?\n```""")

    fun currentUserRequest(prompt: String): String {
        val currentMarker = "Pedido atual do usuário:"
        val innerMarker = "Pedido do usuário:"
        val outerIndex = prompt.lastIndexOf(currentMarker)
        var request = if (outerIndex >= 0) prompt.substring(outerIndex + currentMarker.length).trim() else prompt.trim()
        val innerIndex = request.lastIndexOf(innerMarker)
        if (innerIndex >= 0) request = request.substring(innerIndex + innerMarker.length).trim()
        return request
    }

    fun isNativeApkRequest(prompt: String): Boolean {
        val request = currentUserRequest(prompt)
        if (request.isBlank()) return false
        if (Regex("""(?iu)^\s*(?:como|o que|oque|por que|porque|qual|quais|me explique|explique|me diga)\b""")
                .containsMatchIn(request)) return false
        val action = Regex(
            """(?iu)\b(?:crie|criar|cria|gere|gerar|construa|construir|monte|montar|compile|compilar|compila|produza|produzir|desenvolva|desenvolver|entregue|quero|preciso|faça|fazer)\b"""
        ).containsMatchIn(request)
        if (!action) return false
        val asksApk = Regex("""(?iu)\bapk\b""").containsMatchIn(request)
        val asksAndroidApp =
            Regex("""(?iu)\b(?:app|aplicativo|aplicação|aplicacao)\b""").containsMatchIn(request) &&
            Regex("""(?iu)\b(?:android|nativo|nativa)\b""").containsMatchIn(request)
        return asksApk || asksAndroidApp
    }

    fun isExactSdkLicenseAcceptance(prompt: String): Boolean {
        val request = currentUserRequest(prompt)
        val normalized = Normalizer.normalize(request, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""[.!\s]+$"""), "")
            .trim()
        return normalized == "aceito a licenca do android sdk"
    }

    fun parse(response: String): List<ProjectFileInput> {
        val files = ArrayList<ProjectFileInput>()
        val seen = HashSet<String>()
        for (match in fileBlock.findAll(response)) {
            val path = match.groupValues[1].trim().removeSurrounding("`").trim()
            val contents = match.groupValues[2].trimEnd()
            require(path.isNotBlank()) { "Foi gerado um arquivo sem caminho." }
            require(!path.startsWith("/") && !path.startsWith("\\") && !path.contains("\\")) {
                "Caminho de arquivo absoluto ou com barras inválidas: " + path
            }
            val segments = path.split("/")
            require(segments.all { it.isNotBlank() && it != "." && it != ".." }) {
                "Caminho inseguro no projeto gerado: " + path
            }
            require(segments.none { it.contains(":") || it.any { ch -> ch.code < 32 } }) {
                "O caminho contém caracteres não permitidos: " + path
            }
            require(seen.add(path.lowercase(Locale.ROOT))) { "O modelo gerou o caminho duplicado: " + path }
            files.add(ProjectFileInput(path, contents))
            require(files.size <= 80) { "O projeto gerado excede o limite de 80 arquivos." }
        }
        return files
    }

    fun nativeBuildInstruction(): String = """
        COMPILADOR LOCAL DE APK ANDROID — CONTRATO DO PRIMEIRO PERFIL:
        Gere um aplicativo Android nativo simples em Java (framework Android), pronto para ser compilado offline no próprio aparelho.
        Devolva cada arquivo completo no formato exato:
        Arquivo: caminho/relativo.ext
        ```linguagem
        conteúdo integral
        ```
        Inclua estes sete arquivos:
        1. settings.gradle.kts com repositórios padrão, rootProject.name e include(":app").
        2. build.gradle.kts com id("com.android.application") version "8.7.3" apply false.
        3. gradle.properties com org.gradle.parallel=false e org.gradle.workers.max=2.
        4. app/build.gradle.kts com namespace/applicationId com.example.app, compileSdk 35, minSdk 26 e targetSdk 35; não declare dependências externas.
        5. app/src/main/AndroidManifest.xml com application e uma MainActivity exported=true com intent-filter MAIN e LAUNCHER.
        6. app/src/main/java/com/example/app/MainActivity.java: package com.example.app, extends android.app.Activity, cria a interface com views Android por código e chama setContentView.
        7. README.md com pelo menos 60 caracteres explicando o app.
        Não gere arquivos .kt, Compose, WebView, recursos XML de interface, assets, wrappers ou dependências externas: este primeiro compilador não os empacota. Não use TODO, FIXME, pseudocódigo, reticências ou placeholders. Mantenha a implementação útil e completa dentro desse perfil.
        Não alegue sucesso de compilação; o app vai compilar e verificar o APK somente depois que a ferramenta local confirmar.
    """.trimIndent()
}
