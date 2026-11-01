package com.taskflow.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityMainBinding
import com.taskflow.app.ui.OnboardingActivity
import com.taskflow.app.ui.SettingsActivity
import com.taskflow.app.ui.StatsActivity
import com.taskflow.app.ui.TaskEditorActivity
import com.taskflow.app.ui.VoiceActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    private val outDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<TaskFlowApp>()
        Store.init(context)
        Store.resetAll()
        Store.updateSettings {
            it.userName = "Ana Souza"
            it.hasCompletedOnboarding = true
        }
        Store.seedDemoData()
    }

    private fun capture(view: View, name: String) {
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 1080, 2280)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun captureMainScreens() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        ShadowLooper.idleMainLooper()
        val activity = controller.get()
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, 1080, 2280)
        val nav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)

        capture(decor, "01_home")
        listOf(R.id.nav_tasks to "02_tasks", R.id.nav_calendar to "03_calendar",
            R.id.nav_habits to "04_habits", R.id.nav_focus to "05_focus").forEach { (id, name) ->
            nav.selectedItemId = id
            ShadowLooper.idleMainLooper()
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, 1080, 2280)
            capture(decor, name)
        }
    }

    @Test
    fun captureSecondaryScreens() {
        val stats = Robolectric.buildActivity(StatsActivity::class.java).setup().visible().get()
        stats.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        stats.window.decorView.layout(0, 0, 1080, 2280)
        capture(stats.window.decorView, "06_stats")

        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().visible().get()
        settings.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        settings.window.decorView.layout(0, 0, 1080, 2280)
        capture(settings.window.decorView, "07_settings")

        val editor = Robolectric.buildActivity(TaskEditorActivity::class.java).setup().visible().get()
        editor.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        editor.window.decorView.layout(0, 0, 1080, 2280)
        capture(editor.window.decorView, "08_editor")
    }

    @Test
    fun captureOnboarding() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().visible().get()
        activity.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        activity.window.decorView.layout(0, 0, 1080, 2280)
        capture(activity.window.decorView, "09_onboarding")
    }
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun captureDarkScreens() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        ShadowLooper.idleMainLooper()
        val decor = controller.get().window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, 1080, 2280)
        capture(decor, "10_dark_home")
    }

}
