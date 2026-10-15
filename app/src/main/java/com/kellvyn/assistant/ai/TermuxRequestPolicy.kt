package com.kellvyn.assistant.ai

import java.util.Locale

/**
 * Termux is an independent Android app. The main chat may connect to it only when
 * the user explicitly asks for help using Termux in that chat. Generic coding,
 * runtime, or shell-related requests do not authorize the bridge by themselves.
 *
 * Explanations, commands merely quoted for copying, explicit refusals to run,
 * and Android APK builds handled by the integrated compiler must not trigger it.
 */
internal object TermuxRequestPolicy {
    private val actionPattern = Regex(
        """\b(ajuda|ajudar|ajude|ajudem|use|usar|usa|faça|faca|execute|executar|executa|rode|rodar|roda|instale|instalar|instala|teste|testar|testa|diagnostique|diagnosticar|diagnostica|investigue|investigar|investiga|verifique|verificar|verifica|inspecione|inspecionar|inspeciona|depure|depurar|depura|inicie|iniciar|inicia|analise|analisar|analisa|corrija|corrigir|conserte|consertar|repare|reparar)\b"""
    )
    private val explicitTermuxPattern = Regex("""\btermux\b""")

    fun mayUseTermux(userMessage: String): Boolean {
        val text = userMessage.trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return false

        val explicitNoExecution = listOf(
            "não execute nenhum comando", "nao execute nenhum comando",
            "não execute comandos", "nao execute comandos",
            "não, não execute", "nao, nao execute",
            "não rode nenhum comando", "nao rode nenhum comando",
            "não use o termux", "nao use o termux",
            "não acesse o termux", "nao acesse o termux",
            "não execute no termux", "nao execute no termux",
            "não rode no termux", "nao rode no termux",
            "só me passe para copiar", "so me passe para copiar",
            "comandos para eu copiar", "para eu executar", "pra eu executar",
            "só me explique", "so me explique", "apenas explique", "sem executar"
        ).any { it in text } || Regex(
            """\b(não|nao)\s+(execute|executar|rode|rodar|use|usar|acesse|acessar|teste|testar|instale|instalar)\b"""
        ).containsMatchIn(text)
        if (explicitNoExecution) return false

        // Keep Termux independent: the user must explicitly name it in the main chat.
        if (!explicitTermuxPattern.containsMatchIn(text)) return false

        val asksForInstructions = listOf(
            "como ", "o que ", "para que ", "pra que ", "quais comandos",
            "me mostre comandos", "me mostre o comando", "me passe comandos",
            "me passe o comando", "me diga como", "explique ", "me explica "
        ).any { text.startsWith(it) }
        if (asksForInstructions) return false

        val hasAction = actionPattern.containsMatchIn(text) ||
            listOf(
                "rode os testes", "rodar os testes", "execute os testes", "executar os testes",
                "instale a dependência", "instale a dependencia", "instale as dependências",
                "instale as dependencias", "inicie o servidor", "iniciar o servidor",
                "execute o script", "executar o script", "rode o script", "rodar o script",
                "me ajuda com esse erro", "me ajude com esse erro", "ajuda com esse erro",
                "quero ajuda com o termux", "quero ajuda no termux"
            ).any { it in text }
        if (!hasAction) return false

        // Android APK creation/compilation stays with the integrated compiler.
        val androidBuildRequest = Regex("""\b(apk|aplicativo android|app android|projeto android)\b""")
            .containsMatchIn(text) &&
            listOf("gere", "gerar", "gera", "compile", "compilar", "compila", "crie", "criar", "build")
                .any { it in text }
        if (androidBuildRequest) return false

        return true
    }
}
