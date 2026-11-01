package com.taskflow.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import com.taskflow.app.R
import com.taskflow.app.data.PriceAnalytics
import com.taskflow.app.util.Money
import java.util.Locale

class PriceLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Point(val label: String, val valueCents: Long)

    private var points: List<Point> = emptyList()
    private var accent = ContextCompat.getColor(context, R.color.brand_indigo)
    private var grid = ContextCompat.getColor(context, R.color.border)
    private var textPrimary = ContextCompat.getColor(context, R.color.text_primary)
    private var textSecondary = ContextCompat.getColor(context, R.color.text_tertiary)
    private var background = ContextCompat.getColor(context, R.color.surface)
    private var selectedIndex: Int? = null
    private var lastLeft = 0f
    private var lastRight = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pointStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9f)
        textAlign = Paint.Align.RIGHT
    }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val monthText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val variationText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(8.5f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    fun setData(data: List<Point>, accentColor: Int, text: Int, secondary: Int, surface: Int, gridColor: Int) {
        points = data
        selectedIndex = null
        accent = accentColor
        textPrimary = text
        textSecondary = secondary
        background = surface
        grid = gridColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        linePaint.color = accent
        pointStroke.color = accent
        axisText.color = textSecondary
        valueText.color = textPrimary
        monthText.color = textSecondary
        gridPaint.color = grid

        if (points.isEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary
                textSize = sp(11f)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Sem dados de preço para este filtro", width / 2f, height / 2f, paint)
            return
        }

        val left = dp(42f)
        val right = width - dp(14f)
        lastLeft = left
        lastRight = right
        val top = dp(30f)
        val bottom = height - dp(38f)
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)

        val trend = points.last().valueCents - points.first().valueCents
        val trendColor = when {
            trend < 0 -> Color.parseColor("#059669")
            trend > 0 -> Color.parseColor("#E11D48")
            else -> accent
        }
        linePaint.color = trendColor
        pointStroke.color = trendColor

        val minValue = points.minOf { it.valueCents }.toDouble()
        val maxValue = points.maxOf { it.valueCents }.toDouble()
        val span = (maxValue - minValue).coerceAtLeast(maxValue * 0.12).coerceAtLeast(100.0)
        val low = (minValue - span * 0.25)
        val high = (maxValue + span * 0.25)

        // grade horizontal + rótulos do eixo Y
        for (i in 0..2) {
            val ratio = i / 2f
            val y = bottom - chartHeight * ratio
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = low + (high - low) * ratio
            axisText.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatCompact(value), left - dp(6f), y + dp(3f), axisText)
        }

        val xStep = if (points.size == 1) 0f else chartWidth / (points.size - 1)
        val path = Path()
        val coords = points.mapIndexed { index, point ->
            val x = if (points.size == 1) left + chartWidth / 2f else left + xStep * index
            val ratio = ((point.valueCents - low) / (high - low)).toFloat().coerceIn(0f, 1f)
            val y = bottom - chartHeight * ratio
            x to y
        }

        coords.forEachIndexed { index, (x, y) ->
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (coords.size > 1) canvas.drawPath(path, linePaint)

        coords.forEachIndexed { index, (x, y) ->
            pointPaint.color = background
            canvas.drawCircle(x, y, dp(5f), pointPaint)
            canvas.drawCircle(x, y, dp(5f), pointStroke)

            val label = "R$ " + formatCompact(points[index].valueCents.toDouble())
            canvas.drawText(label, x.coerceIn(left + dp(18f), right - dp(18f)), y - dp(10f), valueText)
            canvas.drawText(
                points[index].label,
                x.coerceIn(left + dp(20f), right - dp(20f)),
                bottom + dp(18f),
                monthText
            )
        }

        // variação percentual entre pontos
        for (index in 1 until points.size) {
            val previous = points[index - 1].valueCents
            val current = points[index].valueCents
            if (previous <= 0) continue
            val variation = (current - previous) * 100.0 / previous
            val (x1, y1) = coords[index - 1]
            val (x2, y2) = coords[index]
            val midX = (x1 + x2) / 2f
            val midY = (y1 + y2) / 2f
            variationText.color = if (variation <= 0) Color.parseColor("#059669") else Color.parseColor("#E11D48")
            val arrow = if (variation <= 0) "↓ " else "↑ "
            canvas.drawText(arrow + PriceAnalytics.formatPercent(variation).removePrefix("+"), midX, midY - dp(7f), variationText)
        }

        selectedIndex?.let { index ->
            if (index in points.indices) drawTooltip(canvas, index, coords, left, right, top, bottom)
        }
    }

    private fun drawTooltip(
        canvas: Canvas,
        index: Int,
        coords: List<Pair<Float, Float>>,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val (x, y) = coords[index]
        val variation = if (index > 0) {
            val previous = points[index - 1].valueCents
            if (previous > 0) (points[index].valueCents - previous) * 100.0 / previous else null
        } else null
        val line1 = "${points[index].label} — R$ ${String.format(Locale("pt", "BR"), "%.2f", points[index].valueCents / 100.0)}"
        val line2 = variation?.let { "Variação: " + PriceAnalytics.formatPercent(it) } ?: "Primeiro mês registrado"

        val tooltipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(9f)
            color = textPrimary
        }
        val tooltipSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(8.5f)
            color = if ((variation ?: 0.0) <= 0) Color.parseColor("#059669") else Color.parseColor("#E11D48")
        }
        val width = maxOf(tooltipText.measureText(line1), tooltipSmall.measureText(line2)) + dp(16f)
        val height = dp(34f)
        var bx = x - width / 2f
        bx = bx.coerceIn(left, right - width)
        var by = y - height - dp(12f)
        if (by < top) by = y + dp(12f)
        if (by + height > bottom) by = bottom - height

        val rect = android.graphics.RectF(bx, by, bx + width, by + height)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
            color = trendColorFor(index)
        }
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), stroke)
        canvas.drawText(line1, bx + dp(8f), by + dp(13f), tooltipText)
        canvas.drawText(line2, bx + dp(8f), by + dp(26f), tooltipSmall)
    }

    private fun trendColorFor(index: Int): Int {
        if (index <= 0) return accent
        return if (points[index].valueCents <= points[index - 1].valueCents) Color.parseColor("#059669")
        else Color.parseColor("#E11D48")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN) {
            val usable = (lastRight - lastLeft).coerceAtLeast(1f)
            val step = if (points.size == 1) 0f else usable / (points.size - 1)
            val index = if (points.size == 1) 0
            else ((event.x - lastLeft) / step).roundToInt().coerceIn(0, points.size - 1)
            selectedIndex = index
            invalidate()
            if (event.action == MotionEvent.ACTION_UP) performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun formatCompact(value: Double): String =
        String.format(Locale("pt", "BR"), "%.2f", value / 100.0)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
