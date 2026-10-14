package com.kellvyn.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Explicitly user-enabled service. It does not log screen contents or upload anything.
 * Text is traversed only when the user taps a control in the floating Kellvyn assistant.
 */
class KellvynScreenReaderAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally do not scrape events continuously. Capture is on-demand from the bubble.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
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

        fun readCurrentScreen(): String? {
            val service = activeService ?: return null
            return service.captureVisibleText()
        }

        fun isEnabledInProcess(): Boolean = activeService != null

        private const val MAX_NODES = 1_200
        private const val MAX_NODE_TEXT_CHARS = 600
    }
}
