package com.kellvyn.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores bounded chat history only in this app's private preferences.
 * The manifest disables Android backup for the app.
 */
class ChatHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kellvyn_chat_history", Context.MODE_PRIVATE)

    data class SavedMessage(val text: String, val fromUser: Boolean, val imagePath: String? = null)

    private fun validImagePath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val root = File(appContext.filesDir, "kellvyn_attachments").canonicalFile
            val image = File(path).canonicalFile
            image.path.takeIf { image.isFile && image.length() > 0L && image.path.startsWith(root.path + File.separator) }
        }.getOrNull()
    }

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
                        add(SavedMessage(
                            text.take(MAX_MESSAGE_CHARS),
                            item.optBoolean("fromUser"),
                            validImagePath(item.optString("imagePath").takeIf { it.isNotBlank() })
                        ))
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
                    .put("imagePath", validImagePath(message.imagePath))
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
