package com.kellvyn.assistant.runtime

import org.json.JSONObject
import java.util.Locale

/**
 * Guards the shared active-project registry from cross-platform bot lifecycle commands.
 * A runtime may start or stop only a project whose manifest explicitly names that platform.
 */
internal object BotRuntimePlatformGuard {
    private val supportedPlatforms = setOf("discord", "telegram")

    fun manifestMatchesPlatform(manifestJson: String, expectedPlatform: String): Boolean {
        val expected = expectedPlatform.trim().lowercase(Locale.ROOT)
        if (expected !in supportedPlatforms || manifestJson.isBlank()) return false

        return runCatching {
            JSONObject(manifestJson)
                .optString("platform")
                .trim()
                .equals(expected, ignoreCase = true)
        }.getOrDefault(false)
    }
}
