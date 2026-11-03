package com.taskflow.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.WorkoutMode
import com.taskflow.app.data.WorkoutPoint
import com.taskflow.app.databinding.ActivityWorkoutBinding
import com.taskflow.app.service.WorkoutController
import com.taskflow.app.service.WorkoutService
import com.taskflow.app.service.WorkoutState
import com.taskflow.app.util.ThemeManager
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class WorkoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutBinding
    private var mode = WorkoutMode.WALK
    private var pendingStart: WorkoutMode? = null
    private var savedHandled = false
    private var polyline: Polyline? = null
    private var marker: Marker? = null
    private var firstFix = true

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) {
            pendingStart?.let { startWorkout(it) }
        } else {
            binding.statusText.text = "Permissão de localização negada. Toque em iniciar para tentar novamente."
        }
        pendingStart = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mode = WorkoutMode.from(intent.getStringExtra(EXTRA_MODE))
        binding.btnClose.setOnClickListener { confirmClose() }
        binding.btnStart.setOnClickListener { requestAndStart(mode) }
        binding.btnPause.setOnClickListener {
            val state = WorkoutController.state.value
            WorkoutService.send(this, if (state.paused) WorkoutService.ACTION_RESUME else WorkoutService.ACTION_PAUSE)
        }
        binding.btnFinish.setOnClickListener { confirmFinish() }
        setupMap()
        setupModeChips()

        val existing = WorkoutController.state.value
        if (!existing.active) WorkoutController.reset()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkoutController.state.collect { render(it) }
            }
        }
    }

    private fun setupMap() {
        Configuration.getInstance().userAgentValue = packageName
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(17.0)
        polyline = Polyline(binding.map).apply {
            width = 10f
            color = ContextCompat.getColor(this@WorkoutActivity, R.color.brand_indigo)
        }
        binding.map.overlays.add(polyline)
        marker = Marker(binding.map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.map.overlays.add(marker)
    }

    private fun setupModeChips() {
        binding.modeGroup.removeAllViews()
        WorkoutMode.entries.forEach { item ->
            val chip = Chip(this).apply {
                text = "${item.emoji} ${item.label}"
                isCheckable = true
                isChecked = item == mode
                textSize = 12f
                chipMinHeight = dp(38).toFloat()
                setOnClickListener {
                    mode = item
                    for (i in 0 until binding.modeGroup.childCount) {
                        val c = binding.modeGroup.getChildAt(i) as Chip
                        c.isChecked = c === this
                    }
                }
            }
            binding.modeGroup.addView(chip)
        }
    }

    private fun requestAndStart(selected: WorkoutMode) {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) {
            pendingStart = selected
            permissions.launch(missing.toTypedArray())
            return
        }
        startWorkout(selected)
    }

    private fun startWorkout(selected: WorkoutMode) {
        savedHandled = false
        firstFix = true
        mode = selected
        WorkoutService.start(this, selected)
        binding.statusText.text = "Buscando sinal de GPS..."
    }

    private fun confirmFinish() {
        val state = WorkoutController.state.value
        if (!state.active) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Finalizar treino?")
            .setMessage(String.format(Locale("pt", "BR"), "%.2f km · %s", state.distanceKm, timeLabel(state.elapsedSeconds)))
            .setNegativeButton("Continuar", null)
            .setPositiveButton("Finalizar e salvar") { _, _ ->
                WorkoutService.send(this, WorkoutService.ACTION_FINISH)
            }
            .show()
    }

    private fun confirmClose() {
        val state = WorkoutController.state.value
        if (!state.active) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Descartar treino?")
            .setMessage("O treino em andamento não será salvo.")
            .setNegativeButton("Continuar", null)
            .setPositiveButton("Descartar") { _, _ ->
                WorkoutService.send(this, WorkoutService.ACTION_STOP)
                WorkoutController.reset()
                finish()
            }
            .show()
    }

    private fun render(state: WorkoutState) {
        val active = state.active || state.paused
        binding.btnStart.visibility = if (active) View.GONE else View.VISIBLE
        binding.modeGroup.visibility = if (active) View.GONE else View.VISIBLE
        binding.activeControls.visibility = if (active) View.VISIBLE else View.GONE
        binding.btnPause.text = if (state.paused) "Retomar" else "Pausar"
        binding.btnPause.setIconResource(if (state.paused) R.drawable.ic_play_arrow else R.drawable.ic_pause)

        binding.distance.text = String.format(Locale("pt", "BR"), "%.2f km", state.distanceKm)
        binding.time.text = timeLabel(state.elapsedSeconds)
        binding.pace.text = if (state.paceSecondsPerKm > 0)
            String.format(Locale.US, "%d:%02d", state.paceSecondsPerKm / 60, state.paceSecondsPerKm % 60)
        else "--:--"
        binding.steps.text = state.steps.toString()
        binding.calories.text = state.calories.toString()
        binding.gpsStatus.text = when {
            state.error != null -> "SEM GPS"
            state.active && state.points.isEmpty() -> "BUSCANDO"
            state.active -> "GPS ATIVO"
            else -> "GPS"
        }
        binding.gpsStatus.setTextColor(
            ContextCompat.getColor(this, if (state.error != null) R.color.rose else R.color.emerald)
        )
        binding.statusText.text = state.error ?: when {
            state.paused -> "Treino pausado"
            state.active -> "${state.mode.label} em andamento · tela mantida ligada"
            else -> "Escolha o tipo de treino e toque em iniciar"
        }

        if (state.points.isNotEmpty()) {
            val geoPoints = state.points.map { GeoPoint(it.latitude, it.longitude) }
            polyline?.setPoints(geoPoints)
            marker?.position = geoPoints.last()
            if (firstFix) {
                binding.map.controller.setCenter(geoPoints.last())
                binding.map.controller.setZoom(17.0)
                firstFix = false
            }
            binding.map.invalidate()
        }

        state.savedWorkoutId?.let { id ->
            if (!savedHandled) {
                savedHandled = true
                startActivity(WorkoutDetailActivity.intent(this, id))
                finish()
            }
        }
    }

    private fun timeLabel(total: Int): String =
        String.format(Locale.US, "%02d:%02d", total / 60, total % 60)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        fun intent(context: android.content.Context, mode: WorkoutMode) =
            Intent(context, WorkoutActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}
