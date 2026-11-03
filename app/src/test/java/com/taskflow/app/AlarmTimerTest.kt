package com.taskflow.app

import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.service.FloatingTimerService
import com.taskflow.app.service.TaskAlarmScheduler
import com.taskflow.app.ui.TaskEditorActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmTimerTest {

    private val context = ApplicationProvider.getApplicationContext<TaskFlowApp>()

    @Test
    fun taskEditorLoadsAlarmAndTimerControls() {
        Store.init(context)
        val activity = Robolectric.buildActivity(TaskEditorActivity::class.java).setup().visible().get()
        assertNotNull(activity.findViewById<android.view.View>(R.id.alarmSwitch))
        assertNotNull(activity.findViewById<android.view.View>(R.id.alarmSoundGroup))
        assertNotNull(activity.findViewById<android.view.View>(R.id.btnFloatingTimer))
    }

    @Test
    fun floatingTimerLayoutInflates() {
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_TaskFlow)
        val view = LayoutInflater.from(themed).inflate(R.layout.view_floating_timer, null)
        assertNotNull(view.findViewById<android.view.View>(R.id.timerValue))
        assertNotNull(view.findViewById<android.view.View>(R.id.btnTimerPause))
        assertNotNull(view.findViewById<android.view.View>(R.id.btnTimerFinish))
    }

    @Test
    fun trackedTimeIsAddedToTask() {
        Store.init(context)
        Store.resetAll()
        val task = Task(
            id = "timer-task",
            title = "Estudar",
            date = LocalDate.now(),
            time = LocalTime.now().plusHours(1)
        )
        Store.saveTask(task)
        Store.addTrackedTime(task.id, 125)
        assertEquals(125, Store.tasks.first { it.id == task.id }.trackedSeconds)
    }

    @Test
    fun alarmCanBeScheduledAndCancelled() {
        Store.init(context)
        Store.resetAll()
        val task = Task(
            id = "alarm",
            title = "Reunião",
            date = LocalDate.now().plusDays(1),
            time = LocalTime.of(9, 0),
            alertEnabled = true,
            alertSound = "alarm"
        )
        Store.saveTask(task)
        TaskAlarmScheduler.schedule(context, task)
        TaskAlarmScheduler.cancel(context, task.id)
        assertTrue(Store.tasks.any { it.alertEnabled })
    }
}
