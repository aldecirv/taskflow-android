package com.taskflow.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.taskflow.app.R
import com.taskflow.app.data.Store
import com.taskflow.app.data.Workout
import com.taskflow.app.databinding.ActivityWorkoutDetailBinding
import com.taskflow.app.util.ThemeManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class WorkoutDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutDetailBinding
    private var workout: Workout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        binding = ActivityWorkoutDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        val id = intent.getStringExtra(EXTRA_ID)
        val loaded = id?.let { Store.workouts.firstOrNull { w -> w.id == it } }
        if (loaded == null) {
            finish()
            return
        }
        workout = loaded
        binding.btnBack.setOnClickListener { finish() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnRepeat.setOnClickListener {
            startActivity(WorkoutActivity.intent(this, loaded.mode))
            finish()
        }
        binding.title.text = "${loaded.mode.emoji} ${loaded.mode.label}"
        binding.date.text = loaded.dateLabel()
        binding.distance.text = loaded.distanceLabel()
        binding.time.text = loaded.durationLabel()
        binding.pace.text = loaded.paceLabel().replace(" /km", "")
        binding.steps.text = loaded.steps.toString()
        binding.calories.text = loaded.calories.toString()
        binding.routeInfo.text = "${loaded.route.size} pontos de GPS registrados"
        setupMap(loaded)
    }

    private fun setupMap(workout: Workout) {
        Configuration.getInstance().userAgentValue = packageName
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(false)
        binding.map.controller.setZoom(16.0)
        if (workout.route.isEmpty()) {
            binding.map.controller.setCenter(GeoPoint(-23.5505, -46.6333))
            return
        }
        val points = workout.route.map { GeoPoint(it.latitude, it.longitude) }
        val line = Polyline(binding.map).apply {
            setPoints(points)
            width = 9f
            color = ContextCompat.getColor(this@WorkoutDetailActivity, R.color.brand_indigo)
        }
        binding.map.overlays.add(line)
        val start = Marker(binding.map).apply {
            position = points.first()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Início"
        }
        val end = Marker(binding.map).apply {
            position = points.last()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Fim"
        }
        binding.map.overlays.add(start)
        binding.map.overlays.add(end)
        if (points.size >= 2) {
            runCatching {
                binding.map.zoomToBoundingBox(
                    BoundingBox.fromGeoPoints(points),
                    true,
                    70
                )
            }
        } else {
            binding.map.controller.setCenter(points.first())
        }
        binding.map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        runCatching { binding.map.onResume() }
    }

    override fun onPause() {
        runCatching { binding.map.onPause() }
        super.onPause()
    }

    private fun confirmDelete() {
        val current = workout ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir treino?")
            .setMessage("${current.mode.label} de ${current.distanceLabel()}")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                Store.deleteWorkout(current.id)
                finish()
            }
            .show()
    }

    companion object {
        private const val EXTRA_ID = "workout_id"
        fun intent(context: android.content.Context, workoutId: String) =
            Intent(context, WorkoutDetailActivity::class.java).putExtra(EXTRA_ID, workoutId)
    }
}
