package com.example.toibot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class ToibotOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class OrbState { IDLE, LISTENING, SPEAKING, CONFIRMING }

    var state: OrbState = OrbState.IDLE
        set(value) {
            field = value
            invalidate()
        }

    var audioAmplitude: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private var phase = 0f

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18B7A6")
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFF3")
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22D3EE")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D9488")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = minOf(cx, cy) * 0.42f

        val pulse = when (state) {
            OrbState.IDLE -> sin(phase) * 3f
            OrbState.LISTENING -> 6f + audioAmplitude * 16f + sin(phase * 2f) * 4f
            OrbState.SPEAKING -> 10f + audioAmplitude * 22f + sin(phase * 3f) * 6f
            OrbState.CONFIRMING -> 8f + sin(phase * 1.5f) * 5f
        }

        for (i in 1..3) {
            val r = baseRadius + i * 11f + pulse * (0.4f + i * 0.25f)
            outerRingPaint.alpha = (180 - i * 45).coerceIn(25, 200)
            canvas.drawCircle(cx, cy, r, outerRingPaint)
        }

        ringPaint.color = when (state) {
            OrbState.CONFIRMING -> Color.parseColor("#F59E0B")
            OrbState.SPEAKING -> Color.parseColor("#2DD4BF")
            else -> Color.parseColor("#14B8A6")
        }
        canvas.drawCircle(cx, cy, baseRadius + pulse * 0.6f, ringPaint)

        corePaint.alpha = 95
        canvas.drawCircle(cx, cy, baseRadius * 0.78f + pulse * 0.3f, corePaint)

        val dotRingR = baseRadius * 0.55f + pulse * 0.25f
        for (k in 0 until 16) {
            val angle = Math.toRadians((k * 22.5 + phase * 18.0))
            val dx = cx + cos(angle).toFloat() * dotRingR
            val dy = cy + sin(angle).toFloat() * dotRingR
            canvas.drawCircle(dx, dy, 2.5f, dotPaint)
        }

        phase += 0.08f
        postInvalidateOnAnimation()
    }
}
