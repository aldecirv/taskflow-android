package com.taskflow.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.taskflow.app.data.Store

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Store.tasks.filter { !it.completed }.forEach { ReminderScheduler.schedule(context, it) }
    }
}
