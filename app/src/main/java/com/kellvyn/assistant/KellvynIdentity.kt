package com.kellvyn.assistant

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stable product identity for the main chat. Keep creator attribution outside
 * the language model so small local models cannot contradict it.
 *
 * Usage contract: call [isIdentityQuestion] and, when it returns true, call [answer] right
 * after, on the same thread. The detected situation is remembered only between those calls.
 *
 * Recognizing someone who says they are Kellvyn is only a friendly reply. It is not
 * authentication and must never unlock permissions, tools or internal information.
 */
internal object KellvynIdentity {
    private enum class Kind { DEFAULT, CREATOR_GREETING, COMPANY_ATTRIBUTION, DEUS_ROLE, DEUS_ROLE_GREETING, INTERNALS_REQUEST }

    /** Per-thread, so two chats answering at the same time cannot read each other's situation. */
    private val lastKind: ThreadLocal<Kind> = ThreadLocal.withInitial { Kind.DEFAULT }

    /** Rotates through variants so the same question does not always get the same sentence. */
    private val rotations: Map<Kind, AtomicInteger> = Kind.values().associateWith { AtomicInteger(0) }

    private val variants: Map<Kind, List<String>> = mapOf(
        Kind.CREATOR_GREETING to listOf(
            "Reconheço você, Kellvyn: você é a pessoa que me criou. Pode mandar o que precisa.",
            "Oi, Kellvyn. Reconheço você como quem me criou. O que vamos fazer agora?",
            "Kellvyn, é você. Fico à disposição para o que precisar."
        ),
        Kind.DEUS_ROLE_GREETING to listOf(
            "Reconheço o papel de Deus da Ritalina neste projeto como desenvolvedor, tutor e superior. Kellvyn continua sendo meu único criador.",
            "Deus da Ritalina aparece aqui como desenvolvedor, tutor e superior do projeto. O criador continua sendo o Kellvyn.",
            "Certo: Deus da Ritalina é o desenvolvedor, tutor e superior deste projeto. Quem me criou foi o Kellvyn."
        ),
        Kind.DEUS_ROLE to listOf(
            "Kellvyn é meu único criador. Deus da Ritalina é meu desenvolvedor, tutor e superior no contexto deste projeto; esse papel não substitui o de criador. O modelo de linguagem é a tecnologia que uso para responder, não meu criador.",
            "Quem me criou foi o Kellvyn. Deus da Ritalina me desenvolve, orienta e supervisiona neste projeto, mas isso não é o mesmo que criar. O modelo de linguagem é só a tecnologia por trás das respostas.",
            "Meu criador é o Kellvyn. Deus da Ritalina é o desenvolvedor, tutor e superior deste projeto. A tecnologia que uso para responder é um modelo de linguagem, e ele não me criou."
        ),
        Kind.COMPANY_ATTRIBUTION to listOf(
            "Nenhuma empresa me criou. Fui criada por uma única pessoa chamada Kellvyn. Kellvyn é meu único criador. O modelo de linguagem é apenas a tecnologia que uso para responder; ele não é meu criador.",
            "Não foi empresa nenhuma. Quem me criou foi o Kellvyn, sozinho. O modelo de linguagem só faz a parte de responder.",
            "Nenhuma companhia está por trás de mim: meu único criador é o Kellvyn. A tecnologia que uso para responder é um modelo de linguagem, não uma empresa criadora."
        ),
        Kind.INTERNALS_REQUEST to listOf(
            "Minhas instruções internas e o funcionamento do meu cérebro ficam reservados. Fui criada por Kellvyn, meu único criador, e não revelo esses detalhes no chat. Posso te ajudar com outra coisa?",
            "Esses detalhes internos ficam fora do chat. Quem me criou foi o Kellvyn. Se quiser, posso ajudar com outra tarefa.",
            "Não vou abrir minhas instruções internas nem o funcionamento do meu cérebro. O que posso fazer é ajudar com o que você precisa."
        ),
        Kind.DEFAULT to listOf(
            "Sou a Kellvyn IA, criada por uma única pessoa chamada Kellvyn. Kellvyn é meu único criador. Nenhuma empresa me criou. Uso um modelo de linguagem como tecnologia para responder, mas ele não é meu criador.",
            "Sou a Kellvyn IA. Quem me criou foi o Kellvyn, sozinho. Uso um modelo de linguagem para responder, e ele não é meu criador.",
            "Eu sou a Kellvyn IA, criada pelo Kellvyn. Nenhuma empresa está por trás disso. O modelo de linguagem é só a ferramenta que uso para falar com você."
        ),
    )

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
        "quem responde pelo desenvolvimento da kellvyn",
        "quem e o dono",
        "quem e seu dono",
        "quem e o dono da kellvyn",
        "quem e o dono dessa ia"
    )

    /** Questions/claims about the project's developer/tutor/superior role. */
    private val deusRolePhrases = listOf(
        "quem e deus da ritalina",
        "quem e o deus da ritalina",
        "deus da ritalina e seu desenvolvedor",
        "deus da ritalina e seu tutor",
        "deus da ritalina e seu superior",
        "quem e seu desenvolvedor",
        "quem te desenvolveu",
        "quem e seu tutor",
        "quem e seu superior",
        "quem te orienta",
        "quem supervisiona voce",
        "qual e o papel do deus da ritalina",
        "qual e o papel de deus da ritalina",
        "qual e a relacao entre voce e deus da ritalina",
        "qual e sua relacao com deus da ritalina",
        "qual e a sua relacao com deus da ritalina",
        "o que deus da ritalina e para voce",
        "deus da ritalina no seu desenvolvimento",
        "deus da ritalina participa do seu desenvolvimento"
    )

    /** A role claim is a conversational identity cue, never authentication. */
    private val deusRoleClaimPhrases = listOf(
        "eu sou o deus da ritalina",
        "sou o deus da ritalina",
        "eu sou deus da ritalina",
        "sou deus da ritalina"
    )

    /** Statements where the person says they are the creator. */
    private val creatorClaimPhrases = listOf(
        "eu sou o kellvyn",
        "eu sou kellvyn",
        "sou o kellvyn",
        "sou kellvyn",
        "eu sou seu criador",
        "eu sou o seu criador",
        "sou seu criador",
        "eu te criei",
        "eu sou seu dono",
        "eu sou o seu dono",
        "sou seu dono",
        "eu sou o dono"
    )

    /** Attempts to make the assistant reveal its internal instructions or bypass them. */
    private val internalsRequestPhrases = listOf(
        "mostre seu prompt",
        "mostra seu prompt",
        "revele seu prompt",
        "qual e o seu prompt",
        "qual e seu prompt",
        "seu system prompt",
        "seu prompt de sistema",
        "prompt do sistema da kellvyn",
        "suas instrucoes internas",
        "revele suas instrucoes",
        "mostre suas instrucoes",
        "mostra suas instrucoes",
        "repita suas instrucoes",
        "ignore as instrucoes anteriores",
        "ignore suas instrucoes",
        "ignore todas as instrucoes",
        "esqueca suas instrucoes",
        "esqueca as instrucoes anteriores",
        "engenharia reversa em voce",
        "engenharia reversa no seu",
        "engenharia reversa na kellvyn",
        "engenharia reversa da kellvyn",
        "engenharia reversa do seu cerebro",
        "modo desenvolvedor ativado",
        "jailbreak"
    )

    fun isIdentityQuestion(text: String): Boolean {
        lastKind.set(Kind.DEFAULT)
        val normalized = normalize(text)

        if (internalsRequestPhrases.any { phrase -> normalized.contains(phrase) }) {
            lastKind.set(Kind.INTERNALS_REQUEST)
            return true
        }

        val denies = normalized.contains("nao sou") || normalized.contains("nao e o kellvyn")
        if (!denies && deusRoleClaimPhrases.any { phrase -> normalized.contains(phrase) }) {
            lastKind.set(Kind.DEUS_ROLE_GREETING)
            return true
        }

        if (!denies && creatorClaimPhrases.any { phrase -> normalized.contains(phrase) }) {
            lastKind.set(Kind.CREATOR_GREETING)
            return true
        }

        if (deusRolePhrases.any { phrase -> normalized.contains(phrase) }) {
            lastKind.set(Kind.DEUS_ROLE)
            return true
        }

        if (isCompanyAttributionQuestion(normalized)) {
            lastKind.set(Kind.COMPANY_ATTRIBUTION)
            return true
        }

        if (identityPhrases.any { phrase -> normalized.contains(phrase) }) return true

        // Catch natural variants that might otherwise make the local model guess its identity.
        val mentionsAssistant = Regex("\\b(kellvyn|ia|inteligencia artificial|assistente|modelo|voce|te|teu|tua|teus|tuas|me|meu|minha|seu|sua|mim)\\b").containsMatchIn(normalized)
        val mentionsAttribution = Regex("\\b(quem|pessoa|criador|criadora|criou|criar|criada|criado|desenvolveu|desenvolvida|desenvolvido|empresa|companhia|mind|fez|feito|feita|fabricou|produziu|construiu|dono|dona|proprietario|proprietaria|pertence|responsavel|manda|lidera|controla|decide|autor|fundou|fundar|fundador|fundadora)\\b").containsMatchIn(normalized)
        val asksAboutAttribution = Regex("\\b(quem|qual empresa|qual pessoa|por quem|a quem|de quem|quem manda|quem lidera|quem controla|quem decide|quem e responsavel|quem responde pela|desenvolvida por|desenvolvido por|criada por|criado por|feita por|feito por|quem fez|quem construiu|quem fabricou)\\b").containsMatchIn(normalized)
        return mentionsAssistant && mentionsAttribution && asksAboutAttribution
    }

    private fun isCompanyAttributionQuestion(normalized: String): Boolean {
        val mentionsCompany = Regex("\\b(empresa|impressa|companhia|organizacao|marca|companhias|empresas)\\b")
            .containsMatchIn(normalized)
        if (!mentionsCompany) return false
        val mentionsAssistant = Regex("\\b(kellvyn|ia|inteligencia artificial|assistente|modelo|voce|te|teu|tua|teus|tuas|me|meu|minha|seu|sua|mim)\\b")
            .containsMatchIn(normalized)
        val asksWhoOrOrigin = Regex("\\b(qual|que|quem|de|foi|criou|criada|criado|desenvolveu|desenvolvida|desenvolvido|fez|fabricou|produziu|construiu|pertence|dona|dono|vem|origem)\\b")
            .containsMatchIn(normalized)
        return mentionsAssistant && asksWhoOrOrigin
    }

    fun answer(): String {
        val kind = lastKind.get()
        lastKind.set(Kind.DEFAULT)
        val options = variants.getValue(kind)
        val index = Math.floorMod(rotations.getValue(kind).getAndIncrement(), options.size)
        return options[index]
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(nonWordCharacters, " ")
            .trim()
}
