package com.taskflow.app

import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.PriceAnalytics
import com.taskflow.app.data.Purchase
import com.taskflow.app.data.Store
import com.taskflow.app.ui.PriceChartsActivity
import com.taskflow.app.ui.PriceSheetActivity
import com.taskflow.app.ui.PriceHistoryActivity
import com.taskflow.app.ui.ProductEditorActivity
import com.taskflow.app.util.ProductVision
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductsTest {

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TaskFlowApp>()
        Store.init(app)
        Store.resetAll()
        Store.updateSettings { it.hasCompletedOnboarding = true }
    }

    @Test
    fun totalsAndUndoRestore() {
        val today = LocalDate.now()
        val first = Purchase(UUID.randomUUID().toString(), "Café", 1890, today, "Mercado", "Mercado")
        val second = Purchase(UUID.randomUUID().toString(), "Leite", 549, today, "Mercado", "Mercado")
        Store.addPurchase(first)
        Store.addPurchase(second)

        assertEquals(2439L, Store.totalOn(today))
        assertEquals(2439L, Store.totalIn(YearMonth.from(today)))

        Store.deletePurchase(first.id)
        assertEquals(549L, Store.totalOn(today))

        Store.restorePurchase(first)
        assertEquals(2439L, Store.totalOn(today))
        assertEquals(2, Store.purchases.size)
    }

    @Test
    fun priceAnalyticsComputesMonthlyAverageAndVariation() {
        val today = LocalDate.now()
        val m2 = YearMonth.from(today).minusMonths(2)
        val m1 = YearMonth.from(today).minusMonths(1)
        val current = YearMonth.from(today)
        fun add(month: YearMonth, cents: Long) {
            Store.addPurchase(
                Purchase(
                    UUID.randomUUID().toString(), "Café 500g", cents,
                    month.atDay(5), "Mercado", "Mercado"
                )
            )
        }
        add(m2, 1890); add(m1, 1980); add(current, 2150)

        val months = PriceAnalytics.months(today, 6)
        val row = PriceAnalytics.rows(Store.purchases, months).first { it.product == "Café 500g" }
        assertEquals(1890L, row.averages[m2])
        assertEquals(1980L, row.averages[m1])
        assertEquals(2150L, row.averages[current])
        val expected = (2150.0 - 1980.0) / 1980.0 * 100.0
        assertEquals(expected, row.variation!!, 0.01)
    }

    @Test
    fun priceSheetBuildsTableAndChartsLaunch() {
        Store.seedDemoData()
        val sheet = Robolectric.buildActivity(PriceSheetActivity::class.java).setup().get()
        val table = sheet.findViewById<LinearLayout>(R.id.tableContainer)
        assertTrue(table.childCount > 2)

        Robolectric.buildActivity(PriceChartsActivity::class.java).setup().get()
    }

    @Test
    fun productEditorSavesManualPurchase() {
        val activity = Robolectric.buildActivity(ProductEditorActivity::class.java).setup().get()
        activity.findViewById<EditText>(R.id.nameInput).setText("Sabão em pó")
        activity.findViewById<EditText>(R.id.priceInput).setText("1590")
        ShadowLooper.idleMainLooper()
        activity.findViewById<android.view.View>(R.id.btnSave).performClick()
        ShadowLooper.idleMainLooper()

        val saved = Store.purchases.firstOrNull { it.name == "Sabão em pó" }
        assertTrue(saved != null)
        assertEquals(1590L, saved!!.priceCents)
        assertEquals(LocalDate.now(), saved.date)
    }

    @Test
    fun fuzzyNameMatchingKeepsSameProductId() {
        Store.addPurchase(
            Purchase(UUID.randomUUID().toString(), "Café Pilão 500g", 1890, LocalDate.now(), "Mercado", "Mercado")
        )
        Store.addPurchase(
            Purchase(UUID.randomUUID().toString(), "cafe pilao 500 g", 1990, LocalDate.now(), "Mercado", "Mercado")
        )
        assertEquals(1, Store.products.size)
        assertEquals(Store.products.first().id, Store.purchases[0].productId)
        assertEquals(Store.products.first().id, Store.purchases[1].productId)
    }

    @Test
    fun barcodeMatchingWinsEvenWithDifferentNames() {
        Store.addPurchase(
            Purchase(UUID.randomUUID().toString(), "Leite Integral 1L", 549, LocalDate.now(), "Mercado", "Mercado", barcode = "7891000053508")
        )
        Store.addPurchase(
            Purchase(UUID.randomUUID().toString(), "Leite da marca boa", 599, LocalDate.now(), "Mercado", "Mercado", barcode = "7891000053508")
        )
        assertEquals(1, Store.products.size)
        assertEquals(Store.products.first().id, Store.purchases[0].productId)
    }

    @Test
    fun variationIsNullWhenPreviousMonthIsMissing() {
        val today = LocalDate.now()
        Store.addPurchase(
            Purchase(UUID.randomUUID().toString(), "Produto novo", 1000, today, "Mercado", "Mercado")
        )
        val months = PriceAnalytics.monthsFor(Store.purchases, today, 6)
        val row = PriceAnalytics.rows(Store.purchases, months).first()
        assertEquals(null, row.variation)
    }

    @Test
    fun priceHistoryActivityHasTwoTabsAndCharts() {
        Store.seedDemoData()
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(),
            PriceHistoryActivity::class.java
        ).putExtra(PriceHistoryActivity.EXTRA_TAB, 1)
        val activity = Robolectric.buildActivity(PriceHistoryActivity::class.java, intent).setup().get()
        val tabs = activity.findViewById<TabLayout>(R.id.tabs)
        assertEquals(2, tabs.tabCount)
        tabs.getTabAt(1)?.select()
        ShadowLooper.idleMainLooper()
        assertTrue(activity.findViewById<android.view.View>(R.id.lineChart) != null)
    }

    @Test
    fun ocrGuessExtractsProductAndPrice() {
        val guess = ProductVision.guessFromText(
            "SUPERMERCADO CENTRAL\nLEITE INTEGRAL 1L\nR$ 5,49\nTOTAL 12,90"
        )
        assertTrue(guess != null)
        assertTrue(guess!!.name.contains("LEITE", ignoreCase = true))
        assertEquals(549L, guess.priceCents)
    }
}
