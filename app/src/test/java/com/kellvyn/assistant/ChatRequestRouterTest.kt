package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestRouterTest {

    @Test
    fun explicitCreationStartsBuildFromTheMainChat() {
        assertTrue(ChatRequestRouter.shouldBuild("Faz minha IA", buildModeSelected = false))
        assertTrue(ChatRequestRouter.shouldBuild("Crie um app Android com login", buildModeSelected = false))
        assertTrue(ChatRequestRouter.shouldBuild("Quero um sistema de estoque com cadastro e busca", buildModeSelected = false))
        assertTrue(ChatRequestRouter.shouldBuild("Quero uma IA que crie aplicativos", buildModeSelected = false))
    }

    @Test
    fun questionsAndAnalysisStayInOrdinaryChat() {
        assertFalse(ChatRequestRouter.shouldBuild("Pode continuar", buildModeSelected = false))
        assertFalse(ChatRequestRouter.shouldBuild("Como criar um app Android?", buildModeSelected = false))
        assertFalse(ChatRequestRouter.shouldBuild("Você consegue criar um app?", buildModeSelected = false))
        assertFalse(ChatRequestRouter.shouldBuild("Faça uma análise da minha IA", buildModeSelected = false))
        assertFalse(ChatRequestRouter.shouldBuild("Faz uma análise da minha IA", buildModeSelected = false))
        assertFalse(ChatRequestRouter.shouldBuild("Analise meu app", buildModeSelected = false))
    }

    @Test
    fun modifyingAnExistingProjectDoesNotCreateAnotherProject() {
        assertFalse(ChatRequestRouter.shouldBuild("Corrija meu app existente", buildModeSelected = false))
        assertFalse(ChatRequestRouter.shouldBuild("Adicione login ao projeto que você criou", buildModeSelected = true))
    }

    @Test
    fun detailedBriefUsesBuildOnlyWhenBuildModeIsSelected() {
        val brief = "Quero um gerenciador de tarefas com tela de login, cadastro de usuários, categorias e busca."
        assertFalse(ChatRequestRouter.shouldBuild(brief, buildModeSelected = false))
        assertTrue(ChatRequestRouter.shouldBuild(brief, buildModeSelected = true))
        assertFalse(ChatRequestRouter.shouldBuild("Como funciona um app com login e cadastro?", buildModeSelected = true))
    }
}
