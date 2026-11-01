package com.taskflow.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Accent
import com.taskflow.app.data.Store
import com.taskflow.app.data.ThemeMode
import com.taskflow.app.databinding.ActivityOnboardingBinding
import com.taskflow.app.util.ThemeManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var accent = Accent.INDIGO
    private var themeMode = ThemeMode.SYSTEM
    private var replay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        replay = intent.getBooleanExtra(EXTRA_REPLAY, false)
        accent = Store.settings.accent
        themeMode = Store.settings.themeMode
        binding.nameInput.setText(if (replay) Store.settings.userName else "")
        binding.demoSwitch.isChecked = !replay

        renderAccents()
        renderThemes()

        binding.btnStart.text = if (replay) "Concluir" else "Começar agora"
        binding.btnStart.setOnClickListener { finishOnboarding() }
    }

    private fun renderAccents() {
        binding.accentRow.removeAllViews()
        Accent.entries.forEach { option ->
            val selected = option == accent
            val view = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(option.colorHex))
                    if (selected) setStroke(dp(3), Color.WHITE)
                }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) }
                setOnClickListener {
                    accent = option
                    renderAccents()
                }
            }
            binding.accentRow.addView(view)
        }
    }

    private fun renderThemes() {
        binding.themeRow.removeAllViews()
        ThemeMode.entries.forEach { mode ->
            val selected = mode == themeMode
            val view = TextView(this).apply {
                text = mode.label
                textSize = 11.5f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(9).toFloat()
                    setColor(if (selected) ContextCompat.getColor(this@OnboardingActivity, R.color.brand_indigo) else Color.TRANSPARENT)
                }
                setTextColor(
                    if (selected) Color.WHITE
                    else ContextCompat.getColor(this@OnboardingActivity, R.color.text_secondary)
                )
                setOnClickListener {
                    themeMode = mode
                    renderThemes()
                }
            }
            binding.themeRow.addView(view)
        }
    }

    private fun finishOnboarding() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty().ifBlank { "Você" }
        Store.updateSettings {
            it.userName = name
            it.accent = accent
            it.themeMode = themeMode
            it.hasCompletedOnboarding = true
        }
        if (!replay && binding.demoSwitch.isChecked) {
            Store.seedDemoData()
        }
        ThemeManager.applyNightMode(this)
        if (replay) {
            finish()
        } else {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_REPLAY = "replay"
    }
}
