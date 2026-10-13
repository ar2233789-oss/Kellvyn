package com.kellvyn.assistant.tools

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualShellExecutorTest {

    @Test
    fun rejectsWorkingDirectoryOutsideWorkspaceBeforeStartingShell() {
        val parent = Files.createTempDirectory("kellvyn-shell-test").toFile()
        try {
            val workspace = File(parent, "workspace").apply { mkdirs() }
            val outside = File(parent, "outside").apply { mkdirs() }
            val executor = ManualShellExecutor(workspace)
            try {
                executor.execute("echo should-not-run", outside)
                throw AssertionError("The executor should reject an outside working directory.")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("dentro da pasta privada"))
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsBlankCommandBeforeStartingProcess() {
        val parent = Files.createTempDirectory("kellvyn-shell-empty-test").toFile()
        try {
            val workspace = File(parent, "workspace").apply { mkdirs() }
            val executor = ManualShellExecutor(workspace)
            try {
                executor.execute("   ", workspace)
                throw AssertionError("Blank commands must be rejected.")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("Digite um comando"))
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun rejectsOversizedCommandBeforeStartingProcess() {
        val parent = Files.createTempDirectory("kellvyn-shell-long-test").toFile()
        try {
            val workspace = File(parent, "workspace").apply { mkdirs() }
            val executor = ManualShellExecutor(workspace)
            try {
                executor.execute("x".repeat(2_001), workspace)
                throw AssertionError("Oversized commands must be rejected.")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("passou do limite"))
            }
        } finally {
            parent.deleteRecursively()
        }
    }
}
