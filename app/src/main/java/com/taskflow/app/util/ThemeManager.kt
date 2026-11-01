package com.taskflow.app.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.taskflow.app.data.Store
import com.taskflow.app.data.ThemeMode

object ThemeManager {

    fun apply(activity: Activity) {
        Store.init(activity)
        activity.theme.applyStyle(Store.settings.accent.overlay, true)
    }

    /** Aplica o modo claro/escuro/automático em toda a sessão. */
    fun applyNightMode(context: Context) {
        val mode = when (Store.settings.themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }


    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
