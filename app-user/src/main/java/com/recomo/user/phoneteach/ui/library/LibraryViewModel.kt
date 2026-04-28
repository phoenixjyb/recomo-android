package com.recomo.user.phoneteach.ui.library

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.capture.util.ThumbnailGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Library view model for Phone Teach recorded sessions.
 *
 * Filesystem-backed: scans getExternalFilesDir(DIRECTORY_MOVIES)/PhoneTeach/ for session_*
 * subdirectories, builds [PhoneTeachSession] UI models from each. No database — session
 * state is whatever exists on disk.
 *
 * Upload integration (showing per-session upload status) is deferred to P7 when the Upload
 * screen + UploadRepository wiring lands.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _sessions = MutableStateFlow<List<PhoneTeachSession>>(emptyList())
    val sessions: StateFlow<List<PhoneTeachSession>> = _sessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    companion object {
        private const val TAG = "PhoneTeachLibrary"
        private const val OUTPUT_SUBDIR = "PhoneTeach"
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val scanned = withContext(Dispatchers.IO) { scanSessions() }
                _sessions.value = scanned
                Log.d(TAG, "Loaded ${scanned.size} sessions")
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning sessions", e)
                _sessions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun scanSessions(): List<PhoneTeachSession> {
        val appMoviesDir = getApplication<Application>()
            .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val phoneTeachDir = File(appMoviesDir, OUTPUT_SUBDIR)

        if (!phoneTeachDir.exists() || !phoneTeachDir.isDirectory) {
            Log.d(TAG, "PhoneTeach directory does not exist yet: ${phoneTeachDir.absolutePath}")
            return emptyList()
        }

        val sessions = mutableListOf<PhoneTeachSession>()
        phoneTeachDir.listFiles()?.filter { it.isDirectory }?.forEach { sessionDir ->
            val videoFile = File(sessionDir, "video.mp4")
            if (!videoFile.exists()) {
                Log.d(TAG, "Skipping session without video.mp4: ${sessionDir.name}")
                return@forEach
            }

            val thumbnailPath = runCatching {
                ThumbnailGenerator.generateThumbnail(videoFile.absolutePath, getApplication())
            }.getOrNull()

            sessions += PhoneTeachSession(
                sessionDir = sessionDir,
                videoFile = videoFile,
                thumbnailPath = thumbnailPath
            )
        }

        return sessions.sortedByDescending { it.createdAtMs }
    }

    fun deleteSession(session: PhoneTeachSession) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (session.sessionDir.exists()) {
                        session.sessionDir.deleteRecursively()
                        Log.d(TAG, "Deleted session: ${session.sessionName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete session ${session.sessionName}", e)
                }
                Unit
            }
            refresh()
        }
    }
}
