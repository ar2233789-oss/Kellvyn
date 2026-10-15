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
    val content: String,
    val imagePath: String? = null
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

sealed interface AssistantResult {
    data class Success(val text: String) : AssistantResult
    data class Unavailable(val reason: String) : AssistantResult
    data class Failure(val reason: String) : AssistantResult
}

/** Returns a user-facing explanation for any outcome without assuming its subtype. */
fun AssistantResult.reasonOrText(): String = when (this) {
    is AssistantResult.Success -> text
    is AssistantResult.Unavailable -> reason
    is AssistantResult.Failure -> reason
}

/**
 * Optional streaming capability for engines that can report generated text as it arrives.
 * Agents may use the stream for responsive progress without exposing intermediate tool JSON
 * or hidden planning content to the chat transcript.
 */
interface StreamingAssistantEngine : AssistantEngine {
    suspend fun replyStreaming(
        conversation: List<AssistantTurn>,
        onPartial: suspend (String) -> Unit
    ): AssistantResult

    override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult =
        replyStreaming(conversation) { }
}
