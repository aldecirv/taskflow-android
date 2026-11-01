package com.taskflow.app.ui.sheets

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.SheetWeeklyBinding
import com.taskflow.app.util.Dates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class WeeklyReviewSheet : BottomSheetDialogFragment() {

    private var _binding: SheetWeeklyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetWeeklyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        binding.weekRange.text = "${Dates.shortDate(weekStart)} a ${Dates.shortDate(today)}"
        binding.btnClose.setOnClickListener { dismiss() }

        val completed = Store.tasks.filter {
            it.completedAt?.toLocalDate()?.let { d -> !d.isBefore(weekStart) } == true
        }
        val created = Store.tasks.count { !it.createdAt.toLocalDate().isBefore(weekStart) }
        val habitCheckins = Store.habits.sumOf { habit ->
            habit.completedDates.count { !it.isBefore(weekStart) && !it.isAfter(today) }
        }

        binding.statsRow.addView(stat("${completed.size}", "Concluídas"))
        binding.statsRow.addView(stat("$created", "Criadas"))
        binding.statsRow.addView(stat("$habitCheckins", "Hábitos"))

        val bestDay = completed.groupBy { it.completedAt!!.dayOfWeek }
            .maxByOrNull { it.value.size }?.key
        val bestCategory = completed.groupBy { it.category }.maxByOrNull { it.value.size }?.key
        val bestHabit = Store.habits.maxByOrNull { it.streak }

        addHighlight("✅", "${completed.size} tarefas concluídas nesta semana")
        if (bestDay != null) {
            addHighlight(
                "📅",
                "Dia mais produtivo: ${bestDay.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))}"
            )
        }
        if (bestCategory != null) addHighlight("🎯", "Categoria em destaque: $bestCategory")
        if (bestHabit != null && bestHabit.streak > 0) {
            addHighlight("🔥", "Sequência ativa: ${bestHabit.name} (${bestHabit.streak} dias)")
        }
        if (completed.isEmpty()) addHighlight("🌱", "Semana tranquila — comece com uma tarefa pequena hoje.")

        val pending = Store.pendingTasks()
        val overdue = Store.overdueTasks()
        binding.suggestion.text = when {
            overdue.isNotEmpty() ->
                "Você tem ${overdue.size} tarefa(s) atrasada(s). Reagende ou redistribua para aliviar a próxima semana."
            pending.size > 8 ->
                "Sua lista tem ${pending.size} tarefas em aberto. Escolha as 3 mais importantes e deixe o resto para depois."
            else ->
                "Bom ritmo! Para a próxima semana, reserve blocos de foco no calendário e proteja seu horário de pico."
        }
    }

    private fun stat(value: String, label: String): View {
        val context = requireContext()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, dp(12), 0, dp(12))
            background = ContextCompat.getDrawable(context, R.drawable.bg_input)
        }
        column.addView(TextView(context).apply {
            text = value
            textSize = 20f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })
        column.addView(TextView(context).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        return column
    }

    private fun addHighlight(emoji: String, text: String) {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, dp(7))
        }
        row.addView(TextView(context).apply {
            this.text = emoji
            textSize = 15f
        })
        row.addView(TextView(context).apply {
            this.text = text
            textSize = 12.5f
            setPadding(dp(10), 0, 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        })
        binding.highlights.addView(row)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
