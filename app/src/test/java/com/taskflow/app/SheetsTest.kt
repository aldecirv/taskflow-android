package com.taskflow.app

import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.Store
import com.taskflow.app.ui.sheets.FocusTaskSheet
import com.taskflow.app.ui.sheets.HabitEditorSheet
import com.taskflow.app.ui.sheets.PlannerSheet
import com.taskflow.app.ui.sheets.TemplatesSheet
import com.taskflow.app.ui.sheets.WeeklyReviewSheet
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetsTest {

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TaskFlowApp>()
        Store.init(app)
        Store.resetAll()
        Store.updateSettings { it.hasCompletedOnboarding = true }
        Store.seedDemoData()
    }

    @Test
    fun allBottomSheetsInflate() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val manager = activity.supportFragmentManager

        TemplatesSheet().show(manager, "templates")
        ShadowLooper.idleMainLooper()
        manager.executePendingTransactions()

        PlannerSheet().show(manager, "planner")
        ShadowLooper.idleMainLooper()
        manager.executePendingTransactions()

        WeeklyReviewSheet().show(manager, "weekly")
        ShadowLooper.idleMainLooper()
        manager.executePendingTransactions()

        FocusTaskSheet().show(manager, "focus")
        ShadowLooper.idleMainLooper()
        manager.executePendingTransactions()

        HabitEditorSheet().show(manager, "habit")
        ShadowLooper.idleMainLooper()
        manager.executePendingTransactions()
    }
}
