package com.promocodeapp.ui.components

import android.graphics.Bitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.promocodeapp.core.model.BarcodeFormat
import com.google.zxing.BarcodeFormat as ZxingFormat

object BarcodeRenderer {
    /** @return the module matrix, or null if [content] is not valid for [format]. */
    fun encode(content: String, format: BarcodeFormat): BitMatrix? = runCatching {
        // Size 0 asks ZXing for the smallest exact matrix (one cell per module).
        MultiFormatWriter().encode(content, format.toZxing(), 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    }.getOrNull()

    fun render(content: String, format: BarcodeFormat): Bitmap? {
        val matrix = encode(content, format) ?: return null
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) BLACK else WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    private fun BarcodeFormat.toZxing(): ZxingFormat = ZxingFormat.valueOf(name)

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
