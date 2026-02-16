package com.screenshotsearcher.infra.mediapipe

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlin.math.sqrt

class TextEmbedderWrapper private constructor(private val embedder: TextEmbedder) {

    fun embed(text: String): FloatArray {
        val result = embedder.embed(text)
        val embedding = result.embeddingResult().embeddings().first().floatEmbedding()
        val norm = l2Norm(embedding)
        check(kotlin.math.abs(norm - 1.0) <= 0.01) {
            "Expected l2-normalized embedding (~1.0), got $norm"
        }
        return embedding
    }

    fun close() {
        embedder.close()
    }

    companion object {
        private const val MODEL_ASSET_PATH = "models/text_embedder.tflite"

        fun create(context: Context): TextEmbedderWrapper {
            try {
                System.loadLibrary("mediapipe_tasks_text_jni")
            } catch (_: UnsatisfiedLinkError) {
                // Library should already be loaded by TextEmbedder; ignore if not found here.
            }
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET_PATH)
                        .build()
                )
                .setL2Normalize(true)
                .build()
            val embedder = TextEmbedder.createFromOptions(context, options)
            return TextEmbedderWrapper(embedder)
        }

        fun l2Norm(vector: FloatArray): Double {
            var sum = 0.0
            for (v in vector) {
                sum += (v * v).toDouble()
            }
            return sqrt(sum)
        }
    }
}
