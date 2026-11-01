package com.taskflow.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuração da IA. A chave fica em um arquivo separado e NUNCA entra no backup.
 */
object AiConfig {
    private const val PREFS = "taskflow_ai"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String = prefs(context).getString(KEY_API, "").orEmpty().trim()

    fun isEnabled(context: Context): Boolean = apiKey(context).isNotBlank()

    fun setApiKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_API, value.trim()).apply()
    }

    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }

    fun setModel(context: Context, value: String) {
        prefs(context).edit().putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply()
    }
}
