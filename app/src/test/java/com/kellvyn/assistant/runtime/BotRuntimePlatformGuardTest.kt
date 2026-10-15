package com.kellvyn.assistant.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotRuntimePlatformGuardTest {
    private val discordManifest = """{"platform":"discord","commands":[{"name":"ping"}]}"""
    private val telegramManifest = """{"platform":"telegram","commands":[{"name":"start"}]}"""

    @Test
    fun discordRuntimeOwnsOnlyDiscordManifests() {
        assertTrue(BotRuntimePlatformGuard.manifestMatchesPlatform(discordManifest, "discord"))
        assertFalse(BotRuntimePlatformGuard.manifestMatchesPlatform(telegramManifest, "discord"))
    }

    @Test
    fun telegramRuntimeOwnsOnlyTelegramManifests() {
        assertTrue(BotRuntimePlatformGuard.manifestMatchesPlatform(telegramManifest, "telegram"))
        assertFalse(BotRuntimePlatformGuard.manifestMatchesPlatform(discordManifest, "telegram"))
    }

    @Test
    fun invalidManifestsAndUnknownPlatformsNeverGrantOwnership() {
        assertFalse(BotRuntimePlatformGuard.manifestMatchesPlatform("", "discord"))
        assertFalse(BotRuntimePlatformGuard.manifestMatchesPlatform("not json", "telegram"))
        assertFalse(BotRuntimePlatformGuard.manifestMatchesPlatform("""{"commands":[]}""", "discord"))
        assertFalse(BotRuntimePlatformGuard.manifestMatchesPlatform(discordManifest, "whatsapp"))
    }
}
