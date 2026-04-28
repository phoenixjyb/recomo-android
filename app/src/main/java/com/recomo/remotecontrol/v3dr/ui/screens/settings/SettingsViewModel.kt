package com.recomo.remotecontrol.v3dr.ui.screens.settings

import android.app.Application
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.settings.Bitrate
import com.recomo.common.settings.NetworkProfile
import com.recomo.common.settings.Resolution
import com.recomo.remotecontrol.v3dr.data.AppSettings
import com.recomo.remotecontrol.v3dr.data.SettingsRepository
import com.recomo.remotecontrol.v3dr.data.VioBackendType
import com.recomo.remotecontrol.v3dr.network.ServerHealthChecker
import com.recomo.remotecontrol.v3dr.network.ServerHealthStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val healthChecker: ServerHealthChecker
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    data class StorageInfo(
        val totalSizeMB: Long,
        val totalFiles: Int,
        val sessions: Int
    )

    init {
        calculateStorageInfo()
    }

    fun updateResolution(resolution: Resolution) {
        viewModelScope.launch {
            settingsRepository.updateResolution(resolution)
        }
    }

    fun updateBitrate(bitrate: Bitrate) {
        viewModelScope.launch {
            settingsRepository.updateBitrate(bitrate)
        }
    }

    fun updateFrameRate(frameRate: Int) {
        viewModelScope.launch {
            settingsRepository.updateFrameRate(frameRate)
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.updateServerUrl(url)
        }
    }
    
    fun updateNetworkProfile(profile: NetworkProfile) {
        viewModelScope.launch {
            settingsRepository.updateNetworkProfile(profile)
        }
    }

    fun updateAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoUpload(enabled)
        }
    }

    fun updateWifiOnlyUpload(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateWifiOnlyUpload(enabled)
        }
    }

    fun updateVioBackend(backend: VioBackendType) {
        viewModelScope.launch {
            settingsRepository.updateVioBackend(backend)
        }
    }

    fun updateCloudSfmUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.updateCloudSfmUrl(url)
        }
    }
    
    fun testServerConnection() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                Toast.makeText(context, "Testing server connection...", Toast.LENGTH_SHORT).show()
                
                val healthStatus = healthChecker.checkHealth()
                val scenesOk = healthChecker.testScenesEndpoint()
                
                val message = when (healthStatus) {
                    is ServerHealthStatus.Healthy -> {
                        if (scenesOk) {
                            "✓ Server is healthy\n✓ Scenes endpoint accessible\nVersion: ${healthStatus.version ?: "unknown"}"
                        } else {
                            "✓ Server is healthy\n✗ Scenes endpoint returned error\nVersion: ${healthStatus.version ?: "unknown"}"
                        }
                    }
                    is ServerHealthStatus.Unhealthy -> {
                        "✗ Server unhealthy: ${healthStatus.reason}"
                    }
                    is ServerHealthStatus.Unreachable -> {
                        "✗ Cannot reach server: ${healthStatus.error}\n\nCheck:\n- Server is running\n- Network connection\n- Server URL is correct"
                    }
                }
                
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val context = getApplication<Application>()
                Toast.makeText(context, "Connection test failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun calculateStorageInfo() {
        viewModelScope.launch {
            try {
                val appMoviesDir = getApplication<Application>()
                    .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val v3drDir = File(appMoviesDir, "V3DR")
                
                if (!v3drDir.exists()) {
                    _storageInfo.value = StorageInfo(0, 0, 0)
                    return@launch
                }

                var totalSize = 0L
                var totalFiles = 0
                val sessions = v3drDir.listFiles()?.filter { it.isDirectory }?.size ?: 0

                v3drDir.walk().forEach { file ->
                    if (file.isFile) {
                        totalSize += file.length()
                        totalFiles++
                    }
                }

                _storageInfo.value = StorageInfo(
                    totalSizeMB = totalSize / (1024 * 1024),
                    totalFiles = totalFiles,
                    sessions = sessions
                )
            } catch (e: Exception) {
                _storageInfo.value = StorageInfo(0, 0, 0)
            }
        }
    }

    fun clearAllRecordings(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val appMoviesDir = getApplication<Application>()
                    .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val v3drDir = File(appMoviesDir, "V3DR")
                
                if (v3drDir.exists()) {
                    v3drDir.deleteRecursively()
                    v3drDir.mkdirs()
                }
                
                calculateStorageInfo()
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}
