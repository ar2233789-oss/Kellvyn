package com.kellvyn.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLookupPolicyTest {
    @Test fun recognizesQuestionsWithoutQuestionMark() {
        assertTrue(WebLookupPolicy.isLookupRequest("Quem é o presidente atual do Brasil"))
        assertTrue(WebLookupPolicy.isLookupRequest("Como funciona o Gemma 4 E2B"))
    }

    @Test fun recognizesDirectWebSearchRequests() {
        assertTrue(WebLookupPolicy.isLookupRequest("Pesquise sobre o LiteRT-LM"))
        assertTrue(WebLookupPolicy.isLookupRequest("Procure na web as notícias de hoje"))
        assertTrue(WebLookupPolicy.isLookupRequest("Leia esta página https://example.com"))
        assertTrue(WebLookupPolicy.isLookupRequest("https://example.com"))
    }

    @Test fun keepsProjectCreationRequestsOutOfWebLookup() {
        assertFalse(WebLookupPolicy.isLookupRequest("Crie um aplicativo para organizar arquivos"))
        assertFalse(WebLookupPolicy.isLookupRequest("Gere um bot para Discord"))
        assertFalse(WebLookupPolicy.isLookupRequest("Faça um jogo simples"))
    }

    @Test fun ignoresBlankLongAndUnrelatedStatements() {
        assertFalse(WebLookupPolicy.isLookupRequest("   "))
        assertFalse(WebLookupPolicy.isLookupRequest("A chuva está caindo"))
        assertFalse(WebLookupPolicy.isLookupRequest("x".repeat(301)))
    }
}
