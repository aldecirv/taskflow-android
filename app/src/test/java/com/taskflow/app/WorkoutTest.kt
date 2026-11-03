package com.taskflow.app

import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.Store
import com.taskflow.app.data.Workout
import com.taskflow.app.data.WorkoutMode
import com.taskflow.app.data.WorkoutPoint
import com.taskflow.app.net.GeminiClient
import com.taskflow.app.service.TaskAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutTest {

    private val context = ApplicationProvider.getApplicationContext<TaskFlowApp>()

    @Test
    fun workoutsAreStoredAndSummarized() {
        Store.init(context)
        Store.resetAll()
        val workout = Workout(
            id = "w1",
            mode = WorkoutMode.WALK,
            startedAt = LocalDateTime.now().minusMinutes(32),
            durationSeconds = 1920,
            distanceMeters = 3200.0,
            steps = 4100,
            calories = 210,
            avgPaceSecondsPerKm = 600,
            route = listOf(
                WorkoutPoint(-23.55, -46.63, 0),
                WorkoutPoint(-23.551, -46.631, 60),
                WorkoutPoint(-23.552, -46.632, 120)
            )
        )
        Store.addWorkout(workout)
        assertEquals(1, Store.workouts.size)
        assertEquals(3, Store.workouts.first().route.size)
        val (count, km, seconds) = Store.workoutSummary()
        assertEquals(1, count)
        assertEquals(3.2, km, 0.001)
        assertEquals(1920, seconds)
        Store.deleteWorkout("w1")
        assertTrue(Store.workouts.isEmpty())
    }

    @Test
    fun alertTaskSchedulesWithoutCrashing() {
        Store.init(context)
        Store.resetAll()
        val task = com.taskflow.app.data.Task(
            id = "alarm-task",
            title = "Reunião",
            date = LocalDate.now().plusDays(1),
            time = LocalTime.now().plusHours(2),
            alertEnabled = true,
            alertSound = "alarm"
        )
        Store.saveTask(task)
        TaskAlarmScheduler.schedule(context, task)
        TaskAlarmScheduler.cancel(context, task.id)
        assertTrue(Store.tasks.any { it.id == task.id && it.alertEnabled })
    }

    @Test
    fun aiPlannerClampsPastDatesToToday() {
        val today = LocalDate.now()
        val raw = """
            {"summary":"teste","tasks":[
              {"title":"Tarefa antiga","category":"Trabalho","priority":"alta","date":"2020-01-01","time":"09:00","estimatedMinutes":30,"notes":"","subtasks":[]},
              {"title":"Tarefa futura","category":"Estudo","priority":"média","date":"2030-01-01","time":"10:00","estimatedMinutes":30,"notes":"","subtasks":[]}
            ]}
        """.trimIndent()
        val plan = GeminiClient.parsePlanJson(raw, today)
        assertEquals(today, plan.tasks[0].date)
        assertEquals(today.plusDays(6), plan.tasks[1].date)
    }
}
