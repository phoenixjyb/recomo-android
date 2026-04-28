package com.recomo.common.capture.recording

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Writes frame timestamps to CSV for VIO session packaging.
 * Format: frame_idx,t_ns (monotonic nanoseconds)
 */
class FrameTimestampWriter {
    private var csvWriter: FileWriter? = null
    private var outputFile: File? = null
    private var frameCount = 0L

    companion object {
        private const val TAG = "FrameTimestampWriter"
        private const val CSV_HEADER = "frame_idx,t_ns\n"
    }

    fun start(file: File) {
        try {
            outputFile = file
            csvWriter = FileWriter(file).apply { write(CSV_HEADER) }
            frameCount = 0L
            Log.i(TAG, "Frame timestamp writer started: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start frame timestamp writer", e)
            stop()
        }
    }

    @Synchronized
    fun record(frameIdx: Long, timestampNs: Long) {
        try {
            csvWriter?.write("$frameIdx,$timestampNs\n")
            frameCount++
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write frame timestamp", e)
        }
    }

    fun stop(): File? {
        try {
            csvWriter?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing frame timestamp file", e)
        }
        val file = outputFile
        csvWriter = null
        outputFile = null
        if (file != null) {
            Log.i(TAG, "Frame timestamp writer stopped: ${file.absolutePath} ($frameCount frames)")
        }
        return file
    }
}
