package com.kellvyn.assistant.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LocalAttachmentExtractionTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun extractsTextFromDocxAndOdtWithoutNetwork() {
        val docx = File(context.cacheDir, "kellvyn-attachment-test.docx")
        val odt = File(context.cacheDir, "kellvyn-attachment-test.odt")
        try {
            writeZipEntry(
                docx,
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="urn:word"><w:body><w:p><w:r><w:t>Texto DOCX local</w:t></w:r></w:p></w:body></w:document>"""
            )
            writeZipEntry(
                odt,
                "content.xml",
                """<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:office" xmlns:text="urn:text"><office:body><office:text><text:p>Texto ODT local</text:p></office:text></office:body></office:document-content>"""
            )

            val docxText = LocalDocumentTextExtractor.extract(
                context, Uri.fromFile(docx), docx.name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
            val odtText = LocalDocumentTextExtractor.extract(
                context, Uri.fromFile(odt), odt.name,
                "application/vnd.oasis.opendocument.text"
            )
            assertTrue("DOCX text was not extracted: $docxText", docxText.contains("Texto DOCX local"))
            assertTrue("ODT text was not extracted: $odtText", odtText.contains("Texto ODT local"))
        } finally {
            docx.delete()
            odt.delete()
        }
    }

    @Test
    fun extractsSearchableTextFromPdfWithoutNetwork() {
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "kellvyn-attachment-test.pdf")
        try {
            PDDocument().use { document ->
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 18f)
                    stream.newLineAtOffset(40f, 700f)
                    stream.showText("Kellvyn PDF local test")
                    stream.endText()
                }
                document.save(file)
            }
            val text = LocalDocumentTextExtractor.extract(
                context, Uri.fromFile(file), file.name, "application/pdf"
            )
            assertTrue("PDF text was not extracted: $text", text.contains("Kellvyn PDF local test"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun extractsVisibleImageTextWithBundledLocalOcr() = runBlocking {
        val file = File(context.cacheDir, "kellvyn-ocr-test.png")
        val bitmap = Bitmap.createBitmap(1200, 320, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 92f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            canvas.drawText("KELLVYN OCR TEST", 28f, 205f, paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val text = LocalImageTextExtractor.extract(context, Uri.fromFile(file))
            assertTrue("OCR did not detect the expected word: $text", text.uppercase().contains("KELLVYN"))
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    private fun writeZipEntry(file: File, entryName: String, xml: String) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
