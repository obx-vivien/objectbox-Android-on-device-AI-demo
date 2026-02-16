package com.screenshotsearcher.infra.mediapipe

import android.content.Context
import android.graphics.Bitmap

class ImageEmbedderWrapper private constructor() {

    fun embed(bitmap: Bitmap): FloatArray {
        throw UnsupportedOperationException("Image embedder not available in textOnly flavor")
    }

    fun close() = Unit

    companion object {
        fun create(context: Context): ImageEmbedderWrapper {
            throw UnsupportedOperationException("Image embedder not available in textOnly flavor")
        }

        fun l2Norm(vector: FloatArray): Double {
            throw UnsupportedOperationException("Image embedder not available in textOnly flavor")
        }
    }
}
