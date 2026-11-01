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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taskflow.app.R
import com.taskflow.app.data.AiConfig
import com.taskflow.app.data.AiPlan
import com.taskflow.app.data.Categories
import com.taskflow.app.data.LocalPlanner
import com.taskflow.app.data.PlanSource
import com.taskflow.app.data.PlannedTask
import com.taskflow.app.data.Store
import com.taskflow.app.data.SubTask
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.SheetAiPlannerBinding
import com.taskflow.app.net.GeminiClient
import com.taskflow.app.ui.SettingsActivity
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Tones
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

class AiPlannerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAiPlannerBinding? = null
    private val binding get() = _binding!!
    private var plan: AiPlan? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAiPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val enabled = AiConfig.isEnabled(requireContext())
        binding.aiStatus.text = if (enabled) "IA Gemini conectada · ${AiConfig.model(requireContext())}"
        else "Modo local · sem chave do Gemini"
        binding.btnConfigure.isVisible = !enabled

        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnApply.setOnClickListener { applyPlan() }
        binding.btnReorganize.setOnClickListener {
            dismiss()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                PlannerSheet().show(parentFragmentManager, "planner")
            }
        }
        binding.btnConfigure.setOnClickListener {
            dismiss()
            SettingsActivity.start(requireContext())
        }
    }

    private fun generate() {
        val prompt = binding.promptEdit.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) {
            binding.promptEdit.error = "Descreva o que você quer planejar"
            return
        }
        binding.promptEdit.error = null
        binding.errorText.isVisible = false
        binding.resultContainer.isVisible = false
        binding.progress.isVisible = true
        binding.btnGenerate.isEnabled = false
        binding.btnGenerate.text = "Gerando plano..."
        plan = null

        val today = LocalDate.now()
        val existing = Store.pendingTasks().take(12).map {
            "${it.date} ${it.timeLabel()} ${it.title}"
        }

        if (!AiConfig.isEnabled(requireContext())) {
            showPlan(LocalPlanner.plan(prompt, today), null)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = GeminiClient.planWeek(requireContext(), prompt, today, existing)
            result.onSuccess { showPlan(it, null) }
                .onFailure { error ->
                    val fallback = LocalPlanner.plan(prompt, today)
                    showPlan(fallback, error.message ?: "Falha ao falar com o Gemini")
                }
        }
    }

    private fun showPlan(value: AiPlan, fallbackReason: String?) {
        if (_binding == null) return
        plan = value
        binding.progress.isVisible = false
        binding.btnGenerate.isEnabled = true
        binding.btnGenerate.text = "Gerar outro plano"
        binding.resultContainer.isVisible = true
        binding.planSummary.text = value.summary

        binding.planSource.text = when {
            fallbackReason != null -> "Plano local (Gemini indisponível: $fallbackReason)"
            value.source == PlanSource.GEMINI -> "Gerado pelo Gemini · revise antes de aplicar"
            else -> "Gerado localmente, sem chave de API · revise antes de aplicar"
        }
        binding.planSource.setTextColor(
            if (fallbackReason != null) ContextCompat.getColor(requireContext(), R.color.rose)
            else ContextCompat.getColor(requireContext(), R.color.text_secondary)
        )

        binding.planList.removeAllViews()
        value.tasks.forEach { binding.planList.addView(taskRow(it)) }
    }

    private fun taskRow(task: PlannedTask): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val time = TextView(context).apply {
            text = if (task.time != null) Dates.timeLabel(task.time) else Dates.relative(task.date)
            textSize = 11.5f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.brand_indigo))
            width = dp(56)
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(context).apply {
            text = task.title
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            maxLines = 2
        })
        texts.addView(TextView(context).apply {
            text = "${Dates.relative(task.date)} · ${task.category} · ${Dates.durationLabel(task.estimateMinutes)}"
            textSize = 10.5f
            setTextColor(Color.parseColor(Categories.color(task.category)))
        })
        row.addView(time)
        row.addView(texts)
        return row
    }

    private fun applyPlan() {
        val value = plan ?: return
        value.tasks.forEach { item ->
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = item.title,
                notes = item.notes.ifBlank { "Criado pelo planejador do TaskFlow" },
                category = item.category,
                priority = item.priority,
                date = item.date,
                time = item.time,
                estimateMinutes = item.estimateMinutes,
                subtasks = item.subtasks.map { SubTask(UUID.randomUUID().toString(), it, false) }.toMutableList(),
                reminders = mutableListOf(15),
                targetPomodoros = ceil(item.estimateMinutes / 25.0).toInt().coerceAtLeast(1)
            )
            Store.saveTask(task)
        }
        Store.award(50)
        Store.markPlannerUsed()
        Tones.levelUp(Store.settings.soundEffects)
        Toast.makeText(requireContext(), "${value.tasks.size} tarefas criadas · +50 XP", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
