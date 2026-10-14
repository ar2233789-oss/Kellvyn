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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
            assertEquals("success", "success".takeIf { buildResult.optString("status") == "success" } ?: toolJson)
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
