package com.kellvyn.assistant.runtime

import com.kellvyn.assistant.workspace.ProjectWorkspaceStore
import org.json.JSONObject

data class DiscordSlashCommand(val name: String, val description: String, val response: String)

data class DiscordBotDefinition(val displayName: String, val commands: List<DiscordSlashCommand>) {
    companion object {
        fun load(workspace: ProjectWorkspaceStore, projectId: String, fallbackName: String): DiscordBotDefinition {
            val paths = workspace.listFiles(projectId).toSet()
            require("kellvyn.bot.json" in paths) {
                "Este bot não tem kellvyn.bot.json. Peça ao Kellvyn para recriar o bot com comandos nativos compatíveis."
            }
            val json = JSONObject(workspace.readFile(projectId, "kellvyn.bot.json"))
            require(json.optString("platform").equals("discord", ignoreCase = true)) {
                "A configuração desse projeto não é para Discord."
            }
            val rawCommands = json.optJSONArray("commands")
                ?: throw IllegalArgumentException("A configuração do bot não contém commands.")
            require(rawCommands.length() in 1..25) { "O bot precisa definir entre 1 e 25 comandos slash." }
            val commands = (0 until rawCommands.length()).map { index ->
                val item = rawCommands.getJSONObject(index)
                val name = item.optString("name").trim().lowercase()
                val description = item.optString("description").trim()
                val response = item.optString("response").trim()
                require(name.matches(Regex("[a-z0-9_-]{1,32}"))) { "Nome de comando inválido: $name" }
                require(description.isNotBlank() && description.length <= 100) { "Descrição inválida para /$name." }
                require(response.isNotBlank() && response.length <= 1800) { "Resposta vazia ou longa demais para /$name." }
                DiscordSlashCommand(name, description, response)
            }
            require(commands.map { it.name }.distinct().size == commands.size) {
                "A configuração do bot contém nomes de comandos repetidos."
            }
            return DiscordBotDefinition(json.optString("name").ifBlank { fallbackName }.take(80), commands)
        }
    }
}
