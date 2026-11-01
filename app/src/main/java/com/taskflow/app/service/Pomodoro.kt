package com.taskflow.app.service

/** Estado compartilhado do timer entre o serviço e as telas. */
object Pomodoro {

    enum class Mode(val label: String, val shortLabel: String) {
        WORK("Foco profundo", "Foco"),
        SHORT_BREAK("Pausa curta", "Pausa"),
        LONG_BREAK("Pausa longa", "Pausa")
    }

    var mode: Mode = Mode.WORK
    var totalSeconds: Int = 25 * 60
    var remainingSeconds: Int = totalSeconds
    var running: Boolean = false
    var taskId: String? = null
    var completedWorkSessions: Int = 0

    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyListeners() {
        listeners.toList().forEach { it() }
    }

    fun prepare(newMode: Mode, minutes: Int) {
        mode = newMode
        totalSeconds = minutes * 60
        remainingSeconds = totalSeconds
        running = false
        notifyListeners()
    }

    fun progress(): Float =
        if (totalSeconds <= 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds

    fun timeText(): String {
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s)
    }
}
