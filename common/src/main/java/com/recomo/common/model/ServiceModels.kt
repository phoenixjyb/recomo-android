package com.recomo.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Status of an individual Orin service
 */
@Serializable
data class OrinServiceStatus(
    @SerialName("name")
    val name: String,
    
    @SerialName("running")
    val running: Boolean,
    
    @SerialName("pid")
    val pid: Int? = null,
    
    @SerialName("uptime_seconds")
    val uptimeSeconds: Double? = null,
    
    @SerialName("port")
    val port: Int? = null,  // Nullable for ROS2 nodes without HTTP ports
    
    @SerialName("last_log_lines")
    val lastLogLines: List<String> = emptyList()
)

/**
 * Response from service control operations (start/stop)
 */
@Serializable
data class ServiceControlResponse(
    @SerialName("success")
    val success: Boolean,
    
    @SerialName("message")
    val message: String,
    
    @SerialName("services")
    val services: Map<String, OrinServiceStatus>
)

@Serializable
data class OrinRobotIdentity(
    @SerialName("product_family")
    val productFamily: String = "",
    @SerialName("platform_rev")
    val platformRev: String = "",
    @SerialName("robot_variant")
    val robotVariant: String = "",
    @SerialName("hardware_variant")
    val hardwareVariant: String = "",
    @SerialName("robot_unit_id")
    val robotUnitId: String = "",
    @SerialName("robot_sn")
    val robotSn: String = "",
    @SerialName("robot_ssid")
    val robotSsid: String = "",
    @SerialName("local_orin_ip")
    val localOrinIp: String = "",
    @SerialName("zerotier_ip")
    val zerotierIp: String = "",
    @SerialName("site_ip")
    val siteIp: String = "",
    @SerialName("ros_domain_id")
    val rosDomainId: String = "",
    @SerialName("robot_profile")
    val robotProfile: String = "",
    @SerialName("identity_env_file")
    val identityEnvFile: String = "",
    @SerialName("device_id")
    val deviceId: String = "",
    @SerialName("device_info_file")
    val deviceInfoFile: String = "",
    @SerialName("identity_source")
    val identitySource: String = ""
)

@Serializable
data class VideoStorageStatus(
    @SerialName("data_root")
    val dataRoot: String = "",
    @SerialName("total_bytes")
    val totalBytes: Long = 0L,
    @SerialName("used_bytes")
    val usedBytes: Long = 0L,
    @SerialName("free_bytes")
    val freeBytes: Long = 0L
)

@Serializable
data class VideoFilesStatus(
    @SerialName("session_count")
    val sessionCount: Int = 0,
    @SerialName("video_count")
    val videoCount: Int = 0,
    @SerialName("face_asset_count")
    val faceAssetCount: Int = 0
)

@Serializable
data class VideoQueueStatus(
    @SerialName("pending")
    val pending: Int = 0,
    @SerialName("uploading")
    val uploading: Int = 0,
    @SerialName("completed")
    val completed: Int = 0,
    @SerialName("failed")
    val failed: Int = 0,
    @SerialName("dead")
    val dead: Int = 0,
    @SerialName("total")
    val total: Int = 0
)

@Serializable
data class VideoDbStatus(
    @SerialName("ok")
    val ok: Boolean = false,
    @SerialName("reason")
    val reason: String = "",
    @SerialName("path")
    val path: String = ""
)

@Serializable
data class VideoServerCheck(
    @SerialName("ok")
    val ok: Boolean = false,
    @SerialName("reason")
    val reason: String = "",
    @SerialName("http")
    val http: Int? = null
)

@Serializable
data class VideoServerHealth(
    @SerialName("cos")
    val cos: VideoServerCheck = VideoServerCheck(),
    @SerialName("backend")
    val backend: VideoServerCheck = VideoServerCheck()
)

@Serializable
data class VideoRecorderStatus(
    @SerialName("recording")
    val recording: Boolean = false,
    @SerialName("session_id")
    val sessionId: String = "",
    @SerialName("frame_count")
    val frameCount: Int = 0,
    @SerialName("video_path")
    val videoPath: String = "",
    @SerialName("updated_at")
    val updatedAt: String = ""
)

@Serializable
data class VideoWorkerStatus(
    @SerialName("last_scan_at")
    val lastScanAt: String = "",
    @SerialName("last_upload_at")
    val lastUploadAt: String = "",
    @SerialName("last_worker_error")
    val lastWorkerError: String = "",
    @SerialName("latest_upload_error")
    val latestUploadError: String? = null
)

@Serializable
data class VideoConfigStatus(
    @SerialName("upload_preset")
    val uploadPreset: String = "custom",
    @SerialName("recording_enabled")
    val recordingEnabled: Boolean = false,
    @SerialName("upload_mode")
    val uploadMode: String = "",
    @SerialName("backend_base_url")
    val backendBaseUrl: String = "",
    @SerialName("skip_backend_notify")
    val skipBackendNotify: Boolean = false
)

@Serializable
data class VideoManagementStatus(
    @SerialName("timestamp")
    val timestamp: String = "",
    @SerialName("storage")
    val storage: VideoStorageStatus = VideoStorageStatus(),
    @SerialName("files")
    val files: VideoFilesStatus = VideoFilesStatus(),
    @SerialName("upload_queue")
    val uploadQueue: VideoQueueStatus = VideoQueueStatus(),
    @SerialName("db")
    val db: VideoDbStatus = VideoDbStatus(),
    @SerialName("server_health")
    val serverHealth: VideoServerHealth = VideoServerHealth(),
    @SerialName("recorder")
    val recorder: VideoRecorderStatus = VideoRecorderStatus(),
    @SerialName("worker")
    val worker: VideoWorkerStatus = VideoWorkerStatus(),
    @SerialName("config")
    val config: VideoConfigStatus = VideoConfigStatus()
)
