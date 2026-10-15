package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestRouterTest {

    @Test
    fun directImageRequestsUseTheLocalImageGeneratorButAnalysisStaysInChat() {
        assertTrue(ChatRequestRouter.shouldGenerateImage("Gere uma imagem de um dragão verde em uma cidade futurista"))
        assertTrue(ChatRequestRouter.shouldGenerateImage("Crie uma ilustração de um necromante"))
        assertTrue(ChatRequestRouter.shouldGenerateImage("Faz uma imagem de um gato preto"))
        assertTrue(ChatRequestRouter.shouldGenerateImage("Gera uma foto de uma floresta neon"))
        assertTrue(ChatRequestRouter.shouldGenerateImage("Desenha uma imagem de um castelo"))
        assertFalse(ChatRequestRouter.shouldGenerateImage("Analise esta imagem"))
        assertFalse(ChatRequestRouter.shouldGenerateImage("Como funcionam os geradores de imagem?"))
        assertFalse(ChatRequestRouter.shouldGenerateImage("Descreva uma imagem de exemplo"))
    }

    @Test
    fun explicitCreationStartsBuildFromTheMainChat() {
        assertTrue(ChatRequestRouter.shouldBuild("Faz minha IA"))
        assertTrue(ChatRequestRouter.shouldBuild("Crie um app Android com login"))
        assertTrue(ChatRequestRouter.shouldBuild("Quero um sistema de estoque com cadastro e busca"))
        assertTrue(ChatRequestRouter.shouldBuild("Quero uma IA que crie aplicativos"))
    }

    @Test
    fun mentioningATerminalOrConsoleAsAnAppFeatureDoesNotBlockBuilderRouting() {
        val brief = "Um aplicativo de gerenciamento com tela de login, cadastro de usuários, banco de dados, botões para salvar e busca, além de um console de logs."
        assertTrue(ChatRequestRouter.shouldBuild(brief))
        val terminalBrief = "Um aplicativo de gerenciamento com tela de login, cadastro de usuários, banco de dados, botões para salvar e busca, além de um terminal de logs."
        assertTrue(ChatRequestRouter.shouldBuild(terminalBrief))
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
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Pesquise online sobre Kotlin coroutines"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Pesquise sobre a versão mais recente do Android"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Importe este repositório https://github.com/example/demo"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Pesquise no GitHub o projeto"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Resuma este link https://developer.android.com"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Qual é a previsão do tempo?"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Explique o que é Kotlin"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Bom dia"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Explique o que é acesso à internet no Android"))
    }

    @Test
    fun externalAndCurrentFactsEnableNetworkToolsWithoutExactSearchWording() {
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Quem é o CEO da NVIDIA?"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Quanto custa o Poco F6 atualmente?"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Quais restaurantes são bons perto de mim?"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Qual é a versão atual do Android?"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Me explica as regras atuais do benefício BPC"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Quais são as diferenças entre estes dois produtos?"))
    }

    @Test
    fun substantialUnclassifiedRequestsCanUseNetworkToolsForAgentDecisions() {
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Quem foi Napoleão Bonaparte?"))
        assertTrue(ChatRequestRouter.shouldUseOnlineTools("Me ajude a entender este assunto específico"))
    }

    @Test
    fun explicitOfflinePreferenceAndClearlyLocalWorkRemainLocal() {
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Não pesquise na internet; responda só com o que já sabe"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Não use a internet para esta resposta"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Corrija o arquivo MainActivity.kt"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Adicione login ao meu app existente"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Compile o projeto que você criou"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Bom dia"))
        assertFalse(ChatRequestRouter.shouldUseOnlineTools("Explique o que é Kotlin"))
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
