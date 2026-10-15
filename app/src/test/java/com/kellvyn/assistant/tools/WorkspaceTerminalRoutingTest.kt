package com.kellvyn.assistant.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTerminalRoutingTest {
    @Test
    fun customWorkspaceCommandsRemainInsideTheBuiltInRouter() {
        listOf("help", "pwd", "cd", "tree", "write", "append", "status", "projects", "git")
            .forEach { assertTrue("$it should use Kellvyn's workspace handler", shouldUseWorkspaceTerminalHandler(it)) }
    }

    @Test
    fun ordinaryCommandsUseAndroidShellAndKeepTheirFlagsAndPipelines() {
        listOf("ls", "cat", "grep", "find", "mkdir", "touch", "rm", "cp", "mv", "stat", "echo", "sh")
            .forEach { assertFalse("$it should use Android's shell", shouldUseWorkspaceTerminalHandler(it)) }
    }

    @Test
    fun commandRoutingIsCaseInsensitive() {
        assertTrue(shouldUseWorkspaceTerminalHandler("GIT status"))
        assertTrue(shouldUseWorkspaceTerminalHandler("Cd app"))
        assertFalse(shouldUseWorkspaceTerminalHandler("LS -la"))
    }

    @Test
    fun shellFlagsAreNotConsumedByBasicWorkspaceHandlers() {
        assertTrue(shouldUseWorkspaceTerminalHandler("tree src"))
        assertFalse(shouldUseWorkspaceTerminalHandler("tree -L 2"))
        assertFalse(shouldUseWorkspaceTerminalHandler("pwd -P"))
    }

    @Test
    fun cdWithShellOperatorsUsesTheActualAndroidShell() {
        assertFalse(shouldUseWorkspaceTerminalHandler("cd app && ls -la"))
        assertFalse(shouldUseWorkspaceTerminalHandler("cd app; pwd"))
        assertFalse(shouldUseWorkspaceTerminalHandler("cd app | cat"))
    }
}
