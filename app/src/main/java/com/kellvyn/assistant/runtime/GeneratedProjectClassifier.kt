package com.kellvyn.assistant.runtime

enum class GeneratedProjectKind(val label: String, val glyph: String) {
    ANDROID_APP("App Android", "📱"),
    DISCORD_BOT("Bot do Discord", "🤖"),
    WHATSAPP_BOT("Bot do WhatsApp", "💬"),
    TELEGRAM_BOT("Bot do Telegram", "✈️"),
    SOURCE_PROJECT("Projeto / ferramenta", "🧩")
}

/** Detects the actual artifact from its saved files rather than the project's display name alone. */
object GeneratedProjectClassifier {
    fun classify(paths: Set<String>, packageJson: String = "", description: String = "", name: String = ""): GeneratedProjectKind {
        val normalizedPaths = paths.map { it.replace('\\', '/').lowercase() }.toSet()
        val signals = (packageJson + "\n" + description + "\n" + name + "\n" + normalizedPaths.joinToString("\n"))
            .lowercase()
        if ("app/src/main/java/com/example/app/mainactivity.java" in normalizedPaths ||
            "app/src/main/androidmanifest.xml" in normalizedPaths
        ) return GeneratedProjectKind.ANDROID_APP
        if ("package.json" in normalizedPaths && listOf("discord.js", "discord bot", "discord").any { it in signals }) {
            return GeneratedProjectKind.DISCORD_BOT
        }
        if ("package.json" in normalizedPaths && listOf("whatsapp", "whats app", "meta graph api").any { it in signals }) {
            return GeneratedProjectKind.WHATSAPP_BOT
        }
        if ("package.json" in normalizedPaths && "telegram" in signals) return GeneratedProjectKind.TELEGRAM_BOT
        if ("package.json" in normalizedPaths) return GeneratedProjectKind.SOURCE_PROJECT
        return if (normalizedPaths.any { it.endsWith(".java") || it.endsWith(".kt") }) {
            GeneratedProjectKind.ANDROID_APP
        } else {
            GeneratedProjectKind.SOURCE_PROJECT
        }
    }
}
