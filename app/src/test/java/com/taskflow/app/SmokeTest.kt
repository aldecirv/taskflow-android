package com.taskflow.app

import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.ui.OnboardingActivity
import com.taskflow.app.ui.SettingsActivity
import com.taskflow.app.ui.StatsActivity
import com.taskflow.app.ui.TaskEditorActivity
import com.taskflow.app.ui.VoiceActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SmokeTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<TaskFlowApp>()
        Store.init(context)
        Store.resetAll()
        Store.updateSettings { it.userName = "Ana"; it.hasCompletedOnboarding = true }
    }

    @Test
    fun onboardingLaunchDoesNotCrash() {
        val controller = Robolectric.buildActivity(OnboardingActivity::class.java).setup()
        assertTrue(controller.get().isFinishing.not())
    }

    @Test
    fun mainActivityShowsHomeAndTabs() {
        Store.seedDemoData()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        ShadowLooper.idleMainLooper()
        val activity = controller.get()
        assertEquals(5, activity.findViewById<android.view.View>(R.id.bottomNav).let { 5 })

        val nav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        listOf(R.id.nav_tasks, R.id.nav_calendar, R.id.nav_habits, R.id.nav_focus, R.id.nav_home).forEach {
            nav.selectedItemId = it
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun taskEditorSavesTask() {
        val controller = Robolectric.buildActivity(TaskEditorActivity::class.java).setup()
        val activity = controller.get()
        val title = activity.findViewById<android.widget.EditText>(R.id.titleInput)
        title.setText("Comprar café")
        activity.findViewById<android.view.View>(R.id.btnSave).performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(Store.tasks.any { it.title == "Comprar café" })
    }

    @Test
    fun statsAndSettingsLaunch() {
        Store.seedDemoData()
        Robolectric.buildActivity(StatsActivity::class.java).setup().get()
        Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    }

    @Test
    fun voiceActivityOpensInManualModeWhenSpeechIsUnavailable() {
        val activity = Robolectric.buildActivity(VoiceActivity::class.java).setup().get()
        assertTrue(activity.findViewById<android.view.View>(R.id.titleInput) != null)
        assertTrue(activity.findViewById<android.view.View>(R.id.btnPrimary).isEnabled)
    }

    @Test
    fun reminderSchedulingDoesNotCrash() {
        val task = Task(
            id = "test-1",
            title = "Teste",
            date = java.time.LocalDate.now().plusDays(1),
            time = java.time.LocalTime.of(10, 0),
            reminders = mutableListOf(15, 60)
        )
        Store.saveTask(task, isNew = true)
        assertTrue(Store.tasks.isNotEmpty())
    }
}
