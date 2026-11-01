package com.taskflow.app.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taskflow.app.R
import com.taskflow.app.data.Habit
import com.taskflow.app.databinding.ItemHabitBinding
import com.taskflow.app.util.Dates
import java.time.LocalDate

class HabitAdapter(
    private val onToggle: (Habit) -> Unit,
    private val onLongPress: (Habit) -> Unit
) : ListAdapter<Habit, HabitAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemHabitBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(habit: Habit) {
            val context = binding.root.context
            val color = runCatching { Color.parseColor(habit.colorHex) }.getOrDefault(Color.parseColor("#06B6D4"))
            val doneToday = habit.isDoneOn(LocalDate.now())

            binding.name.text = habit.name
            binding.subtitle.text = "${habit.category} · ${habit.frequency.label}"
            binding.dot.backgroundTintList = ColorStateList.valueOf(color)
            binding.streak.text = "${habit.streak}d"
            binding.best.text = "Recorde: ${habit.bestStreak} dias"

            binding.weekRow.removeAllViews()
            habit.lastSevenDays().forEach { date ->
                binding.weekRow.addView(dayCell(date, habit.isDoneOn(date), color, date == LocalDate.now()))
            }

            binding.toggleButton.text = if (doneToday) "Feito hoje ✓" else "Marcar hoje"
            binding.toggleButton.backgroundTintList = ColorStateList.valueOf(
                if (doneToday) ColorUtils.setAlphaComponent(color, 40) else color
            )
            binding.toggleButton.setTextColor(if (doneToday) color else Color.WHITE)
            binding.toggleButton.setOnClickListener { onToggle(habit) }
            binding.root.setOnLongClickListener {
                onLongPress(habit)
                true
            }
        }

        private fun dayCell(date: LocalDate, done: Boolean, color: Int, isToday: Boolean): View {
            val context = binding.root.context
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val label = TextView(context).apply {
                text = Dates.weekdayShort(date).take(1)
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(context.getColor(if (isToday) R.color.text_primary else R.color.text_tertiary))
            }
            val circle = TextView(context).apply {
                text = if (done) "✓" else ""
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { topMargin = dp(4) }
                background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_dot)!!.mutate().apply {
                    setTint(if (done) color else ColorUtils.setAlphaComponent(color, 30))
                }
            }
            column.addView(label)
            column.addView(circle)
            return column
        }

        private fun dp(value: Int): Int =
            (value * binding.root.resources.displayMetrics.density).toInt()
    }

    private object Diff : DiffUtil.ItemCallback<Habit>() {
        override fun areItemsTheSame(oldItem: Habit, newItem: Habit) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Habit, newItem: Habit) = oldItem == newItem
    }
}
