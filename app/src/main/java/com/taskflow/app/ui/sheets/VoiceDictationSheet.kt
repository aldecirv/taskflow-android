package com.taskflow.app.ui.sheets

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taskflow.app.R
import com.taskflow.app.data.Habit
import com.taskflow.app.data.HabitFrequency
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.SheetVoiceDictationBinding
import com.taskflow.app.ui.ProductEditorActivity
import com.taskflow.app.ui.TaskEditorActivity
import com.taskflow.app.util.Dates
import com.taskflow.app.util.ParsedVoiceTask
import com.taskflow.app.util.VoiceParser
import com.taskflow.app.voice.ParsedProduct
import com.taskflow.app.voice.VoiceHistory
import com.taskflow.app.voice.VoiceProductParser
import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID

class VoiceDictationSheet : BottomSheetDialogFragment() {

    private var _binding: SheetVoiceDictationBinding? = null
    private val binding get() = _binding!!

    private var speech: SpeechRecognizer? = null
    private var listening = false
    private var finalized = false
    private var transcript = ""
    private var parsedProduct: ParsedProduct? = null
    private var parsedTask: ParsedVoiceTask? = null
    private var parsedHabit: Pair<String, HabitFrequency>? = null

    private val tab: String get() = arguments?.getString(ARG_TAB) ?: "tasks"
    private val mode: String get() = arguments?.getString(ARG_MODE) ?: MODE_DICTATION
    private val requestKey: String? get() = arguments?.getString(ARG_REQUEST)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else showPermissionDenied()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetVoiceDictationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnClose.setOnClickListener { stopAndDismiss() }
        binding.btnCancel.setOnClickListener { stopAndDismiss() }
        binding.btnStop.setOnClickListener { stopListening(usePartial = true) }
        binding.btnConfirm.setOnClickListener { confirm() }
        binding.btnFallback.setOnClickListener { openKeyboardFallback() }

        renderHistory()
        ensurePermission()
    }

    private fun renderHistory() {
        val recent = VoiceHistory.recent(requireContext())
        binding.historyContainer.removeAllViews()
        binding.historyLabel.isVisible = recent.isNotEmpty()
        binding.historyScroll.isVisible = recent.isNotEmpty()
        recent.take(4).forEach { text ->
            val chip = android.widget.TextView(requireContext()).apply {
                this.text = text.take(42)
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(ContextCompat.getColor(requireContext(), R.color.surface_alt))
                }
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    stopListening(usePartial = false)
                    transcript = text
                    binding.transcript.text = text
                    parse(transcript)
                }
            }
            binding.historyContainer.addView(chip)
        }
    }

    // ------------------------------------------------------------------ permissão

    private fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
            return
        }
        val prefs = requireContext().getSharedPreferences("taskflow_voice_ui", Context.MODE_PRIVATE)
        if (prefs.getBoolean("rationale_shown", false)) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            prefs.edit().putBoolean("rationale_shown", true).apply()
            AlertDialog.Builder(requireContext())
                .setTitle("Usar o microfone?")
                .setMessage("Usamos o microfone para você ditar tarefas, produtos e hábitos sem abrir o teclado. O áudio é processado pelo serviço de voz do aparelho.")
                .setNegativeButton("Agora não") { _, _ -> showPermissionDenied() }
                .setPositiveButton("Permitir") { _, _ -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                .show()
        }
    }

    private fun showPermissionDenied() {
        if (_binding == null) return
        binding.waveform.stop()
        binding.status.text = "Permissão de microfone negada"
        binding.errorText.isVisible = true
        binding.errorText.text = "Você pode continuar digitando normalmente."
        binding.btnFallback.isVisible = true
        binding.btnFallback.text = "Usar o teclado"
        binding.btnStop.isEnabled = false
    }

    // -------------------------------------------------------------------- speech

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            binding.status.text = "Reconhecimento de voz indisponível"
            binding.errorText.isVisible = true
            binding.errorText.text = "Este aparelho não tem um serviço de voz. Use o teclado."
            binding.btnFallback.isVisible = true
            return
        }
        finalized = false
        transcript = ""
        binding.transcript.text = "Fale naturalmente. Ex.: “arroz 2 quilos vinte e nove reais e noventa no atacadão”."
        binding.previewContainer.isVisible = false
        binding.errorText.isVisible = false
        binding.btnConfirm.isEnabled = false
        binding.btnFallback.isVisible = false
        binding.btnStop.isEnabled = true
        binding.status.text = "Ouvindo..."

        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
            setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Store.settings.dictationLanguage.ifBlank { "pt-BR" })
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Store.settings.dictationLanguage.ifBlank { "pt-BR" })
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().packageName)
        }
        runCatching { speech?.startListening(intent) }.onFailure { noMatch("Não consegui abrir o microfone. Toque para digitar.") }
        listening = true
        binding.waveform.start()
        haptic(HapticFeedbackConstants.CONFIRM)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (_binding == null) return
            binding.status.text = "Pode falar..."
        }

        override fun onBeginningOfSpeech() {
            if (_binding == null) return
            binding.status.text = "Ouvindo..."
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (_binding == null) return
            binding.waveform.setRms(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (_binding == null) return
            binding.status.text = "Processando..."
            binding.waveform.stop()
        }

        override fun onError(error: Int) {
            if (_binding == null) return
            listening = false
            binding.waveform.stop()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (transcript.isNotBlank()) finishTranscript(transcript)
                    else noMatch("Não entendi. Toque para digitar.")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> showPermissionDenied()
                else -> noMatch("Não entendi. Toque para digitar.")
            }
        }

        override fun onResults(results: Bundle?) {
            if (_binding == null) return
            listening = false
            binding.waveform.stop()
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) {
                if (transcript.isNotBlank()) finishTranscript(transcript) else noMatch("Não entendi. Toque para digitar.")
            } else {
                finishTranscript(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (_binding == null) return
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                transcript = text
                binding.transcript.text = text
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun stopListening(usePartial: Boolean) {
        if (!listening) return
        listening = false
        binding.waveform.stop()
        speech?.stopListening()
        haptic(HapticFeedbackConstants.KEYBOARD_TAP)
        binding.status.text = "Processando..."
        binding.root.postDelayed({
            if (!finalized && usePartial) {
                if (transcript.isNotBlank()) finishTranscript(transcript) else noMatch("Não entendi. Toque para digitar.")
            }
        }, 900)
    }

    private fun finishTranscript(text: String) {
        if (finalized || _binding == null) return
        finalized = true
        transcript = text.trim()
        binding.transcript.text = transcript
        VoiceHistory.add(requireContext(), transcript)
        renderHistory()
        parse(transcript)
    }

    private fun noMatch(message: String) {
        if (_binding == null) return
        binding.status.text = message
        binding.errorText.isVisible = true
        binding.errorText.text = message
        binding.btnFallback.isVisible = true
        binding.btnFallback.text = "Não entendi — prefiro digitar"
        binding.btnConfirm.isEnabled = false
    }

    // --------------------------------------------------------------------- parse

    private fun parse(text: String) {
        val command = normalize(text)
        if (command == "cancelar" || command == "cancele" || command == "fechar") {
            stopAndDismiss()
            return
        }
        if (command == "confirmar" || command == "confirma" || command == "salvar") {
            if (binding.btnConfirm.isEnabled) confirm() else noMatch("Diga o que deseja criar antes de confirmar.")
            return
        }
        if (command.startsWith("corrigir nome")) {
            confirm(openEditor = true)
            return
        }

        parsedProduct = null
        parsedTask = null
        parsedHabit = null
        binding.previewContainer.removeAllViews()
        binding.errorText.isVisible = false

        if (mode == MODE_FIELD) {
            binding.previewContainer.isVisible = true
            addPreviewLine("Texto reconhecido", text)
            binding.btnConfirm.isEnabled = text.isNotBlank()
            binding.status.text = "Confirme para inserir no campo"
            return
        }

        when (tab) {
            "products" -> {
                val product = VoiceProductParser.parse(text)
                parsedProduct = product
                showProductPreview(product)
            }
            "habits" -> {
                val habit = parseHabit(text)
                parsedHabit = habit
                showHabitPreview(habit)
            }
            else -> {
                val task = VoiceParser.parseLocal(text)
                parsedTask = task
                showTaskPreview(task)
            }
        }
        binding.btnConfirm.isEnabled = true
        binding.status.text = "Confira e confirme"
    }

    private fun showProductPreview(product: ParsedProduct) {
        binding.previewContainer.isVisible = true
        addPreviewLine("Nome", product.name)
        addPreviewLine("Quantidade", "${com.taskflow.app.data.Purchase.formatQuantity(product.quantity)} ${product.unit}")
        addPreviewLine(
            "Preço",
            product.priceCents?.let { com.taskflow.app.util.Money.format(it) } ?: "a informar no formulário"
        )
        if (product.place.isNotBlank()) addPreviewLine("Local", product.place)
    }

    private fun showTaskPreview(task: ParsedVoiceTask) {
        binding.previewContainer.isVisible = true
        addPreviewLine("Tarefa", task.title)
        addPreviewLine("Quando", "${Dates.relative(task.date)} · ${Dates.timeLabel(task.time)}")
        addPreviewLine("Prioridade", task.priority.label)
        addPreviewLine("Categoria", task.category)
    }

    private fun showHabitPreview(habit: Pair<String, HabitFrequency>) {
        binding.previewContainer.isVisible = true
        addPreviewLine("Hábito", habit.first)
        addPreviewLine("Frequência", habit.second.label)
    }

    private fun addPreviewLine(label: String, value: String) {
        val row = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        row.addView(android.widget.TextView(requireContext()).apply {
            text = "$label: "
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        })
        row.addView(android.widget.TextView(requireContext()).apply {
            text = value
            textSize = 12.5f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        })
        binding.previewContainer.addView(row)
    }

    private fun parseHabit(text: String): Pair<String, HabitFrequency> {
        val lower = text.lowercase()
        val frequency = when {
            lower.contains("dias úteis") || lower.contains("dias uteis") || lower.contains("segunda a sexta") ->
                HabitFrequency.WEEKDAYS
            lower.contains("3x") || lower.contains("três vezes") || lower.contains("tres vezes") ->
                HabitFrequency.THREE_WEEK
            else -> HabitFrequency.DAILY
        }
        var name = text
        val phrases = listOf(
            "criar hábito", "criar habito", "novo hábito", "novo habito", "hábito de", "habito de",
            "todo dia", "todos os dias", "diariamente", "dias úteis", "dias uteis", "segunda a sexta",
            "3x por semana", "três vezes por semana", "tres vezes por semana", "por favor"
        )
        phrases.forEach {
            name = name.replace(Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE), " ")
        }
        name = name.replace(Regex("""\b(?:às|as)\s+\d{1,2}(?::\d{2})?h?\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim().trim('.', ',', '-', ':')
        if (name.isBlank()) name = text.trim()
        return Dates.capitalize(name) to frequency
    }

    // ------------------------------------------------------------------ confirmar

    private fun confirm(openEditor: Boolean = false) {
        if (mode == MODE_FIELD) {
            val key = requestKey ?: return
            setFragmentResult(key, Bundle().apply { putString("text", transcript) })
            dismiss()
            return
        }

        when (tab) {
            "products" -> {
                val product = parsedProduct ?: return
                startActivity(
                    ProductEditorActivity.voiceIntent(
                        requireContext(),
                        product.name,
                        product.quantity,
                        product.unit,
                        product.priceCents,
                        product.place
                    )
                )
                dismiss()
            }
            "habits" -> {
                val (name, frequency) = parsedHabit ?: return
                Store.addHabit(
                    Habit(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        category = "Saúde & Bem-estar",
                        frequency = frequency
                    )
                )
                Toast.makeText(requireContext(), "Hábito \"$name\" criado", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            else -> {
                val task = parsedTask ?: return
                val intent = Intent(requireContext(), TaskEditorActivity::class.java).apply {
                    putExtra(TaskEditorActivity.EXTRA_TITLE, task.title)
                    putExtra(TaskEditorActivity.EXTRA_DATE, task.date.toString())
                    task.time?.let { putExtra(TaskEditorActivity.EXTRA_TIME, it.toString()) }
                    putExtra(TaskEditorActivity.EXTRA_PRIORITY, task.priority.name)
                    putExtra(TaskEditorActivity.EXTRA_CATEGORY, task.category)
                    putExtra(TaskEditorActivity.EXTRA_ESTIMATE, task.estimatedMinutes)
                }
                startActivity(intent)
                dismiss()
            }
        }
    }

    private fun openKeyboardFallback() {
        if (mode == MODE_FIELD) {
            dismiss()
            return
        }
        when (tab) {
            "products" -> startActivity(ProductEditorActivity.intent(requireContext(), ProductEditorActivity.Mode.MANUAL))
            "habits" -> {
                dismiss()
                Toast.makeText(requireContext(), "Toque em + na aba Hábitos para digitar", Toast.LENGTH_LONG).show()
                return
            }
            else -> startActivity(TaskEditorActivity.startNewIntent(requireContext(), LocalDate.now()))
        }
        dismiss()
    }

    private fun stopAndDismiss() {
        finalized = true
        listening = false
        binding.waveform.stop()
        speech?.destroy()
        speech = null
        dismiss()
    }

    private fun haptic(feedback: Int) {
        binding.root.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) feedback else HapticFeedbackConstants.LONG_PRESS
        )
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        listening = false
        speech?.destroy()
        speech = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_TAB = "tab"
        const val ARG_MODE = "mode"
        const val ARG_REQUEST = "request"
        const val MODE_DICTATION = "dictation"
        const val MODE_FIELD = "field"

        fun newInstance(tab: String, mode: String = MODE_DICTATION, requestKey: String? = null): VoiceDictationSheet =
            VoiceDictationSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TAB, tab)
                    putString(ARG_MODE, mode)
                    if (requestKey != null) putString(ARG_REQUEST, requestKey)
                }
            }
    }
}
