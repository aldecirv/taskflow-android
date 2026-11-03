package com.taskflow.app.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.PopupMenu
import com.taskflow.app.R
import com.taskflow.app.data.Categories
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.ItemTaskBinding
import com.taskflow.app.util.Dates

class TaskAdapter(
    private val onToggle: (Task) -> Unit,
    private val onOpen: (Task) -> Unit,
    private val onFocus: (Task) -> Unit,
    private val onDuplicate: (Task) -> Unit,
    private val onDelete: (Task) -> Unit,
    private val onTimer: ((Task) -> Unit)? = null
) : ListAdapter<Task, TaskAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: Task) {
            val context = binding.root.context
            val priorityColor = Color.parseColor(task.priority.colorHex)
            binding.priority.text = task.priority.label.uppercase()
            binding.priority.setTextColor(priorityColor)
            binding.priority.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(priorityColor, 30))

            binding.category.text = task.category
            binding.category.setTextColor(Color.parseColor(Categories.color(task.category)))
            val today = java.time.LocalDate.now()
            binding.time.text = when {
                task.isOverdue(today) -> "Atrasada"
                task.date == today -> task.timeLabel()
                task.date == today.plusDays(1) -> "Amanhã"
                else -> Dates.relative(task.date)
            }
            binding.time.setTextColor(
                if (task.isOverdue(today)) ContextCompat.getColor(context, R.color.rose)
                else ContextCompat.getColor(context, R.color.text_secondary)
            )
            binding.title.text = task.title
            binding.notes.text = task.notes
            binding.notes.visibility = if (task.notes.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            binding.title.paintFlags = if (task.completed) {
                binding.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.content.alpha = if (task.completed) 0.55f else 1f

            if (task.completed) {
                binding.checkButton.setImageResource(R.drawable.ic_check_circle)
                binding.checkButton.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.emerald))
            } else {
                binding.checkButton.setImageResource(R.drawable.ic_radio_button_unchecked)
                binding.checkButton.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_tertiary))
            }

            val subTotal = task.subtasks.size
            if (subTotal > 0) {
                binding.subtaskRow.visibility = android.view.View.VISIBLE
                binding.subtaskBar.progress = task.subtaskProgress
                binding.subtaskText.text =
                    "${task.subtasks.count { it.done }}/$subTotal etapas"
            } else {
                binding.subtaskRow.visibility = android.view.View.GONE
            }

            binding.estimate.text = Dates.durationLabel(task.estimateMinutes)
            val showPomodoro = task.targetPomodoros > 0
            binding.pomodoroIcon.visibility = if (showPomodoro) android.view.View.VISIBLE else android.view.View.GONE
            binding.pomodoroText.visibility = if (showPomodoro) android.view.View.VISIBLE else android.view.View.GONE
            binding.pomodoroText.text = "${task.pomodoroCount}/${task.targetPomodoros} pomodoros"
            binding.alarmIcon.visibility =
                if (task.alertEnabled && task.time != null) android.view.View.VISIBLE else android.view.View.GONE
            binding.trackedText.visibility =
                if (task.trackedSeconds > 0) android.view.View.VISIBLE else android.view.View.GONE
            binding.trackedText.text = "⏱ " + Dates.durationLabel(task.trackedSeconds / 60)

            binding.recurrence.visibility =
                if (task.recurrence != com.taskflow.app.data.Recurrence.NONE) android.view.View.VISIBLE else android.view.View.GONE
            binding.recurrence.text = task.recurrence.label

            binding.checkButton.setOnClickListener { onToggle(task) }
            binding.content.setOnClickListener { onOpen(task) }
            binding.root.setOnClickListener { onOpen(task) }
            binding.moreButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.menu_task_item, menu)
                    menu.findItem(R.id.action_timer)?.isVisible = onTimer != null
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit -> onOpen(task)
                            R.id.action_focus -> onFocus(task)
                            R.id.action_timer -> onTimer?.invoke(task)
                            R.id.action_duplicate -> onDuplicate(task)
                            R.id.action_delete -> onDelete(task)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
