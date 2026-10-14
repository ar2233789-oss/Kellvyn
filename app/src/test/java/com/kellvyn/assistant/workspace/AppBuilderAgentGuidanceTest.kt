package com.kellvyn.assistant.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBuilderAgentGuidanceTest {
    @Test
    fun rainGameRequiresAnimatedParticlesAndRealControls() {
        val rules = AppBuilderAgent.projectSpecificGuidance(
            "Crie um jogo 2D de sobrevivência com chuva, botão de ligar a chuva e controle de intensidade."
        )
        assertTrue(rules.any { it.contains("MECÂNICA DE CHUVA") })
        assertTrue(rules.any { it.contains("setOnClickListener") })
        assertTrue(rules.any { it.contains("onDraw(Canvas)") })
    }

    @Test
    fun musicPlayerRequiresLocalFilePickerAndRealPlayback() {
        val rules = AppBuilderAgent.projectSpecificGuidance(
            "Crie um player de música com visual industrial pesado e arquivos de áudio locais."
        )
        assertTrue(rules.any { it.contains("ACTION_OPEN_DOCUMENT") })
        assertTrue(rules.any { it.contains("android.media.MediaPlayer") })
        assertTrue(rules.any { it.contains("Estilo visual pesado") })
    }

    @Test
    fun imageRequestsDoNotPretendTheTextModelGeneratedAFile() {
        val rules = AppBuilderAgent.projectSpecificGuidance(
            "Crie um jogo com uma imagem de referência visual complexa."
        )
        assertTrue(rules.any { it.contains("LIMITAÇÃO VISUAL") })
        assertTrue(rules.any { it.contains("não pode anexar PNG/JPEG") })
    }

    @Test
    fun ordinaryAppsDoNotReceiveUnrelatedGameOrMusicRules() {
        val rules = AppBuilderAgent.projectSpecificGuidance(
            "Crie uma calculadora simples com histórico de operações."
        )
        assertFalse(rules.any { it.contains("MECÂNICA DE CHUVA") })
        assertFalse(rules.any { it.contains("PERFIL PLAYER MUSICAL") })
    }
}
