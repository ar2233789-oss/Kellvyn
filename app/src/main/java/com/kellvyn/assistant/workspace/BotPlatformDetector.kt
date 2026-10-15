package com.kellvyn.assistant.workspace

import java.util.Locale

/**
 * Identifies the primary platform requested for a generated bot.
 *
 * The first explicit supported platform mentioned wins. This prevents a later reference
 * such as "not Telegram" or "do not use Discord" from accidentally selecting a different runtime.
 */
internal object BotPlatformDetector {
    private val platformPattern = Regex("""(?<![a-z0-9])(?:discord|telegram|whats\s*app|wpp)(?![a-z0-9])""")

    fun primaryPlatform(brief: String): String? =
        platformPattern.find(brief.lowercase(Locale.ROOT))?.value?.replace(Regex("\\s+"), "")

    fun isDiscord(brief: String): Boolean = primaryPlatform(brief) == "discord"
    fun isTelegram(brief: String): Boolean = primaryPlatform(brief) == "telegram"
}
