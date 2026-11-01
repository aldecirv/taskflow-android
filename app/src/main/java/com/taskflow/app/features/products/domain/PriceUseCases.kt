package com.taskflow.app.features.products.domain

import com.taskflow.app.data.PriceHistory
import com.taskflow.app.data.ProductMatcher
import com.taskflow.app.data.Purchase
import com.taskflow.app.data.Store
import java.time.LocalDate
import java.time.YearMonth

/** Variação percentual entre dois meses. Retorna null quando não há referência anterior. */
object CalculateVariationUseCase {
    fun execute(currentCents: Long?, previousCents: Long?): Double? {
        if (currentCents == null || previousCents == null || previousCents <= 0L) return null
        return (currentCents - previousCents) * 100.0 / previousCents
    }
}

object GetPriceHistoryUseCase {

    fun execute(months: List<YearMonth>, purchases: List<Purchase> = Store.purchases): List<PriceHistory> {
        return purchases.groupBy { entry ->
            entry.productId.ifBlank { entry.name.trim().lowercase() }
        }.flatMap { (productId, entries) ->
            months.mapNotNull { month ->
                val items = entries.filter { YearMonth.from(it.date) == month }
                if (items.isEmpty()) return@mapNotNull null
                PriceHistory(
                    productId = productId,
                    month = month,
                    averagePriceCents = items.map { it.priceCents }.average().toLong(),
                    minPriceCents = items.minOf { it.priceCents },
                    maxPriceCents = items.maxOf { it.priceCents },
                    entriesCount = items.size
                )
            }
        }
    }

    fun forProduct(productId: String, months: List<YearMonth>): List<PriceHistory> =
        execute(months).filter { it.productId == productId }

    /** Meses do primeiro registro até o mês atual (máximo [max]). */
    fun monthsFor(max: Int = 6, today: LocalDate = LocalDate.now()): List<YearMonth> {
        if (Store.purchases.isEmpty()) return (max - 1 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val current = YearMonth.from(today)
        val earliest = Store.purchases.minOf { YearMonth.from(it.date) }
        val start = maxOf(earliest, current.minusMonths((max - 1).toLong()))
        return generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(current) }.toList()
    }
}

object FindMatchingProductUseCase {
    fun execute(name: String, barcode: String?, products: List<com.taskflow.app.data.Product>): com.taskflow.app.data.Product? {
        if (!barcode.isNullOrBlank()) {
            products.firstOrNull { it.barcode == barcode }?.let { return it }
        }
        return products
            .map { it to ProductMatcher.similarity(it.name, name) }
            .filter { it.second >= ProductMatcher.THRESHOLD }
            .maxByOrNull { it.second }?.first
    }
}
