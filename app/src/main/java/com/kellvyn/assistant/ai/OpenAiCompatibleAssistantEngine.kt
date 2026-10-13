package com.kellvyn.assistant.ai

import com.kellvyn.assistant.data.OnlineProviderSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Optional online provider adapter. Prompts are sent only when this engine is selected,
 * and only to the HTTPS endpoint configured by the user.
 */
class OpenAiCompatibleAssistantEngine(
    private val settingsStore: OnlineProviderSettingsStore
) : AssistantEngine {
    override val displayName: String = "OpenAI-compatible endpoint (online)"

    override suspend fun isAvailable(): Boolean {
        val settings = settingsStore.load()
        return settings.isConfigured && runCatching { endpointUrl(settings.endpoint) }.isSuccess
    }

    override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult =
        withContext(Dispatchers.IO) {
            val settings = settingsStore.load()
            if (!settings.isConfigured) {
                return@withContext AssistantResult.Unavailable(
                    "Configure o endpoint HTTPS e o nome do modelo para usar o modo online."
                )
            }

            val lastUserIndex = conversation.indexOfLast { it.role == AssistantTurn.Role.USER }
            if (lastUserIndex < 0) {
                return@withContext AssistantResult.Failure("Não há mensagem do usuário para enviar.")
            }

            var connection: HttpURLConnection? = null
            try {
                val url = endpointUrl(settings.endpoint)
                val messages = JSONArray()
                messages.put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "Você é Kellvyn IA, assistente útil e honesto. Responda em português brasileiro por padrão. " +
                                "Não invente ações realizadas nem capacidades indisponíveis."
                        )
                )
                conversation.take(lastUserIndex + 1).forEach { turn ->
                    if (turn.role != AssistantTurn.Role.SYSTEM) {
                        val role = when (turn.role) {
                            AssistantTurn.Role.USER -> "user"
                            AssistantTurn.Role.ASSISTANT -> "assistant"
                            AssistantTurn.Role.SYSTEM -> "system"
                        }
                        messages.put(JSONObject().put("role", role).put("content", turn.content))
                    }
                }

                val payload = JSONObject()
                    .put("model", settings.model)
                    .put("messages", messages)
                    .put("stream", false)
                    .put("temperature", 0.7)
                    .put("max_tokens", 1024)

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (settings.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                    }
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val code = connection.responseCode
                val responseStream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseBody = responseStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    val providerMessage = runCatching {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    val detail = providerMessage.ifBlank { responseBody.take(MAX_ERROR_CHARS) }
                    return@withContext AssistantResult.Failure(
                        "O provedor online respondeu HTTP $code" +
                            if (detail.isBlank()) "." else ": $detail"
                    )
                }

                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                    ?: return@withContext AssistantResult.Failure("A resposta do provedor não contém 'choices'.")
                if (choices.length() == 0) {
                    return@withContext AssistantResult.Failure("O provedor retornou uma resposta vazia.")
                }

                val answer = choices.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                    .trim()

                if (answer.isBlank()) {
                    AssistantResult.Failure("O provedor não retornou texto de resposta.")
                } else {
                    AssistantResult.Success(answer)
                }
            } catch (e: Exception) {
                AssistantResult.Failure(
                    "Falha no modo online: " + (e.message ?: e.javaClass.simpleName)
                )
            } finally {
                connection?.disconnect()
            }
        }

    private fun endpointUrl(endpoint: String): URL {
        val input = endpoint.trim().trimEnd('/')
        require(input.isNotBlank()) { "Informe um endpoint HTTPS." }
        val uri = URI(input)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Por segurança, o modo online exige endpoint HTTPS."
        }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "O endpoint deve ser uma URL HTTPS válida, sem credenciais, consulta ou fragmento."
        }

        val path = uri.path.orEmpty().trimEnd('/')
        val normalized = when {
            path.endsWith("/chat/completions") -> input
            path.endsWith("/v1") -> "$input/chat/completions"
            path.isBlank() || path == "/" -> "$input/v1/chat/completions"
            else -> "$input/v1/chat/completions"
        }
        return URL(normalized)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_ERROR_CHARS = 500
    }
}
