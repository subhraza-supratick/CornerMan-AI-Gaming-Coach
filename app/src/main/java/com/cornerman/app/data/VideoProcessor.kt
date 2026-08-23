package com.cornerman.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoProcessor {

    suspend fun extractStoryboard(context: Context, uri: Uri, frameCount: Int = 6): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLong() ?: 0L
            
            if (duration > 0) {
                val interval = duration / frameCount
                for (i in 0 until frameCount) {
                    val timeUs = (i * interval) * 1000 // Convert to microseconds
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bitmap != null) {
                        bitmaps.add(bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            // Log or handle error
        } finally {
            retriever.release()
        }
        bitmaps
    }
}
