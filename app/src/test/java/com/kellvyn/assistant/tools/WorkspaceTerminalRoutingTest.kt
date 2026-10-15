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
        assertTrue(shouldUseWorkspaceTerminalHandler("GIT"))
        assertTrue(shouldUseWorkspaceTerminalHandler("Cd"))
        assertFalse(shouldUseWorkspaceTerminalHandler("LS"))
    }
}
