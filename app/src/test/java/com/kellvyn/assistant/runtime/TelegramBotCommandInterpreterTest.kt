package com.kellvyn.assistant.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramBotCommandInterpreterTest {
    private fun command(name: String, action: String, response: String = "") =
        TelegramBotCommand(name, "Descrição de teste", response, action)

    @Test
    fun parsesCommandsWithOptionalBotUsername() {
        assertEquals("ping", TelegramBotCommandInterpreter.commandName("/ping"))
        assertEquals("ping", TelegramBotCommandInterpreter.commandName("/ping@KellvynHelperBot"))
        assertEquals("echo", TelegramBotCommandInterpreter.commandName("  /echo texto livre"))
    }

    @Test
    fun ignoresNonCommandsAndMalformedNames() {
        assertNull(TelegramBotCommandInterpreter.commandName("Olá, bot"))
        assertNull(TelegramBotCommandInterpreter.commandName("/Bad-Name"))
        assertNull(TelegramBotCommandInterpreter.commandName("/"))
    }

    @Test
    fun handlesBasicCommandsAndUserName() {
        assertEquals(
            "Pong! Estou ativo.",
            TelegramBotCommandInterpreter.respond(command("ping", "ping"), "/ping", "Ana", "14/10/2026")
        )
        assertEquals(
            "Você está falando comigo, Ana.",
            TelegramBotCommandInterpreter.respond(command("nome", "user_name"), "/nome", "Ana", "14/10/2026")
        )
    }

    @Test
    fun expandsPlaceholdersInConfiguredReplies() {
        assertEquals(
            "Olá, Ana! 14/10/2026",
            TelegramBotCommandInterpreter.respond(
                command("start", "response", "Olá, {user}! {time}"),
                "/start",
                "Ana",
                "14/10/2026"
            )
        )
    }

    @Test
    fun echoReturnsOnlyThePayloadAfterTheCommand() {
        assertEquals(
            "texto de teste",
            TelegramBotCommandInterpreter.respond(command("echo", "echo"), "/echo texto de teste", "Ana", "agora")
        )
    }
}
