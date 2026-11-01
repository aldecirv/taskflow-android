package com.taskflow.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.FragmentHomeBinding
import com.taskflow.app.databinding.ItemHabitMiniBinding
import com.taskflow.app.service.Pomodoro
import com.taskflow.app.service.PomodoroService
import com.taskflow.app.ui.adapters.TaskAdapter
import com.taskflow.app.ui.sheets.PlannerSheet
import com.taskflow.app.ui.sheets.TemplatesSheet
import com.taskflow.app.ui.sheets.WeeklyReviewSheet
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Tones
import java.time.LocalDate

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TaskAdapter

    private val refresh: () -> Unit = { if (isAdded && _binding != null) render() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = TaskAdapter(
            onToggle = { toggleTask(it) },
            onOpen = { openTask(it) },
            onFocus = { startFocus(it) },
            onDuplicate = { duplicate(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.todayList.layoutManager = LinearLayoutManager(requireContext())
        binding.todayList.adapter = adapter

        binding.btnNewTask.setOnClickListener {
            TaskEditorActivity.startNew(requireContext(), LocalDate.now())
        }
        binding.btnPlanner.setOnClickListener { openPlanner() }
        binding.btnTemplates.setOnClickListener { TemplatesSheet().show(childFragmentManager, "templates") }
        binding.btnWeekly.setOnClickListener { WeeklyReviewSheet().show(childFragmentManager, "weekly") }
        binding.btnAllTasks.setOnClickListener { (requireActivity() as MainActivity).selectTab(R.id.nav_tasks) }
        binding.btnAllHabits.setOnClickListener { (requireActivity() as MainActivity).selectTab(R.id.nav_habits) }
        binding.btnStats.setOnClickListener { StatsActivity.start(requireContext()) }
        binding.btnSettings.setOnClickListener { SettingsActivity.start(requireContext()) }
        binding.btnRedistribute.setOnClickListener { redistribute() }
        binding.btnFocus.setOnClickListener {
            currentFocusTask?.let { startFocus(it) }
        }
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

    private var currentFocusTask: Task? = null

    private fun render() {
        val settings = Store.settings
        val name = settings.userName.ifBlank { "Produtividade" }
        val firstName = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "você" }
        binding.greeting.text = "${Dates.greeting()}, $firstName"
        binding.avatar.text = initials(name)
        binding.levelLabel.text = "Nível ${Store.level()} · ${Store.levelTitle()}"
        binding.dateLabel.text = Dates.longDate()

        val today = LocalDate.now()
        val todayTasks = Store.todayTasks()
        val done = todayTasks.count { it.completed }
        val total = todayTasks.size
        val percent = if (total > 0) done * 100 / total else 0

        binding.heroSubtitle.text = when {
            total == 0 -> "Nenhuma tarefa para hoje ainda. Comece escolhendo um foco."
            percent == 100 -> "Todas as metas do dia foram concluídas. Excelente ritmo!"
            else -> "Você concluiu $done de $total tarefas programadas. Mantenha o ritmo!"
        }
        binding.dayRing.trackColor = Color.parseColor("#33FFFFFF")
        binding.dayRing.progressColor = Color.WHITE
        binding.dayRing.progress = percent / 100f
        binding.dayPercent.text = "$percent%"
        binding.dayCount.text = "$done ${if (done == 1) "concluída" else "concluídas"} · " +
            "${total - done} ${if (total - done == 1) "restante" else "restantes"}"
        val habitsDone = Store.habits.count { it.isDoneOn(today) }
        binding.heroHabits.text = "🔥 $habitsDone/${Store.habits.size} hábitos cumpridos"

        renderOverload(todayTasks)
        renderFocus(todayTasks)
        renderHabits(today)

        binding.todayTitle.text = "Tarefas de hoje (${todayTasks.size})"
        adapter.submitList(todayTasks)
        binding.emptyToday.isVisible = todayTasks.isEmpty()
        binding.todayList.isVisible = todayTasks.isNotEmpty()
    }

    private fun renderOverload(todayTasks: List<Task>) {
        val pending = todayTasks.filter { !it.completed }
        val minutes = pending.sumOf { it.estimateMinutes }
        val overloaded = pending.size >= 6 || minutes >= 330
        binding.overloadCard.isVisible = overloaded
        if (overloaded) {
            binding.overloadText.text = "${Dates.durationLabel(minutes)} de tarefas restantes para hoje."
        }
    }

    private fun renderFocus(todayTasks: List<Task>) {
        val candidate = todayTasks.filter { !it.completed }
            .sortedWith(compareBy({ priorityRank(it) }, { it.time ?: java.time.LocalTime.MAX }))
            .firstOrNull()
        currentFocusTask = candidate
        binding.focusCard.isVisible = candidate != null
        binding.focusTitle.isVisible = candidate != null
        if (candidate != null) {
            binding.focusTitle.text = candidate.title
            binding.focusPriority.text = candidate.priority.label.uppercase()
            binding.focusPriority.setTextColor(Color.parseColor(candidate.priority.colorHex))
            binding.focusMeta.text = listOfNotNull(
                candidate.category,
                candidate.timeLabel(),
                Dates.durationLabel(candidate.estimateMinutes)
            ).joinToString(" · ")
            binding.focusSuggestion.text = "Sessão de ${Store.settings.workMinutes} min recomendada"
        }
    }

    private fun priorityRank(task: Task): Int = when (task.priority) {
        com.taskflow.app.data.Priority.URGENTE -> 0
        com.taskflow.app.data.Priority.ALTA -> 1
        com.taskflow.app.data.Priority.MEDIA -> 2
        else -> 3
    }

    private fun renderHabits(today: LocalDate) {
        binding.habitList.removeAllViews()
        Store.habits.take(3).forEach { habit ->
            val item = ItemHabitMiniBinding.inflate(layoutInflater, binding.habitList, false)
            item.name.text = habit.name
            item.dot.backgroundTintList = ColorStateList.valueOf(
                runCatching { Color.parseColor(habit.colorHex) }.getOrDefault(Color.GRAY)
            )
            item.streak.text = "${habit.streak}d"
            val done = habit.isDoneOn(today)
            item.check.setImageResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked)
            item.check.imageTintList = ColorStateList.valueOf(
                Color.parseColor(if (done) "#10B981" else "#94A3B8")
            )
            item.name.paintFlags = if (done) {
                item.name.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                item.name.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            item.name.alpha = if (done) 0.6f else 1f
            item.root.setOnClickListener {
                val nowDone = Store.toggleHabit(habit.id)
                if (nowDone) Tones.complete(Store.settings.soundEffects)
                render()
            }
            binding.habitList.addView(item.root)
        }
        binding.habitList.isVisible = Store.habits.isNotEmpty()
    }

    private fun toggleTask(task: Task) {
        val completed = Store.toggleTask(task.id)
        if (completed) {
            Tones.complete(Store.settings.soundEffects)
            Toast.makeText(requireContext(), "+10 XP · ${task.title}", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    private fun openTask(task: Task) {
        TaskEditorActivity.startEdit(requireContext(), task.id)
    }

    private fun startFocus(task: Task) {
        PomodoroService.start(requireContext(), Pomodoro.Mode.WORK, Store.settings.workMinutes, task.id)
        (requireActivity() as MainActivity).selectTab(R.id.nav_focus)
    }

    private fun duplicate(task: Task) {
        Store.duplicateTask(task)
        Toast.makeText(requireContext(), "Tarefa duplicada", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(task: Task) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Excluir tarefa?")
            .setMessage(task.title)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ -> Store.deleteTask(task.id) }
            .show()
    }

    private fun redistribute() {
        val moved = Store.redistributeOverload()
        if (moved > 0) {
            Toast.makeText(
                requireContext(),
                "$moved tarefa(s) de menor urgência movidas para amanhã",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(requireContext(), "Nada para redistribuir", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    private fun openPlanner() {
        PlannerSheet().show(childFragmentManager, "planner")
    }

    private fun initials(name: String): String =
        name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "TF" }
}
