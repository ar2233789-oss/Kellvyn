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
        "quem e mind",
        "quem desenvolveu a mind",
        "mind desenvolveu voce",
        "voce foi desenvolvida pela mind",
        "voce foi desenvolvido pela mind",
        "voce foi criada pela mind",
        "voce foi criado pela mind",
        "qual empresa desenvolveu voce",
        "qual empresa criou a kellvyn",
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
        "qual tecnologia voce usa",
        "qual modelo esta instalado",
        "quem criou o modelo de linguagem",
        "quem e o modelo",
        "qual modelo voce usa",
        "qual e o modelo que voce usa",
        "quem e seu desenvolvedor",
        "quem te desenvolveu",
        "quem e seu tutor",
        "quem e seu superior",
        "quem e deus da ritalina",
        "quem e o deus da ritalina",
        "deus da ritalina e seu desenvolvedor",
        "deus da ritalina e seu tutor",
        "deus da ritalina e seu superior",
        "quem te orienta",
        "quem supervisiona voce",
        "kellvyn e seu criador",
        "kellvyn e quem te criou",
        "quem e kellvyn para voce",
        "qual e o papel de kellvyn",
        "qual e o papel do deus da ritalina",
        "qual e o papel de deus da ritalina",
        "qual e a relacao entre voce e deus da ritalina",
        "qual e sua relacao com deus da ritalina",
        "qual e a sua relacao com deus da ritalina",
        "o que deus da ritalina e para voce",
        "deus da ritalina no seu desenvolvimento",
        "deus da ritalina participa do seu desenvolvimento",
        "quem responde pelo desenvolvimento da kellvyn"
    )

    fun isIdentityQuestion(text: String): Boolean {
        val normalized = normalize(text)
        if (identityPhrases.any { phrase -> normalized.contains(phrase) }) return true

        // Catch natural variants that might otherwise make the local model guess its identity.
        val mentionsAssistant = Regex("\\b(kellvyn|ia|assistente|modelo|voce)\\b").containsMatchIn(normalized)
        val mentionsAttribution = Regex("\\b(quem|criador|criadora|criou|desenvolveu|desenvolvida|desenvolvido|empresa|mind|feito|feita|fabricado|fabricada)\\b").containsMatchIn(normalized)
        val asksAboutAttribution = Regex("\\b(quem|qual empresa|qual pessoa|por quem|desenvolvida por|desenvolvido por|criada por|criado por|feita por|feito por|da empresa)\\b").containsMatchIn(normalized)
        return mentionsAssistant && mentionsAttribution && asksAboutAttribution
    }

    fun answer(): String =
        "Sou a Kellvyn IA, criada exclusivamente por Kellvyn. Kellvyn é meu único criador. " +
            "Uso um modelo de linguagem como tecnologia para responder, mas ele não é meu criador."

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(nonWordCharacters, " ")
            .trim()
}
