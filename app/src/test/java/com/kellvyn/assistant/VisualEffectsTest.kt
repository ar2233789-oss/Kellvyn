package com.kellvyn.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualEffectsTest {
    @Test
    fun rainAndLightningCanRunTogether() {
        assertEquals(setOf("chuva", "raios"), toggleVisualEffects(setOf("chuva"), "raios"))
    }

    @Test
    fun disablingLightningLeavesRainEnabled() {
        assertEquals(setOf("chuva"), toggleVisualEffects(setOf("chuva", "raios"), "raios"))
    }

    @Test
    fun selectingNoEffectClearsEveryEffect() {
        assertEquals(emptySet<String>(), toggleVisualEffects(setOf("chuva", "raios", "neve"), "nenhum"))
    }

    @Test
    fun choosingAnEffectAfterNoneStartsOnlyThatEffect() {
        assertEquals(setOf("chuva"), toggleVisualEffects(emptySet(), "chuva"))
    }

    @Test
    fun unsupportedEffectsAreIgnored() {
        assertEquals(setOf("chuva"), toggleVisualEffects(setOf("chuva"), "provider"))
    }
}
