package com.taskflow.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.taskflow.app.R

/** Moldura de enquadramento com máscara escura e cantos coloridos. */
class FramingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.WHITE
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.hero_start)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(15f)
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameWidth = width * 0.82f
        val frameHeight = frameWidth * 0.72f
        val left = (width - frameWidth) / 2f
        val top = (height - frameHeight) / 2.35f
        val right = left + frameWidth
        val bottom = top + frameHeight
        val radius = dp(22f)
        val frame = RectF(left, top, right, bottom)

        canvas.drawRect(0f, 0f, width.toFloat(), top, dimPaint)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, top, left, bottom, dimPaint)
        canvas.drawRect(right, top, width.toFloat(), bottom, dimPaint)

        canvas.drawRoundRect(frame, radius, radius, framePaint)

        val corner = dp(30f)
        canvas.drawLine(left, top + corner, left, top + radius, cornerPaint)
        canvas.drawLine(left + radius, top, left + corner, top, cornerPaint)
        canvas.drawLine(right - corner, top, right - radius, top, cornerPaint)
        canvas.drawLine(right, top + radius, right, top + corner, cornerPaint)
        canvas.drawLine(left, bottom - corner, left, bottom - radius, cornerPaint)
        canvas.drawLine(left + radius, bottom, left + corner, bottom, cornerPaint)
        canvas.drawLine(right - corner, bottom, right - radius, bottom, cornerPaint)
        canvas.drawLine(right, bottom - radius, right, bottom - corner, cornerPaint)

        canvas.drawText("Aponte para o produto", width / 2f, top - dp(18f), titlePaint)
        canvas.drawText("A foto tenta ler o código de barras e reconhecer o nome", width / 2f, bottom + dp(28f), hintPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
