package com.kellvyn.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptBudgetTest {
    @Test
    fun projectGenerationGetsMoreThanChatOutputWhenThePromptIsSmall() {
        val budget = boundLocalPrompt(
            systemInstruction = "You are Kellvyn IA.",
            priorTurns = emptyList(),
            latestUserText = "Crie um app Android Java pequeno.",
            requestedMaxOutputTokens = 8192
        )

        assertTrue("Project generation should receive a multi-thousand-token budget.", budget.maxOutputTokens > 1024)
        assertTrue(budget.maxOutputTokens <= 8192)
    }

    @Test
    fun recentHistoryIsKeptAsAContiguousSuffixWhenContextIsTight() {
        val history = listOf(
            AssistantTurn(AssistantTurn.Role.USER, "old-user-".repeat(500)),
            AssistantTurn(AssistantTurn.Role.ASSISTANT, "old-assistant-".repeat(500)),
            AssistantTurn(AssistantTurn.Role.USER, "recent-user-".repeat(250)),
            AssistantTurn(AssistantTurn.Role.ASSISTANT, "recent-answer-".repeat(40))
        )

        val budget = boundLocalPrompt(
            systemInstruction = "K".repeat(10_000),
            priorTurns = history,
            latestUserText = "continue the current task",
            requestedMaxOutputTokens = 1024
        )

        assertTrue(budget.priorTurns.isNotEmpty())
        assertEquals(history.takeLast(budget.priorTurns.size), budget.priorTurns)
        assertEquals("K".repeat(budget.systemInstruction.length), budget.systemInstruction)
    }

    @Test
    fun inputAndOutputBudgetStayWithinTheEightThousandTokenContextEstimate() {
        val latestUser = "Crie uma aplicação com interface e funcionalidade. ".repeat(220)
        val budget = boundLocalPrompt(
            systemInstruction = "Instruções e contexto antigo. ".repeat(1_000),
            priorTurns = listOf(
                AssistantTurn(AssistantTurn.Role.USER, "contexto antigo ".repeat(400)),
                AssistantTurn(AssistantTurn.Role.ASSISTANT, "resposta anterior ".repeat(400))
            ),
            latestUserText = latestUser,
            requestedMaxOutputTokens = 8192
        )

        val estimatedInputTokens =
            (budget.systemInstruction.length + latestUser.length + 512 + 1) / 2
        assertTrue(estimatedInputTokens + budget.maxOutputTokens + 384 <= 8192)
        assertTrue(budget.maxOutputTokens >= 256)
    }

    @Test
    fun tailOfMostRecentPriorTurnIsKeptWhenItCannotFitWhole() {
        val latestPrior = "CONTEXTO-RECENTE-".repeat(1_000)
        val budget = boundLocalPrompt(
            systemInstruction = "K".repeat(10_000),
            priorTurns = listOf(
                AssistantTurn(AssistantTurn.Role.USER, "turno antigo"),
                AssistantTurn(AssistantTurn.Role.ASSISTANT, latestPrior)
            ),
            latestUserText = "continua",
            requestedMaxOutputTokens = 1024
        )

        assertEquals(1, budget.priorTurns.size)
        val retained = budget.priorTurns.single().content
        assertTrue(retained.startsWith("…"))
        assertTrue(latestPrior.endsWith(retained.removePrefix("…")))
        assertTrue(retained.length < latestPrior.length)
    }

    @Test
    fun chatOutputNeverExceedsItsRequestedLimit() {
        val budget = boundLocalPrompt(
            systemInstruction = "System context",
            priorTurns = emptyList(),
            latestUserText = "Olá",
            requestedMaxOutputTokens = 1024
        )

        assertTrue(budget.maxOutputTokens in 256..1024)
    }
}
