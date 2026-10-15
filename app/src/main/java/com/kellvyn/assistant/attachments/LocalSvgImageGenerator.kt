package com.kellvyn.assistant.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import com.caverock.androidsvg.SVG
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.LocalLiteRtAssistantEngine
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node

sealed interface LocalSvgGenerationResult {
    data class Success(val pngFile: File, val svgFile: File) : LocalSvgGenerationResult
    data class Failure(val reason: String) : LocalSvgGenerationResult
}

/**
 * Local text-model -> safe SVG -> PNG pipeline. This produces vector illustrations; it is not
 * diffusion-based photorealistic generation and cannot interpret attached visual references.
 * It never permits script, external image/link loading, embedded HTML, or arbitrary CSS.
 */
object LocalSvgImageGenerator {
    private const val MAX_SVG_CHARS = 48_000
    private const val OUTPUT_SIZE = 1024
    private const val MAX_OUTPUT_PNG_BYTES = 12 * 1024 * 1024
    private val allowedElements = setOf(
        "svg", "g", "defs", "linearGradient", "radialGradient", "stop",
        "rect", "circle", "ellipse", "line", "polyline", "polygon", "path", "text", "tspan"
    )
    private val allowedAttributes = setOf(
        "xmlns", "width", "height", "viewBox", "version", "id", "x", "y", "x1", "y1", "x2", "y2",
        "cx", "cy", "r", "rx", "ry", "fill", "stroke", "stroke-width", "opacity", "fill-opacity",
        "stroke-opacity", "points", "d", "transform", "offset", "stop-color", "stop-opacity",
        "font-family", "font-size", "font-weight", "font-style", "text-anchor", "dominant-baseline",
        "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "fill-rule", "clip-rule",
        "gradientUnits", "gradientTransform", "spreadMethod"
    )

    suspend fun generate(
        context: Context,
        engine: LocalLiteRtAssistantEngine,
        prompt: String,
        onProgress: (String) -> Unit = {}
    ): LocalSvgGenerationResult {
        if (prompt.isBlank()) return LocalSvgGenerationResult.Failure("Descreva a imagem que deseja gerar.")
        if (prompt.length > 4_000) return LocalSvgGenerationResult.Failure("O pedido de imagem ultrapassa 4.000 caracteres.")
        onProgress("Planejando a ilustração vetorial local…")
        var candidate = engine.generateSvgSource(prompt) { partial ->
            if (partial.isNotBlank()) onProgress("Desenhando elementos vetoriais…")
        }
        var svg: String? = null
        var lastError = "A IA não retornou uma ilustração SVG válida."
        for (attempt in 0..1) {
            when (candidate) {
                is AssistantResult.Unavailable -> return LocalSvgGenerationResult.Failure(candidate.reason)
                is AssistantResult.Failure -> return LocalSvgGenerationResult.Failure(candidate.reason)
                is AssistantResult.Success -> {
                    val responseText = candidate.text
                    try {
                        svg = extractAndValidate(responseText)
                        break
                    } catch (e: Exception) {
                        lastError = e.message ?: lastError
                        if (attempt == 0) {
                            onProgress("Corrigindo a estrutura da ilustração…")
                            candidate = engine.generateSvgSource(
                                "Crie uma versão mais simples da mesma ilustração. Descrição original: " +
                                    prompt.take(1_200) + ". Erro de validação: " + lastError + ". " +
                                    "Devolva somente um SVG válido e completo, começando por <svg e terminando por </svg>. " +
                                    "Use no máximo 14 formas simples, sem explicações, markdown, comentários, links, CSS, scripts ou imagens externas."
                            )
                        }
                    }
                }
            }
            if (svg != null) break
        }
        val validSvg = svg ?: return LocalSvgGenerationResult.Failure("Não consegui validar a ilustração vetorial após a correção: $lastError")
        return try {
            onProgress("Renderizando a imagem PNG neste aparelho…")
            rasterizeAndSave(context.applicationContext, validSvg, prompt)
        } catch (e: Exception) {
            LocalSvgGenerationResult.Failure("A ilustração foi criada, mas não consegui convertê-la em PNG: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Exposed so instrumentation tests can validate the renderer without invoking a language model. */
    fun rasterizeAndSave(context: Context, rawSvg: String, prompt: String = "ilustração"): LocalSvgGenerationResult {
        val svgText = extractAndValidate(rawSvg)
        val directory = File(context.filesDir, "kellvyn_images")
        check(directory.isDirectory || directory.mkdirs()) { "Não foi possível preparar a pasta privada de imagens." }
        val safeSlug = prompt.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(28)
            .ifBlank { "ilustracao" }
        val id = System.currentTimeMillis().toString() + "-" + safeSlug
        val svgFile = File(directory, "kellvyn-image-$id.svg")
        val pngFile = File(directory, "kellvyn-image-$id.png")
        val temporaryPng = File(directory, "kellvyn-image-$id.png.part")
        try {
            svgFile.writeText(svgText, Charsets.UTF_8)
            val picture = SVG.getFromString(svgText).renderToPicture(OUTPUT_SIZE, OUTPUT_SIZE)
            val bitmap = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).drawPicture(picture)
                temporaryPng.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "O Android não conseguiu codificar a ilustração como PNG."
                    }
                }
            } finally {
                bitmap.recycle()
            }
            check(temporaryPng.isFile && temporaryPng.length() in 1..MAX_OUTPUT_PNG_BYTES.toLong()) {
                "O PNG ficou vazio ou ultrapassou o limite de 12 MB."
            }
            check(temporaryPng.renameTo(pngFile)) { "Não foi possível finalizar o arquivo PNG." }
            return LocalSvgGenerationResult.Success(pngFile, svgFile)
        } catch (e: Exception) {
            temporaryPng.delete()
            svgFile.delete()
            pngFile.delete()
            throw e
        }
    }

    fun extractAndValidate(source: String): String {
        require(source.length <= MAX_SVG_CHARS + 12_000) { "A resposta de imagem ultrapassou o limite de texto permitido." }
        val lower = source.lowercase()
        val start = lower.indexOf("<svg")
        val end = lower.lastIndexOf("</svg>")
        require(start >= 0) { "A IA não retornou uma tag SVG completa." }
        val svg = if (end > start) {
            source.substring(start, end + "</svg>".length).trim()
        } else {
            repairTruncatedSvg(source.substring(start))
                ?: throw IllegalArgumentException("A IA não retornou uma tag SVG completa.")
        }
        require(svg.length <= MAX_SVG_CHARS) { "O SVG ultrapassou o limite de 48.000 caracteres." }
        require(!svg.contains("<!doctype", ignoreCase = true) && !svg.contains("<!entity", ignoreCase = true)) {
            "O SVG tentou declarar entidades XML que não são permitidas."
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android's built-in DocumentBuilderFactory throws UnsupportedOperationException
            // for setXIncludeAware, so attempt it defensively; DTD/entities are blocked below.
            runCatching { isXIncludeAware = false }
            isExpandEntityReferences = false
            // Reject DTD/entities in the source itself because not all Android parsers expose
            // the same optional SAX feature URIs.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))
        val root = document.documentElement ?: throw IllegalArgumentException("O SVG não possui elemento raiz.")
        val rootName = root.localName ?: root.nodeName.substringAfter(':')
        require(rootName == "svg") { "A raiz do documento precisa ser SVG." }
        require(root.getAttribute("viewBox").isNotBlank()) { "O SVG precisa definir viewBox para ser renderizado de forma previsível." }
        validateNode(root)
        return svg
    }

    /**
     * Repairs only a truncated tail when the SVG root and at least one complete drawing shape
     * already exist. The repaired document still goes through the full XML and allow-list checks.
     */
    private fun repairTruncatedSvg(fragment: String): String? {
        var candidate = fragment.trim()
        if (candidate.length > MAX_SVG_CHARS || !candidate.startsWith("<svg")) return null

        // If generation stopped in the middle of a tag/attribute, discard just that unfinished tail.
        val lastOpen = candidate.lastIndexOf('<')
        val lastClose = candidate.lastIndexOf('>')
        if (lastOpen > lastClose) candidate = candidate.substring(0, lastOpen).trimEnd()
        if (candidate.isBlank() || candidate.length > MAX_SVG_CHARS) return null

        val tagPattern = Regex("""<(/?)([A-Za-z][A-Za-z0-9:_-]*)([^<>]*?)(/?)>""")
        val tags = tagPattern.findAll(candidate).toList()
        if (tags.isEmpty()) return null
        val root = tags.first()
        if (root.range.first != 0 || root.groupValues[1].isNotEmpty() || root.groupValues[2] != "svg" || root.groupValues[4] == "/") {
            return null
        }

        val stack = mutableListOf<String>()
        val drawableElements = setOf("rect", "circle", "ellipse", "line", "polyline", "polygon", "path", "text", "tspan")
        var drawableCount = 0
        var cursor = 0
        for (tag in tags) {
            if (candidate.substring(cursor, tag.range.first).contains('<')) return null
            val isClosing = tag.groupValues[1] == "/"
            val name = tag.groupValues[2]
            val isSelfClosing = tag.groupValues[4] == "/"
            if (isClosing) {
                if (stack.lastOrNull() != name) return null
                stack.removeAt(stack.lastIndex)
            } else {
                if (name in drawableElements) drawableCount++
                if (!isSelfClosing) stack.add(name)
            }
            cursor = tag.range.last + 1
        }
        if (candidate.substring(cursor).contains('<')) return null
        if (stack.firstOrNull() != "svg" || stack.isEmpty() || drawableCount == 0) return null

        val repaired = candidate + stack.asReversed().joinToString(separator = "") { "</$it>" }
        return repaired.takeIf { it.length <= MAX_SVG_CHARS }
    }

    private fun validateNode(node: Node) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val name = node.localName ?: node.nodeName.substringAfter(':')
            require(name in allowedElements) { "O SVG contém o elemento não permitido <$name>." }
            val attributes = node.attributes
            for (index in 0 until attributes.length) {
                val attr = attributes.item(index)
                val attrName = attr.nodeName.substringAfter(':')
                val value = attr.nodeValue.orEmpty()
                require(attrName in allowedAttributes) { "O SVG contém o atributo não permitido $attrName." }
                if (attrName == "id") {
                    require(value.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,40}"))) { "O SVG contém um identificador inválido." }
                }
                val normalized = value.lowercase()
                if (attrName == "xmlns") {
                    require(value == "http://www.w3.org/2000/svg") {
                        "O SVG contém um namespace não permitido."
                    }
                } else {
                    require(!normalized.contains("javascript:") &&
                        !normalized.contains("data:") &&
                        !normalized.contains("https:") &&
                        !normalized.contains("http:") &&
                        !normalized.contains("file:") &&
                        !normalized.contains("expression(")) {
                        "O SVG tentou referenciar conteúdo externo ou executável."
                    }
                }
                if (normalized.contains("url(")) {
                    require(Regex("""url\(#[A-Za-z][A-Za-z0-9_-]{0,40}\)""").matches(value)) {
                        "O SVG só pode usar referências internas a gradientes."
                    }
                }
            }
        }
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.PROCESSING_INSTRUCTION_NODE ||
                child.nodeType == Node.ENTITY_REFERENCE_NODE ||
                child.nodeType == Node.DOCUMENT_TYPE_NODE
            ) throw IllegalArgumentException("O SVG contém uma instrução XML não permitida.")
            validateNode(child)
        }
    }
}
