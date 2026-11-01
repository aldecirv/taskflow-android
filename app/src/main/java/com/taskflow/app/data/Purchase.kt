package com.taskflow.app.data

import androidx.annotation.DrawableRes
import com.taskflow.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToLong

data class Purchase(
    val id: String,
    var name: String,
    var priceCents: Long,
    var date: LocalDate = LocalDate.now(),
    var place: String = "",
    var category: String = "Mercado",
    var barcode: String? = null,
    var imagePath: String? = null,
    var productId: String = "",
    var latitude: Double? = null,
    var longitude: Double? = null,
    var note: String = "",
    var quantity: Double = 1.0,
    var unit: String = "un",
    var priceIsUnit: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun unitPrice(): Double = priceCents / 100.0

    val normalizedQuantity: Double
        get() = when (unit.lowercase()) {
            "g", "ml" -> quantity / 1000.0
            else -> quantity
        }

    val normalizedUnit: String
        get() = when (unit.lowercase()) {
            "g", "kg" -> "kg"
            "ml", "l" -> "L"
            else -> "un"
        }

    fun normalizedUnitPriceCents(): Long =
        if (normalizedQuantity > 0) (priceCents / normalizedQuantity).roundToLong() else priceCents

    fun unitPriceCents(): Long =
        if (quantity > 0) (priceCents / quantity).roundToLong() else priceCents

    fun quantityLabel(): String = formatQuantity(quantity) + " " + unit

    companion object {
        fun formatQuantity(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString()
            else String.format(java.util.Locale("pt", "BR"), "%.2f", value)
    }
}

/** Nome pedido na especificação do módulo. */
typealias ProductEntry = Purchase

data class Product(
    val id: String,
    var name: String,
    var barcode: String? = null,
    var imagePath: String? = null,
    var categoryId: String = "Mercado",
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class PriceHistory(
    val productId: String,
    val month: java.time.YearMonth,
    val averagePriceCents: Long,
    val minPriceCents: Long,
    val maxPriceCents: Long,
    val entriesCount: Int,
    val unit: String = "un"
)

data class ProductCategory(
    val name: String,
    val colorHex: String,
    @DrawableRes val icon: Int
)

object ProductCategories {
    val all = listOf(
        ProductCategory("Mercado", "#10B981", R.drawable.ic_shopping_bag),
        ProductCategory("Casa", "#3B82F6", R.drawable.ic_home),
        ProductCategory("Higiene", "#06B6D4", R.drawable.ic_water_drop),
        ProductCategory("Saúde", "#8B5CF6", R.drawable.ic_favorite),
        ProductCategory("Outros", "#F59E0B", R.drawable.ic_sell)
    )

    fun byName(name: String): ProductCategory = all.firstOrNull { it.name == name } ?: all.first()

    fun color(name: String): String = byName(name).colorHex

    @DrawableRes fun icon(name: String): Int = byName(name).icon
}

/** Resultado do reconhecimento visual (Gemini ou OCR local). */
data class ProductGuess(
    val name: String,
    val priceCents: Long? = null,
    val source: String = "local"
)
