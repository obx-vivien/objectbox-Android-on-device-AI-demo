package com.screenshotsearcher.infra.captioning

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.Closeable
import java.io.File

class GemmaCaptioner private constructor(
    private val llmInference: LlmInference
) : Closeable {
    fun caption(bitmap: Bitmap): String? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(true)
                    .build()
            )
            .build()

        LlmInferenceSession.createFromOptions(llmInference, sessionOptions).use { session ->
            session.addQueryChunk(PROMPT)
            session.addImage(mpImage)
            val response = session.generateResponse() ?: return null
            return sanitize(response).ifBlank { null }
        }
    }

    override fun close() {
        llmInference.close()
    }

    companion object {
        private const val MODEL_PATH = "/data/local/tmp/llm/gemma-3n-E2B-it-int4.litertlm"
        private const val PROMPT = "Describe the image in one short sentence."
        private const val TOP_K = 20
        private const val TEMPERATURE = 0.3f
        private const val MAX_TOKENS = 512
        private const val MIN_MODEL_BYTES = 1L

        fun create(context: Context): GemmaCaptioner {
            val modelFile = File(MODEL_PATH)
            require(modelFile.exists() && modelFile.length() >= MIN_MODEL_BYTES) {
                "Gemma model not found at $MODEL_PATH. Push it with: adb push <path> $MODEL_PATH"
            }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .setMaxNumImages(1)
                .build()
            val llmInference = LlmInference.createFromOptions(context, options)
            return GemmaCaptioner(llmInference)
        }

        fun modelPath(): String = MODEL_PATH

        fun isModelAvailable(): Boolean {
            val modelFile = File(MODEL_PATH)
            return modelFile.exists() && modelFile.length() >= MIN_MODEL_BYTES
        }

        fun warmUp(context: Context): Result<Unit> {
            return runCatching {
                create(context).use { }
            }
        }

        fun release() {
            // No-op for now; captioner instances are short-lived.
        }

        private fun sanitize(text: String): String {
            val trimmed = text.trim()
            val truncated = trimmed
                .substringBefore("<end_of_turn>")
                .substringBefore("<eos>")
                .substringBefore("</s>")
                .substringBefore("###")
            return truncated.trim()
        }
    }
}
