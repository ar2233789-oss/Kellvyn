package com.kellvyn.assistant.workspace

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAndroidProjectValidatorTest {
    @Test
    fun acceptsMinimalConsistentComposeProject() {
        assertNull(GeneratedAndroidProjectValidator.validate(validProject()))
    }

    @Test
    fun rejectsNamespaceAndKotlinPackageMismatch() {
        val files = validProject().toMutableMap()
        files["app/build.gradle.kts"] = files.getValue("app/build.gradle.kts")
            .replace("namespace = \"com.example.app\"", "namespace = \"com.wrong.package\"")
        files["app/src/main/java/com/example/app/MainActivity.kt"] =
            files.getValue("app/src/main/java/com/example/app/MainActivity.kt")
                .replace("package com.example.app", "package com.wrong.package")

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("namespace"))
        assertTrue(issue.contains("pacote correto"))
    }

    @Test
    fun rejectsInconsistentJavaAndKotlinTargets() {
        val files = validProject().toMutableMap()
        files["app/build.gradle.kts"] = files.getValue("app/build.gradle.kts")
            .replace("JavaVersion.VERSION_17", "JavaVersion.VERSION_1_8")
            .replace("JvmTarget.JVM_17", "JvmTarget.JVM_1_8")

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("JVM 17"))
    }

    @Test
    fun rejectsKotlinAndComposeVersionMismatch() {
        val files = validProject().toMutableMap()
        files["build.gradle.kts"] = files.getValue("build.gradle.kts")
            .replace(
                "id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.4.0\"",
                "id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.3.0\""
            )

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("Kotlin/Compose"))
    }

    @Test
    fun rejectsLauncherWithoutMainLauncherIntent() {
        val files = validProject().toMutableMap()
        files["app/src/main/AndroidManifest.xml"] =
            files.getValue("app/src/main/AndroidManifest.xml")
                .replace("android.intent.action.MAIN", "android.intent.action.VIEW")

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("MAIN/LAUNCHER"))
    }

    @Test
    fun rejectsLauncherWithoutExportedFlag() {
        val files = validProject().toMutableMap()
        files["app/src/main/AndroidManifest.xml"] =
            files.getValue("app/src/main/AndroidManifest.xml").replace("android:exported=\"true\"", "")

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("AndroidManifest.xml"))
    }

    @Test
    fun allowsLegitimatePlaceholderVariableInUiSource() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/SecondActivity.kt"] = """
            package com.example.app
            val placeholder = "Digite seu nome"
            val label = "Campo de busca"
        """.trimIndent()

        assertNull(GeneratedAndroidProjectValidator.validate(files))
    }

    @Test
    fun rejectsFixmeCommentsAndNotImplementedCalls() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/SecondActivity.kt"] = """
            package com.example.app
            // FIXME: wire this screen to storage
            fun pending() = TODO("implement later")
        """.trimIndent()

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("placeholder"))
    }

    @Test
    fun rejectsTodoCommentsInAndroidXml() {
        val files = validProject().toMutableMap()
        files["app/src/main/res/layout/activity_main.xml"] = """
            <!-- TODO connect the buttons -->
            <LinearLayout />
        """.trimIndent()

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("placeholder"))
    }

    @Test
    fun rejectsEmptyComposeContent() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/MainActivity.kt"] =
            files.getValue("app/src/main/java/com/example/app/MainActivity.kt")
                .replace("setContent { Text(\"Hello Kellvyn\") }", "setContent { }")

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("setContent vazio"))
    }

    @Test
    fun rejectsObviousCodePlaceholdersAnywhereInSourceFiles() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/SecondActivity.kt"] =
            "package com.example.app\n// TODO implement screen"

        val issue = GeneratedAndroidProjectValidator.validate(files)
        assertNotNull(issue)
        assertTrue(issue!!.contains("placeholder"))
    }

    private fun validProject(): Map<String, String> = mapOf(
        "settings.gradle.kts" to """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "Demo"
            include(":app")
        """.trimIndent(),
        "build.gradle.kts" to """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.4.0" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
            }
        """.trimIndent(),
        "gradle.properties" to """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            org.gradle.parallel=false
            org.gradle.workers.max=2
            kotlin.compiler.execution.strategy=in-process
        """.trimIndent(),
        "app/build.gradle.kts" to """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }
            android {
                namespace = "com.example.app"
                compileSdk = 35
                defaultConfig {
                    applicationId = "com.example.app"
                    minSdk = 26
                    targetSdk = 35
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
            kotlin {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
            dependencies {
                implementation(platform("androidx.compose:compose-bom:2024.12.01"))
                implementation("androidx.activity:activity-compose:1.10.0")
            }
        """.trimIndent(),
        "app/src/main/AndroidManifest.xml" to """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent(),
        "app/src/main/java/com/example/app/MainActivity.kt" to """
            package com.example.app
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.material3.Text
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent { Text("Hello Kellvyn") }
                }
            }
        """.trimIndent(),
        "README.md" to "A simple demo Android application. Open the project in a compatible Android IDE, sync Gradle, then build and install it."
    )
}
