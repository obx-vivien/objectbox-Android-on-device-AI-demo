package com.screenshotsearcher.infra.mlkit

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.screenshotsearcher.core.model.ImageLabel

object ImageLabelerEngine {
    fun label(bitmap: Bitmap, maxResults: Int = 10): List<ImageLabel> {
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.0f)
                .build()
        )
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(labeler.process(image))
            result.sortedByDescending { it.confidence }
                .take(maxResults)
                .map { ImageLabel(it.text, it.confidence) }
        } catch (e: Exception) {
            emptyList()
        } finally {
            labeler.close()
        }
    }
}
