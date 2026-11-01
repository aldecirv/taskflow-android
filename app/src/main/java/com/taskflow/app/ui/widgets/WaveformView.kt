package com.taskflow.app.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.taskflow.app.R
import kotlin.math.abs
import kotlin.math.sin

/** Barras animadas que simulam a forma de onda do microfone. */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_indigo)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3.5f)
    }
    private var animator: ValueAnimator? = null
    private var phase = 0f
    private var level = 0.25f
    private var active = false

    fun start() {
        active = true
        level = 0.3f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        active = false
        animator?.cancel()
        animator = null
        invalidate()
    }

    fun setRms(rms: Float) {
        level = (rms.coerceIn(0f, 12f) / 12f).coerceAtLeast(0.12f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bars = 26
        val gap = width.toFloat() / bars
        val centerY = height / 2f
        val maxHeight = height * 0.42f
        for (i in 0 until bars) {
            val wave = abs(sin((i * 0.55f) + phase * 6.28f))
            val envelope = 0.35f + 0.65f * abs(sin(i * 0.22f + 1.2f))
            val amplitude = if (active) (0.15f + level) * (0.35f + wave * envelope) else 0.12f
            val barHeight = (maxHeight * amplitude).coerceAtLeast(dp(3f))
            val x = gap * i + gap / 2f
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, paint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
