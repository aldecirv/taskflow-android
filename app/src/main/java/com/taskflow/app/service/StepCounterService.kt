package com.taskflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.StepStore
import java.time.LocalDate

/**
 * Contador de passos contínuo, em primeiro plano.
 *
 * Diferente do podômetro do treino, este serviço fica ativo o dia inteiro
 * (START_STICKY), grava o total em StepStore e atualiza a notificação.
 * Usa TYPE_STEP_COUNTER (acumulado desde o boot) com base diária.
 */
class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "taskflow_steps"
        const val NOTIFICATION_ID = 5150
        const val ACTION_STOP = "com.taskflow.app.STEPS_STOP"

        @Volatile
        private var active = false

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }

        fun isRunning(context: Context): Boolean =
            active || (StepStore.day(context) == LocalDate.now().toString() && StepStore.base(context) >= 0f)
    }

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    private var base = -1f
    private var steps = 0
    private var lastNotified = -1

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        startForeground(NOTIFICATION_ID, buildNotification())
        if (sensor == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (StepStore.day(this) != LocalDate.now().toString()) {
            base = -1f
            steps = 0
            StepStore.resetForToday(this, -1f)
        } else {
            base = StepStore.base(this)
            steps = StepStore.steps(this)
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Contagem de passos",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Total de passos do dia, sem interromper você"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, StepCounterService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val goal = StepStore.goal(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$steps passos hoje")
            .setContentText("Meta: $goal passos")
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val value = event.values.firstOrNull() ?: return
        if (base < 0f || value < base) base = value
        steps = (value - base).toInt().coerceAtLeast(0)
        StepStore.save(this, steps, base)
        if (lastNotified < 0 || steps - lastNotified >= 25) {
            lastNotified = steps
            updateNotification()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        active = false
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
