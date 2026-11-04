package com.taskflow.app.data

import android.content.Context
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Passos do dia medidos pelo sensor, persistidos fora do ciclo de vida da tela.
 * O serviço grava; a interface lê. Distância e calorias são derivadas.
 */
object StepStore {

    private const val PREFS = "taskflow_steps"
    private const val KEY_DAY = "day"
    private const val KEY_BASE = "base"
    private const val KEY_STEPS = "steps"
    private const val KEY_GOAL = "goal"
    private const val KEY_ENABLED = "enabled"
    private const val DEFAULT_GOAL = 8000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now().toString()

    fun steps(context: Context): Int {
        val p = prefs(context)
        if (p.getString(KEY_DAY, "") != today()) return 0
        return p.getInt(KEY_STEPS, 0)
    }

    fun goal(context: Context): Int = prefs(context).getInt(KEY_GOAL, DEFAULT_GOAL)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setGoal(context: Context, goal: Int) {
        prefs(context).edit().putInt(KEY_GOAL, goal.coerceIn(1000, 50000)).apply()
    }

    fun day(context: Context): String = prefs(context).getString(KEY_DAY, "") ?: ""

    fun base(context: Context): Float = prefs(context).getFloat(KEY_BASE, -1f)

    fun resetForToday(context: Context, base: Float) {
        prefs(context).edit()
            .putString(KEY_DAY, today())
            .putFloat(KEY_BASE, base)
            .putInt(KEY_STEPS, 0)
            .apply()
    }

    fun save(context: Context, steps: Int, base: Float) {
        prefs(context).edit()
            .putString(KEY_DAY, today())
            .putFloat(KEY_BASE, base)
            .putInt(KEY_STEPS, steps.coerceAtLeast(0))
            .apply()
    }

    /** 1 passo ≈ 0,72 m (marcha média). */
    fun distanceKm(steps: Int): Double = steps * 0.72 / 1000.0

    /** ~0,04 kcal por passo para 70 kg, ajustado pelo peso configurado. */
    fun calories(steps: Int, weightKg: Double): Int =
        (steps * 0.04 * (weightKg / 70.0)).roundToInt()

    fun activeMinutes(steps: Int): Int = (steps / 110.0).roundToInt()
}
