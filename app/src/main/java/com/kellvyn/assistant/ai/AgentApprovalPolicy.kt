package com.kellvyn.assistant.ai

/**
 * Mutating actions that can damage/remove work or change repository state require explicit consent.
 * Normal source edits requested in chat are intentionally automatic.
 */
internal object AgentApprovalPolicy {
    private val protectedActions = setOf(
        "git_init",
        "git_stage_all",
        "git_commit",
        "delete_file",
        "delete_project",
        "github_import",
        "termux_run_command"
    )

    // Optional setting for people who want explicit consent before normal changes as well.
    private val reinforcedActions = setOf(
        "write_file",
        "patch_file",
        "build_project_apk"
    )

    fun requiresApproval(name: String, reinforcedProtection: Boolean = false): Boolean =
        name in protectedActions || (reinforcedProtection && name in reinforcedActions)
}
