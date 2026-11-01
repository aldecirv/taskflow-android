package com.taskflow.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityMainBinding
import com.taskflow.app.ui.CalendarFragment
import com.taskflow.app.ui.FocusFragment
import com.taskflow.app.ui.HabitsFragment
import com.taskflow.app.ui.HomeFragment
import com.taskflow.app.ui.OnboardingActivity
import com.taskflow.app.ui.ProductsFragment
import com.taskflow.app.ui.SettingsActivity
import com.taskflow.app.ui.StatsActivity
import com.taskflow.app.ui.TaskEditorActivity
import com.taskflow.app.ui.TasksFragment
import com.taskflow.app.ui.VoiceActivity
import com.taskflow.app.ui.sheets.VoiceDictationSheet
import com.taskflow.app.util.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var appliedAccent = ""
    private var selectedTab = R.id.nav_home

    private data class NavItem(
        val id: Int,
        val root: View,
        val pill: View,
        val icon: ImageView,
        val label: TextView
    )

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        Store.init(this)
        ThemeManager.apply(this)
        appliedAccent = Store.settings.accent.key
        super.onCreate(savedInstanceState)

        if (!Store.settings.hasCompletedOnboarding) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.bottomNav.updatePadding(bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        setupNavigation()
        binding.globalMic.setOnClickListener {
            VoiceDictationSheet.newInstance(currentTabTag())
                .show(supportFragmentManager, "voice_dictation")
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedTab != R.id.nav_home) {
                    selectTab(R.id.nav_home)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val initial = savedInstanceState?.getInt(KEY_TAB) ?: R.id.nav_home
        selectTab(initial)
        handleIntent(intent)
        requestNotificationPermission()
        askExactAlarmsOnce()
    }

    override fun onResume() {
        super.onResume()
        if (Store.settings.accent.key != appliedAccent) {
            recreate()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, selectedTab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun navItems(): List<NavItem> = listOf(
        NavItem(R.id.nav_home, binding.navHome, binding.navHomePill, binding.navHomeIcon, binding.navHomeLabel),
        NavItem(R.id.nav_tasks, binding.navTasks, binding.navTasksPill, binding.navTasksIcon, binding.navTasksLabel),
        NavItem(R.id.nav_calendar, binding.navCalendar, binding.navCalendarPill, binding.navCalendarIcon, binding.navCalendarLabel),
        NavItem(R.id.nav_habits, binding.navHabits, binding.navHabitsPill, binding.navHabitsIcon, binding.navHabitsLabel),
        NavItem(R.id.nav_products, binding.navProducts, binding.navProductsPill, binding.navProductsIcon, binding.navProductsLabel),
        NavItem(R.id.nav_focus, binding.navFocus, binding.navFocusPill, binding.navFocusIcon, binding.navFocusLabel)
    )

    private fun setupNavigation() {
        navItems().forEach { item ->
            item.root.setOnClickListener { selectTab(item.id) }
        }
    }

    private fun currentTabTag(): String = when (selectedTab) {
        R.id.nav_tasks -> "tasks"
        R.id.nav_calendar -> "calendar"
        R.id.nav_habits -> "habits"
        R.id.nav_products -> "products"
        R.id.nav_focus -> "focus"
        else -> "home"
    }

    /** Troca a aba inferior e atualiza cores/indicador. */
    fun selectTab(itemId: Int) {
        if (!::binding.isInitialized) return
        selectedTab = itemId
        updateNavigation()
        showTab(itemId)
    }

    private fun updateNavigation() {
        val primary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val idle = ContextCompat.getColor(this, R.color.text_tertiary)
        val selectedBg = ColorStateList.valueOf(ColorUtils.withAlpha(primary, 0.12f))
        val transparent = ColorStateList.valueOf(Color.TRANSPARENT)

        navItems().forEach { item ->
            val selected = item.id == selectedTab
            item.icon.imageTintList = ColorStateList.valueOf(if (selected) primary else idle)
            item.label.setTextColor(if (selected) primary else idle)
            item.label.typeface = Typeface.create(
                if (selected) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL
            )
            item.pill.backgroundTintList = if (selected) selectedBg else transparent
        }
    }

    private fun showTab(itemId: Int) {
        val tag = when (itemId) {
            R.id.nav_tasks -> "tasks"
            R.id.nav_calendar -> "calendar"
            R.id.nav_habits -> "habits"
            R.id.nav_products -> "products"
            R.id.nav_focus -> "focus"
            else -> "home"
        }
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()
        manager.fragments.forEach { if (it.tag != tag && !it.isHidden) transaction.hide(it) }
        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragmentContainer, createFragment(tag), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commitNowAllowingStateLoss()
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        "tasks" -> TasksFragment()
        "calendar" -> CalendarFragment()
        "habits" -> HabitsFragment()
        "products" -> ProductsFragment()
        "focus" -> FocusFragment()
        else -> HomeFragment()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("open_task_id")?.let { taskId ->
            TaskEditorActivity.startEdit(this, taskId)
            intent.removeExtra("open_task_id")
        }
        when (intent.getStringExtra("open_tab")) {
            "focus" -> selectTab(R.id.nav_focus)
            "tasks" -> selectTab(R.id.nav_tasks)
            "habits" -> selectTab(R.id.nav_habits)
            "products" -> selectTab(R.id.nav_products)
            "stats" -> StatsActivity.start(this)
            "settings" -> SettingsActivity.start(this)
            "voice" -> startActivity(Intent(this, VoiceActivity::class.java))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        binding.root.postDelayed({ notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, 700)
    }

    private fun askExactAlarmsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val prefs = getSharedPreferences("taskflow_ui", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_exact_alarms", false)) return
        if (Store.tasks.none { it.reminders.isNotEmpty() && !it.completed }) return
        prefs.edit().putBoolean("asked_exact_alarms", true).apply()
        binding.root.postDelayed({ maybeAskExactAlarms() }, 1200)
    }

    private fun maybeAskExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms() || Store.tasks.none { it.reminders.isNotEmpty() && !it.completed }) return
        AlertDialog.Builder(this)
            .setTitle("Lembretes no horário exato")
            .setMessage("Para avisar você no minuto certo, permita que o TaskFlow use alarmes e lembretes.")
            .setNegativeButton("Agora não", null)
            .setPositiveButton("Permitir") { _, _ ->
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(android.net.Uri.parse("package:$packageName"))
                    )
                }
            }
            .show()
    }

    private object ColorUtils {
        fun withAlpha(color: Int, alpha: Float): Int =
            android.graphics.Color.argb(
                (alpha * 255).toInt(),
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color)
            )
    }

    companion object {
        private const val KEY_TAB = "tab"
    }
}
