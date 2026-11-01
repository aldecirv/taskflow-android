package com.taskflow.app.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taskflow.app.R
import com.taskflow.app.data.ProductCategories
import com.taskflow.app.data.Store
import com.taskflow.app.data.Purchase
import com.taskflow.app.databinding.ItemProductPurchaseBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Images
import com.taskflow.app.util.Money
import java.time.LocalDate

class ProductAdapter(
    private val onOpen: (Purchase) -> Unit,
    private val onDelete: (Purchase) -> Unit
) : ListAdapter<Purchase, ProductAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemProductPurchaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemProductPurchaseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(purchase: Purchase) {
            val context = binding.root.context
            binding.name.text = purchase.name
            binding.price.text = Money.format(purchase.priceCents)

            val dateLabel = when (purchase.date) {
                LocalDate.now() -> "Hoje"
                LocalDate.now().minusDays(1) -> "Ontem"
                else -> Dates.shortDate(purchase.date)
            }
            val place = purchase.place.ifBlank { "Sem local" }
            binding.meta.text = "$dateLabel · $place"

            val category = Store.productCategory(purchase.category)
            val color = runCatching { Color.parseColor(category.colorHex) }.getOrDefault(Color.GRAY)
            binding.categoryIcon.setImageResource(category.icon)
            binding.categoryIcon.imageTintList = ColorStateList.valueOf(color)

            val bitmap = Images.load(purchase.imagePath, 160)
            if (bitmap != null) {
                binding.thumb.setImageBitmap(bitmap)
                binding.thumb.visibility = View.VISIBLE
                binding.categoryIcon.visibility = View.GONE
            } else {
                binding.thumb.visibility = View.GONE
                binding.categoryIcon.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener { onOpen(purchase) }
            binding.more.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.menu_product_item, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit -> onOpen(purchase)
                            R.id.action_delete -> onDelete(purchase)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Purchase>() {
        override fun areItemsTheSame(oldItem: Purchase, newItem: Purchase) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Purchase, newItem: Purchase) = oldItem == newItem
    }
}
