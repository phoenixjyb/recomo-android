package com.recomo.remotecontrol.v3dr.vio.openvins

import com.recomo.remotecontrol.v3dr.vio.SyncPacket
import com.recomo.remotecontrol.v3dr.vio.VioBackend
import com.recomo.remotecontrol.v3dr.vio.VioDepsResult
import com.recomo.remotecontrol.v3dr.vio.VioDepsStatus
import com.recomo.remotecontrol.v3dr.vio.VioResult
import com.recomo.remotecontrol.v3dr.vio.VioSession
import com.recomo.remotecontrol.v3dr.vio.VioStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.File

class OpenVinsBackend : VioBackend {
    override val id: String = "openvins"
    override val displayName: String = "OpenVINS"
    override val version: String = "0.1"

    override fun isAvailable(): Boolean = OpenVinsNative.isLoaded()

    override suspend fun checkDependencies(): VioDepsResult {
        if (!isAvailable()) {
            return VioDepsResult(
                status = VioDepsStatus.NOT_AVAILABLE,
                message = "openvins_jni not loaded"
            )
        }
        val code = OpenVinsNative.nativeSmokeTest()
        return when (code) {
            0 -> VioDepsResult(
                status = VioDepsStatus.AVAILABLE,
                message = "OpenCV/Boost/Eigen available"
            )
            2 -> VioDepsResult(
                status = VioDepsStatus.MISSING,
                message = "OpenCV/Boost/Eigen not linked (run setup + rebuild)"
            )
            else -> VioDepsResult(
                status = VioDepsStatus.ERROR,
                message = "OpenVINS deps check failed (code $code)"
            )
        }
    }

    override suspend fun run(
        session: VioSession,
        syncPackets: List<SyncPacket>,
        outputDir: File
    ): VioResult {
        outputDir.mkdirs()

        if (!isAvailable()) {
            return notImplemented(outputDir, "openvins_jni not loaded")
        }
        
        if (syncPackets.isEmpty()) {
            return failure(outputDir, "No sync packets")
        }

        // Generate OpenVINS config files
        val configDir = File(outputDir, "config")
        val estimatorConfig = OpenVinsConfigWriter.writeConfigs(
            outputDir = configDir,
            calibFile = session.calibFile,
            videoWidth = 1920, // TODO: get from video metadata
            videoHeight = 1080,
            syncPackets = syncPackets
        )
        
        if (estimatorConfig == null) {
            return failure(outputDir, "Failed to generate OpenVINS config")
        }

        // Initialize OpenVINS
        val handle = OpenVinsNative.nativeInit(
            configPath = estimatorConfig.absolutePath,
            outputDir = outputDir.absolutePath
        )
        
        if (handle == 0L) {
            return failure(outputDir, "Failed to initialize OpenVINS")
        }

        try {
            // Feed IMU data and frames
            var processedFrames = 0
            OpenVinsFrameExtractor(session.videoFile.absolutePath).use { extractor ->
                for (packet in syncPackets) {
                    // Feed IMU samples
                    for (imu in packet.imuSamples) {
                        val code = OpenVinsNative.nativeFeedImu(
                            handle = handle,
                            timestampNs = imu.timestampNs,
                            gx = imu.gyro.x,
                            gy = imu.gyro.y,
                            gz = imu.gyro.z,
                            ax = imu.accel.x,
                            ay = imu.accel.y,
                            az = imu.accel.z
                        )
                        if (code != 0) {
                            throw RuntimeException("Failed to feed IMU data (code $code)")
                        }
                    }
                    
                    // Feed frame
                    val timestampUs = packet.frameTimestampNs / 1000
                    val frame = extractor.extractGrayscaleFrame(timestampUs)
                    if (frame != null) {
                        val code = OpenVinsNative.nativeFeedFrame(
                            handle = handle,
                            timestampNs = packet.frameTimestampNs,
                            width = frame.width,
                            height = frame.height,
                            grayBytes = frame.data
                        )
                        if (code != 0) {
                            throw RuntimeException("Failed to feed frame (code $code)")
                        }
                        processedFrames++
                    }
                }
            }
            
            // Finalize processing
            val finalizeCode = OpenVinsNative.nativeFinalize(handle)
            if (finalizeCode != 0) {
                return failure(outputDir, "OpenVINS finalize failed (code $finalizeCode)")
            }
            
            return success(outputDir, syncPackets)
            
        } catch (e: Exception) {
            return failure(outputDir, "OpenVINS error: ${e.message}")
        } finally {
            OpenVinsNative.nativeRelease(handle)
        }
    }

    private fun success(outputDir: File, syncPackets: List<SyncPacket>): VioResult {
        val outputs = ensureOutputs(outputDir, syncPackets, "success")
        return VioResult(
            status = VioStatus.SUCCESS,
            message = "OpenVINS completed",
            outputDir = outputDir,
            outputs = outputs
        )
    }

    private fun notImplemented(outputDir: File, reason: String): VioResult {
        val outputs = ensureOutputs(outputDir, emptyList(), "not_implemented", reason)
        return VioResult(
            status = VioStatus.NOT_IMPLEMENTED,
            message = reason,
            outputDir = outputDir,
            outputs = outputs
        )
    }

    private fun failure(outputDir: File, reason: String): VioResult {
        val outputs = ensureOutputs(outputDir, emptyList(), "failed", reason)
        return VioResult(
            status = VioStatus.FAILED,
            message = reason,
            outputDir = outputDir,
            outputs = outputs
        )
    }

    private fun ensureOutputs(
        outputDir: File,
        syncPackets: List<SyncPacket>,
        status: String,
        message: String? = null
    ): Map<String, File> {
        val trajectoryTum = File(outputDir, "trajectory_${id}_tum.txt")
        if (!trajectoryTum.exists()) {
            trajectoryTum.writeText("# $status\n# algorithm: $id\n# version: $version\n")
        }
        val trajectoryJson = File(outputDir, "trajectory.json")
        if (!trajectoryJson.exists()) {
            val json = buildJsonObject {
                put("status", status)
                put("message", message ?: "")
                put("frames", syncPackets.size)
                put("algorithm", id)
                put("algorithm_name", displayName)
                put("algorithm_version", version)
                put("trajectory_file", trajectoryTum.name)
            }
            trajectoryJson.writeText(json.toString())
        }
        val metricsJson = File(outputDir, "metrics.json")
        if (!metricsJson.exists()) {
            val json = buildJsonObject {
                put("status", status)
                put("frames", syncPackets.size)
                put("imu_samples", syncPackets.sumOf { it.imuSamples.size })
                put("algorithm", id)
            }
            metricsJson.writeText(json.toString())
        }
        val backendLog = File(outputDir, "backend_log.txt")
        if (!backendLog.exists()) {
            backendLog.writeText("$displayName v$version backend ($status). ${message ?: ""}\n")
        }
        return mapOf(
            "trajectory_tum" to trajectoryTum,
            "trajectory_json" to trajectoryJson,
            "metrics" to metricsJson,
            "backend_log" to backendLog
        )
    }
}
