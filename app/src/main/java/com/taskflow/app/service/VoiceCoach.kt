package com.taskflow.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.taskflow.app.data.WorkoutMode
import java.util.Locale

/**
 * Avisos de voz em português a cada quilômetro completado.
 *
 * Usa o TTS nativo do Android, então funciona com a tela bloqueada e com fones
 * Bluetooth (o áudio sai pelo fone pareado, como qualquer reprodução do sistema).
 */
class VoiceCoach(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            tts = null
            return
        }
        val language = tts?.setLanguage(Locale("pt", "BR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
        pending?.let { if (ready) speakNow(it) }
        pending = null
    }

    /** Anuncia o quilômetro recém-completado. */
    fun announce(km: Int, mode: WorkoutMode, paceSeconds: Int) {
        val text = messageFor(km, mode, paceSeconds)
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tf-km-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }

    companion object {
        fun messageFor(km: Int, mode: WorkoutMode, paceSeconds: Int): String {
            val base = when (km) {
                1 -> "Parabéns! Você completou seu primeiro quilômetro. Continue assim!"
                2 -> "Excelente! Dois quilômetros concluídos. Você está indo muito bem!"
                3 -> "Muito bem! Mais um quilômetro conquistado. Continue no seu ritmo!"
                4 -> "Parabéns! Você está superando seus limites!"
                5 -> "Incrível! Cinco quilômetros concluídos. Você conseguiu!"
                else -> "$km quilômetros concluídos! Continue firme, você está indo muito bem!"
            }
            val ritmo = if (paceSeconds > 0) {
                val minutos = paceSeconds / 60
                val segundos = paceSeconds % 60
                " Seu ritmo é de $minutos minutos e $segundos segundos por quilômetro."
            } else {
                ""
            }
            val dica = if (mode == WorkoutMode.RUN) {
                " Mantenha a respiração controlada."
            } else {
                " Mantenha a postura e o passo leve."
            }
            return base + ritmo + dica
        }
    }
}
