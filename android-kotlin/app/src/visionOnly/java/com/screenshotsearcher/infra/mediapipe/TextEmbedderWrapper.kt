package com.screenshotsearcher.infra.mediapipe

import android.content.Context

class TextEmbedderWrapper private constructor() {

    fun embed(text: String): FloatArray {
        throw UnsupportedOperationException("Text embedder not available in visionOnly flavor")
    }

    fun close() = Unit

    companion object {
        fun create(context: Context): TextEmbedderWrapper {
            throw UnsupportedOperationException("Text embedder not available in visionOnly flavor")
        }

        fun l2Norm(vector: FloatArray): Double {
            throw UnsupportedOperationException("Text embedder not available in visionOnly flavor")
        }
    }
}
