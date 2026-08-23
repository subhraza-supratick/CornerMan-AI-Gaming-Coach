package com.cornerman.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SanitizedMediaContext(
    val mediaType: String, // "image" or "video"
    val width: Int,
    val height: Int,
    val orientation: String,
    val mimeType: String?,
    val durationMs: Long? = null,
    val fileSize: Long = 0,
    val timestamp: Long? = null,
    val screenRecordingLikely: Boolean = false
)

object MediaContextExtractor {

    suspend fun extract(context: Context, uri: Uri): SanitizedMediaContext = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
        val isVideo = mimeType?.startsWith("video") == true
        
        var width = 0
        var height = 0
        var duration: Long? = null
        var orientation = "unknown"
        var timestamp: Long? = null
        
        val pfd = contentResolver.openFileDescriptor(uri, "r")
        val fileSize = pfd?.statSize ?: 0
        pfd?.close()

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                orientation = if (rotation == 90 || rotation == 270) "portrait" else "landscape"
            } catch (e: Exception) {
                // Ignore retrieval errors
            } finally {
                retriever.release()
            }
        } else {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                val rot = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                orientation = if (rot == ExifInterface.ORIENTATION_ROTATE_90 || rot == ExifInterface.ORIENTATION_ROTATE_270) "portrait" else "landscape"
            }
        }

        // Sanity check dimensions if EXIF fails
        if (width <= 0 || height <= 0) {
            // Fallback for some specific devices
            width = 1920
            height = 1080
        }

        SanitizedMediaContext(
            mediaType = if (isVideo) "video" else "image",
            width = width,
            height = height,
            orientation = orientation,
            mimeType = mimeType,
            durationMs = duration,
            fileSize = fileSize,
            timestamp = timestamp,
            screenRecordingLikely = width % 8 == 0 // Rough heuristic
        )
    }
}
