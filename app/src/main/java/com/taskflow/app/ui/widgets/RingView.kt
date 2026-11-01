package com.taskflow.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.taskflow.app.R

class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.border)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.brand_indigo)
    }

    private val oval = RectF()

    init {
        trackPaint.strokeWidth = dp(14f)
        progressPaint.strokeWidth = dp(14f)
    }

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var stroke: Float = dp(14f)
        set(value) {
            field = value
            trackPaint.strokeWidth = value
            progressPaint.strokeWidth = value
            invalidate()
        }

    var trackColor: Int
        get() = trackPaint.color
        set(value) {
            trackPaint.color = value
            invalidate()
        }

    var progressColor: Int
        get() = progressPaint.color
        set(value) {
            progressPaint.shader = null
            progressPaint.color = value
            invalidate()
        }

    fun useGradient(start: Int, end: Int) {
        progressPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(), start, end, Shader.TileMode.CLAMP
        )
        invalidate()
    }

    fun setStrokeDp(value: Float) {
        stroke = dp(value)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = stroke / 2f + dp(1f)
        oval.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = stroke / 2f + dp(1f)
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
