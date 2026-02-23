package com.screenshotsearcher.core.data

import com.screenshotsearcher.core.model.Screenshot

data class MetadataFilters(
    val nameContains: String = "",
    val mimeTypeContains: String = "",
    val albumContains: String = "",
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val minWidth: Int? = null,
    val maxWidth: Int? = null,
    val minHeight: Int? = null,
    val maxHeight: Int? = null,
    val orientation: Int? = null,
    val minDurationMs: Long? = null,
    val maxDurationMs: Long? = null,
    val dateTakenFrom: Long? = null,
    val dateTakenTo: Long? = null,
    val dateModifiedFrom: Long? = null,
    val dateModifiedTo: Long? = null
) {
    fun matches(screenshot: Screenshot): Boolean {
        if (nameContains.isNotBlank()) {
            val name = screenshot.displayName ?: ""
            if (!name.contains(nameContains, ignoreCase = true)) return false
        }
        if (mimeTypeContains.isNotBlank()) {
            val mime = screenshot.mimeType ?: ""
            if (!mime.contains(mimeTypeContains, ignoreCase = true)) return false
        }
        if (albumContains.isNotBlank()) {
            val album = screenshot.album ?: ""
            if (!album.contains(albumContains, ignoreCase = true)) return false
        }
        val size = screenshot.sizeBytes
        if (minSizeBytes != null && (size == null || size < minSizeBytes)) return false
        if (maxSizeBytes != null && (size == null || size > maxSizeBytes)) return false
        if (minWidth != null && screenshot.width < minWidth) return false
        if (maxWidth != null && screenshot.width > maxWidth) return false
        if (minHeight != null && screenshot.height < minHeight) return false
        if (maxHeight != null && screenshot.height > maxHeight) return false
        if (orientation != null && screenshot.orientation != orientation) return false
        val duration = screenshot.durationMs
        if (minDurationMs != null && (duration == null || duration < minDurationMs)) return false
        if (maxDurationMs != null && (duration == null || duration > maxDurationMs)) return false
        val dateTaken = screenshot.dateTaken
        if (dateTakenFrom != null && (dateTaken == null || dateTaken < dateTakenFrom)) return false
        if (dateTakenTo != null && (dateTaken == null || dateTaken > dateTakenTo)) return false
        val dateModified = screenshot.dateModified
        if (dateModifiedFrom != null && (dateModified == null || dateModified < dateModifiedFrom)) return false
        if (dateModifiedTo != null && (dateModified == null || dateModified > dateModifiedTo)) return false
        return true
    }
}
