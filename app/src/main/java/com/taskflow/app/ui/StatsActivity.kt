package com.taskflow.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.taskflow.app.R
import com.taskflow.app.data.Badge
import com.taskflow.app.data.Categories
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityStatsBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.util.ThemeManager
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val level = Store.level()
        binding.levelTag.text = "NÍVEL $level"
        binding.levelTitle.text = Store.levelTitle()
        binding.xpLabel.text = "${Store.xp} XP · ${Store.xpForNextLevel() - Store.xpIntoLevel()} XP para o nível ${level + 1}"
        binding.xpBar.max = Store.xpForNextLevel()
        binding.xpBar.setProgressCompat(Store.xpIntoLevel(), true)

        val today = LocalDate.now()
        val weekStart = today.minusDays(6)
        val doneWeek = Store.tasks.count { it.completedAt?.toLocalDate()?.let { d -> !d.isBefore(weekStart) } == true }
        val focusMinutes = Store.totalPomodoros * Store.settings.workMinutes
        binding.metricDone.text = doneWeek.toString()
        binding.metricFocus.text = formatHours(focusMinutes)
        binding.metricStreak.text = "${Store.bestStreak()} dias"
        binding.metricPomodoros.text = Store.totalPomodoros.toString()

        val values = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            Store.tasks.count { it.completedAt?.toLocalDate() == day }
        }
        val labels = (6 downTo 0).map { offset ->
            Dates.weekdayShort(today.minusDays(offset.toLong())).take(1)
        }
        binding.weekChart.setData(values, labels, 6)
        binding.weekChart.setColors(
            ContextCompat.getColor(this, R.color.brand_indigo),
            ContextCompat.getColor(this, R.color.border)
        )
        val total = values.sum()
        val average = if (values.isEmpty()) 0f else total.toFloat() / values.size
        val taskWord = if (total == 1) "tarefa" else "tarefas"
        binding.chartSubtitle.text = "Total de $total $taskWord · média de ${"%.1f".format(average)} por dia"

        renderCategories()
        renderBadges()
        renderInsight()
    }

    private fun renderCategories() {
        binding.categoryList.removeAllViews()
        val all = Store.tasks
        val maxCount = Categories.all.maxOfOrNull { cat -> all.count { it.category == cat.name } }?.coerceAtLeast(1) ?: 1
        Categories.all.forEach { cat ->
            val count = all.count { it.category == cat.name }
            val color = Color.parseColor(cat.colorHex)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
            }
            val name = TextView(this).apply {
                text = cat.name
                textSize = 12.5f
                setTextColor(ContextCompat.getColor(this@StatsActivity, R.color.text_primary))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
            }
            val value = TextView(this).apply {
                text = count.toString()
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@StatsActivity, R.color.text_secondary))
            }
            header.addView(dot)
            header.addView(name)
            header.addView(value)

            val bar = LinearProgressIndicator(this).apply {
                setMax(maxCount)
                setProgressCompat(count, false)
                setIndicatorColor(color)
                trackColor = ContextCompat.getColor(this@StatsActivity, R.color.surface_alt)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
            }
            row.addView(header)
            row.addView(bar)
            binding.categoryList.addView(row)
        }
    }

    private fun renderBadges() {
        binding.badgeList.removeAllViews()
        Store.badges().forEach { badge ->
            binding.badgeList.addView(badgeRow(badge))
        }
    }

    private fun badgeRow(badge: Badge): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val iconWrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    if (badge.unlocked) Color.parseColor("#14F59E0B")
                    else ContextCompat.getColor(this@StatsActivity, R.color.surface_alt)
                )
            }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        val icon = ImageView(this).apply {
            setImageResource(badge.icon)
            imageTintList = ColorStateList.valueOf(
                if (badge.unlocked) Color.parseColor("#F59E0B")
                else ContextCompat.getColor(this@StatsActivity, R.color.text_tertiary)
            )
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        }
        iconWrap.addView(icon)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }
        texts.addView(TextView(this).apply {
            text = badge.title
            textSize = 13.5f
            setTextColor(
                ContextCompat.getColor(
                    this@StatsActivity,
                    if (badge.unlocked) R.color.text_primary else R.color.text_secondary
                )
            )
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        texts.addView(TextView(this).apply {
            text = badge.description
            textSize = 11.5f
            setTextColor(ContextCompat.getColor(this@StatsActivity, R.color.text_tertiary))
        })

        val status = ImageView(this).apply {
            setImageResource(if (badge.unlocked) R.drawable.ic_verified else R.drawable.ic_lock)
            imageTintList = ColorStateList.valueOf(
                if (badge.unlocked) ContextCompat.getColor(this@StatsActivity, R.color.emerald)
                else ContextCompat.getColor(this@StatsActivity, R.color.text_tertiary)
            )
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        }

        row.addView(iconWrap)
        row.addView(texts)
        row.addView(status)
        return row
    }

    private fun renderInsight() {
        val today = LocalDate.now()
        val recent = Store.tasks.filter {
            it.completedAt?.toLocalDate()?.let { d -> !d.isBefore(today.minusDays(27)) } == true
        }
        val bestDay = recent.groupBy { it.completedAt!!.dayOfWeek }
            .maxByOrNull { it.value.size }?.key
        val bestCategory = recent.groupBy { it.category }.maxByOrNull { it.value.size }?.key

        binding.insight.text = when {
            recent.isEmpty() -> "Conclua algumas tarefas para receber insights sobre seus melhores dias e categorias."
            bestDay != null && bestCategory != null ->
                "Seu melhor dia é ${bestDay.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))} e a categoria " +
                    "que você mais conclui é $bestCategory. Proteja esse dia para as tarefas mais difíceis."
            else -> "Continue registrando suas tarefas para descobrir seus padrões de produtividade."
        }
    }

    private fun formatHours(minutes: Int): String =
        if (minutes < 60) "${minutes}min" else "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StatsActivity::class.java))
        }
    }
}
