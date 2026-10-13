package com.kellvyn.assistant.workspace

/**
 * Determines how the editor should react when an agent changes the stored version of an open file.
 * Never choose a destructive refresh when the visible draft differs from the saved version.
 */
internal object EditorSyncPolicy {
    enum class Action {
        NO_CHANGE,
        REFRESH_SAVED_CONTENT,
        PRESERVE_LOCAL_DRAFT,
        CLEAR_REMOVED_FILE,
        PRESERVE_DRAFT_FOR_REMOVED_FILE
    }

    fun decide(
        savedBefore: String?,
        savedAfter: String?,
        draftBefore: String,
        draftNow: String
    ): Action {
        if (savedBefore == savedAfter) return Action.NO_CHANGE

        val wasInSync = if (savedBefore == null) draftBefore.isEmpty() else draftBefore == savedBefore
        val remainedInSync = draftNow == draftBefore
        if (!wasInSync || !remainedInSync) {
            return if (savedAfter == null) {
                Action.PRESERVE_DRAFT_FOR_REMOVED_FILE
            } else {
                Action.PRESERVE_LOCAL_DRAFT
            }
        }

        return if (savedAfter == null) {
            Action.CLEAR_REMOVED_FILE
        } else {
            Action.REFRESH_SAVED_CONTENT
        }
    }
}
