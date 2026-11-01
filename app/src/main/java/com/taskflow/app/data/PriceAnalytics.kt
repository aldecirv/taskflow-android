package com.taskflow.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class PriceRow(
    val productId: String,
    val product: String,
    val averages: Map<YearMonth, Long?>,
    val variation: Double?,
    val history: List<PriceHistory> = emptyList()
)

object PriceAnalytics {

    fun months(today: LocalDate = LocalDate.now(), count: Int = 6): List<YearMonth> {
        val current = YearMonth.from(today)
        return (count - 1 downTo 0).map { current.minusMonths(it.toLong()) }
    }

    /** Meses do primeiro registro até o mês atual (no máximo [max]). */
    fun monthsFor(purchases: List<Purchase>, today: LocalDate = LocalDate.now(), max: Int = 6): List<YearMonth> {
        if (purchases.isEmpty()) return months(today, max)
        val current = YearMonth.from(today)
        val earliest = purchases.minOf { YearMonth.from(it.date) }
        val start = maxOf(earliest, current.minusMonths((max - 1).toLong()))
        return generateSequence(start) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(current) }
            .toList()
    }

    fun rows(purchases: List<Purchase>, months: List<YearMonth>): List<PriceRow> {
        val byProduct = purchases.groupBy { entry ->
            entry.productId.ifBlank { entry.name.trim().lowercase() }
        }
        return byProduct.entries
            .filter { (_, items) -> items.any { YearMonth.from(it.date) in months } }
            .map { (productId, items) ->
                val product = Store.products.firstOrNull { it.id == productId }
                val name = product?.name ?: items.first().name
                val averages = months.associateWith { month ->
                    val values = items.filter { YearMonth.from(it.date) == month }.map { it.priceCents }
                    if (values.isEmpty()) null else values.average().toLong()
                }
                val current = averages[months.lastOrNull()]
                val previous = if (months.size >= 2) averages[months[months.size - 2]] else null
                val change = com.taskflow.app.features.products.domain.CalculateVariationUseCase
                    .execute(current, previous)
                val history = months.mapNotNull { month ->
                    val values = items.filter { YearMonth.from(it.date) == month }.map { it.priceCents }
                    if (values.isEmpty()) null else PriceHistory(
                        productId = productId,
                        month = month,
                        averagePriceCents = values.average().toLong(),
                        minPriceCents = values.minOrNull() ?: 0,
                        maxPriceCents = values.maxOrNull() ?: 0,
                        entriesCount = values.size
                    )
                }
                PriceRow(productId, name, averages, change, history)
            }
            .sortedBy { it.product.lowercase() }
    }

    fun monthlySpend(purchases: List<Purchase>, months: List<YearMonth>): List<Pair<YearMonth, Long>> =
        months.map { month ->
            month to purchases.filter { YearMonth.from(it.date) == month }.sumOf { it.priceCents }
        }

    /** Variação entre os dois meses mais recentes que têm preço. */
    fun variation(valuesChronological: List<Long>): Double? {
        if (valuesChronological.size < 2) return null
        val previous = valuesChronological[valuesChronological.size - 2]
        val current = valuesChronological.last()
        if (previous <= 0L) return null
        return (current - previous) * 100.0 / previous
    }

    fun variationFor(purchases: List<Purchase>, product: String, months: List<YearMonth>): Double? {
        val values = months.associateWith { month ->
            val list = purchases.filter { it.name == product && YearMonth.from(it.date) == month }
            if (list.isEmpty()) null else list.map { it.priceCents }.average().toLong()
        }
        val current = values[months.lastOrNull()]
        val previous = if (months.size >= 2) values[months[months.size - 2]] else null
        return com.taskflow.app.features.products.domain.CalculateVariationUseCase.execute(current, previous)
    }

    fun formatPercent(value: Double): String {
        val formatted = String.format(Locale("pt", "BR"), "%.1f", kotlin.math.abs(value))
        return (if (value < 0) "-" else "+") + formatted + "%"
    }

    fun monthLabel(month: YearMonth): String {
        val name = month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale("pt", "BR"))
            .removeSuffix(".")
        return "$name/${month.year.toString().takeLast(2)}"
    }

    fun monthLabelFull(month: YearMonth): String {
        val name = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.titlecase(Locale("pt", "BR")) }
        return "$name de ${month.year}"
    }
}
