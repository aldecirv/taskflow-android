package com.taskflow.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.taskflow.app.R
import java.time.LocalDate

class WeekBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    private var values: List<Int> = List(7) { 0 }
    private var labels: List<String> = List(7) { "" }
    private var highlightIndex: Int = 6

    private var activeColor = ContextCompat.getColor(context, R.color.brand_indigo)
    private var idleColor = ContextCompat.getColor(context, R.color.border)

    fun setData(values: List<Int>, labels: List<String>, highlightIndex: Int = 6) {
        this.values = values
        this.labels = labels
        this.highlightIndex = highlightIndex
        invalidate()
    }

    fun setColors(active: Int, idle: Int) {
        activeColor = active
        idleColor = idle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val bottom = height - sp(22f)
        val top = sp(18f)
        val usable = (bottom - top).coerceAtLeast(sp(40f))
        val slot = width.toFloat() / values.size
        val barWidth = slot * 0.44f

        values.forEachIndexed { index, value ->
            val cx = slot * index + slot / 2f
            val ratio = value.toFloat() / max
            val barHeight = (usable * ratio).coerceAtLeast(if (value > 0) sp(6f) else sp(3f))
            val rect = RectF(cx - barWidth / 2, bottom - barHeight, cx + barWidth / 2, bottom)
            barPaint.color = if (index == highlightIndex) activeColor else idleColor
            canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, barPaint)

            if (value > 0) {
                canvas.drawText(value.toString(), cx, rect.top - sp(6f), valuePaint)
            }
            canvas.drawText(labels.getOrElse(index) { "" }, cx, height - sp(4f), textPaint)
        }
    }

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
