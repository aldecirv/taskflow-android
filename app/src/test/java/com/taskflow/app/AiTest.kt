package com.taskflow.app

import com.taskflow.app.data.Categories
import com.taskflow.app.data.LocalPlanner
import com.taskflow.app.data.PlanSource
import com.taskflow.app.data.Priority
import com.taskflow.app.net.GeminiClient
import com.taskflow.app.util.VoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiTest {

    @Test
    fun localPlannerCreatesAValidWeek() {
        val start = LocalDate.of(2026, 11, 1)
        val plan = LocalPlanner.plan("Quero organizar meu trabalho e estudar inglês esta semana", start)

        assertEquals(PlanSource.LOCAL, plan.source)
        assertTrue(plan.tasks.size >= 4)
        assertTrue(plan.tasks.all { it.title.isNotBlank() })
        assertTrue(plan.tasks.all { !it.date.isBefore(start) })
        assertTrue(plan.tasks.all { Categories.all.any { category -> category.name == it.category } })
        assertTrue(plan.tasks.any { it.title.contains("inglês") })
        assertTrue(plan.tasks.all { it.estimateMinutes in 10..180 })
    }

    @Test
    fun localPlannerFallsBackWhenNoKeyword() {
        val plan = LocalPlanner.plan("Organizar minha rotina", LocalDate.of(2026, 11, 1))
        assertTrue(plan.tasks.isNotEmpty())
        assertTrue(plan.tasks.any { it.category == "Trabalho" })
    }

    @Test
    fun voiceParserUnderstandsDateHourPriorityAndCategory() {
        val today = LocalDate.of(2026, 11, 1) // domingo
        val parsed = VoiceParser.parseLocal("Amanhã às 9h, revisar o relatório do cliente, urgente", today)

        assertEquals(today.plusDays(1), parsed.date)
        assertEquals(LocalTime.of(9, 0), parsed.time)
        assertEquals(Priority.URGENTE, parsed.priority)
        assertEquals("Trabalho", parsed.category)
        assertTrue(parsed.title.contains("revisar", ignoreCase = true))
        assertTrue(!parsed.title.contains("amanhã", ignoreCase = true))
    }

    @Test
    fun voiceParserUnderstandsWeekdayAndAfternoon() {
        val today = LocalDate.of(2026, 11, 1)
        val parsed = VoiceParser.parseLocal("Sexta às 15h30 treino na academia por 45 minutos", today)
        assertEquals(5, parsed.date.dayOfWeek.value)
        assertEquals(LocalTime.of(15, 30), parsed.time)
        assertEquals("Saúde & Bem-estar", parsed.category)
        assertEquals(45, parsed.estimatedMinutes)
    }

    @Test
    fun geminiPlanJsonIsParsed() {
        val raw = """
            ```json
            {"summary":"Semana equilibrada","tasks":[
              {"title":"Estudar inglês","category":"Estudo","priority":"alta","date":"2026-11-02",
               "time":"19:00","estimatedMinutes":45,"notes":"Listening","subtasks":["Podcast","Anotar expressões"]},
              {"title":"Treino","category":"Saúde","priority":"baixa","date":"2026-11-03",
               "time":"7:00","estimatedMinutes":45,"subtasks":[]}
            ]}
            ```
        """.trimIndent()
        val plan = GeminiClient.parsePlanJson(raw, LocalDate.of(2026, 11, 1))
        assertEquals(2, plan.tasks.size)
        assertEquals("Estudo", plan.tasks[0].category)
        assertEquals(Priority.ALTA, plan.tasks[0].priority)
        assertEquals(LocalDate.of(2026, 11, 2), plan.tasks[0].date)
        assertEquals("Saúde & Bem-estar", plan.tasks[1].category)
    }

    @Test
    fun geminiVoiceJsonIsParsed() {
        val raw = """{"title":"Revisar relatório","date":"2026-11-02","time":"9:00",
            "priority":"urgente","category":"Trabalho","estimatedMinutes":30}"""
        val parsed = GeminiClient.parseVoiceJson(raw, LocalDate.of(2026, 11, 1))
        assertEquals("Revisar relatório", parsed.title)
        assertEquals(LocalDate.of(2026, 11, 2), parsed.date)
        assertEquals(LocalTime.of(9, 0), parsed.time)
        assertEquals(Priority.URGENTE, parsed.priority)
        assertEquals("Trabalho", parsed.category)
    }
}
