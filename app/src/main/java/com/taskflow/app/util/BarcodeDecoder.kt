package com.taskflow.app.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Decodifica EAN/UPC/Code128 de uma foto usando ZXing, 100% offline. */
object BarcodeDecoder {

    private val formats = listOf(
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.ITF,
        BarcodeFormat.QR_CODE
    )

    fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to formats
        )

        val normal = runCatching {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        }.getOrNull()
        if (!normal.isNullOrBlank()) return normal

        return runCatching {
            val inverted = BinaryBitmap(HybridBinarizer(source.invert()))
            MultiFormatReader().decode(inverted, hints).text
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
