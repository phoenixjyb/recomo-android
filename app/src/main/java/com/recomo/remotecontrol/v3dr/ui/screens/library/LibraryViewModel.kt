package com.recomo.remotecontrol.v3dr.ui.screens.library

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.data.model.UploadStatus
import com.recomo.remotecontrol.v3dr.upload.UploadRepository
import com.recomo.remotecontrol.v3dr.util.ThumbnailGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for Library screen - manages loading and displaying recorded videos
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val uploadRepository: UploadRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val V3DR_FOLDER = "V3DR"
    }

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val recordingsList = withContext(Dispatchers.IO) {
                    scanRecordingsDirectory()
                }
                _recordings.value = recordingsList
                Log.d(TAG, "Loaded ${recordingsList.size} recordings")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recordings", e)
                _recordings.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun scanRecordingsDirectory(): List<Recording> {
        // Use app's private external files directory (same as recording location)
        val appMoviesDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val v3drDir = File(appMoviesDir, V3DR_FOLDER)
        
        if (!v3drDir.exists() || !v3drDir.isDirectory) {
            Log.w(TAG, "V3DR directory does not exist: ${v3drDir.absolutePath}")
            return emptyList()
        }

        // Scan only session subdirectories for video files (new format only)
        val recordings = mutableListOf<Recording>()
        
        v3drDir.listFiles()?.filter { it.isDirectory }?.forEach { sessionDir ->
            sessionDir.listFiles()?.filter { file ->
                file.isFile && file.extension.lowercase() == "mp4"
            }?.forEach { videoFile ->
                val recording = createRecordingFromSession(videoFile, sessionDir)
                recordings.add(recording)
            }
        }
        
        val sortedRecordings = recordings.sortedByDescending { it.timestamp }

        Log.d(TAG, "Found ${sortedRecordings.size} recordings in ${v3drDir.absolutePath}")

        return sortedRecordings
    }
    
    private fun createRecordingFromSession(videoFile: File, sessionDir: File): Recording {
        val timestamp = parseTimestampFromFilename(videoFile.name) ?: videoFile.lastModified()
        val thumbnailPath = ThumbnailGenerator.generateThumbnail(
            videoFile.absolutePath,
            getApplication()
        )
        
        // Detect recording type and trajectory status
        val arcoreTrajectory = File(sessionDir, "trajectory_arcore_tum.txt")
        val cloudSfmTrajectory = File(sessionDir, "results/trajectory_cloud_sfm_tum.txt")
        val openVinsTrajectory = File(sessionDir, "results/trajectory_openvins_tum.txt")
        val genericTrajectory = File(sessionDir, "results/trajectory_tum.txt")
        
        // Check if this is an ARCore recording (has arcore trajectory)
        val isArcoreRecording = arcoreTrajectory.exists()
        
        // Determine trajectory source
        val (hasTrajectory, trajectorySource) = when {
            arcoreTrajectory.exists() -> true to "arcore"
            cloudSfmTrajectory.exists() -> true to "cloud_sfm"
            openVinsTrajectory.exists() -> true to "openvins"
            genericTrajectory.exists() -> true to "unknown"
            else -> false to null
        }
        
        val recordingType = if (isArcoreRecording) RecordingType.ARCORE else RecordingType.CAMERA2_IMU
        
        return Recording(
            id = videoFile.absolutePath,
            fileName = sessionDir.name,  // Use session folder name for display
            filePath = videoFile.absolutePath,
            fileSizeBytes = videoFile.length(),
            timestamp = timestamp,
            thumbnailPath = thumbnailPath,
            type = recordingType,
            hasTrajectory = hasTrajectory,
            trajectorySource = trajectorySource
        )
    }

    private fun parseTimestampFromFilename(filename: String): Long? {
        // Expected format: v3dr_clip_20240101_123045_30s.mp4 (with duration)
        // Or old format: v3dr_clip_20240101_123045.mp4 (without duration)
        val regex = """v3dr_clip_(\d{8})_(\d{6})(?:_\d+s)?\.mp4""".toRegex()
        val match = regex.find(filename) ?: return null
        
        val (dateStr, timeStr) = match.destructured
        val dateTimeStr = "$dateStr$timeStr"
        
        return try {
            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            format.parse(dateTimeStr)?.time
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse timestamp from filename: $filename", e)
            null
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(recording.filePath)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Deleted recording: ${recording.fileName}")
                    }
                }
                loadRecordings() // Refresh list
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting recording", e)
            }
        }
    }

    fun uploadRecording(recording: Recording) {
        viewModelScope.launch {
            // Find associated IMU and metadata files
            val videoFile = File(recording.filePath)
            val sessionDir = videoFile.parentFile

            val imuFile = sessionDir?.let { File(it, "imu.csv") }?.takeIf { it.exists() }
            val metadataFile = sessionDir?.let { File(it, "metadata.jsonl") }?.takeIf { it.exists() }
            val frameTimestampsFile = sessionDir?.let { File(it, "frame_timestamps.csv") }?.takeIf { it.exists() }
            val calibFile = sessionDir?.let { File(it, "calib.json") }?.takeIf { it.exists() }
            val manifestFile = sessionDir?.let { File(it, "manifest.json") }?.takeIf { it.exists() }

            // Use session directory name as ID
            val sessionId = sessionDir?.name ?: File(recording.filePath).nameWithoutExtension

            uploadRepository.queueUpload(
                sessionId = sessionId,
                videoPath = recording.filePath,
                imuPath = imuFile?.absolutePath,
                metadataPath = metadataFile?.absolutePath,
                frameTimestampsPath = frameTimestampsFile?.absolutePath,
                calibPath = calibFile?.absolutePath,
                manifestPath = manifestFile?.absolutePath
            )
            
            Log.d(TAG, "Queued upload for: ${recording.fileName}")
        }
    }

    fun getUploadStatus(recordingId: String): UploadStatus {
        return uploadRepository.getUploadStatus(recordingId)?.status ?: UploadStatus.NOT_UPLOADED
    }

    /**
     * Delete old format recordings (video files in V3DR root directory)
     * Old format: Files directly in V3DR folder, not in session subdirectories
     * Returns the number of files deleted
     */
    fun deleteOldFormatRecordings(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val deletedCount = withContext(Dispatchers.IO) {
                try {
                    val appMoviesDir = getApplication<Application>()
                        .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    val v3drDir = File(appMoviesDir, V3DR_FOLDER)
                    
                    if (!v3drDir.exists()) {
                        return@withContext 0
                    }

                    // Find all files (not directories) in V3DR root
                    val oldFormatFiles = v3drDir.listFiles()?.filter { it.isFile } ?: emptyList()
                    
                    var deleted = 0
                    oldFormatFiles.forEach { file ->
                        if (file.delete()) {
                            deleted++
                            Log.d(TAG, "Deleted old format file: ${file.name}")
                        } else {
                            Log.w(TAG, "Failed to delete: ${file.name}")
                        }
                    }
                    
                    deleted
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting old format recordings", e)
                    0
                }
            }
            
            onComplete(deletedCount)
            if (deletedCount > 0) {
                loadRecordings() // Refresh the list
            }
        }
    }
}

/**
 * Recording type based on how it was captured
 */
enum class RecordingType {
    CAMERA2_IMU,  // Camera2 + IMU - needs VIO processing (OpenVINS or Cloud SFM)
    ARCORE        // ARCore - trajectory already captured during recording
}

/**
 * Data class representing a recorded video
 */
data class Recording(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val timestamp: Long,
    val thumbnailPath: String? = null,
    val type: RecordingType = RecordingType.CAMERA2_IMU,
    val hasTrajectory: Boolean = false,
    val trajectorySource: String? = null  // "arcore", "cloud_sfm", "openvins", etc.
) {
    val formattedSize: String
        get() {
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            return if (mb < 1) {
                String.format("%.2f KB", fileSizeBytes / 1024.0)
            } else {
                String.format("%.2f MB", mb)
            }
        }

    val formattedDate: String
        get() {
            val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return format.format(Date(timestamp))
        }
    
    val needsVioProcessing: Boolean
        get() = type == RecordingType.CAMERA2_IMU && !hasTrajectory
}
