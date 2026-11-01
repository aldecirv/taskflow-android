package com.taskflow.app.voice

import android.content.Context

object VoiceHistory {
    private const val PREFS = "taskflow_voice"
    private const val KEY = "history"
    private const val MAX = 6

    fun recent(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }.take(MAX)
    }

    fun add(context: Context, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + recent(context)).distinct().take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, updated.joinToString("\n")).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
