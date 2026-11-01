package com.taskflow.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import com.taskflow.app.R
import com.taskflow.app.data.PriceAnalytics
import com.taskflow.app.data.ProductCategories
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityPriceChartsBinding
import com.taskflow.app.ui.widgets.PriceLineChartView
import com.taskflow.app.ui.widgets.SpendBarChartView
import com.taskflow.app.util.Money
import com.taskflow.app.util.ThemeManager
import java.time.LocalDate
import java.time.YearMonth

class PriceChartsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriceChartsBinding
    private var productFilter = "Todos"
    private var categoryFilter = "Todas"
    private var placeFilter = "Todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPriceChartsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.filterProduct.setOnClickListener {
            pick("Produto", listOf("Todos") + Store.distinctProducts(), productFilter) {
                productFilter = it
                render()
            }
        }
        binding.filterCategory.setOnClickListener {
            pick("Categoria", listOf("Todas") + Store.allProductCategories().map { it.name }, categoryFilter) {
                categoryFilter = it
                render()
            }
        }
        binding.filterPlace.setOnClickListener {
            pick("Local de compra", listOf("Todos") + Store.distinctPlaces(), placeFilter) {
                placeFilter = it
                render()
            }
        }

        render()
    }

    private fun render() {
        val months = PriceAnalytics.monthsFor(Store.purchases, LocalDate.now(), 6)
        val filtered = Store.purchases.filter { purchase ->
            (categoryFilter == "Todas" || purchase.category == categoryFilter) &&
                (placeFilter == "Todos" || purchase.place == placeFilter)
        }

        binding.filterProduct.text = "Produto: $productFilter"
        binding.filterCategory.text = "Categoria: ${categoryFilter.lowercase()}"
        binding.filterPlace.text = "Local: ${placeFilter.lowercase()}"

        val product = when {
            productFilter != "Todos" -> productFilter
            else -> filtered.groupBy { it.name }.maxByOrNull { it.value.size }?.key
        }

        val linePoints = mutableListOf<PriceLineChartView.Point>()
        if (product != null) {
            months.forEach { month ->
                val items = filtered.filter { it.name == product && YearMonth.from(it.date) == month }
                if (items.isNotEmpty()) {
                    linePoints.add(
                        PriceLineChartView.Point(
                            PriceAnalytics.monthLabel(month),
                            items.map { it.priceCents }.average().toLong()
                        )
                    )
                }
            }
        }
        binding.lineSubtitle.text = when {
            product == null -> "Nenhuma compra para os filtros escolhidos"
            productFilter == "Todos" -> "Produto com mais compras: $product"
            else -> "Preço médio de $product por mês"
        }

        val barPurchases = if (productFilter == "Todos") filtered else filtered.filter { it.name == productFilter }
        val bars = months.map { month ->
            SpendBarChartView.Bar(
                PriceAnalytics.monthLabel(month),
                barPurchases.filter { YearMonth.from(it.date) == month }.sumOf { it.priceCents }
            )
        }

        val primary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        val surface = ContextCompat.getColor(this, R.color.surface)
        val border = ContextCompat.getColor(this, R.color.border)

        binding.lineChart.setData(linePoints, primary, textPrimary, textSecondary, surface, border)
        binding.barChart.setData(bars, primary, textPrimary, textSecondary, border)

        val total = barPurchases.sumOf { it.priceCents }
        val average = if (months.isEmpty()) 0L else total / months.size
        binding.summary.text = if (barPurchases.isEmpty()) {
            "Nenhuma compra registrada para estes filtros. Adicione produtos para ver a evolução."
        } else {
            "Total no período: ${Money.format(total)} · média por mês: ${Money.format(average)} · " +
                "${barPurchases.size} ${if (barPurchases.size == 1) "compra" else "compras"}"
        }
    }

    private fun pick(title: String, options: List<String>, current: String, onPick: (String) -> Unit) {
        val index = options.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), index) { dialog, which ->
                onPick(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PriceChartsActivity::class.java))
        }
    }
}
