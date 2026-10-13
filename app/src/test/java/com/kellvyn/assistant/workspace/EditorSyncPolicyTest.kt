package com.kellvyn.assistant.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSyncPolicyTest {
    @Test
    fun refreshesWhenSavedFileChangedAndDraftWasClean() {
        assertEquals(
            EditorSyncPolicy.Action.REFRESH_SAVED_CONTENT,
            EditorSyncPolicy.decide(
                savedBefore = "old source",
                savedAfter = "new source",
                draftBefore = "old source",
                draftNow = "old source"
            )
        )
    }

    @Test
    fun preservesDraftWhenUserHadUnsavedChangesBeforeAgentEdit() {
        assertEquals(
            EditorSyncPolicy.Action.PRESERVE_LOCAL_DRAFT,
            EditorSyncPolicy.decide(
                savedBefore = "old source",
                savedAfter = "new source",
                draftBefore = "user draft",
                draftNow = "user draft"
            )
        )
    }

    @Test
    fun preservesDraftWhenUserChangesEditorDuringAgentRequest() {
        assertEquals(
            EditorSyncPolicy.Action.PRESERVE_LOCAL_DRAFT,
            EditorSyncPolicy.decide(
                savedBefore = "old source",
                savedAfter = "new source",
                draftBefore = "old source",
                draftNow = "new unsaved draft"
            )
        )
    }

    @Test
    fun clearsCleanEditorWhenAgentRemovesSavedFile() {
        assertEquals(
            EditorSyncPolicy.Action.CLEAR_REMOVED_FILE,
            EditorSyncPolicy.decide(
                savedBefore = "old source",
                savedAfter = null,
                draftBefore = "old source",
                draftNow = "old source"
            )
        )
    }

    @Test
    fun preservesDraftWhenAgentRemovesFileWithUnsavedChanges() {
        assertEquals(
            EditorSyncPolicy.Action.PRESERVE_DRAFT_FOR_REMOVED_FILE,
            EditorSyncPolicy.decide(
                savedBefore = "old source",
                savedAfter = null,
                draftBefore = "user draft",
                draftNow = "user draft"
            )
        )
    }

    @Test
    fun leavesUnchangedFileAlone() {
        assertEquals(
            EditorSyncPolicy.Action.NO_CHANGE,
            EditorSyncPolicy.decide(
                savedBefore = "same",
                savedAfter = "same",
                draftBefore = "same",
                draftNow = "same"
            )
        )
    }
}
