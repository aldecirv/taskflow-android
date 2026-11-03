package com.taskflow.app.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class WorkoutMode(val label: String, val emoji: String, val met: Double) {
    WALK("Caminhada", "🚶", 3.8),
    RUN("Corrida", "🏃", 8.3);

    companion object {
        fun from(value: String?): WorkoutMode = entries.firstOrNull { it.name == value } ?: WALK
    }
}

data class WorkoutPoint(
    val latitude: Double,
    val longitude: Double,
    val elapsedSeconds: Int = 0
)

data class Workout(
    val id: String,
    val mode: WorkoutMode,
    val startedAt: LocalDateTime,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val steps: Int,
    val calories: Int,
    val avgPaceSecondsPerKm: Int,
    val route: List<WorkoutPoint>,
    val weightKg: Double = 70.0
) {
    val distanceKm: Double get() = distanceMeters / 1000.0

    fun distanceLabel(): String = String.format(Locale("pt", "BR"), "%.2f km", distanceKm)

    fun durationLabel(): String {
        val h = durationSeconds / 3600
        val m = (durationSeconds % 3600) / 60
        val s = durationSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun paceLabel(): String {
        if (avgPaceSecondsPerKm <= 0) return "--:-- /km"
        return String.format(
            Locale.US,
            "%d:%02d /km",
            avgPaceSecondsPerKm / 60,
            avgPaceSecondsPerKm % 60
        )
    }

    fun dateLabel(): String =
        DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale("pt", "BR")).format(startedAt)
}
