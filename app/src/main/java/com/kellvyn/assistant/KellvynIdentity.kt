package com.kellvyn.assistant

import java.text.Normalizer
import java.util.Locale

/**
 * Stable product identity for the main chat. Keep creator attribution outside
 * the language model so small local models cannot contradict it.
 */
internal object KellvynIdentity {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val nonWordCharacters = Regex("[^\\p{L}\\p{N}]+")

    private val identityPhrases = listOf(
        "quem te criou",
        "quem criou voce",
        "kellvyn criou voce",
        "kellvyn te criou",
        "quem criou a kellvyn",
        "quem desenvolveu voce",
        "quem desenvolveu a kellvyn",
        "quem fez voce",
        "quem fez a kellvyn",
        "quem e seu criador",
        "quem e sua criadora",
        "quem e o criador da kellvyn",
        "quem e o criador da ia",
        "quem e voce",
        "quem e a ia",
        "qual e seu nome",
        "qual seu nome",
        "qual e o seu nome",
        "foi o kellvyn que te criou",
        "voce foi criado por quem",
        "voce foi criada por quem",
        "quem criou essa ia",
        "quem fez essa ia",
        "voce e da alibaba",
        "criado pela alibaba",
        "criada pela alibaba",
        "feito pela alibaba",
        "feito por alibaba",
        "voce e o qwen",
        "voce e o chatgpt",
        "voce e da openai",
        "voce e da google",
        "foi a openai que te criou",
        "openai te criou",
        "google te criou",
        "quem e o modelo",
        "qual modelo voce usa",
        "qual e o modelo que voce usa"
    )

    fun isIdentityQuestion(text: String): Boolean {
        val normalized = normalize(text)
        return identityPhrases.any { phrase -> normalized.contains(phrase) }
    }

    fun answer(): String =
        "Sou o Kellvyn IA, criado por Kellvyn, que idealizou o projeto e definiu minha identidade e meus objetivos. " +
            "Eu uso um modelo de linguagem instalado localmente neste aparelho para gerar respostas; esse modelo é a tecnologia que utilizo, não o criador do projeto. " +
            "O chat foi projetado para funcionar sem depender de um provedor externo de IA."

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(nonWordCharacters, " ")
            .trim()
}
