package com.taskflow.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.taskflow.app.R
import com.taskflow.app.data.Priority
import com.taskflow.app.databinding.ActivityVoiceBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.util.ThemeManager
import java.time.LocalDate
import java.time.LocalTime

class VoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceBinding
    private var parsedTime: LocalTime? = null
    private var parsedDate: LocalDate = LocalDate.now()
    private var parsedPriority: Priority = Priority.MEDIA
    private var parsedEstimate: Int = 30
    private var hasResult = false

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            stopPulse()
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    parse(text)
                    return@registerForActivityResult
                }
            }
            binding.status.text = "Não consegui ouvir. Tente novamente ou digite a tarefa."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityVoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.btnPrimary.setOnClickListener {
            if (hasResult) createTask() else startListening()
        }
        binding.btnSecondary.setOnClickListener {
            if (hasResult) {
                startListening()
            } else {
                showManual()
            }
        }

        if (!isSpeechAvailable()) {
            binding.status.text = "Reconhecimento de voz indisponível neste aparelho. Digite sua tarefa."
            showManual()
        } else {
            binding.root.postDelayed({ startListening() }, 350)
        }
    }

    private fun isSpeechAvailable(): Boolean =
        packageManager.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            0
        ).isNotEmpty()

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua tarefa")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { speechLauncher.launch(intent) }.onFailure {
            binding.status.text = "Não foi possível abrir o microfone. Digite sua tarefa."
            showManual()
        }
        binding.status.text = "Ouvindo..."
        startPulse()
    }

    private fun startPulse() {
        binding.micPulse.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.pulse).apply { repeatCount = -1 }
        )
    }

    private fun stopPulse() {
        binding.micPulse.clearAnimation()
        binding.micPulse.scaleX = 1f
        binding.micPulse.scaleY = 1f
    }

    private fun showManual() {
        hasResult = true
        parsedDate = LocalDate.now()
        parsedTime = LocalTime.of(9, 0)
        binding.resultCard.isVisible = true
        binding.titleInput.setText("")
        binding.titleInput.requestFocus()
        binding.status.text = "Digite a tarefa e ajuste os detalhes."
        binding.btnPrimary.text = "Criar tarefa"
        binding.btnSecondary.text = "Limpar"
        renderMeta()
        binding.btnSecondary.setOnClickListener {
            binding.titleInput.setText("")
            parsedTime = null
            renderMeta()
        }
    }

    private fun parse(original: String) {
        hasResult = true
        val lower = original.lowercase()
        parsedDate = LocalDate.now()
        parsedTime = null
        parsedPriority = Priority.MEDIA
        parsedEstimate = 30

        when {
            lower.contains("depois de amanhã") -> parsedDate = LocalDate.now().plusDays(2)
            lower.contains("amanhã") -> parsedDate = LocalDate.now().plusDays(1)
            lower.contains("hoje") -> parsedDate = LocalDate.now()
            else -> {
                val weekdays = mapOf(
                    "segunda" to 1, "terça" to 2, "terca" to 2, "quarta" to 3,
                    "quinta" to 4, "sexta" to 5, "sábado" to 6, "sabado" to 6, "domingo" to 7
                )
                weekdays.entries.firstOrNull { lower.contains(it.key) }?.let { entry ->
                    var d = LocalDate.now()
                    while (d.dayOfWeek.value != entry.value || d == LocalDate.now()) d = d.plusDays(1)
                    parsedDate = d
                }
            }
        }

        val timeRegex = Regex("""(\d{1,2})(?::|h)(\d{2})?""")
        timeRegex.find(lower)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 0
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            var h = hour
            if ((lower.contains("tarde") || lower.contains("noite")) && h in 1..11) h += 12
            if (h in 0..23 && minute in 0..59) parsedTime = LocalTime.of(h, minute)
        }

        if (lower.contains("urgente")) parsedPriority = Priority.URGENTE
        else if (lower.contains("importante") || lower.contains("prioridade alta")) parsedPriority = Priority.ALTA
        else if (lower.contains("baixa prioridade") || lower.contains("quando der")) parsedPriority = Priority.BAIXA

        Regex("""(?:por|durante)?\s*(\d{1,3})\s*(?:min|minutos)""").find(lower)?.let {
            parsedEstimate = it.groupValues[1].toIntOrNull()?.coerceIn(5, 240) ?: 30
        }

        var title = original
        val cleanup = listOf(
            "criar tarefa", "adicionar tarefa", "nova tarefa", "me lembra de", "me lembre de",
            "lembrete de", "lembrete", "lembrar de", "por favor",
            "depois de amanhã", "amanhã", "hoje",
            "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira",
            "sábado", "domingo", "segunda", "terça", "quarta", "quinta", "sexta",
            "prioridade alta", "prioridade baixa", "urgente", "importante",
            "de manhã", "da manhã", "à tarde", "da tarde", "à noite", "da noite", "às", "as"
        )
        cleanup.forEach { phrase ->
            title = title.replace(Regex("""\b""" + Regex.escape(phrase) + """\b""", RegexOption.IGNORE_CASE), " ")
        }
        title = title.replace(timeRegex, " ")
        title = title.replace(Regex("""(?:por|durante)?\s*\d{1,3}\s*(?:min|minutos)\b""", RegexOption.IGNORE_CASE), " ")
        title = title.replace(Regex("""\s+"""), " ").trim().trim('.', ',', '!', '?', '-')

        binding.resultCard.isVisible = true
        binding.titleInput.setText(Dates.capitalize(title.ifBlank { original.trim() }))
        binding.titleInput.requestFocus()
        binding.titleInput.setSelection(binding.titleInput.text?.length ?: 0)
        binding.status.text = "Confira os detalhes antes de salvar."
        binding.btnPrimary.text = "Criar tarefa"
        binding.btnSecondary.text = "Falar de novo"
        binding.btnSecondary.setOnClickListener { startListening() }
        renderMeta()
    }

    private fun renderMeta() {
        val time = parsedTime?.let { Dates.timeLabel(it) } ?: "Dia todo"
        binding.parsedMeta.text = "${Dates.relative(parsedDate)} · $time · ${parsedPriority.label}"
    }

    private fun createTask() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            Toast.makeText(this, "Dê um nome à tarefa", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, TaskEditorActivity::class.java).apply {
            putExtra(TaskEditorActivity.EXTRA_TITLE, title)
            putExtra(TaskEditorActivity.EXTRA_DATE, parsedDate.toString())
            parsedTime?.let { putExtra(TaskEditorActivity.EXTRA_TIME, it.toString()) }
            putExtra(TaskEditorActivity.EXTRA_PRIORITY, parsedPriority.name)
            putExtra(TaskEditorActivity.EXTRA_ESTIMATE, parsedEstimate)
        }
        startActivity(intent)
        finish()
    }
}
