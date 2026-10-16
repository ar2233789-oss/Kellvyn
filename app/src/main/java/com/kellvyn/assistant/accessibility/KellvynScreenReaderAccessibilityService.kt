package com.kellvyn.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicitly user-enabled service. It does not log screen contents or upload anything.
 * Text is traversed only when the user taps a control in the floating Kellvyn assistant.
 */
class KellvynScreenReaderAccessibilityService : AccessibilityService() {
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var captureInFlight = false
    private val gameCaptureLoop = object : Runnable {
        override fun run() {
            if (!isGameModeEnabled()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) captureGameFrame()
            mainHandler.postDelayed(this, GAME_CAPTURE_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        refreshGameCaptureLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen reading remains on-demand. Auto-capture runs only after the user enables Game Mode.
        if (isGameModeEnabled()) refreshGameCaptureLoop()
    }

    private fun isGameModeEnabled(): Boolean =
        getSharedPreferences("kellvyn_ui", MODE_PRIVATE).getBoolean("game_mode_enabled", false)

    private fun refreshGameCaptureLoop() {
        mainHandler.removeCallbacks(gameCaptureLoop)
        if (!isGameModeEnabled()) {
            latestScreenshotPath = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mainHandler.postDelayed(gameCaptureLoop, 350L)
        }
    }

    private fun captureGameFrame() {
        if (captureInFlight || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        captureInFlight = true
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val colorSpace = screenshot.colorSpace
                    captureScope.launch {
                        try {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                                ?: throw IllegalStateException("O Android não disponibilizou pixels para o quadro do jogo.")
                            val bitmap = try {
                                hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: throw IllegalStateException("Não consegui preparar o quadro do jogo para análise.")
                            } finally {
                                hardwareBitmap.recycle()
                            }
                            try {
                                val folder = File(filesDir, "kellvyn_game_capture")
                                check(folder.isDirectory || folder.mkdirs()) { "Não consegui preparar o armazenamento do modo de jogo." }
                                val temporary = File(folder, "latest.jpg.part")
                                val destination = File(folder, "latest.jpg")
                                temporary.outputStream().use { output ->
                                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                                        "Não consegui salvar a captura automática do jogo."
                                    }
                                }
                                if (!temporary.renameTo(destination)) {
                                    destination.delete()
                                    check(temporary.renameTo(destination)) { "Não consegui concluir a captura automática do jogo." }
                                }
                                latestScreenshotPath = destination.absolutePath
                            } finally {
                                bitmap.recycle()
                            }
                        } catch (_: Exception) {
                            // Keep the last successful frame; failures must not crash the service.
                        } finally {
                            runCatching { buffer.close() }
                            withContext(Dispatchers.Main.immediate) { captureInFlight = false }
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    captureInFlight = false
                }
            })
        } catch (_: Exception) {
            captureInFlight = false
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(gameCaptureLoop)
        captureScope.cancel()
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    fun captureVisibleText(maxChars: Int = 10_000): String {
        val root = rootInActiveWindow ?: return ""
        val output = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && output.length < maxChars && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            val text = sequenceOf(node.text, node.contentDescription, node.hintText)
                .mapNotNull { it?.toString()?.trim() }
                .firstOrNull { it.isNotBlank() }
            if (text != null && !output.contains(text)) {
                if (output.isNotEmpty()) output.append('\n')
                output.append(text.take(MAX_NODE_TEXT_CHARS))
            }
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull()
                if (child != null) stack.add(child)
            }
        }
        return output.toString().take(maxChars)
    }

    companion object {
        @Volatile private var activeService: KellvynScreenReaderAccessibilityService? = null
        @Volatile private var latestScreenshotPath: String? = null

        private const val GAME_CAPTURE_INTERVAL_MS = 2_200L

        fun onGameModePreferenceChanged() {
            activeService?.refreshGameCaptureLoop()
        }

        fun latestGameScreenshotPath(): String? {
            val path = latestScreenshotPath ?: return null
            val file = File(path)
            return path.takeIf { file.isFile && file.length() > 0L }
        }

        fun readCurrentScreen(): String? {
            val service = activeService ?: return null
            return service.captureVisibleText()
        }

        fun isEnabledInProcess(): Boolean = activeService != null

        private const val MAX_NODES = 1_200
        private const val MAX_NODE_TEXT_CHARS = 600
    }
}
