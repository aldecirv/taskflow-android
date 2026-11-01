package com.taskflow.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.FragmentTasksBinding
import com.taskflow.app.service.Pomodoro
import com.taskflow.app.service.PomodoroService
import com.taskflow.app.ui.adapters.TaskAdapter
import com.taskflow.app.util.Tones
import java.time.LocalDate

class TasksFragment : Fragment() {

    private enum class Filter { ALL, TODAY, OVERDUE, DONE }

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TaskAdapter
    private var filter = Filter.ALL
    private var query = ""

    private val refresh: () -> Unit = { if (isAdded && _binding != null) render() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = TaskAdapter(
            onToggle = { toggle(it) },
            onOpen = { TaskEditorActivity.startEdit(requireContext(), it.id) },
            onFocus = { startFocus(it) },
            onDuplicate = { Store.duplicateTask(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.taskList.layoutManager = LinearLayoutManager(requireContext())
        binding.taskList.adapter = adapter
        binding.fab.setOnClickListener { TaskEditorActivity.startNew(requireContext(), LocalDate.now()) }

        binding.btnSearch.setOnClickListener { toggleSearch() }
        binding.btnClearSearch.setOnClickListener {
            binding.searchInput.setText("")
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                render()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.filters.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chipToday -> Filter.TODAY
                R.id.chipOverdue -> Filter.OVERDUE
                R.id.chipDone -> Filter.DONE
                else -> Filter.ALL
            }
            render()
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

    private fun render() {
        val today = LocalDate.now()
        var list = when (filter) {
            Filter.ALL -> Store.tasks.toList()
            Filter.TODAY -> Store.tasks.filter { it.date == today }
            Filter.OVERDUE -> Store.tasks.filter { it.isOverdue(today) }
            Filter.DONE -> Store.tasks.filter { it.completed }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                    it.notes.lowercase().contains(q) ||
                    it.category.lowercase().contains(q) ||
                    it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }
        list = if (filter == Filter.DONE) {
            list.sortedByDescending { it.completedAt }
        } else {
            list.sortedWith(compareBy({ it.completed }, { it.date }, { it.time ?: java.time.LocalTime.MAX }))
        }

        adapter.submitList(list)
        binding.emptyState.isVisible = list.isEmpty()
        binding.taskList.isVisible = list.isNotEmpty()

        val pending = Store.tasks.count { !it.completed }
        val done = Store.tasks.count { it.completed }
        binding.subtitle.text = "$pending ${if (pending == 1) "pendente" else "pendentes"} · " +
            "$done ${if (done == 1) "concluída" else "concluídas"}"
    }

    private fun toggleSearch() {
        val box = binding.searchBox
        if (box.isVisible) {
            box.isVisible = false
            binding.searchInput.setText("")
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        } else {
            box.isVisible = true
            binding.searchInput.requestFocus()
            binding.searchInput.post {
                val imm = requireContext().getSystemService(InputMethodManager::class.java)
                imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun toggle(task: Task) {
        val completed = Store.toggleTask(task.id)
        if (completed) {
            Tones.complete(Store.settings.soundEffects)
            android.widget.Toast.makeText(requireContext(), "+10 XP", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun startFocus(task: Task) {
        PomodoroService.start(requireContext(), Pomodoro.Mode.WORK, Store.settings.workMinutes, task.id)
        (requireActivity() as com.taskflow.app.MainActivity).selectTab(R.id.nav_focus)
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
