package com.taskflow.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ViewFloatingTimerBinding
import com.taskflow.app.util.Notifications

/** Cronômetro flutuante (overlay) vinculado a uma tarefa. */
class FloatingTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var binding: ViewFloatingTimerBinding? = null
    private var taskId: String = ""
    private var taskTitle: String = ""
    private var seconds = 0
    private var running = true
    private var ticker: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.ensureChannels(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_FINISH -> finishTimer()
            ACTION_STOP -> closeTimer(save = false)
            else -> {
                taskId = intent?.getStringExtra("task_id").orEmpty()
                taskTitle = intent?.getStringExtra("title") ?: "Tarefa"
                if (taskId.isBlank()) return stopNow()
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Permissão de sobreposição não concedida", Toast.LENGTH_LONG).show()
                    return stopNow()
                }
                startForegroundCompat()
                showOverlay()
                startTicker()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlay != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.view_floating_timer, null)
        val b = ViewFloatingTimerBinding.bind(view)
        binding = b
        b.timerTitle.text = taskTitle
        b.btnTimerClose.setOnClickListener { closeTimer(save = false) }
        b.btnTimerFinish.setOnClickListener { finishTimer() }
        b.btnTimerPause.setOnClickListener { toggle() }
        b.btnTimerPause.setImageResource(R.drawable.ic_pause)
        attachDrag(b.timerDrag)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }
        runCatching { windowManager.addView(view, params) }
            .onFailure {
                Toast.makeText(this, "Não foi possível exibir o cronômetro", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
        overlay = view
        view.tag = params
    }

    private fun attachDrag(view: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            val params = overlay?.tag as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(overlay, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                if (running) {
                    seconds += 1
                    render()
                    if (seconds % 10 == 0) updateNotification()
                }
                handler.postDelayed(this, 1000)
            }
        }
        ticker = runnable
        handler.postDelayed(runnable, 1000)
        render()
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun render() {
        val b = binding ?: return
        b.timerValue.text = format(seconds)
        b.timerState.text = if (running) "Em andamento" else "Pausado"
        b.btnTimerPause.setImageResource(if (running) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        b.btnTimerPause.contentDescription = if (running) "Pausar cronômetro" else "Retomar cronômetro"
    }

    private fun toggle() {
        running = !running
        render()
        updateNotification()
    }

    private fun finishTimer() {
        Store.addTrackedTime(taskId, seconds)
        Toast.makeText(this, "Tempo salvo na tarefa: ${format(seconds)}", Toast.LENGTH_LONG).show()
        closeTimer(save = false)
    }

    private fun closeTimer(save: Boolean) {
        if (save && seconds > 0) Store.addTrackedTime(taskId, seconds)
        stopTicker()
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        binding = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, FloatingTimerService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val finish = PendingIntent.getService(
            this, 2, Intent(this, FloatingTimerService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(taskTitle)
            .setContentText(format(seconds) + if (running) " · em andamento" else " · pausado")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (running) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (running) "Pausar" else "Retomar",
                toggle
            )
            .addAction(R.drawable.ic_check, "Finalizar", finish)
            .build()
    }

    private fun updateNotification() {
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun stopNow(): Int {
        stopForegroundCompat()
        stopSelf()
        return START_NOT_STICKY
    }

    private fun format(total: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)

    override fun onDestroy() {
        stopTicker()
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "com.taskflow.app.TIMER_START"
        private const val ACTION_TOGGLE = "com.taskflow.app.TIMER_TOGGLE"
        private const val ACTION_FINISH = "com.taskflow.app.TIMER_FINISH"
        private const val ACTION_STOP = "com.taskflow.app.TIMER_STOP"
        private const val NOTIFICATION_ID = 7401

        fun start(context: Context, taskId: String, title: String) {
            val intent = Intent(context, FloatingTimerService::class.java).apply {
                action = ACTION_START
                putExtra("task_id", taskId)
                putExtra("title", title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
