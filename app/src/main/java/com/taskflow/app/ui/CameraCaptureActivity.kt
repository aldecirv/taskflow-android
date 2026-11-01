package com.taskflow.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.taskflow.app.databinding.ActivityCameraCaptureBinding
import com.taskflow.app.util.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraCaptureBinding
    private var imageCapture: ImageCapture? = null
    private var pendingGalleryFallback = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else {
                Toast.makeText(this, "Permissão negada. Escolha uma foto da galeria.", Toast.LENGTH_LONG).show()
                openGallery(fallback = true)
            }
        }

    private val gallery =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) copyAndFinish(uri)
            else if (pendingGalleryFallback || imageCapture == null) finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.btnGallery.setOnClickListener { openGallery(fallback = false) }
        binding.btnShutter.setOnClickListener { capture() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.preview.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure {
                Toast.makeText(this, "Câmera indisponível. Use a galeria.", Toast.LENGTH_LONG).show()
                openGallery(fallback = true)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, "Câmera ainda iniciando...", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = File(filesDir, "product_images").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val file = File(dir, "prod_$stamp.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                haptic()
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, file.absolutePath))
                finish()
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@CameraCaptureActivity, "Falha ao capturar: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun openGallery(fallback: Boolean) {
        pendingGalleryFallback = fallback
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun copyAndFinish(uri: Uri) {
        runCatching {
            val dir = File(filesDir, "product_images").apply { mkdirs() }
            val file = File(dir, "prod_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Não foi possível abrir a imagem")
            haptic()
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, file.absolutePath))
            finish()
        }.onFailure {
            Toast.makeText(this, "Não foi possível usar esta imagem", Toast.LENGTH_LONG).show()
        }
    }

    private fun haptic() {
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        binding.root.performHapticFeedback(feedback)
    }

    companion object {
        const val EXTRA_PATH = "photo_path"
    }
}
