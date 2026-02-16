package com.screenshotsearcher.infra.mlkit

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OcrEngine {
    fun recognizeText(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(image))
            result.text
        } catch (e: Exception) {
            ""
        } finally {
            recognizer.close()
        }
    }
}
