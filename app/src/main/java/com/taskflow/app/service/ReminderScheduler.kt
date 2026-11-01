package com.taskflow.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {

    const val ACTION_REMIND = "com.taskflow.app.REMIND"
    const val ACTION_COMPLETE = "com.taskflow.app.COMPLETE"
    const val ACTION_SNOOZE = "com.taskflow.app.SNOOZE"

    val options = listOf(5, 10, 15, 30, 60, 120, 1440)

    fun schedule(context: Context, task: Task) {
        cancel(context, task.id)
        if (task.completed || !Store.settings.notificationsEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val base = task.date.atTime(task.time ?: LocalTime.of(9, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        task.reminders.forEach { minutes ->
            val triggerAt = base - minutes * 60_000L
            if (triggerAt > now + 2_000) {
                scheduleAt(context, alarmManager, task.id, minutes, triggerAt)
            }
        }
    }

    fun scheduleSnooze(context: Context, taskId: String, minutes: Int = 10) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val code = (taskId.hashCode() * 31) + 999
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("taskflow://reminder/$taskId/snooze")
            putExtra("task_id", taskId)
            putExtra("snooze", true)
        }
        val pending = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(alarmManager, triggerAt, pending)
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val hash = taskId.hashCode()
        (options + 999).forEach { minutes ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                data = Uri.parse("taskflow://reminder/$taskId/$minutes")
            }
            val pending = PendingIntent.getBroadcast(
                context, hash * 31 + minutes, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun scheduleAt(context: Context, manager: AlarmManager, taskId: String, minutes: Int, at: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("taskflow://reminder/$taskId/$minutes")
            putExtra("task_id", taskId)
            putExtra("minutes", minutes)
        }
        val pending = PendingIntent.getBroadcast(
            context, taskId.hashCode() * 31 + minutes, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(manager, at, pending)
    }

    private fun setAlarm(manager: AlarmManager, at: Long, pending: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        try {
            when {
                exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                exact -> manager.setExact(AlarmManager.RTC_WAKEUP, at, pending)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else -> manager.set(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            manager.set(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}
