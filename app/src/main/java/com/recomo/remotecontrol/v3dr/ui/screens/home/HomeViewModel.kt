package com.recomo.remotecontrol.v3dr.ui.screens.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.data.SettingsRepository
import com.recomo.remotecontrol.v3dr.data.VioBackendType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()
    
    val vioBackend: StateFlow<VioBackendType> = settingsRepository.settingsFlow
        .map { it.vioBackend }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VioBackendType.CLOUD_SFM)

    data class StorageStats(
        val recordingsCount: Int,
        val storageSizeMB: Long
    )

    init {
        loadStorageStats()
    }

    fun loadStorageStats() {
        viewModelScope.launch {
            try {
                val appMoviesDir = getApplication<Application>()
                    .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val v3drDir = File(appMoviesDir, "V3DR")
                
                if (!v3drDir.exists()) {
                    _storageStats.value = StorageStats(0, 0)
                    return@launch
                }

                // Count video files from session subdirectories only (new format)
                val videoFiles = mutableListOf<File>()
                
                // Add video files from session subdirectories
                v3drDir.listFiles()?.filter { it.isDirectory }?.forEach { sessionDir ->
                    sessionDir.listFiles()?.filter { file ->
                        file.isFile && file.extension.lowercase() == "mp4"
                    }?.let { videoFiles.addAll(it) }
                }
                
                val totalRecordings = videoFiles.size

                // Calculate total storage used
                var totalSize = 0L
                v3drDir.walk().forEach { file ->
                    if (file.isFile) {
                        totalSize += file.length()
                    }
                }

                _storageStats.value = StorageStats(
                    recordingsCount = totalRecordings,
                    storageSizeMB = totalSize / (1024 * 1024)
                )
            } catch (e: Exception) {
                _storageStats.value = StorageStats(0, 0)
            }
        }
    }
}
