package com.screenshotsearcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.screenshotsearcher.core.model.IndexingStatus
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.Screenshot_
import com.screenshotsearcher.core.pipeline.Thumbnailer
import com.screenshotsearcher.infra.image.ImageDecoder
import com.screenshotsearcher.infra.mediapipe.ImageEmbedderWrapper
import com.screenshotsearcher.infra.mediapipe.TextEmbedderWrapper
import com.screenshotsearcher.infra.mlkit.OcrEngine
import io.objectbox.Box
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

class ScreenshotRepository(private val box: Box<Screenshot>) {
    private val _screenshots = MutableStateFlow<List<Screenshot>>(emptyList())
    val screenshots: StateFlow<List<Screenshot>> = _screenshots

    fun refresh() {
        _screenshots.value = box.query().orderDesc(Screenshot_.createdAt).build().find()
    }

    suspend fun seedFromAssetsIfEmpty(context: Context, thumbnailMaxPx: Int) {
        if (box.count() > 0) {
            refresh()
            return
        }
        val assets = context.assets
        val files = assets.list("")?.filter { it.isImageFile() }.orEmpty()
        if (files.isEmpty()) {
            refresh()
            return
        }
        for (file in files) {
            val bitmap = assets.open(file).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: continue
            val thumb = Thumbnailer.generate(bitmap, thumbnailMaxPx)
            val ocrText = OcrEngine.recognizeText(bitmap)
            val textEmbedding = embedTextIfAvailable(context, ocrText)
            val imageEmbedding = embedImageIfAvailable(context, bitmap)
            val bytes = thumb.toJpegBytes()
            val screenshot = Screenshot(
                originalUri = "asset://$file",
                thumbnailBytes = bytes,
                createdAt = System.currentTimeMillis(),
                indexingStatus = IndexingStatus.INDEXED,
                ocrText = ocrText,
                textEmbedding = textEmbedding,
                imageEmbedding = imageEmbedding
            )
            box.put(screenshot)
        }
        refresh()
    }

    suspend fun importFromUri(context: Context, uri: Uri, thumbnailMaxPx: Int) {
        val bitmap = ImageDecoder.decodeNormalized(context, uri) ?: return
        val thumb = Thumbnailer.generate(bitmap, thumbnailMaxPx)
        val ocrText = OcrEngine.recognizeText(bitmap)
        val textEmbedding = embedTextIfAvailable(context, ocrText)
        val imageEmbedding = embedImageIfAvailable(context, bitmap)
        val bytes = thumb.toJpegBytes()
        val screenshot = Screenshot(
            originalUri = uri.toString(),
            thumbnailBytes = bytes,
            createdAt = System.currentTimeMillis(),
            indexingStatus = IndexingStatus.INDEXED,
            ocrText = ocrText,
            textEmbedding = textEmbedding,
            imageEmbedding = imageEmbedding
        )
        box.put(screenshot)
        refresh()
    }

    fun searchByKeyword(query: String): List<Screenshot> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return box.query().orderDesc(Screenshot_.createdAt).build().find()
        }
        return box.query(
            Screenshot_.ocrText.contains(trimmed, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        ).orderDesc(Screenshot_.createdAt).build().find()
    }

    fun searchBySemantic(context: Context, query: String, limit: Int = 50): List<SemanticResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val embedding = embedTextIfAvailable(context, trimmed) ?: return emptyList()
        val queryResult = box.query()
            .nearestNeighbors(Screenshot_.textEmbedding, embedding, limit)
            .build()
            .findWithScores()
        return queryResult.mapNotNull { item ->
            val score = (1.0 - item.score).coerceIn(0.0, 1.0)
            if (score >= 0.25) {
                SemanticResult(item.get(), score)
            } else {
                null
            }
        }
    }

    fun searchByImageSimilarity(context: Context, bitmap: Bitmap, limit: Int = 50): List<ImageResult> {
        val embedding = embedImageIfAvailable(context, bitmap) ?: return emptyList()
        val queryResult = box.query()
            .nearestNeighbors(Screenshot_.imageEmbedding, embedding, limit)
            .build()
            .findWithScores()
        return queryResult.mapNotNull { item ->
            val score = (1.0 - item.score).coerceIn(0.0, 1.0)
            if (score >= 0.30) {
                ImageResult(item.get(), score)
            } else {
                null
            }
        }
    }

    private fun embedTextIfAvailable(context: Context, text: String): FloatArray? {
        if (text.isBlank()) return null
        val embedder = try {
            TextEmbedderWrapper.create(context)
        } catch (e: Exception) {
            return null
        }
        return try {
            embedder.embed(text)
        } catch (e: Exception) {
            null
        } finally {
            embedder.close()
        }
    }

    private fun embedImageIfAvailable(context: Context, bitmap: Bitmap): FloatArray? {
        val embedder = try {
            ImageEmbedderWrapper.create(context)
        } catch (e: Exception) {
            return null
        }
        return try {
            embedder.embed(bitmap)
        } catch (e: Exception) {
            null
        } finally {
            embedder.close()
        }
    }
}

data class SemanticResult(val screenshot: Screenshot, val score: Double)
data class ImageResult(val screenshot: Screenshot, val score: Double)

private fun String.isImageFile(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
}

private fun Bitmap.toJpegBytes(): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return stream.toByteArray()
}
