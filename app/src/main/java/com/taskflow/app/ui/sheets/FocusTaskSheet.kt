package com.taskflow.app.ui.sheets

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.SheetFocusTaskBinding
import com.taskflow.app.service.Pomodoro
import com.taskflow.app.util.Dates

class FocusTaskSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFocusTaskBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetFocusTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.taskOptions.addView(option("Foco livre", "Sem tarefa vinculada", null))

        Store.pendingTasks().take(30).forEach { task ->
            binding.taskOptions.addView(
                option(
                    task.title,
                    "${Dates.relative(task.date)} · ${task.timeLabel()} · ${task.category}",
                    task
                )
            )
        }
    }

    private fun option(title: String, subtitle: String, task: Task?): View {
        val context = requireContext()
        val titleColor = com.google.android.material.color.MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            Color.parseColor("#0F172A")
        )
        val subtitleColor = com.google.android.material.color.MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.parseColor("#64748B")
        )
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(com.taskflow.app.R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            isClickable = true
            setOnClickListener {
                Pomodoro.taskId = task?.id
                Pomodoro.notifyListeners()
                dismiss()
            }
        }
        val titleView = TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(if (task == null) Color.parseColor("#4F46E5") else titleColor)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.START
        }
        val subtitleView = TextView(context).apply {
            text = subtitle
            textSize = 11f
            setTextColor(subtitleColor)
        }
        row.addView(titleView)
        row.addView(subtitleView)
        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
