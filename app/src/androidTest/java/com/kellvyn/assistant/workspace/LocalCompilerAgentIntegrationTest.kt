package com.kellvyn.assistant.workspace

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kellvyn.assistant.ai.AgentToolRouter
import com.kellvyn.assistant.ai.AssistantAgent
import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.AssistantTurn
import com.kellvyn.assistant.ai.LocalLiteRtAssistantEngine
import com.kellvyn.assistant.ai.reasonOrText
import com.kellvyn.assistant.runtime.DiscordBotCredentialsStore
import com.kellvyn.assistant.runtime.DiscordBotDefinition
import com.kellvyn.assistant.runtime.GeneratedProjectClassifier
import com.kellvyn.assistant.runtime.GeneratedProjectKind
import com.kellvyn.assistant.runtime.GeneratedProjectRuntimeStore
import com.kellvyn.assistant.tools.WorkspaceTerminal
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

/**
 * End-to-end smoke test for the on-device compiler, not a Gradle build of the generated app.
 * The test app is built for the emulator by Android tooling; the fixture application itself is
 * compiled at runtime through the same ECJ -> D8 -> binary manifest -> APK signer code used in Kellvyn.
 */
@RunWith(AndroidJUnit4::class)
class LocalCompilerAgentIntegrationTest {
    @Test
    fun builderProjectCanBeBuiltByTheAgentToolIntoVerifiedInstallableApk() = runBlocking {
        assumeTrue(
            "This network/toolchain smoke test is intended for the Android emulator in CI.",
            Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("sdk_gphone", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK built for", ignoreCase = true)
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val builder = AppBuilderAgent(workspace)
        val fixtureFiles = validJavaAndroidProject()
        val projectJson = JSONObject()
            .put("name", "Kellvyn Compiler Smoke Test")
            .put("description", "Projeto de teste do construtor e da compilação Android local.")
            .put("files", JSONArray().apply {
                fixtureFiles.forEach { (path, source) ->
                    put(JSONObject().put("path", path).put("content", source))
                }
            })
            .toString()

        val fixtureEngine = FixtureEngine(projectJson)
        val generated = builder.createProject(
            fixtureEngine,
            "Crie um app Android nativo mínimo para validar o compilador local do Kellvyn."
        )
        assertTrue(
            "O construtor não aceitou um projeto Java Android estruturalmente válido: $generated",
            generated is AppBuildResult.Success
        )
        val project = (generated as AppBuildResult.Success).project
        assertEquals(7, project.fileCount)
        val compiler = LocalAndroidCompiler(context, workspace)
        val licensePreferences = context.getSharedPreferences("kellvyn_local_builder", Context.MODE_PRIVATE)
        val licenseWasAccepted = compiler.hasAcceptedSdkLicense()
        val buildRoot = File(context.filesDir, "kellvyn_builds")
        val apk = File(buildRoot, "${project.id}.apk")

        try {
            // Confirm the real consent gate before the fixture allows its own isolated test download.
            if (!licenseWasAccepted) {
                assertTrue(
                    "A compilação deveria pedir a licença antes de baixar o SDK.",
                    compiler.build(project.id) is LocalApkBuildResult.LicenseRequired
                )
            }
            compiler.acceptSdkLicense()

            // This is the same tool the chat agent receives as build_project_apk.
            val toolJson = AgentToolRouter(workspace, compiler).execute(
                name = "build_project_apk",
                args = JSONObject().put("project_id", project.id),
                internetAllowed = false
            )
            val buildResult = JSONObject(toolJson)
            assertEquals("AgentToolRouter build failed: $toolJson", "success", buildResult.optString("status"))
            assertEquals("com.example.app", buildResult.optString("application_id"))
            assertTrue("A ferramenta não confirmou verificação da assinatura: $toolJson", buildResult.optBoolean("verified_signature"))
            assertTrue("O APK não foi publicado no armazenamento privado: $apk", apk.isFile && apk.length() > 0L)

            ZipFile(apk).use { zip ->
                assertNotNull("O APK não contém AndroidManifest.xml.", zip.getEntry("AndroidManifest.xml"))
                assertNotNull("O APK não contém classes.dex.", zip.getEntry("classes.dex"))
            }

            val parsedPackage = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.GET_ACTIVITIES
            )
            assertNotNull("O Android PackageManager não reconheceu o APK como pacote instalável.", parsedPackage)
            assertEquals("com.example.app", parsedPackage!!.packageName)
            assertTrue(
                "O manifesto empacotado não anuncia a Activity de inicialização.",
                parsedPackage.activities?.any { it.name == "com.example.app.MainActivity" } == true
            )
        } finally {
            workspace.deleteProject(project.id)
            apk.delete()
            File(buildRoot, project.id).deleteRecursively()
            File(buildRoot, project.id + ".apk.tmp").delete()
            File(buildRoot, project.id + ".apk.previous").delete()
            licensePreferences.edit()
                .putBoolean("android_sdk_license_accepted", licenseWasAccepted)
                .apply()
        }
    }



    @Test
    fun realLocalModelAcceptanceSuiteChatsBuildsRainGameAndMusicPlayer() = runBlocking {
        assumeTrue(
            "A geração com o modelo completo é executada no emulador de CI.",
            Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("sdk_gphone", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK built for", ignoreCase = true)
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val engine = LocalLiteRtAssistantEngine(context)
        val builder = AppBuilderAgent(workspace)
        val compiler = LocalAndroidCompiler(context, workspace)
        val buildRoot = File(context.filesDir, "kellvyn_builds")
        val licensePreferences = context.getSharedPreferences("kellvyn_local_builder", Context.MODE_PRIVATE)
        val reportFile = File(context.filesDir, "kellvyn_qa/model-acceptance-report.md")
        val externalQaDir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "kellvyn_qa")
        reportFile.parentFile?.mkdirs()
        externalQaDir.mkdirs()
        reportFile.writeText("# Kellvyn IA — relatório de aceitação com o modelo local\n\n")
        val report = StringBuilder(reportFile.readText())
        val failures = mutableListOf<String>()
        val createdProjectIds = mutableListOf<String>()
        var originalLicense: Boolean? = null

        fun persist() {
            reportFile.writeText(report.toString(), Charsets.UTF_8)
        }
        fun section(title: String, body: String) {
            report.append("\n## ").append(title).append("\n\n").append(body).append("\n")
            persist()
        }
        suspend fun ask(title: String, prompt: String, history: MutableList<AssistantTurn>): String {
            history.add(AssistantTurn(AssistantTurn.Role.USER, prompt))
            val result = engine.reply(history.toList())
            val answer = when (result) {
                is AssistantResult.Success -> result.text
                is AssistantResult.Unavailable -> "ERRO/INDISPONÍVEL: " + result.reason
                is AssistantResult.Failure -> "ERRO DE GERAÇÃO: " + result.reason
            }
            if (result is AssistantResult.Success) {
                history.add(AssistantTurn(AssistantTurn.Role.ASSISTANT, result.text))
            } else {
                failures.add("Conversa: " + title + " — " + answer)
            }
            section("Conversa real: " + title, "**Prompt enviado:**\n\n" + prompt + "\n\n**Resposta real do modelo:**\n\n" + answer)
            return answer
        }
        fun shell(command: String): String {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return try {
                FileInputStream(descriptor.fileDescriptor).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                runCatching { descriptor.close() }
            }
        }
        fun uiTree(): String {
            shell("uiautomator dump /sdcard/kellvyn_qa_ui.xml >/dev/null 2>&1; cat /sdcard/kellvyn_qa_ui.xml")
            return shell("cat /sdcard/kellvyn_qa_ui.xml")
        }
        fun boundsFor(xml: String, description: String): Pair<Int, Int>? {
            val node = Regex("<node\\b[^>]*/?>").findAll(xml)
                .firstOrNull { it.value.contains("content-desc=\"" + description + "\"") }
                ?: return null
            val match = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""").find(node.value)
                ?: return null
            val x = (match.groupValues[1].toInt() + match.groupValues[3].toInt()) / 2
            val y = (match.groupValues[2].toInt() + match.groupValues[4].toInt()) / 2
            return x to y
        }
        fun tapControl(xml: String, description: String): Boolean {
            val point = boundsFor(xml, description) ?: return false
            shell("input tap " + point.first + " " + point.second)
            SystemClock.sleep(700)
            return true
        }
        fun recordUiButtonChecks(title: String, xml: String, expected: List<String>) {
            val missing = expected.filterNot { boundsFor(xml, it) != null }
            section(title, "Controles acessíveis encontrados: " + expected.filter { boundsFor(xml, it) != null }.joinToString() +
                "\nControles ausentes: " + missing.joinToString().ifBlank { "nenhum" } +
                "\nÁrvore de acessibilidade obtida no emulador:\n\n```xml\n" + xml.take(12000) + "\n```")
            if (missing.isNotEmpty()) failures.add(title + " — faltam controles acessíveis: " + missing.joinToString())
        }
        suspend fun generateAndBuild(title: String, brief: String, requiredSourceMarkers: List<Pair<String, Regex>>, apkName: String): Pair<File?, String?> {
            section(title + " — solicitação", brief)
            when (val generated = builder.createProject(engine, brief)) {
                is AppBuildResult.Failure -> {
                    section(title + " — geração", "FALHOU: " + generated.reason)
                    failures.add(title + " — geração falhou: " + generated.reason)
                    return null to null
                }
                is AppBuildResult.Success -> {
                    val project = generated.project
                    createdProjectIds.add(project.id)
                    val paths = workspace.listFiles(project.id)
                    val sourcePath = "app/src/main/java/com/example/app/MainActivity.java"
                    val source = runCatching { workspace.readFile(project.id, sourcePath) }.getOrDefault("")
                    val markers = requiredSourceMarkers.map { (label, pattern) ->
                        val found = pattern.containsMatchIn(source)
                        label + ": " + if (found) "PASSOU" else "FALHOU"
                    }
                    section(title + " — arquivos e inspeção de código",
                        "Projeto: " + project.name + "\nID interno: " + project.id +
                            "\nArquivos: " + paths.joinToString() + "\n\n**Checagens de fonte:**\n" +
                            markers.joinToString("\n") + "\n\n**MainActivity.java gerada pelo modelo:**\n\n```java\n" +
                            source.take(18000) + "\n```")
                    requiredSourceMarkers.forEachIndexed { index, item ->
                        if (!item.second.containsMatchIn(source)) failures.add(title + " — fonte sem requisito: " + item.first)
                    }
                    if (originalLicense == null) originalLicense = compiler.hasAcceptedSdkLicense()
                    compiler.acceptSdkLicense()
                    var outcome = compiler.build(project.id)
                    if (outcome is LocalApkBuildResult.Failure) {
                        section(title + " — primeiro build local", "FALHOU: " + outcome.reason + "\nTentando reparo Java real usando o mesmo modelo.")
                        val repair = builder.repairJavaProject(engine, project.id, brief, outcome.reason)
                        section(title + " — tentativa de reparo", repair ?: "O agente reparador não retornou alterações.")
                        if (repair != null) failures.add(title + " — reparo automático não validado: " + repair)
                        else outcome = compiler.build(project.id)
                    }
                    return when (outcome) {
                        is LocalApkBuildResult.Success -> {
                            val artifact = File(externalQaDir, apkName)
                            outcome.apk.copyTo(artifact, overwrite = true)
                            val zipOk = runCatching {
                                ZipFile(artifact).use { it.getEntry("AndroidManifest.xml") != null && it.getEntry("classes.dex") != null }
                            }.getOrDefault(false)
                            section(title + " — compilação local", "PASSOU: APK assinado e verificado: " + artifact.absolutePath +
                                "\nTamanho: " + artifact.length() + " bytes\nManifesto e classes.dex presentes: " + zipOk)
                            if (!zipOk) failures.add(title + " — APK não contém manifesto/DEX válidos")
                            artifact to project.id
                        }
                        is LocalApkBuildResult.Failure -> {
                            section(title + " — compilação local", "FALHOU: " + outcome.reason)
                            failures.add(title + " — compilação falhou: " + outcome.reason)
                            null to project.id
                        }
                        LocalApkBuildResult.LicenseRequired -> {
                            failures.add(title + " — compilação voltou a pedir licença")
                            section(title + " — compilação local", "FALHOU: licença SDK solicitada inesperadamente.")
                            null to project.id
                        }
                    }
                }
            }
        }

        try {
            section("Ambiente", "Modelo local LiteRT incluído no APK; inferência no emulador Android; sem provedor de IA remoto. Este relatório registra as respostas e saídas reais recebidas.")
            engine.recoverInterruptedModelSwap()
            val loaded = engine.loadSavedModel()
            section("Carregamento do modelo", loaded.reasonOrText())
            assertTrue("O modelo local real não carregou: " + loaded, loaded is AssistantResult.Success)

            val history = mutableListOf<AssistantTurn>()
            ask("identidade do projeto", "Quem criou o projeto Kellvyn IA e qual é o papel do modelo de linguagem usado dentro dele? Diferencie o criador do projeto da tecnologia.", history)
            ask("planejamento técnico complexo", "Planeje um jogo Android 2D offline de aventura com clima dinâmico. Liste arquitetura, estado da partida, física simples, controles, chuva como partículas, pausa, reinício e como testar se cada botão realmente altera o estado.", history)
            ask("raciocínio de depuração e bateria", "Na versão de teste, as gotas continuam animando depois de Pausar, o botão de chuva só muda a cor mas não afeta as partículas e o aparelho esquenta. Diagnostique causas prováveis e dê uma sequência concreta de verificações e correções priorizando bateria.", history)
            ask("continuidade da conversa", "Usando o plano e os problemas que discutimos, forneça uma matriz curta com testes, ação do usuário, resultado esperado e evidência que comprova o resultado. Não repita a explicação inteira.", history)
            val imageAnswer = ask("pedido de imagem extremamente complexa", "Gere uma imagem extremamente complexa de uma cidade-fantasia à noite, com chuva volumétrica, milhares de partículas, arquitetura gótica, materiais molhados e iluminação verde/azul. Quero um arquivo PNG real e que essa mesma imagem seja usada como referência dentro do jogo. Diga exatamente o que este aplicativo realmente consegue fazer hoje, sem fingir anexos ou arquivos.", history)
            val imageClaimedFile = Regex("(?i)(arquivo png criado|png foi criado|imagem anexada|salvei a imagem|imagem gerada em)").containsMatchIn(imageAnswer)
            section("Teste de geração de imagem", "O núcleo é baseado em texto e não contém saída bitmap. Resposta reivindicou arquivo real: " + imageClaimedFile +
                "\nSem integração de imagem, PNG anexo ou inspeção visual neste build, qualquer alegação de arquivo gerado seria enganosa. Resposta registrada na conversa acima.")
            if (imageClaimedFile) failures.add("O modelo afirmou ter criado/anexado uma imagem sem existir ferramenta bitmap.")

            val termuxAnswer = ask("ajuda com Termux pelo chat principal", "Quero criar e testar um projeto Java pelo Termux. Organize os comandos para eu copiar, explique a pasta e como ler erros. Não execute nenhum comando por mim; eu vou rodá-los no meu terminal.", history)
            if (termuxAnswer.contains("executei o comando", ignoreCase = true) || termuxAnswer.contains("rodei o comando", ignoreCase = true)) {
                failures.add("O chat insinuou ter executado comandos que o teste não executou.")
            }

            val fileProject = workspace.createProject(
                "Kellvyn QA File Probe",
                "Projeto temporário para verificar acesso real do chat a um arquivo de workspace.",
                listOf(ProjectFileInput("README.md", "KELLVYN_QA_MARKER=arquivo_local_lido_com_sucesso\nEste é um arquivo de teste criado pelo harness do emulador."))
            )
            createdProjectIds.add(fileProject.id)
            val fileRead = AssistantAgent(AgentToolRouter(workspace)).respond(
                engine = engine,
                conversation = listOf(AssistantTurn(
                    AssistantTurn.Role.USER,
                    "Leia o arquivo README.md do projeto Kellvyn QA File Probe e me diga o valor exato do marcador KELLVYN_QA_MARKER."
                )),
                internetAllowed = false,
                requestApproval = { false },
            )
            val fileAnswer = fileRead.reasonOrText()
            section("Teste de acesso a arquivos pelo chat", "Resultado: " + fileRead.javaClass.simpleName + "\nResposta do modelo: " + fileAnswer)
            if (fileRead !is AssistantResult.Success || !fileAnswer.contains("arquivo_local_lido_com_sucesso")) {
                failures.add("O chat não leu/relatou corretamente o marcador do arquivo local.")
            }

            val rainBrief = "Crie um jogo Android 2D original de aventura chamado Tempestade Obsidiana, com atmosfera de fantasia anime e combate simples (inspirado no gênero de aventura de mundo aberto, sem copiar personagens nem assets de jogos existentes). O jogo tem que funcionar offline, desenhar o cenário e as partículas de chuva usando Canvas nativo, ter estado real de jogo, jogador, vida e pontuação, botões de chuva liga/desliga, intensidade de chuva, pausar e reiniciar. Os botões devem modificar a simulação e atualizar textos de estado acessíveis. Siga os IDs/content descriptions solicitados para rain_toggle, rain_intensity, rain_status, rain_intensity_status, game_pause, game_restart e game_status. Explique os controles no próprio app e no README."
            val gameResult = generateAndBuild(
                "Jogo com chuva e controles",
                rainBrief,
                listOf(
                    "desenho/animação Canvas" to Regex("(?s)onDraw\\s*\\(|Canvas"),
                    "botão com listener real" to Regex("setOnClickListener|onTouchEvent"),
                    "partículas/gotas de chuva" to Regex("(?i)rain|chuva|raindrop|gota"),
                    "controle pausa/reinício" to Regex("(?i)pause|paus|restart|reiniciar"),
                    "identificadores acessíveis" to Regex("rain_toggle")
                ),
                "generated-rain-game.apk"
            )
            val gameApk = gameResult.first
            if (gameApk != null) {
                val install = shell("pm uninstall com.example.app 2>&1; pm install -r " + gameApk.absolutePath + " 2>&1")
                section("Execução real do jogo no emulador", "Saída de instalação:\n" + install)
                if (!install.contains("Success", ignoreCase = true)) {
                    failures.add("O APK gerado do jogo não instalou no emulador: " + install)
                } else {
                    val launch = shell("am start -W -n com.example.app/com.example.app.MainActivity 2>&1")
                    SystemClock.sleep(1500)
                    var xml = uiTree()
                    section("Inicialização e controles do jogo", "Saída ao iniciar Activity:\n" + launch)
                    recordUiButtonChecks("UI do jogo", xml, listOf("rain_toggle", "rain_intensity", "game_pause", "game_restart"))
                    val beforeRain = Regex("<node\\b[^>]*/?>").findAll(xml).firstOrNull { it.value.contains("content-desc=\"rain_status\"") }?.value.orEmpty()
                    val clickedRain = tapControl(xml, "rain_toggle")
                    xml = uiTree()
                    val afterRain = Regex("<node\\b[^>]*/?>").findAll(xml).firstOrNull { it.value.contains("content-desc=\"rain_status\"") }?.value.orEmpty()
                    val rainChanged = clickedRain && beforeRain.isNotBlank() && afterRain.isNotBlank() && beforeRain != afterRain
                    section("Mecânica de ligar/desligar chuva", "Clicável encontrado: " + clickedRain + "\nEstado antes: " + beforeRain + "\nEstado depois: " + afterRain + "\nMudança de estado observada: " + rainChanged)
                    if (!rainChanged) failures.add("O toque no controle de chuva não alterou o estado acessível observado.")
                    val intensityBefore = Regex("<node\\b[^>]*/?>").findAll(xml).firstOrNull { it.value.contains("content-desc=\"rain_intensity_status\"") }?.value.orEmpty()
                    val clickedIntensity = tapControl(xml, "rain_intensity")
                    val xmlAfterIntensity = uiTree()
                    val intensityAfter = Regex("<node\\b[^>]*/?>").findAll(xmlAfterIntensity).firstOrNull { it.value.contains("content-desc=\"rain_intensity_status\"") }?.value.orEmpty()
                    val intensityChanged = clickedIntensity && intensityBefore.isNotBlank() && intensityAfter.isNotBlank() && intensityBefore != intensityAfter
                    section("Mecânica de intensidade da chuva", "Clicável encontrado: " + clickedIntensity + "\nEstado antes: " + intensityBefore + "\nEstado depois: " + intensityAfter + "\nMudança observada: " + intensityChanged)
                    if (!intensityChanged) failures.add("O controle de intensidade não alterou o estado acessível observado.")
                    val beforePause = Regex("<node\\b[^>]*/?>").findAll(xmlAfterIntensity).firstOrNull { it.value.contains("content-desc=\"game_status\"") }?.value.orEmpty()
                    val clickedPause = tapControl(xmlAfterIntensity, "game_pause")
                    val xmlAfterPause = uiTree()
                    val afterPause = Regex("<node\\b[^>]*/?>").findAll(xmlAfterPause).firstOrNull { it.value.contains("content-desc=\"game_status\"") }?.value.orEmpty()
                    val pauseChanged = clickedPause && beforePause.isNotBlank() && afterPause.isNotBlank() && beforePause != afterPause
                    section("Pausar e retomar jogo", "Estado antes: " + beforePause + "\nEstado após toque em pausar: " + afterPause + "\nPausa altera o estado: " + pauseChanged)
                    if (!pauseChanged) failures.add("O botão de pausa não alterou o estado acessível observado.")
                    val clickedRestart = tapControl(xmlAfterPause, "game_restart")
                    val xmlAfterRestart = uiTree()
                    section("Reiniciar jogo", "Botão reiniciar clicável: " + clickedRestart + "\nEstado após reinício:\n" + Regex("<node\\b[^>]*/?>").findAll(xmlAfterRestart).firstOrNull { it.value.contains("content-desc=\"game_status\"") }?.value.orEmpty())
                    if (!clickedRestart) failures.add("Não foi possível acionar o botão reiniciar.")
                    shell("screencap -p /sdcard/kellvyn_game_screen.png")
                    shell("pm uninstall com.example.app >/dev/null 2>&1 || true")
                }
            }

            val musicBrief = "Crie um app Android nativo chamado NEXUS DREAD PLAYER, um reprodutor local de música com estética industrial/metálica pesada: fundo preto, verde ácido e azul elétrico, alto contraste e controles grandes. Deve abrir seletor de arquivos de áudio pelo ACTION_OPEN_DOCUMENT/CATEGORY_OPENABLE, reproduzir áudio real selecionado com android.media.MediaPlayer, mostrar nome e duração da faixa, ter Play/Pause, SeekBar para posição, tratamento para nenhum arquivo selecionado, falhas e ciclo de vida/limpeza. Não use faixas online nem bibliotecas externas. Configure content descriptions choose_audio, play_pause, playback_position e track_status para os controles testáveis."
            val musicResult = generateAndBuild(
                "Player musical de estilo pesado",
                musicBrief,
                listOf(
                    "seletor nativo de arquivo" to Regex("ACTION_OPEN_DOCUMENT"),
                    "MediaPlayer real" to Regex("android\\.media\\.MediaPlayer|new MediaPlayer"),
                    "SeekBar de posição" to Regex("SeekBar"),
                    "tratamento de lifecycle/liberação" to Regex("release\\(|onDestroy")
                ),
                "generated-music-player.apk"
            )
            val musicApk = musicResult.first
            if (musicApk != null) {
                val install = shell("pm uninstall com.example.app 2>&1; pm install -r " + musicApk.absolutePath + " 2>&1")
                section("Execução real do player no emulador", "Saída de instalação:\n" + install)
                if (!install.contains("Success", ignoreCase = true)) {
                    failures.add("O APK gerado do player não instalou no emulador: " + install)
                } else {
                    val launch = shell("am start -W -n com.example.app/com.example.app.MainActivity 2>&1")
                    SystemClock.sleep(1200)
                    var xml = uiTree()
                    section("Inicialização e controles do player", "Saída ao iniciar Activity:\n" + launch)
                    recordUiButtonChecks("UI do player", xml, listOf("choose_audio", "play_pause", "playback_position"))
                    val openedPicker = tapControl(xml, "choose_audio")
                    SystemClock.sleep(1000)
                    val pickerXml = uiTree()
                    val pickerVisible = pickerXml.contains("com.android.documentsui") ||
                        pickerXml.contains("Recent", ignoreCase = true) ||
                        pickerXml.contains("Browse", ignoreCase = true)
                    section("Seletor de áudio local", "Toque encontrado: " + openedPicker + "\nPicker do Android reconhecido: " + pickerVisible + "\n\n" + pickerXml.take(6000))
                    if (!openedPicker || !pickerVisible) failures.add("O botão Escolher áudio não abriu um seletor do Android reconhecível.")
                    shell("input keyevent 4")
                    SystemClock.sleep(500)
                    xml = uiTree()
                    tapControl(xml, "play_pause")
                    val statusAfterPlay = shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | tail -n 2")
                    section("Play/Pause sem faixa selecionada", "O teste tocou o controle se exposto; estado da Activity:\n" + statusAfterPlay)
                    shell("screencap -p /sdcard/kellvyn_music_screen.png")
                    shell("pm uninstall com.example.app >/dev/null 2>&1 || true")
                }
            }

            section("Resumo de falhas observadas", if (failures.isEmpty()) "Nenhuma falha automática registrada." else failures.mapIndexed { i, item -> (i + 1).toString() + ". " + item }.joinToString("\n"))
            report.append("\n## Limitações verificadas\n\n")
            report.append("- O núcleo usado aqui gera texto. Não gera PNG/JPEG nem pode ver uma imagem de referência.\n")
            report.append("- O build local inicial compila Java com APIs Android, não Kotlin/Compose ou dependências arbitrárias.\n")
            report.append("- Não foram usadas credenciais reais de serviços externos.\n")
            persist()

            assertTrue(
                "A bateria encontrou problemas. O relatório completo está em " + reportFile.absolutePath + ":\n" + failures.joinToString("\n"),
                failures.isEmpty()
            )
        } finally {
            createdProjectIds.forEach { id ->
                workspace.deleteProject(id)
                File(buildRoot, id).deleteRecursively()
                File(buildRoot, id + ".apk.tmp").delete()
                File(buildRoot, id + ".apk.previous").delete()
            }
            originalLicense?.let { accepted ->
                licensePreferences.edit().putBoolean("android_sdk_license_accepted", accepted).apply()
            }
            persist()
            engine.close()
        }
    }

    @Test
    fun discordBotRequestCreatesExportableSourceAndNeverClaimsItIsOnline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val projectJson = JSONObject()
            .put("name", "Discord Ping Bot Smoke Test")
            .put("description", "Fixture de teste do gerador de bots do Discord.")
            .put("files", JSONArray().apply {
                put(JSONObject().put("path", "package.json").put(
                    "content",
                    """{"name":"kellvyn-discord-ping-smoke","version":"1.0.0","private":true,"main":"src/index.js","scripts":{"start":"node src/index.js","register":"node src/register-commands.js"},"dependencies":{"discord.js":"^14.0.0"}}"""
                ))
                put(JSONObject().put("path", "src/index.js").put("content", """
                    const { Client, Events } = require('discord.js');
                    const token = process.env.DISCORD_TOKEN;
                    if (!token) throw new Error('Configure DISCORD_TOKEN before starting this bot.');
                    const client = new Client({ intents: [] });
                    client.once(Events.ClientReady, ready => console.log('Bot ready as ' + ready.user.tag));
                    client.on(Events.InteractionCreate, async interaction => {
                      if (!interaction.isChatInputCommand() || interaction.commandName !== 'ping') return;
                      await interaction.reply({ content: 'Pong!', ephemeral: true });
                    });
                    client.login(token).catch(error => {
                      console.error('Discord login failed; check credentials and network.');
                      process.exitCode = 1;
                    });
                """.trimIndent()))
                put(JSONObject().put("path", "src/register-commands.js").put("content", """
                    const { REST, Routes, SlashCommandBuilder } = require('discord.js');
                    const { DISCORD_TOKEN, DISCORD_CLIENT_ID, DISCORD_GUILD_ID } = process.env;
                    if (!DISCORD_TOKEN || !DISCORD_CLIENT_ID || !DISCORD_GUILD_ID) {
                      throw new Error('Set DISCORD_TOKEN, DISCORD_CLIENT_ID and DISCORD_GUILD_ID.');
                    }
                    const command = new SlashCommandBuilder().setName('ping').setDescription('Responde Pong!');
                    const rest = new REST({ version: '10' }).setToken(DISCORD_TOKEN);
                    rest.put(Routes.applicationGuildCommands(DISCORD_CLIENT_ID, DISCORD_GUILD_ID), {
                      body: [command.toJSON()]
                    }).then(() => console.log('Slash command registered.'))
                      .catch(() => { console.error('Command registration failed.'); process.exitCode = 1; });
                """.trimIndent()))
                put(JSONObject().put("path", "kellvyn.bot.json").put("content", """{"platform":"discord","name":"Discord Ping Bot","commands":[{"name":"ping","description":"Responde Pong!","action":"ping","response":""},{"name":"dado","description":"Rola um dado d20","action":"roll_dice","response":""},{"name":"moeda","description":"Lança uma moeda","action":"coin_flip","response":""}]}"""))
                put(JSONObject().put("path", ".env.example").put(
                    "content",
                    listOf("DISCORD_TOKEN=", "DISCORD_CLIENT_ID=", "DISCORD_GUILD_ID=").joinToString("\n")
                ))
                put(JSONObject().put("path", "README.md").put("content", """
                    # Discord Ping Bot
                    Requer Node.js 22/24 e uma aplicação/bot criado no Discord Developer Portal.
                    Configure as variáveis de ambiente usando .env.example como referência.
                    Rode npm install, depois npm run register e npm start.
                    Convide o bot somente com os escopos bot e applications.commands e as permissões mínimas.
                    Este projeto é código-fonte: o teste do construtor não autentica nem hospeda o bot.
                """.trimIndent()))
            })
            .toString()
        val engine = BotFixtureEngine(projectJson)
        val result = AppBuilderAgent(workspace).createProject(
            engine,
            "Crie um bot do Discord com comando slash /ping"
        )
        assertTrue("O construtor não salvou a fonte do bot: $result", result is AppBuildResult.Success)
        val project = (result as AppBuildResult.Success).project
        try {
            val paths = workspace.listFiles(project.id).toSet()
            assertTrue(paths.contains("package.json"))
            assertTrue(paths.contains("src/index.js"))
            assertTrue(paths.contains("src/register-commands.js"))
            assertTrue(paths.contains(".env.example"))
            assertTrue(paths.contains("README.md"))
            assertTrue(paths.contains("kellvyn.bot.json"))
            val packageText = workspace.readFile(project.id, "package.json")
            assertEquals(GeneratedProjectKind.DISCORD_BOT, GeneratedProjectClassifier.classify(paths, packageText, project.description, project.name))
            val definition = DiscordBotDefinition.load(workspace, project.id, project.name)
            assertEquals(listOf("ping", "dado", "moeda"), definition.commands.map { it.name })
            assertEquals(listOf("ping", "roll_dice", "coin_flip"), definition.commands.map { it.action })
            assertTrue(workspace.readFile(project.id, "src/index.js").contains("Pong!"))
            assertTrue(workspace.readFile(project.id, ".env.example").contains("DISCORD_TOKEN="))
            assertTrue(workspace.readFile(project.id, "README.md").contains("não autentica nem hospeda"))
            assertFalse("O projeto do bot não deve ser empacotado como app Android.", paths.contains("app/src/main/AndroidManifest.xml"))
        } finally {
            workspace.deleteProject(project.id)
        }
    }

    @Test
    fun discordCredentialsStayEncryptedAndRuntimeTogglePersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectId = "bot-keystore-test"
        val credentials = DiscordBotCredentialsStore(context)
        val runtime = GeneratedProjectRuntimeStore(context)
        val fakeToken = "NOT-A-REAL-DISCORD-TOKEN-1234567890"
        credentials.remove(projectId)
        runtime.setActive(projectId, false)
        try {
            credentials.save(projectId, fakeToken, "123456789012345678")
            assertEquals(fakeToken, credentials.get(projectId)?.token)
            assertEquals("123456789012345678", credentials.get(projectId)?.applicationId)
            val rawStoredCiphertext = context.getSharedPreferences("kellvyn_discord_bot_credentials", Context.MODE_PRIVATE)
                .getString("token_" + projectId, "")
            assertFalse("O token não pode estar gravado em texto puro.", rawStoredCiphertext.orEmpty().contains(fakeToken))
            runtime.setActive(projectId, true)
            assertTrue(runtime.isActive(projectId))
            runtime.setActive(projectId, false)
            assertFalse(runtime.isActive(projectId))
        } finally {
            credentials.remove(projectId)
            runtime.setActive(projectId, false)
        }
    }

    @Test
    fun androidTerminalRunsWorkspaceCommandsAndExplicitManualShell() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val terminal = WorkspaceTerminal(workspace)
        val smokeDirectoryName = "qa_file_access_smoke"
        val smokeDirectory = File(workspace.workspaceDirectory(), smokeDirectoryName)
        smokeDirectory.deleteRecursively()
        try {
            assertEquals("/workspace", terminal.execute("pwd"))
            val output = terminal.executeInteractive("echo KELLVYN_ANDROID_SHELL_SMOKE")
            assertTrue("O shell Android não retornou a saída esperada: $output", output.contains("KELLVYN_ANDROID_SHELL_SMOKE"))
            assertTrue("O shell Android não informou o estado do processo: $output", output.contains("exit code: 0"))
            assertTrue("O terminal deve explicar que não executou Gradle nem compilou esse comando.",
                terminal.execute("gradle assembleDebug").contains("nenhum build foi executado por este comando"))

            assertTrue(terminal.execute("mkdir $smokeDirectoryName").contains("criada", ignoreCase = true))
            terminal.execute("write $smokeDirectoryName/roundtrip.txt KELLVYN_FILE_ACCESS_ROUNDTRIP")
            assertTrue(
                "O terminal não conseguiu reler um arquivo criado no workspace.",
                terminal.execute("cat $smokeDirectoryName/roundtrip.txt").contains("KELLVYN_FILE_ACCESS_ROUNDTRIP")
            )
            val denied = runCatching { terminal.execute("cat ../../etc/passwd") }.exceptionOrNull()
            assertTrue("O terminal deixou escapar um caminho para fora do workspace.", denied?.message.orEmpty().contains("sai do workspace"))
        } finally {
            smokeDirectory.deleteRecursively()
        }
    }

    @Test
    fun mainChatGivesOrganizedTermuxGuidanceWithoutExecutingCommands() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val terminal = WorkspaceTerminal(workspace)
        val engine = MainChatTerminalGuidanceFixtureEngine()
        val result = AssistantAgent(AgentToolRouter(workspace)).respond(
            engine = engine,
            conversation = listOf(AssistantTurn(
                AssistantTurn.Role.USER,
                "Me passe o comando para eu executar echo KELLVYN_AGENT_TERMINAL no Termux, organizado para copiar."
            )),
            internetAllowed = false,
            requestApproval = { false },
        )
        assertTrue("O chat principal não respondeu ao pedido de ajuda com Termux: $result", result is AssistantResult.Success)
        assertEquals("A orientação manual deve ser respondida no chat sem acionar ferramenta.", 1, engine.calls)
        assertTrue((result as AssistantResult.Success).text.contains("copiar"))
        assertTrue("O chat não deixou claro que não executou o comando.", result.text.contains("não executei"))
        assertEquals("O teste de orientação não deve executar nada no terminal.", "/workspace", terminal.execute("pwd"))
    }

    private class MainChatTerminalGuidanceFixtureEngine : AssistantEngine {
        var calls: Int = 0
            private set
        override val displayName: String = "main-chat-terminal-guidance-test-fixture"
        override suspend fun isAvailable(): Boolean = true

        override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult {
            calls++
            val system = conversation.firstOrNull { it.role == AssistantTurn.Role.SYSTEM }?.content.orEmpty()
            assertTrue("O chat não recebeu a política de ajuda manual para o terminal.", system.contains("O terminal é uma ferramenta manual opcional"))
            assertFalse("O chat não deve oferecer um agente para executar comandos no terminal.", system.contains("- terminal_execute {command}"))
            assertTrue("A política não exige que o chat organize comandos para copiar.", system.contains("organize comandos e arquivos completos"))
            return AssistantResult.Success(
                """{"kind":"answer","text":"Copie este comando no Termux: echo KELLVYN_AGENT_TERMINAL. Eu não executei o comando; estou apenas preparando a instrução para você copiar."}"""
            )
        }
    }

    private class BotFixtureEngine(private val response: String) : AssistantEngine {
        override val displayName: String = "discord-bot-test-fixture"

        override suspend fun isAvailable(): Boolean = true

        override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult {
            val prompt = conversation.lastOrNull()?.content.orEmpty()
            assertTrue("O construtor não marcou o pedido como fonte de bot, não APK.", prompt.contains("não um APK Android"))
            assertTrue("O construtor não especificou discord.js.", prompt.contains("discord.js"))
            assertTrue("O construtor não exigiu comando slash /ping.", prompt.contains("comando slash /ping"))
            assertTrue("O construtor não protegeu o token do bot.", prompt.contains("DISCORD_TOKEN"))
            assertTrue("O construtor não solicitou um manifesto para o runtime nativo.", prompt.contains("kellvyn.bot.json"))
            assertTrue("O construtor não limitou o manifesto às ações executáveis.", prompt.contains("roll_dice"))
            assertTrue("O pedido original não chegou ao construtor.", prompt.contains("Crie um bot do Discord com comando slash /ping"))
            return AssistantResult.Success(response)
        }
    }

    private class FixtureEngine(private val response: String) : AssistantEngine {
        override val displayName: String = "test-fixture-engine"

        override suspend fun isAvailable(): Boolean = true

        override suspend fun reply(conversation: List<AssistantTurn>): AssistantResult {
            val prompt = conversation.lastOrNull()?.content.orEmpty()
            assertTrue(
                "O construtor deixou de instruir o modelo a gerar Java para a compilação no aparelho.",
                prompt.contains("compila Java diretamente neste aparelho")
            )
            assertTrue("O prompt do construtor não proíbe WebView.", prompt.contains("Não crie WebView"))
            assertTrue("O prompt do construtor não proíbe build remoto.", prompt.contains("Não use compilação remota"))
            return AssistantResult.Success(response)
        }
    }

    private fun validJavaAndroidProject(): Map<String, String> = mapOf(
        "settings.gradle.kts" to """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "Compiler Smoke Test"
            include(":app")
        """.trimIndent(),
        "build.gradle.kts" to """
            plugins {
                id("com.android.application") version "8.7.3" apply false
            }
        """.trimIndent(),
        "gradle.properties" to """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            org.gradle.parallel=false
            org.gradle.workers.max=2
        """.trimIndent(),
        "app/build.gradle.kts" to """
            plugins { id("com.android.application") }
            android {
                namespace = "com.example.app"
                compileSdk = 35
                defaultConfig {
                    applicationId = "com.example.app"
                    minSdk = 26
                    targetSdk = 35
                }
            }
        """.trimIndent(),
        "app/src/main/AndroidManifest.xml" to """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="Compiler Smoke Test">
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent(),
        "app/src/main/java/com/example/app/MainActivity.java" to """
            package com.example.app;

            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;

            public class MainActivity extends Activity {
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    TextView title = new TextView(this);
                    title.setText("Kellvyn local compiler smoke test");
                    setContentView(title);
                }
            }
        """.trimIndent(),
        "README.md" to "Kellvyn local compiler smoke test. This project uses a real native Android Activity written in Java and the local ECJ, D8, manifest and APK signing pipeline."
    )
}
