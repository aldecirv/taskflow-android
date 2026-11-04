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
import com.taskflow.app.ui.adapters.WorkoutAdapter
import java.util.Locale

class WorkoutFragment : Fragment() {

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
    }

    private fun start(mode: WorkoutMode) {
        startActivity(Intent(requireContext(), WorkoutActivity::class.java).putExtra("mode", mode.name))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
