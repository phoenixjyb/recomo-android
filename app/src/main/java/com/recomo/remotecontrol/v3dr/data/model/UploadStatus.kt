package com.recomo.remotecontrol.v3dr.data.model

/**
 * Upload status for recordings
 */
enum class UploadStatus {
    NOT_UPLOADED,
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}

/**
 * Upload progress information
 */
data class UploadProgress(
    val recordingId: String,
    val status: UploadStatus,
    val progress: Float = 0f, // 0.0 to 1.0
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null,
    val startTime: Long = 0,
    val completedTime: Long = 0
) {
    val isInProgress: Boolean
        get() = status == UploadStatus.UPLOADING || status == UploadStatus.PENDING

    val isCompleted: Boolean
        get() = status == UploadStatus.COMPLETED

    val isFailed: Boolean
        get() = status == UploadStatus.FAILED
}
