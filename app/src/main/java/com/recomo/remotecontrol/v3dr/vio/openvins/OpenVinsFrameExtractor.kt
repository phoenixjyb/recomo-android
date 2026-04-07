package com.recomo.remotecontrol.v3dr.vio.openvins

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * Extracts grayscale frames from video using MediaMetadataRetriever.
 */
class OpenVinsFrameExtractor(private val videoPath: String) : AutoCloseable {
    private val retriever = MediaMetadataRetriever()
    private var initialized = false
    
    companion object {
        private const val TAG = "OpenVinsFrameExtractor"
    }
    
    init {
        try {
            retriever.setDataSource(videoPath)
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaMetadataRetriever", e)
        }
    }
    
    /**
     * Extract a frame at the given timestamp and convert to grayscale.
     * @param timestampUs timestamp in microseconds
     * @return grayscale frame data (row-major, 1 byte per pixel) or null if failed
     */
    fun extractGrayscaleFrame(timestampUs: Long): GrayscaleFrame? {
        if (!initialized) return null
        
        return try {
            val bitmap = retriever.getFrameAtTime(
                timestampUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            
            val width = bitmap.width
            val height = bitmap.height
            val grayBytes = bitmapToGrayscale(bitmap)
            bitmap.recycle()
            
            GrayscaleFrame(width, height, grayBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract frame at $timestampUs", e)
            null
        }
    }
    
    /**
     * Convert ARGB bitmap to grayscale using standard luminance formula.
     * Y = 0.299*R + 0.587*G + 0.114*B
     */
    private fun bitmapToGrayscale(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val grayBytes = ByteArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Standard luminance formula
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            grayBytes[i] = gray.toByte()
        }
        
        return grayBytes
    }
    
    override fun close() {
        try {
            retriever.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release retriever", e)
        }
    }
    
    data class GrayscaleFrame(
        val width: Int,
        val height: Int,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as GrayscaleFrame
            if (width != other.width) return false
            if (height != other.height) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }
        
        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
