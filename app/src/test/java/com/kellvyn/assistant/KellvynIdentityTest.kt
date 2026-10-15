package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KellvynIdentityTest {
    @Test
    fun recognizesCreatorQuestionsWithAndWithoutAccents() {
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem te criou?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem criou você?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem é seu criador?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Kellvyn criou você?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Você foi criada por quem?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem desenvolveu a Kellvyn?"))
    }

    @Test
    fun recognizesIdentityAndModelAttributionQuestions() {
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem é você?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Qual é seu nome?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Qual modelo está instalado?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Qual tecnologia você usa?"))
    }

    @Test
    fun recognizesDeusDaRitalinaAsDeveloperTutorAndSuperior() {
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem é Deus da Ritalina?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem é seu desenvolvedor?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem é seu tutor?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Quem é seu superior?"))

        val answer = KellvynIdentity.answer().lowercase()
        assertTrue(answer.contains("kellvyn é meu criador"))
        assertTrue(answer.contains("deus da ritalina é meu desenvolvedor, tutor e superior"))
    }

    @Test
    fun answerSeparatesTheLocalModelFromTheCreators() {
        val answer = KellvynIdentity.answer().lowercase()
        assertTrue(answer.contains("modelo de linguagem"))
        assertTrue(answer.contains("não o criador do projeto"))
    }

    @Test
    fun ordinaryRequestsStayInTheModelConversation() {
        assertFalse(KellvynIdentity.isIdentityQuestion("Explique como funciona o Kotlin"))
        assertFalse(KellvynIdentity.isIdentityQuestion("Crie um aplicativo Android"))
        assertFalse(KellvynIdentity.isIdentityQuestion("Como melhorar a memória do app?"))
    }
}
