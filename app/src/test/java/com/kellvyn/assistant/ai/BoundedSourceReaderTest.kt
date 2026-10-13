package com.kellvyn.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedSourceReaderTest {
    @Test
    fun readsOnlyRequestedLineRange() {
        val result = BoundedSourceReader.render(
            projectId = "project-1",
            path = "src/Main.kt",
            text = "one\ntwo\nthree\nfour\nfive",
            startLine = 2,
            maxLines = 2
        )

        assertTrue(result.contains("linhas 2-3 de 5"))
        assertTrue(result.contains("\ntwo\nthree\n"))
        assertTrue(!result.contains("\nfour\n"))
    }

    @Test
    fun clampsLineRangeAtEndOfFile() {
        val result = BoundedSourceReader.render(
            projectId = "project-1",
            path = "src/Main.kt",
            text = "one\ntwo\nthree",
            startLine = 2,
            maxLines = 120
        )

        assertTrue(result.contains("linhas 2-3 de 3"))
        assertTrue(result.contains("\ntwo\nthree\n"))
    }

    @Test
    fun refusesOversizedWholeFileButAllowsSmallWindow() {
        val text = (1..5_000).joinToString("\n") { "line-$it with some content" }

        assertThrows(IllegalArgumentException::class.java) {
            BoundedSourceReader.render("project-1", "large.kt", text)
        }

        val result = BoundedSourceReader.render(
            projectId = "project-1",
            path = "large.kt",
            text = text,
            startLine = 4_000,
            maxLines = 3
        )
        assertTrue(result.contains("linhas 4000-4002 de 5000"))
        assertTrue(result.contains("line-4000"))
        assertTrue(result.contains("line-4002"))
        assertTrue(!result.contains("line-4003"))
    }

    @Test
    fun rejectsInvalidLineRangesAndOversizedLineCount() {
        val text = "one\ntwo\nthree"
        assertThrows(IllegalArgumentException::class.java) {
            BoundedSourceReader.render("project-1", "a.kt", text, startLine = 0, maxLines = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoundedSourceReader.render("project-1", "a.kt", text, startLine = 1, maxLines = 121)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoundedSourceReader.render("project-1", "a.kt", text, startLine = 4, maxLines = 1)
        }
    }

    @Test
    fun normalizesCrLfAndCrLineEndingsForExcerpts() {
        val result = BoundedSourceReader.render(
            projectId = "project-1",
            path = "src/Main.kt",
            text = "one\r\ntwo\rthree\n",
            startLine = 2,
            maxLines = 2
        )

        assertTrue(result.contains("linhas 2-4 de 4"))
        assertTrue(result.contains("\ntwo\nthree\n"))
        assertTrue(!result.contains("\r"))
    }

    @Test
    fun returnsSmallFileInFullWhenNoRangeIsRequested() {
        val result = BoundedSourceReader.render("project-1", "a.kt", "one\ntwo")
        assertTrue(result.contains("(arquivo completo)"))
        assertEquals(1, "one".toRegex().findAll(result).count())
        assertTrue(result.contains("\none\ntwo\n"))
    }
}
