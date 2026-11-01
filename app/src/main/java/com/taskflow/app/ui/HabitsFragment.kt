package com.taskflow.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.taskflow.app.R
import com.taskflow.app.data.Habit
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.FragmentHabitsBinding
import com.taskflow.app.ui.adapters.HabitAdapter
import com.taskflow.app.ui.sheets.HabitEditorSheet
import com.taskflow.app.util.Tones
import java.time.LocalDate

class HabitsFragment : Fragment() {

    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HabitAdapter

    private val refresh: () -> Unit = { if (isAdded && _binding != null) render() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HabitAdapter(
            onToggle = { habit ->
                if (Store.toggleHabit(habit.id)) {
                    Tones.complete(Store.settings.soundEffects)
                }
            },
            onLongPress = { habit -> confirmDelete(habit) }
        )
        binding.habitList.layoutManager = LinearLayoutManager(requireContext())
        binding.habitList.adapter = adapter

        binding.btnAddHabit.setOnClickListener { showEditor() }
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

    private fun render() {
        val today = LocalDate.now()
        val habits = Store.habits
        adapter.submitList(habits.toList())
        binding.emptyState.isVisible = habits.isEmpty()
        binding.habitList.isVisible = habits.isNotEmpty()

        val doneToday = habits.count { it.isDoneOn(today) }
        binding.subtitle.text = "$doneToday de ${habits.size} concluídos hoje"
        binding.statToday.text = "$doneToday/${habits.size}"
        binding.statStreak.text = Store.bestStreak().toString()

        var scheduled = 0
        var completed = 0
        habits.forEach { habit ->
            (0..6).forEach { offset ->
                val date = today.minusDays(offset.toLong())
                if (habit.scheduledOn(date)) {
                    scheduled++
                    if (habit.isDoneOn(date)) completed++
                }
            }
        }
        val adherence = if (scheduled == 0) 0 else completed * 100 / scheduled
        binding.statWeek.text = "$adherence%"
    }

    private fun showEditor() {
        HabitEditorSheet().show(childFragmentManager, "habit_editor")
    }

    private fun confirmDelete(habit: Habit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir hábito?")
            .setMessage(habit.name)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ -> Store.deleteHabit(habit.id) }
            .show()
    }
}
