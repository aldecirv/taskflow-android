package com.taskflow.app

import android.app.Application
import com.taskflow.app.data.Store
import com.taskflow.app.util.Notifications
import com.taskflow.app.util.ThemeManager

class TaskFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)
        Store.init(this)
        ThemeManager.applyNightMode(this)
    }

    companion object {
        lateinit var instance: TaskFlowApp
            private set
    }
}
