package com.taskflow.app.service

import com.taskflow.app.data.WorkoutMode
import com.taskflow.app.data.WorkoutPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WorkoutState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val mode: WorkoutMode = WorkoutMode.WALK,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Int = 0,
    val steps: Int = 0,
    val calories: Int = 0,
    val paceSecondsPerKm: Int = 0,
    val points: List<WorkoutPoint> = emptyList(),
    val savedWorkoutId: String? = null,
    val error: String? = null
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
}

object WorkoutController {
    private val _state = MutableStateFlow(WorkoutState())
    val state: StateFlow<WorkoutState> = _state

    fun update(block: (WorkoutState) -> WorkoutState) {
        _state.value = block(_state.value)
    }

    fun set(value: WorkoutState) {
        _state.value = value
    }

    fun reset() {
        _state.value = WorkoutState()
    }
}
