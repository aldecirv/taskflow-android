package com.taskflow.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.taskflow.app.R
import com.taskflow.app.data.PriceAnalytics
import java.util.Locale

class SpendBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Bar(val label: String, val valueCents: Long)

    private var bars: List<Bar> = emptyList()
    private var accent = ContextCompat.getColor(context, R.color.brand_indigo)
    private var textSecondary = ContextCompat.getColor(context, R.color.text_tertiary)
    private var textPrimary = ContextCompat.getColor(context, R.color.text_primary)
    private var grid = ContextCompat.getColor(context, R.color.border)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    fun setData(data: List<Bar>, accentColor: Int, primary: Int, secondary: Int, gridColor: Int) {
        bars = data
        accent = accentColor
        textPrimary = primary
        textSecondary = secondary
        grid = gridColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        valuePaint.color = textPrimary
        labelPaint.color = textSecondary
        gridPaint.color = grid

        if (bars.isEmpty() || bars.all { it.valueCents == 0L }) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary
                textSize = sp(11f)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Sem gastos no período", width / 2f, height / 2f, paint)
            return
        }

        val left = dp(10f)
        val right = width - dp(10f)
        val top = dp(26f)
        val bottom = height - dp(30f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        val max = bars.maxOf { it.valueCents }.coerceAtLeast(1L)
        val slot = (right - left) / bars.size
        val barWidth = slot * 0.5f

        // linhas de referência
        for (i in 0..2) {
            val y = bottom - chartHeight * (i / 2f)
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        bars.forEachIndexed { index, bar ->
            val cx = left + slot * index + slot / 2f
            val ratio = bar.valueCents.toFloat() / max
            val heightPx = chartHeight * ratio
            val rect = RectF(cx - barWidth / 2, bottom - heightPx, cx + barWidth / 2, bottom)
            barPaint.color = accent
            canvas.drawRoundRect(rect, dp(6f), dp(6f), barPaint)

            if (bar.valueCents > 0) {
                val value = "R$ " + String.format(Locale("pt", "BR"), "%.0f", bar.valueCents / 100.0)
                canvas.drawText(value, cx, (bottom - heightPx) - dp(7f), valuePaint)
            }
            canvas.drawText(bar.label, cx, bottom + dp(18f), labelPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
