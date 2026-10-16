package com.kellvyn.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOutputSanitizerTest {

    @Test
    fun hidesAnOpenThinkingBlockWhileTokensAreStillStreaming() {
        assertEquals("", sanitizeModelOutput("<think>private reasoning still being generated"))
    }

    @Test
    fun keepsOnlyTheFinalAnswerAfterACompleteThinkingBlock() {
        assertEquals(
            "Final answer.",
            sanitizeModelOutput("<think>private reasoning</think>Final answer.")
        )
    }

    @Test
    fun removesStandaloneInternalAnalysisLines() {
        assertEquals(
            "The answer is 42.",
            sanitizeModelOutput("analysis: private notes\nThe answer is 42.")
        )
    }

    @Test
    fun removesProtocolTokensWithoutBreakingUserFacingText() {
        val output = sanitizeModelOutput("Hello <|im_end|>")
        assertTrue(output.startsWith("Hello"))
        assertTrue(!output.contains("<|"))
    }
}
