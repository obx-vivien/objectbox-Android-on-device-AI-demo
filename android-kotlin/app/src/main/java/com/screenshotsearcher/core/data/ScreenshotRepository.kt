package com.screenshotsearcher.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder as AndroidImageDecoder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.screenshotsearcher.core.model.IndexingStage
import com.screenshotsearcher.core.model.IndexingStatus
import com.screenshotsearcher.core.model.ImageLabel
import com.screenshotsearcher.core.model.ImageMetadata
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.Screenshot_
import com.screenshotsearcher.core.pipeline.Thumbnailer
import com.screenshotsearcher.infra.image.ImageDecoder
import com.screenshotsearcher.infra.image.MetadataExtractor
import com.screenshotsearcher.infra.image.DominantColorExtractor
import com.screenshotsearcher.infra.captioning.GemmaCaptioner
import com.screenshotsearcher.infra.mediapipe.TextEmbedderWrapper
import com.screenshotsearcher.infra.mlkit.ImageLabelerEngine
import com.screenshotsearcher.infra.mlkit.OcrEngine
import io.objectbox.Box
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream

class ScreenshotRepository(private val box: Box<Screenshot>) {
    private val _screenshots = MutableStateFlow<List<Screenshot>>(emptyList())
    val screenshots: StateFlow<List<Screenshot>> = _screenshots
    private val _indexingStats = MutableStateFlow(IndexingStats())
    val indexingStats: StateFlow<IndexingStats> = _indexingStats
    private val _seedProgress = MutableStateFlow(SeedProgress())
    val seedProgress: StateFlow<SeedProgress> = _seedProgress
    private val _captionProgress = MutableStateFlow(CaptionProgress())
    val captionProgress: StateFlow<CaptionProgress> = _captionProgress
    private val captionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val captionMutex = Mutex()
    init {
        refresh()
    }

    fun refresh() {
        _screenshots.value = box.query().orderDesc(Screenshot_.createdAt).build().find()
        updateIndexingStats()
    }

    private fun updateIndexingStats() {
        val queued = box.query(Screenshot_.indexingStatus.equal(IndexingStatus.QUEUED.ordinal)).build().count().toInt()
        val indexed = box.query(Screenshot_.indexingStatus.equal(IndexingStatus.INDEXED.ordinal)).build().count().toInt()
        val failed = box.query(Screenshot_.indexingStatus.equal(IndexingStatus.FAILED.ordinal)).build().count().toInt()
        val cancelled = box.query(Screenshot_.indexingStatus.equal(IndexingStatus.CANCELLED.ordinal)).build().count().toInt()
        _indexingStats.value = IndexingStats(
            queued = queued,
            indexed = indexed,
            failed = failed,
            cancelled = cancelled
        )
    }

    suspend fun seedFromAssetsIfEmpty(context: Context, thumbnailMaxPx: Int) {
        val assets = context.assets
        val rootFiles = assets.list("")?.toList().orEmpty()
        val sampleFiles = assets.list("samples")?.map { "samples/$it" }.orEmpty()
        val allFiles = (rootFiles + sampleFiles).filter { it.isImageFile() }
        val baseToFile = mutableMapOf<String, String>()
        for (file in allFiles) {
            val lower = file.lowercase()
            val base = lower.substringAfterLast('/').substringBeforeLast('.')
            val isHeic = lower.endsWith(".heic") || lower.endsWith(".heif")
            val preferExisting = baseToFile[base]
            if (preferExisting == null) {
                baseToFile[base] = file
            } else {
                val existingLower = preferExisting.lowercase()
                val existingIsHeic = existingLower.endsWith(".heic") || existingLower.endsWith(".heif")
                if (existingIsHeic && !isHeic) {
                    baseToFile[base] = file
                }
            }
        }
        val dedupedFiles = baseToFile.values.toList()
        Log.i("Seed", "assets root count=${rootFiles.size}, samples count=${sampleFiles.size}, image count=${allFiles.size}")
        if (allFiles.isEmpty()) {
            refresh()
            return
        }
        val existingAssetUris = box.query(Screenshot_.originalUri.startsWith("asset://"))
            .build()
            .find()
            .map { it.originalUri.removePrefix("asset://") }
            .toSet()
        val files = dedupedFiles.filterNot { existingAssetUris.contains(it) }
        if (files.isEmpty()) {
            refresh()
            return
        }
        var inserted = 0
        var index = 0
        _seedProgress.value = SeedProgress(total = files.size, processed = 0, currentFile = null, isRunning = true)
        _captionProgress.value = CaptionProgress(total = files.size, completed = 0, inFlight = 0, isRunning = files.isNotEmpty())
        for (file in files) {
            index += 1
            Log.i("Seed", "seeding $index/${files.size}: $file")
            _seedProgress.value = _seedProgress.value.copy(processed = index, currentFile = file, isRunning = true)
            try {
                val bitmap = decodeAssetBitmap(assets, file) ?: run {
                    Log.w("Seed", "Failed to decode asset: $file")
                    continue
                }
                val thumb = runSeedStage("thumbnail", file) {
                    Thumbnailer.generate(bitmap, thumbnailMaxPx)
                }
                val ocrText = runSeedStage("ocr", file) {
                    OcrEngine.recognizeText(bitmap)
                }
                val labels = runSeedStage("labeling", file) {
                    labelImage(bitmap)
                }
                val metadata = runSeedStage("metadata", file) {
                    MetadataExtractor.fromAsset(context, file, bitmap)
                }
                val colors = runSeedStage("colors", file) {
                    DominantColorExtractor.extract(bitmap).toList()
                }
                val description: String? = null
                val captionEmbedding: FloatArray? = null
                val textEmbedding = runSeedStage("textEmbedding", file) {
                    embedTextIfAvailable(context, ocrText)
                }
                val bytes = thumb.toJpegBytes()
                val screenshot = Screenshot(
                    originalUri = "asset://$file",
                    thumbnailBytes = bytes,
                    createdAt = System.currentTimeMillis(),
                    indexingStatus = IndexingStatus.INDEXED,
                    displayName = metadata.displayName,
                    mimeType = metadata.mimeType,
                    sizeBytes = metadata.sizeBytes,
                    ocrText = ocrText,
                    labels = labels,
                    dominantColors = colors,
                    description = description,
                    captionEmbedding = captionEmbedding,
                    textEmbedding = textEmbedding,
                    lastCompletedStage = IndexingStage.DONE,
                    width = metadata.width,
                    height = metadata.height,
                    orientation = metadata.orientation,
                    dateTaken = metadata.dateTaken,
                    dateModified = metadata.dateModified,
                    dateImported = System.currentTimeMillis(),
                    album = metadata.album,
                    durationMs = metadata.durationMs
                )
                box.put(screenshot)
                inserted += 1
                refresh()
                Log.i("Seed", "seeded $index/${files.size}: $file")

                if (index <= SEED_CAPTION_LIMIT) {
                    val seededId = screenshot.id
                    _captionProgress.update { current ->
                        current.copy(inFlight = current.inFlight + 1, isRunning = true)
                    }
                    captionScope.launch {
                        val seededDescription = runSeedStage("caption", file) {
                            withTimeoutOrNull(SEED_CAPTION_TIMEOUT_MS) {
                                captionImageIfAvailable(context, bitmap)
                            } ?: run {
                                Log.w("Seed", "caption timeout after ${SEED_CAPTION_TIMEOUT_MS}ms: $file")
                                null
                            }
                        }
                        if (!seededDescription.isNullOrBlank()) {
                            val seededCaptionEmbedding = runSeedStage("captionEmbedding", file) {
                                embedTextIfAvailable(context, seededDescription)
                            }
                            val updated = box.get(seededId)
                            if (updated != null) {
                                updated.description = seededDescription
                                updated.captionEmbedding = seededCaptionEmbedding
                                box.put(updated)
                                refresh()
                            }
                        }
                        _captionProgress.update { current ->
                            val completed = current.completed + 1
                            val inFlight = (current.inFlight - 1).coerceAtLeast(0)
                            current.copy(
                                completed = completed,
                                inFlight = inFlight,
                                isRunning = completed < current.total
                            )
                        }
                    }
                } else {
                    Log.i("Seed", "caption skipped for seed item $index/${files.size}: $file")
                    _captionProgress.update { current ->
                        val completed = current.completed + 1
                        current.copy(completed = completed, isRunning = completed < current.total)
                    }
                }
            } catch (e: Exception) {
                Log.e("Seed", "Failed to seed asset: $file", e)
                _captionProgress.update { current ->
                    if (current.total == 0) {
                        current
                    } else {
                        val completed = current.completed + 1
                        current.copy(completed = completed, isRunning = completed < current.total)
                    }
                }
            }
        }
        Log.i("Seed", "seeded $inserted items, total=${box.count()}")
        _seedProgress.value = _seedProgress.value.copy(isRunning = false, currentFile = null)
        refresh()
    }

    fun enqueueCaptionBackfill(context: Context) {
        val pending = box.all.filter { screenshot ->
            screenshot.description.isNullOrBlank() || screenshot.captionEmbedding == null
        }
        if (pending.isEmpty()) {
            return
        }
        _captionProgress.value = CaptionProgress(
            total = pending.size,
            completed = 0,
            inFlight = 1,
            isRunning = true
        )
        captionScope.launch {
            var completed = 0
            for (screenshot in pending) {
                val bitmap = loadBitmapForCaption(context, screenshot.originalUri)
                if (bitmap != null) {
                    val description = if (screenshot.description.isNullOrBlank()) {
                        captionImageIfAvailable(context, bitmap)
                    } else {
                        screenshot.description
                    }
                    val captionEmbedding = if (!description.isNullOrBlank()) {
                        embedTextIfAvailable(context, description)
                    } else {
                        null
                    }
                    if (!description.isNullOrBlank() || captionEmbedding != null) {
                        val updated = box.get(screenshot.id)
                        if (updated != null) {
                            if (!description.isNullOrBlank()) {
                                updated.description = description
                            }
                            if (captionEmbedding != null) {
                                updated.captionEmbedding = captionEmbedding
                            }
                            box.put(updated)
                            refresh()
                        }
                    }
                }
                completed += 1
                _captionProgress.update { current ->
                    current.copy(
                        completed = completed,
                        isRunning = completed < current.total
                    )
                }
            }
            _captionProgress.update { current ->
                current.copy(inFlight = 0, isRunning = false)
            }
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri, thumbnailMaxPx: Int) {
        val bitmap = ImageDecoder.decodeNormalized(context, uri) ?: return
        val thumb = Thumbnailer.generate(bitmap, thumbnailMaxPx)
        val ocrText = OcrEngine.recognizeText(bitmap)
        val labels = labelImage(bitmap)
        val metadata = MetadataExtractor.fromUri(context, uri, bitmap)
        val colors = DominantColorExtractor.extract(bitmap).toList()
        val description = captionImageIfAvailable(context, bitmap)
        val captionEmbedding = embedTextIfAvailable(context, description ?: "")
        val textEmbedding = embedTextIfAvailable(context, ocrText)
        val bytes = thumb.toJpegBytes()
        val screenshot = Screenshot(
            originalUri = uri.toString(),
            thumbnailBytes = bytes,
            createdAt = System.currentTimeMillis(),
            indexingStatus = IndexingStatus.INDEXED,
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            ocrText = ocrText,
            labels = labels,
            dominantColors = colors,
            description = description,
            captionEmbedding = captionEmbedding,
            textEmbedding = textEmbedding,
            lastCompletedStage = IndexingStage.DONE,
            width = metadata.width,
            height = metadata.height,
            orientation = metadata.orientation,
            dateTaken = metadata.dateTaken,
            dateModified = metadata.dateModified,
            dateImported = System.currentTimeMillis(),
            album = metadata.album,
            durationMs = metadata.durationMs
        )
        box.put(screenshot)
        refresh()
    }

    suspend fun enqueueImportFromUri(context: Context, uri: Uri, thumbnailMaxPx: Int) {
        val bitmap = ImageDecoder.decodeNormalized(context, uri) ?: return
        val thumb = Thumbnailer.generate(bitmap, thumbnailMaxPx)
        val metadata = MetadataExtractor.fromUri(context, uri, bitmap)
        val bytes = thumb.toJpegBytes()
        val screenshot = Screenshot(
            originalUri = uri.toString(),
            thumbnailBytes = bytes,
            createdAt = System.currentTimeMillis(),
            indexingStatus = IndexingStatus.QUEUED,
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            ocrText = "",
            labels = emptyList(),
            dominantColors = emptyList(),
            description = null,
            captionEmbedding = null,
            textEmbedding = null,
            lastCompletedStage = IndexingStage.NONE,
            width = metadata.width,
            height = metadata.height,
            orientation = metadata.orientation,
            dateTaken = metadata.dateTaken,
            dateModified = metadata.dateModified,
            dateImported = System.currentTimeMillis(),
            album = metadata.album,
            durationMs = metadata.durationMs
        )
        box.put(screenshot)
        refresh()
    }

    suspend fun processQueuedScreenshot(
        context: Context,
        screenshot: Screenshot,
        config: com.screenshotsearcher.core.model.ModuleConfig
    ): Boolean {
        val bitmap = loadBitmapForCaption(context, screenshot.originalUri) ?: run {
            screenshot.indexingStatus = IndexingStatus.FAILED
            box.put(screenshot)
            refresh()
            return false
        }
        return try {
            val updated = box.get(screenshot.id) ?: screenshot
            var currentStage = updated.lastCompletedStage

            fun isStageDone(stage: IndexingStage): Boolean {
                return currentStage.ordinal >= stage.ordinal
            }

            fun markStage(stage: IndexingStage) {
                updated.lastCompletedStage = stage
                box.put(updated)
                currentStage = stage
            }

            if (!isStageDone(IndexingStage.THUMBNAIL)) {
                val thumb = Thumbnailer.generate(bitmap, 256)
                updated.thumbnailBytes = thumb.toJpegBytes()
                markStage(IndexingStage.THUMBNAIL)
            }

            if (!isStageDone(IndexingStage.METADATA)) {
                val metadata = MetadataExtractor.fromUri(context, Uri.parse(screenshot.originalUri), bitmap)
                updated.displayName = metadata.displayName
                updated.mimeType = metadata.mimeType
                updated.sizeBytes = metadata.sizeBytes
                updated.width = metadata.width
                updated.height = metadata.height
                updated.orientation = metadata.orientation
                updated.dateTaken = metadata.dateTaken
                updated.dateModified = metadata.dateModified
                updated.album = metadata.album
                updated.durationMs = metadata.durationMs
                markStage(IndexingStage.METADATA)
            }

            if (!isStageDone(IndexingStage.COLORS)) {
                updated.dominantColors = DominantColorExtractor.extract(bitmap).toList()
                markStage(IndexingStage.COLORS)
            }

            if (!isStageDone(IndexingStage.OCR)) {
                updated.ocrText = if (config.ocrEnabled) OcrEngine.recognizeText(bitmap) else ""
                markStage(IndexingStage.OCR)
            }

            if (!isStageDone(IndexingStage.LABELS)) {
                updated.labels = if (config.labelingEnabled) labelImage(bitmap) else emptyList()
                markStage(IndexingStage.LABELS)
            }

            if (!isStageDone(IndexingStage.DESCRIPTION)) {
                updated.description = if (config.llmEnabled) captionImageIfAvailable(context, bitmap) else null
                markStage(IndexingStage.DESCRIPTION)
            }

            if (!isStageDone(IndexingStage.EMBEDDINGS)) {
                updated.textEmbedding = if (config.textEmbeddingsEnabled) {
                    embedTextIfAvailable(context, updated.ocrText)
                } else {
                    null
                }
                updated.captionEmbedding = if (config.textEmbeddingsEnabled && !updated.description.isNullOrBlank()) {
                    embedTextIfAvailable(context, updated.description ?: "")
                } else {
                    null
                }
                markStage(IndexingStage.EMBEDDINGS)
            }

            updated.indexingStatus = IndexingStatus.INDEXED
            updated.lastCompletedStage = IndexingStage.DONE
            box.put(updated)
            true
        } catch (_: Exception) {
            screenshot.indexingStatus = IndexingStatus.FAILED
            box.put(screenshot)
            refresh()
            false
        }
    }

    fun reindexAll() {
        val all = box.all
        if (all.isEmpty()) return
        all.forEach { screenshot ->
            screenshot.indexingStatus = IndexingStatus.QUEUED
            screenshot.lastCompletedStage = IndexingStage.NONE
            screenshot.ocrText = ""
            screenshot.labels = emptyList()
            screenshot.dominantColors = emptyList()
            screenshot.description = null
            screenshot.captionEmbedding = null
            screenshot.textEmbedding = null
        }
        box.put(all)
        refresh()
    }

    fun clearAll() {
        box.removeAll()
        refresh()
    }

    fun searchByKeywordOcr(query: String): List<Screenshot> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return box.query().orderDesc(Screenshot_.createdAt).build().find()
        }
        return box.query(
            Screenshot_.ocrText.contains(trimmed, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        ).orderDesc(Screenshot_.createdAt).build().find()
    }

    fun searchByKeywordDescription(query: String): List<Screenshot> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return box.query().orderDesc(Screenshot_.createdAt).build().find()
        }
        return box.query(
            Screenshot_.description.contains(trimmed, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        ).orderDesc(Screenshot_.createdAt).build().find()
    }

    fun searchBySemanticText(context: Context, query: String, limit: Int = 50): List<SemanticResult> {
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
            if (score >= SEMANTIC_MIN_SCORE) {
                SemanticResult(item.get(), score)
            } else {
                null
            }
        }
    }

    fun searchBySemanticDescription(context: Context, query: String, limit: Int = 50): List<SemanticResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val embedding = embedTextIfAvailable(context, trimmed) ?: return emptyList()
        val queryResult = box.query()
            .nearestNeighbors(Screenshot_.captionEmbedding, embedding, limit)
            .build()
            .findWithScores()
        return queryResult.mapNotNull { item ->
            val score = (1.0 - item.score).coerceIn(0.0, 1.0)
            if (score >= SEMANTIC_MIN_SCORE) {
                SemanticResult(item.get(), score)
            } else {
                null
            }
        }
    }


    fun availableLabels(minConfidence: Float = LABEL_DISPLAY_CONFIDENCE): List<String> {
        val labels = mutableSetOf<String>()
        val screenshots = box.all
        screenshots.forEach { screenshot ->
            screenshot.labels.forEach { label ->
                if (label.confidence >= minConfidence) {
                    labels.add(label.text)
                }
            }
        }
        return labels.sorted()
    }

    fun filterByLabels(
        results: List<Screenshot>,
        selectedLabels: Set<String>,
        minConfidence: Float = LABEL_DISPLAY_CONFIDENCE
    ): List<Screenshot> {
        if (selectedLabels.isEmpty()) return results
        val normalized = selectedLabels.map { it.lowercase() }.toSet()
        return results.filter { screenshot ->
            screenshot.labels.any { label ->
                label.confidence >= minConfidence && normalized.contains(label.text.lowercase())
            }
        }
    }

    fun filterByMetadata(results: List<Screenshot>, filters: MetadataFilters): List<Screenshot> {
        return results.filter { screenshot ->
            filters.matches(screenshot)
        }
    }

    private fun labelImage(bitmap: Bitmap): List<ImageLabel> {
        return ImageLabelerEngine.label(bitmap, maxResults = MAX_LABELS)
    }

    private fun decodeAssetBitmap(assets: android.content.res.AssetManager, path: String): Bitmap? {
        val lower = path.lowercase()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (lower.endsWith(".heic") || lower.endsWith(".heif"))
        ) {
            val source = AndroidImageDecoder.createSource(assets, path)
            AndroidImageDecoder.decodeBitmap(source)
        } else {
            assets.open(path).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }

    private suspend fun <T> runSeedStage(stage: String, file: String, block: suspend () -> T): T {
        Log.i("Seed", "stage start: $stage ($file)")
        val start = SystemClock.elapsedRealtime()
        val result = block()
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.i("Seed", "stage end: $stage ($file) ${elapsed}ms")
        return result
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


    private suspend fun captionImageIfAvailable(context: Context, bitmap: Bitmap): String? {
        return captionMutex.withLock {
            val captioner = try {
                GemmaCaptioner.create(context)
            } catch (e: Exception) {
                return@withLock null
            }
            try {
                captioner.caption(bitmap)?.ifBlank { null }
            } catch (e: Exception) {
                null
            } finally {
                captioner.close()
            }
        }
    }

    private fun loadBitmapForCaption(context: Context, originalUri: String): Bitmap? {
        return if (originalUri.startsWith("asset://")) {
            val assetPath = originalUri.removePrefix("asset://")
            decodeAssetBitmap(context.assets, assetPath)
        } else {
            val uri = runCatching { Uri.parse(originalUri) }.getOrNull() ?: return null
            ImageDecoder.decodeNormalized(context, uri)
        }
    }

    companion object {
        private const val MAX_LABELS = 10
        const val LABEL_DISPLAY_CONFIDENCE = 0.70f
        const val SEMANTIC_MIN_SCORE = 0.80
        private const val SEED_CAPTION_TIMEOUT_MS = 8_000L
        private const val SEED_CAPTION_LIMIT = Int.MAX_VALUE
    }
}

data class IndexingStats(
    val queued: Int = 0,
    val indexed: Int = 0,
    val failed: Int = 0,
    val cancelled: Int = 0
)

data class SemanticResult(val screenshot: Screenshot, val score: Double)

data class SeedProgress(
    val total: Int = 0,
    val processed: Int = 0,
    val currentFile: String? = null,
    val isRunning: Boolean = false
)

data class CaptionProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val inFlight: Int = 0,
    val isRunning: Boolean = false
)

private fun String.isImageFile(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
}

private fun Bitmap.toJpegBytes(): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return stream.toByteArray()
}
