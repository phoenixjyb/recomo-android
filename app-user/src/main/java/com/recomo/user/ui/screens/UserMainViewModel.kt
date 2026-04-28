package com.recomo.user.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.NetworkPreset
import com.recomo.common.model.RobotProfile
import com.recomo.common.model.VideoSource
import com.recomo.common.chat.voice.VoiceEngine
import com.recomo.common.chat.voice.WhisperModelRepository
import com.recomo.common.network.OrinGatewayClient
import com.recomo.common.sceneviewer.SceneAssetRepository
import com.recomo.user.control.UserConnectionStatus
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.ui.screens.viewer.SceneViewerHttpServer
import com.recomo.user.ui.screens.viewer.SceneViewerLaunchRequest
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UserShellStage {
    Splash,
    Connection,
    Main
}

enum class UserMainRoute {
    MainControl,
    TouchControl,
    MotionLibrary,
    MotionCreator,
    MotionRunner,
    PostRecord,
    MediaGallery,
    SlamMaps,
    SmartFollow
}

data class UserRobotOption(
    val profile: RobotProfile,
    val preset: NetworkPreset,
    val label: String
)

@HiltViewModel
class UserMainViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val gatewayClient: OrinGatewayClient,
    val sceneAssetRepository: SceneAssetRepository,
    val sceneViewerHttpServer: SceneViewerHttpServer,
    val whisperModelRepository: WhisperModelRepository
) : ViewModel() {
    private val robotOptions = listOf(
        UserRobotOption(RobotProfile.RECOMO_PROTO1_DOGWOOD01, NetworkPreset.RECOMO_LOCAL, "dogwood-01"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_CEDAR01, NetworkPreset.RECOMO_LOCAL, "cedar-01"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_ELM01, NetworkPreset.RECOMO_LOCAL, "elm-01"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_CICDEVICE, NetworkPreset.RECOMO_LOCAL, "test-01 (Orin 160)"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_TEST02, NetworkPreset.RECOMO_LOCAL, "test-02 (Orin 162)"),
        UserRobotOption(RobotProfile.VIRTUAL_SIM, NetworkPreset.ZEROTIER, "virtual-sim (x86)"),
        UserRobotOption(RobotProfile.CUSTOM, NetworkPreset.CUSTOM, "Custom")
    )

    private val _stage = MutableStateFlow(UserShellStage.Splash)
    val stage: StateFlow<UserShellStage> = _stage.asStateFlow()

    private val _availableRobots = MutableStateFlow(robotOptions)
    val availableRobots: StateFlow<List<UserRobotOption>> = _availableRobots.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _route = MutableStateFlow(UserMainRoute.MainControl)
    val route: StateFlow<UserMainRoute> = _route.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _sceneViewerRequest = MutableStateFlow<SceneViewerLaunchRequest?>(null)
    val sceneViewerRequest: StateFlow<SceneViewerLaunchRequest?> = _sceneViewerRequest.asStateFlow()

    val selectedRobot: StateFlow<UserRobotOption> = combine(
        userSettingsRepository.appSettings,
        _availableRobots
    ) { settings, options ->
        options.firstOrNull {
            it.profile == settings.robotProfile && it.preset == settings.networkPreset
        } ?: options.first()
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        robotOptions.first()
    )

    val chatServerUrl: StateFlow<String> = userSettingsRepository.chatServerUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val chatDirectEnabled: StateFlow<Boolean> = userSettingsRepository.chatDirectEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val chatDirectBaseUrl: StateFlow<String> = userSettingsRepository.chatDirectBaseUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val chatDirectAuthToken: StateFlow<String> = userSettingsRepository.chatDirectAuthToken
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val voiceEngine: StateFlow<VoiceEngine> = userSettingsRepository.voiceEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceEngine.SYSTEM)

    val speedTierOverrides: StateFlow<UserSettingsRepository.SpeedTierOverrides> =
        userSettingsRepository.speedTierOverrides
            .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettingsRepository.SpeedTierOverrides())

    val voiceModel: StateFlow<com.recomo.common.chat.voice.WhisperModel> = userSettingsRepository.voiceModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.recomo.common.chat.voice.WhisperModel.TINY)

    val customGatewayUrl: StateFlow<String> = userSettingsRepository.customGatewayUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val sessionFolderPath: StateFlow<String> = userSettingsRepository.sessionFolderPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val countdownDurationSeconds: StateFlow<Int> = userSettingsRepository.countdownDurationSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 3)

    val sceneViewerFolderPath: StateFlow<String> = userSettingsRepository.sceneViewerFolderPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val useWebRTC: StateFlow<Boolean> = userSettingsRepository.appSettings
        .map { it.useWebRTC }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val videoSource: StateFlow<VideoSource> = userSettingsRepository.appSettings
        .map { it.videoSource }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VideoSource.WS_ORIN)

    val connectionStatus: StateFlow<UserConnectionStatus> = gatewayClient.connectionState
        .combine(_stage) { state, stage ->
            when (state) {
                is ConnectionState.Connected -> UserConnectionStatus.Connected
                is ConnectionState.Connecting -> UserConnectionStatus.Connecting
                is ConnectionState.Disconnected -> {
                    if (stage == UserShellStage.Main) UserConnectionStatus.Disconnected
                    else UserConnectionStatus.Disconnected
                }
                is ConnectionState.Error -> UserConnectionStatus.Disconnected
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserConnectionStatus.Disconnected)

    init {
        viewModelScope.launch {
            delay(1200)
            _stage.value = UserShellStage.Connection
            _statusMessage.value = "Ready to connect"
        }

        viewModelScope.launch {
            gatewayClient.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        _statusMessage.value = "Connected"
                        _route.value = UserMainRoute.MainControl
                        _stage.value = UserShellStage.Main
                    }
                    is ConnectionState.Connecting -> {
                        _statusMessage.value = "Connecting to ${selectedRobot.value.label}..."
                    }
                    is ConnectionState.Disconnected -> {
                        if (_stage.value != UserShellStage.Splash) {
                            _statusMessage.value = "Disconnected"
                        }
                    }
                    is ConnectionState.Error -> {
                        _statusMessage.value = state.message.ifBlank { "Connection failed" }
                        _stage.value = UserShellStage.Connection
                    }
                }
            }
        }
    }

    fun selectRobot(option: UserRobotOption) {
        viewModelScope.launch {
            userSettingsRepository.selectRobot(option.profile, option.preset)
            _statusMessage.value = "Selected ${option.label}"
        }
    }

    fun updateChatServerUrl(url: String) {
        viewModelScope.launch {
            userSettingsRepository.updateChatServerUrl(url)
        }
    }

    fun updateChatDirectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateChatDirectEnabled(enabled)
        }
    }

    fun updateChatDirectBaseUrl(url: String) {
        viewModelScope.launch {
            userSettingsRepository.updateChatDirectBaseUrl(url)
        }
    }

    fun updateChatDirectAuthToken(token: String) {
        viewModelScope.launch {
            userSettingsRepository.updateChatDirectAuthToken(token)
        }
    }

    fun updateVoiceEngine(engine: VoiceEngine) {
        viewModelScope.launch {
            userSettingsRepository.updateVoiceEngine(engine)
        }
    }

    fun updateVoiceModel(model: com.recomo.common.chat.voice.WhisperModel) {
        viewModelScope.launch {
            userSettingsRepository.updateVoiceModel(model)
        }
    }

    fun downloadWhisperModel() {
        viewModelScope.launch {
            val modelId = voiceModel.value.modelId
            whisperModelRepository.ensureModel(modelId)
        }
    }

    fun updateCustomGatewayUrl(url: String) {
        viewModelScope.launch {
            userSettingsRepository.updateCustomGatewayUrl(url)
        }
    }

    fun updateSessionFolderPath(path: String) {
        viewModelScope.launch {
            userSettingsRepository.updateSessionFolderPath(path)
        }
    }

    fun updateSceneViewerFolderPath(path: String) {
        viewModelScope.launch {
            userSettingsRepository.updateSceneViewerFolderPath(path)
            // Apply immediately so the repository picks up the new folder.
            if (path.isNotBlank()) {
                sceneAssetRepository.setScenesFolder(File(path))
            }
        }
    }

    fun updateUseWebRTC(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateUseWebRTC(enabled)
        }
    }

    fun updateSpeedTiers(slow: Float, normal: Float, fast: Float) {
        viewModelScope.launch {
            userSettingsRepository.updateSpeedTier(slow, normal, fast)
        }
    }

    fun updateCountdownDuration(seconds: Int) {
        viewModelScope.launch {
            userSettingsRepository.updateCountdownDuration(seconds)
        }
    }

    fun updateVideoSource(source: VideoSource) {
        viewModelScope.launch {
            userSettingsRepository.updateVideoSource(source)
        }
    }

    fun connect(pendingCustomUrl: String? = null) {
        viewModelScope.launch {
            // Flush any pending custom URL to DataStore before reading settings
            if (pendingCustomUrl != null) {
                userSettingsRepository.updateCustomGatewayUrl(pendingCustomUrl)
            }
            val selected = selectedRobot.value
            _statusMessage.value = "Connecting to ${selected.label}..."
            val settings = userSettingsRepository.appSettings.first()
            val url = settings.signalingUrl
            gatewayClient.connect(url)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gatewayClient.disconnect()
            _stage.value = UserShellStage.Connection
            _route.value = UserMainRoute.MainControl
            _settingsOpen.value = false
            _statusMessage.value = "Disconnected"
        }
    }

    /**
     * Skip robot connection and go straight to the main workspace.
     * Features that require the gateway (touch control, run on real robot)
     * degrade gracefully. Features that don't (AI chat, 3D preview, library
     * browsing, settings) work normally.
     */
    fun enterDemoMode() {
        _stage.value = UserShellStage.Main
        _route.value = UserMainRoute.MainControl
        _statusMessage.value = "Demo mode — no robot connected"
    }

    fun navigateTo(route: UserMainRoute) {
        _route.value = route
        _settingsOpen.value = false
    }

    fun openSettings() {
        _settingsOpen.value = true
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    fun openSceneViewer(request: SceneViewerLaunchRequest) {
        _sceneViewerRequest.value = request
        _settingsOpen.value = false
    }

    fun closeSceneViewer() {
        _sceneViewerRequest.value = null
    }

    /**
     * Ensure the SceneViewer HTTP server is running and the SPZ folder has been
     * scanned. Safe to call multiple times — server start is idempotent and folder
     * scans are cheap.
     *
     * Priority for the scenes folder:
     *   1. User-configured folder from Settings (sceneViewerFolderPath)
     *   2. [fallbackFolder] (typically `<externalFilesDir>/sceneviewer/scenes`)
     *   3. Currently-configured repository folder, if any
     */
    fun ensureSceneAssetsReady(fallbackFolder: File?) {
        val userPath = sceneViewerFolderPath.value.takeIf { it.isNotBlank() }
        val userFolder = userPath?.let { File(it) }?.takeIf { it.isDirectory }
        val folder = userFolder ?: fallbackFolder ?: sceneAssetRepository.currentFolder()
        if (folder != null && folder != sceneAssetRepository.currentFolder()) {
            sceneAssetRepository.setScenesFolder(folder)
        } else if (folder != null && sceneAssetRepository.entries.value.isEmpty()) {
            sceneAssetRepository.refresh()
        }
        runCatching { sceneViewerHttpServer.start() }
    }

    fun stopSceneViewerServer() {
        runCatching { sceneViewerHttpServer.stop() }
    }

    override fun onCleared() {
        super.onCleared()
        stopSceneViewerServer()
    }
}
