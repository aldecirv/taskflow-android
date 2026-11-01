package com.taskflow.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/** Chimes sintetizados — o app não carrega nenhum arquivo de áudio. */
object Tones {

    fun complete(enabled: Boolean) {
        if (enabled) play(listOf(587.33f to 0.25f, 880f to 0.45f), gapMs = 80)
    }

    fun levelUp(enabled: Boolean) {
        if (enabled) play(listOf(440f to 0.16f, 554.37f to 0.16f, 659.25f to 0.16f, 880f to 0.4f), gapMs = 60)
    }

    fun timerDone(enabled: Boolean) {
        if (enabled) play(listOf(523.25f to 0.5f, 392f to 0.5f, 261.63f to 0.9f), gapMs = 120)
    }

    private fun play(notes: List<Pair<Float, Float>>, gapMs: Int) {
        Thread {
            runCatching {
                val sampleRate = 44100
                val gap = sampleRate * gapMs / 1000
                var totalSamples = 0
                notes.forEach { (_, seconds) -> totalSamples += (seconds * sampleRate).toInt() + gap }
                val buffer = ShortArray(totalSamples)
                var offset = 0
                notes.forEach { (freq, seconds) ->
                    val count = (seconds * sampleRate).toInt()
                    for (i in 0 until count) {
                        val t = i.toDouble() / sampleRate
                        val envelope = Math.exp(-3.2 * t / seconds)
                        val sample = (Math.sin(2 * Math.PI * freq * t) * envelope * 0.28 * Short.MAX_VALUE).toInt()
                        buffer[offset + i] = sample.toShort()
                    }
                    offset += count + gap
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(buffer.size * 2)
                    .build()
                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep((totalSamples * 1000L / sampleRate) + 150)
                track.stop()
                track.release()
            }
        }.start()
    }
}
