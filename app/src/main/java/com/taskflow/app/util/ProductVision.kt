package com.taskflow.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.taskflow.app.data.AiConfig
import com.taskflow.app.data.ProductGuess
import com.taskflow.app.net.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Reconhecimento de produtos:
 * 1) Gemini Vision (se houver chave) para entender a foto;
 * 2) OCR local (ML Kit) como fallback offline;
 * 3) código de barras + Open Food Facts para nome/marca.
 */
object ProductVision {

    suspend fun identify(context: Context, path: String): ProductGuess? = withContext(Dispatchers.IO) {
        if (AiConfig.isEnabled(context)) {
            val bytes = prepareImage(path)
            if (bytes != null) {
                val guess = GeminiClient.identifyProduct(context, bytes).getOrNull()
                if (guess != null) return@withContext guess
            }
        }
        val text = recognizeText(context, path) ?: return@withContext null
        guessFromText(text)
    }

    suspend fun recognizeText(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = Images.load(path, 2000) ?: return@runCatching null
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                Tasks.await(recognizer.process(image)).text
            } finally {
                recognizer.close()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    suspend fun scanBarcode(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            // Bitmap com rotação EXIF já aplicada: mais confiável que file:// URI
            // em aparelhos novos e em fotos giradas pela câmera.
            val bitmap = Images.load(path, 2000) ?: return@runCatching null

            // 1) ZXing: offline e independente do Google Play Services.
            BarcodeDecoder.decode(bitmap)?.let { return@runCatching it }

            // 2) ML Kit como reforço (quando o módulo do Play Services está disponível).
            val image = InputImage.fromBitmap(bitmap, 0)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            try {
                Tasks.await(scanner.process(image))
                    .mapNotNull { it.rawValue }
                    .firstOrNull { it.isNotBlank() && it.length in 4..80 }
            } finally {
                scanner.close()
            }
        }.getOrNull()
    }

    /** Consulta gratuita do Open Food Facts (nome/marca a partir do EAN). */
    suspend fun lookupBarcode(ean: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$ean.json?fields=product_name,brands")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "TaskFlow/1.2 (Android)")
            }
            try {
                if (connection.responseCode != 200) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                if (root.optInt("status", 0) != 1) return@runCatching null
                val product = root.optJSONObject("product") ?: return@runCatching null
                val brand = product.optString("brands").substringBefore(',').trim()
                val name = product.optString("product_name").trim()
                val joined = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ").trim()
                joined.takeIf { it.isNotBlank() }?.take(80)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun guessFromText(raw: String): ProductGuess? {
        val lines = raw.lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 3..70 }
        if (lines.isEmpty()) return null

        val blocked = listOf(
            "cnpj", "cpf", "total", "subtotal", "desconto", "cupom", "obrigado", "troco",
            "dinheiro", "cartao", "cartão", "data", "hora", "supermercado", "mercado",
            "ltda", "nota fiscal", "quantidade", "unidade", "valor", "pix", "nf-e", "nfce"
        )
        val candidates = lines.filter { line ->
            val lower = line.lowercase()
            line.count { it.isLetter() } >= 3 &&
                blocked.none { lower.contains(it) } &&
                !lower.matches(Regex(".*\\d{4,}.*"))
        }
        val best = candidates.maxByOrNull { it.count { c -> c.isLetter() } } ?: lines.first()

        val pricePatterns = listOf(
            Regex("(?i)R\\$\\s*(\\d{1,4}(?:[.,]\\d{2}))"),
            Regex("\\b(\\d{1,4}[.,]\\d{2})\\b")
        )
        var cents: Long? = null
        for (pattern in pricePatterns) {
            val match = pattern.find(raw)
            if (match != null) {
                cents = match.groupValues[1]
                    .replace(".", "")
                    .replace(',', '.')
                    .toDoubleOrNull()
                    ?.let { (it * 100).toLong() }
                    ?.takeIf { it in 1..999_999 }
                if (cents != null) break
            }
        }

        val cleanName = best
            .replace(Regex("^[^A-Za-zÀ-ÿ]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ',', '-', ':', ';')
            .take(70)
        if (cleanName.isBlank()) return null

        return ProductGuess(Dates.capitalize(cleanName), cents, "ocr")
    }

    private fun prepareImage(path: String): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
        var bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = 1200f / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        stream.toByteArray()
    }.getOrNull()

    @Suppress("unused")
    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
