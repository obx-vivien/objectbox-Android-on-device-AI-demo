package com.screenshotsearcher.core.pipeline

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

object Thumbnailer {
    fun generate(bitmap: Bitmap, maxSizePx: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSizePx && height <= maxSizePx) {
            return bitmap
        }
        val (targetW, targetH) = calculateTargetSize(width, height, maxSizePx)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun calculateTargetSize(width: Int, height: Int, maxSizePx: Int): Pair<Int, Int> {
        val scale = maxSizePx.toFloat() / max(width, height).toFloat()
        val targetW = (width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (height * scale).roundToInt().coerceAtLeast(1)
        return targetW to targetH
    }
}
