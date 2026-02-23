package com.screenshotsearcher.infra.image

import android.graphics.Bitmap
import kotlin.math.roundToInt

object DominantColorExtractor {
    fun extract(bitmap: Bitmap, maxColors: Int = 5): IntArray {
        if (bitmap.width == 0 || bitmap.height == 0) return intArrayOf()
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val counts = HashMap<Int, Int>()
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        for (pixel in pixels) {
            val alpha = (pixel shr 24) and 0xFF
            if (alpha < 16) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val bucket = quantize(r, g, b)
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(maxColors)
            .map { it.key }
            .toIntArray()
    }

    private fun quantize(r: Int, g: Int, b: Int): Int {
        val qr = (r / 16.0).roundToInt().coerceIn(0, 15)
        val qg = (g / 16.0).roundToInt().coerceIn(0, 15)
        val qb = (b / 16.0).roundToInt().coerceIn(0, 15)
        val rr = qr * 16
        val gg = qg * 16
        val bb = qb * 16
        return (rr shl 16) or (gg shl 8) or bb
    }
}
