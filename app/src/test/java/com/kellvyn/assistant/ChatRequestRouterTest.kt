package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestRouterTest {

    @Test
    fun explicitCreationStartsBuildFromTheMainChat() {
        assertTrue(ChatRequestRouter.shouldBuild("Faz minha IA"))
        assertTrue(ChatRequestRouter.shouldBuild("Crie um app Android com login"))
        assertTrue(ChatRequestRouter.shouldBuild("Quero um sistema de estoque com cadastro e busca"))
        assertTrue(ChatRequestRouter.shouldBuild("Quero uma IA que crie aplicativos"))
    }

    @Test
    fun questionsAndAnalysisStayInOrdinaryChat() {
        assertFalse(ChatRequestRouter.shouldBuild("Pode continuar"))
        assertFalse(ChatRequestRouter.shouldBuild("Como criar um app Android?"))
        assertFalse(ChatRequestRouter.shouldBuild("Você consegue criar um app?"))
        assertFalse(ChatRequestRouter.shouldBuild("Faça uma análise da minha IA"))
        assertFalse(ChatRequestRouter.shouldBuild("Faz uma análise da minha IA"))
        assertFalse(ChatRequestRouter.shouldBuild("Analise meu app"))
    }

    @Test
    fun botCreationRoutesToBuilderButCapabilityQuestionsStayInChat() {
        assertTrue(ChatRequestRouter.shouldBuild("Crie um bot do Discord com comando ping"))
        assertTrue(ChatRequestRouter.shouldBuild("Quero um bot para WhatsApp"))
        assertFalse(ChatRequestRouter.shouldBuild("Ela gera bots no Discord? Consegue gerar bot de WhatsApp?"))
    }

    @Test
    fun terminalRunsOnlyAfterAnExplicitRequestInTheCurrentMessage() {
        assertTrue(ChatRequestRouter.shouldUseTerminal("terminal: ls"))
        assertTrue(ChatRequestRouter.shouldUseTerminal("Execute no terminal pwd"))
        assertTrue(ChatRequestRouter.shouldUseTerminal("Use o terminal para executar git status"))
        assertFalse(ChatRequestRouter.shouldUseTerminal("Como funciona um terminal Android?"))
        assertFalse(ChatRequestRouter.shouldUseTerminal("Bom dia"))
    }

    @Test
    fun modifyingAnExistingProjectDoesNotCreateAnotherProject() {
        assertFalse(ChatRequestRouter.shouldBuild("Corrija meu app existente"))
        assertFalse(ChatRequestRouter.shouldBuild("Adicione login ao projeto que você criou"))
    }

    @Test
    fun explicitApkBuildRoutesToExistingProjectInsteadOfCreatingANewOne() {
        assertTrue(ChatRequestRouter.shouldCompileExistingProject("Compile o projeto"))
        assertTrue(ChatRequestRouter.shouldCompileExistingProject("Gere o APK desse projeto"))
        assertTrue(ChatRequestRouter.shouldCompileExistingProject("Gerar APK do meu app"))
        assertTrue(ChatRequestRouter.shouldCompileExistingProject("Compile o projeto que você criou"))
        assertFalse(ChatRequestRouter.shouldCompileExistingProject("Como gerar o APK desse projeto?"))
        assertFalse(ChatRequestRouter.shouldCompileExistingProject("Crie um novo app Android com login"))
    }

    @Test
    fun explicitOnlineRequestsUseWebToolsWithoutMakingOrdinaryChatOnline() {
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Pesquise na internet sobre Kotlin coroutines"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Pesquise no GitHub o projeto"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Resuma este link https://developer.android.com"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Qual é a previsão do tempo?"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Explique o que é Kotlin"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Bom dia"))
    }

    @Test
    fun installedCoderIsPreferredForCodeWorkButNotOrdinaryQuestions() {
        assertTrue(ChatRequestRouter.shouldPreferCoder("Corrija o arquivo MainActivity.kt"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Adicione login ao meu app existente"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Continue o projeto que você criou"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Finaliza essa IA"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Conclua a IA"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Termina tudo que tem fazer"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Termine tudo que falta"))
        assertTrue(ChatRequestRouter.shouldPreferCoder("Conclua tudo que falta no projeto"))
        assertFalse(ChatRequestRouter.shouldPreferCoder("Finaliza essa conta"))
        assertFalse(ChatRequestRouter.shouldPreferCoder("Explique o que é Kotlin"))
        assertFalse(ChatRequestRouter.shouldPreferCoder("Pesquise na internet sobre Kotlin coroutines"))
        assertFalse(ChatRequestRouter.shouldPreferCoder("Bom dia"))
    }

    @Test
    fun detailedAppBriefUsesTheHiddenBuilderAutomatically() {
        val brief = "Quero um gerenciador de tarefas com tela de login, cadastro de usuários, categorias e busca."
        assertTrue(ChatRequestRouter.shouldBuild(brief))
        assertTrue(ChatRequestRouter.shouldBuild(brief))
        assertFalse(ChatRequestRouter.shouldBuild("Como funciona um app com login e cadastro?"))
        assertFalse(ChatRequestRouter.shouldBuild("Adicione login ao meu app existente"))
    }
}
