package com.taskflow.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.EAN13Writer
import com.taskflow.app.util.BarcodeDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarcodeTest {

    @Test
    fun decodesEan13FromPhotoBitmap() {
        val code = "7891000315507"
        val matrix = EAN13Writer().encode(code, BarcodeFormat.EAN_13, 720, 320)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        assertEquals(code, BarcodeDecoder.decode(bitmap))
    }

}
