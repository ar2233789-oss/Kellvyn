package com.kellvyn.assistant.attachments

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/**
 * Extracts searchable text from PDF, DOCX, and ODT locally. Scanned pages are not OCRed here;
 * image files use LocalImageTextExtractor instead. Size/page/output limits avoid unbounded work.
 */
object LocalDocumentTextExtractor {
    private const val MAX_DOCUMENT_BYTES = 20 * 1024 * 1024
    private const val MAX_XML_BYTES = 5 * 1024 * 1024
    private const val MAX_EXTRACTED_CHARS = 60_000
    private const val MAX_PDF_PAGES = 50

    fun supports(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf("pdf", "docx", "odt") || mimeType.lowercase() in setOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text"
        )
    }

    fun extract(context: Context, uri: Uri, fileName: String, mimeType: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val kind = when {
            extension == "pdf" || mimeType.equals("application/pdf", true) -> "pdf"
            extension == "docx" || mimeType.equals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true
            ) -> "docx"
            extension == "odt" || mimeType.equals("application/vnd.oasis.opendocument.text", true) -> "odt"
            else -> throw IllegalArgumentException("Formato de documento não suportado: $fileName")
        }

        val temp = File(context.cacheDir, "kellvyn-document-" + System.nanoTime() + "." + kind)
        try {
            copyBounded(context, uri, temp)
            val extracted = when (kind) {
                "pdf" -> extractPdf(context, temp)
                "docx" -> extractXmlFromZip(temp, "word/document.xml")
                else -> extractXmlFromZip(temp, "content.xml")
            }.trim()
            require(extracted.isNotBlank()) {
                when (kind) {
                    "pdf" -> "O PDF não contém texto pesquisável. Pode ser um escaneamento; a leitura visual/OCR de PDF ainda não está disponível."
                    else -> "Não encontrei texto legível dentro deste documento."
                }
            }
            return extracted.take(MAX_EXTRACTED_CHARS)
        } finally {
            temp.delete()
        }
    }

    private fun copyBounded(context: Context, uri: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("O Android não conseguiu abrir o documento escolhido.")
        var total = 0L
        try {
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        require(total <= MAX_DOCUMENT_BYTES) {
                            "O documento ultrapassa o limite local de 20 MB."
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    private fun extractPdf(context: Context, file: File): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { document ->
            require(document.numberOfPages > 0) { "O PDF não contém páginas." }
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(document.numberOfPages, MAX_PDF_PAGES)
                sortByPosition = true
            }
            val value = stripper.getText(document)
            val clipped = value.take(MAX_EXTRACTED_CHARS)
            return if (document.numberOfPages > MAX_PDF_PAGES) {
                clipped + "\n\n[Leitura limitada às primeiras $MAX_PDF_PAGES páginas de ${document.numberOfPages}.]"
            } else clipped
        }
    }

    private fun extractXmlFromZip(file: File, entryName: String): String {
        var xmlBytes: ByteArray? = null
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == entryName) {
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        require(total <= MAX_XML_BYTES) {
                            "O conteúdo XML descompactado ultrapassou o limite seguro de 5 MB."
                        }
                        output.write(buffer, 0, count)
                    }
                    xmlBytes = output.toByteArray()
                    break
                }
                zip.closeEntry()
            }
        }
        val bytes = xmlBytes ?: throw IllegalArgumentException("O documento não contém o arquivo de texto esperado: $entryName")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val root = factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement
        val tags = if (entryName == "word/document.xml") setOf("p") else setOf("p", "h")
        val result = StringBuilder()
        appendParagraphs(root, tags, result)
        return result.toString().take(MAX_EXTRACTED_CHARS)
    }

    private fun appendParagraphs(node: Node, paragraphTags: Set<String>, output: StringBuilder) {
        if (output.length >= MAX_EXTRACTED_CHARS) return
        val local = (node.localName ?: node.nodeName.substringAfter(':')).lowercase()
        if (node.nodeType == Node.ELEMENT_NODE && local in paragraphTags) {
            appendTextOnly(node, output)
            if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
            return
        }
        val children: NodeList = node.childNodes
        for (index in 0 until children.length) {
            appendParagraphs(children.item(index), paragraphTags, output)
            if (output.length >= MAX_EXTRACTED_CHARS) break
        }
    }

    private fun appendTextOnly(node: Node, output: StringBuilder) {
        if (output.length >= MAX_EXTRACTED_CHARS) return
        if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            output.append(node.nodeValue.orEmpty())
            return
        }
        val children = node.childNodes
        for (index in 0 until children.length) {
            appendTextOnly(children.item(index), output)
            if (output.length >= MAX_EXTRACTED_CHARS) break
        }
    }
}
