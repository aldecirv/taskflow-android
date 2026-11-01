package com.taskflow.app.ui.sheets

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.taskflow.app.R
import com.taskflow.app.data.PlanTemplate
import com.taskflow.app.data.Store
import com.taskflow.app.data.SubTask
import com.taskflow.app.data.Task
import com.taskflow.app.data.Templates
import com.taskflow.app.databinding.SheetTemplatesBinding
import com.taskflow.app.util.Dates
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class TemplatesSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTemplatesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Templates.all.forEach { template ->
            binding.templateList.addView(templateCard(template))
        }
    }

    private fun templateCard(template: PlanTemplate): View {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            radius = dp(20).toFloat()
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(context, R.color.border)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = template.emoji
            textSize = 22f
        })
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        }
        titles.addView(TextView(context).apply {
            text = template.title
            textSize = 14.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })
        titles.addView(TextView(context).apply {
            text = "${template.category} · ${template.badge}"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        header.addView(titles)
        content.addView(header)

        content.addView(TextView(context).apply {
            text = template.description
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        })

        content.addView(TextView(context).apply {
            text = "${template.tasks.size} tarefas · ${template.tasks.sumOf { it.estimateMinutes }} min no total"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            setPadding(0, dp(6), 0, 0)
        })

        val button = MaterialButton(context).apply {
            text = "Usar este modelo"
            textSize = 12f
            cornerRadius = dp(12)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.brand_indigo)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply { topMargin = dp(12) }
            setOnClickListener { applyTemplate(template) }
        }
        content.addView(button)
        card.addView(content)
        return card
    }

    private fun applyTemplate(template: PlanTemplate) {
        val today = LocalDate.now()
        template.tasks.forEach { item ->
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = item.title,
                category = template.category,
                priority = item.priority,
                date = today.plusDays(item.dayOffset.toLong()),
                time = runCatching { LocalTime.parse(item.time) }.getOrNull(),
                estimateMinutes = item.estimateMinutes,
                subtasks = item.subtasks.map {
                    SubTask(UUID.randomUUID().toString(), it, false)
                }.toMutableList(),
                reminders = mutableListOf(15)
            )
            Store.saveTask(task)
        }
        Store.award(50)
        Toast.makeText(
            requireContext(),
            "${template.tasks.size} tarefas criadas · +50 XP",
            Toast.LENGTH_SHORT
        ).show()
        dismiss()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
