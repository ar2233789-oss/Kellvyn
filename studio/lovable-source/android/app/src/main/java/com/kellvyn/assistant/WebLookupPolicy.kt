package com.kellvyn.assistant

/**
 * Decides when the native chat should add public web context.
 * Kept pure so matching behavior can be unit-tested without starting the model.
 */
internal object WebLookupPolicy {
    private val projectRequest = Regex(
        "(?i)\\b(crie|criar|faça|fazer|gere|gerar|desenvolva|construa)\\b.{0,80}\\b(app|aplicativo|bot|jogo|site|programa|script|api)\\b"
    )
    private val explicitUrl = Regex("(?i)https?://\\S+")
    private val questionOrSearchStart = Regex(
        "(?i)^(quem|o que|oque|quando|onde|qual|quais|como|por que|porque|quanto|quantos|quantas|me (fale|explique|diga|pesquise|resuma|conte)|pesquise|pesquisa|busque|procure|leia|analise|analisa|not[ií]cia|not[ií]cias|fale sobre|explique|resuma|atualiza[cç][aã]o)\\b"
    )
    private val explicitWebRequest = Regex(
        "(?i)\\b(pesquise na internet|pesquise na web|busca na internet|procure na web|leia esta página|resuma este site|notícias de hoje)\\b"
    )

    fun isLookupRequest(prompt: String): Boolean {
        val query = prompt.trim()
        if (query.isEmpty() || query.length > 300) return false
        if (projectRequest.containsMatchIn(query)) return false

        return query.contains("?") ||
            explicitUrl.containsMatchIn(query) ||
            questionOrSearchStart.containsMatchIn(query) ||
            explicitWebRequest.containsMatchIn(query)
    }
}
