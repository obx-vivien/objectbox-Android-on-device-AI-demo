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
    var ocrText: String = "",
    @HnswIndex(dimensions = 100, distanceType = VectorDistanceType.COSINE)
    var textEmbedding: FloatArray? = null,
    @HnswIndex(dimensions = 1024, distanceType = VectorDistanceType.COSINE)
    var imageEmbedding: FloatArray? = null
) {
    // Storage guardrail: never store full-resolution image bytes in this entity.
}
