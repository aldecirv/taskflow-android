package com.taskflow.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.taskflow.app.data.Store

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val taskId = intent.getStringExtra("task_id") ?: return
        val task = Store.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.completed) return
        val service = Intent(context, TaskAlarmService::class.java).apply {
            action = TaskAlarmService.ACTION_START
            putExtra("task_id", taskId)
            putExtra("sound", task.alertSound)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service)
        else context.startService(service)
    }
}
