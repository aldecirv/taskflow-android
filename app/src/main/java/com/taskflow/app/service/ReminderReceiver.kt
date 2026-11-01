package com.taskflow.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Notifications

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val taskId = intent.getStringExtra("task_id") ?: return
        val task = Store.tasks.firstOrNull { it.id == taskId } ?: return

        when (intent.action) {
            ReminderScheduler.ACTION_COMPLETE -> {
                if (!task.completed) Store.toggleTask(taskId)
                NotificationManagerCompat.from(context).cancel(taskId.hashCode())
            }

            ReminderScheduler.ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(taskId.hashCode())
                ReminderScheduler.scheduleSnooze(context, taskId)
            }

            else -> {
                if (task.completed) return
                showNotification(context, taskId)
            }
        }
    }

    private fun showNotification(context: Context, taskId: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return

        val task = Store.tasks.firstOrNull { it.id == taskId } ?: return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_task_id", taskId)
        }
        val openPending = PendingIntent.getActivity(
            context, taskId.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completePending = PendingIntent.getBroadcast(
            context, taskId.hashCode() + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_COMPLETE
                putExtra("task_id", taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePending = PendingIntent.getBroadcast(
            context, taskId.hashCode() + 2,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_SNOOZE
                putExtra("task_id", taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val time = if (task.time != null) " às ${Dates.timeLabel(task.time)}" else ""
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(task.title)
            .setContentText("${Dates.relative(task.date)}$time · ${task.category}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.notes.ifBlank { "Toque para abrir a tarefa." }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_check, "Concluir", completePending)
            .addAction(R.drawable.ic_schedule, "Adiar 10 min", snoozePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        }
    }
}
