package com.kellvyn.assistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentApprovalPolicyTest {
    @Test
    fun normalRequestedSourceEditsRunWithoutExtraApproval() {
        assertFalse(AgentApprovalPolicy.requiresApproval("write_file"))
        assertFalse(AgentApprovalPolicy.requiresApproval("patch_file"))
    }

    @Test
    fun destructiveFileActionsStillRequireApproval() {
        assertTrue(AgentApprovalPolicy.requiresApproval("delete_file"))
        assertTrue(AgentApprovalPolicy.requiresApproval("delete_project"))
    }

    @Test
    fun importingProjectsAndChangingGitHistoryRequireApproval() {
        assertTrue(AgentApprovalPolicy.requiresApproval("github_import"))
        assertTrue(AgentApprovalPolicy.requiresApproval("git_init"))
        assertTrue(AgentApprovalPolicy.requiresApproval("git_stage_all"))
        assertTrue(AgentApprovalPolicy.requiresApproval("git_commit"))
    }

    @Test
    fun reinforcedProtectionRequiresConsentForNormalChangesButLeavesReadsAvailable() {
        assertFalse(AgentApprovalPolicy.requiresApproval("write_file"))
        assertFalse(AgentApprovalPolicy.requiresApproval("patch_file"))
        assertTrue(AgentApprovalPolicy.requiresApproval("write_file", reinforcedProtection = true))
        assertTrue(AgentApprovalPolicy.requiresApproval("patch_file", reinforcedProtection = true))
        assertFalse(AgentApprovalPolicy.requiresApproval("build_project_apk"))
        assertTrue(AgentApprovalPolicy.requiresApproval("build_project_apk", reinforcedProtection = true))
        assertFalse(AgentApprovalPolicy.requiresApproval("read_file", reinforcedProtection = true))
        assertFalse(AgentApprovalPolicy.requiresApproval("list_projects", reinforcedProtection = true))
    }

    @Test
    fun everyTermuxCommandRequiresExplicitApprovalEvenWithoutReinforcedProtection() {
        assertTrue(AgentApprovalPolicy.requiresApproval("termux_run_command"))
        assertTrue(AgentApprovalPolicy.requiresApproval("termux_run_command", reinforcedProtection = false))
        assertTrue(AgentApprovalPolicy.requiresApproval("termux_run_command", reinforcedProtection = true))
    }

    @Test
    fun readOnlyAndUnknownActionsDoNotRequestMutationApproval() {
        assertFalse(AgentApprovalPolicy.requiresApproval("read_file"))
        assertFalse(AgentApprovalPolicy.requiresApproval("list_projects"))
        assertFalse(AgentApprovalPolicy.requiresApproval("unknown_tool"))
    }
}
