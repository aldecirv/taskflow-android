package com.taskflow.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.taskflow.app.BuildConfig
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Accent
import com.taskflow.app.data.AiConfig
import com.taskflow.app.data.Store
import com.taskflow.app.data.ThemeMode
import com.taskflow.app.databinding.ActivitySettingsBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.util.ThemeManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var requestingNotifications = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Store.updateSettings { it.notificationsEnabled = granted }
            render()
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray()) }
                Toast.makeText(this, "Backup exportado", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Falha ao exportar", Toast.LENGTH_SHORT).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runCatching {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (Store.importJson(raw)) {
                    Toast.makeText(this, "Backup importado", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this, "Arquivo inválido", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, "Falha ao importar", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEditName.setOnClickListener { editName() }
        binding.rowTheme.setOnClickListener { pickTheme() }
        binding.rowWork.setOnClickListener { pickMinutes("Sessão de foco", Store.settings.workMinutes, 10, 90) { Store.updateSettings { s -> s.workMinutes = it } } }
        binding.rowShort.setOnClickListener { pickMinutes("Pausa curta", Store.settings.shortBreakMinutes, 1, 30) { Store.updateSettings { s -> s.shortBreakMinutes = it } } }
        binding.rowLong.setOnClickListener { pickMinutes("Pausa longa", Store.settings.longBreakMinutes, 5, 45) { Store.updateSettings { s -> s.longBreakMinutes = it } } }
        binding.rowGoal.setOnClickListener { pickMinutes("Meta diária de tarefas", Store.settings.dailyGoal, 1, 20) { Store.updateSettings { s -> s.dailyGoal = it } } }
        binding.rowExactAlarms.setOnClickListener { openExactAlarmSettings() }
        binding.rowExport.setOnClickListener {
            exportLauncher.launch("taskflow-backup-${java.time.LocalDate.now()}.json")
        }
        binding.rowImport.setOnClickListener { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        binding.rowDemo.setOnClickListener { loadDemo() }
        binding.rowReset.setOnClickListener { confirmReset() }
        binding.rowTour.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_REPLAY, true))
        }
        binding.rowAiKey.setOnClickListener { editAiKey() }
        binding.rowAiModel.setOnClickListener { editAiModel() }
        binding.rowAiHelp.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
            }
        }
        binding.rowGithub.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aldecirv/taskflow-android")))
            }
        }
        binding.versionValue.text = "TaskFlow ${BuildConfig.VERSION_NAME}"

        render()

        binding.switchSound.setOnCheckedChangeListener { _, checked ->
            Store.updateSettings { it.soundEffects = checked }
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            if (requestingNotifications) return@setOnCheckedChangeListener
            if (checked) {
                enableNotifications()
            } else {
                Store.updateSettings { it.notificationsEnabled = false }
                Store.tasks.forEach { com.taskflow.app.service.ReminderScheduler.cancel(this, it.id) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val settings = Store.settings
        val name = settings.userName.ifBlank { "Você" }
        binding.userName.text = name
        binding.avatar.text = initials(name)
        binding.userLevel.text = "Nível ${Store.level()} · ${Store.levelTitle()}"

        binding.themeValue.text = settings.themeMode.label
        binding.workValue.text = "${settings.workMinutes} min"
        binding.shortValue.text = "${settings.shortBreakMinutes} min"
        binding.longValue.text = "${settings.longBreakMinutes} min"
        binding.goalValue.text = "${settings.dailyGoal} ${if (settings.dailyGoal == 1) "tarefa" else "tarefas"}"

        binding.switchSound.isChecked = settings.soundEffects
        binding.switchNotifications.isChecked = settings.notificationsEnabled

        binding.exactValue.text = exactAlarmStatus()
        binding.aiKeyValue.text = if (AiConfig.isEnabled(this)) "Configurada" else "Não configurada"
        binding.aiModelValue.text = AiConfig.model(this)
        renderAccents()
    }

    private fun renderAccents() {
        binding.accentRow.removeAllViews()
        Accent.entries.forEach { accent ->
            val color = Color.parseColor(accent.colorHex)
            val selected = accent == Store.settings.accent
            val view = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (selected) setStroke(dp(3), ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
                }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginEnd = dp(12)
                }
                setOnClickListener {
                    if (accent != Store.settings.accent) {
                        Store.updateSettings { it.accent = accent }
                        recreate()
                    }
                }
            }
            binding.accentRow.addView(view)
        }
    }

    private fun exactAlarmStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "Sempre permitido"
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (manager.canScheduleExactAlarms()) "Permitido" else "Toque para permitir"
    }

    private fun pickTheme() {
        val modes = ThemeMode.entries
        val labels = modes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tema do app")
            .setSingleChoiceItems(labels, modes.indexOf(Store.settings.themeMode)) { dialog, which ->
                Store.updateSettings { it.themeMode = modes[which] }
                dialog.dismiss()
                ThemeManager.applyNightMode(this)
            }
            .show()
    }

    private fun pickMinutes(title: String, current: Int, min: Int, max: Int, onPicked: (Int) -> Unit) {
        val picker = NumberPicker(this).apply {
            this.minValue = min
            this.maxValue = max
            value = current.coerceIn(min, max)
            wrapSelectorWheel = false
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        container.addView(picker)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                onPicked(picker.value)
                render()
            }
            .show()
    }

    private fun editName() {
        val input = EditText(this).apply {
            setText(Store.settings.userName)
            hint = "Seu nome"
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val container = FrameLayout(this).apply { setPadding(dp(8), dp(8), dp(8), 0) }
        container.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Como podemos te chamar?")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) Store.updateSettings { it.userName = name }
                render()
            }
            .show()
    }

    private fun editAiKey() {
        val input = EditText(this).apply {
            setText(AiConfig.apiKey(this@SettingsActivity))
            hint = "AIza..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val container = FrameLayout(this).apply { setPadding(dp(8), dp(8), dp(8), 0) }
        container.addView(input)

        val builder = AlertDialog.Builder(this)
            .setTitle("Chave da API Gemini")
            .setMessage("Crie uma chave gratuita no Google AI Studio. Ela fica apenas neste aparelho.")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                AiConfig.setApiKey(this, input.text?.toString().orEmpty())
                render()
                Toast.makeText(this, "Chave salva", Toast.LENGTH_SHORT).show()
            }
        if (AiConfig.isEnabled(this)) {
            builder.setNeutralButton("Remover") { _, _ ->
                AiConfig.setApiKey(this, "")
                render()
                Toast.makeText(this, "Chave removida", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    private fun editAiModel() {
        val input = EditText(this).apply {
            setText(AiConfig.model(this@SettingsActivity))
            hint = AiConfig.DEFAULT_MODEL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val container = FrameLayout(this).apply { setPadding(dp(8), dp(8), dp(8), 0) }
        container.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Modelo do Gemini")
            .setMessage("Padrão: ${AiConfig.DEFAULT_MODEL}. Se o modelo não existir, o app tenta o fallback automaticamente.")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                AiConfig.setModel(this, input.text?.toString().orEmpty())
                render()
            }
            .show()
    }

    private fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestingNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            requestingNotifications = false
        }
        Store.updateSettings { it.notificationsEnabled = true }
        Store.tasks.filter { !it.completed }.forEach {
            com.taskflow.app.service.ReminderScheduler.schedule(this, it)
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "Nesta versão do Android os alarmes já são exatos.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName"))
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun loadDemo() {
        if (Store.tasks.isNotEmpty() || Store.habits.isNotEmpty()) {
            Toast.makeText(this, "Você já tem dados. Apague tudo antes de carregar o exemplo.", Toast.LENGTH_LONG).show()
            return
        }
        Store.seedDemoData()
        Toast.makeText(this, "Dados de exemplo carregados", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Apagar todos os dados?")
            .setMessage("Tarefas, hábitos, XP e ajustes serão removidos. Essa ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Apagar tudo") { _, _ ->
                Store.resetAll()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun initials(name: String): String =
        name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "TF" }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }
}
