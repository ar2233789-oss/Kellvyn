package com.kellvyn.assistant.ai

import java.util.Locale

/**
 * The main chat is the only place where the user asks the agent for work.
 * Permit the agent to use Termux for explicit terminal requests and concrete
 * software/runtime work that may need a real shell. The user need not type
 * the word "Termux"; the agent still decides whether the shell is actually useful.
 *
 * Explanations, commands merely quoted for copying, explicit refusals to run,
 * and normal Android APK builds (handled by the integrated compiler) must not
 * trigger the Termux bridge.
 */
internal object TermuxRequestPolicy {
    private val actionPattern = Regex(
        """\b(ajude|ajuda|use|usar|usa|faça|faca|execute|executar|executa|rode|rodar|roda|instale|instalar|instala|teste|testar|testa|diagnostique|diagnosticar|diagnostica|investigue|investigar|investiga|verifique|verificar|verifica|inspecione|inspecionar|inspeciona|depure|depurar|depura|inicie|iniciar|inicia|analise|analisar|analisa|corrija|corrigir|conserte|consertar|repare|reparar|depure|depurar)\b"""
    )
    private val commandOrRuntimePattern = Regex(
        """\b(npm|npx|node|python3?|pip3?|bash|sh|pkg\s+install|apt(?:-get)?\s+install|chmod|chown|grep|awk|sed|make|cmake|cargo|rustc|gcc|g\+\+|clang|javac|pytest|gradle|ls|pwd|cat|tail|head|ps|top|env|find|wc|touch|mkdir|cp|mv|curl|wget|ssh|scp|rsync)\b|\.(sh|bash|py|js|mjs|cjs)\b|\b(script|scripts|dependências|dependencias|pacotes do ambiente|logs do processo|logs|servidor local|processo em execução|processo em execucao|testes|tests?|runtime|stack trace|traceback|bug|crash|código|codigo|programa|servidor|execução|execucao|erro de execução|erro de execucao|falha ao executar|fecha sozinho|não inicia|nao inicia|não compila|nao compila|não funciona|nao funciona)\b"""
    )
    private val explicitTerminalPattern = Regex(
        """\b(termux|terminal|shell|linha de comando|ambiente linux)\b"""
    )

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
            "não use o terminal", "nao use o terminal",
            "não execute no termux", "nao execute no termux",
            "não rode no termux", "nao rode no termux",
            "só me passe para copiar", "so me passe para copiar",
            "comandos para eu copiar", "para eu executar", "pra eu executar",
            "só me explique", "so me explique", "apenas explique", "sem executar"
        ).any { it in text } || Regex(
            """\b(não|nao)\s+(execute|executar|rode|rodar|use|usar|acesse|acessar|teste|testar|instale|instalar)\b"""
        ).containsMatchIn(text)
        if (explicitNoExecution) return false

        val asksForInstructions = listOf(
            "como ", "o que ", "para que ", "pra que ", "quais comandos",
            "me mostre comandos", "me mostre o comando", "me passe comandos",
            "me passe o comando", "me diga como", "explique ", "me explica "
        ).any { text.startsWith(it) }
        if (asksForInstructions) return false

        val explicitTerminal = explicitTerminalPattern.containsMatchIn(text)
        val hasAction = actionPattern.containsMatchIn(text) ||
            listOf("rode os testes", "rodar os testes", "execute os testes", "executar os testes",
                "instale a dependência", "instale a dependencia", "instale as dependências",
                "instale as dependencias", "inicie o servidor", "iniciar o servidor",
                "execute o script", "executar o script", "rode o script", "rodar o script")
                .any { it in text }

        if (explicitTerminal) {
            return hasAction || listOf(
                "me ajuda com esse erro", "me ajude com esse erro",
                "ajuda com esse erro", "diagnóstico", "diagnostico"
            ).any { it in text }
        }

        // Android APK creation/compilation stays with the integrated compiler.
        val androidBuildRequest = Regex("""\b(apk|aplicativo android|app android|projeto android)\b""")
            .containsMatchIn(text) &&
            listOf("gere", "gerar", "gera", "compile", "compilar", "compila", "crie", "criar", "build")
                .any { it in text }
        if (androidBuildRequest) return false

        // Enable shell access for real software/runtime tasks; the agent makes the final choice.
        return hasAction && commandOrRuntimePattern.containsMatchIn(text)
    }
}
