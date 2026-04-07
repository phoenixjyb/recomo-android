package com.recomo.remotecontrol.v3dr.data.model

/**
 * 3D scene reconstruction data model
 * Linked to a recording session by sessionId
 */
data class SceneReconstruction(
    val sceneId: String,
    val sessionId: String,              // Links to RecordingSession
    val status: ReconstructionStatus,
    val pointCloudUrl: String? = null,  // URL to point cloud (.bin/.ply)
    val meshUrl: String? = null,        // Optional mesh
    val glbUrl: String? = null,
    val splatUrl: String? = null,
    val previewUrl: String? = null,
    val thumbnailUrl: String? = null,
    val fileSizeBytes: Long = 0,
    val vertexCount: Int? = null,
    val faceCount: Int? = null,
    val progressPercent: Int = 0,
    val createdAtIso: String? = null,
    val completedAtIso: String? = null,
    val createdAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val errorMessage: String? = null,
    val localCachePath: String? = null,  // Local cached model file
    
    // Source video information (from RecordingSession)
    val sourceVideoFileName: String? = null,
    val sourceVideoFilePath: String? = null,  // Server path or download URL
    val sourceVideoSizeBytes: Long? = null,
    val sourceVideoDurationSeconds: Int? = null,
    val sourceVideoResolution: String? = null  // e.g., "1920x1080"
)

/**
 * Status of 3D reconstruction processing
 */
enum class ReconstructionStatus {
    PENDING,      // Queued for processing
    PROCESSING,   // COLMAP/reconstruction running
    COMPLETED,    // Ready to view
    FAILED        // Error occurred
}

/**
 * Download progress for 3D model files
 */
data class ModelDownloadProgress(
    val sceneId: String,
    val progress: Float = 0f,  // 0.0 to 1.0
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)
