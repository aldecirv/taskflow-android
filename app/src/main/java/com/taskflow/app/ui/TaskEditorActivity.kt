package com.taskflow.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.taskflow.app.R
import com.taskflow.app.data.Categories
import com.taskflow.app.data.Priority
import com.taskflow.app.data.Recurrence
import com.taskflow.app.data.Store
import com.taskflow.app.data.SubTask
import com.taskflow.app.data.Task
import com.taskflow.app.databinding.ActivityTaskEditorBinding
import com.taskflow.app.databinding.ItemSubtaskEditBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.ui.sheets.VoiceDictationSheet
import com.taskflow.app.util.ThemeManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class TaskEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskEditorBinding
    private var editing: Task? = null

    private var date: LocalDate = LocalDate.now()
    private var time: LocalTime? = null
    private var category: String = Categories.all.first().name
    private var priority: Priority = Priority.MEDIA
    private var recurrence: Recurrence = Recurrence.NONE
    private val reminders = mutableSetOf<Int>()
    private var targetPomodoros = 1
    private var estimate = 30

    private val subtaskRows = mutableListOf<SubtaskRow>()

    private data class SubtaskRow(val binding: ItemSubtaskEditBinding, var done: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityTaskEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        editing = taskId?.let { id -> Store.tasks.firstOrNull { it.id == id } }

        binding.btnClose.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnSaveBottom.setOnClickListener { save() }
        binding.btnDelete.visibility = if (editing != null) View.VISIBLE else View.GONE
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.screenTitle.text = if (editing != null) "Editar tarefa" else "Nova tarefa"

        setupVoiceField()
        setupCategories()
        setupPriority()
        setupDateAndTime()
        setupRecurrence()
        setupReminders()
        setupPomodoros()
        binding.allDaySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                time = null
                renderTime()
            } else if (time == null) {
                time = LocalTime.of(9, 0)
                renderTime()
            }
        }
        binding.btnAddSubtask.setOnClickListener { addSubtaskRow(null) }

        fillFromTask()
    }

    // ------------------------------------------------------------------ setup

    private fun setupVoiceField() {
        supportFragmentManager.setFragmentResultListener("voice_task_title", this) { _, bundle ->
            val text = bundle.getString("text").orEmpty().trim()
            if (text.isNotBlank()) {
                binding.titleInput.setText(text)
                binding.titleInput.setSelection(text.length)
            }
        }
        binding.titleLayout.setEndIconOnClickListener { openVoiceTitle() }
        if (Store.settings.voicePreferred) {
            binding.titleInput.showSoftInputOnFocus = false
            binding.titleInput.setOnClickListener { openVoiceTitle() }
            binding.titleInput.setOnLongClickListener {
                binding.titleInput.showSoftInputOnFocus = true
                binding.titleInput.setOnClickListener(null)
                android.widget.Toast.makeText(this, "Teclado ativado para este campo", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun openVoiceTitle() {
        VoiceDictationSheet.newInstance("tasks", VoiceDictationSheet.MODE_FIELD, "voice_task_title")
            .show(supportFragmentManager, "voice_task_title")
    }

    private fun setupCategories() {
        Categories.all.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat.name
                isCheckable = true
                isClickable = true
                textSize = 12f
                chipMinHeight = dp(38).toFloat()
                chipStrokeWidth = 0f
                chipStartPadding = dp(6).toFloat()
                chipEndPadding = dp(6).toFloat()
                isChecked = cat.name == category
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        category = cat.name
                        updateCategoryChips()
                    }
                }
            }
            binding.categoryGroup.addView(chip)
        }
        updateCategoryChips()
    }

    private fun updateCategoryChips() {
        for (i in 0 until binding.categoryGroup.childCount) {
            val chip = binding.categoryGroup.getChildAt(i) as Chip
            val cat = Categories.all[i]
            val color = Color.parseColor(cat.colorHex)
            chip.isChecked = cat.name == category
            chip.chipBackgroundColor = ColorStateList.valueOf(
                if (chip.isChecked) color else ContextCompatColor.surfaceAlt(this)
            )
            chip.setTextColor(if (chip.isChecked) Color.WHITE else ContextCompatColor.textSecondary(this))
        }
    }

    private fun setupPriority() {
        binding.priorityGroup.removeAllViews()
        Priority.entries.forEach { p ->
            val view = TextView(this).apply {
                text = p.label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dp(11), 0, dp(11))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    priority = p
                    updatePriority()
                }
            }
            binding.priorityGroup.addView(view)
        }
        updatePriority()
    }

    private fun updatePriority() {
        for (i in 0 until binding.priorityGroup.childCount) {
            val view = binding.priorityGroup.getChildAt(i) as TextView
            val p = Priority.entries[i]
            val active = p == priority
            view.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (active) Color.parseColor(p.colorHex) else ContextCompatColor.surfaceAlt(this@TaskEditorActivity))
            }
            view.setTextColor(if (active) Color.WHITE else ContextCompatColor.textSecondary(this))
            view.typeface = android.graphics.Typeface.create(
                if (active) "sans-serif-medium" else "sans-serif",
                android.graphics.Typeface.NORMAL
            )
        }
    }

    private fun setupDateAndTime() {
        binding.dateField.setOnClickListener { openDatePicker() }
        binding.timeField.setOnClickListener { openTimePicker() }
    }

    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Escolher data")
            .setSelection(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            renderDate()
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    private fun openTimePicker() {
        val current = time ?: LocalTime.of(9, 0)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText("Escolher horário")
            .build()
        picker.addOnPositiveButtonClickListener {
            time = LocalTime.of(picker.hour, picker.minute)
            binding.allDaySwitch.isChecked = false
            renderTime()
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    private fun setupEstimate() {
        val options = (listOf(15, 30, 45, 60, 90, 120) + estimate).distinct().sorted()
        binding.estimateGroup.removeAllViews()
        options.forEach { minutes ->
            val chip = Chip(this).apply {
                text = Dates.durationLabel(minutes)
                isCheckable = true
                textSize = 12f
                chipMinHeight = dp(38).toFloat()
                chipStrokeWidth = 0f
                isChecked = minutes == estimate
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        estimate = minutes
                        binding.estimateValue.text = "$estimate min"
                    }
                }
            }
            binding.estimateGroup.addView(chip)
        }
    }

    private fun setupRecurrence() {
        binding.recurrenceValue.text = recurrence.label
        binding.recurrenceField.setOnClickListener {
            val labels = Recurrence.entries.map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Repetição")
                .setSingleChoiceItems(labels, Recurrence.entries.indexOf(recurrence)) { dialog, which ->
                    recurrence = Recurrence.entries[which]
                    binding.recurrenceValue.text = recurrence.label
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupReminders() {
        binding.reminderGroup.removeAllViews()
        val options = listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h", 1440 to "1 dia")
        options.forEach { (minutes, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                textSize = 11.5f
                chipMinHeight = dp(36).toFloat()
                isChecked = reminders.contains(minutes)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) reminders.add(minutes) else reminders.remove(minutes)
                }
            }
            binding.reminderGroup.addView(chip)
        }
    }

    private fun setupPomodoros() {
        binding.btnPomodoroPlus.setOnClickListener {
            if (targetPomodoros < 10) {
                targetPomodoros++
                renderPomodoros()
            }
        }
        binding.btnPomodoroMinus.setOnClickListener {
            if (targetPomodoros > 0) {
                targetPomodoros--
                renderPomodoros()
            }
        }
    }

    private fun renderPomodoros() {
        binding.pomodoroValue.text = targetPomodoros.toString()
        binding.btnPomodoroMinus.alpha = if (targetPomodoros == 0) 0.3f else 1f
    }

    // ------------------------------------------------------------------- fill

    private fun fillFromTask() {
        val task = editing
        if (task != null) {
            binding.titleInput.setText(task.title)
            binding.notesInput.setText(task.notes)
            category = task.category
            priority = task.priority
            date = task.date
            time = task.time
            estimate = task.estimateMinutes
            recurrence = task.recurrence
            reminders.clear()
            reminders.addAll(task.reminders)
            targetPomodoros = task.targetPomodoros
            task.subtasks.forEach { addSubtaskRow(it) }
            binding.recurrenceValue.text = recurrence.label
            updateCategoryChips()
            updatePriority()
            setupReminders()
        } else {
            val extraDate = intent.getStringExtra(EXTRA_DATE)
            if (extraDate != null) runCatching { date = LocalDate.parse(extraDate) }
            intent.getStringExtra(EXTRA_TITLE)?.let { binding.titleInput.setText(it) }
            intent.getStringExtra(EXTRA_TIME)?.let { raw ->
                runCatching { time = LocalTime.parse(raw) }
            }
            intent.getStringExtra(EXTRA_PRIORITY)?.let { priority = Priority.from(it) }
            intent.getStringExtra(EXTRA_CATEGORY)?.let { raw ->
                Categories.all.firstOrNull { it.name == raw }?.let { category = it.name }
            }
            estimate = intent.getIntExtra(EXTRA_ESTIMATE, estimate)
            updatePriority()
            updateCategoryChips()
        }
        setupEstimate()
        binding.estimateValue.text = "$estimate min"
        renderDate()
        renderTime()
        renderPomodoros()
        binding.allDaySwitch.isChecked = time == null
    }

    private fun renderDate() {
        binding.dateValue.text = Dates.relative(date)
    }

    private fun renderTime() {
        binding.timeValue.text = time?.let { Dates.timeLabel(it) } ?: "Dia todo"
        binding.timeField.alpha = if (time == null) 0.55f else 1f
    }

    // --------------------------------------------------------------- subtasks

    private fun addSubtaskRow(source: SubTask?) {
        val rowBinding = ItemSubtaskEditBinding.inflate(layoutInflater, binding.subtaskContainer, false)
        val row = SubtaskRow(rowBinding, source?.done ?: false)
        if (source != null) {
            rowBinding.subtaskTitle.setText(source.title)
        }
        rowBinding.subtaskCheck.isChecked = row.done
        rowBinding.subtaskCheck.setOnCheckedChangeListener { _, checked -> row.done = checked }
        rowBinding.btnRemoveSubtask.setOnClickListener {
            binding.subtaskContainer.removeView(rowBinding.root)
            subtaskRows.remove(row)
        }
        subtaskRows.add(row)
        binding.subtaskContainer.addView(rowBinding.root)
        if (source == null) rowBinding.subtaskTitle.requestFocus()
    }

    // ------------------------------------------------------------------- save

    private fun save() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            binding.titleInput.error = "Informe um título"
            binding.titleInput.requestFocus()
            return
        }

        val subtasks = subtaskRows.mapNotNull { row ->
            val text = row.binding.subtaskTitle.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) null else SubTask(
                id = UUID.randomUUID().toString(),
                title = text,
                done = row.done
            )
        }.toMutableList()

        val base = editing
        val task = if (base != null) {
            base.copy(
                title = title,
                notes = binding.notesInput.text?.toString()?.trim().orEmpty(),
                category = category,
                priority = priority,
                date = date,
                time = if (binding.allDaySwitch.isChecked) null else time,
                estimateMinutes = estimate,
                subtasks = subtasks,
                recurrence = recurrence,
                reminders = reminders.sorted().toMutableList(),
                targetPomodoros = targetPomodoros
            )
        } else {
            Task(
                id = UUID.randomUUID().toString(),
                title = title,
                notes = binding.notesInput.text?.toString()?.trim().orEmpty(),
                category = category,
                priority = priority,
                date = date,
                time = if (binding.allDaySwitch.isChecked) null else time,
                estimateMinutes = estimate,
                subtasks = subtasks,
                recurrence = recurrence,
                reminders = reminders.sorted().toMutableList(),
                targetPomodoros = targetPomodoros
            )
        }
        Store.saveTask(task, isNew = base == null)
        finish()
    }

    private fun confirmDelete() {
        val task = editing ?: return
        AlertDialog.Builder(this)
            .setTitle("Excluir tarefa?")
            .setMessage(task.title)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                Store.deleteTask(task.id)
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME = "time"
        const val EXTRA_PRIORITY = "priority"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_ESTIMATE = "estimate"

        fun startNewIntent(context: Context, date: LocalDate): Intent =
            Intent(context, TaskEditorActivity::class.java).putExtra(EXTRA_DATE, date.toString())

        fun startNew(context: Context, date: LocalDate) {
            context.startActivity(startNewIntent(context, date))
        }

        fun startEdit(context: Context, taskId: String) {
            context.startActivity(
                Intent(context, TaskEditorActivity::class.java)
                    .putExtra(EXTRA_TASK_ID, taskId)
            )
        }
    }
}

/** Pequenos atalhos de cor do tema para os chips. */
private object ContextCompatColor {
    fun surfaceAlt(context: Context): Int = context.getColor(R.color.surface_alt)
    fun textSecondary(context: Context): Int = context.getColor(R.color.text_secondary)
}
