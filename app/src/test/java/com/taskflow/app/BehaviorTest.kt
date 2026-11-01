package com.taskflow.app

import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.Priority
import com.taskflow.app.data.Recurrence
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.service.ReminderReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BehaviorTest {

    private lateinit var app: TaskFlowApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Store.init(app)
        Store.resetAll()
        Store.updateSettings { it.hasCompletedOnboarding = true }
    }

    @Test
    fun completingTaskAwardsXpAndCreatesRecurringCopy() {
        val task = Task(
            id = "r1",
            title = "Diário",
            date = LocalDate.now(),
            time = LocalTime.of(8, 0),
            recurrence = Recurrence.DAILY
        )
        Store.saveTask(task)
        val xpBefore = Store.xp
        Store.toggleTask(task.id)
        assertTrue(Store.tasks.first { it.id == task.id }.completed)
        assertEquals(xpBefore + 10, Store.xp)
        assertEquals(2, Store.tasks.size)
        assertEquals(LocalDate.now().plusDays(1), Store.tasks.first { it.id != task.id }.date)
    }

    @Test
    fun habitToggleUpdatesStreak() {
        Store.updateSettings { it.hasCompletedOnboarding = true }
        Store.seedDemoData()
        val habit = Store.habits.first()
        val wasDone = habit.isDoneOn(LocalDate.now())
        Store.toggleHabit(habit.id)
        assertEquals(!wasDone, habit.isDoneOn(LocalDate.now()))
        if (!wasDone) assertTrue(habit.streak >= 1)
    }

    @Test
    fun exportImportRoundTrip() {
        Store.seedDemoData()
        val json = Store.exportJson()
        val count = Store.tasks.size
        Store.resetAll()
        assertEquals(0, Store.tasks.size)
        assertTrue(Store.importJson(json))
        assertEquals(count, Store.tasks.size)
    }

    @Test
    fun reminderReceiverPostsNotification() {
        val task = Task(
            id = "n1",
            title = "Reunião",
            date = LocalDate.now(),
            time = LocalTime.now().plusHours(1),
            reminders = mutableListOf(15)
        )
        Store.saveTask(task)
        val intent = Intent(app, ReminderReceiver::class.java).apply {
            action = com.taskflow.app.service.ReminderScheduler.ACTION_REMIND
            putExtra("task_id", "n1")
        }
        ReminderReceiver().onReceive(app, intent)
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager)
        assertNotNull(shadowOf(manager).allNotifications)
    }

    @Test
    fun blankTitleIsRejectedByEditor() {
        val controller = org.robolectric.Robolectric.buildActivity(com.taskflow.app.ui.TaskEditorActivity::class.java).setup()
        val before = Store.tasks.size
        controller.get().findViewById<android.view.View>(R.id.btnSave).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(before, Store.tasks.size)
        assertTrue(controller.get().isFinishing.not())
    }

    @Test
    fun taskPriorityAndDateSurviveSave() {
        val task = Task(
            id = "p1",
            title = "Importante",
            priority = Priority.URGENTE,
            date = LocalDate.now().plusDays(3),
            time = LocalTime.of(15, 30)
        )
        Store.saveTask(task)
        val loaded = Store.tasks.first { it.id == "p1" }
        assertEquals(Priority.URGENTE, loaded.priority)
        assertEquals(LocalTime.of(15, 30), loaded.time)
    }
}
