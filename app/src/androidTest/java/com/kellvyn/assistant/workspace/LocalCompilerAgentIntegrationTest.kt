package com.kellvyn.assistant.workspace

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kellvyn.assistant.ai.AgentToolRouter
import com.kellvyn.assistant.ai.AssistantEngine
import com.kellvyn.assistant.ai.AssistantResult
import com.kellvyn.assistant.ai.AssistantTurn
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
                put(JSONObject().put("path", ".env.example").put(
                    "content",
                    "DISCORD_TOKEN=\\nDISCORD_CLIENT_ID=\\nDISCORD_GUILD_ID=\\n"
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
            assertTrue(workspace.readFile(project.id, "src/index.js").contains("Pong!"))
            assertTrue(workspace.readFile(project.id, ".env.example").contains("DISCORD_TOKEN="))
            assertTrue(workspace.readFile(project.id, "README.md").contains("não autentica nem hospeda"))
            assertFalse("O projeto do bot não deve ser empacotado como app Android.", paths.contains("app/src/main/AndroidManifest.xml"))
        } finally {
            workspace.deleteProject(project.id)
        }
    }

    @Test
    fun androidTerminalRunsWorkspaceCommandsAndExplicitManualShell() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = ProjectWorkspaceStore(context)
        val terminal = WorkspaceTerminal(workspace)
        assertEquals("/workspace", terminal.execute("pwd"))
        val output = terminal.executeInteractive("echo KELLVYN_ANDROID_SHELL_SMOKE")
        assertTrue("O shell Android não retornou a saída esperada: $output", output.contains("KELLVYN_ANDROID_SHELL_SMOKE"))
        assertTrue("O shell Android não informou o estado do processo: $output", output.contains("exit code: 0"))
        assertFalse("O terminal não deve reportar comandos de shell como executados pelo interpretador de workspace.",
            terminal.execute("gradle assembleDebug").contains("nenhum build foi executado por este comando").not())
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
