package com.recomo.remotecontrol.v3dr.recording

import android.util.Log
import com.recomo.remotecontrol.v3dr.data.model.Telemetry
import com.recomo.remotecontrol.v3dr.data.model.ImuTelemetry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Collects and synchronizes camera telemetry with IMU data during recording.
 * Writes combined metadata to JSON lines file for post-processing.
 */
class MetadataCollector {
    
    private var jsonWriter: FileWriter? = null
    private var outputFile: File? = null
    private var frameCount = 0L
    private val json = Json { prettyPrint = false }
    
    companion object {
        private const val TAG = "MetadataCollector"
    }
    
    @Serializable
    data class CombinedMetadata(
        val frameNumber: Long,
        val camera: Telemetry,
        val imu: ImuTelemetry? = null
    )
    
    /**
     * Start collecting metadata to JSON lines file.
     * Each line is a JSON object with camera telemetry and optional IMU data.
     */
    fun start(file: File) {
        try {
            outputFile = file
            jsonWriter = FileWriter(file)
            frameCount = 0L
            Log.i(TAG, "Metadata collector started: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start metadata collector", e)
            stop()
        }
    }
    
    /**
     * Record camera telemetry with optional IMU snapshot.
     * @param cameraTelemetry Camera metadata from Camera2Controller
     * @param imuTelemetry Optional IMU snapshot (from ImuLogger.getCurrentTelemetry())
     */
    fun recordFrame(cameraTelemetry: Telemetry, imuTelemetry: ImuTelemetry? = null) {
        try {
            val combined = CombinedMetadata(
                frameNumber = frameCount++,
                camera = cameraTelemetry,
                imu = imuTelemetry
            )
            val jsonLine = json.encodeToString(combined) + "\n"
            jsonWriter?.write(jsonLine)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write metadata frame", e)
        }
    }
    
    /**
     * Stop collecting and close the file.
     * @return The output file that was created, or null if collector wasn't started
     */
    fun stop(): File? {
        try {
            jsonWriter?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing metadata file", e)
        }
        
        val file = outputFile
        jsonWriter = null
        outputFile = null
        
        if (file != null) {
            Log.i(TAG, "Metadata collector stopped: ${file.absolutePath} ($frameCount frames)")
        }
        
        return file
    }
}
