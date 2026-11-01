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
import androidx.lifecycle.lifecycleScope
import com.taskflow.app.R
import com.taskflow.app.data.AiConfig
import com.taskflow.app.data.Priority
import com.taskflow.app.databinding.ActivityVoiceBinding
import com.taskflow.app.net.GeminiClient
import com.taskflow.app.util.Dates
import com.taskflow.app.util.ParsedVoiceTask
import com.taskflow.app.util.ThemeManager
import com.taskflow.app.util.VoiceParser
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class VoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceBinding

    private var parsedDate: LocalDate = LocalDate.now()
    private var parsedTime: LocalTime? = null
    private var parsedPriority: Priority = Priority.MEDIA
    private var parsedCategory: String = "Pessoal"
    private var parsedEstimate: Int = 30
    private var hasResult = false

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            stopPulse()
            binding.aiProgress.isVisible = false
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    handleTranscript(text)
                    return@registerForActivityResult
                }
            }
            listeningFailed()
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

        binding.aiBadge.text = if (AiConfig.isEnabled(this)) {
            "IA Gemini · ${AiConfig.model(this)}"
        } else {
            "IA local (offline)"
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.btnPrimary.setOnClickListener {
            if (hasResult) createTask() else startListening()
        }
        binding.btnSecondary.setOnClickListener {
            if (hasResult) startListening() else showManual()
        }

        if (!isSpeechAvailable()) {
            binding.status.text = "Reconhecimento de voz indisponível. Digite sua tarefa."
            showManual()
        } else {
            binding.root.postDelayed({ startListening() }, 300)
        }
    }

    private fun isSpeechAvailable(): Boolean =
        packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua tarefa")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        hasResult = false
        binding.resultCard.isVisible = false
        binding.btnPrimary.isEnabled = false
        binding.btnPrimary.text = "Ouvindo..."
        binding.btnSecondary.text = "Digitar manualmente"
        binding.status.text = "Ouvindo... fale naturalmente."
        startPulse()
        runCatching { speechLauncher.launch(intent) }.onFailure {
            stopPulse()
            listeningFailed()
        }
    }

    private fun listeningFailed() {
        hasResult = false
        binding.btnPrimary.isEnabled = true
        binding.btnPrimary.text = "Falar de novo"
        binding.resultCard.isVisible = false
        binding.status.text = "Não consegui ouvir. Tente novamente ou digite a tarefa."
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

    /**
     * Com chave configurada, o Gemini transforma a fala em tarefa estruturada.
     * Sem chave (ou em caso de erro), cai no interpretador local offline.
     */
    private fun handleTranscript(text: String) {
        if (!AiConfig.isEnabled(this)) {
            showParsed(VoiceParser.parseLocal(text))
            return
        }

        binding.aiProgress.isVisible = true
        binding.btnPrimary.isEnabled = false
        binding.status.text = "Interpretando com IA..."
        lifecycleScope.launch {
            val result = GeminiClient.parseVoice(this@VoiceActivity, text, LocalDate.now())
            if (isFinishing || isDestroyed) return@launch
            binding.aiProgress.isVisible = false
            result.onSuccess { showParsed(it) }
                .onFailure { error ->
                    binding.aiBadge.text = "IA local (Gemini indisponível)"
                    Toast.makeText(
                        this@VoiceActivity,
                        "IA indisponível: ${error.message}. Usei a interpretação local.",
                        Toast.LENGTH_LONG
                    ).show()
                    showParsed(VoiceParser.parseLocal(text))
                }
        }
    }

    private fun showParsed(parsed: ParsedVoiceTask) {
        hasResult = true
        parsedDate = parsed.date
        parsedTime = parsed.time
        parsedPriority = parsed.priority
        parsedCategory = parsed.category
        parsedEstimate = parsed.estimatedMinutes

        binding.resultCard.isVisible = true
        binding.titleInput.setText(parsed.title)
        binding.titleInput.requestFocus()
        binding.titleInput.setSelection(binding.titleInput.text?.length ?: 0)
        binding.status.text = "Confira os detalhes antes de salvar."
        binding.btnPrimary.isEnabled = true
        binding.btnPrimary.text = "Criar tarefa"
        binding.btnSecondary.text = "Falar de novo"
        binding.btnSecondary.setOnClickListener { startListening() }
        renderMeta()
    }

    private fun showManual() {
        hasResult = true
        parsedDate = LocalDate.now()
        parsedTime = null
        parsedPriority = Priority.MEDIA
        parsedCategory = "Pessoal"
        parsedEstimate = 30
        binding.resultCard.isVisible = true
        binding.titleInput.setText("")
        binding.titleInput.requestFocus()
        binding.status.text = "Digite a tarefa e ajuste os detalhes."
        binding.btnPrimary.isEnabled = true
        binding.btnPrimary.text = "Criar tarefa"
        binding.btnSecondary.text = "Limpar"
        binding.btnSecondary.setOnClickListener {
            binding.titleInput.setText("")
            parsedTime = null
            renderMeta()
        }
        renderMeta()
    }

    private fun renderMeta() {
        val time = parsedTime?.let { Dates.timeLabel(it) } ?: "Dia todo"
        binding.parsedMeta.text =
            "${Dates.relative(parsedDate)} · $time · ${parsedPriority.label} · $parsedCategory"
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
            putExtra(TaskEditorActivity.EXTRA_CATEGORY, parsedCategory)
            putExtra(TaskEditorActivity.EXTRA_ESTIMATE, parsedEstimate)
        }
        startActivity(intent)
        finish()
    }
}
