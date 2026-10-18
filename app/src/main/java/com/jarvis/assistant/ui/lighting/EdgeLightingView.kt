package com.jarvis.assistant.ui.lighting

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View

/**
 * DEV STYLE: soft, translucent pastel rainbow edge-lighting glow.
 * A rounded-rect ring around the screen edges with an animated gradient that
 * slowly flows around the border. Layered translucent strokes create the soft
 * glow with pure hardware rendering, throttled to ~30 FPS to keep battery use low.
 */
class EdgeLightingView(context: Context) : View(context) {

    companion object {
        // Pastel rainbow palette - exact brand colors, in flow order.
        val PASTEL_COLORS = intArrayOf(
            0xFFFF9DAD.toInt(), // Coral Pink
            0xFFFFD0C2.toInt(), // Soft Peach
            0xFFFFF3A6.toInt(), // Pastel Yellow
            0xFFA8F2D0.toInt(), // Mint Green
            0xFF91E8F5.toInt(), // Cyan
            0xFF9BCBFF.toInt(), // Sky Blue
            0xFFD5A6FF.toInt()  // Lavender Purple
        )
        private const val FRAME_MS = 32L // ~30 FPS
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private var phase = 0f
    private var lastFrame = 0L
    private var running = false

    /** Call with true to animate, false to freeze (during fade-out). */
    fun setRunning(run: Boolean) {
        running = run
        if (run) {
            lastFrame = 0L
            postInvalidateOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val inset = -30f // glow slightly outside the visible edge
        val ring = RectF(inset, inset, w - inset, h - inset)

        // Flowing pastel gradient, rotated over time.
        val matrix = Matrix()
        matrix.postRotate(phase, cx, cy)
        val gradSpan = (w + h) * 0.55f
        val shader = LinearGradient(
            cx - gradSpan, cy, cx + gradSpan, cy,
            PASTEL_COLORS, null, Shader.TileMode.MIRROR
        )
        shader.setLocalMatrix(matrix)
        ringPaint.shader = shader

        // Layered strokes: wide translucent halo -> narrow brighter core = soft glow.
        val layers = arrayOf(
            floatArrayOf(110f, 0.12f),
            floatArrayOf(72f, 0.18f),
            floatArrayOf(44f, 0.26f),
            floatArrayOf(24f, 0.38f),
            floatArrayOf(10f, 0.50f)
        )
        for (layer in layers) {
            ringPaint.strokeWidth = layer[0]
            ringPaint.alpha = (255f * layer[1]).toInt()
            canvas.drawRoundRect(ring, 44f, 44f, ringPaint)
        }

        if (running) {
            val now = SystemClock.uptimeMillis()
            if (lastFrame == 0L || now - lastFrame >= FRAME_MS) {
                phase = (phase + 0.9f) % 360f
                lastFrame = now
            }
            postInvalidateOnAnimation(this)
        }
    }
}
