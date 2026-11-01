package com.taskflow.app.ui.sheets

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
import com.taskflow.app.R
import com.taskflow.app.data.Priority
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.SheetPlannerBinding
import com.taskflow.app.util.Dates
import java.time.LocalDate
import java.time.LocalTime

class PlannerSheet : BottomSheetDialogFragment() {

    private data class Proposal(val task: Task, val date: LocalDate, val time: LocalTime)

    private var _binding: SheetPlannerBinding? = null
    private val binding get() = _binding!!
    private val proposals = mutableListOf<Proposal>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildPlan()
        renderPlan()
        binding.btnApply.setOnClickListener { applyPlan() }
    }

    private fun buildPlan() {
        proposals.clear()
        val today = LocalDate.now()
        val pending = Store.pendingTasks()
            .filter { it.time == null && !it.date.isAfter(today.plusDays(6L)) }
            .filter { !it.date.isBefore(today) }

        pending.groupBy { it.date }.toSortedMap().forEach { (date, tasks) ->
            var cursor = LocalTime.of(9, 0)
            if (date == today) {
                val now = LocalTime.now()
                val earliest = now.plusMinutes((15 - (now.minute % 15)).toLong())
                if (earliest.isAfter(cursor)) cursor = earliest
            }
            tasks.sortedWith(compareBy({ rank(it.priority) }, { it.estimateMinutes })).forEach { task ->
                if (cursor in LocalTime.of(12, 0)..LocalTime.of(12, 59)) cursor = LocalTime.of(13, 0)
                if (cursor.isAfter(LocalTime.of(19, 0))) return@forEach
                proposals.add(Proposal(task, date, cursor))
                cursor = cursor.plusMinutes(task.estimateMinutes.toLong() + 10)
            }
        }
    }

    private fun rank(priority: Priority) = when (priority) {
        Priority.URGENTE -> 0
        Priority.ALTA -> 1
        Priority.MEDIA -> 2
        Priority.BAIXA -> 3
    }

    private fun renderPlan() {
        binding.planList.removeAllViews()
        binding.emptyPlan.visibility = if (proposals.isEmpty()) View.VISIBLE else View.GONE
        binding.btnApply.isEnabled = proposals.isNotEmpty()
        binding.btnApply.alpha = if (proposals.isEmpty()) 0.5f else 1f

        var lastDate: LocalDate? = null
        proposals.forEach { proposal ->
            if (proposal.date != lastDate) {
                lastDate = proposal.date
                binding.planList.addView(dateHeader(proposal.date))
            }
            binding.planList.addView(proposalRow(proposal))
        }
    }

    private fun dateHeader(date: LocalDate): View = TextView(requireContext()).apply {
        text = if (date == LocalDate.now()) "HOJE · ${Dates.shortDate(date)}" else "${Dates.relative(date).uppercase()} · ${Dates.shortDate(date)}"
        textSize = 10f
        letterSpacing = 0.08f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun proposalRow(proposal: Proposal): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(context).apply {
            text = Dates.timeLabel(proposal.time)
            textSize = 12f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.brand_indigo))
            width = dp(52)
        })
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(context).apply {
            text = proposal.task.title
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        info.addView(TextView(context).apply {
            text = "${proposal.task.category} · ${Dates.durationLabel(proposal.task.estimateMinutes)}"
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        row.addView(info)
        return row
    }

    private fun applyPlan() {
        proposals.forEach { proposal ->
            proposal.task.time = proposal.time
            Store.saveTask(proposal.task)
        }
        Store.award(50)
        Store.markPlannerUsed()
        Toast.makeText(requireContext(), "Plano aplicado · +50 XP", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
