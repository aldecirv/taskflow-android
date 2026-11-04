package com.taskflow.app.data

import androidx.annotation.DrawableRes
import com.taskflow.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Priority(val label: String, val colorHex: String) {
    BAIXA("Baixa", "#3B82F6"),
    MEDIA("Média", "#F59E0B"),
    ALTA("Alta", "#F97316"),
    URGENTE("Urgente", "#EF4444");

    companion object {
        fun from(value: String?): Priority = entries.firstOrNull { it.name == value } ?: MEDIA
    }
}

enum class Recurrence(val label: String) {
    NONE("Nunca"),
    DAILY("Diariamente"),
    WEEKDAYS("Dias úteis"),
    WEEKLY("Semanalmente"),
    MONTHLY("Mensalmente"),
    YEARLY("Anualmente");

    companion object {
        fun from(value: String?): Recurrence = entries.firstOrNull { it.name == value } ?: NONE
    }
}

enum class HabitFrequency(val label: String) {
    DAILY("Todos os dias"),
    WEEKDAYS("Dias úteis (Seg a Sex)"),
    THREE_WEEK("3x por semana");

    companion object {
        fun from(value: String?): HabitFrequency = entries.firstOrNull { it.name == value } ?: DAILY
    }
}

enum class Accent(val key: String, val label: String, val colorHex: String, val overlay: Int) {
    INDIGO("indigo", "Índigo", "#4F46E5", R.style.ThemeOverlay_TaskFlow_Indigo),
    VIOLET("violet", "Violeta", "#7C3AED", R.style.ThemeOverlay_TaskFlow_Violet),
    EMERALD("emerald", "Esmeralda", "#059669", R.style.ThemeOverlay_TaskFlow_Emerald),
    AMBER("amber", "Âmbar", "#F59E0B", R.style.ThemeOverlay_TaskFlow_Amber),
    ROSE("rose", "Rosa", "#F43F5E", R.style.ThemeOverlay_TaskFlow_Rose),
    CYAN("cyan", "Ciano", "#06B6D4", R.style.ThemeOverlay_TaskFlow_Cyan);

    companion object {
        fun from(value: String?): Accent = entries.firstOrNull { it.key == value } ?: INDIGO
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("Automático"),
    LIGHT("Claro"),
    DARK("Escuro");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class SubTask(
    val id: String,
    var title: String,
    var done: Boolean = false
)

data class Task(
    val id: String,
    var title: String,
    var notes: String = "",
    var category: String = "Trabalho",
    val tags: MutableList<String> = mutableListOf(),
    var priority: Priority = Priority.MEDIA,
    var date: LocalDate = LocalDate.now(),
    var time: LocalTime? = null,
    var estimateMinutes: Int = 30,
    var completed: Boolean = false,
    var completedAt: LocalDateTime? = null,
    val subtasks: MutableList<SubTask> = mutableListOf(),
    var recurrence: Recurrence = Recurrence.NONE,
    val reminders: MutableList<Int> = mutableListOf(15),
    var pomodoroCount: Int = 0,
    var targetPomodoros: Int = 1,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    var alertEnabled: Boolean = false,
    var alertSound: String = "notification",
    var trackedSeconds: Int = 0
) {
    val subtaskProgress: Int
        get() = if (subtasks.isEmpty()) 0 else (subtasks.count { it.done } * 100) / subtasks.size

    fun isOverdue(now: LocalDate = LocalDate.now()): Boolean =
        !completed && date.isBefore(now)

    fun timeLabel(): String = time?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "Dia todo"
}

data class Habit(
    val id: String,
    var name: String,
    var category: String = "Saúde & Bem-estar",
    var colorHex: String = "#06B6D4",
    var frequency: HabitFrequency = HabitFrequency.DAILY,
    var streak: Int = 0,
    var bestStreak: Int = 0,
    val completedDates: MutableSet<LocalDate> = mutableSetOf(),
    val createdAt: LocalDate = LocalDate.now(),
    var description: String = "",
    var goal: String = "",
    var nextAction: String = "",
    var scheduledTime: String = "08:00",
    var weekdays: MutableSet<Int> = mutableSetOf(1, 2, 3, 4, 5, 6, 7),
    var paused: Boolean = false,
    var snoozeUntil: LocalDateTime? = null
) {
    fun isDoneOn(date: LocalDate): Boolean = completedDates.contains(date)

    fun isSnoozed(now: LocalDateTime = LocalDateTime.now()): Boolean =
        snoozeUntil?.isAfter(now) == true

    fun scheduledOn(date: LocalDate): Boolean {
        if (paused) return false
        val allowed = when (frequency) {
            HabitFrequency.DAILY -> true
            HabitFrequency.WEEKDAYS -> date.dayOfWeek.value <= 5
            HabitFrequency.THREE_WEEK -> true
        }
        return allowed && (weekdays.isEmpty() || weekdays.contains(date.dayOfWeek.value))
    }

    fun actionLabel(): String = nextAction.ifBlank { name }

    fun lastSevenDays(today: LocalDate = LocalDate.now()): List<LocalDate> =
        (6 downTo 0).map { today.minusDays(it.toLong()) }
}

data class Category(
    val name: String,
    val colorHex: String,
    @DrawableRes val icon: Int
)

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    @DrawableRes val icon: Int,
    val unlocked: Boolean
)

data class UserSettings(
    var userName: String = "",
    var accent: Accent = Accent.INDIGO,
    var themeMode: ThemeMode = ThemeMode.SYSTEM,
    var soundEffects: Boolean = true,
    var workMinutes: Int = 25,
    var shortBreakMinutes: Int = 5,
    var longBreakMinutes: Int = 15,
    var dailyGoal: Int = 5,
    var notificationsEnabled: Boolean = true,
    var hasCompletedOnboarding: Boolean = false,
    var priceIsUnit: Boolean = false,
    var voicePreferred: Boolean = false,
    var dictationLanguage: String = "pt-BR",
    var weightKg: Double = 70.0,
    var voiceKmEnabled: Boolean = true
)

data class TemplateTask(
    val title: String,
    val dayOffset: Int,
    val time: String,
    val priority: Priority,
    val estimateMinutes: Int,
    val subtasks: List<String>
)

data class PlanTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val badge: String,
    val emoji: String,
    val tasks: List<TemplateTask>
)

object Categories {
    val all = listOf(
        Category("Trabalho", "#3B82F6", R.drawable.ic_work),
        Category("Estudo", "#8B5CF6", R.drawable.ic_menu_book),
        Category("Saúde & Bem-estar", "#10B981", R.drawable.ic_favorite),
        Category("Pessoal", "#F59E0B", R.drawable.ic_person),
        Category("Finanças", "#06B6D4", R.drawable.ic_account_balance_wallet)
    )

    fun byName(name: String): Category = all.firstOrNull { it.name == name } ?: all.first()
    fun color(name: String): String = byName(name).colorHex
    @DrawableRes fun icon(name: String): Int = byName(name).icon
}
