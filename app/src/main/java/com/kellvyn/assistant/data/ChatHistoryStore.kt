package com.kellvyn.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores chat messages only in this app's private preferences. */
class ChatHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("kellvyn_chat_history", Context.MODE_PRIVATE)

    data class SavedMessage(val text: String, val fromUser: Boolean)

    fun load(): List<SavedMessage> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(SavedMessage(item.getString("text"), item.getBoolean("fromUser")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<SavedMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(JSONObject().put("text", message.text).put("fromUser", message.fromUser))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "messages"
    }
}
