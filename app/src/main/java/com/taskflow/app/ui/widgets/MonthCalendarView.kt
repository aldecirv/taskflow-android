package com.taskflow.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.taskflow.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var month: YearMonth = YearMonth.now()
        private set
    var selectedDate: LocalDate = LocalDate.now()
        private set

    var markedDates: Set<LocalDate> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    var onDateSelected: ((LocalDate) -> Unit)? = null

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        color = ContextCompat.getColor(context, R.color.text_primary)
    }
    private val mutedPaint = Paint(dayPaint).apply {
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(10f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_indigo)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.brand_indigo)
    }
    private val selectedTextPaint = Paint(dayPaint).apply { color = android.graphics.Color.WHITE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_indigo)
    }

    private val cellHeight = dp(46f)
    private val headerHeight = dp(28f)
    private val rect = RectF()

    private val weekdays = listOf("S", "T", "Q", "Q", "S", "S", "D")

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (headerHeight + cellHeight * rowsForMonth()).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }

    private fun rowsForMonth(): Int {
        val leading = month.atDay(1).dayOfWeek.value % 7
        return ((leading + month.lengthOfMonth() + 6) / 7).coerceIn(4, 6)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slot = width.toFloat() / 7f
        val today = LocalDate.now()

        weekdays.forEachIndexed { i, label ->
            canvas.drawText(label, slot * i + slot / 2f, headerHeight - dp(8f), headerPaint)
        }

        val first = month.atDay(1)
        val leading = first.dayOfWeek.value % 7
        val days = month.lengthOfMonth()
        val rows = rowsForMonth()

        for (index in 0 until rows * 7) {
            val dayOfMonth = index - leading + 1
            if (dayOfMonth < 1 || dayOfMonth > days) continue
            val date = month.atDay(dayOfMonth)
            val col = index % 7
            val row = index / 7
            val cx = slot * col + slot / 2f
            val cy = headerHeight + cellHeight * row + cellHeight / 2f
            val radius = dp(17f)

            val isSelected = date == selectedDate
            val isToday = date == today

            if (isSelected) {
                canvas.drawCircle(cx, cy, radius, circlePaint)
            } else if (isToday) {
                canvas.drawCircle(cx, cy, radius, ringPaint)
            }

            val paint = when {
                isSelected -> selectedTextPaint
                date.dayOfWeek.value == 7 || date.dayOfWeek.value == 6 -> mutedPaint
                else -> dayPaint
            }
            val textY = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(dayOfMonth.toString(), cx, textY, paint)

            if (markedDates.contains(date) && !isSelected) {
                canvas.drawCircle(cx, cy + dp(16f), dp(2.2f), dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val slot = width.toFloat() / 7f
            val col = (event.x / slot).toInt().coerceIn(0, 6)
            val row = ((event.y - headerHeight) / cellHeight).toInt()
            if (event.y > headerHeight && row in 0..5) {
                val index = row * 7 + col
                val leading = month.atDay(1).dayOfWeek.value % 7
                val day = index - leading + 1
                if (day in 1..month.lengthOfMonth()) {
                    selectedDate = month.atDay(day)
                    onDateSelected?.invoke(selectedDate)
                    invalidate()
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun showMonth(value: YearMonth) {
        month = value
        invalidate()
        requestLayout()
    }

    fun select(date: LocalDate) {
        selectedDate = date
        month = YearMonth.from(date)
        invalidate()
    }

    fun monthLabel(): String {
        val raw = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
        return raw.replaceFirstChar { it.titlecase(Locale("pt", "BR")) } + " de " + month.year
    }

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
