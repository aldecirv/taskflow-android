package com.taskflow.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val ptBr = Locale("pt", "BR")
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBr)
    private val shortDate = DateTimeFormatter.ofPattern("d 'de' MMM", ptBr)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)
    private val weekday = DateTimeFormatter.ofPattern("EEE", ptBr)
    private val weekdayLong = DateTimeFormatter.ofPattern("EEEE", ptBr)

    fun capitalize(value: String): String =
        value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBr) else it.toString() }

    fun greeting(): String {
        val hour = LocalTime.now().hour
        return when {
            hour < 12 -> "Bom dia"
            hour < 18 -> "Boa tarde"
            else -> "Boa noite"
        }
    }

    fun longDate(date: LocalDate = LocalDate.now()): String = capitalize(date.format(longDate))
    fun shortDate(date: LocalDate): String = date.format(shortDate)
    fun monthYear(date: LocalDate): String = capitalize(date.format(monthYear))
    fun weekdayShort(date: LocalDate): String = capitalize(date.format(weekday).removeSuffix("."))
    fun weekdayLong(date: LocalDate): String = capitalize(date.format(weekdayLong))
    fun dayNumber(date: LocalDate): String = date.dayOfMonth.toString()

    fun relative(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Hoje"
            today.plusDays(1) -> "Amanhã"
            today.minusDays(1) -> "Ontem"
            else -> capitalize(date.format(shortDate))
        }
    }

    fun relativeWithWeekday(date: LocalDate): String =
        if (date == LocalDate.now()) "Hoje" else "${relative(date)} · ${weekdayShort(date)}"

    fun timeLabel(time: LocalTime?): String =
        time?.let { String.format(Locale.ROOT, "%02d:%02d", it.hour, it.minute) } ?: "Dia todo"

    fun durationLabel(minutes: Int): String = when {
        minutes < 60 -> "${minutes}min"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h${minutes % 60}"
    }

    fun timestamp(date: LocalDateTime = LocalDateTime.now()): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}
