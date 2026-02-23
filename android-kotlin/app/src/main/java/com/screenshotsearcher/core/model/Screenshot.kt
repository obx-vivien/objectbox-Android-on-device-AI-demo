package com.screenshotsearcher.core.model

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

@Entity
class Screenshot(
    @Id var id: Long = 0,
    var originalUri: String = "",
    var thumbnailBytes: ByteArray = byteArrayOf(),
    var createdAt: Long = System.currentTimeMillis(),
    @Convert(converter = IndexingStatusConverter::class, dbType = Int::class)
    var indexingStatus: IndexingStatus = IndexingStatus.INDEXED,
    var displayName: String? = null,
    var mimeType: String? = null,
    var sizeBytes: Long? = null,
    var ocrText: String = "",
    @Convert(converter = LabelListConverter::class, dbType = String::class)
    var labels: List<ImageLabel> = emptyList(),
    @Convert(converter = IntListConverter::class, dbType = String::class)
    var dominantColors: List<Int> = emptyList(),
    var description: String? = null,
    @HnswIndex(dimensions = 100, distanceType = VectorDistanceType.COSINE)
    var captionEmbedding: FloatArray? = null,
    @HnswIndex(dimensions = 100, distanceType = VectorDistanceType.COSINE)
    var textEmbedding: FloatArray? = null,
    @Convert(converter = IndexingStageConverter::class, dbType = Int::class)
    var lastCompletedStage: IndexingStage = IndexingStage.NONE,
    var width: Int = 0,
    var height: Int = 0,
    var orientation: Int? = null,
    var dateTaken: Long? = null,
    var dateModified: Long? = null,
    var dateImported: Long = System.currentTimeMillis(),
    var album: String? = null,
    var durationMs: Long? = null
) {
    // Storage guardrail: never store full-resolution image bytes in this entity.
}
