package com.kellvyn.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryFact(
    val id: String,
    val text: String,
    val createdAt: Long
)

/**
 * Explicit user-controlled assistant memory. Nothing is automatically extracted
 * from chat; users choose facts to save and can inspect/delete each fact.
 */
class UserMemoryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("kellvyn_user_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun list(): List<MemoryFact> {
        val raw = prefs.getString(KEY_FACTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val text = obj.optString("text").trim()
                    if (text.isNotBlank()) {
                        add(
                            MemoryFact(
                                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                text = text.take(MAX_FACT_CHARS),
                                createdAt = obj.optLong("createdAt", 0L)
                            )
                        )
                    }
                }
            }.takeLast(MAX_FACTS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(text: String): MemoryFact {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "A memória não pode ficar vazia." }
        require(normalized.length <= MAX_FACT_CHARS) {
            "Cada memória pode ter no máximo $MAX_FACT_CHARS caracteres."
        }
        val fact = MemoryFact(UUID.randomUUID().toString(), normalized, System.currentTimeMillis())
        val facts = (list() + fact).takeLast(MAX_FACTS)
        persist(facts)
        return fact
    }

    @Synchronized
    fun remove(id: String) {
        persist(list().filterNot { it.id == id })
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_FACTS).apply()
    }

    /** Context is assembled only from facts explicitly saved by the user. */
    fun promptContext(maxFacts: Int = 20): String {
        val facts = list().takeLast(maxFacts.coerceIn(1, MAX_FACTS))
        if (facts.isEmpty()) return ""
        return buildString {
            appendLine("Memórias explícitas salvas pelo usuário (podem estar desatualizadas; confirme quando houver dúvida):")
            facts.forEachIndexed { index, fact ->
                appendLine("${index + 1}. ${fact.text}")
            }
            appendLine("Use essas memórias apenas quando forem relevantes. Não invente informações além delas.")
        }
    }

    private fun persist(facts: List<MemoryFact>) {
        val array = JSONArray()
        facts.takeLast(MAX_FACTS).forEach { fact ->
            array.put(
                JSONObject()
                    .put("id", fact.id)
                    .put("text", fact.text.take(MAX_FACT_CHARS))
                    .put("createdAt", fact.createdAt)
            )
        }
        prefs.edit().putString(KEY_FACTS, array.toString()).apply()
    }

    private companion object {
        const val KEY_FACTS = "facts"
        const val MAX_FACTS = 80
        const val MAX_FACT_CHARS = 1_000
    }
}
