package com.screenshotsearcher.infra.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import kotlin.math.sqrt

class ImageEmbedderWrapper private constructor(private val embedder: ImageEmbedder) {

    fun embed(bitmap: Bitmap): FloatArray {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = embedder.embed(mpImage)
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
        private const val MODEL_ASSET_PATH = "models/image_embedder.tflite"

        fun create(context: Context): ImageEmbedderWrapper {
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET_PATH)
                        .build()
                )
                .setL2Normalize(true)
                .build()
            val embedder = ImageEmbedder.createFromOptions(context, options)
            return ImageEmbedderWrapper(embedder)
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
