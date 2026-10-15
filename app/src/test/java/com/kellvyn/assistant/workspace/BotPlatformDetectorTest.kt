package com.kellvyn.assistant.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPlatformDetectorTest {
    @Test
    fun detectsDiscordWhenItIsTheFirstRequestedPlatform() {
        assertTrue(BotPlatformDetector.isDiscord("Crie um bot para Discord e publique avisos no Telegram"))
    }

    @Test
    fun doesNotMisclassifyTelegramWhenDiscordIsMentionedLater() {
        assertFalse(BotPlatformDetector.isDiscord("Crie um bot para Telegram, não para Discord"))
    }

    @Test
    fun detectsTelegramAsPrimaryPlatform() {
        assertTrue(BotPlatformDetector.isTelegram("Crie um bot para Telegram"))
        assertFalse(BotPlatformDetector.isTelegram("Crie um bot para Discord e também avise no Telegram"))
        assertFalse(BotPlatformDetector.isTelegram("Crie um bot para WhatsApp, não Telegram"))
    }

    @Test
    fun detectsWhatsAppAsASeparatePlatform() {
        assertFalse(BotPlatformDetector.isDiscord("Quero um bot para WhatsApp Business"))
        assertFalse(BotPlatformDetector.isDiscord("Crie um bot para WPP"))
    }

    @Test
    fun detectsDiscordVariations() {
        assertTrue(BotPlatformDetector.isDiscord("Faça meu bot Discord"))
        assertTrue(BotPlatformDetector.isDiscord("Quero um Discord bot"))
    }

    @Test
    fun noPlatformDoesNotAccidentallyEnableDiscordRuntime() {
        assertFalse(BotPlatformDetector.isDiscord("Crie um bot que gere relatórios em arquivos"))
    }
}
