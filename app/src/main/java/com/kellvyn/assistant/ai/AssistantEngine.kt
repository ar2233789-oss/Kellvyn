package com.kellvyn.assistant.ai

/**
 * Contract for a real assistant inference engine.
 *
 * Implementations must report unavailable models and inference failures explicitly.
 * The interface itself does not generate AI responses.
 */
interface AssistantEngine {
    val displayName: String
    suspend fun isAvailable(): Boolean
    suspend fun reply(conversation: List<AssistantTurn>): AssistantResult
}

data class AssistantTurn(
    val role: Role,
    val content: String
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

sealed interface AssistantResult {
    data class Success(val text: String) : AssistantResult
    data class Unavailable(val reason: String) : AssistantResult
    data class Failure(val reason: String) : AssistantResult
}
