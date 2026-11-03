package com.taskflow.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Task
import com.taskflow.app.util.Notifications

/** Toca o alarme da tarefa em primeiro plano (rápido ou contínuo). */
class TaskAlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var task: Task? = null
    private var continuous = false

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                return START_NOT_STICKY
            }
            else -> {
                val taskId = intent?.getStringExtra("task_id") ?: return stopNow()
                task = Store.tasks.firstOrNull { it.id == taskId } ?: return stopNow()
                continuous = (intent.getStringExtra("sound") ?: task?.alertSound) == "alarm"
                startForegroundCompat()
                if (continuous) playContinuous()
                else handler.postDelayed({
                    stopForegroundCompat(detach = true)
                    stopSelf()
                }, 2_500)
            }
        }
        return START_NOT_STICKY
    }

    private fun playContinuous() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@TaskAlarmService, uri)
            isLooping = true
            prepare()
            start()
        }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskFlow:alarm")
            .apply { acquire(5 * 60_000L) }
        handler.postDelayed({ stopAlarm() }, 5 * 60_000L)
    }

    private fun buildNotification(): Notification {
        val task = task
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_task_id", task?.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TaskAlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channel = if (continuous) Notifications.CHANNEL_ALARM_CONTINUOUS
        else Notifications.CHANNEL_ALARM_QUICK
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(task?.title ?: "Tarefa em 5 minutos")
            .setContentText("Começa em 5 minutos · toque para abrir")
            .setStyle(NotificationCompat.BigTextStyle().bigText(task?.notes?.ifBlank { "Sua tarefa começa em 5 minutos." }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(continuous)
            .setContentIntent(open)
            .addAction(R.drawable.ic_close, "Parar", stop)
            .build()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat(detach: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (detach) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(detach)
        }
    }

    private fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop(); player?.release() }
        player = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        (getSystemService(NotificationManager::class.java)).cancel(NOTIFICATION_ID)
        stopForegroundCompat(detach = false)
        stopSelf()
    }

    private fun stopNow(): Int {
        stopForegroundCompat(detach = false)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { player?.release() }
        player = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.taskflow.app.TASK_ALARM_START"
        private const val ACTION_STOP = "com.taskflow.app.TASK_ALARM_STOP"
        private const val NOTIFICATION_ID = 7301
    }
}
