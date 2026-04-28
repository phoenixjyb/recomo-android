package com.recomo.common.capture.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for generating video thumbnails
 */
object ThumbnailGenerator {
    private const val TAG = "ThumbnailGenerator"
    private const val THUMBNAIL_WIDTH = 320
    private const val THUMBNAIL_HEIGHT = 180
    
    /**
     * Generate a thumbnail for a video file
     * @param videoPath Absolute path to the video file
     * @param context Application context
     * @return Path to the generated thumbnail file, or null if generation failed
     */
    fun generateThumbnail(videoPath: String, context: Context): String? {
        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            Log.w(TAG, "Video file does not exist: $videoPath")
            return null
        }

        val thumbnailDir = File(context.cacheDir, "thumbnails")
        thumbnailDir.mkdirs()
        
        // Use session folder name + video filename to create unique thumbnail name
        // This handles cases where all videos are named "video.mp4" in different session folders
        val sessionName = videoFile.parentFile?.name ?: "unknown"
        val thumbnailFileName = "${sessionName}_${videoFile.nameWithoutExtension}_thumb.jpg"
        val thumbnailFile = File(thumbnailDir, thumbnailFileName)
        
        // Return cached thumbnail if it exists and is newer than video
        if (thumbnailFile.exists() && thumbnailFile.lastModified() >= videoFile.lastModified()) {
            return thumbnailFile.absolutePath
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            
            // Extract frame at 1 second (or first frame if video is shorter)
            val bitmap = retriever.getFrameAtTime(
                1_000_000, // 1 second in microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            
            if (bitmap == null) {
                Log.w(TAG, "Could not extract frame from video: $videoPath")
                return null
            }

            // Scale bitmap to thumbnail size
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                THUMBNAIL_WIDTH,
                THUMBNAIL_HEIGHT,
                true
            )
            
            // Save thumbnail
            FileOutputStream(thumbnailFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            bitmap.recycle()
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            
            Log.d(TAG, "Generated thumbnail: ${thumbnailFile.absolutePath}")
            return thumbnailFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnail for $videoPath", e)
            return null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Clear all cached thumbnails
     */
    fun clearCache(context: Context) {
        val thumbnailDir = File(context.cacheDir, "thumbnails")
        if (thumbnailDir.exists()) {
            thumbnailDir.listFiles()?.forEach { it.delete() }
        }
    }
}
