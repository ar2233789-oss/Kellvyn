package com.kellvyn.assistant.attachments

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSvgImageGeneratorTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun safeSvgRendersToRealPngFileLocally() {
        val source = """
            <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
              <rect x="0" y="0" width="1024" height="1024" fill="#061f17"/>
              <circle cx="512" cy="512" r="230" fill="#38e6bc"/>
            </svg>
        """.trimIndent()
        val result = LocalSvgImageGenerator.rasterizeAndSave(context, source, "render-test")
        try {
            assertTrue(result is LocalSvgGenerationResult.Success)
            val success = result as LocalSvgGenerationResult.Success
            assertTrue(success.pngFile.isFile && success.pngFile.length() > 0L)
            assertTrue(success.svgFile.isFile && success.svgFile.length() > 0L)
            val bitmap = BitmapFactory.decodeFile(success.pngFile.absolutePath)
            assertTrue("PNG could not be decoded.", bitmap != null)
            bitmap!!.let { rendered ->
                try {
                    assertEquals(1024, rendered.width)
                    assertEquals(1024, rendered.height)
                    assertFalse("Rendered art is fully transparent.", rendered.getPixel(512, 512) ushr 24 == 0)
                } finally {
                    rendered.recycle()
                }
            }
        } finally {
            if (result is LocalSvgGenerationResult.Success) {
                result.pngFile.delete()
                result.svgFile.delete()
            }
        }
    }

    @Test
    fun allowsInternalGradientsWithoutAllowingExternalUrls() {
        val gradient = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><defs><linearGradient id="g"><stop offset="0" stop-color="#38e6bc"/><stop offset="1" stop-color="#061f17"/></linearGradient></defs><rect x="0" y="0" width="10" height="10" fill="url(#g)"/></svg>"""
        assertTrue(LocalSvgImageGenerator.extractAndValidate(gradient).contains("url(#g)"))
    }

    @Test
    fun rejectsScriptsAndExternalImageReferences() {
        val scripted = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><script>alert(1)</script></svg>"""
        val linked = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><image href="https://example.org/secret.png"/></svg>"""
        assertTrue(runCatching { LocalSvgImageGenerator.extractAndValidate(scripted) }.isFailure)
        assertTrue(runCatching { LocalSvgImageGenerator.extractAndValidate(linked) }.isFailure)
    }

}
