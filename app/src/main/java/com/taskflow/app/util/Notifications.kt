package com.taskflow.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_POMODORO = "pomodoro"
    const val CHANNEL_ALARM_QUICK = "task_alarm_quick"
    const val CHANNEL_ALARM_CONTINUOUS = "task_alarm_continuous"
    const val CHANNEL_TIMER = "floating_timer"
    const val CHANNEL_WORKOUT = "workout_tracking"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Lembretes de tarefas", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_POMODORO, "Pomodoro e foco", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM_QUICK, "Alarme rápido de tarefas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Som curto 5 minutos antes da tarefa"
                enableVibration(true)
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (alarmUri != null) {
                    setSound(
                        alarmUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM_CONTINUOUS, "Alarme contínuo de tarefas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarme sonoro contínuo até ser parado"
                setSound(null, null)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Cronômetro flutuante", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORKOUT, "Treinos e caminhadas", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
