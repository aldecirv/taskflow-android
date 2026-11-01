package com.taskflow.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.taskflow.app.R
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.service.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Armazenamento local-first do TaskFlow.
 * Tudo fica em SharedPreferences como JSON — sem servidor, sem login e sem rede.
 */
object Store {

    private const val PREFS = "taskflow_store"
    private const val KEY_TASKS = "tasks"
    private const val KEY_HABITS = "habits"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_XP = "xp"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_PLANNER = "planner_used"

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    val tasks = mutableListOf<Task>()
    val habits = mutableListOf<Habit>()
    var settings = UserSettings()
        private set
    var xp: Int = 0
        private set
    var totalPomodoros: Int = 0
        private set
    var plannerUsed: Boolean = false
        private set

    private val listeners = mutableSetOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (initialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
        initialized = true
    }

    // ---------------------------------------------------------------- listeners

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        main.post { listeners.toList().forEach { it() } }
    }

    // ------------------------------------------------------------------ leitura

    fun level(): Int = xp / 150 + 1

    fun levelTitle(): String {
        val titles = listOf(
            "Iniciante Dedicado", "Organizador Atento", "Focalizador Ágil",
            "Estrategista Diário", "Mestre dos Hábitos", "Comandante de Metas",
            "Especialista em Fluxo", "Alta Performance", "Guru da Disciplina",
            "Titã da Produtividade"
        )
        return titles[(level() - 1).coerceIn(0, titles.lastIndex)]
    }

    fun xpIntoLevel(): Int = xp % 150
    fun xpForNextLevel(): Int = 150

    fun tasksOn(date: LocalDate): List<Task> =
        tasks.filter { it.date == date }.sortedWith(compareBy({ it.completed }, { it.time ?: LocalTime.MAX }))

    fun todayTasks(): List<Task> = tasksOn(LocalDate.now())

    fun pendingTasks(): List<Task> =
        tasks.filter { !it.completed }.sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MAX }))

    fun overdueTasks(): List<Task> =
        tasks.filter { it.isOverdue() }.sortedBy { it.date }

    fun completedToday(): Int = tasks.count { it.completed && it.completedAt?.toLocalDate() == LocalDate.now() }

    fun bestStreak(): Int = habits.maxOfOrNull { it.bestStreak } ?: 0

    fun focusMinutes(): Int = totalPomodoros * settings.workMinutes

    fun badges(): List<Badge> {
        val completedEver = tasks.count { it.completed }
        return listOf(
            Badge("first_task", "Primeiro Passo", "Conclua sua primeira tarefa", R.drawable.ic_verified, completedEver >= 1),
            Badge("pomodoro_master", "Mente Focada", "Complete 4 sessões de Pomodoro", R.drawable.ic_local_fire_department, totalPomodoros >= 4),
            Badge("streak_7", "Hábito de Ferro", "Alcance 7 dias seguidos em um hábito", R.drawable.ic_bolt, bestStreak() >= 7),
            Badge("planner", "Arquiteto do Dia", "Organize sua agenda com o planejador", R.drawable.ic_auto_awesome, plannerUsed),
            Badge("level_10", "Titã da Produtividade", "Alcance o nível 10", R.drawable.ic_workspace_premium, level() >= 10)
        )
    }

    // ------------------------------------------------------------------ tarefas

    fun saveTask(task: Task, isNew: Boolean = false) {
        val index = tasks.indexOfFirst { it.id == task.id }
        task.updatedAt = LocalDateTime.now()
        if (index >= 0) {
            tasks[index] = task
        } else {
            tasks.add(0, task)
            if (isNew) award(5)
        }
        persistTasks()
        ReminderScheduler.schedule(TaskFlowApp.instance, task)
        notifyChanged()
    }

    fun deleteTask(id: String) {
        tasks.removeAll { it.id == id }
        ReminderScheduler.cancel(TaskFlowApp.instance, id)
        persistTasks()
        notifyChanged()
    }

    /** Marca/desmarca e devolve o novo estado de conclusão. */
    fun toggleTask(id: String): Boolean {
        val task = tasks.firstOrNull { it.id == id } ?: return false
        if (task.completed) {
            task.completed = false
            task.completedAt = null
        } else {
            task.completed = true
            task.completedAt = LocalDateTime.now()
            award(10)
            if (task.recurrence != Recurrence.NONE) createNextOccurrence(task)
        }
        task.updatedAt = LocalDateTime.now()
        persistTasks()
        ReminderScheduler.schedule(TaskFlowApp.instance, task)
        notifyChanged()
        return task.completed
    }

    fun registerPomodoro(taskId: String?) {
        totalPomodoros += 1
        if (taskId != null) {
            tasks.firstOrNull { it.id == taskId }?.let {
                it.pomodoroCount += 1
                it.updatedAt = LocalDateTime.now()
            }
        }
        persistTasks()
        prefs.edit().putInt(KEY_SESSIONS, totalPomodoros).apply()
        award(25)
        notifyChanged()
    }

    fun markPlannerUsed() {
        plannerUsed = true
        prefs.edit().putBoolean(KEY_PLANNER, true).apply()
        notifyChanged()
    }

    private fun createNextOccurrence(task: Task) {
        val nextDate = when (task.recurrence) {
            Recurrence.DAILY -> task.date.plusDays(1)
            Recurrence.WEEKDAYS -> {
                var d = task.date.plusDays(1)
                while (d.dayOfWeek.value > 5) d = d.plusDays(1)
                d
            }
            Recurrence.WEEKLY -> task.date.plusWeeks(1)
            Recurrence.MONTHLY -> task.date.plusMonths(1)
            Recurrence.YEARLY -> task.date.plusYears(1)
            Recurrence.NONE -> return
        }
        val clone = Task(
            id = UUID.randomUUID().toString(),
            title = task.title,
            notes = task.notes,
            category = task.category,
            tags = task.tags.toMutableList(),
            priority = task.priority,
            date = nextDate,
            time = task.time,
            estimateMinutes = task.estimateMinutes,
            subtasks = task.subtasks.map { SubTask(UUID.randomUUID().toString(), it.title, false) }.toMutableList(),
            recurrence = task.recurrence,
            reminders = task.reminders.toMutableList(),
            targetPomodoros = task.targetPomodoros
        )
        tasks.add(0, clone)
        ReminderScheduler.schedule(TaskFlowApp.instance, clone)
    }

    // ------------------------------------------------------------------ hábitos

    fun addHabit(habit: Habit) {
        habits.add(habit)
        persistHabits()
        award(10)
        notifyChanged()
    }

    fun deleteHabit(id: String) {
        habits.removeAll { it.id == id }
        persistHabits()
        notifyChanged()
    }

    fun toggleHabit(id: String): Boolean {
        val habit = habits.firstOrNull { it.id == id } ?: return false
        val today = LocalDate.now()
        val nowDone = if (habit.completedDates.contains(today)) {
            habit.completedDates.remove(today)
            false
        } else {
            habit.completedDates.add(today)
            true
        }
        recomputeStreak(habit)
        persistHabits()
        if (nowDone) award(15)
        notifyChanged()
        return nowDone
    }

    fun recomputeStreak(habit: Habit) {
        val today = LocalDate.now()
        var cursor = if (habit.completedDates.contains(today)) today else today.minusDays(1)
        var streak = 0
        while (habit.completedDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        habit.streak = streak
        if (streak > habit.bestStreak) habit.bestStreak = streak
    }

    // ------------------------------------------------------------------- ajustes

    fun updateSettings(block: (UserSettings) -> Unit) {
        block(settings)
        persistSettings()
        notifyChanged()
    }

    fun setOnboarded() {
        settings.hasCompletedOnboarding = true
        persistSettings()
    }

    // ---------------------------------------------------------------------- XP

    fun award(points: Int): Boolean {
        val before = level()
        xp += points
        prefs.edit().putInt(KEY_XP, xp).apply()
        val after = level()
        notifyChanged()
        return after > before
    }

    // ---------------------------------------------------------------- demo/data

    fun seedDemoData() {
        if (tasks.isNotEmpty() || habits.isNotEmpty()) return
        val today = LocalDate.now()
        tasks.addAll(
            listOf(
                Task(
                    id = UUID.randomUUID().toString(),
                    title = "Finalizar apresentação do projeto",
                    notes = "Revisar os slides e ensaiar a fala de abertura.",
                    category = "Trabalho",
                    tags = mutableListOf("Deep Work", "Urgente"),
                    priority = Priority.URGENTE,
                    date = today,
                    time = LocalTime.of(10, 0),
                    estimateMinutes = 90,
                    subtasks = mutableListOf(
                        SubTask(UUID.randomUUID().toString(), "Revisar os números do trimestre", true),
                        SubTask(UUID.randomUUID().toString(), "Ensaiar a abertura", false)
                    ),
                    pomodoroCount = 1,
                    targetPomodoros = 3
                ),
                Task(
                    id = UUID.randomUUID().toString(),
                    title = "Treino de força na academia",
                    category = "Saúde & Bem-estar",
                    tags = mutableListOf("Rotina"),
                    priority = Priority.ALTA,
                    date = today,
                    time = LocalTime.of(18, 30),
                    estimateMinutes = 60,
                    targetPomodoros = 0
                ),
                Task(
                    id = UUID.randomUUID().toString(),
                    title = "Ler 20 páginas do livro atual",
                    category = "Estudo",
                    tags = mutableListOf("< 15 min"),
                    priority = Priority.MEDIA,
                    date = today,
                    estimateMinutes = 25,
                    recurrence = Recurrence.DAILY,
                    reminders = mutableListOf(30),
                    completed = true,
                    completedAt = LocalDateTime.now().minusHours(2)
                ),
                Task(
                    id = UUID.randomUUID().toString(),
                    title = "Planejar os gastos da semana",
                    notes = "Conferir assinaturas e separar o aporte do mês.",
                    category = "Finanças",
                    priority = Priority.MEDIA,
                    date = today.plusDays(1),
                    time = LocalTime.of(9, 0),
                    estimateMinutes = 30
                ),
                Task(
                    id = UUID.randomUUID().toString(),
                    title = "Consulta médica de rotina",
                    category = "Saúde & Bem-estar",
                    priority = Priority.ALTA,
                    date = today.plusDays(2),
                    time = LocalTime.of(14, 0),
                    estimateMinutes = 60,
                    reminders = mutableListOf(60, 1440)
                )
            )
        )
        val habitSeeds = listOf(
            Triple("Beber 2,5 L de água", "#06B6D4", 6),
            Triple("Ler 20 minutos", "#8B5CF6", 12),
            Triple("Meditar 10 minutos", "#F59E0B", 3),
            Triple("Exercício físico", "#10B981", 4)
        )
        habitSeeds.forEach { (name, color, streak) ->
            val h = Habit(
                id = UUID.randomUUID().toString(),
                name = name,
                colorHex = color,
                frequency = if (name.contains("Exercício")) HabitFrequency.WEEKDAYS else HabitFrequency.DAILY
            )
            for (i in 0 until streak) h.completedDates.add(today.minusDays(i.toLong()))
            h.streak = streak
            h.bestStreak = streak + 3
            habits.add(h)
        }
        persistTasks()
        persistHabits()
        notifyChanged()
    }

    fun resetAll() {
        tasks.clear()
        habits.clear()
        settings = UserSettings(hasCompletedOnboarding = true)
        xp = 0
        totalPomodoros = 0
        plannerUsed = false
        prefs.edit().clear().apply()
        persistAll()
        notifyChanged()
    }

    fun loadDemoHabitsOnly() {
        if (habits.isNotEmpty() || tasks.isNotEmpty()) return
        seedDemoData()
    }

    // ------------------------------------------------------------- persistência

    private fun load() {
        tasks.clear()
        habits.clear()
        runCatching {
            JSONArray(prefs.getString(KEY_TASKS, "[]")).let { arr ->
                for (i in 0 until arr.length()) taskFromJson(arr.getJSONObject(i))?.let(tasks::add)
            }
        }
        runCatching {
            JSONArray(prefs.getString(KEY_HABITS, "[]")).let { arr ->
                for (i in 0 until arr.length()) habitFromJson(arr.getJSONObject(i))?.let(habits::add)
            }
        }
        runCatching {
            prefs.getString(KEY_SETTINGS, null)?.let { settings = settingsFromJson(JSONObject(it)) }
        }
        xp = prefs.getInt(KEY_XP, 0)
        totalPomodoros = prefs.getInt(KEY_SESSIONS, 0)
        plannerUsed = prefs.getBoolean(KEY_PLANNER, false)
    }

    private fun persistTasks() {
        prefs.edit().putString(KEY_TASKS, tasksJson().toString()).apply()
    }

    private fun persistHabits() {
        prefs.edit().putString(KEY_HABITS, habitsJson().toString()).apply()
    }

    private fun persistSettings() {
        prefs.edit().putString(KEY_SETTINGS, settingsToJson(settings).toString()).apply()
    }

    private fun persistAll() {
        persistTasks(); persistHabits(); persistSettings()
        prefs.edit().putInt(KEY_XP, xp).putInt(KEY_SESSIONS, totalPomodoros)
            .putBoolean(KEY_PLANNER, plannerUsed).apply()
    }

    // ---------------------------------------------------------------- JSON I/O

    private fun tasksJson(): JSONArray {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("notes", t.notes)
                put("category", t.category); put("tags", JSONArray(t.tags))
                put("priority", t.priority.name); put("date", t.date.toString())
                put("time", t.time?.toString() ?: JSONObject.NULL)
                put("estimate", t.estimateMinutes); put("completed", t.completed)
                put("completedAt", t.completedAt?.toString() ?: JSONObject.NULL)
                put("subtasks", JSONArray().apply {
                    t.subtasks.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id); put("title", s.title); put("done", s.done)
                        })
                    }
                })
                put("recurrence", t.recurrence.name)
                put("reminders", JSONArray(t.reminders))
                put("pomodoroCount", t.pomodoroCount)
                put("targetPomodoros", t.targetPomodoros)
                put("createdAt", t.createdAt.toString())
                put("updatedAt", t.updatedAt.toString())
            })
        }
        return arr
    }

    private fun habitsJson(): JSONArray {
        val arr = JSONArray()
        habits.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id); put("name", h.name); put("category", h.category)
                put("color", h.colorHex); put("frequency", h.frequency.name)
                put("streak", h.streak); put("bestStreak", h.bestStreak)
                put("dates", JSONArray(h.completedDates.map { it.toString() }))
                put("createdAt", h.createdAt.toString())
            })
        }
        return arr
    }

    private fun taskFromJson(o: JSONObject): Task? = runCatching {
        Task(
            id = o.getString("id"),
            title = o.getString("title"),
            notes = o.optString("notes", ""),
            category = o.optString("category", "Trabalho"),
            tags = mutableListOf<String>().apply {
                o.optJSONArray("tags")?.let { a -> for (i in 0 until a.length()) add(a.getString(i)) }
            },
            priority = Priority.from(o.optString("priority")),
            date = parseDate(o.optString("date")) ?: LocalDate.now(),
            time = parseTime(o),
            estimateMinutes = o.optInt("estimate", 30),
            completed = o.optBoolean("completed", false),
            completedAt = o.optString("completedAt").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            subtasks = mutableListOf<SubTask>().apply {
                o.optJSONArray("subtasks")?.let { a ->
                    for (i in 0 until a.length()) {
                        val s = a.getJSONObject(i)
                        add(SubTask(s.optString("id", UUID.randomUUID().toString()), s.optString("title"), s.optBoolean("done")))
                    }
                }
            },
            recurrence = Recurrence.from(o.optString("recurrence")),
            reminders = mutableListOf<Int>().apply {
                o.optJSONArray("reminders")?.let { a -> for (i in 0 until a.length()) add(a.getInt(i)) }
                if (isEmpty()) add(15)
            },
            pomodoroCount = o.optInt("pomodoroCount", 0),
            targetPomodoros = o.optInt("targetPomodoros", 1),
            createdAt = o.optString("createdAt").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now(),
            updatedAt = o.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now()
        )
    }.getOrNull()

    private fun habitFromJson(o: JSONObject): Habit? = runCatching {
        Habit(
            id = o.getString("id"),
            name = o.getString("name"),
            category = o.optString("category", "Saúde & Bem-estar"),
            colorHex = o.optString("color", "#06B6D4"),
            frequency = HabitFrequency.from(o.optString("frequency")),
            streak = o.optInt("streak", 0),
            bestStreak = o.optInt("bestStreak", 0),
            completedDates = mutableSetOf<LocalDate>().apply {
                o.optJSONArray("dates")?.let { a ->
                    for (i in 0 until a.length()) parseDate(a.getString(i))?.let(::add)
                }
            },
            createdAt = parseDate(o.optString("createdAt")) ?: LocalDate.now()
        )
    }.getOrNull()

    private fun settingsToJson(s: UserSettings) = JSONObject().apply {
        put("userName", s.userName); put("accent", s.accent.key); put("themeMode", s.themeMode.name)
        put("sound", s.soundEffects); put("work", s.workMinutes); put("shortBreak", s.shortBreakMinutes)
        put("longBreak", s.longBreakMinutes); put("dailyGoal", s.dailyGoal)
        put("notifications", s.notificationsEnabled); put("onboarded", s.hasCompletedOnboarding)
    }

    private fun settingsFromJson(o: JSONObject) = UserSettings(
        userName = o.optString("userName", ""),
        accent = Accent.from(o.optString("accent")),
        themeMode = ThemeMode.from(o.optString("themeMode")),
        soundEffects = o.optBoolean("sound", true),
        workMinutes = o.optInt("work", 25),
        shortBreakMinutes = o.optInt("shortBreak", 5),
        longBreakMinutes = o.optInt("longBreak", 15),
        dailyGoal = o.optInt("dailyGoal", 5),
        notificationsEnabled = o.optBoolean("notifications", true),
        hasCompletedOnboarding = o.optBoolean("onboarded", false)
    )

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank() || value == "null") return null
        return try {
            if (value.length > 10) LocalDateTime.parse(value).toLocalDate() else LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun parseTime(o: JSONObject): LocalTime? =
        if (o.isNull("time")) null else o.optString("time").takeIf { it.isNotBlank() && it != "null" }?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }

    // -------------------------------------------------------------- backup JSON

    fun exportJson(): String = JSONObject().apply {
        put("app", "TaskFlow"); put("version", 2)
        put("exportedAt", LocalDateTime.now().toString())
        put("tasks", tasksJson()); put("habits", habitsJson())
        put("settings", settingsToJson(settings)); put("xp", xp)
        put("sessions", totalPomodoros); put("plannerUsed", plannerUsed)
    }.toString(2)

    fun importJson(raw: String): Boolean = runCatching {
        val root = JSONObject(raw)
        val newTasks = mutableListOf<Task>()
        root.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) taskFromJson(arr.getJSONObject(i))?.let(newTasks::add)
        }
        val newHabits = mutableListOf<Habit>()
        root.optJSONArray("habits")?.let { arr ->
            for (i in 0 until arr.length()) habitFromJson(arr.getJSONObject(i))?.let(newHabits::add)
        }
        tasks.clear(); tasks.addAll(newTasks)
        habits.clear(); habits.addAll(newHabits)
        root.optJSONObject("settings")?.let { settings = settingsFromJson(it) }
        xp = root.optInt("xp", xp)
        totalPomodoros = root.optInt("sessions", totalPomodoros)
        plannerUsed = root.optBoolean("plannerUsed", plannerUsed)
        persistAll()
        notifyChanged()
        true
    }.getOrDefault(false)

    // --------------------------------------------------------------- utilidades

    fun duplicateTask(task: Task): Task {
        val copy = Task(
            id = UUID.randomUUID().toString(),
            title = task.title,
            notes = task.notes,
            category = task.category,
            tags = task.tags.toMutableList(),
            priority = task.priority,
            date = task.date,
            time = task.time,
            estimateMinutes = task.estimateMinutes,
            subtasks = task.subtasks.map { SubTask(UUID.randomUUID().toString(), it.title, false) }.toMutableList(),
            recurrence = task.recurrence,
            reminders = task.reminders.toMutableList(),
            targetPomodoros = task.targetPomodoros
        )
        tasks.add(0, copy)
        persistTasks()
        ReminderScheduler.schedule(TaskFlowApp.instance, copy)
        notifyChanged()
        return copy
    }

    /** Move tarefas de baixa/média prioridade de hoje para amanhã. */
    fun redistributeOverload(): Int {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        var moved = 0
        tasks.filter {
            !it.completed && it.date == today && (it.priority == Priority.BAIXA || it.priority == Priority.MEDIA)
        }.forEach {
            it.date = tomorrow
            it.updatedAt = LocalDateTime.now()
            ReminderScheduler.cancel(TaskFlowApp.instance, it.id)
            ReminderScheduler.schedule(TaskFlowApp.instance, it)
            moved++
        }
        if (moved > 0) {
            persistTasks()
            notifyChanged()
        }
        return moved
    }
}
