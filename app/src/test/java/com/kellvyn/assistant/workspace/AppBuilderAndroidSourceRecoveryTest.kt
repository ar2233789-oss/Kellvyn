package com.kellvyn.assistant.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBuilderAndroidSourceRecoveryTest {
    @Test
    fun extractsFencedJavaNormalizesPackageAndScaffoldsValidProject() {
        val response = """
            Aqui está o código solicitado:
            ```java
            package generated.demo;
            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;
            public class MainActivity extends Activity {
                @Override public void onCreate(Bundle state) {
                    super.onCreate(state);
                    TextView title = new TextView(this);
                    title.setText("App real");
                    setContentView(title);
                }
            }
            ```
        """.trimIndent()

        val activity = AppBuilderAgent.extractCompleteAndroidActivity(response)
        assertNotNull(activity)
        assertTrue(activity!!.contains("package com.example.app;"))
        assertFalse(activity.contains("package generated.demo;"))

        val files = AppBuilderAgent.createAndroidProjectFiles(
            name = "App de teste",
            idea = "Crie um app Android para validar extração Java e controles reais.",
            rawActivity = response
        )
        assertNotNull(files)
        assertEquals(7, files!!.size)
        assertNull(GeneratedAndroidProjectValidator.validate(files.associate { it.path to it.content }))
    }

    @Test
    fun rejectsIncompleteOrNonActivityModelOutput() {
        assertNull(AppBuilderAgent.extractCompleteAndroidActivity("Aqui está um aplicativo incrível."))
        assertNull(
            AppBuilderAgent.extractCompleteAndroidActivity(
                "package com.example.app; public class MainActivity extends Activity { void onCreate() { setContentView(new View(this));"
            )
        )
    }

    @Test
    fun diagnosesIncompleteActivityWithTheSpecificMissingPiece() {
        assertTrue(
            AppBuilderAgent.diagnoseAndroidActivity("Aqui está uma ideia de aplicativo.")
                .contains("não encontrei a declaração da classe MainActivity")
        )
        assertTrue(
            AppBuilderAgent.diagnoseAndroidActivity(
                "package com.example.app; public class MainActivity extends Activity { void onCreate() { setContentView(new TextView(this));"
            ).contains("bloco da classe está incompleto")
        )
        assertTrue(
            AppBuilderAgent.diagnoseAndroidActivity(
                "package com.example.app; public class MainActivity extends Activity { void onCreate() { setContentView(new View(this)); } }"
            ).contains("nenhum controle nativo visível")
        )
    }

    @Test
    fun scaffoldsCompleteProjectFromAPlainJavaResponse() {
        val response = """
            package another.package;
            import android.app.Activity;
            import android.os.Bundle;
            import android.widget.TextView;
            public class MainActivity extends Activity {
                @Override public void onCreate(Bundle state) {
                    super.onCreate(state);
                    TextView title = new TextView(this);
                    title.setText("Fixture");
                    setContentView(title);
                }
            }
        """.trimIndent()
        val files = AppBuilderAgent.createAndroidProjectFiles(
            name = "Fixture",
            idea = "Crie um app Android mínimo para testar o compilador local.",
            rawActivity = response,
            generatedReadme = "A fixture README has sufficient details to describe the native Activity, source path, and local compilation workflow."
        )
        assertNotNull(files)
        assertEquals(7, files!!.size)
        assertNull(GeneratedAndroidProjectValidator.validate(files.associate { it.path to it.content }))
        assertTrue(files.any { it.path == "app/build.gradle.kts" })
        assertTrue(files.any { it.path == "app/src/main/AndroidManifest.xml" })
    }
}
