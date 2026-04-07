package com.recomo.user.control

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
import com.recomo.user.BuildConfig
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.data.system.UserOrinRobotIdentity
import com.recomo.user.data.system.UserOrinServiceRepository
import com.recomo.user.data.system.UserOrinServiceStatus
import com.recomo.user.ui.screens.system.GatewayConnectionStatus
import com.recomo.user.ui.screens.system.GatewayControlCardUiState
import com.recomo.user.ui.screens.system.GatewayServiceCardUiState
import com.recomo.user.ui.screens.system.ActuatorCardUiState
import com.recomo.user.ui.screens.system.AutonomyCardUiState
import com.recomo.user.ui.screens.system.CloudCardUiState
import com.recomo.user.ui.screens.system.PncAuthorityCardUiState
import com.recomo.user.ui.screens.system.RobotPreparationCardUiState
import com.recomo.user.ui.screens.system.SceneCardUiState
import com.recomo.user.ui.screens.system.StackLifecycleCardUiState
import com.recomo.user.ui.screens.system.SystemInfoCardUiState
import com.recomo.user.ui.screens.system.SystemLoadCardUiState
import com.recomo.user.ui.screens.system.TierComponentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val GATEWAY_SERVICE_ID = "recomo_gateway_ws"
private const val DRIVER_ACTION_COOLDOWN_MS = 2500L
private const val DRIVER_ACTION_TIMEOUT_MS = 10_000L
private const val STACK_ACTION_ACK_TIMEOUT_MS = 3000L
private const val DEFAULT_PNC_MODE = "motion_replica"

private enum class DriverAction {
    None,
    Start,
    Stop
}

private enum class DriverLifecycleState {
    Idle,
    Preparing,
    Ready,
    Dismissing
}

private data class GatewayLiveSnapshot(
    val connection: ConnectionState,
    val stateHz: Double,
    val lastStateAtMs: Long,
    val robotState: JsonObject?
)

private data class SystemMetaSnapshot(
    val gatewayService: UserOrinServiceStatus?,
    val apiStatus: String,
    val apiLatencyMs: Long?,
    val identity: UserOrinRobotIdentity?,
    val prepMessage: String?,
    val prepError: String?
)

private data class GatewayServiceMeta(
    val gatewayService: UserOrinServiceStatus?,
    val apiStatus: String,
    val apiLatencyMs: Long?,
    val identity: UserOrinRobotIdentity?
)

private data class DriverPrepMeta(
    val prepMessage: String?,
    val prepError: String?
)

private data class UserOrinResources(
    val cpuOverallPct: Float,
    val gpuLoadPct: Float?,
    val memoryUsedPct: Float
)

@HiltViewModel
class UserSystemViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient,
    private val userSettingsRepository: UserSettingsRepository,
    private val orinServiceRepository: UserOrinServiceRepository
) : ViewModel() {
    private val _gatewayServiceStatus = MutableStateFlow<UserOrinServiceStatus?>(null)
    private val _gatewayServiceBusy = MutableStateFlow(false)
    private val _gatewayServiceError = MutableStateFlow<String?>(null)
    private val _gatewayApiStatus = MutableStateFlow("Unknown")
    private val _gatewayApiLatencyMs = MutableStateFlow<Long?>(null)
    private val _robotIdentity = MutableStateFlow<UserOrinRobotIdentity?>(null)
    private val _lastConnectedLabel = MutableStateFlow<String?>(null)
    private val _orinResources = MutableStateFlow<UserOrinResources?>(null)
    private val _recordingTopicsBusy = MutableStateFlow(false)
    private val _recordingTopicsError = MutableStateFlow<String?>(null)
    private val _recordingTopicsMessage = MutableStateFlow<String?>(null)
    private val _driverLifecycle = MutableStateFlow(DriverLifecycleState.Idle)
    private val _stackActionBusy = MutableStateFlow(false)
    private val _stackActionError = MutableStateFlow<String?>(null)
    private val _stackActionMessage = MutableStateFlow<String?>(null)

    private var lastDriverActionMs = 0L
    private var pendingDriverAction = DriverAction.None
    private var driverActionTimeoutJob: Job? = null
    private var pendingStackAction: String? = null
    private var stackActionAckTimeoutJob: Job? = null
    private var lastMapDetailSignature: String? = null

    private val liveSnapshot: StateFlow<GatewayLiveSnapshot> = combine(
        gatewayClient.connectionState,
        gatewayClient.stateHz,
        gatewayClient.lastStateAtMs,
        gatewayClient.robotState
    ) { connection, stateHz, lastStateAtMs, robotState ->
        GatewayLiveSnapshot(connection, stateHz, lastStateAtMs, robotState)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        GatewayLiveSnapshot(ConnectionState.Disconnected, 0.0, 0L, null)
    )

    private val systemMetaSnapshot: StateFlow<SystemMetaSnapshot> = combine(
        combine(
            _gatewayServiceStatus,
            _gatewayApiStatus,
            _gatewayApiLatencyMs,
            _robotIdentity
        ) { gatewayService, apiStatus, apiLatencyMs, identity ->
            GatewayServiceMeta(gatewayService, apiStatus, apiLatencyMs, identity)
        },
        combine(_recordingTopicsMessage, _recordingTopicsError) { prepMessage, prepError ->
            DriverPrepMeta(prepMessage, prepError)
        }
    ) { gatewayInfo, prepInfo ->
        SystemMetaSnapshot(
            gatewayService = gatewayInfo.gatewayService,
            apiStatus = gatewayInfo.apiStatus,
            apiLatencyMs = gatewayInfo.apiLatencyMs,
            identity = gatewayInfo.identity,
            prepMessage = prepInfo.prepMessage,
            prepError = prepInfo.prepError
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SystemMetaSnapshot(null, "Unknown", null, null, null, null)
    )

    private val streamStatusLabel: StateFlow<String> = combine(
        gatewayClient.lastStateAtMs,
        gatewayClient.stateHz
    ) { lastStateAtMs, stateHz ->
        val streamLabel = lastStateAtMs
            .takeIf { it > 0L }
            ?.let { timestamp -> (System.currentTimeMillis() - timestamp).coerceAtLeast(0L) }
            ?.let { age ->
                when {
                    age < 800L -> "Live"
                    age < 2000L -> "Lag"
                    else -> "Stale"
                }
            } ?: "Unknown"
        if (stateHz > 0.0) {
            "${String.format("%.1f Hz", stateHz)} · $streamLabel"
        } else {
            streamLabel
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Unknown")

    val infoCardState: StateFlow<SystemInfoCardUiState> = combine(
        userSettingsRepository.appSettings,
        liveSnapshot,
        systemMetaSnapshot
    ) { settings, live, meta ->
        val gatewayStatus = live.connection.toGatewayConnectionStatus()
        val batteryLabel = live.robotState.batteryLabel()
        val safetyLabel = live.robotState.safetyLabel()
        val ageLabel = live.lastStateAtMs
            .takeIf { it > 0L }
            ?.let { age -> "${(System.currentTimeMillis() - age).coerceAtLeast(0L)}ms" }

        SystemInfoCardUiState(
            systemName = settings.robotProfile.displayName,
            environmentLabel = meta.identity?.robotUnitId?.ifBlank { null }
                ?: meta.identity?.deviceId?.ifBlank { null }
                ?: settings.networkPreset.name,
            gatewayLabel = settings.signalingUrl,
            connectionStatus = gatewayStatus,
            deviceLabel = listOfNotNull(
                meta.identity?.robotSn?.takeIf { it.isNotBlank() },
                meta.identity?.localOrinIp?.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { null },
            appVersion = BuildConfig.VERSION_NAME,
            stateHzLabel = live.stateHz.takeIf { it > 0.0 }?.let { String.format("%.1f Hz", it) },
            lastStateAgeLabel = ageLabel,
            safetyLabel = safetyLabel ?: batteryLabel,
            note = listOfNotNull(
                meta.gatewayService?.let { if (it.running) "Gateway service running" else "Gateway service stopped" },
                meta.apiStatus.takeIf { it != "Unknown" }?.let { status ->
                    if (meta.apiLatencyMs != null && status == "OK") "$status ${meta.apiLatencyMs}ms" else status
                },
                meta.prepError?.takeIf { it.isNotBlank() },
                meta.prepMessage?.takeIf { it.isNotBlank() }
            ).firstOrNull()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SystemInfoCardUiState())

    val gatewayControlState: StateFlow<GatewayControlCardUiState> = combine(
        userSettingsRepository.appSettings,
        gatewayClient.connectionState,
        _gatewayServiceError
    ) { settings, connection, errorMessage ->
        val status = connection.toGatewayConnectionStatus()
        GatewayControlCardUiState(
            gatewayUrl = settings.signalingUrl,
            connectionStatus = status,
            statusLabel = when (connection) {
                is ConnectionState.Connected -> "Socket connected"
                is ConnectionState.Connecting -> "Opening control/state streams"
                is ConnectionState.Error -> connection.message.ifBlank { "Socket error" }
                is ConnectionState.Disconnected -> "Socket disconnected"
            },
            isConnecting = connection is ConnectionState.Connecting,
            connectButtonLabel = if (connection is ConnectionState.Connected) "Reconnect" else "Connect",
            disconnectButtonLabel = "Disconnect",
            primaryActionEnabled = connection !is ConnectionState.Connecting,
            secondaryActionEnabled = connection is ConnectionState.Connected || connection is ConnectionState.Connecting,
            errorMessage = errorMessage?.takeIf { connection !is ConnectionState.Connected },
            lastConnectedLabel = _lastConnectedLabel.value
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GatewayControlCardUiState())

    val systemLoadState: StateFlow<SystemLoadCardUiState> = combine(
        liveSnapshot,
        _orinResources
    ) { live, resources ->
        SystemLoadCardUiState(
            batteryLabel = live.robotState.batteryPercentLabel(),
            cpuLabel = resources?.cpuOverallPct?.toPercentLabel() ?: "—",
            gpuLabel = resources?.gpuLoadPct?.toPercentLabel() ?: "—",
            memoryLabel = resources?.memoryUsedPct?.toPercentLabel() ?: "—",
            note = resources?.let {
                "CPU ${it.cpuOverallPct.toPercentLabel()} · GPU ${it.gpuLoadPct.toPercentLabel()} · RAM ${it.memoryUsedPct.toPercentLabel()}"
            } ?: "System resource endpoint unavailable"
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SystemLoadCardUiState())

    val gatewayServiceState: StateFlow<GatewayServiceCardUiState> = combine(
        _gatewayServiceStatus,
        _gatewayServiceBusy,
        _gatewayServiceError,
        _gatewayApiStatus,
        _gatewayApiLatencyMs
    ) { serviceStatus, busy, error, apiStatus, apiLatencyMs ->
        GatewayServiceCardUiState(
            serviceLabel = when {
                busy -> "Working..."
                serviceStatus?.running == true -> "Running${serviceStatus.pid?.let { " (pid $it)" } ?: ""}"
                serviceStatus != null -> "Stopped"
                else -> "Unknown"
            },
            apiLabel = when {
                apiStatus == "OK" && apiLatencyMs != null -> "OK ${apiLatencyMs}ms"
                apiStatus == "OK" -> "OK"
                apiStatus == "ERR" -> "Error"
                else -> "Unknown"
            },
            isBusy = busy,
            canRefresh = !busy,
            canStart = !busy && serviceStatus?.running != true,
            canStop = !busy && serviceStatus?.running == true,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GatewayServiceCardUiState())

    val robotPreparationState: StateFlow<RobotPreparationCardUiState> = combine(
        _driverLifecycle,
        _recordingTopicsBusy,
        _recordingTopicsMessage,
        _recordingTopicsError,
        streamStatusLabel
    ) { lifecycle, busy, message, error, streamLabel ->
        RobotPreparationCardUiState(
            statusLabel = when (lifecycle) {
                DriverLifecycleState.Idle -> "Idle"
                DriverLifecycleState.Preparing -> "Preparing"
                DriverLifecycleState.Ready -> "Ready"
                DriverLifecycleState.Dismissing -> "Dismissing"
            },
            stateStreamLabel = streamLabel,
            isBusy = busy,
            canPrepare = !busy && lifecycle == DriverLifecycleState.Idle,
            canDismiss = !busy && (lifecycle == DriverLifecycleState.Ready || lifecycle == DriverLifecycleState.Preparing),
            message = message,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RobotPreparationCardUiState())

    val pncAuthorityState: StateFlow<PncAuthorityCardUiState> = combine(
        liveSnapshot,
        _stackActionBusy,
        _stackActionMessage,
        _stackActionError
    ) { live, busy, message, error ->
        PncAuthorityCardUiState(
            authorityLabel = live.robotState.currentPncAuthority(),
            isBusy = busy,
            message = message,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PncAuthorityCardUiState())

    val stackLifecycleState: StateFlow<StackLifecycleCardUiState> = combine(
        liveSnapshot,
        _stackActionBusy,
        _stackActionMessage,
        _stackActionError
    ) { live, busy, message, error ->
        val currentLocation = live.robotState.currentLocationLabel()
        val currentMap = live.robotState.currentMapAssetLabel()
        StackLifecycleCardUiState(
            contextLabel = "$currentLocation / $currentMap",
            modeLabel = DEFAULT_PNC_MODE,
            isBusy = busy,
            canPrepareLocalization = currentLocation != "—",
            message = message,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StackLifecycleCardUiState())

    // ── Tier-based state flows ──────────────────────────────────

    val actuatorCardState: StateFlow<ActuatorCardUiState> = combine(
        _driverLifecycle,
        _recordingTopicsBusy,
        _recordingTopicsMessage,
        _recordingTopicsError,
        liveSnapshot
    ) { lifecycle, busy, message, error, live ->
        val tier = live.robotState.tierStatus("actuators")
        val armPubs = tier?.intField("arm_pubs") ?: 0
        val gimbalPubs = tier?.intField("gimbal_pubs") ?: 0
        val chassisPubs = tier?.intField("chassis_pubs") ?: 0
        ActuatorCardUiState(
            statusLabel = lifecycle.name,
            stateStreamLabel = live.stateHz.let { if (it > 0) "${String.format("%.1f Hz", it)}" else "—" },
            components = listOf(
                TierComponentStatus("Arm", armPubs > 0),
                TierComponentStatus("Gimbal", gimbalPubs > 0),
                TierComponentStatus("Chassis", chassisPubs > 0)
            ),
            isBusy = busy,
            canPrepare = !busy && lifecycle == DriverLifecycleState.Idle,
            canDismiss = !busy && (lifecycle == DriverLifecycleState.Ready || lifecycle == DriverLifecycleState.Preparing),
            message = message,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ActuatorCardUiState())

    val sceneCardState: StateFlow<SceneCardUiState> = combine(
        liveSnapshot,
        _stackActionBusy,
        _stackActionMessage,
        _stackActionError
    ) { live, busy, message, error ->
        val sensors = live.robotState.tierStatus("sensors")
        val loc = live.robotState.tierStatus("localization")
        val perc = live.robotState.tierStatus("perception")
        val currentLocation = live.robotState.currentLocationLabel()
        val currentMap = live.robotState.currentMapAssetLabel()
        SceneCardUiState(
            contextLabel = "$currentLocation / $currentMap",
            sensors = listOf(
                TierComponentStatus("Lidar", sensors?.boolField("lidar_ok") ?: false),
                TierComponentStatus("Camera", sensors?.boolField("camera_ok") ?: false),
                TierComponentStatus("IMU", (sensors?.intField("imu_pubs") ?: 0) > 0)
            ),
            localization = listOf(
                TierComponentStatus("Odom", (loc?.intField("odom_pubs") ?: 0) > 0),
                TierComponentStatus("SLAM", (loc?.intField("robot_pose_pubs") ?: 0) > 0)
            ),
            perception = listOf(
                TierComponentStatus("Occupancy", perc?.boolField("occupancy_ok") ?: false),
                TierComponentStatus("Detection", perc?.boolField("detection_ok") ?: false)
            ),
            sensorsReady = sensors?.boolField("ready") ?: false,
            localizationReady = loc?.boolField("ready") ?: false,
            perceptionReady = perc?.boolField("ready") ?: false,
            isBusy = busy,
            canPrepareLocalization = currentLocation != "—",
            message = message,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SceneCardUiState())

    val autonomyCardState: StateFlow<AutonomyCardUiState> = combine(
        liveSnapshot,
        _stackActionBusy,
        _stackActionMessage,
        _stackActionError
    ) { live, busy, message, error ->
        val autonomy = live.robotState.tierStatus("autonomy")
        val loc = live.robotState.tierStatus("localization")
        val perc = live.robotState.tierStatus("perception")
        val sceneReady = (loc?.boolField("ready") ?: false) && (perc?.boolField("ready") ?: false)
        val fsmState = live.robotState?.get("run")?.jsonObject
            ?.get("pnc_fsm_state")?.jsonPrimitive?.intOrNull
        AutonomyCardUiState(
            pncModeLabel = autonomy?.get("pnc_mode")?.jsonPrimitive?.contentOrNull ?: "none",
            authorityLabel = live.robotState.currentPncAuthority(),
            fsmStateLabel = when (fsmState) {
                0 -> "Init"
                1 -> "Running"
                2 -> "Exit"
                3 -> "Demo"
                4 -> "Finished"
                else -> "—"
            },
            ready = autonomy?.boolField("ready") ?: false,
            sceneReady = sceneReady,
            isBusy = busy,
            message = message,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AutonomyCardUiState())

    val cloudCardState: StateFlow<CloudCardUiState> = liveSnapshot.map { live ->
        val cloud = live.robotState.tierStatus("cloud")
        CloudCardUiState(
            videoManagementOk = cloud?.boolField("video_management_ok") ?: false,
            chatServerOk = cloud?.boolField("chat_server_ok") ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CloudCardUiState())

    val topStatusHighlights: StateFlow<List<String>> = combine(
        liveSnapshot,
        _orinResources,
        _gatewayServiceStatus,
        _driverLifecycle
    ) { live, resources, gatewayService, driverLifecycle ->
        buildList {
            live.robotState.batteryLabel()?.let { add(it) }
            resources?.worstLoadLabel()?.let { add(it) }
            buildServiceHealthLabel(
                gatewayService = gatewayService,
                robotState = live.robotState
            )?.let { add(it) }
            if (driverLifecycle != DriverLifecycleState.Idle) {
                add("Robot ${driverLifecycle.name.lowercase()}")
            }
            live.stateHz.takeIf { it > 0.0 }?.let { add("State ${String.format("%.1f Hz", it)}") }
        }.take(3)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            gatewayClient.connectionState
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connection ->
                    if (connection) {
                        _lastConnectedLabel.value = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        refreshGatewayService()
                        refreshRobotIdentity()
                        refreshSystemResources()
                    }
                }
        }

        viewModelScope.launch {
            gatewayClient.connectionState.collectLatest { connection ->
                if (connection is ConnectionState.Connected) {
                    while (true) {
                        delay(10_000)
                        refreshGatewayService()
                        refreshSystemResources()
                    }
                }
            }
        }

        viewModelScope.launch {
            gatewayClient.robotState.collect { state ->
                updateDriverLifecycleFromState(state)
                processMapDetailAck(state)
            }
        }
    }

    fun connectOrReconnect() {
        viewModelScope.launch {
            val gatewayUrl = userSettingsRepository.appSettings.first().signalingUrl
            if (gatewayUrl.isBlank()) return@launch
            if (gatewayClient.isConnected()) {
                gatewayClient.disconnect()
                delay(200)
            }
            gatewayClient.connect(gatewayUrl)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gatewayClient.disconnect()
        }
    }

    fun refreshGatewayService() {
        viewModelScope.launch {
            _gatewayServiceBusy.value = true
            _gatewayServiceError.value = null
            val startedAt = System.currentTimeMillis()
            val result = orinServiceRepository.fetchStatus()
            if (result.isSuccess) {
                val services = result.getOrNull().orEmpty()
                _gatewayServiceStatus.value = services[GATEWAY_SERVICE_ID]
                _gatewayApiStatus.value = "OK"
                _gatewayApiLatencyMs.value = System.currentTimeMillis() - startedAt
            } else {
                _gatewayServiceError.value = result.exceptionOrNull()?.message ?: "Gateway status failed"
                _gatewayApiStatus.value = "ERR"
                _gatewayApiLatencyMs.value = null
            }
            _gatewayServiceBusy.value = false
        }
    }

    fun startGatewayService() {
        viewModelScope.launch {
            _gatewayServiceBusy.value = true
            _gatewayServiceError.value = null
            val result = orinServiceRepository.startService(GATEWAY_SERVICE_ID)
            if (result.isSuccess) {
                _gatewayServiceStatus.value = result.getOrNull()?.services?.get(GATEWAY_SERVICE_ID)
                _gatewayApiStatus.value = "OK"
            } else {
                _gatewayServiceError.value = result.exceptionOrNull()?.message ?: "Gateway start failed"
            }
            _gatewayServiceBusy.value = false
        }
    }

    fun stopGatewayService() {
        viewModelScope.launch {
            _gatewayServiceBusy.value = true
            _gatewayServiceError.value = null
            val result = orinServiceRepository.stopService(GATEWAY_SERVICE_ID)
            if (result.isSuccess) {
                _gatewayServiceStatus.value = result.getOrNull()?.services?.get(GATEWAY_SERVICE_ID)
            } else {
                _gatewayServiceError.value = result.exceptionOrNull()?.message ?: "Gateway stop failed"
            }
            _gatewayServiceBusy.value = false
        }
    }

    fun refreshRobotIdentity() {
        viewModelScope.launch {
            val result = orinServiceRepository.fetchIdentity()
            _robotIdentity.value = result.getOrNull()
        }
    }

    fun refreshSystemResources() {
        viewModelScope.launch {
            val result = orinServiceRepository.fetchSystemResources()
            _orinResources.value = result.getOrNull()?.toUserOrinResources()
        }
    }

    fun prepareRobot() {
        viewModelScope.launch {
            if (!canTriggerDriverAction(DriverAction.Start)) return@launch
            _recordingTopicsBusy.value = true
            _recordingTopicsError.value = null
            _recordingTopicsMessage.value = "Preparing robot drivers..."
            pendingDriverAction = DriverAction.Start
            _driverLifecycle.value = DriverLifecycleState.Preparing
            scheduleDriverActionTimeout(DriverAction.Start)
            try {
                gatewayClient.sendControl(
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", "LocalizationCmd")
                        put("action", "start_all")
                    }
                )
            } catch (e: Exception) {
                failDriverAction("Failed to start drivers: ${e.message}")
            }
        }
    }

    fun dismissRobot() {
        viewModelScope.launch {
            if (!canTriggerDriverAction(DriverAction.Stop)) return@launch
            _recordingTopicsBusy.value = true
            _recordingTopicsError.value = null
            _recordingTopicsMessage.value = "Dismissing robot drivers..."
            pendingDriverAction = DriverAction.Stop
            _driverLifecycle.value = DriverLifecycleState.Dismissing
            scheduleDriverActionTimeout(DriverAction.Stop)
            try {
                gatewayClient.sendControl(
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", "LocalizationCmd")
                        put("action", "stop_all")
                    }
                )
            } catch (e: Exception) {
                failDriverAction("Failed to stop drivers: ${e.message}")
            }
        }
    }

    // ── Tier 1: Actuators ──────────────────────────────────────────

    fun prepareActuators() {
        viewModelScope.launch {
            if (!canTriggerDriverAction(DriverAction.Start)) return@launch
            _recordingTopicsBusy.value = true
            _recordingTopicsError.value = null
            _recordingTopicsMessage.value = "Preparing actuators..."
            pendingDriverAction = DriverAction.Start
            _driverLifecycle.value = DriverLifecycleState.Preparing
            scheduleDriverActionTimeout(DriverAction.Start)
            try {
                gatewayClient.sendControl(buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", "prepare_actuators")
                })
            } catch (e: Exception) {
                failDriverAction("Failed to prepare actuators: ${e.message}")
            }
        }
    }

    fun dismissActuators() {
        viewModelScope.launch {
            if (!canTriggerDriverAction(DriverAction.Stop)) return@launch
            _recordingTopicsBusy.value = true
            _recordingTopicsError.value = null
            _recordingTopicsMessage.value = "Dismissing actuators..."
            pendingDriverAction = DriverAction.Stop
            _driverLifecycle.value = DriverLifecycleState.Dismissing
            scheduleDriverActionTimeout(DriverAction.Stop)
            try {
                gatewayClient.sendControl(buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", "dismiss_actuators")
                })
            } catch (e: Exception) {
                failDriverAction("Failed to dismiss actuators: ${e.message}")
            }
        }
    }

    // ── Tier 2a: Sensors ────────────────────────────────────────

    fun prepareSensors() {
        triggerStackAction(
            action = "prepare_sensors",
            busyMessage = "Preparing sensors..."
        )
    }

    fun dismissSensors() {
        triggerStackAction(
            action = "dismiss_sensors",
            busyMessage = "Stopping sensors..."
        )
    }

    // ── Tier 2 convenience: Scene ──────────────────────────────

    fun prepareScene(threshold: Double = 0.30) {
        val locationId = liveSnapshot.value.robotState.currentLocationId()
        if (locationId.isNullOrBlank()) {
            _stackActionError.value = "Select location in Home first"
            return
        }
        val mapName = liveSnapshot.value.robotState.currentMapAssetId() ?: "hummingbird_map_01"
        triggerStackAction(
            action = "prepare_scene",
            busyMessage = "Preparing scene (sensors + localization + perception)..."
        ) {
            put("location_id", locationId)
            put("map_name", mapName)
            put("threshold", threshold.coerceIn(0.0, 1.0))
        }
    }

    fun dismissScene() {
        triggerStackAction(
            action = "dismiss_scene",
            busyMessage = "Stopping scene..."
        )
    }

    // ── Existing stack actions (keep for backward compat) ───────

    fun preparePerceptionNodes() {
        triggerStackAction(
            action = "prepare_perception",
            busyMessage = "Preparing perception nodes..."
        )
    }

    fun dismissPerceptionNodes() {
        triggerStackAction(
            action = "dismiss_perception",
            busyMessage = "Stopping perception nodes..."
        )
    }

    fun prepareLocalizationNodes(threshold: Double = 0.30) {
        val locationId = liveSnapshot.value.robotState.currentLocationId()
        if (locationId.isNullOrBlank()) {
            _stackActionError.value = "Select location in Home first"
            return
        }
        val mapName = liveSnapshot.value.robotState.currentMapAssetId() ?: "hummingbird_map_01"
        triggerStackAction(
            action = "prepare_localization",
            busyMessage = "Preparing localization nodes..."
        ) {
            put("location_id", locationId)
            put("map_name", mapName)
            put("threshold", threshold.coerceIn(0.0, 1.0))
        }
    }

    fun dismissLocalizationNodes() {
        triggerStackAction(
            action = "dismiss_localization",
            busyMessage = "Stopping localization nodes..."
        )
    }

    fun preparePncNodes() {
        triggerStackAction(
            action = "prepare_pnc",
            busyMessage = "Preparing PnC for ${DEFAULT_PNC_MODE.replace('_', ' ')}..."
        ) {
            put("mode", DEFAULT_PNC_MODE)
        }
    }

    fun dismissPncNodes() {
        triggerStackAction(
            action = "dismiss_pnc",
            busyMessage = "Stopping PnC nodes..."
        )
    }

    fun setPncAuthority(authority: String) {
        val normalized = authority.trim().lowercase()
        if (normalized !in setOf("auto", "local", "external")) {
            _stackActionError.value = "Invalid PnC authority: $authority"
            return
        }
        triggerStackAction(
            action = "set_pnc_authority",
            busyMessage = "Setting PnC authority to $normalized..."
        ) {
            put("authority", normalized)
        }
    }

    private fun triggerStackAction(
        action: String,
        busyMessage: String,
        onBuild: (kotlinx.serialization.json.JsonObjectBuilder.() -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (_stackActionBusy.value) return@launch
            _stackActionBusy.value = true
            _stackActionError.value = null
            _stackActionMessage.value = busyMessage
            pendingStackAction = action
            stackActionAckTimeoutJob?.cancel()
            stackActionAckTimeoutJob = viewModelScope.launch {
                delay(STACK_ACTION_ACK_TIMEOUT_MS)
                if (pendingStackAction == action) {
                    pendingStackAction = null
                    _stackActionBusy.value = false
                    _stackActionMessage.value = null
                    _stackActionError.value = "$action: timeout waiting for ack"
                }
            }
            try {
                val cmd = buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", action)
                    onBuild?.invoke(this)
                }
                gatewayClient.sendControl(cmd)
            } catch (e: Exception) {
                stackActionAckTimeoutJob?.cancel()
                stackActionAckTimeoutJob = null
                pendingStackAction = null
                _stackActionBusy.value = false
                _stackActionMessage.value = null
                _stackActionError.value = "Failed to send $action: ${e.message}"
            }
        }
    }

    private fun processMapDetailAck(state: JsonObject?) {
        val detail = state?.get("map_detail")?.jsonObject
            ?: state?.get("maps")?.jsonObject?.get("detail")?.jsonObject
            ?: return
        val action = detail["action"]?.jsonPrimitive?.contentOrNull ?: return
        val signature = detail.toString()
        val shouldProcess = signature != lastMapDetailSignature || pendingStackAction == action
        if (!shouldProcess) return

        lastMapDetailSignature = signature
        val success = detail["success"]?.jsonPrimitive?.booleanOrNull
        when (action) {
            "prepare_sensors",
            "dismiss_sensors",
            "prepare_scene",
            "dismiss_scene",
            "prepare_perception",
            "dismiss_perception",
            "prepare_localization",
            "dismiss_localization",
            "prepare_pnc",
            "dismiss_pnc",
            "stop_pnc",
            "set_pnc_authority" -> {
                if (pendingStackAction == action) {
                    stackActionAckTimeoutJob?.cancel()
                    stackActionAckTimeoutJob = null
                    pendingStackAction = null
                    _stackActionBusy.value = false
                    if (success == true) {
                        _stackActionError.value = null
                        _stackActionMessage.value = "$action: OK"
                    } else if (success == false) {
                        _stackActionMessage.value = null
                        _stackActionError.value =
                            detail["error"]?.jsonPrimitive?.contentOrNull ?: "$action failed"
                    }
                }
            }
            "start_all", "prepare_actuators" -> {
                if (success == false) {
                    failDriverAction(
                        detail["error"]?.jsonPrimitive?.contentOrNull ?: "Prepare failed"
                    )
                }
            }
            "stop_all", "dismiss_actuators" -> {
                if (success == false) {
                    failDriverAction(
                        detail["error"]?.jsonPrimitive?.contentOrNull ?: "Dismiss failed"
                    )
                }
            }
        }
    }

    private fun updateDriverLifecycleFromState(state: JsonObject?) {
        val topicHealth = state?.get("topic_health")?.jsonObject ?: return
        val armPubs = topicHealth["arm_state_pubs"]?.jsonPrimitive?.intOrNull ?: 0
        val gimbalPubs = topicHealth["gimbal_state_pubs"]?.jsonPrimitive?.intOrNull ?: 0

        // Lifecycle tracks arm + gimbal actuator drivers only.
        // Odom/chassis is started by gateway's start_all but doesn't gate
        // the Ready transition — chassis has no dedicated state topic in
        // topic_health.  Don't use the sticky _ok flags (have_arm_js etc.)
        // because they stay true after drivers stop and cause a stuck
        // Preparing state.
        val actuatorPubs = armPubs + gimbalPubs
        val armRunning = armPubs > 0
        val gimbalRunning = gimbalPubs > 0
        val ready = armRunning && gimbalRunning

        val next = when {
            ready -> DriverLifecycleState.Ready
            actuatorPubs == 0 && pendingDriverAction == DriverAction.Stop -> DriverLifecycleState.Idle
            pendingDriverAction == DriverAction.Stop -> DriverLifecycleState.Dismissing
            else -> if (armRunning || gimbalRunning) DriverLifecycleState.Preparing
                    else DriverLifecycleState.Idle
        }
        _driverLifecycle.value = next

        if (ready) {
            finalizeDriverAction("Robot ready")
        } else if (actuatorPubs == 0 && pendingDriverAction == DriverAction.Stop) {
            finalizeDriverAction("Drivers dismissed")
        }
    }

    private fun finalizeDriverAction(message: String) {
        driverActionTimeoutJob?.cancel()
        driverActionTimeoutJob = null
        if (pendingDriverAction != DriverAction.None) {
            _recordingTopicsMessage.value = message
        }
        pendingDriverAction = DriverAction.None
        _recordingTopicsBusy.value = false
    }

    private fun failDriverAction(message: String) {
        driverActionTimeoutJob?.cancel()
        driverActionTimeoutJob = null
        pendingDriverAction = DriverAction.None
        _recordingTopicsBusy.value = false
        _recordingTopicsMessage.value = null
        _recordingTopicsError.value = message
    }

    private fun scheduleDriverActionTimeout(action: DriverAction) {
        driverActionTimeoutJob?.cancel()
        driverActionTimeoutJob = viewModelScope.launch {
            delay(DRIVER_ACTION_TIMEOUT_MS)
            if (_recordingTopicsBusy.value && pendingDriverAction == action) {
                pendingDriverAction = DriverAction.None
                _recordingTopicsBusy.value = false
                _recordingTopicsError.value = if (action == DriverAction.Start) {
                    "Prepare robot timed out"
                } else {
                    "Dismiss timed out"
                }
            }
        }
    }

    private fun canTriggerDriverAction(action: DriverAction): Boolean {
        if (_recordingTopicsBusy.value) {
            _recordingTopicsMessage.value = "Please wait..."
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastDriverActionMs < DRIVER_ACTION_COOLDOWN_MS) {
            _recordingTopicsMessage.value = "Please wait..."
            return false
        }
        when (action) {
            DriverAction.Start -> when (_driverLifecycle.value) {
                DriverLifecycleState.Ready -> {
                    _recordingTopicsMessage.value = "Already ready"
                    return false
                }
                DriverLifecycleState.Preparing -> {
                    _recordingTopicsMessage.value = "Already preparing"
                    return false
                }
                else -> Unit
            }
            DriverAction.Stop -> when (_driverLifecycle.value) {
                DriverLifecycleState.Idle -> {
                    _recordingTopicsMessage.value = "Already dismissed"
                    return false
                }
                DriverLifecycleState.Dismissing -> {
                    _recordingTopicsMessage.value = "Already dismissing"
                    return false
                }
                else -> Unit
            }
            DriverAction.None -> Unit
        }
        lastDriverActionMs = now
        return true
    }
}

private fun ConnectionState.toGatewayConnectionStatus(): GatewayConnectionStatus = when (this) {
    is ConnectionState.Connected -> GatewayConnectionStatus.Connected
    is ConnectionState.Connecting -> GatewayConnectionStatus.Connecting
    is ConnectionState.Error -> GatewayConnectionStatus.Error
    is ConnectionState.Disconnected -> GatewayConnectionStatus.Disconnected
}

private fun JsonObject?.batteryLabel(): String? {
    val battery = this?.get("battery")?.jsonObject ?: return null
    val percentage = battery["percentage"]?.jsonPrimitive?.floatOrNull
    val voltage = battery["voltage"]?.jsonPrimitive?.doubleOrNull
    return when {
        percentage != null -> "Battery ${percentage.toInt()}%"
        voltage != null -> "Battery ${String.format("%.1fV", voltage)}"
        else -> null
    }
}

private fun JsonObject?.batteryPercentLabel(): String {
    val percentage = this?.get("battery")?.jsonObject
        ?.get("percentage")
        ?.jsonPrimitive
        ?.floatOrNull
    return percentage?.let { "${it.toInt()}%" } ?: "—"
}

private fun JsonObject?.safetyLabel(): String? {
    val safety = this?.get("safety")?.jsonObject ?: return null
    return when {
        safety["estop"]?.jsonPrimitive?.booleanOrNull == true -> "E-stop"
        safety["freeze_all"]?.jsonPrimitive?.booleanOrNull == true -> "Frozen"
        safety["comm_ok"]?.jsonPrimitive?.booleanOrNull == false -> "Comm lost"
        else -> "Nominal"
    }
}

private fun JsonObject?.currentLocationId(): String? {
    val maps = this?.get("maps")?.jsonObject ?: return null
    return maps["current_location"]?.jsonPrimitive?.contentOrNull
        ?: maps["current"]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject?.currentMapAssetId(): String? {
    val maps = this?.get("maps")?.jsonObject ?: return null
    return maps["current_map"]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject?.currentLocationLabel(): String = currentLocationId().orEmpty().ifBlank { "—" }

private fun JsonObject?.currentMapAssetLabel(): String = currentMapAssetId().orEmpty().ifBlank { "—" }

private fun JsonObject?.currentPncAuthority(): String {
    return this?.get("run")?.jsonObject
        ?.get("pnc_authority")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.ifBlank { "auto" }
        ?: "auto"
}

private fun JsonObject.toUserOrinResources(): UserOrinResources? {
    return try {
        val cpuObj = this["cpu"]?.jsonObject ?: return null
        val cpuOverall = cpuObj["overall_pct"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: return null
        val gpuLoad = this["gpu"]?.jsonObject?.get("load_pct")?.jsonPrimitive?.doubleOrNull?.toFloat()
        val memoryObj = this["memory"]?.jsonObject ?: return null
        val memoryUsedPct = memoryObj["used_pct"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: return null
        UserOrinResources(
            cpuOverallPct = cpuOverall,
            gpuLoadPct = gpuLoad,
            memoryUsedPct = memoryUsedPct
        )
    } catch (_: Exception) {
        null
    }
}

private fun UserOrinResources.worstLoadLabel(): String {
    val worst = listOfNotNull(cpuOverallPct, gpuLoadPct, memoryUsedPct).maxOrNull() ?: 0f
    return "Load ${worst.toInt()}%"
}

private fun buildServiceHealthLabel(
    gatewayService: UserOrinServiceStatus?,
    robotState: JsonObject?
): String? {
    val topicHealth = robotState?.get("topic_health")?.jsonObject
    val robotPoseOk = topicHealth?.get("robot_pose_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val pncAuthorityReady = robotState?.get("run")?.jsonObject
        ?.get("pnc_fsm_state")
        ?.jsonPrimitive
        ?.intOrNull
        ?.let { it == 1 || it == 4 } ?: false

    val statuses = listOf(
        gatewayService?.running == true,
        robotPoseOk,
        pncAuthorityReady
    )
    val running = statuses.count { it }
    return "Svc $running/${statuses.size}"
}

private fun Float?.toPercentLabel(): String = this?.let { "${it.toInt()}%" } ?: "—"

// ── tier_status JSON helpers ────────────────────────────────────

private fun JsonObject?.tierStatus(tier: String): JsonObject? =
    this?.get("tier_status")?.jsonObject?.get(tier)?.jsonObject

private fun JsonObject.intField(key: String): Int? =
    get(key)?.jsonPrimitive?.intOrNull

private fun JsonObject.boolField(key: String): Boolean? =
    get(key)?.jsonPrimitive?.booleanOrNull
