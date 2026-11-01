package com.taskflow.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.FragmentFocusBinding
import com.taskflow.app.service.Pomodoro
import com.taskflow.app.service.PomodoroService
import com.taskflow.app.ui.sheets.FocusTaskSheet

class FocusFragment : Fragment() {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!

    private val timerListener: () -> Unit = { if (isAdded && _binding != null) renderTimer() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFocusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.modeWork.setOnClickListener { selectMode(Pomodoro.Mode.WORK) }
        binding.modeShort.setOnClickListener { selectMode(Pomodoro.Mode.SHORT_BREAK) }
        binding.modeLong.setOnClickListener { selectMode(Pomodoro.Mode.LONG_BREAK) }

        binding.btnPlay.setOnClickListener { toggleTimer() }
        binding.btnReset.setOnClickListener { resetTimer() }
        binding.btnSkip.setOnClickListener { skipStage() }
        binding.btnPickTask.setOnClickListener {
            FocusTaskSheet().show(childFragmentManager, "focus_task")
        }
        binding.btnFocusSettings.setOnClickListener { SettingsActivity.start(requireContext()) }

        Pomodoro.addListener(timerListener)
        if (Pomodoro.totalSeconds == 25 * 60 && Pomodoro.mode == Pomodoro.Mode.WORK) {
            Pomodoro.prepare(Pomodoro.Mode.WORK, Store.settings.workMinutes)
        }
        renderAll()
    }

    override fun onResume() {
        super.onResume()
        renderAll()
    }

    override fun onDestroyView() {
        Pomodoro.removeListener(timerListener)
        _binding = null
        super.onDestroyView()
    }

    private fun renderAll() {
        renderTimer()
        renderModes()
        renderTask()
        renderSessionInfo()
    }

    private fun renderTimer() {
        val binding = _binding ?: return
        binding.timeText.text = Pomodoro.timeText()
        binding.modeLabel.text = Pomodoro.mode.label

        val primary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        binding.timerRing.trackColor = ContextCompat.getColor(requireContext(), R.color.border)
        binding.timerRing.progressColor = primary
        binding.timerRing.progress = Pomodoro.progress()

        if (Pomodoro.running) {
            binding.btnPlay.text = "Pausar"
            binding.btnPlay.setIconResource(R.drawable.ic_pause)
            binding.btnPlay.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.amber)
            )
        } else {
            val resuming = Pomodoro.remainingSeconds in 1 until Pomodoro.totalSeconds
            binding.btnPlay.text = if (resuming) "Continuar" else "Começar ${Pomodoro.mode.shortLabel.lowercase()}"
            binding.btnPlay.setIconResource(R.drawable.ic_play_arrow)
            binding.btnPlay.backgroundTintList = ColorStateList.valueOf(primary)
        }

        binding.modeWork.text = "Foco"
        binding.modeShort.text = "Pausa curta"
        binding.modeLong.text = "Pausa longa"
    }

    private fun renderModes() {
        val primary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val selected = Color.WHITE
        val idle = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        listOf(
            binding.modeWork to Pomodoro.Mode.WORK,
            binding.modeShort to Pomodoro.Mode.SHORT_BREAK,
            binding.modeLong to Pomodoro.Mode.LONG_BREAK
        ).forEach { (view, mode) ->
            styleMode(view, mode == Pomodoro.mode, primary, selected, idle)
        }
    }

    private fun styleMode(view: TextView, active: Boolean, primary: Int, activeText: Int, idleText: Int) {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            if (active) {
                setColor(primary)
            } else {
                setColor(Color.TRANSPARENT)
            }
        }
        view.background = bg
        view.setTextColor(if (active) activeText else idleText)
        view.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun renderTask() {
        val task = Pomodoro.taskId?.let { id -> Store.tasks.firstOrNull { it.id == id } }
        if (task != null) {
            binding.taskTitle.text = task.title
            binding.taskMeta.text =
                "${task.category} · ${task.pomodoroCount}/${task.targetPomodoros} pomodoros"
        } else {
            binding.taskTitle.text = "Nenhuma tarefa selecionada"
            binding.taskMeta.text = "Escolha uma tarefa para acompanhar o progresso"
        }
    }

    private fun renderSessionInfo() {
        val minutes = Store.totalPomodoros * Store.settings.workMinutes
        binding.sessionInfo.text =
            "${Store.totalPomodoros} sessões · ${formatMinutes(minutes)} de foco acumulado"
    }

    private fun formatMinutes(minutes: Int): String =
        if (minutes < 60) "${minutes}min" else "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"

    private fun selectMode(mode: Pomodoro.Mode) {
        if (Pomodoro.running) {
            PomodoroService.stop(requireContext())
        }
        val minutes = when (mode) {
            Pomodoro.Mode.WORK -> Store.settings.workMinutes
            Pomodoro.Mode.SHORT_BREAK -> Store.settings.shortBreakMinutes
            Pomodoro.Mode.LONG_BREAK -> Store.settings.longBreakMinutes
        }
        Pomodoro.prepare(mode, minutes)
        renderAll()
    }

    private fun toggleTimer() {
        if (Pomodoro.running) {
            PomodoroService.toggle(requireContext())
            return
        }
        val resuming = Pomodoro.remainingSeconds in 1 until Pomodoro.totalSeconds
        if (resuming) {
            PomodoroService.toggle(requireContext())
        } else {
            val minutes = when (Pomodoro.mode) {
                Pomodoro.Mode.WORK -> Store.settings.workMinutes
                Pomodoro.Mode.SHORT_BREAK -> Store.settings.shortBreakMinutes
                Pomodoro.Mode.LONG_BREAK -> Store.settings.longBreakMinutes
            }
            PomodoroService.start(requireContext(), Pomodoro.mode, minutes, Pomodoro.taskId)
        }
    }

    private fun resetTimer() {
        PomodoroService.stop(requireContext())
        val minutes = when (Pomodoro.mode) {
            Pomodoro.Mode.WORK -> Store.settings.workMinutes
            Pomodoro.Mode.SHORT_BREAK -> Store.settings.shortBreakMinutes
            Pomodoro.Mode.LONG_BREAK -> Store.settings.longBreakMinutes
        }
        Pomodoro.prepare(Pomodoro.mode, minutes)
        renderAll()
    }

    private fun skipStage() {
        PomodoroService.stop(requireContext())
        val next = if (Pomodoro.mode == Pomodoro.Mode.WORK) Pomodoro.Mode.SHORT_BREAK else Pomodoro.Mode.WORK
        val minutes = if (next == Pomodoro.Mode.WORK) Store.settings.workMinutes else Store.settings.shortBreakMinutes
        Pomodoro.prepare(next, minutes)
        renderAll()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
