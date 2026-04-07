package com.recomo.user.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.NetworkPreset
import com.recomo.common.model.RobotProfile
import com.recomo.common.network.OrinGatewayClient
import com.recomo.user.control.UserConnectionStatus
import com.recomo.user.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    SlamMaps
}

data class UserRobotOption(
    val profile: RobotProfile,
    val preset: NetworkPreset,
    val label: String
)

@HiltViewModel
class UserMainViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {
    private val robotOptions = listOf(
        UserRobotOption(RobotProfile.RECOMO_PROTO1_DOGWOOD01, NetworkPreset.RECOMO_LOCAL, "dogwood-01"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_CEDAR01, NetworkPreset.RECOMO_LOCAL, "cedar-01"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_ELM01, NetworkPreset.RECOMO_LOCAL, "elm-01"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_CICDEVICE, NetworkPreset.RECOMO_LOCAL, "test-01 (Orin 160)"),
        UserRobotOption(RobotProfile.RECOMO_PROTO1_TEST02, NetworkPreset.RECOMO_LOCAL, "test-02 (Orin 162)"),
        UserRobotOption(RobotProfile.VIRTUAL_SIM, NetworkPreset.DEBUG_MAC, "virtual-sim"),
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

    val customGatewayUrl: StateFlow<String> = userSettingsRepository.customGatewayUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val sessionFolderPath: StateFlow<String> = userSettingsRepository.sessionFolderPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

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

    fun connect() {
        viewModelScope.launch {
            val selected = selectedRobot.value
            _statusMessage.value = "Connecting to ${selected.label}..."
            val url = if (selected.profile == RobotProfile.CUSTOM) {
                customGatewayUrl.value
            } else {
                selected.preset.getSignalingUrl(
                    profile = selected.profile,
                    useTztek = selected.profile.useTztekOrin ?: false
                )
            }
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
}
