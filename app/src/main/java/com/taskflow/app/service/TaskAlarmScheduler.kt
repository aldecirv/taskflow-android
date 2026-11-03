package com.taskflow.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.taskflow.app.data.Task
import java.time.ZoneId

/** Agenda o alarme sonoro 5 minutos antes do horário da tarefa. */
object TaskAlarmScheduler {

    const val ACTION_ALARM = "com.taskflow.app.TASK_ALARM"
    private const val MINUTES_BEFORE = 5L

    fun schedule(context: Context, task: Task) {
        cancel(context, task.id)
        if (!task.alertEnabled || task.completed || task.time == null) return

        val triggerAt = task.date.atTime(task.time)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - MINUTES_BEFORE * 60_000L
        if (triggerAt <= System.currentTimeMillis() + 1_000L) return

        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context, task.id, task.alertSound, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            when {
                exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                exact -> manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                else -> manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }.onFailure {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context, taskId, null, PendingIntent.FLAG_NO_CREATE)
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun pending(
        context: Context,
        taskId: String,
        sound: String?,
        extraFlags: Int
    ): PendingIntent? {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = Uri.parse("taskflow://task-alarm/$taskId")
            putExtra("task_id", taskId)
            if (sound != null) putExtra("sound", sound)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode() * 41 + 5,
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
