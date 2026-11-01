package com.taskflow.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.util.Notifications
import com.taskflow.app.util.Tones

class PomodoroService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        manager = getSystemService(NotificationManager::class.java)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = runCatching {
                    Pomodoro.Mode.valueOf(intent.getStringExtra("mode") ?: Pomodoro.Mode.WORK.name)
                }.getOrDefault(Pomodoro.Mode.WORK)
                val minutes = intent.getIntExtra("minutes", defaultMinutes(mode))
                Pomodoro.prepare(mode, minutes)
                Pomodoro.taskId = intent.getStringExtra("task_id")
                Pomodoro.running = true
                startForegroundCompat()
                startTicker()
            }

            ACTION_TOGGLE -> {
                if (Pomodoro.remainingSeconds <= 0) {
                    Pomodoro.prepare(Pomodoro.mode, defaultMinutes(Pomodoro.mode))
                }
                Pomodoro.running = !Pomodoro.running
                Pomodoro.notifyListeners()
                startForegroundCompat()
                if (Pomodoro.running) startTicker() else stopTicker()
                updateNotification()
            }

            ACTION_STOP -> {
                startForegroundCompat()
                stopTicker()
                Pomodoro.running = false
                Pomodoro.prepare(Pomodoro.Mode.WORK, Store.settings.workMinutes)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }

    private fun defaultMinutes(mode: Pomodoro.Mode): Int = when (mode) {
        Pomodoro.Mode.WORK -> Store.settings.workMinutes
        Pomodoro.Mode.SHORT_BREAK -> Store.settings.shortBreakMinutes
        Pomodoro.Mode.LONG_BREAK -> Store.settings.longBreakMinutes
    }

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                if (!Pomodoro.running) return
                Pomodoro.remainingSeconds -= 1
                Pomodoro.notifyListeners()
                if (Pomodoro.remainingSeconds <= 0) {
                    finishSession()
                    return
                }
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
        ticker = runnable
        handler.postDelayed(runnable, 1000)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun finishSession() {
        stopTicker()
        Pomodoro.running = false
        val finished = Pomodoro.mode

        val nextMode: Pomodoro.Mode
        val nextMinutes: Int
        if (finished == Pomodoro.Mode.WORK) {
            Store.registerPomodoro(Pomodoro.taskId)
            Pomodoro.completedWorkSessions += 1
            nextMode = if (Pomodoro.completedWorkSessions % 4 == 0) Pomodoro.Mode.LONG_BREAK else Pomodoro.Mode.SHORT_BREAK
            nextMinutes = defaultMinutes(nextMode)
            Tones.timerDone(Store.settings.soundEffects)
        } else {
            nextMode = Pomodoro.Mode.WORK
            nextMinutes = defaultMinutes(nextMode)
            Tones.complete(Store.settings.soundEffects)
        }

        val message = if (finished == Pomodoro.Mode.WORK)
            "Sessão de foco concluída. Que tal uma pausa de $nextMinutes min?"
        else
            "Pausa concluída. Pronto para o próximo bloco de foco?"

        Pomodoro.prepare(nextMode, nextMinutes)
        manager.cancel(ONGOING_ID)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("open_tab", "focus")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val done = NotificationCompat.Builder(this, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(if (finished == Pomodoro.Mode.WORK) "Pomodoro concluído 🎉" else "Pausa concluída")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(FINISHED_ID, done)

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ONGOING_ID, notification, type)
    }

    private fun updateNotification() {
        manager.notify(ONGOING_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("open_tab", "focus")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = Intent(this, PomodoroActionReceiver::class.java).setAction(ACTION_TOGGLE)
        val togglePending = PendingIntent.getBroadcast(
            this, 1, toggleAction,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, PomodoroActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val taskTitle = Pomodoro.taskId?.let { id -> Store.tasks.firstOrNull { it.id == id }?.title }
        val text = taskTitle ?: when (Pomodoro.mode) {
            Pomodoro.Mode.WORK -> "Elimine as distrações e mergulhe no foco."
            else -> "Respire, alongue e descanse a mente."
        }

        return NotificationCompat.Builder(this, Notifications.CHANNEL_POMODORO)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("${Pomodoro.mode.label} · ${Pomodoro.timeText()}")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setProgress(100, (Pomodoro.progress() * 100).toInt(), false)
            .setContentIntent(open)
            .addAction(
                if (Pomodoro.running) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (Pomodoro.running) "Pausar" else "Continuar",
                togglePending
            )
            .addAction(R.drawable.ic_close, "Encerrar", stopPending)
            .build()
    }

    companion object {
        const val ACTION_START = "com.taskflow.app.POMODORO_START"
        const val ACTION_TOGGLE = "com.taskflow.app.POMODORO_TOGGLE"
        const val ACTION_STOP = "com.taskflow.app.POMODORO_STOP"
        private const val ONGOING_ID = 4201
        private const val FINISHED_ID = 4202

        fun start(context: Context, mode: Pomodoro.Mode, minutes: Int, taskId: String?) {
            val intent = Intent(context, PomodoroService::class.java).apply {
                action = ACTION_START
                putExtra("mode", mode.name)
                putExtra("minutes", minutes)
                putExtra("task_id", taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, PomodoroService::class.java).setAction(ACTION_TOGGLE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PomodoroService::class.java))
            Pomodoro.running = false
            Pomodoro.taskId = null
            Pomodoro.prepare(Pomodoro.Mode.WORK, Store.settings.workMinutes)
        }
    }
}
