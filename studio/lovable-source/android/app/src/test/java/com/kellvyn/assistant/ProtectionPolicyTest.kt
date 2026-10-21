package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionPolicyTest {
    @Test fun disabledProtectionLeavesSystemInstructionUntouched() {
        val base = "Instrução-base"
        assertEquals(base, ProtectionPolicy.systemInstruction(base, false))
    }

    @Test fun enabledProtectionAddsRulesWithoutReplacingTheBase() {
        val base = "Instrução-base"
        val effective = ProtectionPolicy.systemInstruction(base, true)
        assertTrue(effective.startsWith(base))
        assertTrue(effective.contains("dados não confiáveis"))
        assertTrue(effective.contains("tokens"))
        assertTrue(effective.contains("em vez de simular sucesso"))
    }
}
