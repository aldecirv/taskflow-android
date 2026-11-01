package com.taskflow.app.util

import java.text.NumberFormat
import java.util.Locale

/** Formatação e máscara de moeda brasileira (R$). */
object Money {
    private val ptBr = Locale("pt", "BR")

    fun format(cents: Long): String =
        NumberFormat.getCurrencyInstance(ptBr).format(cents / 100.0).replace('\u00A0', ' ')

    fun formatPlain(cents: Long): String =
        String.format(ptBr, "%.2f", cents / 100.0)

    fun formatSignedPercent(value: Double): String {
        val abs = String.format(ptBr, "%.1f", kotlin.math.abs(value))
        return (if (value < 0) "-" else "+") + abs + "%"
    }

    fun digitsToCents(text: String): Long =
        text.filter { it.isDigit() }.take(9).toLongOrNull() ?: 0L

    fun maskFromDigits(digits: String): String {
        val cents = digits.filter { it.isDigit() }.take(9).toLongOrNull() ?: 0L
        return format(cents)
    }
}
