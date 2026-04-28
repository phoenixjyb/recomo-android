package com.recomo.common.upload

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing uploads
 */
@Singleton
class UploadRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UploadRepository"
        private const val UPLOAD_WORK_TAG = "upload_work"
    }

    private val workManager = WorkManager.getInstance(context)

    private val _uploads = MutableStateFlow<Map<String, UploadProgress>>(emptyMap())
    val uploads: StateFlow<Map<String, UploadProgress>> = _uploads.asStateFlow()

    private val _uploadUrl = MutableStateFlow<String?>("http://192.168.100.97:9100/api/v1/moco/upload")
    val uploadUrl: StateFlow<String?> = _uploadUrl.asStateFlow()

    init {
        observeWorkManager()
    }

    fun setUploadUrl(url: String) {
        _uploadUrl.value = url
        Log.d(TAG, "Upload URL set to: $url")
    }

    /**
     * Queue an upload for a recording session
     */
    fun queueUpload(
        sessionId: String,
        videoPath: String,
        imuPath: String? = null,
        metadataPath: String? = null,
        frameTimestampsPath: String? = null,
        calibPath: String? = null,
        manifestPath: String? = null
    ) {
        val uploadUrl = _uploadUrl.value
        if (uploadUrl.isNullOrBlank()) {
            Log.e(TAG, "Upload URL not configured")
            updateUploadStatus(
                sessionId,
                UploadStatus.FAILED,
                errorMessage = "Upload URL not configured"
            )
            return
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file not found: $videoPath")
            updateUploadStatus(
                sessionId,
                UploadStatus.FAILED,
                errorMessage = "Video file not found"
            )
            return
        }

        // Calculate total size
        var totalBytes = videoFile.length()
        imuPath?.let { File(it).takeIf { it.exists() }?.let { totalBytes += it.length() } }
        metadataPath?.let { File(it).takeIf { it.exists() }?.let { totalBytes += it.length() } }
        frameTimestampsPath?.let { File(it).takeIf { it.exists() }?.let { totalBytes += it.length() } }
        calibPath?.let { File(it).takeIf { it.exists() }?.let { totalBytes += it.length() } }
        manifestPath?.let { File(it).takeIf { it.exists() }?.let { totalBytes += it.length() } }

        // Update status to pending
        updateUploadStatus(
            sessionId,
            UploadStatus.PENDING,
            totalBytes = totalBytes
        )

        // Create work request
        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_VIDEO_PATH, videoPath)
            .putString(UploadWorker.KEY_SESSION_ID, sessionId)
            .putString(UploadWorker.KEY_UPLOAD_URL, uploadUrl)
            .apply {
                imuPath?.let { putString(UploadWorker.KEY_IMU_PATH, it) }
                metadataPath?.let { putString(UploadWorker.KEY_METADATA_PATH, it) }
                frameTimestampsPath?.let { putString(UploadWorker.KEY_FRAME_TIMESTAMPS_PATH, it) }
                calibPath?.let { putString(UploadWorker.KEY_CALIB_PATH, it) }
                manifestPath?.let { putString(UploadWorker.KEY_MANIFEST_PATH, it) }
            }
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(UPLOAD_WORK_TAG)
            .addTag("upload_$sessionId")
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "upload_$sessionId",
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )

        Log.d(TAG, "Queued upload for session: $sessionId")
    }

    /**
     * Cancel an upload
     */
    fun cancelUpload(sessionId: String) {
        workManager.cancelAllWorkByTag("upload_$sessionId")
        updateUploadStatus(sessionId, UploadStatus.NOT_UPLOADED)
        Log.d(TAG, "Cancelled upload for session: $sessionId")
    }

    /**
     * Retry a failed upload
     */
    fun retryUpload(sessionId: String) {
        val currentUpload = _uploads.value[sessionId]
        if (currentUpload == null) {
            Log.w(TAG, "Cannot retry upload: session not found")
            return
        }

        // Re-queue the upload (WorkManager will handle retry logic)
        workManager.cancelAllWorkByTag("upload_$sessionId")
        
        // Small delay then re-queue
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateUploadStatus(sessionId, UploadStatus.PENDING)
        }, 500)
        
        Log.d(TAG, "Retrying upload for session: $sessionId")
    }

    /**
     * Get upload status for a session
     */
    fun getUploadStatus(sessionId: String): UploadProgress? {
        return _uploads.value[sessionId]
    }

    private fun updateUploadStatus(
        sessionId: String,
        status: UploadStatus,
        progress: Float = 0f,
        bytesUploaded: Long = 0,
        totalBytes: Long = _uploads.value[sessionId]?.totalBytes ?: 0,
        errorMessage: String? = null
    ) {
        val currentUploads = _uploads.value.toMutableMap()
        currentUploads[sessionId] = UploadProgress(
            recordingId = sessionId,
            status = status,
            progress = progress,
            bytesUploaded = bytesUploaded,
            totalBytes = totalBytes,
            errorMessage = errorMessage,
            startTime = currentUploads[sessionId]?.startTime ?: System.currentTimeMillis(),
            completedTime = if (status == UploadStatus.COMPLETED || status == UploadStatus.FAILED) {
                System.currentTimeMillis()
            } else {
                0
            }
        )
        _uploads.value = currentUploads
    }

    private fun observeWorkManager() {
        workManager.getWorkInfosByTagLiveData(UPLOAD_WORK_TAG)
            .observeForever { workInfos ->
                workInfos?.forEach { workInfo ->
                    val sessionId = workInfo.tags.firstOrNull { 
                        it.startsWith("upload_") && it != UPLOAD_WORK_TAG 
                    }?.removePrefix("upload_") ?: return@forEach

                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> {
                            updateUploadStatus(sessionId, UploadStatus.PENDING)
                        }
                        WorkInfo.State.RUNNING -> {
                            updateUploadStatus(sessionId, UploadStatus.UPLOADING)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            updateUploadStatus(sessionId, UploadStatus.COMPLETED, progress = 1.0f)
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString("error") ?: "Upload failed"
                            updateUploadStatus(sessionId, UploadStatus.FAILED, errorMessage = error)
                        }
                        WorkInfo.State.CANCELLED -> {
                            updateUploadStatus(sessionId, UploadStatus.NOT_UPLOADED)
                        }
                        else -> {}
                    }
                }
            }
    }
}
