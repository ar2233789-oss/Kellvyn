package com.kellvyn.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Optional ambient rain for the interface; it runs only while enabled and draws no bitmaps. */
@Composable
internal fun StartupRainOverlay(
    modifier: Modifier = Modifier,
    accent: Color
) {
    val transition = rememberInfiniteTransition(label = "kellvyn-startup-rain")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing)
        ),
        label = "rain-phase"
    )

    Canvas(modifier = modifier.alpha(0.30f)) {
        val count = ((size.width * size.height) / 22000f).toInt().coerceIn(28, 100)
        val stroke = 0.8.dp.toPx()
        for (index in 0 until count) {
            val xUnit = ((index * 73) % 997) / 997f
            val yBase = ((index * 127) % 991) / 991f
            val y = ((yBase + phase) % 1f) * size.height
            val x = xUnit * size.width
            drawLine(
                color = accent.copy(alpha = 0.34f),
                start = Offset(x, y),
                end = Offset(x - 3.5.dp.toPx(), y + 14.dp.toPx()),
                strokeWidth = stroke
            )
        }
    }
}
