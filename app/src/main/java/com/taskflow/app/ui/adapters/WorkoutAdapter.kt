package com.taskflow.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.taskflow.app.data.Workout
import com.taskflow.app.databinding.ItemWorkoutBinding
import com.taskflow.app.ui.WorkoutDetailActivity
import java.util.Locale

class WorkoutAdapter(private val items: List<Workout>) : RecyclerView.Adapter<WorkoutAdapter.Holder>() {

    inner class Holder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(workout: Workout) {
            binding.title.text = "${workout.mode.emoji} ${workout.mode.label} · ${workout.distanceLabel()}"
            binding.date.text = workout.dateLabel()
            binding.meta.text = String.format(
                Locale("pt", "BR"),
                "%s · %s · %d passos · %d kcal",
                workout.durationLabel(),
                workout.paceLabel(),
                workout.steps,
                workout.calories
            )
            binding.root.setOnClickListener {
                it.context.startActivity(WorkoutDetailActivity.intent(it.context, workout.id))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
