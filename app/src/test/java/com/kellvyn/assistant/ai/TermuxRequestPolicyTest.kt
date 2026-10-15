package com.kellvyn.assistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxRequestPolicyTest {
    @Test
    fun explicitTermuxHelpRequestedInTheMainChatCanUseTheConnection() {
        assertTrue(TermuxRequestPolicy.mayUseTermux("Me ajuda a diagnosticar esse erro no Termux"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Use o Termux para executar esse comando"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Verifique no Termux por que o pacote não instala"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Pode executar esse comando no Termux?"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Faça isso no Termux para mim"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Pode usar o Termux para verificar esse erro?"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Por favor, execute o comando no Termux"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("No Termux, rode este comando"))
        assertTrue(TermuxRequestPolicy.mayUseTermux("Quero ajuda no Termux"))
    }

    @Test
    fun genericSoftwareTasksNeverConnectToIndependentTermuxWithoutAnExplicitRequest() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("Execute o script install.sh e mostre o erro real"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Instale a dependência pip requests e rode os testes"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda a executar npm install e iniciar o servidor"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Inspecione a pasta com ls e leia os logs"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Rode os testes com pytest e corrija a falha"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Rode os testes do meu projeto"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Corrija esse bug no meu código e mostre o resultado real"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Meu programa não inicia; investigue a falha de execução"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda com esse erro no terminal"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Pode executar esse comando no shell?"))
    }

    @Test
    fun merelyMentioningTermuxOrAskingForInstructionsDoesNotConnect() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("O que é Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Como instalar o Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me mostre comandos que existem no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Quais comandos existem no Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me passe o comando para eu executar no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Organize os comandos para eu copiar no Termux"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Como executar este comando no Termux?"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda com esse erro"))
    }

    @Test
    fun explicitNoExecutionAlwaysBlocksTheTermuxConnection() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("Me ajuda com esse erro no Termux, mas não execute nenhum comando por mim"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Não use o Termux; só me explique como resolver"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Execute no Termux? Não, não execute. Só explique."))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Não rode no Termux; só explique."))
    }

    @Test
    fun androidApkBuildsStayOnTheIntegratedCompiler() {
        assertFalse(TermuxRequestPolicy.mayUseTermux("Crie um aplicativo Android e gere o APK"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Compile o projeto Android e corrija os arquivos"))
        assertFalse(TermuxRequestPolicy.mayUseTermux("Compile o projeto Android no Termux"))
    }
}
