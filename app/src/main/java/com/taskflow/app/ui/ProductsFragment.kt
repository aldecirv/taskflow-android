package com.taskflow.app.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.taskflow.app.R
import com.taskflow.app.data.Purchase
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.FragmentProductsBinding
import com.taskflow.app.ui.adapters.ProductAdapter
import com.taskflow.app.util.Money
import java.time.LocalDate
import java.time.YearMonth

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductAdapter
    private var displayedTotal: Long? = null

    private val refresh: () -> Unit = { if (isAdded && _binding != null) render() }

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val id = data.getStringExtra(ProductEditorActivity.EXTRA_PURCHASE_ID) ?: return@registerForActivityResult
            val name = data.getStringExtra(ProductEditorActivity.EXTRA_PURCHASE_NAME).orEmpty()
            val isNew = data.getBooleanExtra(ProductEditorActivity.EXTRA_IS_NEW, false)
            if (isNew) showUndo("\"${name.ifBlank { "Produto" }}\" adicionada") { Store.deletePurchase(id) }
            render()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductAdapter(
            onOpen = { openEditor(it.id) },
            onDelete = { confirmDelete(it) }
        )
        binding.purchaseList.layoutManager = LinearLayoutManager(requireContext())
        binding.purchaseList.adapter = adapter

        binding.btnCamera.setOnClickListener { openNew(ProductEditorActivity.Mode.PHOTO) }
        binding.btnBarcode.setOnClickListener { openNew(ProductEditorActivity.Mode.BARCODE) }
        binding.btnManual.setOnClickListener { openNew(ProductEditorActivity.Mode.MANUAL) }
        binding.btnSheet.setOnClickListener { PriceHistoryActivity.start(requireContext(), 0) }
        binding.btnCharts.setOnClickListener { PriceHistoryActivity.start(requireContext(), 1) }

        Store.addListener(refresh)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroyView() {
        Store.removeListener(refresh)
        _binding = null
        super.onDestroyView()
    }

    private fun render() {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val monthPurchases = Store.purchases.filter { YearMonth.from(it.date) == month }
        val monthTotal = monthPurchases.sumOf { it.priceCents }
        val todayPurchases = Store.purchases.filter { it.date == today }

        animateTotal(monthTotal)
        binding.dayTotal.text = "Hoje: ${Money.format(todayPurchases.sumOf { it.priceCents })} · ${todayPurchases.size} itens"
        binding.monthItems.text = "${monthPurchases.size} itens no mês"
        binding.subtitle.text = "${Store.purchases.size} compras · ${Money.format(monthTotal)} no mês"
        binding.recentCount.text = "${Store.purchases.size}"

        val list = Store.purchases.sortedWith(
            compareByDescending<Purchase> { it.date }.thenByDescending { it.createdAt }
        )
        adapter.submitList(list)
        binding.emptyState.isVisible = list.isEmpty()
        binding.purchaseList.isVisible = list.isNotEmpty()
    }

    private fun animateTotal(target: Long) {
        val from = displayedTotal ?: target
        displayedTotal = target
        if (from == target || !isAdded) {
            binding.monthTotal.text = Money.format(target)
            return
        }
        ValueAnimator.ofFloat(from.toFloat(), target.toFloat()).apply {
            duration = 520
            addUpdateListener {
                binding.monthTotal.text = Money.format((it.animatedValue as Float).toLong())
            }
            start()
        }
    }

    private fun openNew(mode: ProductEditorActivity.Mode) {
        editorLauncher.launch(ProductEditorActivity.intent(requireContext(), mode))
    }

    private fun openEditor(id: String) {
        editorLauncher.launch(ProductEditorActivity.editIntent(requireContext(), id))
    }

    private fun confirmDelete(purchase: Purchase) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir compra?")
            .setMessage(purchase.name)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                Store.deletePurchase(purchase.id)
                showUndo("\"${purchase.name}\" removida") { Store.restorePurchase(purchase) }
                render()
            }
            .show()
    }

    private fun showUndo(message: String, onUndo: () -> Unit) {
        val root = _binding?.root ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
            .setAction("Desfazer") {
                onUndo()
                render()
            }
            .show()
    }
}
