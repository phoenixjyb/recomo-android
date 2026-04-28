package com.recomo.user.phoneteach.ui.upload

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.settings.CaptureSettingsRepository
import com.recomo.common.upload.UploadProgress
import com.recomo.common.upload.UploadRepository
import com.recomo.common.upload.UploadStatus
import com.recomo.user.phoneteach.cloud.PhoneTeachCloudClient
import com.recomo.user.phoneteach.cloud.SessionCloudStatus
import com.recomo.user.phoneteach.ui.library.PhoneTeachSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Upload view model for Phone Teach. Composes :common's [UploadRepository] with local session
 * scanning so the UI can render a per-session row with live upload progress.
 *
 * Note: the current upload endpoint points at V3DR Lake for testing. When the production cloud
 * endpoint lands, swap via [CaptureSettingsRepository.updateServerUrl] (exposed in Settings).
 */
/**
 * Cloud processing status for a single session, observed from the phone-moco backend.
 */
data class CloudStatus(
    val status: String = "unknown",    // uploaded, processing, completed, failed
    val hasTrajectory: Boolean = false,
    val error: String? = null,
    val trajectoryDownloaded: Boolean = false
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    application: Application,
    private val uploadRepository: UploadRepository,
    private val captureSettingsRepository: CaptureSettingsRepository,
    private val cloudClient: PhoneTeachCloudClient
) : AndroidViewModel(application) {

    private val _sessions = MutableStateFlow<List<PhoneTeachSession>>(emptyList())
    val sessions: StateFlow<List<PhoneTeachSession>> = _sessions.asStateFlow()

    val uploads: StateFlow<Map<String, UploadProgress>> = uploadRepository.uploads

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Per-session cloud processing status, keyed by session dir name. */
    private val _cloudStatuses = MutableStateFlow<Map<String, CloudStatus>>(emptyMap())
    val cloudStatuses: StateFlow<Map<String, CloudStatus>> = _cloudStatuses.asStateFlow()

    companion object {
        private const val TAG = "PhoneTeachUpload"
        private const val OUTPUT_SUBDIR = "PhoneTeach"
    }

    init {
        // Keep the UploadRepository URL in sync with Settings so the worker picks up edits.
        captureSettingsRepository.getServerUrl()
            .onEach { url ->
                // Derive the upload endpoint from base URL.
                val endpoint = url.trimEnd('/') + "/api/v1/moco/upload"
                uploadRepository.setUploadUrl(endpoint)
                Log.d(TAG, "Upload URL set to $endpoint")
            }
            .launchIn(viewModelScope)

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val scanned = withContext(Dispatchers.IO) { scanSessions() }
                _sessions.value = scanned
                Log.d(TAG, "Found ${scanned.size} sessions for upload")
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning sessions", e)
                _sessions.value = emptyList()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun scanSessions(): List<PhoneTeachSession> {
        val appMoviesDir = getApplication<Application>()
            .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val phoneTeachDir = File(appMoviesDir, OUTPUT_SUBDIR)
        if (!phoneTeachDir.exists() || !phoneTeachDir.isDirectory) return emptyList()

        val sessions = mutableListOf<PhoneTeachSession>()
        phoneTeachDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val videoFile = File(dir, "video.mp4")
            if (videoFile.exists()) {
                sessions += PhoneTeachSession(
                    sessionDir = dir,
                    videoFile = videoFile
                )
            }
        }
        return sessions.sortedByDescending { it.createdAtMs }
    }

    fun queueUpload(session: PhoneTeachSession) {
        viewModelScope.launch {
            val sessionDir = session.sessionDir
            val imuFile = File(sessionDir, "imu.csv").takeIf { it.exists() }
            val metadataFile = File(sessionDir, "metadata.jsonl").takeIf { it.exists() }
            val frameTimestampsFile = File(sessionDir, "frame_timestamps.csv").takeIf { it.exists() }
            val calibFile = File(sessionDir, "calib.json").takeIf { it.exists() }
            val manifestFile = File(sessionDir, "manifest.json").takeIf { it.exists() }

            uploadRepository.queueUpload(
                sessionId = sessionDir.name,
                videoPath = session.videoFile.absolutePath,
                imuPath = imuFile?.absolutePath,
                metadataPath = metadataFile?.absolutePath,
                frameTimestampsPath = frameTimestampsFile?.absolutePath,
                calibPath = calibFile?.absolutePath,
                manifestPath = manifestFile?.absolutePath
            )
            Log.d(TAG, "Queued upload: ${sessionDir.name}")
        }
    }

    fun cancelUpload(session: PhoneTeachSession) {
        uploadRepository.cancelUpload(session.sessionDir.name)
    }

    fun retryUpload(session: PhoneTeachSession) {
        uploadRepository.retryUpload(session.sessionDir.name)
    }

    fun statusFor(session: PhoneTeachSession): UploadStatus {
        return uploads.value[session.sessionDir.name]?.status ?: UploadStatus.NOT_UPLOADED
    }

    // ─── Cloud status polling + trajectory download ────────────────────

    /**
     * Poll cloud status for a session. Auto-polls every 3s while status is "processing".
     */
    fun pollCloudStatus(session: PhoneTeachSession) {
        val name = session.sessionDir.name
        viewModelScope.launch {
            var keepPolling = true
            while (keepPolling) {
                when (val result = cloudClient.getSessionStatus(name)) {
                    is SessionCloudStatus.Ok -> {
                        val alreadyDownloaded = _cloudStatuses.value[name]?.trajectoryDownloaded == true
                        updateCloudStatus(name, CloudStatus(
                            status = result.status,
                            hasTrajectory = result.hasTrajectory,
                            error = result.error,
                            trajectoryDownloaded = alreadyDownloaded
                        ))
                        // Keep polling if still processing
                        keepPolling = result.status == "processing" || result.status == "uploaded"
                        if (keepPolling) delay(3000)
                    }
                    is SessionCloudStatus.Error -> {
                        updateCloudStatus(name, CloudStatus(status = "error", error = result.message))
                        keepPolling = false
                    }
                }
            }
        }
    }

    /**
     * Download the VIO trajectory from the cloud into the session's local directory.
     */
    fun downloadTrajectory(session: PhoneTeachSession) {
        val name = session.sessionDir.name
        viewModelScope.launch {
            val destFile = File(session.sessionDir, "trajectory_vio_tum.txt")
            val ok = cloudClient.downloadTrajectory(name, destFile)
            if (ok) {
                Log.i(TAG, "Trajectory downloaded to ${destFile.absolutePath}")
                updateCloudStatus(name, _cloudStatuses.value[name]?.copy(trajectoryDownloaded = true)
                    ?: CloudStatus(status = "completed", hasTrajectory = true, trajectoryDownloaded = true))
            } else {
                Log.w(TAG, "Trajectory download failed for $name")
            }
        }
    }

    private fun updateCloudStatus(sessionName: String, status: CloudStatus) {
        _cloudStatuses.value = _cloudStatuses.value.toMutableMap().apply {
            put(sessionName, status)
        }
    }
}
