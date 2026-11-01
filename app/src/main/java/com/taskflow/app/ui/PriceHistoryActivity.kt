package com.taskflow.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import com.taskflow.app.R
import com.taskflow.app.data.PriceAnalytics
import com.taskflow.app.data.PriceRow
import com.taskflow.app.data.ProductCategories
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityPriceHistoryBinding
import com.taskflow.app.ui.widgets.PriceLineChartView
import com.taskflow.app.ui.widgets.SpendBarChartView
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Money
import com.taskflow.app.util.ThemeManager
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class PriceHistoryActivity : AppCompatActivity() {

    private enum class Sort { NAME, UP, DOWN }

    private lateinit var binding: ActivityPriceHistoryBinding
    private var sort = Sort.NAME
    private var productFilter = "Todos"
    private var categoryFilter = "Todas"
    private var placeFilter = "Todos"
    private var months: List<YearMonth> = emptyList()
    private var rows: List<PriceRow> = emptyList()

    private val csvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri ?: return@registerForActivityResult
            writeCsv(uri)
        }

    private val pdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            uri ?: return@registerForActivityResult
            writePdf(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPriceHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCsv.setOnClickListener { if (rows.isEmpty()) toast("Nada para exportar") else csvLauncher.launch("taskflow-precos-${LocalDate.now()}.csv") }
        binding.btnPdf.setOnClickListener { if (rows.isEmpty()) toast("Nada para exportar") else pdfLauncher.launch("taskflow-precos-${LocalDate.now()}.pdf") }

        binding.tabs.addTab(binding.tabs.newTab().setText("Tabela"))
        binding.tabs.addTab(binding.tabs.newTab().setText("Gráficos"))
        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val charts = tab.position == 1
                binding.tablePane.isVisible = !charts
                binding.chartsPane.isVisible = charts
                if (charts) renderCharts()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })

        binding.sortGroup.setOnCheckedStateChangeListener { _, ids ->
            sort = when (ids.firstOrNull()) {
                R.id.sortUp -> Sort.UP
                R.id.sortDown -> Sort.DOWN
                else -> Sort.NAME
            }
            renderTable()
        }

        binding.filterProduct.setOnClickListener { pickProduct() }
        binding.filterCategory.setOnClickListener {
            pick("Categoria", listOf("Todas") + Store.allProductCategories().map { it.name }, categoryFilter) {
                categoryFilter = it
                renderTable()
                renderCharts()
            }
        }
        binding.filterPlace.setOnClickListener {
            pick("Local de compra", listOf("Todos") + Store.distinctPlaces(), placeFilter) {
                placeFilter = it
                renderTable()
                renderCharts()
            }
        }

        binding.bodyScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            binding.headerScroll.scrollTo(scrollX, 0)
        }
        binding.headerScroll.setOnTouchListener { _, _ -> true }

        months = PriceAnalytics.monthsFor(Store.purchases, LocalDate.now(), 6)
        rows = PriceAnalytics.rows(Store.purchases, months)
        renderTable()
        renderCharts()

        if (intent.getIntExtra(EXTRA_TAB, 0) == 1) {
            binding.tabs.getTabAt(1)?.select()
        }
    }

    // ------------------------------------------------------------------ tabela

    private fun sortedRows(): List<PriceRow> = when (sort) {
        Sort.NAME -> rows.sortedBy { it.product.lowercase() }
        Sort.UP -> rows.sortedWith(
            compareByDescending<PriceRow> { it.variation ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.product.lowercase() }
        )
        Sort.DOWN -> rows.sortedWith(
            compareBy<PriceRow> { it.variation ?: Double.POSITIVE_INFINITY }
                .thenBy { it.product.lowercase() }
        )
    }

    private fun renderTable() {
        months = PriceAnalytics.monthsFor(Store.purchases, LocalDate.now(), 6)
        rows = PriceAnalytics.rows(Store.purchases, months)
        binding.emptyState.isVisible = rows.isEmpty()
        binding.headerScroll.isVisible = rows.isNotEmpty()
        binding.bodyScroll.isVisible = rows.isNotEmpty()

        val sorted = sortedRows()
        buildHeader()
        binding.bodyRows.removeAllViews()
        sorted.forEach { row -> binding.bodyRows.addView(tableRow(row)) }

        // linha de totais
        val totalCells = mutableListOf<TextView>()
        totalCells.add(cell("Total gasto", dp(108), Gravity.START, true, ContextCompat.getColor(this, R.color.text_primary)))
        totalCells.add(cell("", dp(38), Gravity.CENTER, false, ContextCompat.getColor(this, R.color.text_secondary)))
        totalCells.add(cell("", dp(72), Gravity.CENTER, false, ContextCompat.getColor(this, R.color.text_secondary)))
        months.forEach { month ->
            val total = Store.purchases.filter { YearMonth.from(it.date) == month }.sumOf { it.priceCents }
            totalCells.add(cell(Money.format(total), dp(62), Gravity.CENTER, true, ContextCompat.getColor(this, R.color.brand_indigo)))
        }
        binding.bodyRows.addView(rowWrapper(totalCells, total = true))

        binding.bodyScroll.post { binding.headerScroll.scrollTo(binding.bodyScroll.scrollX, 0) }
    }

    private fun buildHeader() {
        val cells = mutableListOf<TextView>()
        cells.add(cell("Produto", dp(108), Gravity.START, true, ContextCompat.getColor(this, R.color.text_primary)))
        cells.add(cell("Un.", dp(38), Gravity.CENTER, true, ContextCompat.getColor(this, R.color.text_primary)))
        cells.add(cell("Variação", dp(72), Gravity.CENTER, true, ContextCompat.getColor(this, R.color.text_primary)))
        months.forEach { month ->
            cells.add(cell(PriceAnalytics.monthLabel(month), dp(62), Gravity.CENTER, true, ContextCompat.getColor(this, R.color.text_primary)))
        }
        binding.headerRow.removeAllViews()
        binding.headerRow.addView(rowWrapper(cells, header = true))
    }

    private fun tableRow(priceRow: PriceRow): View {
        val cells = mutableListOf<TextView>()
        cells.add(cell(priceRow.product, dp(108), Gravity.START, false, ContextCompat.getColor(this, R.color.text_primary)))
        cells.add(cell(priceRow.unit, dp(38), Gravity.CENTER, false, ContextCompat.getColor(this, R.color.text_tertiary)))

        val variation = priceRow.variation
        val text = when {
            variation == null -> "—"
            variation < 0 -> "↓ ${PriceAnalytics.formatPercent(variation).removePrefix("-")}"
            variation > 0 -> "↑ ${PriceAnalytics.formatPercent(variation).removePrefix("+")}"
            else -> "0,0%"
        }
        val color = when {
            variation == null -> R.color.text_tertiary
            variation < 0 -> R.color.emerald
            variation > 0 -> R.color.rose
            else -> R.color.text_secondary
        }
        cells.add(cell(text, dp(72), Gravity.CENTER, true, ContextCompat.getColor(this, color)))

        months.forEach { month ->
            val value = priceRow.averages[month]
            cells.add(
                cell(
                    value?.let { Money.format(it) } ?: "—",
                    dp(62),
                    Gravity.CENTER,
                    false,
                    ContextCompat.getColor(this, if (value == null) R.color.text_tertiary else R.color.text_secondary)
                )
            )
        }
        return rowWrapper(cells)
    }

    private fun rowWrapper(cells: List<TextView>, header: Boolean = false, total: Boolean = false): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(
                when {
                    header -> ContextCompat.getColor(this@PriceHistoryActivity, R.color.surface_alt)
                    total -> ContextCompat.getColor(this@PriceHistoryActivity, R.color.indigo_50)
                    else -> ContextCompat.getColor(this@PriceHistoryActivity, R.color.surface)
                }
            )
        }
        cells.forEach { row.addView(it) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(View(this@PriceHistoryActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(ContextCompat.getColor(this@PriceHistoryActivity, R.color.divider))
            })
        }
    }

    private fun cell(text: String, width: Int, gravity: Int, bold: Boolean, color: Int): TextView =
        TextView(this).apply {
            this.text = text
            this.width = width
            this.height = dp(42)
            this.gravity = gravity or Gravity.CENTER_VERTICAL
            setTextColor(color)
            textSize = 11f
            setPadding(dp(8), 0, dp(8), 0)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

    // ----------------------------------------------------------------- gráficos

    private fun filteredPurchases(): List<com.taskflow.app.data.Purchase> =
        Store.purchases.filter { purchase ->
            (categoryFilter == "Todas" || purchase.category == categoryFilter) &&
                (placeFilter == "Todos" || purchase.place == placeFilter)
        }

    private fun renderCharts() {
        val filtered = filteredPurchases()
        val chartMonths = PriceAnalytics.monthsFor(Store.purchases, LocalDate.now(), 6)

        binding.filterProduct.text = "Produto: $productFilter"
        binding.filterCategory.text = "Categoria: ${categoryFilter.lowercase()}"
        binding.filterPlace.text = "Local: ${placeFilter.lowercase()}"

        val product = when {
            productFilter != "Todos" -> productFilter
            else -> filtered.groupBy { it.name }.maxByOrNull { it.value.size }?.key
        }

        val linePoints = mutableListOf<PriceLineChartView.Point>()
        if (product != null) {
            chartMonths.forEach { month ->
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
        val unit = PriceAnalytics.rows(filtered, chartMonths)
            .firstOrNull { it.product == product }?.unit ?: "un"
        binding.lineSubtitle.text = when {
            product == null -> "Nenhuma compra para os filtros escolhidos"
            productFilter == "Todos" -> "Produto com mais compras: $product (R$/$unit) · toque nos pontos"
            else -> "Preço médio de $product por $unit · toque nos pontos"
        }

        val barPurchases = if (productFilter == "Todos") filtered else filtered.filter { it.name == productFilter }
        val bars = chartMonths.map { month ->
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

        val currentMonth = YearMonth.from(LocalDate.now())
        val monthTotal = barPurchases.filter { YearMonth.from(it.date) == currentMonth }.sumOf { it.priceCents }
        binding.cardMonthTotal.text = Money.format(monthTotal)

        val filteredRows = PriceAnalytics.rows(filtered, chartMonths)
        val rise = filteredRows.filter { (it.variation ?: 0.0) > 0 }.maxByOrNull { it.variation ?: 0.0 }
        val fall = filteredRows.filter { (it.variation ?: 0.0) < 0 }.minByOrNull { it.variation ?: 0.0 }
        binding.cardRise.text = rise?.let { "${it.product.take(16)} ${PriceAnalytics.formatPercent(it.variation!!)}" } ?: "—"
        binding.cardFall.text = fall?.let { "${it.product.take(16)} ${PriceAnalytics.formatPercent(it.variation!!)}" } ?: "—"

        val savings = filteredRows.sumOf { row ->
            val current = row.averages[currentMonth] ?: return@sumOf 0L
            val minimum = row.averages.values.filterNotNull().minOrNull() ?: current
            val count = Store.purchases.count { it.productId == row.productId && YearMonth.from(it.date) == currentMonth }
            if (current > minimum) (current - minimum) * count else 0L
        }
        binding.cardSavings.text = Money.format(savings)

        val total = barPurchases.sumOf { it.priceCents }
        val average = if (chartMonths.isEmpty()) 0L else total / chartMonths.size
        binding.summary.text = if (barPurchases.isEmpty()) {
            "Nenhuma compra registrada para estes filtros."
        } else {
            "Total no período: ${Money.format(total)} · média por mês: ${Money.format(average)} · " +
                "${barPurchases.size} ${if (barPurchases.size == 1) "compra" else "compras"}"
        }
    }

    // ------------------------------------------------------------------ filtros

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

    private fun pickProduct() {
        val options = mutableListOf("Todos")
        options.addAll(Store.distinctProducts())
        val view = layoutInflater.inflate(R.layout.dialog_search_list, null)
        val search = view.findViewById<EditText>(R.id.search)
        val list = view.findViewById<ListView>(R.id.list)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
        list.adapter = adapter
        val dialog = AlertDialog.Builder(this)
            .setTitle("Produto")
            .setView(view)
            .setNegativeButton("Cancelar", null)
            .create()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, position, _ ->
            productFilter = adapter.getItem(position) ?: "Todos"
            dialog.dismiss()
            renderCharts()
        }
        dialog.show()
    }

    // -------------------------------------------------------------------- CSV

    private fun writeCsv(uri: Uri) {
        runCatching {
            val builder = StringBuilder("\uFEFF")
            builder.append("Produto;Unidade;")
            months.forEach { builder.append(PriceAnalytics.monthLabel(it)).append(";") }
            builder.append("Variação\n")
            sortedRows().forEach { row ->
                builder.append(csvEscape(row.product)).append(";").append(row.unit).append(";")
                months.forEach { month ->
                    builder.append(row.averages[month]?.let { Money.formatPlain(it) } ?: "").append(";")
                }
                builder.append(row.variation?.let { PriceAnalytics.formatPercent(it) } ?: "")
                builder.append("\n")
            }
            contentResolver.openOutputStream(uri)?.use {
                it.write(builder.toString().toByteArray(Charsets.UTF_8))
            }
            toast("CSV exportado")
        }.onFailure { toast("Falha ao exportar CSV") }
    }

    private fun csvEscape(value: String): String =
        if (value.contains(';') || value.contains('"')) "\"${value.replace("\"", "\"\"")}\"" else value

    // -------------------------------------------------------------------- PDF

    private fun writePdf(uri: Uri) {
        runCatching {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 9f
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(79, 70, 229)
                textSize = 17f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.7f
            }

            val productX = margin
            val unitX = margin + 108f
            val variationX = unitX + 34f
            val monthsX = variationX + 58f
            val monthWidth = 48f

            fun drawHeader(canvas: Canvas, page: Int) {
                canvas.drawText("TaskFlow — Histórico de preços", productX, 52f, titlePaint)
                canvas.drawText("Média paga por produto · gerado em ${Dates.timestamp()}", productX, 68f, mutedPaint)
                canvas.drawText("Produto", productX, 96f, headerPaint)
                canvas.drawText("Un.", unitX, 96f, headerPaint)
                canvas.drawText("Variação", variationX, 96f, headerPaint)
                var x = monthsX
                months.forEach { month ->
                    canvas.drawText(PriceAnalytics.monthLabel(month), x, 96f, headerPaint)
                    x += monthWidth
                }
                canvas.drawLine(margin, 101f, pageWidth - margin, 101f, linePaint)
                canvas.drawText("Página $page", pageWidth - margin - 40f, pageHeight - 24f, mutedPaint)
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            drawHeader(canvas, pageNumber)
            var y = 118f

            sortedRows().forEach { row ->
                if (y > pageHeight - 60f) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    drawHeader(canvas, pageNumber)
                    y = 118f
                }
                val name = if (row.product.length > 20) row.product.take(19) + "…" else row.product
                canvas.drawText(name, productX, y, textPaint)
                canvas.drawText(row.unit, unitX, y, textPaint)

                val variation = row.variation
                val label = variation?.let {
                    (if (it < 0) "↓ " else "↑ ") + PriceAnalytics.formatPercent(it).removePrefix("+").removePrefix("-")
                } ?: "—"
                textPaint.color = when {
                    variation == null -> Color.rgb(148, 163, 184)
                    variation < 0 -> Color.rgb(5, 150, 105)
                    variation > 0 -> Color.rgb(225, 29, 72)
                    else -> Color.rgb(100, 116, 139)
                }
                canvas.drawText(label, variationX, y, textPaint)
                textPaint.color = Color.rgb(15, 23, 42)

                var x = monthsX
                months.forEach { month ->
                    val value = row.averages[month]?.let { Money.formatPlain(it) } ?: "—"
                    canvas.drawText(value, x, y, textPaint)
                    x += monthWidth
                }

                canvas.drawLine(margin, y + 5f, pageWidth - margin, y + 5f, linePaint)
                y += 20f
            }

            document.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            document.close()
            toast("PDF exportado")
        }.onFailure { toast("Falha ao exportar PDF") }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TAB = "tab"

        fun start(context: Context, tab: Int = 0) {
            context.startActivity(
                Intent(context, PriceHistoryActivity::class.java).putExtra(EXTRA_TAB, tab)
            )
        }
    }
}
