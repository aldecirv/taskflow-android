package com.taskflow.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.taskflow.app.R
import com.taskflow.app.data.ProductCategories
import com.taskflow.app.data.Purchase
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.ActivityProductEditorBinding
import com.taskflow.app.util.Dates
import com.taskflow.app.util.Images
import com.taskflow.app.util.Money
import com.taskflow.app.util.ProductVision
import com.taskflow.app.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class ProductEditorActivity : AppCompatActivity() {

    enum class Mode { PHOTO, BARCODE, MANUAL }

    private lateinit var binding: ActivityProductEditorBinding
    private var editing: Purchase? = null
    private var photoPath: String? = null
    private var barcode: String? = null
    private var purchaseDate: LocalDate = LocalDate.now()
    private var category: String = ProductCategories.all.first().name
    private var scanMode: Mode = Mode.MANUAL
    private var updatingPrice = false
    private var locationListener: LocationListener? = null

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val path = result.data?.getStringExtra(CameraCaptureActivity.EXTRA_PATH) ?: return@registerForActivityResult
            photoPath = path
            showPhoto(path)
            binding.formContainer.alpha = 0f
            binding.formContainer.translationY = dp(26).toFloat()
            analyze(path)
        }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) requestLocation()
            else Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityProductEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        val id = intent.getStringExtra(EXTRA_PURCHASE_ID)
        editing = id?.let { purchaseId -> Store.purchases.firstOrNull { it.id == purchaseId } }
        isNew = editing == null

        setupUi()
        fillForm()
        renderCategory()

        if (editing == null) {
            scanMode = runCatching {
                Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.MANUAL.name)
            }.getOrDefault(Mode.MANUAL)
            if (scanMode != Mode.MANUAL) binding.root.postDelayed({ launchCamera(scanMode) }, 350)
        }
    }

    private var isNew = true

    private fun setupUi() {
        binding.btnClose.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnSaveBottom.setOnClickListener { save() }
        binding.btnDelete.isVisible = editing != null
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.screenTitle.text = if (editing != null) "Editar produto" else "Adicionar produto"

        binding.dateField.setOnClickListener { openDatePicker() }
        binding.btnPhoto.setOnClickListener { launchCamera(Mode.PHOTO) }
        binding.btnBarcode.setOnClickListener { launchCamera(Mode.BARCODE) }
        binding.placeLayout.setEndIconOnClickListener { requestLocationPermission() }

        setupMoneyMask()
        setupCategories()
        setupPlaceSuggestions()
        renderDate()
    }

    private fun setupMoneyMask() {
        binding.priceInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingPrice) return
                val digits = s?.toString().orEmpty().filter { it.isDigit() }.take(9)
                val formatted = Money.maskFromDigits(digits)
                if (formatted != s?.toString()) {
                    updatingPrice = true
                    binding.priceInput.setText(formatted)
                    binding.priceInput.setSelection(formatted.length)
                    updatingPrice = false
                }
            }
        })
    }

    private fun setupCategories() {
        binding.categoryGroup.removeAllViews()
        ProductCategories.all.forEach { cat ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = cat.name
                isCheckable = true
                textSize = 12f
                chipMinHeight = dp(38).toFloat()
                chipStrokeWidth = 0f
                isChecked = cat.name == category
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        category = cat.name
                        renderCategory()
                    }
                }
            }
            binding.categoryGroup.addView(chip)
        }
        renderCategory()
    }

    private fun renderCategory() {
        for (i in 0 until binding.categoryGroup.childCount) {
            val chip = binding.categoryGroup.getChildAt(i) as com.google.android.material.chip.Chip
            val cat = ProductCategories.all[i]
            val color = Color.parseColor(cat.colorHex)
            chip.isChecked = cat.name == category
            chip.chipBackgroundColor = ColorStateList.valueOf(
                if (chip.isChecked) color else ContextCompat.getColor(this, R.color.surface_alt)
            )
            chip.setTextColor(
                if (chip.isChecked) Color.WHITE else ContextCompat.getColor(this, R.color.text_secondary)
            )
        }
    }

    private fun setupPlaceSuggestions() {
        binding.placeChips.removeAllViews()
        Store.distinctPlaces().take(5).forEach { place ->
            val chip = TextView(this).apply {
                text = place
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@ProductEditorActivity, R.color.text_secondary))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(ContextCompat.getColor(this@ProductEditorActivity, R.color.surface_alt))
                }
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    binding.placeInput.setText(place)
                    binding.placeInput.setSelection(place.length)
                }
            }
            binding.placeChips.addView(chip)
        }
    }

    private fun fillForm() {
        val purchase = editing ?: return
        binding.nameInput.setText(purchase.name)
        setPrice(purchase.priceCents)
        purchaseDate = purchase.date
        binding.placeInput.setText(purchase.place)
        category = purchase.category
        barcode = purchase.barcode
        photoPath = purchase.imagePath
        renderBarcode()
        renderCategory()
        renderDate()
        purchase.imagePath?.let { if (File(it).exists()) showPhoto(it) }
    }

    private fun setPrice(cents: Long) {
        updatingPrice = true
        binding.priceInput.setText(Money.format(cents))
        binding.priceInput.setSelection(binding.priceInput.text?.length ?: 0)
        updatingPrice = false
    }

    private fun renderDate() {
        binding.dateValue.text = if (purchaseDate == LocalDate.now()) "Hoje" else Dates.shortDate(purchaseDate)
    }

    private fun renderBarcode() {
        val code = barcode
        if (code.isNullOrBlank()) {
            binding.barcodeLabel.isVisible = false
        } else {
            binding.barcodeLabel.isVisible = true
            binding.barcodeLabel.text = "Código de barras: $code"
        }
    }

    // ------------------------------------------------------------------- câmera

    private fun launchCamera(mode: Mode) {
        scanMode = mode
        runCatching {
            cameraLauncher.launch(Intent(this, CameraCaptureActivity::class.java))
        }.onFailure {
            Toast.makeText(this, "Não foi possível abrir a câmera. Use a galeria.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPhoto(path: String) {
        val bitmap = Images.load(path, 1000) ?: return
        binding.photo.setImageBitmap(bitmap)
        binding.photo.isVisible = true
        binding.photoPlaceholder.isVisible = false
    }

    private fun analyze(path: String) {
        binding.analysisOverlay.isVisible = true
        binding.analysisText.text =
            if (scanMode == Mode.BARCODE) "Lendo o código de barras..." else "Analisando a imagem..."

        lifecycleScope.launch {
            if (isFinishing || isDestroyed) return@launch
            if (scanMode == Mode.BARCODE) {
                val code = ProductVision.scanBarcode(this@ProductEditorActivity, path)
                if (isFinishing || isDestroyed) return@launch
                binding.analysisOverlay.isVisible = false
                if (code.isNullOrBlank()) {
                    Toast.makeText(
                        this@ProductEditorActivity,
                        "Não encontrei um código de barras. Tente outra foto ou digite o nome.",
                        Toast.LENGTH_LONG
                    ).show()
                    revealForm()
                    focusName()
                    return@launch
                }
                barcode = code
                renderBarcode()
                val name = ProductVision.lookupBarcode(code)
                if (!name.isNullOrBlank() && binding.nameInput.text.isNullOrBlank()) {
                    binding.nameInput.setText(name)
                    binding.aiBadge.isVisible = true
                    binding.aiBadge.text = "Encontrado pelo código de barras ✨"
                    Toast.makeText(
                        this@ProductEditorActivity,
                        "Produto encontrado: $name. Informe o preço pago.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@ProductEditorActivity,
                        "Código $code lido. Complete o nome e o preço.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                revealForm()
            } else {
                val guess = ProductVision.identify(this@ProductEditorActivity, path)
                if (isFinishing || isDestroyed) return@launch
                binding.analysisOverlay.isVisible = false
                if (guess == null) {
                    Toast.makeText(
                        this@ProductEditorActivity,
                        "Não consegui identificar o produto. Preencha manualmente.",
                        Toast.LENGTH_LONG
                    ).show()
                    revealForm()
                    focusName()
                    return@launch
                }
                if (binding.nameInput.text.isNullOrBlank()) binding.nameInput.setText(guess.name)
                binding.aiBadge.isVisible = true
                binding.aiBadge.text = "Reconhecido por IA ✨"
                if (Money.digitsToCents(binding.priceInput.text?.toString().orEmpty()) == 0L && guess.priceCents != null) {
                    setPrice(guess.priceCents!!)
                }
                revealForm()
                Toast.makeText(
                    this@ProductEditorActivity,
                    "Reconhecido ${if (guess.source == "gemini") "com IA" else "por OCR"}. Revise os dados.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // -------------------------------------------------------------------- data

    private fun openDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Data da compra")
            .setSelection(purchaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            purchaseDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            renderDate()
        }
        picker.show(supportFragmentManager, "purchase_date")
    }

    // --------------------------------------------------------------- localização

    private fun requestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            requestLocation()
        } else {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun requestLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            Toast.makeText(this, "Ative a localização do aparelho para usar o GPS", Toast.LENGTH_LONG).show()
            return
        }

        val last = providers.mapNotNull { provider ->
            runCatching {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) manager.getLastKnownLocation(provider) else null
            }.getOrNull()
        }.maxByOrNull { it.time }

        if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000) {
            resolvePlace(last)
            return
        }

        Toast.makeText(this, "Buscando sua localização...", Toast.LENGTH_SHORT).show()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                locationListener = null
                resolvePlace(location)
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        locationListener = listener

        runCatching {
            manager.requestLocationUpdates(providers.first(), 0L, 0f, listener, Looper.getMainLooper())
        }.onFailure {
            Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (locationListener === listener) {
                manager.removeUpdates(listener)
                locationListener = null
                if (last != null) resolvePlace(last)
                else Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
            }
        }, 12_000)
    }

    private fun resolvePlace(location: Location) {
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    if (!Geocoder.isPresent()) return@runCatching null
                    @Suppress("DEPRECATION")
                    val address = Geocoder(this@ProductEditorActivity, Locale("pt", "BR"))
                        .getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                    if (address == null) null
                    else listOfNotNull(address.subLocality ?: address.thoroughfare, address.locality)
                        .distinct().joinToString(", ").ifBlank { address.featureName }
                }.getOrNull()
            }

            if (isFinishing || isDestroyed) return@launch
            val value = name?.takeIf { it.isNotBlank() }
                ?: String.format(Locale.ROOT, "%.5f, %.5f", location.latitude, location.longitude)
            binding.placeInput.setText(value)
            binding.placeInput.setSelection(value.length)
            Toast.makeText(this@ProductEditorActivity, "Local preenchido pelo GPS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun revealForm() {
        if (binding.formContainer.alpha < 1f) {
            binding.formContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .start()
        }
    }

    private fun focusName() {
        binding.nameInput.requestFocus()
        val imm = getSystemService(InputMethodManager::class.java)
        binding.nameInput.post { imm.showSoftInput(binding.nameInput, InputMethodManager.SHOW_IMPLICIT) }
    }

    // -------------------------------------------------------------------- salvar

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            binding.nameInput.error = "Informe o nome do produto"
            binding.nameInput.requestFocus()
            return
        }
        val cents = Money.digitsToCents(binding.priceInput.text?.toString().orEmpty())
        if (cents <= 0L) {
            binding.priceInput.error = "Informe o preço pago"
            binding.priceInput.requestFocus()
            return
        }

        val place = binding.placeInput.text?.toString()?.trim().orEmpty()
        val purchase = if (editing != null) {
            editing!!.copy(
                name = name,
                priceCents = cents,
                date = purchaseDate,
                place = place,
                category = category,
                barcode = barcode,
                imagePath = photoPath
            )
        } else {
            Purchase(
                id = UUID.randomUUID().toString(),
                name = name,
                priceCents = cents,
                date = purchaseDate,
                place = place,
                category = category,
                barcode = barcode,
                imagePath = photoPath
            )
        }

        if (editing != null) Store.updatePurchase(purchase) else Store.addPurchase(purchase)
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_PURCHASE_ID, purchase.id)
                .putExtra(EXTRA_PURCHASE_NAME, purchase.name)
                .putExtra(EXTRA_IS_NEW, editing == null)
        )
        finish()
    }

    private fun confirmDelete() {
        val purchase = editing ?: return
        AlertDialog.Builder(this)
            .setTitle("Excluir compra?")
            .setMessage(purchase.name)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                Store.deletePurchase(purchase.id)
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PURCHASE_ID, purchase.id).putExtra(EXTRA_IS_NEW, false))
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PURCHASE_ID = "purchase_id"
        const val EXTRA_PURCHASE_NAME = "purchase_name"
        const val EXTRA_IS_NEW = "is_new"
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, mode: Mode): Intent =
            Intent(context, ProductEditorActivity::class.java).putExtra(EXTRA_MODE, mode.name)

        fun editIntent(context: Context, purchaseId: String): Intent =
            Intent(context, ProductEditorActivity::class.java).putExtra(EXTRA_PURCHASE_ID, purchaseId)
    }
}
