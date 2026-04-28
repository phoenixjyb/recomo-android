package com.recomo.user.ui.screens.settings

import com.recomo.common.chat.voice.VoiceEngine
import com.recomo.common.chat.voice.WhisperModel
import com.recomo.common.chat.voice.WhisperModelRepository
import com.recomo.common.model.VideoSource

data class SettingsUiState(
    val robotLabel: String = "",
    val robotProfileLabel: String = "",
    val networkPresetLabel: String = "",
    val chatServerUrl: String = "",
    val chatDirectEnabled: Boolean = false,
    val chatDirectBaseUrl: String = "",
    val chatDirectAuthToken: String = "",
    val voiceEngine: VoiceEngine = VoiceEngine.SYSTEM,
    val voiceModel: WhisperModel = WhisperModel.TINY,
    val whisperDownloadState: WhisperModelRepository.DownloadState = WhisperModelRepository.DownloadState.Idle,
    val sessionFolderPath: String = "",
    val sceneViewerFolderPath: String = "",
    val gatewayStatusLabel: String = "Disconnected",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val useWebRTC: Boolean = false,
    val videoSource: VideoSource = VideoSource.WS_ORIN,
    val hdmiDeviceAvailable: Boolean = false,
    // Speed tier overrides (chassis m/s). 0 = use built-in default.
    val speedSlowMps: Float = 0f,
    val speedNormalMps: Float = 0f,
    val speedFastMps: Float = 0f,
    // Studio Dance countdown duration in seconds (1-10)
    val countdownDurationSeconds: Int = 3
) {
    val canConnect: Boolean get() = !isConnected && !isConnecting
    val canDisconnect: Boolean get() = isConnected && !isConnecting
}
