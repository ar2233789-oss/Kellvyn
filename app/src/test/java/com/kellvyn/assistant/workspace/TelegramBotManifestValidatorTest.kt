package com.kellvyn.assistant.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramBotManifestValidatorTest {
    @Test
    fun acceptsAValidTelegramManifest() {
        val content = """{"platform":"telegram","name":"Helper","commands":[{"name":"start","description":"Iniciar","action":"response","response":"Olá, {user}!"},{"name":"ping","description":"Testar conexão","action":"ping","response":""}]}"""
        assertNull(TelegramBotManifestValidator.validate(content))
    }

    @Test
    fun rejectsNonTelegramPlatform() {
        val content = """{"platform":"discord","commands":[{"name":"start","description":"Start","action":"ping"}]}"""
        assertEquals("platform precisa ser telegram.", TelegramBotManifestValidator.validate(content))
    }

    @Test
    fun rejectsInvalidNamesDuplicateNamesAndUnsupportedActions() {
        val invalidName = """{"platform":"telegram","commands":[{"name":"Bad-Name","description":"Teste","action":"ping"}]}"""
        assertEquals("nome de comando Telegram inválido: bad-name", TelegramBotManifestValidator.validate(invalidName))
        val duplicates = """{"platform":"telegram","commands":[{"name":"ping","description":"Teste","action":"ping"},{"name":"ping","description":"Outro","action":"ping"}]}"""
        assertEquals("há nomes de comandos repetidos.", TelegramBotManifestValidator.validate(duplicates))
        val unsupported = """{"platform":"telegram","commands":[{"name":"ping","description":"Teste","action":"exec"}]}"""
        assertEquals("ação não suportada em /ping: exec", TelegramBotManifestValidator.validate(unsupported))
    }

    @Test
    fun rejectsMissingResponseForResponseAction() {
        val content = """{"platform":"telegram","commands":[{"name":"start","description":"Iniciar","action":"response","response":"  "}]}"""
        assertEquals("a resposta de /start está vazia ou longa demais.", TelegramBotManifestValidator.validate(content))
    }
}
