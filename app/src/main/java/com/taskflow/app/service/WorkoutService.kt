package com.taskflow.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Workout
import com.taskflow.app.data.WorkoutMode
import com.taskflow.app.data.WorkoutPoint
import com.taskflow.app.util.Notifications
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Rastreia caminhada/corrida com GPS e podômetro em primeiro plano. */
class WorkoutService : Service(), LocationListener, SensorEventListener {

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepBaseline: Float? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private var mode = WorkoutMode.WALK
    private var running = false
    private var paused = false
    private var elapsed = 0
    private var distance = 0.0
    private var steps = 0
    private var calories = 0
    private var pace = 0
    private val points = mutableListOf<WorkoutPoint>()
    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.ensureChannels(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(WorkoutMode.from(intent.getStringExtra("mode")))
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_FINISH -> finishTracking()
            ACTION_STOP -> stopTracking()
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startTracking(selected: WorkoutMode) {
        mode = selected
        running = true
        paused = false
        elapsed = 0
        distance = 0.0
        steps = 0
        calories = 0
        pace = 0
        points.clear()
        lastLocation = null
        stepBaseline = null
        if (!hasLocationPermission()) {
            WorkoutController.set(WorkoutState(error = "Permissão de localização necessária"))
            stopSelf()
            return
        }
        startForegroundCompat()
        acquireWakeLock()
        WorkoutController.set(
            WorkoutState(active = true, paused = false, mode = mode)
        )
        requestLocation()
        registerSteps()
        startTicker()
        updateNotification()
    }

    private fun requestLocation() {
        runCatching {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 2f, this, Looper.getMainLooper()
            )
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 3000L, 5f, this, Looper.getMainLooper()
                )
            }
        }.onFailure {
            WorkoutController.update { state -> state.copy(error = "GPS indisponível neste aparelho") }
        }
    }

    private fun registerSteps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= 29
        ) return
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                if (!running) return
                if (!paused) {
                    elapsed += 1
                    calculateMetrics()
                    publish()
                    if (elapsed % 10 == 0) updateNotification()
                }
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

    private fun calculateMetrics() {
        val weight = Store.settings.weightKg
        val minutes = elapsed / 60.0
        val met = if (mode == WorkoutMode.RUN) {
            when {
                pace <= 0 -> mode.met
                pace < 300 -> 11.0
                pace < 360 -> 9.8
                else -> 8.3
            }
        } else {
            when {
                pace <= 0 -> mode.met
                pace < 600 -> 4.3
                else -> 3.5
            }
        }
        calories = (met * 3.5 * weight / 200.0 * minutes).toInt().coerceAtLeast(0)
        pace = if (distance > 30) (elapsed / (distance / 1000.0)).toInt() else 0
    }

    private fun publish() {
        WorkoutController.update {
            WorkoutState(
                active = true,
                paused = paused,
                mode = mode,
                distanceMeters = distance,
                elapsedSeconds = elapsed,
                steps = steps,
                calories = calories,
                paceSecondsPerKm = pace,
                points = points.toList()
            )
        }
    }

    private fun pauseTracking() {
        if (!running) return
        paused = true
        running = true
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
        publish()
        updateNotification()
    }

    private fun resumeTracking() {
        if (!running) return
        paused = false
        requestLocation()
        registerSteps()
        publish()
        updateNotification()
    }

    private fun finishTracking() {
        if (!running) return
        running = false
        stopTicker()
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
        calculateMetrics()
        val workout = Workout(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAt = LocalDateTime.now().minusSeconds(elapsed.toLong()),
            durationSeconds = elapsed,
            distanceMeters = distance,
            steps = steps,
            calories = calories,
            avgPaceSecondsPerKm = pace,
            route = points.toList(),
            weightKg = Store.settings.weightKg
        )
        Store.addWorkout(workout)
        WorkoutController.update {
            it.copy(active = false, paused = false, savedWorkoutId = workout.id)
        }
        updateNotification()
        stopForegroundCompat()
        releaseWakeLock()
        stopSelf()
    }

    private fun stopTracking() {
        running = false
        stopTicker()
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this)
        releaseWakeLock()
        stopForegroundCompat()
        WorkoutController.reset()
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        if (!running || paused) return
        if (location.accuracy > 60f) return
        val previous = lastLocation
        if (previous != null) {
            val delta = previous.distanceTo(location)
            if (delta < 2.0) return
            distance += delta
        }
        lastLocation = location
        points.add(WorkoutPoint(location.latitude, location.longitude, elapsed))
        calculateMetrics()
        publish()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) {
        WorkoutController.update { it.copy(error = "Ative o GPS para registrar o trajeto") }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val value = event.values.firstOrNull() ?: return
        val base = stepBaseline
        if (base == null) {
            stepBaseline = value
        } else {
            steps = (value - base).toInt().coerceAtLeast(0)
            publish()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskFlow:workout")
            .apply { acquire(3 * 60 * 60_000L) }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pause = PendingIntent.getService(
            this, 1,
            Intent(this, WorkoutService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val finish = PendingIntent.getService(
            this, 2, Intent(this, WorkoutService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_fitness_center)
            .setContentTitle(mode.label + " em andamento")
            .setContentText(String.format(java.util.Locale("pt", "BR"), "%.2f km · %s", distance / 1000.0, format(elapsed)))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause, if (paused) "Retomar" else "Pausar", pause)
            .addAction(R.drawable.ic_flag, "Finalizar", finish)
            .build()
    }

    private fun updateNotification() {
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun format(total: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)

    override fun onDestroy() {
        stopTicker()
        runCatching { locationManager.removeUpdates(this) }
        sensorManager.unregisterListener(this)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.taskflow.app.WORKOUT_START"
        const val ACTION_PAUSE = "com.taskflow.app.WORKOUT_PAUSE"
        const val ACTION_RESUME = "com.taskflow.app.WORKOUT_RESUME"
        const val ACTION_FINISH = "com.taskflow.app.WORKOUT_FINISH"
        const val ACTION_STOP = "com.taskflow.app.WORKOUT_STOP"
        private const val NOTIFICATION_ID = 7501

        fun start(context: Context, mode: WorkoutMode) {
            val intent = Intent(context, WorkoutService::class.java).apply {
                action = ACTION_START
                putExtra("mode", mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, WorkoutService::class.java).setAction(action))
        }
    }
}
