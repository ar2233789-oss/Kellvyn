package com.kellvyn.assistant.workspace

import java.util.Locale
import org.json.JSONObject

/** Validates the bounded command manifest used by the on-device Telegram runtime. */
internal object TelegramBotManifestValidator {
    private val supportedActions = setOf(
        "response", "ping", "current_time", "roll_dice", "coin_flip", "user_name", "echo"
    )

    fun validate(content: String): String? {
        return try {
        val json = JSONObject(content)
        if (!json.optString("platform").equals("telegram", ignoreCase = true)) {
            return "platform precisa ser telegram."
        }
        val commands = json.optJSONArray("commands") ?: return "commands precisa ser uma lista."
        if (commands.length() !in 1..30) return "defina de 1 a 30 comandos."
        val names = ArrayList<String>()
        for (index in 0 until commands.length()) {
            val item = commands.optJSONObject(index) ?: return "o comando " + (index + 1) + " não é um objeto."
            val name = item.optString("name").trim().lowercase(Locale.ROOT)
            val description = item.optString("description").trim()
            val action = item.optString("action", "response").trim().lowercase(Locale.ROOT).ifBlank { "response" }
            val response = item.optString("response").trim()
            if (!name.matches(Regex("[a-z][a-z0-9_]{0,31}"))) return "nome de comando Telegram inválido: " + name
            if (description.isBlank() || description.length > 256) return "descrição inválida para /" + name
            if (action !in supportedActions) return "ação não suportada em /" + name + ": " + action
            if (action == "response" && (response.isBlank() || response.length > 3800)) {
                return "a resposta de /" + name + " está vazia ou longa demais."
            }
            names += name
        }
        if (names.distinct().size != names.size) "há nomes de comando repetidos." else null
        } catch (error: Exception) {
            "JSON inválido (" + (error.message ?: error.javaClass.simpleName) + ")."
        }
    }
}
