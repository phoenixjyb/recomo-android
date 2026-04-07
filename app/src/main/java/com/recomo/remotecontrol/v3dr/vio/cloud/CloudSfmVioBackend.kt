package com.recomo.remotecontrol.v3dr.vio.cloud

import android.util.Log
import com.recomo.remotecontrol.v3dr.vio.SyncPacket
import com.recomo.remotecontrol.v3dr.vio.VioBackend
import com.recomo.remotecontrol.v3dr.vio.VioDepsResult
import com.recomo.remotecontrol.v3dr.vio.VioDepsStatus
import com.recomo.remotecontrol.v3dr.vio.VioResult
import com.recomo.remotecontrol.v3dr.vio.VioSession
import com.recomo.remotecontrol.v3dr.vio.VioStatus
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cloud-based VIO backend using the SFM reconstruction service.
 * Uploads video to server, runs reconstruction, downloads camera trajectory.
 */
class CloudSfmVioBackend(
    private val serverUrl: String = "http://192.168.100.100:7000"
) : VioBackend {
    
    override val id: String = "cloud_sfm"
    override val displayName: String = "Cloud SFM"
    override val version: String = "1.0"

    companion object {
        private const val TAG = "CloudSfmVioBackend"
        private const val POLL_INTERVAL_MS = 5000L
        private const val MAX_POLL_ATTEMPTS = 360 // 30 minutes max
        private const val GROUP_NAME = "V3DR_App"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS) // 5 min for upload
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun isAvailable(): Boolean = true // Always available if network is up

    override suspend fun checkDependencies(): VioDepsResult {
        return try {
            val request = Request.Builder()
                .url("$serverUrl/")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VioDepsResult(
                    status = VioDepsStatus.AVAILABLE,
                    message = "Cloud SFM service reachable at $serverUrl"
                )
            } else {
                VioDepsResult(
                    status = VioDepsStatus.NOT_AVAILABLE,
                    message = "Cloud SFM service returned ${response.code}"
                )
            }
        } catch (e: Exception) {
            VioDepsResult(
                status = VioDepsStatus.NOT_AVAILABLE,
                message = "Cannot reach Cloud SFM service: ${e.message}"
            )
        }
    }

    override suspend fun run(
        session: VioSession,
        syncPackets: List<SyncPacket>,
        outputDir: File
    ): VioResult {
        outputDir.mkdirs()
        
        Log.d(TAG, "Starting Cloud SFM for ${session.videoFile.name}")
        
        // Step 1: Upload video
        val projectId = try {
            uploadVideo(session.videoFile)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            return failure(outputDir, syncPackets, "Upload failed: ${e.message}")
        }
        
        if (projectId == null) {
            return failure(outputDir, syncPackets, "Failed to get project ID from server")
        }
        
        Log.d(TAG, "Video uploaded, project ID: $projectId")
        
        // Step 2: Start reconstruction
        try {
            startReconstruction(projectId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start reconstruction", e)
            return failure(outputDir, syncPackets, "Failed to start reconstruction: ${e.message}")
        }
        
        Log.d(TAG, "Reconstruction started, polling for completion...")
        
        // Step 3: Poll for completion
        val status = try {
            pollForCompletion(projectId)
        } catch (e: Exception) {
            Log.e(TAG, "Polling failed", e)
            return failure(outputDir, syncPackets, "Reconstruction polling failed: ${e.message}")
        }
        
        if (!status.hasPointCloud || !status.hasCameraPoses) {
            return failure(outputDir, syncPackets, "Reconstruction incomplete: pointcloud=${status.hasPointCloud}, poses=${status.hasCameraPoses}")
        }
        
        Log.d(TAG, "Reconstruction complete, downloading trajectory...")
        
        // Step 4: Download camera poses
        try {
            downloadCameraPoses(projectId, outputDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download poses", e)
            return failure(outputDir, syncPackets, "Failed to download trajectory: ${e.message}")
        }
        
        return success(outputDir, syncPackets, projectId)
    }

    private fun uploadVideo(videoFile: File): String? {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                videoFile.name,
                videoFile.asRequestBody("video/mp4".toMediaType())
            )
            .addFormDataPart("group", GROUP_NAME)
            .build()
        
        val request = Request.Builder()
            .url("$serverUrl/api/upload")
            .post(requestBody)
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Upload failed: ${response.code} ${response.message}")
        }
        
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        return json.optString("project_id", null)
    }

    private fun startReconstruction(projectId: String) {
        val request = Request.Builder()
            .url("$serverUrl/api/projects/$projectId/reconstruct?script_type=full")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to start reconstruction: ${response.code}")
        }
    }

    private data class ProjectStatus(
        val reconstructionStatus: String,
        val hasImages: Boolean,
        val hasPointCloud: Boolean,
        val hasCameraPoses: Boolean
    )

    private suspend fun pollForCompletion(projectId: String): ProjectStatus {
        var attempts = 0
        while (attempts < MAX_POLL_ATTEMPTS) {
            val status = getProjectStatus(projectId)
            
            Log.d(TAG, "Poll #$attempts: status=${status.reconstructionStatus}, " +
                    "pointcloud=${status.hasPointCloud}, poses=${status.hasCameraPoses}")
            
            when (status.reconstructionStatus) {
                "完成" -> return status
                "失败", "error", "failed" -> throw RuntimeException("Reconstruction failed on server")
            }
            
            if (status.hasPointCloud && status.hasCameraPoses) {
                return status
            }
            
            delay(POLL_INTERVAL_MS)
            attempts++
        }
        throw RuntimeException("Reconstruction timed out after ${MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000}s")
    }

    private fun getProjectStatus(projectId: String): ProjectStatus {
        val request = Request.Builder()
            .url("$serverUrl/api/projects/$projectId/status")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Status check failed: ${response.code}")
        }
        
        val body = response.body?.string() ?: throw RuntimeException("Empty status response")
        val json = JSONObject(body)
        
        return ProjectStatus(
            reconstructionStatus = json.optString("status", "unknown"),
            hasImages = json.optBoolean("has_images", false),
            hasPointCloud = json.optBoolean("has_pointcloud", false),
            hasCameraPoses = json.optBoolean("has_camera_poses", false)
        )
    }

    private fun downloadCameraPoses(projectId: String, outputDir: File) {
        val request = Request.Builder()
            .url("$serverUrl/static/$projectId/camera_poses.txt")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Download failed: ${response.code}")
        }
        
        val content = response.body?.string() ?: throw RuntimeException("Empty poses file")
        
        // Save as trajectory file with algorithm name
        val trajectoryFile = File(outputDir, "trajectory_${id}_tum.txt")
        trajectoryFile.writeText("# Cloud SFM trajectory\n# algorithm: $id\n# version: $version\n$content")
        
        // Also save as standard trajectory_tum.txt for compatibility
        File(outputDir, "trajectory_tum.txt").writeText(content)
    }

    private fun success(outputDir: File, syncPackets: List<SyncPacket>, projectId: String): VioResult {
        val trajectoryTum = File(outputDir, "trajectory_${id}_tum.txt")
        val trajectoryJson = File(outputDir, "trajectory.json")
        
        val json = buildJsonObject {
            put("status", "success")
            put("message", "Cloud SFM reconstruction completed")
            put("frames", syncPackets.size)
            put("algorithm", id)
            put("algorithm_name", displayName)
            put("algorithm_version", version)
            put("trajectory_file", trajectoryTum.name)
            put("cloud_project_id", projectId)
            put("server_url", serverUrl)
        }
        trajectoryJson.writeText(json.toString())
        
        val metricsJson = File(outputDir, "metrics.json")
        val metrics = buildJsonObject {
            put("status", "success")
            put("frames", syncPackets.size)
            put("imu_samples", syncPackets.sumOf { it.imuSamples.size })
            put("algorithm", id)
            put("cloud_project_id", projectId)
        }
        metricsJson.writeText(metrics.toString())
        
        val backendLog = File(outputDir, "backend_log.txt")
        backendLog.writeText("$displayName v$version backend (success). Project ID: $projectId\n")
        
        return VioResult(
            status = VioStatus.SUCCESS,
            message = "Cloud SFM completed (project: $projectId)",
            outputDir = outputDir,
            outputs = mapOf(
                "trajectory_tum" to trajectoryTum,
                "trajectory_json" to trajectoryJson,
                "metrics" to metricsJson,
                "backend_log" to backendLog
            )
        )
    }

    private fun failure(outputDir: File, syncPackets: List<SyncPacket>, reason: String): VioResult {
        val trajectoryTum = File(outputDir, "trajectory_${id}_tum.txt")
        trajectoryTum.writeText("# failed\n# algorithm: $id\n# reason: $reason\n")
        
        val trajectoryJson = File(outputDir, "trajectory.json")
        val json = buildJsonObject {
            put("status", "failed")
            put("message", reason)
            put("frames", syncPackets.size)
            put("algorithm", id)
            put("algorithm_name", displayName)
            put("algorithm_version", version)
            put("trajectory_file", trajectoryTum.name)
        }
        trajectoryJson.writeText(json.toString())
        
        val backendLog = File(outputDir, "backend_log.txt")
        backendLog.writeText("$displayName v$version backend (failed). $reason\n")
        
        return VioResult(
            status = VioStatus.FAILED,
            message = reason,
            outputDir = outputDir,
            outputs = mapOf(
                "trajectory_tum" to trajectoryTum,
                "trajectory_json" to trajectoryJson,
                "backend_log" to backendLog
            )
        )
    }
}
