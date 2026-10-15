package com.kellvyn.assistant.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCatchingSuspendTest {
    @Test
    fun cancellationIsRethrownInsteadOfConvertedToFailure() = runBlocking {
        val cancellation = CancellationException("cancel current request")
        var caught: CancellationException? = null

        try {
            runCatchingSuspend<String> { throw cancellation }
        } catch (error: CancellationException) {
            caught = error
        }

        assertSame("Cancellation must preserve the original signal.", cancellation, caught)
    }

    @Test
    fun ordinaryErrorsAreReturnedAsFailures() = runBlocking {
        val result = runCatchingSuspend<String> {
            throw IllegalStateException("expected tool failure")
        }

        assertTrue(result.isFailure)
        assertEquals("expected tool failure", result.exceptionOrNull()?.message)
    }
}
