package com.kellvyn.assistant.ai

import java.util.Locale

/**
 * A command can be routed to Termux only if the current user message names Termux and asks the
 * assistant for help/diagnosis there or directly asks it to use/execute something there. Tutorials,
 * command examples, negated requests, and "copy this for me to run myself" do not grant permission.
 */
internal object TermuxRequestPolicy {
    fun mayUseTermux(userMessage: String): Boolean {
        val text = userMessage.trim().lowercase(Locale.ROOT)
        if (text.isBlank() || !Regex("\\btermux\\b").containsMatchIn(text)) return false

        val explicitNoExecution = listOf(
            "não execute nenhum comando", "nao execute nenhum comando",
            "não execute comandos", "nao execute comandos",
            "não, não execute", "nao, nao execute",
            "não execute nenhum comando por mim", "nao execute nenhum comando por mim",
            "não rode nenhum comando", "nao rode nenhum comando",
            "não use o termux", "nao use o termux",
            "não acesse o termux", "nao acesse o termux",
            "não execute no termux", "nao execute no termux",
            "não rode no termux", "nao rode no termux",
            "só me passe para copiar", "so me passe para copiar",
            "organize os comandos para eu copiar", "comandos para eu copiar",
            "para eu executar", "pra eu executar", "só me explique", "so me explique",
            "apenas explique", "sem executar"
        ).any { it in text }
        if (explicitNoExecution) return false

        val normalizedQuestion = text.trimEnd('?', '!', '.', ' ')
        val explanationOnly = setOf(
            "o que é termux", "o que e termux", "para que serve termux",
            "pra que serve termux", "como instalar o termux", "como usar o termux",
            "como usar termux", "me explique o termux", "me explica o termux",
            "explique o termux", "o que significa termux"
        )
        if (normalizedQuestion in explanationOnly) return false

        val asksForInstructions = listOf(
            "como ", "o que ", "para que ", "pra que ", "quais comandos",
            "me mostre comandos", "me mostre o comando", "me passe comandos",
            "me passe o comando", "me diga como", "explique ", "me explica "
        ).any { text.startsWith(it) }
        val directRequestOverride = listOf(
            "me ajuda", "me ajude", "preciso de ajuda", "ajuda no termux",
            "ajuda com termux", "ajude no termux", "ajude com termux",
            "use o termux", "usa o termux", "usar o termux agora",
            "acesse o termux", "acessa o termux", "acessar o termux",
            "execute no termux", "executa no termux", "executar no termux",
            "rode no termux", "rodar no termux", "roda no termux",
            "verifique no termux", "verificar no termux", "confira no termux",
            "cheque no termux", "teste no termux", "testar no termux",
            "diagnostique no termux", "diagnosticar no termux",
            "investigue no termux", "investigar no termux",
            "corrija no termux", "conserte no termux", "resolva no termux",
            "analise esse erro no termux", "analisa esse erro no termux",
            "verifique esse erro no termux", "veja esse erro no termux",
            "execute esse comando no termux", "executa esse comando no termux",
            "pode executar esse comando no termux", "pode executar no termux",
            "pode rodar no termux", "pode usar o termux para verificar",
            "faça isso no termux para mim", "faca isso no termux para mim",
            "execute isso por mim no termux", "executa isso por mim no termux",
            "rode esse comando no termux", "roda esse comando no termux",
            "execute um comando no termux", "executa um comando no termux",
            "rode um comando no termux", "roda um comando no termux"
        ).any { it in text }

        if (asksForInstructions && !directRequestOverride) return false
        return directRequestOverride
    }
}
