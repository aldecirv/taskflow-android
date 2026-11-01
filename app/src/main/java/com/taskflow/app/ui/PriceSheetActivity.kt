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
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.taskflow.app.R
import com.taskflow.app.data.PriceAnalytics
import com.taskflow.app.data.PriceRow
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityPriceSheetBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Money
import com.taskflow.app.util.ThemeManager
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class PriceSheetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriceSheetBinding
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
        binding = ActivityPriceSheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCsv.setOnClickListener {
            if (rows.isEmpty()) toast("Nada para exportar") else
                csvLauncher.launch("taskflow-precos-${LocalDate.now()}.csv")
        }
        binding.btnPdf.setOnClickListener {
            if (rows.isEmpty()) toast("Nada para exportar") else
                pdfLauncher.launch("taskflow-precos-${LocalDate.now()}.pdf")
        }

        months = PriceAnalytics.monthsFor(Store.purchases, LocalDate.now(), 6)
        rows = PriceAnalytics.rows(Store.purchases, months)
        buildTable()
    }

    private fun buildTable() {
        binding.tableContainer.removeAllViews()
        binding.emptyState.isVisible = rows.isEmpty()

        if (rows.isEmpty()) return

        val productWidth = dp(122)
        val monthWidth = dp(64)
        val variationWidth = dp(76)

        binding.tableContainer.addView(
            row(
                cells = listOf(
                    cell("Produto", productWidth, Gravity.START, true, ContextCompat.getColor(this, R.color.text_primary)),
                    cell(
                        "Variação",
                        variationWidth,
                        Gravity.CENTER,
                        true,
                        ContextCompat.getColor(this, R.color.text_primary)
                    ),
                    *months.map { month ->
                        cell(
                            PriceAnalytics.monthLabel(month),
                            monthWidth,
                            Gravity.CENTER,
                            true,
                            ContextCompat.getColor(this, R.color.text_primary)
                        )
                    }.toTypedArray()
                ),
                header = true
            )
        )

        rows.forEach { priceRow ->
            val cells = mutableListOf<TextView>()
            cells.add(cell(priceRow.product, productWidth, Gravity.START, false, ContextCompat.getColor(this, R.color.text_primary)))
            val variation = priceRow.variation
            val variationText = when {
                variation == null -> "—"
                variation < 0 -> "↓ ${PriceAnalytics.formatPercent(variation).removePrefix("-")}"
                variation > 0 -> "↑ ${PriceAnalytics.formatPercent(variation).removePrefix("+")}"
                else -> "0,0%"
            }
            val variationColor = when {
                variation == null -> R.color.text_tertiary
                variation < 0 -> R.color.emerald
                variation > 0 -> R.color.rose
                else -> R.color.text_secondary
            }
            cells.add(
                cell(
                    variationText,
                    variationWidth,
                    Gravity.CENTER,
                    true,
                    ContextCompat.getColor(this, variationColor)
                )
            )
            months.forEach { month ->
                val value = priceRow.averages[month]
                cells.add(
                    cell(
                        value?.let { Money.format(it) } ?: "—",
                        monthWidth,
                        Gravity.CENTER,
                        false,
                        ContextCompat.getColor(
                            this,
                            if (value == null) R.color.text_tertiary else R.color.text_secondary
                        )
                    )
                )
            }
            binding.tableContainer.addView(row(cells, header = false))
        }

        // linha de totais
        val totals = months.map { month -> Store.purchases.filter { YearMonth.from(it.date) == month }.sumOf { it.priceCents } }
        val totalCells = mutableListOf<TextView>()
        totalCells.add(cell("Total", productWidth, Gravity.START, true, ContextCompat.getColor(this, R.color.text_primary)))
        totalCells.add(cell("", variationWidth, Gravity.CENTER, false, ContextCompat.getColor(this, R.color.text_secondary)))
        months.indices.forEach { index ->
            totalCells.add(
                cell(
                    Money.format(totals[index]),
                    monthWidth,
                    Gravity.CENTER,
                    true,
                    ContextCompat.getColor(this, R.color.brand_indigo)
                )
            )
        }
        binding.tableContainer.addView(row(totalCells, header = false, total = true))
    }

    private fun cell(
        text: String,
        width: Int,
        gravity: Int,
        bold: Boolean,
        color: Int
    ): TextView = TextView(this).apply {
        this.text = text
        this.gravity = gravity
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

    private fun row(cells: List<TextView>, header: Boolean, total: Boolean = false): LinearLayout {
        val context = this
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(
                when {
                    header -> ContextCompat.getColor(context, R.color.surface_alt)
                    total -> ContextCompat.getColor(context, R.color.indigo_50)
                    else -> ContextCompat.getColor(context, R.color.surface)
                }
            )
        }
        cells.forEach { row.addView(it) }
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(android.view.View(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            })
        }
        return wrapper
    }

    // -------------------------------------------------------------------- CSV

    private fun writeCsv(uri: Uri) {
        runCatching {
            val builder = StringBuilder("\uFEFF")
            builder.append("Produto;")
            builder.append(months.joinToString(";") { PriceAnalytics.monthLabel(it) })
            builder.append(";Variação\n")
            rows.forEach { row ->
                builder.append(csvEscape(row.product)).append(";")
                months.forEach { month ->
                    val value = row.averages[month]
                    builder.append(value?.let { Money.formatPlain(it) } ?: "").append(";")
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
        if (value.contains(';') || value.contains('"')) "\"" + value.replace("\"", "\"\"") + "\"" else value

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
            val monthsX = margin + 118f
            val monthWidth = 48f
            val variationX = monthsX + monthWidth * months.size + 8f

            fun drawHeader(canvas: Canvas, page: Int) {
                canvas.drawText("TaskFlow — Planilha de preços", productX, 52f, titlePaint)
                canvas.drawText(
                    "Média paga por produto · gerado em ${Dates.timestamp()}",
                    productX, 68f, mutedPaint
                )
                var x = productX
                canvas.drawText("Produto", x, 96f, headerPaint)
                x = monthsX
                months.forEach { month ->
                    canvas.drawText(PriceAnalytics.monthLabel(month), x, 96f, headerPaint)
                    x += monthWidth
                }
                canvas.drawText("Variação", variationX, 96f, headerPaint)
                canvas.drawLine(margin, 101f, pageWidth - margin, 101f, linePaint)
                canvas.drawText("Página $page", pageWidth - margin - 40f, pageHeight - 24f, mutedPaint)
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            drawHeader(canvas, pageNumber)
            var y = 118f

            rows.forEach { row ->
                if (y > pageHeight - 60f) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    drawHeader(canvas, pageNumber)
                    y = 118f
                }
                var x = productX
                val name = if (row.product.length > 24) row.product.take(23) + "…" else row.product
                canvas.drawText(name, x, y, textPaint)
                x = monthsX
                months.forEach { month ->
                    val value = row.averages[month]?.let { Money.formatPlain(it) } ?: "—"
                    canvas.drawText(value, x, y, textPaint)
                    x += monthWidth
                }
                val variation = row.variation
                val variationLabel = variation?.let {
                    (if (it < 0) "↓ " else "↑ ") + PriceAnalytics.formatPercent(it).removePrefix("+").removePrefix("-")
                } ?: "—"
                val color = when {
                    variation == null -> Color.rgb(148, 163, 184)
                    variation < 0 -> Color.rgb(5, 150, 105)
                    variation > 0 -> Color.rgb(225, 29, 72)
                    else -> Color.rgb(100, 116, 139)
                }
                textPaint.color = color
                canvas.drawText(variationLabel, variationX, y, textPaint)
                textPaint.color = Color.rgb(15, 23, 42)

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
        fun start(context: Context) {
            context.startActivity(Intent(context, PriceSheetActivity::class.java))
        }
    }
}
