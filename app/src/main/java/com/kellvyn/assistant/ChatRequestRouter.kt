package com.kellvyn.assistant

import java.util.Locale

/**
 * Pure routing rules for the main chat composer.
 *
 * A direct creation request can start Build even when the composer currently says Converse.
 * Questions, analysis requests and modifications to existing projects stay in the chat agent.
 */
object ChatRequestRouter {

    fun shouldBuild(text: String, buildModeSelected: Boolean): Boolean =
        isExplicitAppBuildRequest(text) || (buildModeSelected && isBuildBrief(text))

    fun shouldCompileExistingProject(text: String): Boolean {
        val t = text.trim().lowercase(Locale.ROOT)
        if (listOf(
                "como ", "me explique", "explique como", "tutorial", "passo a passo",
                "o que é", "o que e", "por que", "você consegue", "voce consegue"
            ).any { it in t }) return false

        val compileAction = listOf(
            "compile o projeto", "compila o projeto", "compilar o projeto",
            "compile esse projeto", "compile este projeto", "compile meu projeto",
            "compilar meu projeto", "compilar esse projeto", "compilar este projeto",
            "compile o apk", "compilar o apk", "compila o apk", "compile apk", "compilar apk",
            "compile o app", "compila o app", "compilar o app", "compile meu app",
            "compila meu app", "compilar meu app", "compile meu aplicativo",
            "compile o projeto existente", "compilar projeto existente",
            "compile o projeto salvo", "build apk"
        ).any { it in t }
        if (compileAction) return true

        val existingReference = listOf(
            "desse projeto", "deste projeto", "do meu projeto", "no meu projeto",
            "projeto existente", "projeto salvo", "meu app", "meu aplicativo",
            "desse app", "deste app", "do app existente", "aplicativo existente",
            "projeto que você criou", "projeto que voce criou", "app que você criou",
            "app que voce criou", "que já existe", "que ja existe"
        ).any { it in t }
        if (!existingReference) return false

        return listOf(
            "gere o apk", "gera o apk", "gerar o apk", "crie o apk", "criar o apk",
            "gere apk", "gerar apk", "crie apk", "criar apk", "transforme em apk",
            "construa o apk", "construir o apk", "faça o build", "faca o build", "build "
        ).any { it in t }
    }

    fun shouldPreferCoder(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        if (shouldBuild(text, buildModeSelected = false)) return true

        val explicitWorkAction = listOf(
            "corrija", "corrigir", "corrige", "conserte", "consertar", "arrume", "arrumar",
            "altere", "alterar", "modifique", "modificar", "edite", "editar", "atualize",
            "atualizar", "melhore", "melhorar", "adicione", "adicionar", "inclua", "incluir",
            "implemente", "implementar", "depure", "depurar", "analise ", "analisar ", "analisa ",
            "revise ", "revisar ", "inspecione ", "inspecionar ", "leia ", "ler ",
            "corrija o bug", "corrija o erro", "fix ", "debug ", "review ", "patch ",
            "continue o projeto", "termine o projeto",
            "termine minha ia", "finalize o projeto", "conclua o projeto", "complete o projeto"
        ).any { it in t }
        val codeTarget = Regex("\\.(kt|kts|java|xml|gradle|md|json|properties|py|js|ts|tsx|html|css)(\\b|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(text) ||
            listOf(
                "arquivo", "código", "codigo", "função", "funcao", "classe", "método", "metodo",
                "bug", "compilar", "compilação", "compilacao", "apk", "kotlin", "java",
                "git status", "git diff", "histórico de commits", "historico de commits",
                "projeto existente", "meu projeto", "meu app", "minha ia", "app que você criou",
                "app que voce criou", "projeto que você criou", "projeto que voce criou"
            ).any { it in t }
        return explicitWorkAction && codeTarget
    }

    private fun isExplicitAppBuildRequest(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        if (isExistingProjectModification(text)) return false

        val action = listOf(
            "crie", "criar", "cria ", "construa", "construir", "constrói", "constroi",
            "desenvolva", "desenvolver", "desenvolve", "gere", "gerar", "gera ",
            "faça", "faca", "faz ", "fazer ", "monta", "monte", "montar",
            "implemente", "implementar", "programe", "programar", "escreva", "escrever",
            "build ", "create ", "make ", "develop ", "implement "
        ).any { it in t }
        val target = listOf(
            "aplicativo", "app android", "apk", "aplicação android", "aplicacao android",
            "aplicativo móvel", "aplicativo movel", "app móvel", "app movel",
            "projeto android", "app com", "aplicativo com", "assistente virtual",
            "assistente de ia", "inteligência artificial", "inteligencia artificial",
            "chatbot", "chat bot", "jogo", "sistema", "software", "programa", "ferramenta",
            "minha ia", "ia local"
        ).any { it in t } ||
            t.split(Regex("[^a-z0-9]+")).contains("app") ||
            Regex("(^|[^a-z0-9])ia([^a-z0-9]|$)").containsMatchIn(t)

        val directCreationIntent = listOf(
            "quero um app", "quero uma app", "quero um aplicativo", "quero uma aplicação",
            "preciso de um app", "preciso de um aplicativo", "preciso de uma aplicação",
            "quero uma ia", "quero minha ia", "preciso de uma ia", "preciso da minha ia",
            "faça minha ia", "faca minha ia", "crie minha ia", "quero um assistente",
            "preciso de um assistente", "quero um chatbot", "preciso de um chatbot",
            "quero um jogo", "preciso de um jogo", "quero um sistema", "preciso de um sistema",
            "quero um software", "quero um programa", "quero uma ferramenta",
            "quero um jogo android", "preciso de um app android"
        ).any { it in t }
        val instructionalQuestion = listOf(
            "como criar", "como faço", "como faco", "me explique", "explique como",
            "tutorial de", "passo a passo para", "você consegue", "voce consegue",
            "é possível", "e possivel", "qual aplicativo devo usar", "você pode",
            "voce pode", "consegue criar", "será que", "sera que", "tem como"
        ).any { it in t }
        val startsWithAnalysisOrReview = listOf(
            "faça uma análise", "faca uma analise", "faz uma análise", "faz uma analise",
            "fazer uma análise", "fazer uma analise", "analise ", "analisa ",
            "avalie ", "avalia ", "revise ", "revisar ", "explique minha ia",
            "explique meu app", "me fale sobre minha ia", "me fale sobre meu app"
        ).any { t.trimStart().startsWith(it) }

        return (action || directCreationIntent) && target &&
            !instructionalQuestion && !startsWithAnalysisOrReview
    }

    private fun isExistingProjectModification(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        val modifying = listOf(
            "corrija", "corrigir", "conserte", "consertar", "arrume", "arrumar", "altere", "alterar",
            "modifique", "modificar", "edite", "editar", "atualize", "atualizar", "melhore", "melhorar",
            "adicione", "adicionar", "inclua", "incluir", "remova", "remover", "troque", "trocar",
            "ajuste", "ajustar", "continue", "implemente", "implementar", "mude", "mudar"
        ).any { it in t }
        val existingReference = listOf(
            "projeto existente", "projeto atual", "projeto salvo", "projeto anterior",
            "último projeto", "ultimo projeto", "projeto que você criou", "projeto que criou",
            "nesse projeto", "neste projeto", "naquele projeto",
            "aplicativo existente", "aplicativo atual", "aplicativo que você criou", "aplicativo que criou",
            "app existente", "app atual", "app que você criou", "app que criou",
            "no meu app", "no meu aplicativo", "meu projeto", "meu app", "meu aplicativo",
            "já criado", "ja criado", "que já existe", "que ja existe"
        ).any { it in t }
        return modifying && existingReference
    }

    private fun isBuildBrief(text: String): Boolean {
        val t = text.trim().lowercase(Locale.ROOT)
        if (isExistingProjectModification(text)) return false
        if (t.length < 30 || '?' in t) return false
        if (listOf(
                "terminal", "console", "problema", "erro", "ajuda", "me ajuda",
                "não consigo", "nao consigo", "estou com", "preciso de ajuda", "explique",
                "me explica", "o que é", "o que e", "quem é", "quem e", "por que",
                "como funciona", "olá", "ola", "bom dia", "boa tarde", "boa noite"
            ).any { it in t }) return false
        val features = listOf(
            "tela", "login", "cadastro", "botão", "botao", "salvar", "banco de dados",
            "offline", "notificação", "notificacao", "gerenciador", "usuários", "usuarios",
            "funções", "funcoes", "interface", "categorias", "busca"
        )
        val featureCount = features.count { it in t }
        val hasProjectNoun = listOf(
            "aplicativo", "sistema", "gerenciador", "aplicação", "aplicacao", "projeto"
        ).any { it in t } || t.split(Regex("[^a-z0-9]+")).contains("app")
        return featureCount >= 2 || (hasProjectNoun && featureCount >= 1)
    }
}
