package com.kellvyn.assistant.ai

import java.util.Locale

/**
 * The assistant may call into Termux only when the current user message explicitly asks for help
 * using Termux, for diagnosis there, or to execute a command there. Merely mentioning Termux is not
 * permission to execute anything.
 */
internal object TermuxRequestPolicy {
    fun mayUseTermux(userMessage: String): Boolean {
        val text = userMessage.trim().lowercase(Locale.ROOT)
        if (text.isBlank() || !Regex("\\btermux\\b").containsMatchIn(text)) return false

        val asksForExplanationOnly = listOf(
            "o que é termux", "o que e termux", "para que serve termux", "pra que serve termux",
            "como instalar o termux", "como usar o termux", "como usar termux",
            "me explique o termux", "me explica o termux", "explique o termux",
            "o que significa termux"
        ).any { it in text }
        if (asksForExplanationOnly) return false

        val asksForActionOrHelp = listOf(
            "me ajuda", "me ajude", "ajuda no termux", "ajuda com termux",
            "ajude no termux", "ajude com termux", "use o termux", "usa o termux",
            "acesse o termux", "acessa o termux", "no termux execute", "no termux rode",
            "executa no termux", "execute no termux", "executar no termux",
            "rode no termux", "rodar no termux", "roda no termux",
            "teste no termux", "testar no termux", "verifique no termux",
            "verificar no termux", "diagnostique no termux", "diagnosticar no termux",
            "investigue no termux", "investigar no termux", "corrija no termux",
            "conserte no termux", "resolva no termux", "corrija esse erro no termux",
            "erro no termux", "problema no termux", "falha no termux",
            "comando no termux", "comandos no termux", "execute esse comando no termux",
            "roda esse comando no termux"
        ).any { it in text }
        return asksForActionOrHelp
    }
}
