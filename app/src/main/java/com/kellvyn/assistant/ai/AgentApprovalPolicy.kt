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
        "github_import"
    )

    fun requiresApproval(name: String): Boolean = name in protectedActions
}
