package com.kellvyn.assistant.runtime

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import com.kellvyn.assistant.workspace.TelegramBotManifestValidator
import org.json.JSONObject

data class TelegramBotCommand(
    val name: String,
    val description: String,
    val response: String,
    val action: String
)

data class TelegramBotDefinition(val displayName: String, val commands: List<TelegramBotCommand>) {
    companion object {
        fun load(workspace: ProjectWorkspaceStore, projectId: String, fallbackName: String): TelegramBotDefinition {
            val paths = workspace.listFiles(projectId).toSet()
            require("kellvyn.telegram.json" in paths) {
                "Este projeto não tem kellvyn.telegram.json para o runtime nativo."
            }
            val json = JSONObject(workspace.readFile(projectId, "kellvyn.telegram.json"))
            val error = TelegramBotManifestValidator.validate(json.toString())
            require(error == null) { error ?: "Configuração Telegram inválida." }
            val rawCommands = json.getJSONArray("commands")
            val commands = (0 until rawCommands.length()).map { index ->
                val item = rawCommands.getJSONObject(index)
                TelegramBotCommand(
                    name = item.getString("name").trim().lowercase(),
                    description = item.getString("description").trim(),
                    response = item.optString("response").trim(),
                    action = item.optString("action", "response").trim().lowercase().ifBlank { "response" }
                )
            }
            return TelegramBotDefinition(
                json.optString("name").ifBlank { fallbackName }.take(80),
                commands
            )
        }
    }
}
