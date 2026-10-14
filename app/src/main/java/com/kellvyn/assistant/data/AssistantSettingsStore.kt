package com.kellvyn.assistant.data

import android.content.Context

enum class KellvynAccent(val colorArgb: Long, val displayName: String) {
    NEON_GREEN(0xFF38E6BC, "Verde neon"),
    ELECTRIC_BLUE(0xFF42A5FF, "Azul elétrico"),
    VIOLET(0xFFB388FF, "Violeta"),
    CRIMSON(0xFFFF5D73, "Carmesim"),
    AMBER(0xFFFFC857, "Âmbar")
}

data class AssistantUiSettings(
    val onlineEnabled: Boolean = false,
    val accent: KellvynAccent = KellvynAccent.NEON_GREEN,
    val startupRain: Boolean = false,
    val proactiveSafety: Boolean = true
)

/** Local-only settings for network mode and appearance; never stores provider credentials. */
class AssistantSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "kellvyn_assistant_settings",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): AssistantUiSettings {
        val accentName = preferences.getString(KEY_ACCENT, KellvynAccent.NEON_GREEN.name)
        return AssistantUiSettings(
            onlineEnabled = preferences.getBoolean(KEY_ONLINE, false),
            accent = runCatching { KellvynAccent.valueOf(accentName.orEmpty()) }
                .getOrDefault(KellvynAccent.NEON_GREEN),
            startupRain = preferences.getBoolean(KEY_RAIN, false),
            proactiveSafety = preferences.getBoolean(KEY_SAFETY, true)
        )
    }

    @Synchronized
    fun setOnlineEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_ONLINE, enabled).apply() }

    @Synchronized
    fun setAccent(accent: KellvynAccent) { preferences.edit().putString(KEY_ACCENT, accent.name).apply() }

    @Synchronized
    fun setStartupRain(enabled: Boolean) { preferences.edit().putBoolean(KEY_RAIN, enabled).apply() }

    @Synchronized
    fun setProactiveSafety(enabled: Boolean) { preferences.edit().putBoolean(KEY_SAFETY, enabled).apply() }

    private companion object {
        const val KEY_ONLINE = "online_enabled"
        const val KEY_ACCENT = "accent"
        const val KEY_RAIN = "startup_rain"
        const val KEY_SAFETY = "proactive_safety"
    }
}
