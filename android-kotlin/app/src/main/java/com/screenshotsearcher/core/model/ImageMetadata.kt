package com.screenshotsearcher.core.model

data class ImageMetadata(
    val displayName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int? = null,
    val dateTaken: Long? = null,
    val dateModified: Long? = null,
    val album: String? = null,
    val durationMs: Long? = null
)
