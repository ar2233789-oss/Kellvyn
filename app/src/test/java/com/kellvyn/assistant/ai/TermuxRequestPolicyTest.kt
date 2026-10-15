package com.kellvyn.assistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxRequestPolicyTest {
    @Test
    fun explicitHelpOrExecutionRequestAllowsTermuxTools() {
        assertTrue(TermuxRequestPolicy.mayUseTermux("Me ajuda a diagnosticar esse erro no Termux"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Use o Termux para executar esse comando"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Verifique no Termux por que o pacote não instala"))
    }

    @Test
    fun merelyMentioningTermuxDoesNotAuthorizeExecution() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("O que é Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Como instalar o Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me mostre comandos que existem no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda com esse erro"))
    }
}
