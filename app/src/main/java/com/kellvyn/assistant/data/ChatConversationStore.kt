package com.kellvyn.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatHistoryStore.SavedMessage>
)

/**
 * Stores separate chat conversations on-device. No conversation is sent to an online service.
 */
class ChatConversationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "kellvyn_conversations",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): List<SavedConversation> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isBlank()) continue
                    val messagesJson = item.optJSONArray("messages") ?: JSONArray()
                    val messages = buildList {
                        val start = (messagesJson.length() - MAX_MESSAGES_PER_CONVERSATION).coerceAtLeast(0)
                        for (j in start until messagesJson.length()) {
                            val message = messagesJson.optJSONObject(j) ?: continue
                            val text = message.optString("text")
                            if (text.isNotBlank()) {
                                add(
                                    ChatHistoryStore.SavedMessage(
                                        text = text.take(MAX_MESSAGE_CHARS),
                                        fromUser = message.optBoolean("fromUser")
                                    )
                                )
                            }
                        }
                    }
                    add(
                        SavedConversation(
                            id = id,
                            title = item.optString("title").ifBlank { "Nova conversa" }.take(MAX_TITLE_CHARS),
                            updatedAt = item.optLong("updatedAt", 0L),
                            messages = messages
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(conversation: SavedConversation) {
        val updated = (load().filterNot { it.id == conversation.id } + conversation)
            .sortedByDescending { it.updatedAt }
            .take(MAX_CONVERSATIONS)
        persist(updated)
    }

    @Synchronized
    fun delete(id: String) {
        persist(load().filterNot { it.id == id })
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun persist(conversations: List<SavedConversation>) {
        val array = JSONArray()
        conversations.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS).forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.takeLast(MAX_MESSAGES_PER_CONVERSATION).forEach { message ->
                messages.put(
                    JSONObject()
                        .put("text", message.text.take(MAX_MESSAGE_CHARS))
                        .put("fromUser", message.fromUser)
                )
            }
            array.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("title", conversation.title.take(MAX_TITLE_CHARS))
                    .put("updatedAt", conversation.updatedAt)
                    .put("messages", messages)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "saved_conversations"
        const val MAX_CONVERSATIONS = 30
        const val MAX_MESSAGES_PER_CONVERSATION = 120
        const val MAX_MESSAGE_CHARS = 12_000
        const val MAX_TITLE_CHARS = 48
    }
}
