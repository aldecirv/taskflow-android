package com.taskflow.app.data

import java.time.LocalDate
import java.time.LocalTime

enum class PlanSource { GEMINI, LOCAL }

data class PlannedTask(
    val title: String,
    val category: String = "Pessoal",
    val priority: Priority = Priority.MEDIA,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime? = null,
    val estimateMinutes: Int = 30,
    val notes: String = "",
    val subtasks: List<String> = emptyList()
)

data class AiPlan(
    val summary: String,
    val tasks: List<PlannedTask>,
    val source: PlanSource
)
