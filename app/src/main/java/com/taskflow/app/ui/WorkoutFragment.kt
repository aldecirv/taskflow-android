package com.taskflow.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.taskflow.app.data.Store
import com.taskflow.app.data.WorkoutMode
import com.taskflow.app.databinding.FragmentWorkoutBinding
import com.taskflow.app.service.WorkoutController
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.taskflow.app.data.StepStore
import com.taskflow.app.service.StepCounterService
import java.text.NumberFormat
import com.taskflow.app.ui.adapters.WorkoutAdapter
import java.util.Locale

class WorkoutFragment : Fragment() {

    private var autoStepTried = false

    private val stepPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACTIVITY_RECOGNITION] ?: true
        if (ok) startSteps()
        else refreshSteps()
    }


    private var _binding: FragmentWorkoutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.workoutList.layoutManager = LinearLayoutManager(requireContext())
        binding.btnStartWalk.setOnClickListener { start(WorkoutMode.WALK) }
        binding.btnStartRun.setOnClickListener { start(WorkoutMode.RUN) }
        binding.btnStepToggle.setOnClickListener {
            val ctx = requireContext()
            if (StepCounterService.isRunning(ctx)) {
                StepStore.setEnabled(ctx, false)
                StepCounterService.stop(ctx)
                binding.root.postDelayed({ refreshSteps() }, 400)
            } else if (!hasStepPermission()) {
                val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                stepPermissionLauncher.launch(permissions.toTypedArray())
            } else {
                startSteps()
            }
        }
        binding.stepGoalButton.setOnClickListener { pickStepGoal() }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(requireContext(), WorkoutHistoryActivity::class.java))
        }
        binding.btnContinue.setOnClickListener { start(WorkoutMode.WALK) }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun refresh() {
        if (_binding == null) return
        val workouts = Store.workouts
        binding.workoutList.adapter = WorkoutAdapter(workouts)
        binding.emptyState.visibility = if (workouts.isEmpty()) View.VISIBLE else View.GONE
        binding.workoutList.visibility = if (workouts.isEmpty()) View.GONE else View.VISIBLE
        val (count, km, seconds) = Store.workoutSummary()
        binding.totalWorkouts.text = "$count ${if (count == 1) "treino" else "treinos"}"
        binding.totalDistance.text = String.format(Locale("pt", "BR"), "%.1f km", km)
        binding.totalTime.text = if (seconds >= 3600) "${seconds / 3600}h" else "${seconds / 60}min"
        binding.historyCount.text = "$count ${if (count == 1) "treino" else "treinos"}"
        val active = WorkoutController.state.value
        binding.btnContinue.visibility = if (active.active) View.VISIBLE else View.GONE
        binding.btnContinue.text = if (active.paused) "Retomar treino em andamento" else "Continuar treino em andamento"
        refreshSteps()
    }

    private fun hasStepPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

    private fun startSteps() {
        val ctx = requireContext()
        StepStore.setEnabled(ctx, true)
        StepCounterService.start(ctx)
        binding.root.postDelayed({ refreshSteps() }, 700)
    }

    private fun refreshSteps() {
        if (_binding == null) return
        val ctx = requireContext()
        val steps = StepStore.steps(ctx)
        val goal = StepStore.goal(ctx)
        val weight = Store.settings.weightKg
        val running = StepCounterService.isRunning(ctx)
        val number = NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR"))

        binding.stepCount.text = number.format(steps)
        binding.stepGoalButton.text = "Meta " + number.format(goal)
        binding.stepDistance.text = String.format(java.util.Locale("pt", "BR"), "%.2f km", StepStore.distanceKm(steps))
        binding.stepCalories.text = StepStore.calories(steps, weight).toString() + " kcal"
        binding.stepProgress.progress = if (goal > 0) (steps * 100 / goal).coerceIn(0, 100) else 0
        binding.stepStatus.text = when {
            running -> if (steps >= goal) "Meta batida! Contagem ativa" else "Contagem ativa em segundo plano"
            !hasStepPermission() -> "Permissão de atividade física necessária"
            else -> "Contagem parada"
        }
        binding.btnStepToggle.text = if (running) "Parar" else "Ativar"

        if (!running && !autoStepTried && StepStore.enabled(ctx) && hasStepPermission()) {
            autoStepTried = true
            StepCounterService.start(ctx)
            binding.root.postDelayed({ refreshSteps() }, 1200)
        }
    }

    private fun pickStepGoal() {
        val ctx = requireContext()
        val options = intArrayOf(5000, 8000, 10000, 12000, 15000)
        val number = NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR"))
        val labels = options.map { number.format(it) + " passos" }.toTypedArray()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Meta diária de passos")
            .setItems(labels) { _, which ->
                StepStore.setGoal(ctx, options[which])
                refreshSteps()
            }
            .show()
    }

    private fun start(mode: WorkoutMode) {
        startActivity(Intent(requireContext(), WorkoutActivity::class.java).putExtra("mode", mode.name))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
