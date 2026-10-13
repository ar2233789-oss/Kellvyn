package com.kellvyn.assistant.workspace

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAndroidProjectValidatorTest {
    @Test
    fun acceptsMinimalNativeJavaProject() {
        assertNull(GeneratedAndroidProjectValidator.validate(validProject()))
    }

    @Test
    fun rejectsWrongPackage() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/MainActivity.java"] =
            files.getValue("app/src/main/java/com/example/app/MainActivity.java")
                .replace("package com.example.app;", "package com.other.app;")
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("MainActivity.java"))
    }

    @Test
    fun rejectsMissingLauncherIntent() {
        val files = validProject().toMutableMap()
        files["app/src/main/AndroidManifest.xml"] =
            files.getValue("app/src/main/AndroidManifest.xml").replace("android.intent.action.MAIN", "android.intent.action.VIEW")
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("MAIN/LAUNCHER"))
    }

    @Test
    fun rejectsNonExportedLauncher() {
        val files = validProject().toMutableMap()
        files["app/src/main/AndroidManifest.xml"] =
            files.getValue("app/src/main/AndroidManifest.xml").replace("android:exported=\"true\"", "")
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("exported=true"))
    }

    @Test
    fun rejectsKotlinSourceForFirstLocalCompiler() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/Other.kt"] = "package com.example.app"
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("fontes Java"))
    }

    @Test
    fun rejectsExternalDependenciesForFirstCompiler() {
        val files = validProject().toMutableMap()
        files["app/build.gradle.kts"] = files.getValue("app/build.gradle.kts") +
            "\ndependencies { implementation(\"some:library:1.0\") }\n"
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("dependências externas"))
    }

    @Test
    fun rejectsWebView() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/MainActivity.java"] =
            files.getValue("app/src/main/java/com/example/app/MainActivity.java")
                .replace("package com.example.app;", "package com.example.app;\nimport android.webkit.WebView;")
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("WebView"))
    }

    @Test
    fun rejectsEmptyActivityWithoutNativeControls() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/MainActivity.java"] =
            "package com.example.app; public class MainActivity extends Activity { }"
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("setContentView"))
    }

    @Test
    fun rejectsPlaceholderComments() {
        val files = validProject().toMutableMap()
        files["app/src/main/java/com/example/app/MainActivity.java"] =
            files.getValue("app/src/main/java/com/example/app/MainActivity.java")
                .replace("public class MainActivity", "// TODO connect storage\npublic class MainActivity")
        assertTrue(GeneratedAndroidProjectValidator.validate(files).orEmpty().contains("placeholder"))
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
                <application android:label="Demo">
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
                @Override public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    TextView title = new TextView(this);
                    title.setText("Hello Kellvyn");
                    setContentView(title);
                }
            }
        """.trimIndent(),
        "README.md" to "Projeto Demo. Compile o APK nativo usando o construtor local do Kellvyn; esta aplicação tem uma Activity Java."
    )
}
