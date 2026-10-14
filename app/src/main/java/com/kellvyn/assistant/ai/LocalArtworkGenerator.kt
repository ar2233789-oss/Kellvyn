package com.kellvyn.assistant.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Offline procedural illustration renderer. This creates a genuine PNG file from shapes/particles;
 * it is deliberately described as procedural artwork, not as a neural image-generation model.
 */
class LocalArtworkGenerator(context: Context) {
    private val appContext = context.applicationContext

    fun generate(prompt: String): File {
        val normalized = prompt.lowercase()
        val seed = prompt.hashCode().toLong() xor System.nanoTime()
        val random = Random(seed)
        val side = IMAGE_SIDE
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val purple = "roxo" in normalized || "violet" in normalized || "violeta" in normalized
        val red = "vermelho" in normalized || "red" in normalized || "sangue" in normalized
        val gold = "dourad" in normalized || "ouro" in normalized || "gold" in normalized
        val accent = when {
            purple -> Color.rgb(176, 88, 255)
            red -> Color.rgb(255, 63, 104)
            gold -> Color.rgb(255, 195, 82)
            else -> Color.rgb(45, 231, 190)
        }
        val blue = Color.rgb(42, 130, 255)
        val rain = listOf("chuva", "rain", "tempestade", "storm", "molhad").any { it in normalized }
        val gothic = listOf("gótico", "gotico", "gothic", "fantasia", "fantasy", "castelo", "castle", "catedral").any { it in normalized }
        val montage = listOf("montagem", "montage", "colagem", "collage", "tríptico", "triptico").any { it in normalized }

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, side.toFloat(), side.toFloat(),
                intArrayOf(Color.rgb(3, 7, 23), Color.rgb(10, 20, 48), Color.rgb(15, 4, 32)),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), background)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val glowX = side * (0.34f + random.nextFloat() * 0.32f)
        val glowY = side * (0.24f + random.nextFloat() * 0.18f)
        glowPaint.shader = RadialGradient(
            glowX, glowY, side * 0.52f,
            intArrayOf(withAlpha(accent, 105), withAlpha(blue, 40), Color.TRANSPARENT),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), glowPaint)
        glowPaint.shader = null

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        repeat(420) {
            val radius = if (random.nextFloat() < 0.12f) random.nextFloat() * 2.6f + 0.6f else random.nextFloat() * 1.1f
            starPaint.alpha = random.nextInt(55, 220)
            canvas.drawCircle(random.nextFloat() * side, random.nextFloat() * side * 0.76f, radius, starPaint)
        }

        val moonRadius = side * (0.075f + random.nextFloat() * 0.045f)
        val moonX = if (random.nextBoolean()) side * 0.76f else side * 0.24f
        val moonY = side * (0.17f + random.nextFloat() * 0.1f)
        glowPaint.shader = RadialGradient(
            moonX, moonY, moonRadius * 2.7f,
            intArrayOf(withAlpha(accent, 78), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(moonX, moonY, moonRadius * 2.7f, glowPaint)
        glowPaint.shader = null
        starPaint.color = Color.rgb(207, 228, 255)
        starPaint.alpha = 235
        canvas.drawCircle(moonX, moonY, moonRadius, starPaint)
        starPaint.color = Color.rgb(6, 12, 32)
        starPaint.alpha = 150
        canvas.drawCircle(moonX + moonRadius * 0.34f, moonY - moonRadius * 0.12f, moonRadius * 0.87f, starPaint)

        // Long bands of distant mountains and luminous fog.
        drawMountain(canvas, random, side, side * 0.48f, Color.rgb(12, 28, 58), 0.12f)
        drawMountain(canvas, random, side, side * 0.57f, Color.rgb(10, 17, 41), 0.16f)
        val fog = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, side * 0.48f, 0f, side * 0.82f,
                intArrayOf(withAlpha(blue, 25), withAlpha(accent, 36), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, side * 0.48f, side.toFloat(), side * 0.84f, fog)

        // Gothic skyline, narrow spires and layered towers.
        val buildingBase = side * 0.83f
        var x = -side * 0.02f
        while (x < side * 1.04f) {
            val width = side * (0.025f + random.nextFloat() * 0.07f)
            val height = side * (0.12f + random.nextFloat() * 0.34f)
            val left = x
            val top = buildingBase - height
            val right = x + width
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(random.nextInt(4, 17), random.nextInt(8, 22), random.nextInt(24, 49))
            }
            canvas.drawRect(left, top, right, buildingBase, body)
            if (gothic && random.nextFloat() < 0.72f) {
                val spire = Path().apply {
                    moveTo(left, top + width * 0.18f)
                    lineTo((left + right) / 2f, top - width * (0.5f + random.nextFloat() * 1.8f))
                    lineTo(right, top + width * 0.18f)
                    close()
                }
                body.color = Color.rgb(17, 26, 58)
                canvas.drawPath(spire, body)
                val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 180); strokeWidth = 1.1f }
                canvas.drawLine((left + right) / 2f, top - width, (left + right) / 2f, top - width * 1.8f, stem)
            }
            val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val columns = (width / (side * 0.012f)).toInt().coerceIn(1, 5)
            val rows = (height / (side * 0.025f)).toInt().coerceIn(2, 18)
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    if (random.nextFloat() < 0.36f) continue
                    windowPaint.color = when (random.nextInt(5)) {
                        0 -> withAlpha(accent, random.nextInt(90, 230))
                        1 -> withAlpha(blue, random.nextInt(100, 220))
                        else -> Color.argb(random.nextInt(100, 210), 255, 200, 117)
                    }
                    val wx = left + width * (col + 0.25f) / columns
                    val wy = top + height * (row + 0.17f) / rows
                    canvas.drawRoundRect(wx, wy, wx + width / (columns * 3f), wy + height / (rows * 2.6f), 1.3f, 1.3f, windowPaint)
                }
            }
            x += width * 0.72f
        }

        // A luminous portal and its numerous concentric technical/occult rings.
        val portalX = side * (0.43f + random.nextFloat() * 0.14f)
        val portalY = side * 0.66f
        val portalRadius = side * 0.13f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 125)
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        for (ring in 0..7) {
            val r = portalRadius * (0.44f + ring * 0.095f)
            ringPaint.alpha = 32 + ring * 13
            canvas.drawCircle(portalX, portalY, r, ringPaint)
        }
        for (ray in 0 until 72) {
            val angle = ray * (2.0 * PI / 72.0)
            val r1 = portalRadius * (0.91f + random.nextFloat() * 0.18f)
            val r2 = r1 + side * (0.004f + random.nextFloat() * 0.025f)
            ringPaint.alpha = random.nextInt(35, 170)
            ringPaint.strokeWidth = if (ray % 6 == 0) 2.1f else 0.8f
            canvas.drawLine(
                (portalX + cos(angle).toFloat() * r1),
                (portalY + sin(angle).toFloat() * r1),
                (portalX + cos(angle).toFloat() * r2),
                (portalY + sin(angle).toFloat() * r2),
                ringPaint
            )
        }

        // Thousands of controlled sparks, embers and rain streaks give the piece texture.
        val particle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        repeat(950) {
            val px = random.nextFloat() * side
            val py = side * (0.30f + random.nextFloat() * 0.65f)
            particle.alpha = random.nextInt(22, 165)
            val radius = if (random.nextFloat() < 0.08f) random.nextFloat() * 3.5f + 1f else random.nextFloat() * 1.2f
            canvas.drawCircle(px, py, radius, particle)
        }
        if (rain) {
            val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 135, 201, 255)
                strokeWidth = 1.0f
            }
            repeat(1800) {
                val rx = random.nextFloat() * side
                val ry = random.nextFloat() * side
                val length = random.nextFloat() * 24f + 5f
                canvas.drawLine(rx, ry, rx - length * 0.23f, ry + length, rainPaint)
            }
        }

        // Optional triptych/collage framing; the panels share the same art direction.
        if (montage) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accent, 225)
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRect(side * 0.025f, side * 0.025f, side * 0.975f, side * 0.975f, line)
            canvas.drawLine(side / 3f, side * 0.045f, side / 3f, side * 0.955f, line)
            canvas.drawLine(side * 2f / 3f, side * 0.045f, side * 2f / 3f, side * 0.955f, line)
            val labels = listOf("I • ORIGEM", "II • TEMPESTADE", "III • RENASCER")
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = side * 0.021f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                setShadowLayer(8f, 0f, 0f, accent)
            }
            labels.forEachIndexed { index, label ->
                canvas.drawText(label, side * (index / 3f) + side * 0.025f, side * 0.94f, labelPaint)
            }
        }

        // A subtle frame keeps the output looking like a finished piece rather than a raw plot.
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 100)
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }
        canvas.drawRoundRect(RectF(8f, 8f, side - 8f, side - 8f), 7f, 7f, frame)

        val directory = File(appContext.filesDir, "generated_artwork").apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("Não consegui preparar a pasta das imagens.")
        }
        val file = File(directory, "kellvyn-art-${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Não consegui codificar a imagem PNG.")
                }
                output.flush()
                output.fd.sync()
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun drawMountain(canvas: Canvas, random: Random, side: Int, baseY: Float, color: Int, roughness: Float) {
        val path = Path().apply {
            moveTo(0f, side.toFloat())
            lineTo(0f, baseY)
            var x = 0f
            while (x < side) {
                val peakY = baseY - random.nextFloat() * side * roughness
                lineTo(x, peakY)
                x += side * (0.025f + random.nextFloat() * 0.06f)
                lineTo(x, baseY + random.nextFloat() * side * roughness * 0.25f)
            }
            lineTo(side.toFloat(), side.toFloat())
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val IMAGE_SIDE = 1024
    }
}
