package com.taskflow.app.ui

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
import androidx.core.view.updatePadding
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Workout
import com.taskflow.app.data.WorkoutMode
import com.taskflow.app.databinding.ActivityWorkoutHistoryBinding
import com.taskflow.app.util.ThemeManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Histórico analítico de treinos: calorias por período, recompensas, conquistas
 * e benefícios à saúde — complementa a lista simples da aba Treinos.
 */
class WorkoutHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutHistoryBinding
    private var period = Period.WEEK

    private enum class Period { WEEK, WEEKS, MONTHS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityWorkoutHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.periodGroup.check(R.id.period7)
        binding.periodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            period = when (checkedId) {
                R.id.period8w -> Period.WEEKS
                R.id.period6m -> Period.MONTHS
                else -> Period.WEEK
            }
            renderChart()
        }

        renderSummary()
        renderChart()
        renderRewards()
        renderBenefits()
    }

    // ------------------------------------------------------------------ resumo
    private fun renderSummary() {
        val workouts = Store.workouts
        val totalKcal = workouts.sumOf { it.calories }
        val walkKcal = workouts.filter { it.mode == WorkoutMode.WALK }.sumOf { it.calories }
        val runKcal = workouts.filter { it.mode == WorkoutMode.RUN }.sumOf { it.calories }
        val km = workouts.sumOf { it.distanceMeters } / 1000.0
        val seconds = workouts.sumOf { it.durationSeconds }

        binding.totalKcal.text = "$totalKcal kcal"
        binding.walkKcal.text = "$walkKcal kcal"
        binding.runKcal.text = "$runKcal kcal"
        binding.totalWorkouts.text = workouts.size.toString()
        binding.totalKm.text = String.format(Locale("pt", "BR"), "%.1f km", km)
        binding.totalTime.text = when {
            seconds >= 3600 -> String.format(Locale("pt", "BR"), "%.1f h", seconds / 3600.0)
            else -> "${seconds / 60} min"
        }
        binding.avgKcal.text = if (workouts.isEmpty()) "0 kcal" else "${totalKcal / workouts.size} kcal"
    }

    // ------------------------------------------------------------------ gráfico
    private fun bucketList(): List<Pair<String, Int>> {
        val workouts = Store.workouts
        val today = LocalDate.now()
        val dayFmt = DateTimeFormatter.ofPattern("dd/MM")
        return when (period) {
            Period.WEEK -> (6 downTo 0).map { offset ->
                val day = today.minusDays(offset.toLong())
                val kcal = workouts.filter { it.startedAt.toLocalDate() == day }.sumOf { it.calories }
                dayFmt.format(day) to kcal
            }
            Period.WEEKS -> (7 downTo 0).map { offset ->
                val start = today.minusWeeks(offset.toLong()).with(DayOfWeek.MONDAY)
                val end = start.plusDays(6)
                val kcal = workouts
                    .filter { val d = it.startedAt.toLocalDate(); !d.isBefore(start) && !d.isAfter(end) }
                    .sumOf { it.calories }
                dayFmt.format(start) to kcal
            }
            Period.MONTHS -> (5 downTo 0).map { offset ->
                val month = YearMonth.from(today).minusMonths(offset.toLong())
                val kcal = workouts.filter { YearMonth.from(it.startedAt) == month }.sumOf { it.calories }
                DateTimeFormatter.ofPattern("MMM", Locale("pt", "BR")).format(month) to kcal
            }
        }
    }

    private fun renderChart() {
        val buckets = bucketList()
        val max = (buckets.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        val barColor = ContextCompat.getColor(this, R.color.brand_indigo)
        val textColor = ContextCompat.getColor(this, R.color.text_tertiary)

        binding.chartContainer.removeAllViews()
        buckets.forEach { (label, kcal) ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }

            val heightPx = dp(if (kcal == 0) 3 else (kcal.toFloat() / max * 118).roundToInt().coerceAtLeast(6))
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(if (buckets.size > 10) dp(10) else dp(18), heightPx).apply {
                    bottomMargin = dp(4)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(if (kcal == 0) ContextCompat.getColor(this@WorkoutHistoryActivity, R.color.divider) else barColor)
                }
            }

            val caption = TextView(this).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(textColor)
            }

            column.addView(bar)
            column.addView(caption)
            binding.chartContainer.addView(column)
        }

        val total = buckets.sumOf { it.second }
        val active = buckets.count { it.second > 0 }
        binding.chartSummary.text = if (total == 0) {
            "Sem treinos no período."
        } else {
            "$total kcal no período · $active ${if (active == 1) "período com" else "períodos com"} atividade"
        }
    }

    // ------------------------------------------------------------------ recompensas
    private fun pointsFor(workout: Workout): Int {
        val km = workout.distanceKm
        var points = (km * if (workout.mode == WorkoutMode.RUN) 16 else 10).roundToInt()
        if (km >= 5) points += 20
        if (km >= 10) points += 40
        return points
    }

    private fun renderRewards() {
        val workouts = Store.workouts
        val points = workouts.sumOf { pointsFor(it) }
        val goals = workouts.count { it.distanceKm >= 5 || it.durationSeconds >= 1800 }
        binding.rewardPoints.text = points.toString()
        binding.rewardGoals.text = goals.toString()

        val totalKm = workouts.sumOf { it.distanceKm }
        val maxKm = workouts.maxOfOrNull { it.distanceKm } ?: 0.0
        val totalKcal = workouts.sumOf { it.calories }

        val achievements = listOf(
            Achievement("Primeiro quilômetro", "Complete 1 km em um treino", maxKm >= 1.0),
            Achievement("Cinco de uma vez", "Complete 5 km em um único treino", maxKm >= 5.0),
            Achievement("Dez em um treino", "Complete 10 km em um único treino", maxKm >= 10.0),
            Achievement("Ritmo constante", "Finalize 10 treinos", workouts.size >= 10),
            Achievement("50 km acumulados", "Some 50 km em treinos", totalKm >= 50.0),
            Achievement("100 km acumulados", "Some 100 km em treinos", totalKm >= 100.0),
            Achievement("5.000 kcal", "Acumule 5.000 kcal em atividades", totalKcal >= 5000)
        )

        binding.achievementsContainer.removeAllViews()
        achievements.forEach { achievement ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val icon = TextView(this).apply {
                text = if (achievement.unlocked) "🏅" else "🔒"
                textSize = 16f
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                }
            }
            texts.addView(TextView(this).apply {
                text = achievement.title
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@WorkoutHistoryActivity, R.color.text_primary))
            })
            texts.addView(TextView(this).apply {
                text = achievement.description
                textSize = 10.5f
                setTextColor(ContextCompat.getColor(this@WorkoutHistoryActivity, R.color.text_tertiary))
            })
            row.addView(icon)
            row.addView(texts)
            if (!achievement.unlocked) row.alpha = 0.45f
            binding.achievementsContainer.addView(row)
        }
    }

    // ------------------------------------------------------------------ saúde
    private fun renderBenefits() {
        val workouts = Store.workouts
        val maxWalk = workouts.filter { it.mode == WorkoutMode.WALK }.maxOfOrNull { it.distanceKm } ?: 0.0
        val maxRun = workouts.filter { it.mode == WorkoutMode.RUN }.maxOfOrNull { it.distanceKm } ?: 0.0

        val benefits = listOf(
            Benefit("Controle de peso", "Cada quilômetro gasta energia e ajuda no balanço calórico, favorecendo a perda de gordura com consistência.", null, 0.0),
            Benefit("Coração mais forte", "A caminhada reduz a frequência cardíaca de repouso; a corrida aumenta a capacidade cardiovascular.", null, 0.0),
            Benefit("Pressão arterial menor", "Caminhadas de 30 minutos ajudam a reduzir a pressão em hipertensos leves.", WorkoutMode.WALK, 2.0),
            Benefit("Controle do açúcar no sangue", "Caminhar após as refeições melhora a captação de glicose pelos músculos.", WorkoutMode.WALK, 0.8),
            Benefit("Ossos e articulações", "O impacto controlado da corrida estimula a densidade óssea; a caminhada fortalece pernas e quadris.", null, 1.0),
            Benefit("Humor e ansiedade", "A atividade libera endorfinas e reduz sintomas de ansiedade nas horas seguintes.", null, 1.0),
            Benefit("Sono profundo", "Treinos moderados durante o dia aumentam o tempo de sono profundo.", null, 1.5),
            Benefit("Foco e memória", "Exercício aeróbico melhora a perfusão cerebral e a atenção.", null, 1.0)
        )

        binding.benefitsContainer.removeAllViews()
        benefits.forEach { benefit ->
            val unlocked = when (benefit.mode) {
                WorkoutMode.WALK -> maxWalk >= benefit.minKm
                WorkoutMode.RUN -> maxRun >= benefit.minKm
                null -> maxWalk >= benefit.minKm || maxRun >= benefit.minKm
            }
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            block.addView(TextView(this).apply {
                text = (if (unlocked) "✓ " else "") + benefit.title
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(
                        this@WorkoutHistoryActivity,
                        if (unlocked) R.color.emerald else R.color.text_primary
                    )
                )
            })
            block.addView(TextView(this).apply {
                text = benefit.description
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@WorkoutHistoryActivity, R.color.text_secondary))
            })
            binding.benefitsContainer.addView(block)
        }
    }

    private data class Achievement(val title: String, val description: String, val unlocked: Boolean)

    private data class Benefit(
        val title: String,
        val description: String,
        val mode: WorkoutMode?,
        val minKm: Double
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
