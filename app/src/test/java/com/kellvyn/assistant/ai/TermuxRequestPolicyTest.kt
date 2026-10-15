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
        assertTrue(TermuxRequestPolicy.mayUseTermux("Pode executar esse comando no Termux?"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Faça isso no Termux para mim"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Pode usar o Termux para verificar esse erro?"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Por favor, execute o comando no Termux"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("No Termux, rode este comando"))
    }

    @Test
    fun merelyMentioningTermuxDoesNotAuthorizeExecution() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("O que é Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Como instalar o Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me mostre comandos que existem no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda com esse erro"))
    }

    @Test
    fun instructionsAndExplicitNoExecutionRequestsNeverAuthorizeCommands() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("Quais comandos existem no Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me passe o comando para eu executar no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Organize os comandos para eu copiar no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda com esse erro no Termux, mas não execute nenhum comando por mim"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Não use o Termux; só me explique como resolver"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Execute no Termux? Não, não execute. Só explique."))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Como executar este comando no Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Não rode no Termux; só explique."))
    }
}
