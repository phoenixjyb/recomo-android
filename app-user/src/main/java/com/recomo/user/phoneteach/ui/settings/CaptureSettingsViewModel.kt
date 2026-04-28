package com.recomo.user.phoneteach.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.settings.Bitrate
import com.recomo.common.settings.CaptureSettings
import com.recomo.common.settings.CaptureSettingsRepository
import com.recomo.common.settings.Resolution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings view model for Phone Teach capture. Thin wrapper over [CaptureSettingsRepository]
 * from :common. Dropped vs v3dr's equivalent: no VIO-backend picker, no Cloud SFM URL,
 * no "test connection" button (server isn't live yet).
 */
@HiltViewModel
class CaptureSettingsViewModel @Inject constructor(
    private val captureSettingsRepository: CaptureSettingsRepository
) : ViewModel() {

    val settings: StateFlow<CaptureSettings> = captureSettingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CaptureSettings()
        )

    fun updateResolution(resolution: Resolution) {
        viewModelScope.launch { captureSettingsRepository.updateResolution(resolution) }
    }

    fun updateBitrate(bitrate: Bitrate) {
        viewModelScope.launch { captureSettingsRepository.updateBitrate(bitrate) }
    }

    fun updateFrameRate(frameRate: Int) {
        viewModelScope.launch { captureSettingsRepository.updateFrameRate(frameRate) }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch { captureSettingsRepository.updateServerUrl(url) }
    }

    fun updateAutoUpload(enabled: Boolean) {
        viewModelScope.launch { captureSettingsRepository.updateAutoUpload(enabled) }
    }

    fun updateWifiOnlyUpload(enabled: Boolean) {
        viewModelScope.launch { captureSettingsRepository.updateWifiOnlyUpload(enabled) }
    }
}
