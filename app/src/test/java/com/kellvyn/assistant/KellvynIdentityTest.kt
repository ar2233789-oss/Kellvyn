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
        assertTrue(KellvynIdentity.isIdentityQuestion("Você é da OpenAI?"))
        assertTrue(KellvynIdentity.isIdentityQuestion("Você é o Qwen?"))
    }

    @Test
    fun answerCreditsKellvynAndSeparatesModelFromCreator() {
        val answer = KellvynIdentity.answer().lowercase()
        assertTrue(answer.contains("criado por kellvyn"))
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
