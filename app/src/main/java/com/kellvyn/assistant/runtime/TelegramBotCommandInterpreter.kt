package com.kellvyn.assistant.runtime

import java.util.Locale

/** Pure parser/reply rules for Telegram commands; separated so the bot behavior can be unit-tested. */
internal object TelegramBotCommandInterpreter {
    fun commandName(messageText: String, botUsername: String? = null): String? {
        val text = messageText.trim()
        if (!text.startsWith("/")) return null
        val firstToken = text.takeWhile { !it.isWhitespace() }
        val rawCommand = firstToken.removePrefix("/")
        if (rawCommand.contains("@")) {
            val addressedUsername = rawCommand.substringAfter("@")
            if (!botUsername.isNullOrBlank() && !addressedUsername.equals(botUsername, ignoreCase = true)) return null
        }
        val name = rawCommand.substringBefore('@').lowercase(Locale.ROOT)
        return name.takeIf { it.matches(Regex("[a-z][a-z0-9_]{0,31}")) }
    }

    fun respond(command: TelegramBotCommand, messageText: String, userName: String, timeText: String): String =
        when (command.action) {
            "ping" -> "Pong! Estou ativo."
            "current_time" -> "Agora são " + timeText + "."
            "roll_dice" -> "🎲 Resultado: " + kotlin.random.Random.nextInt(1, 21)
            "coin_flip" -> "🪙 " + if (kotlin.random.Random.nextBoolean()) "Cara!" else "Coroa!"
            "user_name" -> "Você está falando comigo, " + userName + "."
            "echo" -> {
                val payload = messageText.trim().replace(Regex("^/\\S+"), "").trim()
                payload.ifBlank { "Você não enviou texto para repetir." }.take(3500)
            }
            else -> command.response.replace("{user}", userName).replace("{time}", timeText)
        }.take(3800)
}
