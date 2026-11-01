package com.taskflow.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.FragmentCalendarBinding
import com.taskflow.app.service.Pomodoro
import com.taskflow.app.service.PomodoroService
import com.taskflow.app.ui.adapters.TaskAdapter
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Tones
import java.time.LocalDate
import java.time.YearMonth

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TaskAdapter

    private val refresh: () -> Unit = { if (isAdded && _binding != null) render() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = TaskAdapter(
            onToggle = { toggle(it) },
            onOpen = { TaskEditorActivity.startEdit(requireContext(), it.id) },
            onFocus = { focus(it) },
            onDuplicate = { Store.duplicateTask(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.dayList.layoutManager = LinearLayoutManager(requireContext())
        binding.dayList.adapter = adapter

        binding.calendar.onDateSelected = { render() }
        binding.btnPrevMonth.setOnClickListener {
            binding.calendar.showMonth(binding.calendar.month.minusMonths(1))
            binding.monthLabel.text = binding.calendar.monthLabel()
        }
        binding.btnNextMonth.setOnClickListener {
            binding.calendar.showMonth(binding.calendar.month.plusMonths(1))
            binding.monthLabel.text = binding.calendar.monthLabel()
        }
        binding.btnToday.setOnClickListener {
            binding.calendar.select(LocalDate.now())
            binding.monthLabel.text = binding.calendar.monthLabel()
            render()
        }
        binding.btnAdd.setOnClickListener {
            TaskEditorActivity.startNew(requireContext(), binding.calendar.selectedDate)
        }
        Store.addListener(refresh)
    }

    override fun onResume() {
        super.onResume()
        binding.monthLabel.text = binding.calendar.monthLabel()
        render()
    }

    override fun onDestroyView() {
        Store.removeListener(refresh)
        _binding = null
        super.onDestroyView()
    }

    private fun render() {
        val selected = binding.calendar.selectedDate
        binding.monthLabel.text = binding.calendar.monthLabel()
        binding.calendar.markedDates = Store.tasks.map { it.date }.toSet()

        val tasks = Store.tasksOn(selected)
        adapter.submitList(tasks)
        binding.emptyState.isVisible = tasks.isEmpty()
        binding.dayList.isVisible = tasks.isNotEmpty()

        binding.selectedLabel.text = if (selected == LocalDate.now()) {
            "Hoje, ${Dates.shortDate(selected)}"
        } else {
            "${Dates.weekdayLong(selected)}, ${Dates.shortDate(selected)}"
        }
    }

    private fun toggle(task: Task) {
        if (Store.toggleTask(task.id)) Tones.complete(Store.settings.soundEffects)
    }

    private fun focus(task: Task) {
        PomodoroService.start(requireContext(), Pomodoro.Mode.WORK, Store.settings.workMinutes, task.id)
        (requireActivity() as MainActivity).selectTab(R.id.nav_focus)
    }

    private fun confirmDelete(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir tarefa?")
            .setMessage(task.title)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ -> Store.deleteTask(task.id) }
            .show()
    }
}
