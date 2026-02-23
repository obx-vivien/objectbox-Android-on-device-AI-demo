package com.screenshotsearcher.infra.image

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.screenshotsearcher.core.model.ImageMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object MetadataExtractor {
    fun fromUri(context: Context, uri: Uri, bitmap: Bitmap): ImageMetadata {
        val contentResolver = context.contentResolver
        val exif = readExif(contentResolver, uri)
        val media = queryMediaStore(contentResolver, uri)
        val fileMeta = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            File(uri.path ?: "")
        } else {
            null
        }

        val displayName = media.displayName ?: fileMeta?.name
        val mimeType = media.mimeType ?: contentResolver.getType(uri)
        val sizeBytes = media.sizeBytes ?: fileMeta?.length()
        val dateTaken = exif.dateTaken ?: media.dateTaken
        val dateModified = media.dateModified ?: fileMeta?.lastModified()
        val album = media.album
        val durationMs = media.durationMs
        val orientation = exif.orientation
        val width = exif.width ?: media.width ?: bitmap.width
        val height = exif.height ?: media.height ?: bitmap.height

        return ImageMetadata(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            orientation = orientation,
            dateTaken = dateTaken,
            dateModified = dateModified,
            album = album,
            durationMs = durationMs
        )
    }

    fun fromAsset(context: Context, assetName: String, bitmap: Bitmap): ImageMetadata {
        val assetManager = context.assets
        val length = try {
            assetManager.openFd(assetName).length
        } catch (e: Exception) {
            null
        }
        val mimeType = when {
            assetName.endsWith(".jpg", true) || assetName.endsWith(".jpeg", true) -> "image/jpeg"
            assetName.endsWith(".png", true) -> "image/png"
            else -> null
        }
        return ImageMetadata(
            displayName = assetName,
            mimeType = mimeType,
            sizeBytes = length,
            width = bitmap.width,
            height = bitmap.height
        )
    }

    private fun readExif(contentResolver: ContentResolver, uri: Uri): ExifMetadata {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                val dateTaken = parseExifDate(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                )
                ExifMetadata(
                    orientation = if (orientation == ExifInterface.ORIENTATION_UNDEFINED) null else orientation,
                    width = width,
                    height = height,
                    dateTaken = dateTaken
                )
            } ?: ExifMetadata()
        } catch (e: Exception) {
            ExifMetadata()
        }
    }

    private fun parseExifDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            format.parse(value)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun queryMediaStore(contentResolver: ContentResolver, uri: Uri): MediaMetadata {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return MediaMetadata()
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        val cursor = try {
            contentResolver.query(uri, projection, null, null, null)
        } catch (e: Exception) {
            null
        }
        return cursor.useFirst { c ->
            val displayName = c.getStringSafe(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeType = c.getStringSafe(MediaStore.MediaColumns.MIME_TYPE)
            val sizeBytes = c.getLongSafe(MediaStore.MediaColumns.SIZE)
            val dateModified = c.getLongSafe(MediaStore.MediaColumns.DATE_MODIFIED)?.let { it * 1000 }
            val dateTaken = c.getLongSafe(MediaStore.Images.Media.DATE_TAKEN)
            val width = c.getIntSafe(MediaStore.MediaColumns.WIDTH)
            val height = c.getIntSafe(MediaStore.MediaColumns.HEIGHT)
            val album = c.getStringSafe(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val durationMs = c.getLongSafe(MediaStore.Video.Media.DURATION)
            MediaMetadata(
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                dateModified = dateModified,
                dateTaken = dateTaken,
                width = width,
                height = height,
                album = album,
                durationMs = durationMs
            )
        } ?: MediaMetadata()
    }

    private fun <T> Cursor?.useFirst(block: (Cursor) -> T): T? {
        if (this == null) return null
        return try {
            if (moveToFirst()) block(this) else null
        } finally {
            close()
        }
    }

    private fun Cursor.getStringSafe(column: String): String? {
        val index = getColumnIndex(column)
        if (index == -1) return null
        return getString(index)
    }

    private fun Cursor.getLongSafe(column: String): Long? {
        val index = getColumnIndex(column)
        if (index == -1) return null
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.getIntSafe(column: String): Int? {
        val index = getColumnIndex(column)
        if (index == -1) return null
        return if (isNull(index)) null else getInt(index)
    }

    private data class ExifMetadata(
        val orientation: Int? = null,
        val width: Int? = null,
        val height: Int? = null,
        val dateTaken: Long? = null
    )

    private data class MediaMetadata(
        val displayName: String? = null,
        val mimeType: String? = null,
        val sizeBytes: Long? = null,
        val dateModified: Long? = null,
        val dateTaken: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val album: String? = null,
        val durationMs: Long? = null
    )
}
