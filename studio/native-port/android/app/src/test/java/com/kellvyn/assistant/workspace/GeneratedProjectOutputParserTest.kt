package com.kellvyn.assistant.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedProjectOutputParserTest {
    @Test
    fun extractsCompleteFileBlocks() {
        val response = """
            Arquivo: app/src/main/java/com/example/app/MainActivity.java
            ```java
            package com.example.app;
            public class MainActivity {}
            ```

            Arquivo: README.md
            ```markdown
            Este é um aplicativo demonstrativo que explica sua finalidade.
            ```
        """.trimIndent()
        val files = GeneratedProjectOutputParser.parse(response)
        assertEquals(2, files.size)
        assertEquals("app/src/main/java/com/example/app/MainActivity.java", files[0].path)
        assertTrue(files[0].content.contains("package com.example.app;"))
        assertEquals("README.md", files[1].path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDirectoryTraversal() {
        GeneratedProjectOutputParser.parse("""
            Arquivo: ../outside.txt
            ```text
            nope
            ```
        """.trimIndent())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicatePathsIgnoringCase() {
        GeneratedProjectOutputParser.parse("""
            Arquivo: README.md
            ```text
            First contents
            ```
            Arquivo: readme.md
            ```text
            Second contents
            ```
        """.trimIndent())
    }

    @Test
    fun distinguishesNativeApkRequestsFromQuestionsAndBots() {
        assertTrue(GeneratedProjectOutputParser.isNativeApkRequest("Crie um aplicativo Android nativo e gere o APK."))
        assertTrue(GeneratedProjectOutputParser.isNativeApkRequest("Quero compilar este app como APK."))
        assertFalse(GeneratedProjectOutputParser.isNativeApkRequest("Como faço para compilar um APK?"))
        assertFalse(GeneratedProjectOutputParser.isNativeApkRequest("Crie um bot para Telegram."))
    }

    @Test
    fun acceptsOnlyExplicitSdkConsentPhrase() {
        assertTrue(GeneratedProjectOutputParser.isExactSdkLicenseAcceptance("ACEITO A LICENÇA DO ANDROID SDK"))
        assertTrue(GeneratedProjectOutputParser.isExactSdkLicenseAcceptance("aceito a licenca do android sdk."))
        assertFalse(GeneratedProjectOutputParser.isExactSdkLicenseAcceptance("acho que aceito a licença do Android SDK"))
    }
}
