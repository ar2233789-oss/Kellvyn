package com.kellvyn.assistant.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedProjectClassifierTest {
    @Test
    fun recognizesNativeAndroidAppsFromTheirManifestAndMainActivity() {
        assertEquals(
            GeneratedProjectKind.ANDROID_APP,
            GeneratedProjectClassifier.classify(
                setOf("app/src/main/AndroidManifest.xml", "app/src/main/java/com/example/app/MainActivity.java")
            )
        )
    }

    @Test
    fun recognizesDiscordBotFromItsNodeDependencyEvenWhenNamedCreatively() {
        assertEquals(
            GeneratedProjectKind.DISCORD_BOT,
            GeneratedProjectClassifier.classify(
                setOf("package.json", "src/index.js", "kellvyn.bot.json"),
                """{"dependencies":{"discord.js":"^14.0.0"}}""",
                "Moderation helper",
                "Night Guardian"
            )
        )
    }

    @Test
    fun distinguishesOtherBotPlatformsAndGenericSourceProjects() {
        assertEquals(
            GeneratedProjectKind.WHATSAPP_BOT,
            GeneratedProjectClassifier.classify(
                setOf("package.json", "src/index.js"),
                """{"description":"WhatsApp Business Cloud API bot"}"""
            )
        )
        assertEquals(
            GeneratedProjectKind.TELEGRAM_BOT,
            GeneratedProjectClassifier.classify(
                setOf("package.json", "src/index.js"),
                """{"description":"Telegram notification bot"}"""
            )
        )
        assertEquals(
            GeneratedProjectKind.SOURCE_PROJECT,
            GeneratedProjectClassifier.classify(setOf("package.json", "src/index.js"), """{"name":"utility"}""")
        )
    }
}
