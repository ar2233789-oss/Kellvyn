package com.kellvyn.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores bounded chat history only in this app's private preferences.
 * The manifest disables Android backup for the app.
 */
class ChatHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("kellvyn_chat_history", Context.MODE_PRIVATE)

    data class SavedMessage(val text: String, val fromUser: Boolean)

    fun load(): List<SavedMessage> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                val start = (array.length() - MAX_MESSAGES).coerceAtLeast(0)
                for (i in start until array.length()) {
                    val item = array.getJSONObject(i)
                    val text = item.optString("text")
                    if (text.isNotBlank()) {
                        add(SavedMessage(text.take(MAX_MESSAGE_CHARS), item.optBoolean("fromUser")))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<SavedMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            array.put(
                JSONObject()
                    .put("text", message.text.take(MAX_MESSAGE_CHARS))
                    .put("fromUser", message.fromUser)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "messages"
        const val MAX_MESSAGES = 200
        const val MAX_MESSAGE_CHARS = 12_000
    }
}
