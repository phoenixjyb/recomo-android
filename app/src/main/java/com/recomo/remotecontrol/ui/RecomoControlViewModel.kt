package com.recomo.remotecontrol.ui

import android.content.Context
import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.camviewer.data.model.OrinRobotIdentity
import com.recomo.remotecontrol.camviewer.data.model.OrinServiceStatus
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import com.recomo.remotecontrol.camviewer.data.model.VideoManagementStatus
import com.recomo.remotecontrol.camviewer.data.repository.OrinServiceRepository
import com.recomo.remotecontrol.camviewer.data.repository.SettingsRepository
import com.recomo.remotecontrol.config.TrackingConfig
import com.recomo.remotecontrol.network.OrinGatewayClient
import com.recomo.remotecontrol.preview.TrajectoryInterpolator
import com.recomo.remotecontrol.preview.TrajectoryPreview
import com.recomo.remotecontrol.settings.StepSettings
import com.recomo.remotecontrol.settings.StepSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong
import java.net.URI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.max

// TTL increased from 250ms to 1000ms to tolerate clock skew between Android and Orin
private const val DRIVE_TTL_MS = 1000
private const val BASE_MAX_ACCEL_M_S2 = 2.0
private const val BASE_MAX_ANG_ACCEL_RAD_S2 = Math.PI
private const val JOG_TTL_MS = 1000
private const val CAMERA_TTL_MS = 2000
private const val RUN_TTL_MS = 2000
private const val CHASSIS_STEP_DURATION_MS = 100L
private const val ARM_STEP_PULSE_MS = 150L
private const val DRIVER_ACTION_COOLDOWN_MS = 2500L
private const val DRIVER_ACTION_TIMEOUT_MS = 10000L
private const val STACK_ACTION_ACK_TIMEOUT_MS = 3000L
private const val NAV_SPEED_UPDATE_DEBOUNCE_MS = 150L
private const val TAG = "RecomoControlVM"
private val ARM_HOMING_Q_DEG = listOf(135.0, 90.0, 0.0)
private val GIMBAL_HOMING_Q_DEG = listOf(0.0, 0.0, 0.0)

data class SafetyStatus(
    val estop: Boolean,
    val freezeAll: Boolean,
    val estopCooldownMs: Long,
    val deadmanOk: Boolean,
    val commOk: Boolean
) {
    val canClearEstop: Boolean get() = estop && estopCooldownMs <= 0
}

// ---------------------------------------------------------------------------
// System Health data classes
// ---------------------------------------------------------------------------

enum class SyncSeverity { OK, WARNING, ERROR, CRITICAL }

data class ClockSyncStatus(
    val offsetMs: Long,          // raw offset: orinTimestamp - tabletTimestamp (constant if clocks are stable)
    val baselineMs: Long,        // learned baseline from gateway (first message offset)
    val baselineSet: Boolean,    // whether gateway has calibrated the baseline
    val driftMs: Long,           // live drift from baseline (this is what matters for safety)
    val severity: SyncSeverity   // based on DRIFT, not absolute offset
)

data class OrinResources(
    val cpuOverallPct: Float,
    val cpuCores: List<Float>,
    val gpuLoadPct: Float?,
    val memoryUsedPct: Float,
    val memoryUsedMb: Int,
    val memoryTotalMb: Int,
    val cpuTempC: Float?,
    val gpuTempC: Float?
)

data class ServiceEntry(
    val id: String,
    val displayName: String,
    val running: Boolean
)

data class GroupHealth(
    val name: String,
    val services: List<ServiceEntry>,
    val allHealthy: Boolean
)

data class ServiceHealthSummary(
    val totalServices: Int,
    val runningServices: Int,
    val groups: Map<String, GroupHealth>
)

data class BaseStatus(
    val x: Double,
    val y: Double,
    val yaw: Double
)

data class CameraPose(
    val x: Double,
    val y: Double,
    val z: Double,
    val qw: Double,
    val qx: Double,
    val qy: Double,
    val qz: Double,
    val ok: Boolean
)

data class SessionSummary(
    val sessionId: String,
    val sessionName: String,
    val robotName: String,
    val frameId: String,
    val category: String,
    val count: Int,
    val target: LibraryTarget
)

data class LibrarySummary(
    val foiSessions: List<SessionSummary>,
    val poiSessions: List<SessionSummary> = emptyList()
)

data class FrameRecord(
    val name: String,
    val sequenceIndex: Int,
    val sessionRole: String,
    val baseX: Double,
    val baseY: Double,
    val baseYaw: Double,
    val armQ: List<Double>,
    val gimbalQ: List<Double>,
    val timestampMs: Long,
    val thumbnail: String? = null,  // Base64-encoded JPEG
    val dwellS: Double? = null,  // Pause at this frame
    val transitionS: Double? = null,  // Time to next frame
    val ease: String? = null  // Easing function
)

data class SessionDetail(
    val sessionId: String,
    val sessionName: String,
    val frames: List<FrameRecord>
)

data class FrameTiming(
    val dwellS: Double? = null,
    val transitionS: Double? = null,
    val ease: String? = null
)

data class PendingFrameTiming(
    val frameIndex: Int,
    val frameName: String
)

enum class RunStatus {
    IDLE,
    HOMING,
    LOADING,
    LOADED,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR
}

enum class RunControlMode {
    SIMPLE_TRACK,
    LIVE_PNC
}

enum class DriverLifecycleState {
    IDLE,
    PREPARING,
    READY,
    DISMISSING
}

enum class OdomMode {
    REAL,
    FAKE,
    OFF,
    CONFLICT,
    UNKNOWN
}

data class RunState(
    val loadedSessionId: String? = null,
    val loadedSessionName: String? = null,
    val currentFoiIndex: Int = 0,
    val totalFois: Int = 0,
    val status: RunStatus = RunStatus.IDLE,
    val homebaseSet: Boolean = false,
    val skipHomebase: Boolean = false,
    val pncFsmState: Int = 0,  // 0=Init,1=Running,2=Exit,3=Demo,4=Finished
    val runControlMode: RunControlMode = RunControlMode.SIMPLE_TRACK
)

data class FixedPositionState(
    val trajectories: List<String> = emptyList(),
    val selectedTrajectory: String? = null,
    val selectedTrajectoryPath: String? = null,
    val status: String = "idle",
    val message: String? = null,
    val motionActive: Boolean = false,
    val paused: Boolean = false,
    val motionComplete: Boolean = false,
    val executorState: Int = 0,
    val stage2ExecutionStatus: String = "idle",
    val recordingActive: Boolean = false,
    val videoReady: Boolean = false,
    val thumbnailReady: Boolean = false,
    val uploadEligible: Boolean = false
)

data class SimpleTrackState(
    val trajectories: List<String> = emptyList(),
    val selectedTrajectory: String? = null,
    val selectedTrajectoryPath: String? = null,
    val status: String = "idle",
    val homeArmQDeg: List<Double> = emptyList()
)

data class ControllerStatus(
    val enabled: Boolean = false,
    val testMode: Boolean = false,
    val connected: Boolean = false,
    val devicePresent: Boolean = false,
    val deviceName: String? = null,
    val deviceId: Int? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val profileName: String? = null,
    val lastEventAtMs: Long = 0L,
    val controlMode: ControlMode = ControlMode.FOCUS,
    val modeSelectActive: Boolean = false,
    val modeCandidate: ControlMode = ControlMode.FOCUS,
    val cineActive: Boolean = false,
    val allDofActive: Boolean = false,
    val tabSelectActive: Boolean = false,
    val focus: ControlFocus = ControlFocus.CHASSIS,
    val armMode: ArmControlMode = ArmControlMode.JOINT_ANGLE
)

@HiltViewModel
class RecomoControlViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient,
    private val settingsRepository: SettingsRepository,
    private val stepSettingsRepository: StepSettingsRepository,
    private val orinServiceRepository: OrinServiceRepository
) : ViewModel() {
    private enum class DriverAction {
        NONE,
        START,
        STOP
    }

    // Public getter for gatewayClient (needed by UI components like EEPositionControlPanel)
    fun getGatewayClient(): OrinGatewayClient = gatewayClient

    private val seqId = AtomicLong(0)
    private val gimbalTarget = doubleArrayOf(0.0, 0.0, 0.0)
    private var lastGimbalCmdMs: Long = 0
    // armJointTarget order: [joint4/elbow, joint5/pitch, joint6/yaw]
    private val armJointTarget = doubleArrayOf(0.0, 0.0, 0.0)
    private var lastArmVelocityUpdateMs = 0L
    private var lastArmVelocityClockIsState = false
    private var lastRobotStateTimestampMs = 0L
    private var lastArmCmdMs = 0L
    // Limits from recomoProto1-190 URDF (2026-01-27):
    // joint4_elbow_pitch: ±135° (±2.3562 rad)
    // joint5_arm_pitch: ±135° (±2.3562 rad)
    // joint6_arm_yaw: ±180° (±3.1416 rad)
    private val armJointMinRad = doubleArrayOf(-2.3562, -2.3562, -3.1416)
    private val armJointMaxRad = doubleArrayOf(2.3562, 2.3562, 3.1416)
    private var chassisStepJob: kotlinx.coroutines.Job? = null
    private var chassisMoveJob: Job? = null
    private var armMoveJob: Job? = null
    private var gimbalMoveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var navSpeedUpdateJob: Job? = null
    private var activeFoiSessionId: String? = null
    private var activeFoiSessionName: String? = null
    private var activePoiSessionId: String? = null
    private val draftFois = mutableListOf<JsonObject>()
    private val draftPois = mutableListOf<JsonObject>()
    private var draftHomebase: JsonObject? = null
    private val gatewayServiceId = "recomo_gateway_ws"
    private val videoManagementServiceId = "video_management"
    private var lastGatewayUrl: String? = null
    private var currentRobotProfile: RobotProfile = RobotProfile.NONE
    private val _robotProfile = MutableStateFlow(currentRobotProfile)
    val robotProfile: StateFlow<RobotProfile> = _robotProfile.asStateFlow()
    private val _sampleSessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    private val sampleSessionDetails = mutableMapOf<String, SessionDetail>()
    private val _localSessionDetail = MutableStateFlow<SessionDetail?>(null)
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private var samplesLoaded = false
    
    // Thumbnail provider callback (set from UI layer)
    var thumbnailProvider: (() -> String?)? = null

    // Expose current session info for UI
    private val _currentSessionName = MutableStateFlow<String?>(null)
    val currentSessionName: StateFlow<String?> = _currentSessionName.asStateFlow()
    private val _currentFrameCount = MutableStateFlow(0)
    val currentFrameCount: StateFlow<Int> = _currentFrameCount.asStateFlow()
    private val _hasHomebase = MutableStateFlow(false)
    val hasHomebase: StateFlow<Boolean> = _hasHomebase.asStateFlow()
    
    // Frame timing dialog state
    private val _pendingFrameTiming = MutableStateFlow<PendingFrameTiming?>(null)
    val pendingFrameTiming: StateFlow<PendingFrameTiming?> = _pendingFrameTiming.asStateFlow()
    
    // Expose draft frames for UI display
    private val _draftFramesList = MutableStateFlow<List<JsonObject>>(emptyList())
    val draftFramesList: StateFlow<List<JsonObject>> = _draftFramesList.asStateFlow()
    
    // Map selection for FOI sessions
    private val _availableMaps = MutableStateFlow<List<String>>(emptyList())
    val availableMaps: StateFlow<List<String>> = _availableMaps.asStateFlow()
    
    private val _selectedMap = MutableStateFlow<String?>(null)
    val selectedMap: StateFlow<String?> = _selectedMap.asStateFlow()

    private val _selectedMapAsset = MutableStateFlow<String?>(null)
    val selectedMapAsset: StateFlow<String?> = _selectedMapAsset.asStateFlow()

    private val _mapAssets = MutableStateFlow<List<String>>(emptyList())
    val mapAssets: StateFlow<List<String>> = _mapAssets.asStateFlow()

    private val _mapAssetsByLocation = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val mapAssetsByLocation: StateFlow<Map<String, List<String>>> = _mapAssetsByLocation.asStateFlow()
    
    private val _currentFoiMap = MutableStateFlow<String?>(null)
    val currentFoiMap: StateFlow<String?> = _currentFoiMap.asStateFlow()
    
    // Robot pose status for localization
    private val _robotPoseOk = MutableStateFlow(false)
    val robotPoseOk: StateFlow<Boolean> = _robotPoseOk.asStateFlow()
    
    // Placeholder/suggested names for UI
    val suggestedSessionName: StateFlow<String> = _currentSessionName.map { current ->
        current ?: generateSessionName()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, generateSessionName())
    
    val suggestedFrameName: StateFlow<String> = _currentFrameCount.map { count ->
        "frame_${count + 1}"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "frame_1")

    private val _gatewayUrl = MutableStateFlow("")
    val gatewayUrl: StateFlow<String> = _gatewayUrl.asStateFlow()
    private val _trackingTargetWidth = MutableStateFlow(TrackingConfig.TARGET_WIDTH)
    val trackingTargetWidth: StateFlow<Int> = _trackingTargetWidth.asStateFlow()
    private val _trackingTargetHeight = MutableStateFlow(TrackingConfig.TARGET_HEIGHT)
    val trackingTargetHeight: StateFlow<Int> = _trackingTargetHeight.asStateFlow()
    val stepSettings: StateFlow<StepSettings> = stepSettingsRepository.stepSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, StepSettings())

    private val _stepMode = MutableStateFlow(StepMode.NORMAL)
    val stepMode: StateFlow<StepMode> = _stepMode.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _settingsInitialGroup = MutableStateFlow("ROBOT")
    val settingsInitialGroup: StateFlow<String> = _settingsInitialGroup.asStateFlow()

    fun navigateToSettingsGroup(group: String) {
        _settingsInitialGroup.value = group
        _selectedTab.value = 5  // TAB_SETTINGS
    }

    private val _controlFocus = MutableStateFlow(ControlFocus.CHASSIS)
    val controlFocus: StateFlow<ControlFocus> = _controlFocus.asStateFlow()

    private val _armControlMode = MutableStateFlow(ArmControlMode.JOINT_ANGLE)
    val armControlMode: StateFlow<ArmControlMode> = _armControlMode.asStateFlow()

    private val _controlViewMode = MutableStateFlow(ControlViewMode.WORLD_VIEW)
    val controlViewMode: StateFlow<ControlViewMode> = _controlViewMode.asStateFlow()

    private val _controllerStatus = MutableStateFlow(ControllerStatus())
    val controllerStatus: StateFlow<ControllerStatus> = _controllerStatus.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = gatewayClient.connectionState
    val robotState: StateFlow<JsonObject?> = gatewayClient.robotState
    val lastStateAtMs: StateFlow<Long> = gatewayClient.lastStateAtMs
    val stateHz: StateFlow<Double> = gatewayClient.stateHz
    val armAngles: StateFlow<List<Double>> = robotState.map { state ->
        armPositionsFromState(state)?.toList() ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val gimbalAngles: StateFlow<List<Double>> = robotState.map { state ->
        state?.get("gimbal")?.jsonObject?.get("q")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val baseYawRad: StateFlow<Double?> = robotState.map { state ->
        state?.get("base")?.jsonObject?.get("yaw")?.jsonPrimitive?.doubleOrNull
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _gatewayServiceStatus = MutableStateFlow<OrinServiceStatus?>(null)
    val gatewayServiceStatus: StateFlow<OrinServiceStatus?> = _gatewayServiceStatus.asStateFlow()
    private val _videoManagementServiceStatus = MutableStateFlow<OrinServiceStatus?>(null)
    val videoManagementServiceStatus: StateFlow<OrinServiceStatus?> = _videoManagementServiceStatus.asStateFlow()
    private val _gatewayServiceBusy = MutableStateFlow(false)
    val gatewayServiceBusy: StateFlow<Boolean> = _gatewayServiceBusy.asStateFlow()
    private val _gatewayServiceError = MutableStateFlow<String?>(null)
    val gatewayServiceError: StateFlow<String?> = _gatewayServiceError.asStateFlow()
    private val _gatewayApiStatus = MutableStateFlow("Unknown")
    val gatewayApiStatus: StateFlow<String> = _gatewayApiStatus.asStateFlow()
    private val _gatewayApiLatencyMs = MutableStateFlow<Long?>(null)
    val gatewayApiLatencyMs: StateFlow<Long?> = _gatewayApiLatencyMs.asStateFlow()
    private val _robotIdentity = MutableStateFlow<OrinRobotIdentity?>(null)
    val robotIdentity: StateFlow<OrinRobotIdentity?> = _robotIdentity.asStateFlow()
    private val _robotIdentityBusy = MutableStateFlow(false)
    val robotIdentityBusy: StateFlow<Boolean> = _robotIdentityBusy.asStateFlow()
    private val _robotIdentityError = MutableStateFlow<String?>(null)
    val robotIdentityError: StateFlow<String?> = _robotIdentityError.asStateFlow()
    private val _videoManagementStatus = MutableStateFlow<VideoManagementStatus?>(null)
    val videoManagementStatus: StateFlow<VideoManagementStatus?> = _videoManagementStatus.asStateFlow()
    private val _videoManagementBusy = MutableStateFlow(false)
    val videoManagementBusy: StateFlow<Boolean> = _videoManagementBusy.asStateFlow()
    private val _videoManagementError = MutableStateFlow<String?>(null)
    val videoManagementError: StateFlow<String?> = _videoManagementError.asStateFlow()
    private val _videoManagementMessage = MutableStateFlow<String?>(null)
    val videoManagementMessage: StateFlow<String?> = _videoManagementMessage.asStateFlow()
    private val _currentUploadPreset = MutableStateFlow("custom")
    val currentUploadPreset: StateFlow<String> = _currentUploadPreset.asStateFlow()

    // System health StateFlows
    private val _clockSyncStatus = MutableStateFlow<ClockSyncStatus?>(null)
    val clockSyncStatus: StateFlow<ClockSyncStatus?> = _clockSyncStatus.asStateFlow()

    private val _orinResources = MutableStateFlow<OrinResources?>(null)
    val orinResources: StateFlow<OrinResources?> = _orinResources.asStateFlow()

    private val _serviceHealthSummary = MutableStateFlow<ServiceHealthSummary?>(null)
    val serviceHealthSummary: StateFlow<ServiceHealthSummary?> = _serviceHealthSummary.asStateFlow()

    private val _baseAccelInput = MutableStateFlow("0.8")
    val baseAccelInput: StateFlow<String> = _baseAccelInput.asStateFlow()
    private val _baseAngAccelInput = MutableStateFlow("2.5")
    val baseAngAccelInput: StateFlow<String> = _baseAngAccelInput.asStateFlow()

    private val _odomMode = MutableStateFlow(OdomMode.UNKNOWN)
    val odomMode: StateFlow<OdomMode> = _odomMode.asStateFlow()
    private val _odomSwitchBusy = MutableStateFlow(false)
    val odomSwitchBusy: StateFlow<Boolean> = _odomSwitchBusy.asStateFlow()
    private val _odomSwitchError = MutableStateFlow<String?>(null)
    val odomSwitchError: StateFlow<String?> = _odomSwitchError.asStateFlow()
    private val _odomSwitchMessage = MutableStateFlow<String?>(null)
    val odomSwitchMessage: StateFlow<String?> = _odomSwitchMessage.asStateFlow()

    // Recording topics state
    private val _recordingTopicsBusy = MutableStateFlow(false)
    val recordingTopicsBusy: StateFlow<Boolean> = _recordingTopicsBusy.asStateFlow()
    private val _recordingTopicsError = MutableStateFlow<String?>(null)
    val recordingTopicsError: StateFlow<String?> = _recordingTopicsError.asStateFlow()
    private val _recordingTopicsMessage = MutableStateFlow<String?>(null)
    val recordingTopicsMessage: StateFlow<String?> = _recordingTopicsMessage.asStateFlow()

    private val _stackActionBusy = MutableStateFlow(false)
    val stackActionBusy: StateFlow<Boolean> = _stackActionBusy.asStateFlow()
    private val _stackActionError = MutableStateFlow<String?>(null)
    val stackActionError: StateFlow<String?> = _stackActionError.asStateFlow()
    private val _stackActionMessage = MutableStateFlow<String?>(null)
    val stackActionMessage: StateFlow<String?> = _stackActionMessage.asStateFlow()
    
    // ── IMU Teleop ────────────────────────────────────────────────────────
    val imuTeleopManager = com.recomo.remotecontrol.imu.ImuTeleopManager()
    private var imuCommandCollectorJob: Job? = null

    /** Tighter TTL for IMU gimbal commands — 120ms (3× the 40ms send interval). */
    private val IMU_GIMBAL_TTL_MS = 120

    /** Call from Activity.onResume to register the IMU sensor. */
    fun initImuTeleop(context: android.content.Context) {
        if (imuTeleopManager.start(context)) {
            // Start collecting IMU commands and dispatching to gateway
            if (imuCommandCollectorJob?.isActive != true) {
                imuCommandCollectorJob = viewModelScope.launch {
                    imuTeleopManager.commandFlow.collect { cmd ->
                        // Guard: only send EE commands if IK controller is running (C3 fix)
                        val ikRunning = _eeIkControllerRunning.value
                        val hasEeCmd = cmd.eeDx != 0.0 || cmd.eeDy != 0.0 || cmd.eeDz != 0.0

                        // Send EE position command (only when IK is active)
                        if (hasEeCmd && ikRunning) {
                            val eeMsg = kotlinx.serialization.json.buildJsonObject {
                                put("type", "ee_position_cmd")
                                put("dx", cmd.eeDx)
                                put("dy", cmd.eeDy)
                                put("dz", cmd.eeDz)
                                put("frame", cmd.eeFrame)
                            }
                            sendControl(eeMsg)
                        }
                        // Send gimbal velocity command (works independently of IK)
                        if (cmd.gimbalRollVel != 0.0 || cmd.gimbalPitchVel != 0.0 || cmd.gimbalYawVel != 0.0) {
                            val gimbalMsg = kotlinx.serialization.json.buildJsonObject {
                                put("type", "JogCmd")
                                put("seq_id", seqId.getAndIncrement())
                                put("timestamp_ms", System.currentTimeMillis())
                                put("ttl_ms", IMU_GIMBAL_TTL_MS)
                                put("deadman", true)
                                put("target", "gimbal")
                                put("mode", "velocity")
                                put("values", kotlinx.serialization.json.buildJsonArray {
                                    add(cmd.gimbalRollVel)
                                    add(cmd.gimbalPitchVel)
                                    add(cmd.gimbalYawVel)
                                })
                            }
                            sendControl(gimbalMsg)
                        }
                    }
                }
            }
        }
    }

    /** Call from Activity.onPause to unregister the IMU sensor. */
    fun stopImuTeleop() {
        imuCommandCollectorJob?.cancel()
        imuCommandCollectorJob = null
        imuTeleopManager.stop()
    }

    // EE IK Controller status
    private val _eeIkControllerRunning = MutableStateFlow(false)
    val eeIkControllerRunning: StateFlow<Boolean> = _eeIkControllerRunning.asStateFlow()
    private val _eeIkControllerBusy = MutableStateFlow(false)
    val eeIkControllerBusy: StateFlow<Boolean> = _eeIkControllerBusy.asStateFlow()
    private val _eeIkDryRun = MutableStateFlow(true)
    val eeIkDryRun: StateFlow<Boolean> = _eeIkDryRun.asStateFlow()
    private val _driverLifecycle = MutableStateFlow(DriverLifecycleState.IDLE)
    val driverLifecycle: StateFlow<DriverLifecycleState> = _driverLifecycle.asStateFlow()
    private var pendingDriverAction = DriverAction.NONE
    private var driverActionTimeoutJob: Job? = null
    private var lastDriverActionMs = 0L
    private var lastMapDetailSignature = ""
    private var pendingStackAction: String? = null
    private var stackActionAckTimeoutJob: Job? = null

    private val _runState = MutableStateFlow(RunState())
    val runState: StateFlow<RunState> = _runState.asStateFlow()
    private val _fixedPositionState = MutableStateFlow(FixedPositionState())
    val fixedPositionState: StateFlow<FixedPositionState> = _fixedPositionState.asStateFlow()

    private val _simpleTrackState = MutableStateFlow(SimpleTrackState())
    val simpleTrackState: StateFlow<SimpleTrackState> = _simpleTrackState.asStateFlow()

    private val _operationMode = MutableStateFlow(OperationMode.MANUAL)
    val operationMode: StateFlow<OperationMode> = _operationMode.asStateFlow()

    private val _poiNavActive = MutableStateFlow(false)
    val poiNavActive: StateFlow<Boolean> = _poiNavActive.asStateFlow()
    private val _poiNamesBySession = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val poiNamesBySession: StateFlow<Map<String, List<String>>> = _poiNamesBySession.asStateFlow()

    val safetyStatus: StateFlow<SafetyStatus> = robotState.map { state ->
        val safety = state?.get("safety")?.jsonObject
        SafetyStatus(
            estop = safety?.get("estop")?.jsonPrimitive?.booleanOrNull ?: false,
            freezeAll = safety?.get("freeze_all")?.jsonPrimitive?.booleanOrNull ?: false,
            estopCooldownMs = safety?.get("estop_cooldown_remaining_ms")?.jsonPrimitive?.longOrNull ?: 0L,
            deadmanOk = safety?.get("deadman_ok")?.jsonPrimitive?.booleanOrNull ?: false,
            commOk = safety?.get("comm_ok")?.jsonPrimitive?.booleanOrNull ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SafetyStatus(false, false, 0L, false, false))
    
    // Config toggles from gateway (mirrors ConfigCmd state)
    val cameraCanRecordEnabled: StateFlow<Boolean> = robotState.map { state ->
        state?.get("config")?.jsonObject
            ?.get("camera_can_record_enabled")?.jsonPrimitive?.booleanOrNull ?: true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // EE IK Controller status from gateway — pure transform, no side effects (A-C4 fix)
    private val eeIkStatusFromGateway: StateFlow<Boolean> = robotState.map { state ->
        val ikObj = state?.get("ee_ik_controller")?.jsonObject
        ikObj?.get("running")?.jsonPrimitive?.booleanOrNull ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val baseStatus: StateFlow<BaseStatus> = robotState.map { state ->
        val base = state?.get("base")?.jsonObject
        BaseStatus(
            x = base?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            y = base?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            yaw = base?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BaseStatus(0.0, 0.0, 0.0))

    val cameraPose: StateFlow<CameraPose> = robotState.map { state ->
        val camera = state?.get("camera")?.jsonObject
        val position = camera?.get("position")?.jsonObject
        val quat = camera?.get("orientation_quat")?.jsonObject
        CameraPose(
            x = position?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            y = position?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            z = position?.get("z")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            qw = quat?.get("w")?.jsonPrimitive?.doubleOrNull ?: 1.0,
            qx = quat?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            qy = quat?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            qz = quat?.get("z")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            ok = camera?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        CameraPose(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, false)
    )

    val librarySummary: StateFlow<LibrarySummary> = combine(robotState, _sampleSessions) { state, samples ->
        val library = state?.get("library")?.jsonObject
        val remoteSessions = library?.get("foi_sessions")?.jsonArray?.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            SessionSummary(
                sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull ?: "",
                sessionName = obj["session_name"]?.jsonPrimitive?.contentOrNull ?: "",
                robotName = obj["robot_name"]?.jsonPrimitive?.contentOrNull ?: "",
                frameId = obj["frame_id"]?.jsonPrimitive?.contentOrNull ?: "",
                category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "",
                count = obj["foi_count"]?.jsonPrimitive?.intOrNull ?: 0,
                target = LibraryTarget.FOI_SESSION
            )
        } ?: emptyList()
        val poiSessions = library?.get("poi_sessions")?.jsonArray?.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val sid = obj["session_id"]?.jsonPrimitive?.contentOrNull ?: ""
            SessionSummary(
                sessionId = sid,
                sessionName = obj["session_name"]?.jsonPrimitive?.contentOrNull ?: sid,
                robotName = obj["robot_name"]?.jsonPrimitive?.contentOrNull ?: "",
                frameId = obj["frame_id"]?.jsonPrimitive?.contentOrNull ?: "",
                category = "",
                count = obj["poi_count"]?.jsonPrimitive?.intOrNull ?: 0,
                target = LibraryTarget.POI_SESSION
            )
        } ?: emptyList()
        val sessions = if (remoteSessions.isNotEmpty()) remoteSessions else samples
        LibrarySummary(foiSessions = sessions, poiSessions = poiSessions)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySummary(emptyList()))

    val sessionDetail: StateFlow<SessionDetail?> = combine(robotState, _localSessionDetail) { state, local ->
        val detail = state?.get("library_detail")?.jsonObject
        val target = detail?.get("target")?.jsonPrimitive?.contentOrNull
        if (target != null && target != "foi_session") return@combine local
        val session = detail?.get("session")?.jsonObject
        parseSessionDetail(session) ?: local
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val trajectoryPreview: StateFlow<TrajectoryPreview?> = sessionDetail.map { session ->
        session?.let { TrajectoryInterpolator.buildPreview(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { normalizeGatewayUrl(it.signalingUrl) }
                .distinctUntilChanged()
                .collect { url ->
                    _gatewayUrl.value = url
                    val previous = lastGatewayUrl
                    lastGatewayUrl = url
                    if (url.isBlank()) {
                        gatewayClient.disconnect()
                        return@collect
                    }
                    if (previous != null && previous != url) {
                        gatewayClient.disconnect()
                        delay(200)
                        gatewayClient.connect(url)
                        return@collect
                    }
                    if (connectionState.value !is ConnectionState.Connected
                        && connectionState.value !is ConnectionState.Connecting) {
                        gatewayClient.connect(url)
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.robotProfile }
                .distinctUntilChanged()
                .collect { profile ->
                    currentRobotProfile = profile
                    _robotProfile.value = profile
                }
        }
        // Sync EE IK controller status from gateway to internal state
        viewModelScope.launch {
            // Collect the full robotState to extract both running + dry_run in one place (A-C4 fix)
            robotState.collect { state ->
                val ikObj = state?.get("ee_ik_controller")?.jsonObject
                val running = ikObj?.get("running")?.jsonPrimitive?.booleanOrNull ?: false
                val dryRun = ikObj?.get("dry_run")?.jsonPrimitive?.booleanOrNull ?: true
                if (_eeIkControllerRunning.value != running) {
                    Log.d(TAG, "EE IK status from gateway: running=$running, dry_run=$dryRun")
                }
                _eeIkControllerRunning.value = running
                _eeIkDryRun.value = dryRun
            }
        }
        // Auto-refresh Orin service status + video management on startup
        viewModelScope.launch {
            delay(2000)
            refreshGatewayService()
            refreshVideoManagementStatus()
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { settings -> settings.trackingTargetWidth to settings.trackingTargetHeight }
                .distinctUntilChanged()
                .collect { (width, height) ->
                    _trackingTargetWidth.value = width
                    _trackingTargetHeight.value = height
                }
        }
        viewModelScope.launch {
            robotState.collect { state ->
                val run = state?.get("run")?.jsonObject ?: return@collect
                val status = run["status"]?.jsonPrimitive?.contentOrNull
                val loadedId = run["session_id"]?.jsonPrimitive?.contentOrNull
                val loadedName = run["session_name"]?.jsonPrimitive?.contentOrNull
                val currentFoi = run["current_foi"]?.jsonPrimitive?.intOrNull
                val totalFois = run["total_fois"]?.jsonPrimitive?.intOrNull
                val homebaseSet = run["homebase_set"]?.jsonPrimitive?.booleanOrNull
                val skipHomebase = run["skip_homebase"]?.jsonPrimitive?.booleanOrNull
                val navActive = run["nav_active"]?.jsonPrimitive?.booleanOrNull
                val nextStatus = runStatusFromWire(status)
                val pncFsmState = run["pnc_fsm_state"]?.jsonPrimitive?.intOrNull
                val runControlMode = run["run_control_mode"]?.jsonPrimitive?.contentOrNull
                if (nextStatus != null || loadedId != null || loadedName != null ||
                    currentFoi != null || totalFois != null || skipHomebase != null ||
                    homebaseSet != null || pncFsmState != null || runControlMode != null
                ) {
                    _runState.update { prev ->
                        val resolvedNavActive = when {
                            pncFsmState == 4 -> false
                            navActive != null -> navActive
                            else -> _poiNavActive.value
                        }
                        // Some PnC stacks briefly publish FSM=4 then return to 1.
                        // Preserve Arrived until the next explicit GO command.
                        val resolvedFsm = when {
                            pncFsmState == null -> prev.pncFsmState
                            !resolvedNavActive && pncFsmState == 1 && prev.pncFsmState == 4 -> 4
                            else -> pncFsmState
                        }
                        _poiNavActive.value = resolvedNavActive
                        prev.copy(
                            status = nextStatus ?: prev.status,
                            loadedSessionId = loadedId ?: prev.loadedSessionId,
                            loadedSessionName = loadedName ?: prev.loadedSessionName,
                            currentFoiIndex = currentFoi ?: prev.currentFoiIndex,
                            totalFois = totalFois ?: prev.totalFois,
                            homebaseSet = homebaseSet ?: prev.homebaseSet,
                            skipHomebase = skipHomebase ?: prev.skipHomebase,
                            pncFsmState = resolvedFsm,
                            runControlMode = runControlModeFromWire(runControlMode) ?: prev.runControlMode
                        )
                    }
                }

                state.get("fixed_position")?.jsonObject?.let { fixed ->
                    val trajectories = fixed["trajectories"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: _fixedPositionState.value.trajectories
                    val selectedTrajectory = fixed["selected_trajectory"]?.jsonPrimitive?.contentOrNull
                    val selectedTrajectoryPath = fixed["selected_trajectory_path"]?.jsonPrimitive?.contentOrNull
                    val status = fixed["status"]?.jsonPrimitive?.contentOrNull
                    val message = fixed["message"]?.jsonPrimitive?.contentOrNull
                    val motionActive = fixed["motion_active"]?.jsonPrimitive?.booleanOrNull
                    val paused = fixed["paused"]?.jsonPrimitive?.booleanOrNull
                    val motionComplete = fixed["motion_complete"]?.jsonPrimitive?.booleanOrNull
                    val executorState = fixed["executor_state"]?.jsonPrimitive?.intOrNull
                    val stage2ExecutionStatus = fixed["stage2_execution_status"]?.jsonPrimitive?.contentOrNull
                    val recordingActive = fixed["recording_active"]?.jsonPrimitive?.booleanOrNull
                    val videoReady = fixed["video_ready"]?.jsonPrimitive?.booleanOrNull
                    val thumbnailReady = fixed["thumbnail_ready"]?.jsonPrimitive?.booleanOrNull
                    val uploadEligible = fixed["upload_eligible"]?.jsonPrimitive?.booleanOrNull

                    _fixedPositionState.update { prev ->
                        prev.copy(
                            trajectories = trajectories,
                            selectedTrajectory = selectedTrajectory ?: prev.selectedTrajectory,
                            selectedTrajectoryPath = selectedTrajectoryPath ?: prev.selectedTrajectoryPath,
                            status = status ?: prev.status,
                            message = message ?: prev.message,
                            motionActive = motionActive ?: prev.motionActive,
                            paused = paused ?: prev.paused,
                            motionComplete = motionComplete ?: prev.motionComplete,
                            executorState = executorState ?: prev.executorState,
                            stage2ExecutionStatus = stage2ExecutionStatus ?: prev.stage2ExecutionStatus,
                            recordingActive = recordingActive ?: prev.recordingActive,
                            videoReady = videoReady ?: prev.videoReady,
                            thumbnailReady = thumbnailReady ?: prev.thumbnailReady,
                            uploadEligible = uploadEligible ?: prev.uploadEligible
                        )
                    }

                    if (_runState.value.runControlMode == RunControlMode.LIVE_PNC) {
                        val mappedStatus = when (status?.lowercase()) {
                            "running" -> RunStatus.RUNNING
                            "paused" -> RunStatus.PAUSED
                            "initing", "ready", "homing" -> RunStatus.LOADING
                            "end", "completed", "idle", "stopped" -> RunStatus.IDLE
                            "error" -> RunStatus.ERROR
                            else -> null
                        }
                        _runState.update { prev ->
                            prev.copy(
                                loadedSessionId = selectedTrajectory ?: prev.loadedSessionId,
                                loadedSessionName = selectedTrajectory ?: prev.loadedSessionName,
                                status = mappedStatus ?: prev.status
                            )
                        }
                    }
                }

                state.get("simple_track")?.jsonObject?.let { st ->
                    val trajectories = st["trajectories"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: _simpleTrackState.value.trajectories
                    val selectedTrajectory = st["selected_trajectory"]?.jsonPrimitive?.contentOrNull
                    val selectedTrajectoryPath = st["selected_trajectory_path"]?.jsonPrimitive?.contentOrNull
                    val status = st["status"]?.jsonPrimitive?.contentOrNull
                    val homeArmQDeg = st["home_arm_q_deg"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
                        ?: _simpleTrackState.value.homeArmQDeg

                    _simpleTrackState.update { prev ->
                        prev.copy(
                            trajectories = trajectories,
                            selectedTrajectory = selectedTrajectory ?: prev.selectedTrajectory,
                            selectedTrajectoryPath = selectedTrajectoryPath ?: prev.selectedTrajectoryPath,
                            status = status ?: prev.status,
                            homeArmQDeg = homeArmQDeg
                        )
                    }

                    // Sync run state when in simple_track mode
                    if (_runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
                        val mappedStatus = when (status?.lowercase()) {
                            "running" -> RunStatus.RUNNING
                            "paused" -> RunStatus.PAUSED
                            "homing" -> RunStatus.HOMING
                            "loaded" -> RunStatus.LOADED
                            "completed", "idle" -> RunStatus.IDLE
                            "error" -> RunStatus.ERROR
                            else -> null
                        }
                        if (mappedStatus != null) {
                            _runState.update { prev ->
                                prev.copy(
                                    loadedSessionId = selectedTrajectory ?: prev.loadedSessionId,
                                    loadedSessionName = selectedTrajectory ?: prev.loadedSessionName,
                                    status = mappedStatus
                                )
                            }
                        }
                    }
                }

                state.get("timestamp_ms")?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { ts ->
                    lastRobotStateTimestampMs = ts
                }

                val gimbal = state.get("gimbal")?.jsonObject
                val q = gimbal?.get("q")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }
                if (q != null && q.size >= 3) {
                    val now = System.currentTimeMillis()
                    if (gimbalMoveJob == null && now - lastGimbalCmdMs > 400) {
                        gimbalTarget[0] = q[0]
                        gimbalTarget[1] = q[1]
                        gimbalTarget[2] = q[2]
                    }
                }

                val armPos = armPositionsFromState(state)
                if (armPos != null) {
                    val now = System.currentTimeMillis()
                    if (armMoveJob == null && now - lastArmCmdMs > 400) {
                        armJointTarget[0] = armPos[0]
                        armJointTarget[1] = armPos[1]
                        armJointTarget[2] = armPos[2]
                        if (lastRobotStateTimestampMs > 0L) {
                            lastArmVelocityUpdateMs = lastRobotStateTimestampMs
                            lastArmVelocityClockIsState = true
                        } else {
                            lastArmVelocityUpdateMs = now
                            lastArmVelocityClockIsState = false
                        }
                    }
                }
                
                // Parse map list from gateway
                state.get("maps")?.jsonObject?.let { maps ->
                    val locations = maps["locations"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: maps["available"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList()
                    if (locations != _availableMaps.value) {
                        _availableMaps.value = locations
                        Log.d("RecomoControl", "Available locations: $locations")
                    }

                    val assetsByLocation = maps["map_assets_by_location"]?.jsonObject?.mapValues { (_, value) ->
                        (value as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    } ?: emptyMap()
                    if (assetsByLocation != _mapAssetsByLocation.value) {
                        _mapAssetsByLocation.value = assetsByLocation
                    }

                    val currentLocation = maps["current_location"]?.jsonPrimitive?.contentOrNull
                        ?: maps["current"]?.jsonPrimitive?.contentOrNull
                    if (!currentLocation.isNullOrBlank() && currentLocation != _selectedMap.value) {
                        _selectedMap.value = currentLocation
                        _currentFoiMap.value = currentLocation
                        Log.d("RecomoControl", "Current location: $currentLocation")
                    }

                    val currentMapAsset = maps["current_map"]?.jsonPrimitive?.contentOrNull
                    if (!currentMapAsset.isNullOrBlank() && currentMapAsset != _selectedMapAsset.value) {
                        _selectedMapAsset.value = currentMapAsset
                    }

                    val directAssets = maps["map_assets"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    val fallbackAssets = currentLocation?.let { assetsByLocation[it] } ?: emptyList()
                    val resolvedAssets = directAssets ?: fallbackAssets
                    if (resolvedAssets != _mapAssets.value) {
                        _mapAssets.value = resolvedAssets
                    }
                }

                val detail = state.get("map_detail")?.jsonObject
                    ?: state.get("maps")?.jsonObject?.get("detail")?.jsonObject
                detail?.let { mapDetail ->
                    val action = mapDetail["action"]?.jsonPrimitive?.contentOrNull ?: ""
                    val signature = mapDetail.toString()
                    val shouldProcess = signature != lastMapDetailSignature || pendingStackAction == action
                    if (shouldProcess) {
                        lastMapDetailSignature = signature
                        val success = mapDetail["success"]?.jsonPrimitive?.booleanOrNull
                        when (action) {
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
                                            mapDetail["error"]?.jsonPrimitive?.contentOrNull ?: "$action failed"
                                    }
                                }
                            }
                            "start_all" -> {
                                if (success == false) {
                                    _recordingTopicsError.value =
                                        mapDetail["error"]?.jsonPrimitive?.contentOrNull ?: "Prepare robot failed"
                                }
                            }
                            "stop_all" -> {
                                if (success == false) {
                                    _recordingTopicsError.value =
                                        mapDetail["error"]?.jsonPrimitive?.contentOrNull ?: "Dismiss robot failed"
                                }
                            }
                        }
                    }
                }
                
                // Parse robot pose status
                (state.get("topic_health")?.jsonObject?.get("robot_pose_ok")
                    ?: state.get("robot_pose_ok"))?.jsonPrimitive?.booleanOrNull?.let { ok ->
                    _robotPoseOk.value = ok
                }
            }
        }
        viewModelScope.launch {
            robotState.collect { state ->
                updateDriverLifecycleFromState(state)
            }
        }
        viewModelScope.launch {
            robotState.collect { state ->
                val detail = state?.get("library_detail")?.jsonObject ?: return@collect
                val target = detail["target"]?.jsonPrimitive?.contentOrNull ?: return@collect
                if (target != "poi_session") return@collect
                val session = detail["session"]?.jsonObject ?: return@collect
                val sessionId = session["session_id"]?.jsonPrimitive?.contentOrNull ?: return@collect
                val pois = session["pois"]?.jsonArray ?: JsonArray(emptyList())
                val names = pois.mapIndexed { idx, item ->
                    val parsed = runCatching {
                        item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    parsed?.takeIf { it.isNotBlank() } ?: "POI ${idx + 1}"
                }
                _poiNamesBySession.update { prev ->
                    if (prev[sessionId] == names) prev else prev + (sessionId to names)
                }
            }
        }
        viewModelScope.launch {
            sessionDetail.collect { detail ->
                if (detail == null) return@collect
                _runState.update { prev ->
                    val total = detail.frames.size
                    val clampedIndex = prev.currentFoiIndex.coerceIn(0, max(0, total - 1))
                    val hasHomebase = detail.frames.any { it.sessionRole == "homebase" }
                    val nextStatus = if (prev.status == RunStatus.LOADING || prev.status == RunStatus.IDLE) {
                        RunStatus.LOADED
                    } else {
                        prev.status
                    }
                    prev.copy(
                        loadedSessionId = detail.sessionId,
                        loadedSessionName = detail.sessionName,
                        totalFois = total,
                        currentFoiIndex = clampedIndex,
                        homebaseSet = hasHomebase,
                        status = nextStatus
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.settings.map { it.viewOnly },
                connectionState
            ) { viewOnly, state -> Pair(viewOnly, state) }
                .collect { (viewOnly, state) ->
                    val connected = state is ConnectionState.Connected
                    if (connected && !viewOnly) startHeartbeat()
                    else stopHeartbeat()
                }
        }
        // Clock sync: baseline + drift strategy
        // The gateway learns a clock offset baseline on first message per session.
        // We display DRIFT from that baseline (actionable) rather than absolute offset (misleading).
        viewModelScope.launch {
            robotState.collect { state ->
                val orinTs = state?.get("timestamp_ms")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                if (orinTs != null) {
                    val tabletTs = System.currentTimeMillis()
                    val rawOffset = orinTs - tabletTs  // Orin clock - tablet clock

                    // Read gateway-reported clock baseline (if available)
                    val clockSync = state?.get("clock_sync")
                    val baselineSet = clockSync?.jsonObject?.get("baseline_set")
                        ?.jsonPrimitive?.booleanOrNull ?: false
                    val baselineOffset = clockSync?.jsonObject?.get("baseline_offset_ms")
                        ?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

                    // Drift = how much the offset has changed from the learned baseline
                    // Gateway baseline is (client_ts - server_ts), i.e. tablet_clock - orin_clock
                    // Our rawOffset is orin_clock - tablet_clock = -baseline
                    // So drift = rawOffset - (-baseline) = rawOffset + baseline
                    val drift = if (baselineSet) rawOffset + baselineOffset else 0L

                    val absDrift = kotlin.math.abs(drift)
                    val severity = when {
                        !baselineSet -> SyncSeverity.OK  // not yet calibrated, don't alarm
                        absDrift < 500 -> SyncSeverity.OK
                        absDrift < 2000 -> SyncSeverity.WARNING
                        absDrift < 5000 -> SyncSeverity.ERROR
                        else -> SyncSeverity.CRITICAL
                    }
                    _clockSyncStatus.value = ClockSyncStatus(
                        offsetMs = rawOffset,
                        baselineMs = baselineOffset,
                        baselineSet = baselineSet,
                        driftMs = drift,
                        severity = severity
                    )
                }
            }
        }
        // Resource polling: GET /api/system/resources every 5 seconds
        viewModelScope.launch {
            while (isActive) {
                val result = orinServiceRepository.fetchSystemResources()
                if (result.isSuccess) {
                    val json = result.getOrNull()
                    if (json != null) {
                        _orinResources.value = parseOrinResources(json)
                    }
                }
                delay(5000)
            }
        }
        // Service health: rebuild summary whenever service status refreshes
        viewModelScope.launch {
            combine(
                _gatewayServiceStatus,
                _videoManagementServiceStatus,
                robotState
            ) { gw, vm, state -> Triple(gw, vm, state) }
                .collect { (gw, vm, state) ->
                    _serviceHealthSummary.value = buildServiceHealthSummary(gw, vm, state)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
        stackActionAckTimeoutJob?.cancel()
    }

    fun setStepMode(mode: StepMode) {
        _stepMode.value = mode
    }

    fun cycleStepMode() {
        _stepMode.value = when (_stepMode.value) {
            StepMode.FINE -> StepMode.NORMAL
            StepMode.NORMAL -> StepMode.LEAP
            StepMode.LEAP -> StepMode.FINE
        }
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun setControlFocus(focus: ControlFocus) {
        _controlFocus.value = focus
    }

    fun cycleControlFocus() {
        _controlFocus.value = when (_controlFocus.value) {
            ControlFocus.CHASSIS -> ControlFocus.ARM
            ControlFocus.ARM -> ControlFocus.GIMBAL
            ControlFocus.GIMBAL -> ControlFocus.CHASSIS
        }
    }

    fun setArmControlMode(mode: ArmControlMode) {
        _armControlMode.value = mode
    }

    fun setControlViewMode(mode: ControlViewMode) {
        _controlViewMode.value = mode
    }

    fun toggleArmControlMode() {
        _armControlMode.value = when (_armControlMode.value) {
            ArmControlMode.EE_POSITION -> ArmControlMode.JOINT_ANGLE
            ArmControlMode.JOINT_ANGLE -> ArmControlMode.EE_POSITION
        }
    }

    fun updateControllerStatus(status: ControllerStatus) {
        _controllerStatus.value = status
    }

    fun selectSimpleTrackTrajectory(trajectory: String) {
        _simpleTrackState.update { it.copy(selectedTrajectory = trajectory) }
    }

    fun refreshSimpleTrackTrajectories() {
        sendRunControl("refresh", runControlMode = RunControlMode.SIMPLE_TRACK)
    }

    fun triggerCameraPhoto() {
        val cmd = buildJsonObject {
            put("type", "CameraPhoto")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
        }
        sendControl(cmd)
    }

    fun simpleTrackIniting() {
        val trajectory = _simpleTrackState.value.selectedTrajectory
        if (trajectory.isNullOrBlank()) return
        _simpleTrackState.update { it.copy(status = "initing") }
        _runState.update { it.copy(status = RunStatus.LOADING) }
        sendRunControl("init", trajectory = trajectory, runControlMode = RunControlMode.SIMPLE_TRACK)
    }

    private fun loadSimpleTrackTrajectory(trajectory: String) {
        if (trajectory.isBlank()) return
        _simpleTrackState.update { it.copy(selectedTrajectory = trajectory, status = "loaded") }
        _runState.update { prev ->
            prev.copy(
                loadedSessionId = trajectory,
                loadedSessionName = trajectory,
                currentFoiIndex = 0,
                totalFois = 0,
                status = RunStatus.LOADING,
                homebaseSet = false
            )
        }
        sendRunControl(
            action = "load",
            trajectory = trajectory,
            runControlMode = RunControlMode.SIMPLE_TRACK
        )
    }

    fun loadSession(input: String) {
        if (runState.value.runControlMode == RunControlMode.LIVE_PNC) {
            loadLivePncTrajectory(input)
            return
        }
        if (runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
            loadSimpleTrackTrajectory(input)
            return
        }
        val resolved = resolveFoiSession(input)
        val sessionId = resolved?.sessionId ?: input.trim()
        if (sessionId.isBlank()) return
        val localDetail = sampleSessionDetails[sessionId]
        _localSessionDetail.value = localDetail
        _runState.update { prev ->
            prev.copy(
                loadedSessionId = sessionId,
                loadedSessionName = resolved?.sessionName ?: localDetail?.sessionName ?: prev.loadedSessionName,
                currentFoiIndex = 0,
                totalFois = localDetail?.frames?.size ?: resolved?.count ?: prev.totalFois,
                status = if (localDetail != null) RunStatus.LOADED else RunStatus.LOADING
            )
        }
        if (localDetail != null) {
            return
        }
        requestLibraryGet(LibraryTarget.FOI_SESSION, sessionId)
        sendRunControl("load", sessionId = sessionId, sessionName = resolved?.sessionName)
    }

    private fun loadLivePncTrajectory(input: String) {
        val trajectory = input.trim()
        if (trajectory.isBlank()) return
        selectFixedPositionTrajectory(trajectory)
        _runState.update { prev ->
            prev.copy(
                loadedSessionId = trajectory,
                loadedSessionName = trajectory,
                currentFoiIndex = 0,
                totalFois = 0,
                status = RunStatus.LOADING,
                homebaseSet = false
            )
        }
        sendRunControl(
            action = "load",
            sessionId = trajectory,
            sessionName = trajectory,
            trajectory = trajectory
        )
    }

    fun startRun(deadman: Boolean = true) {
        if (runState.value.runControlMode == RunControlMode.LIVE_PNC) {
            fixedPositionStart()
            _runState.update { it.copy(status = RunStatus.RUNNING) }
            return
        }
        if (runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
            sendRunControl("run", deadman = deadman, runControlMode = RunControlMode.SIMPLE_TRACK)
            _runState.update { it.copy(status = RunStatus.RUNNING) }
            _simpleTrackState.update { it.copy(status = "running") }
            return
        }
        sendRunControl(
            "run",
            sessionId = runState.value.loadedSessionId,
            trajectory = null,
            deadman = deadman,
            skipHomebase = runState.value.skipHomebase
        )
        _runState.update { it.copy(status = RunStatus.RUNNING) }
    }

    fun homeRun(input: String = runState.value.loadedSessionId ?: runState.value.loadedSessionName.orEmpty()) {
        if (runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
            val trajectory = _simpleTrackState.value.selectedTrajectory
            if (trajectory.isNullOrBlank()) return
            _runState.update { it.copy(status = RunStatus.HOMING) }
            _simpleTrackState.update { it.copy(status = "homing") }
            sendRunControl("home", runControlMode = RunControlMode.SIMPLE_TRACK)
            return
        }
        if (runState.value.runControlMode == RunControlMode.LIVE_PNC) {
            val trajectory = _fixedPositionState.value.selectedTrajectory
                ?: runState.value.loadedSessionId
                ?: input.trim().takeIf { it.isNotBlank() }
            if (trajectory.isNullOrBlank()) {
                _fixedPositionState.update { it.copy(message = "Select a trajectory first") }
                return
            }
            _runState.update {
                it.copy(
                    loadedSessionId = trajectory,
                    loadedSessionName = trajectory,
                    status = RunStatus.HOMING
                )
            }
            _fixedPositionState.update {
                it.copy(
                    selectedTrajectory = trajectory,
                    status = "homing",
                    message = "Homing: matching POI to current map...",
                    motionActive = false,
                    paused = false
                )
            }
            sendRunControl(
                action = "home",
                sessionId = trajectory,
                sessionName = trajectory,
                trajectory = trajectory
            )
            return
        }
        val resolved = resolveFoiSession(input)
        val sessionId = runState.value.loadedSessionId ?: resolved?.sessionId ?: input.trim()
        if (sessionId.isBlank()) return
        val sessionName = resolved?.sessionName ?: runState.value.loadedSessionName ?: sessionId
        _runState.update {
            it.copy(
                loadedSessionId = sessionId,
                loadedSessionName = sessionName,
                status = RunStatus.HOMING
            )
        }
        sendRunControl(
            action = "home",
            sessionId = sessionId,
            sessionName = sessionName,
            skipHomebase = runState.value.skipHomebase
        )
    }

    fun resumeRun(deadman: Boolean = true) {
        if (runState.value.runControlMode == RunControlMode.LIVE_PNC) {
            fixedPositionStart()
            _runState.update { it.copy(status = RunStatus.RUNNING) }
            return
        }
        if (runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
            sendRunControl("resume", deadman = deadman, runControlMode = RunControlMode.SIMPLE_TRACK)
            _runState.update { it.copy(status = RunStatus.RUNNING) }
            _simpleTrackState.update { it.copy(status = "running") }
            return
        }
        sendRunControl(
            "resume",
            sessionId = runState.value.loadedSessionId,
            trajectory = null,
            deadman = deadman,
            skipHomebase = runState.value.skipHomebase
        )
        _runState.update { it.copy(status = RunStatus.RUNNING) }
    }

    fun pauseRun() {
        if (runState.value.runControlMode == RunControlMode.LIVE_PNC) {
            fixedPositionPause()
            _runState.update { it.copy(status = RunStatus.PAUSED) }
            return
        }
        if (runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
            sendRunControl("pause", runControlMode = RunControlMode.SIMPLE_TRACK)
            _runState.update { it.copy(status = RunStatus.PAUSED) }
            _simpleTrackState.update { it.copy(status = "paused") }
            return
        }
        sendRunControl(
            "pause",
            sessionId = runState.value.loadedSessionId,
            skipHomebase = runState.value.skipHomebase
        )
        _runState.update { it.copy(status = RunStatus.PAUSED) }
    }

    fun stopRun() {
        if (runState.value.runControlMode == RunControlMode.LIVE_PNC) {
            fixedPositionStop()
            _runState.update { it.copy(status = RunStatus.IDLE) }
            return
        }
        if (runState.value.runControlMode == RunControlMode.SIMPLE_TRACK) {
            sendRunControl("stop", runControlMode = RunControlMode.SIMPLE_TRACK)
            _runState.update { it.copy(status = RunStatus.IDLE) }
            _simpleTrackState.update { it.copy(status = "idle") }
            return
        }
        sendRunControl(
            "stop",
            sessionId = runState.value.loadedSessionId,
            skipHomebase = runState.value.skipHomebase
        )
        _runState.update { it.copy(status = RunStatus.IDLE) }
    }

    fun toggleSkipHomebase() {
        val next = !runState.value.skipHomebase
        _runState.update { it.copy(skipHomebase = next) }
        sendRunControl(
            "skip_homebase",
            sessionId = runState.value.loadedSessionId,
            skipHomebase = next
        )
    }

    fun nextFoi() {
        val nextIndex = runState.value.currentFoiIndex + 1
        seekFoi(nextIndex)
    }

    fun prevFoi() {
        val prevIndex = runState.value.currentFoiIndex - 1
        seekFoi(prevIndex)
    }

    fun seekFoi(index: Int) {
        val total = runState.value.totalFois
        if (total <= 0) return
        val clamped = index.coerceIn(0, total - 1)
        _runState.update { it.copy(currentFoiIndex = clamped) }
        sendRunControl(
            "seek",
            sessionId = runState.value.loadedSessionId,
            targetIndex = clamped,
            skipHomebase = runState.value.skipHomebase
        )
    }

    fun connectGateway() {
        val url = _gatewayUrl.value
        if (url.isBlank()) return
        viewModelScope.launch {
            gatewayClient.connect(url)
        }
    }

    fun disconnectGateway() {
        viewModelScope.launch {
            gatewayClient.disconnect()
        }
    }

    fun reconnectGateway() {
        val url = _gatewayUrl.value
        if (url.isBlank()) return
        viewModelScope.launch {
            gatewayClient.disconnect()
            delay(200)
            gatewayClient.connect(url)
        }
    }

    fun refreshGatewayService() {
        viewModelScope.launch {
            _gatewayServiceBusy.value = true
            _gatewayServiceError.value = null
            val startMs = System.currentTimeMillis()
            val result = orinServiceRepository.fetchStatus()
            if (result.isSuccess) {
                val services = result.getOrNull()
                _gatewayServiceStatus.value = services?.get(gatewayServiceId)
                _videoManagementServiceStatus.value = services?.get(videoManagementServiceId)
                updateOdomModeFromServices(services)
                _gatewayApiStatus.value = "OK"
                _gatewayApiLatencyMs.value = System.currentTimeMillis() - startMs
            } else {
                _gatewayServiceError.value = result.exceptionOrNull()?.message ?: "Gateway status failed"
                _gatewayApiStatus.value = "ERR"
                _gatewayApiLatencyMs.value = null
                _odomMode.value = OdomMode.UNKNOWN
            }
            _gatewayServiceBusy.value = false
            refreshRobotIdentity()
        }
    }

    fun refreshRobotIdentity() {
        viewModelScope.launch {
            _robotIdentityBusy.value = true
            _robotIdentityError.value = null
            val result = orinServiceRepository.fetchIdentity()
            if (result.isSuccess) {
                _robotIdentity.value = result.getOrNull()
            } else {
                _robotIdentityError.value = result.exceptionOrNull()?.message ?: "Robot identity failed"
            }
            _robotIdentityBusy.value = false
        }
    }

    fun startGatewayService() {
        viewModelScope.launch {
            _gatewayServiceBusy.value = true
            _gatewayServiceError.value = null
            val result = orinServiceRepository.startService(gatewayServiceId)
            if (result.isSuccess) {
                _gatewayServiceStatus.value = result.getOrNull()?.services?.get(gatewayServiceId)
                _videoManagementServiceStatus.value = result.getOrNull()?.services?.get(videoManagementServiceId)
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
            val result = orinServiceRepository.stopService(gatewayServiceId)
            if (result.isSuccess) {
                _gatewayServiceStatus.value = result.getOrNull()?.services?.get(gatewayServiceId)
                _videoManagementServiceStatus.value = result.getOrNull()?.services?.get(videoManagementServiceId)
            } else {
                _gatewayServiceError.value = result.exceptionOrNull()?.message ?: "Gateway stop failed"
            }
            _gatewayServiceBusy.value = false
        }
    }

    fun refreshVideoManagementStatus() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            val result = orinServiceRepository.fetchVideoStatus()
            if (result.isSuccess) {
                val status = result.getOrNull()
                _videoManagementStatus.value = status
                status?.config?.uploadPreset?.let { _currentUploadPreset.value = it }
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Video status failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun setUploadPreset(preset: String) {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            _videoManagementMessage.value = null
            val result = orinServiceRepository.setUploadPreset(preset)
            if (result.isSuccess) {
                _currentUploadPreset.value = preset
                _videoManagementMessage.value = "Upload preset: $preset"
                refreshVideoManagementStatus()
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Set preset failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun startVideoRecording(sessionName: String = "") {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            _videoManagementMessage.value = null
            val result = orinServiceRepository.startVideoRecording(sessionName.ifBlank { null })
            if (result.isSuccess) {
                _videoManagementMessage.value = "Record start requested"
                refreshVideoManagementStatus()
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Start recording failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun stopVideoRecording() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            _videoManagementMessage.value = null
            val result = orinServiceRepository.stopVideoRecording()
            if (result.isSuccess) {
                _videoManagementMessage.value = "Record stop requested"
                refreshVideoManagementStatus()
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Stop recording failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun requeueForCurrentDestination() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            _videoManagementMessage.value = null
            val result = orinServiceRepository.requeueForCurrentDestination()
            if (result.isSuccess) {
                _videoManagementMessage.value = "Requeued for ${_currentUploadPreset.value}"
                refreshVideoManagementStatus()
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Requeue failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun retryFailedVideoUploads() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            _videoManagementMessage.value = null
            val result = orinServiceRepository.retryFailedVideoUploads()
            if (result.isSuccess) {
                _videoManagementMessage.value = "Retry requested"
                refreshVideoManagementStatus()
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Retry failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun triggerVideoUploadNow() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            _videoManagementMessage.value = null
            val result = orinServiceRepository.triggerVideoUploadNow()
            if (result.isSuccess) {
                _videoManagementMessage.value = "Upload triggered"
                refreshVideoManagementStatus()
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Upload trigger failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun startVideoManagementService() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            val result = orinServiceRepository.startService(videoManagementServiceId)
            if (result.isSuccess) {
                _videoManagementServiceStatus.value =
                    result.getOrNull()?.services?.get(videoManagementServiceId)
                _videoManagementMessage.value = "Video service started"
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Video service start failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun stopVideoManagementService() {
        viewModelScope.launch {
            _videoManagementBusy.value = true
            _videoManagementError.value = null
            val result = orinServiceRepository.stopService(videoManagementServiceId)
            if (result.isSuccess) {
                _videoManagementServiceStatus.value =
                    result.getOrNull()?.services?.get(videoManagementServiceId)
                _videoManagementMessage.value = "Video service stopped"
            } else {
                _videoManagementError.value = result.exceptionOrNull()?.message ?: "Video service stop failed"
            }
            _videoManagementBusy.value = false
        }
    }

    fun refreshOdomMode() {
        viewModelScope.launch {
            if (_odomSwitchBusy.value) return@launch
            _odomSwitchBusy.value = true
            _odomSwitchError.value = null
            val result = orinServiceRepository.fetchStatus()
            if (result.isSuccess) {
                updateOdomModeFromServices(result.getOrNull())
                _odomSwitchMessage.value = null
            } else {
                _odomSwitchError.value = result.exceptionOrNull()?.message ?: "Odom status failed"
            }
            _odomSwitchBusy.value = false
        }
    }

    fun setOdomMode(target: OdomMode) {
        if (target != OdomMode.REAL && target != OdomMode.FAKE) return
        viewModelScope.launch {
            if (_odomSwitchBusy.value) return@launch
            _odomSwitchBusy.value = true
            _odomSwitchError.value = null
            _odomSwitchMessage.value = null
            val modeValue = if (target == OdomMode.REAL) "real" else "fake"
            val result = orinServiceRepository.setOdomMode(modeValue)
            if (result.isSuccess) {
                val response = result.getOrNull()
                updateOdomModeFromServices(response?.services)
                _odomSwitchMessage.value = response?.message
            } else {
                _odomSwitchError.value = result.exceptionOrNull()?.message ?: "Odom switch failed"
            }
            _odomSwitchBusy.value = false
        }
    }

    private fun updateOdomModeFromServices(services: Map<String, OrinServiceStatus>?) {
        if (services == null) {
            _odomMode.value = OdomMode.UNKNOWN
            return
        }
        val realRunning = services["odom_wheel"]?.running == true
        val fakeRunning = services["odom_fake"]?.running == true
        _odomMode.value = when {
            realRunning && fakeRunning -> OdomMode.CONFLICT
            realRunning -> OdomMode.REAL
            fakeRunning -> OdomMode.FAKE
            else -> OdomMode.OFF
        }
    }

    private fun parseOrinResources(json: JsonObject): OrinResources? {
        // API returns nested structure: {cpu: {overall_pct, cores}, gpu: {load_pct},
        //   memory: {total_mb, used_mb, available_mb, used_pct}, thermal: {cpu_temp_c, gpu_temp_c}}
        return try {
            val cpuObj = json["cpu"]?.jsonObject ?: return null
            val cpuOverall = cpuObj["overall_pct"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                ?: return null
            val cpuCores = cpuObj["cores"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.doubleOrNull?.toFloat() }
                ?: emptyList()
            val gpuObj = json["gpu"]?.jsonObject
            val gpuLoad = gpuObj?.get("load_pct")?.jsonPrimitive?.doubleOrNull?.toFloat()
            val memObj = json["memory"]?.jsonObject ?: return null
            val memUsedPct = memObj["used_pct"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                ?: return null
            val memUsedMb = memObj["used_mb"]?.jsonPrimitive?.intOrNull ?: 0
            val memTotalMb = memObj["total_mb"]?.jsonPrimitive?.intOrNull ?: 0
            val thermalObj = json["thermal"]?.jsonObject
            val cpuTempC = thermalObj?.get("cpu_temp_c")?.jsonPrimitive?.doubleOrNull?.toFloat()
            val gpuTempC = thermalObj?.get("gpu_temp_c")?.jsonPrimitive?.doubleOrNull?.toFloat()
            OrinResources(
                cpuOverallPct = cpuOverall,
                cpuCores = cpuCores,
                gpuLoadPct = gpuLoad,
                memoryUsedPct = memUsedPct,
                memoryUsedMb = memUsedMb,
                memoryTotalMb = memTotalMb,
                cpuTempC = cpuTempC,
                gpuTempC = gpuTempC
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse OrinResources: ${e.message}")
            null
        }
    }

    private fun buildServiceHealthSummary(
        gatewayStatus: OrinServiceStatus?,
        videoMgmtStatus: OrinServiceStatus?,
        state: JsonObject?
    ): ServiceHealthSummary {
        val topicHealth = state?.get("topic_health")?.jsonObject
        val robotPoseOk = topicHealth?.get("robot_pose_ok")?.jsonPrimitive?.booleanOrNull ?: false
        val pncFsm = _runState.value.pncFsmState

        val coreServices = listOf(
            ServiceEntry("recomo_gateway_ws", "Gateway", gatewayStatus?.running == true)
        )
        val videoServices = listOf(
            ServiceEntry("video_management", "Video Management", videoMgmtStatus?.running == true)
        )
        val navServices = listOf(
            ServiceEntry("robot_pose", "Robot Pose", robotPoseOk),
            ServiceEntry("pnc_fsm", "PnC FSM", pncFsm == 1 || pncFsm == 4)
        )

        val groups = mapOf(
            "Core" to GroupHealth("Core", coreServices, coreServices.all { it.running }),
            "Video" to GroupHealth("Video", videoServices, videoServices.all { it.running }),
            "Navigation" to GroupHealth("Navigation", navServices, navServices.all { it.running })
        )

        val allServices = coreServices + videoServices + navServices
        return ServiceHealthSummary(
            totalServices = allServices.size,
            runningServices = allServices.count { it.running },
            groups = groups
        )
    }
    
    // EE IK Controller management - separate start/stop like gateway service
    fun startEEIKController(dryRun: Boolean = true) {
        viewModelScope.launch {
            _eeIkControllerBusy.value = true
            try {
                Log.d(TAG, "startEEIKController: sending start command (dry_run=$dryRun)")
                val command = buildJsonObject {
                    put("type", "ee_ik_controller_cmd")
                    put("action", "start")
                    put("dry_run", dryRun)
                }
                sendControl(command)
                // State will be updated automatically via gateway broadcast
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start EE IK controller", e)
            } finally {
                _eeIkControllerBusy.value = false
            }
        }
    }
    
    fun stopEEIKController() {
        viewModelScope.launch {
            _eeIkControllerBusy.value = true
            try {
                Log.d(TAG, "stopEEIKController: sending stop command")
                val command = buildJsonObject {
                    put("type", "ee_ik_controller_cmd")
                    put("action", "stop")
                }
                sendControl(command)
                // State will be updated automatically via gateway broadcast
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop EE IK controller", e)
            } finally {
                _eeIkControllerBusy.value = false
            }
        }
    }

    fun startRecordingTopics() {
        viewModelScope.launch {
            if (!canTriggerDriverAction(DriverAction.START)) return@launch
            _recordingTopicsBusy.value = true
            _recordingTopicsError.value = null
            _recordingTopicsMessage.value = "Preparing robot drivers..."
            pendingDriverAction = DriverAction.START
            _driverLifecycle.value = DriverLifecycleState.PREPARING
            scheduleDriverActionTimeout(DriverAction.START)
            
            try {
                val cmd = buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", "start_all")
                }
                sendControl(cmd)
                Log.d("RecomoControl", "Preparing robot drivers (lidar, camera, chassis, arm, gimbal)")
            } catch (e: Exception) {
                Log.e("RecomoControl", "startRecordingTopics failed: ${e.message}")
                _recordingTopicsError.value = "Failed to start drivers: ${e.message}"
                pendingDriverAction = DriverAction.NONE
                _recordingTopicsBusy.value = false
            }
        }
    }

    fun stopRecordingTopics() {
        viewModelScope.launch {
            if (!canTriggerDriverAction(DriverAction.STOP)) return@launch
            _recordingTopicsBusy.value = true
            _recordingTopicsError.value = null
            _recordingTopicsMessage.value = "Dismissing robot drivers..."
            pendingDriverAction = DriverAction.STOP
            _driverLifecycle.value = DriverLifecycleState.DISMISSING
            scheduleDriverActionTimeout(DriverAction.STOP)
            
            try {
                val cmd = buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", "stop_all")
                }
                sendControl(cmd)
                Log.d("RecomoControl", "Dismissing robot drivers")
            } catch (e: Exception) {
                Log.e("RecomoControl", "stopRecordingTopics failed: ${e.message}")
                _recordingTopicsError.value = "Failed to stop drivers: ${e.message}"
                pendingDriverAction = DriverAction.NONE
                _recordingTopicsBusy.value = false
            }
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
                sendControl(cmd)
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
        val locationId = selectedMap.value
        val mapName = selectedMapAsset.value
        if (locationId.isNullOrBlank()) {
            _stackActionError.value = "Select location in MAP & POI first"
            return
        }
        val resolvedMapName = mapName?.takeIf { it.isNotBlank() } ?: "hummingbird_map_01"
        triggerStackAction(
            action = "prepare_localization",
            busyMessage = "Preparing localization nodes..."
        ) {
            put("location_id", locationId)
            put("map_name", resolvedMapName)
            put("threshold", threshold.coerceIn(0.0, 1.0))
        }
    }

    fun dismissLocalizationNodes() {
        triggerStackAction(
            action = "dismiss_localization",
            busyMessage = "Stopping localization nodes..."
        )
    }

    fun preparePncNodesForCurrentMode() {
        val mode = operationModeWireName(operationMode.value)
        triggerStackAction(
            action = "prepare_pnc",
            busyMessage = "Preparing PnC for ${mode.replace('_', ' ')}..."
        ) {
            put("mode", mode)
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

    fun sendEstop() = sendSafety("estop")
    fun clearEstop() = sendSafety("clear_estop")
    fun freezeAll() = sendSafety("freeze_all")
    fun unfreezeAll() = sendSafety("unfreeze_all")

    private fun operationModeWireName(mode: OperationMode): String {
        return when (mode) {
            OperationMode.MANUAL -> "manual"
            OperationMode.MOTION_REPLICA -> "motion_replica"
            OperationMode.SUBJECT_FOLLOWING -> "subject_following"
            OperationMode.FIXED_POSITION -> "fixed_position"
        }
    }

    private fun runControlModeWireName(mode: RunControlMode): String {
        return when (mode) {
            RunControlMode.SIMPLE_TRACK -> "simple_track"
            RunControlMode.LIVE_PNC -> "live_pnc"
        }
    }

    private fun runControlModeFromWire(mode: String?): RunControlMode? {
        return when (mode?.trim()?.lowercase()) {
            "simple_track", "simpletrack" -> RunControlMode.SIMPLE_TRACK
            "live_pnc", "livepnc" -> RunControlMode.LIVE_PNC
            else -> null
        }
    }

    fun setOperationMode(mode: OperationMode) {
        _operationMode.value = mode
        val cmd = buildJsonObject {
            put("type", "ModeCmd")
            put("mode", operationModeWireName(mode))
        }
        sendControl(cmd)
        if (mode == OperationMode.FIXED_POSITION) {
            requestFixedPositionTrajectories()
        }
    }

    fun setRunControlMode(mode: RunControlMode) {
        _runState.update { it.copy(runControlMode = mode) }
        if (mode == RunControlMode.LIVE_PNC) {
            requestFixedPositionTrajectories()
        }
        sendRunControl(
            action = "set_mode",
            runControlMode = mode
        )
    }

    fun sendPreparePose(armQ: List<Double>, gimbalQ: List<Double>) {
        val cmd = buildJsonObject {
            put("type", "PreparePose")
            put("action", "go")
            put("arm_q", kotlinx.serialization.json.buildJsonArray { armQ.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("gimbal_q", kotlinx.serialization.json.buildJsonArray { gimbalQ.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        }
        sendControl(cmd)
    }

    fun sendArmHomingPose() {
        // Delegate arm homing to homing.py on Orin (jerk-limited, supervised).
        // Hold gimbal at current angles while arm homes.
        val holdGimbal = gimbalAngles.value.takeIf { it.size >= 3 }?.take(3) ?: GIMBAL_HOMING_Q_DEG
        val cmd = buildJsonObject {
            put("type", "ArmHomeCmd")
            put("gimbal_q", kotlinx.serialization.json.buildJsonArray {
                holdGimbal.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }
        sendControl(cmd)
    }

    fun sendGimbalHomingPose() {
        // Gateway PreparePose arm_q follows arm command units (degrees on recomoProto1).
        val holdArmDeg = armAngles.value
            .takeIf { it.size >= 3 }
            ?.take(3)
            ?.map { Math.toDegrees(it) }
            ?: ARM_HOMING_Q_DEG
        sendPreparePose(holdArmDeg, GIMBAL_HOMING_Q_DEG)
    }

    fun stopPreparePose() {
        val cmd = buildJsonObject {
            put("type", "PreparePose")
            put("action", "stop")
        }
        sendControl(cmd)
    }

    fun sendFollowCmd(action: String, maxSpeed: Double = 0.3, followDistance: Double = 2.0) {
        val cmd = buildJsonObject {
            put("type", "FollowCmd")
            put("action", action)
            put("max_speed", maxSpeed)
            put("follow_distance", followDistance)
        }
        sendControl(cmd)
    }

    fun navigateToPoi(sessionId: String, speed: Double, poiIndex: Int = 0) {
        _poiNavActive.value = true
        _runState.update { it.copy(pncFsmState = 1) }
        navSpeedUpdateJob?.cancel()
        val mode = operationModeWireName(operationMode.value)
        val cmd = buildJsonObject {
            put("type", "NavCmd")
            put("action", "go")
            put("session_id", sessionId)
            put("speed", speed)
            put("poi_index", poiIndex.coerceAtLeast(0))
            put("mode", mode)
        }
        sendControl(cmd)
    }

    fun updatePoiNavSpeed(speed: Double) {
        val runFsm = _runState.value.pncFsmState
        if (!_poiNavActive.value && runFsm != 1) return
        navSpeedUpdateJob?.cancel()
        navSpeedUpdateJob = viewModelScope.launch {
            delay(NAV_SPEED_UPDATE_DEBOUNCE_MS)
            val cmd = buildJsonObject {
                put("type", "NavCmd")
                put("action", "speed")
                put("speed", speed)
            }
            sendControl(cmd)
        }
    }

    fun stopPoiNav() {
        _poiNavActive.value = false
        navSpeedUpdateJob?.cancel()
        navSpeedUpdateJob = null
        val cmd = buildJsonObject {
            put("type", "NavCmd")
            put("action", "stop")
        }
        sendControl(cmd)
    }

    fun pausePoiNav() {
        val cmd = buildJsonObject {
            put("type", "NavCmd")
            put("action", "pause")
        }
        sendControl(cmd)
    }

    fun requestFixedPositionTrajectories() {
        sendFixedPositionCmd("list")
    }

    fun selectFixedPositionTrajectory(trajectory: String) {
        if (trajectory.isBlank()) return
        _fixedPositionState.update { it.copy(selectedTrajectory = trajectory) }
        sendFixedPositionCmd(
            action = "select",
            extra = buildJsonObject {
                put("trajectory", trajectory)
            }
        )
    }

    fun fixedPositionHoming() {
        _fixedPositionState.update {
            it.copy(
                status = "homing",
                message = "Homing command sent to arm/gimbal drivers.",
                executorState = 0,
                stage2ExecutionStatus = "idle",
                motionActive = false,
                paused = false,
                motionComplete = false,
                uploadEligible = false
            )
        }
        sendFixedPositionCmd("homing")
    }

    fun fixedPositionIniting() {
        val selected = _fixedPositionState.value.selectedTrajectory
        if (selected.isNullOrBlank()) {
            _fixedPositionState.update { it.copy(message = "Select a trajectory first") }
            return
        }
        _fixedPositionState.update {
            it.copy(
                status = "initing",
                message = "Initing requested: loading trajectory to first waypoint.",
                executorState = 1,
                stage2ExecutionStatus = "idle",
                motionActive = true,
                paused = false,
                motionComplete = false,
                recordingActive = false,
                videoReady = false,
                thumbnailReady = false,
                uploadEligible = false
            )
        }
        sendFixedPositionCmd(
            action = "initing",
            extra = buildJsonObject {
                put("trajectory", selected)
            }
        )
    }

    fun fixedPositionStart() {
        val selected = _fixedPositionState.value.selectedTrajectory
        if (selected.isNullOrBlank()) {
            _fixedPositionState.update { it.copy(message = "Select a trajectory first") }
            return
        }
        _fixedPositionState.update {
            it.copy(
                status = "running",
                message = "Stage2 trigger sent; recording and thumbnail capture started.",
                stage2ExecutionStatus = "triggering",
                motionActive = true,
                paused = false,
                motionComplete = false,
                recordingActive = true,
                videoReady = false,
                thumbnailReady = false,
                uploadEligible = false
            )
        }
        sendFixedPositionCmd(
            action = "start",
            extra = buildJsonObject {
                put("trajectory", selected)
            }
        )
    }

    fun fixedPositionPause() {
        _fixedPositionState.update {
            it.copy(
                status = "paused",
                message = "Motion paused",
                motionActive = true,
                paused = true
            )
        }
        sendFixedPositionCmd("pause")
    }

    fun fixedPositionStop() {
        _fixedPositionState.update {
            it.copy(
                status = "stopped",
                message = "Motion stopped early; recording marked incomplete, upload disabled.",
                executorState = 0,
                stage2ExecutionStatus = "stopped",
                motionActive = false,
                paused = false,
                motionComplete = false,
                recordingActive = false,
                uploadEligible = false
            )
        }
        sendFixedPositionCmd("stop")
    }

    fun sendChassisStep(dxDir: Int, dyDir: Int) {
        val step = stepMeters(stepSettings.value.chassisFineM, stepSettings.value.chassisNormalM, stepSettings.value.chassisLeapM)
        val durationSec = CHASSIS_STEP_DURATION_MS / 1000.0
        val vx = (dxDir.coerceIn(-1, 1) * step) / durationSec
        val vy = (dyDir.coerceIn(-1, 1) * step) / durationSec
        Log.d(TAG, "sendChassisStep: dxDir=$dxDir, dyDir=$dyDir, step=$step, vx=$vx, vy=$vy")
        if (vx == 0.0 && vy == 0.0) return
        launchChassisPulse(vx, vy, 0.0)
    }

    fun sendChassisRotate(dir: Int) {
        val stepDeg = stepMeters(
            stepSettings.value.yawFineDeg,
            stepSettings.value.yawNormalDeg,
            stepSettings.value.yawLeapDeg
        )
        val stepRad = stepDeg * PI / 180.0
        val durationSec = CHASSIS_STEP_DURATION_MS / 1000.0
        val wz = (dir.coerceIn(-1, 1) * stepRad) / durationSec
        Log.d(TAG, "sendChassisRotate: dir=$dir, stepDeg=$stepDeg, wz=$wz")
        if (wz == 0.0) return
        launchChassisPulse(0.0, 0.0, wz)
    }

    fun sendDrive(vx: Double, vy: Double, wz: Double, brake: Boolean = false, smooth: Boolean = true) {
        Log.d(TAG, "sendDrive: vx=$vx, vy=$vy, wz=$wz, brake=$brake, smooth=$smooth")
        val cmd = buildJsonObject {
            put("type", "DriveCmd")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", DRIVE_TTL_MS)
            put("deadman", true)
            put("vx", vx)
            put("vy", vy)
            put("wz", wz)
            put("brake", brake)
            put("smooth", smooth)
        }
        sendControl(cmd)
    }

    fun sendChassisVelocityNormalized(vx: Double, vy: Double, wz: Double, brakeWhenZero: Boolean = true) {
        val vel = chassisVelocity()
        val brake = brakeWhenZero && vx == 0.0 && vy == 0.0 && wz == 0.0
        sendDrive(vx * vel, vy * vel, wz * vel, brake = brake)
    }

    fun sendCameraCanRecordEnabled(enabled: Boolean) {
        val cmd = buildJsonObject {
            put("type", "ConfigCmd")
            put("camera_can_record_enabled", enabled)
        }
        sendControl(cmd)
    }

    fun sendBaseAccelTuning(linearAccel: Double, angularAccel: Double) {
        val lin = linearAccel.coerceIn(0.0, BASE_MAX_ACCEL_M_S2)
        val ang = angularAccel.coerceIn(0.0, BASE_MAX_ANG_ACCEL_RAD_S2)
        _baseAccelInput.value = String.format("%.2f", lin)
        _baseAngAccelInput.value = String.format("%.2f", ang)
        val cmd = buildJsonObject {
            put("type", "LiveTuning")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", DRIVE_TTL_MS)
            put("smoothing", buildJsonObject {
                put("base_max_accel_m_s2", lin)
                put("base_max_ang_accel_rad_s2", ang)
            })
        }
        sendControl(cmd)
    }

    fun setBaseAccelInput(text: String) {
        _baseAccelInput.value = text
    }

    fun setBaseAngAccelInput(text: String) {
        _baseAngAccelInput.value = text
    }

    private fun launchChassisPulse(vx: Double, vy: Double, wz: Double) {
        chassisStepJob?.cancel()
        sendDrive(vx, vy, wz, brake = false, smooth = false)
        chassisStepJob = viewModelScope.launch {
            kotlinx.coroutines.delay(CHASSIS_STEP_DURATION_MS)
            sendDrive(0.0, 0.0, 0.0, brake = true, smooth = false)
        }
    }

    fun sendArmJog(j4: Double, j5: Double, j6: Double) {
        val cmd = buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "arm")
            put("mode", "velocity")
            put("values", buildJsonArray {
                add(j4)  // joint4 (elbow) - 0.0 if not present
                add(j5)  // joint5 (pitch)
                add(j6)  // joint6 (yaw)
            })
        }
        sendControl(cmd)
    }

    fun sendArmJointVelocityNormalized(jointIndex: Int, velocity: Double) {
        if (jointIndex == 0 && !isJoint4Enabled()) return
        val vel = armVelocityRadS() * velocity
        val j4Vel = if (jointIndex == 0) vel else 0.0
        val j5Vel = if (jointIndex == 1) vel else 0.0
        val j6Vel = if (jointIndex == 2) vel else 0.0
        sendArmJog(j4Vel, j5Vel, j6Vel)
    }

    fun nudgeArmJointPositive(index: Int) = nudgeArmJoint(index, armJointStepRad())
    fun nudgeArmJointNegative(index: Int) = nudgeArmJoint(index, -armJointStepRad())

    private fun nudgeArmJoint(index: Int, deltaRad: Double) {
        if (index !in armJointTarget.indices) return
        if (index == 0 && !isJoint4Enabled()) return
        val durationMs = ARM_STEP_PULSE_MS
        val durationSec = durationMs / 1000.0
        val maxVel = armVelocityRadS()
        val vel = (deltaRad / durationSec).coerceIn(-maxVel, maxVel)
        val j4Vel = if (index == 0) vel else 0.0
        val j5Vel = if (index == 1) vel else 0.0
        val j6Vel = if (index == 2) vel else 0.0
        armMoveJob?.cancel()
        armPulseJob?.cancel()
        sendArmJog(j4Vel, j5Vel, j6Vel)
        armPulseJob = viewModelScope.launch {
            delay(durationMs)
            sendArmJog(0.0, 0.0, 0.0)
        }
    }

    fun nudgeCameraGoal(dxDir: Int, dyDir: Int, dzDir: Int) {
        val step = stepMeters(stepSettings.value.armFineM, stepSettings.value.armNormalM, stepSettings.value.armLeapM)
        val dxBody = dxDir.coerceIn(-1, 1) * step
        val dyBody = dyDir.coerceIn(-1, 1) * step
        val dzBody = dzDir.coerceIn(-1, 1) * step
        if (dxBody == 0.0 && dyBody == 0.0 && dzBody == 0.0) return
        nudgeCameraGoalMeters(dxBody, dyBody, dzBody)
    }

    private fun nudgeCameraGoalMeters(dxBody: Double, dyBody: Double, dzBody: Double) {
        val cam = cameraPose.value
        if (!cam.ok) return

        val base = baseStatus.value
        val c = cos(base.yaw)
        val s = sin(base.yaw)
        val dxOdom = c * dxBody - s * dyBody
        val dyOdom = s * dxBody + c * dyBody

        sendCameraGoal(
            x = cam.x + dxOdom,
            y = cam.y + dyOdom,
            z = cam.z + dzBody,
            qw = cam.qw,
            qx = cam.qx,
            qy = cam.qy,
            qz = cam.qz,
            frameId = "odom"
        )
    }

    fun nudgeCameraGoalContinuous(dxBody: Double, dyBody: Double, dzBody: Double) {
        if (dxBody == 0.0 && dyBody == 0.0 && dzBody == 0.0) return
        nudgeCameraGoalMeters(dxBody, dyBody, dzBody)
    }

    fun sendCameraGoal(
        x: Double,
        y: Double,
        z: Double,
        qw: Double,
        qx: Double,
        qy: Double,
        qz: Double,
        frameId: String
    ) {
        val cmd = buildJsonObject {
            put("type", "CameraGoal")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", CAMERA_TTL_MS)
            put("deadman", true)
            put("frame_id", frameId)
            put("position", buildJsonObject {
                put("x", x)
                put("y", y)
                put("z", z)
            })
            put("orientation_quat", buildJsonObject {
                put("w", qw)
                put("x", qx)
                put("y", qy)
                put("z", qz)
            })
        }
        sendControl(cmd)
    }

    fun nudgeGimbal(axis: Int, deltaRad: Double) {
        if (axis !in 0..2) return
        lastGimbalCmdMs = System.currentTimeMillis()
        gimbalTarget[axis] += deltaRad
        val cmd = buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "gimbal")
            put("mode", "position")
            put("values", buildJsonArray {
                add(gimbalTarget[0])
                add(gimbalTarget[1])
                add(gimbalTarget[2])
            })
        }
        sendControl(cmd)
    }

    fun nudgeGimbalPositive(axis: Int) = nudgeGimbal(axis, gimbalStepRad())
    fun nudgeGimbalNegative(axis: Int) = nudgeGimbal(axis, -gimbalStepRad())

    // Generate session name: session_ddMMyyyy-HHmmss
    private fun generateSessionName(): String {
        val sdf = java.text.SimpleDateFormat("ddMMyyyy-HHmmss", java.util.Locale.US)
        return "session_${sdf.format(java.util.Date())}"
    }

    // Generate session ID: foi_ddMMyyyy-HHmmss or poi_ddMMyyyy-HHmmss
    private fun generateSessionId(prefix: String): String {
        val sdf = java.text.SimpleDateFormat("ddMMyyyy-HHmmss", java.util.Locale.US)
        return "${prefix}_${sdf.format(java.util.Date())}"
    }

    private fun resolveFoiSession(input: String): SessionSummary? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val sessions = librarySummary.value.foiSessions
        return sessions.firstOrNull { it.sessionId == trimmed }
            ?: sessions.firstOrNull { it.sessionName.equals(trimmed, ignoreCase = true) }
    }

    fun loadSampleSessions(context: Context) {
        if (samplesLoaded) return
        samplesLoaded = true
        viewModelScope.launch {
            try {
                val indexText = context.assets.open("sample_sessions/index.json")
                    .bufferedReader().use { it.readText() }
                val arr = jsonParser.parseToJsonElement(indexText).jsonArray
                val sessions = mutableListOf<SessionSummary>()
                for (elem in arr) {
                    val obj = elem.jsonObject
                    val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull ?: continue
                    val file = obj["file"]?.jsonPrimitive?.contentOrNull ?: continue
                    val summary = SessionSummary(
                        sessionId = sessionId,
                        sessionName = obj["session_name"]?.jsonPrimitive?.contentOrNull ?: sessionId,
                        robotName = obj["robot_name"]?.jsonPrimitive?.contentOrNull ?: "",
                        frameId = obj["frame_id"]?.jsonPrimitive?.contentOrNull ?: "",
                        category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "",
                        count = obj["foi_count"]?.jsonPrimitive?.intOrNull ?: 0,
                        target = LibraryTarget.FOI_SESSION
                    )
                    sessions.add(summary)
                    val detailText = context.assets.open("sample_sessions/$file")
                        .bufferedReader().use { it.readText() }
                    val sessionObj = jsonParser.parseToJsonElement(detailText).jsonObject
                    val detail = parseSessionDetail(sessionObj)
                    if (detail != null) {
                        sampleSessionDetails[sessionId] = detail
                    }
                }
                _sampleSessions.value = sessions
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load sample sessions", e)
            }
        }
    }

    private fun parseSessionDetail(session: JsonObject?): SessionDetail? {
        if (session == null) return null
        val sessionId = session["session_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val sessionName = session["session_name"]?.jsonPrimitive?.contentOrNull ?: sessionId
        val frames = session["fois"]?.jsonArray?.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val poi = obj["poi"]?.jsonObject
            val basePose = poi?.get("base_pose")?.jsonObject
            FrameRecord(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                sequenceIndex = obj["sequence_index"]?.jsonPrimitive?.intOrNull ?: 0,
                sessionRole = obj["session_role"]?.jsonPrimitive?.contentOrNull ?: "",
                baseX = poi?.get("x")?.jsonPrimitive?.doubleOrNull
                    ?: basePose?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                baseY = poi?.get("y")?.jsonPrimitive?.doubleOrNull
                    ?: basePose?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                baseYaw = poi?.get("yaw")?.jsonPrimitive?.doubleOrNull
                    ?: basePose?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                armQ = obj["arm_q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList(),
                gimbalQ = obj["gimbal_q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList(),
                timestampMs = obj["timestamp_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull,
                dwellS = obj["dwell_s"]?.jsonPrimitive?.doubleOrNull,
                transitionS = obj["transition_s"]?.jsonPrimitive?.doubleOrNull,
                ease = obj["ease"]?.jsonPrimitive?.contentOrNull
            )
        } ?: emptyList()
        return SessionDetail(sessionId = sessionId, sessionName = sessionName, frames = frames)
    }

    private fun runStatusFromWire(raw: String?): RunStatus? {
        return when (raw?.lowercase()) {
            "idle" -> RunStatus.IDLE
            "homing" -> RunStatus.HOMING
            "loading" -> RunStatus.LOADING
            "loaded" -> RunStatus.LOADED
            "running" -> RunStatus.RUNNING
            "paused" -> RunStatus.PAUSED
            "completed" -> RunStatus.COMPLETED
            "error" -> RunStatus.ERROR
            else -> null
        }
    }

    private fun updateDriverLifecycleFromState(state: JsonObject?) {
        val topicHealth = state?.get("topic_health")?.jsonObject ?: return
        val odomPubs = topicHealth["odom_pubs"]?.jsonPrimitive?.intOrNull ?: 0
        val armPubs = topicHealth["arm_state_pubs"]?.jsonPrimitive?.intOrNull ?: 0
        val gimbalPubs = topicHealth["gimbal_state_pubs"]?.jsonPrimitive?.intOrNull ?: 0
        val odomOk = topicHealth["odom_ok"]?.jsonPrimitive?.booleanOrNull ?: false
        val armOk = topicHealth["arm_ok"]?.jsonPrimitive?.booleanOrNull ?: false
        val gimbalOk = topicHealth["gimbal_ok"]?.jsonPrimitive?.booleanOrNull ?: false

        val odomReady = odomPubs > 0 || odomOk
        val armReady = armPubs > 0 || armOk
        val gimbalReady = gimbalPubs > 0 || gimbalOk
        val anyPubs = (odomPubs + armPubs + gimbalPubs) > 0
        val ready = odomReady && armReady && gimbalReady

        val next = when {
            ready -> DriverLifecycleState.READY
            !anyPubs -> DriverLifecycleState.IDLE
            pendingDriverAction == DriverAction.STOP -> DriverLifecycleState.DISMISSING
            else -> DriverLifecycleState.PREPARING
        }

        if (next != _driverLifecycle.value) {
            _driverLifecycle.value = next
        }

        if (ready) {
            finalizeDriverAction("Ready")
        } else if (!anyPubs) {
            finalizeDriverAction("All drivers dismissed")
        }
    }

    private fun finalizeDriverAction(message: String) {
        driverActionTimeoutJob?.cancel()
        driverActionTimeoutJob = null
        if (pendingDriverAction != DriverAction.NONE) {
            _recordingTopicsMessage.value = message
        }
        pendingDriverAction = DriverAction.NONE
        _recordingTopicsBusy.value = false
    }

    private fun scheduleDriverActionTimeout(action: DriverAction) {
        driverActionTimeoutJob?.cancel()
        driverActionTimeoutJob = viewModelScope.launch {
            delay(DRIVER_ACTION_TIMEOUT_MS)
            if (_recordingTopicsBusy.value && pendingDriverAction == action) {
                pendingDriverAction = DriverAction.NONE
                _recordingTopicsBusy.value = false
                _recordingTopicsError.value = if (action == DriverAction.START) {
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
        val lifecycle = _driverLifecycle.value
        if (action == DriverAction.START) {
            if (lifecycle == DriverLifecycleState.READY) {
                _recordingTopicsMessage.value = "Already ready"
                return false
            }
            if (lifecycle == DriverLifecycleState.PREPARING) {
                _recordingTopicsMessage.value = "Already preparing"
                return false
            }
        } else {
            if (lifecycle == DriverLifecycleState.IDLE) {
                _recordingTopicsMessage.value = "Already dismissed"
                return false
            }
            if (lifecycle == DriverLifecycleState.DISMISSING) {
                _recordingTopicsMessage.value = "Already dismissing"
                return false
            }
        }
        lastDriverActionMs = now
        return true
    }

    // Start a new recording session
    fun newSession(sessionName: String) {
        // Clear any existing draft data
        draftFois.clear()
        draftPois.clear()
        draftHomebase = null
        
        // Set session name (auto-generate if blank)
        val name = sessionName.ifBlank { generateSessionName() }
        activeFoiSessionName = name
        activeFoiSessionId = generateSessionId("foi")
        activePoiSessionId = generateSessionId("poi")
        
        // Update UI state
        _currentSessionName.value = name
        _currentFrameCount.value = 0
        _hasHomebase.value = false
        _draftFramesList.value = emptyList()
    }

    fun recordFrame(frameName: String, sessionName: String = "") {
        // Auto-start session if not started
        if (activeFoiSessionId == null) {
            newSession(sessionName)
        }
        
        if (activeFoiSessionId == null) return
        val index = draftFois.size
        val name = frameName.ifBlank { "frame_${index + 1}" }
        val state = robotState.value
        val thumbnail = thumbnailProvider?.invoke()
        val record = buildFoiRecord(name, "shot", index, state, thumbnail)
        draftFois.add(record)
        _currentFrameCount.value = draftFois.size
        _draftFramesList.value = draftFois.toList()  // Notify observers
        
        // Trigger timing dialog for the just-recorded frame
        _pendingFrameTiming.value = PendingFrameTiming(frameIndex = index, frameName = name)
        
        Log.d("RecomoControl", "recordFrame: recorded '$name', total frames: ${draftFois.size}, hasState: ${state != null}, hasThumbnail: ${thumbnail != null}")
    }
    
    fun setFrameTiming(frameIndex: Int, timing: FrameTiming) {
        if (frameIndex >= 0 && frameIndex < draftFois.size) {
            val oldFrame = draftFois[frameIndex]
            val updatedFrame = buildJsonObject {
                // Copy all existing fields
                oldFrame.forEach { (key, value) ->
                    val shouldSkip = (key == "dwell_s" && timing.dwellS == null) ||
                        (key == "transition_s" && timing.transitionS == null) ||
                        (key == "ease" && timing.ease == null)
                    if (!shouldSkip) {
                        put(key, value)
                    }
                }
                // Update/add timing fields
                timing.dwellS?.let { put("dwell_s", it) }
                timing.transitionS?.let { put("transition_s", it) }
                timing.ease?.let { put("ease", it) }
            }
            draftFois[frameIndex] = updatedFrame
            _draftFramesList.value = draftFois.toList()  // Notify observers
            Log.d("RecomoControl", "setFrameTiming: updated frame $frameIndex with dwell=${timing.dwellS}s, transition=${timing.transitionS}s, ease=${timing.ease}")
        }
        _pendingFrameTiming.value = null
    }
    
    fun cancelFrameTiming() {
        _pendingFrameTiming.value = null
        Log.d("RecomoControl", "cancelFrameTiming: skipped timing for frame")
    }
    
    fun editFrameTiming(frameIndex: Int) {
        if (frameIndex >= 0 && frameIndex < draftFois.size) {
            val frame = draftFois[frameIndex]
            val frameName = frame["name"]?.jsonPrimitive?.contentOrNull ?: "frame_$frameIndex"
            _pendingFrameTiming.value = PendingFrameTiming(frameIndex = frameIndex, frameName = frameName)
            Log.d("RecomoControl", "editFrameTiming: editing frame $frameIndex ($frameName)")
        }
    }

    fun addPoi(poiName: String, sessionName: String = "") {
        // Auto-start session if not started
        if (activePoiSessionId == null) {
            newSession(sessionName)
        }
        
        val sessionId = activePoiSessionId ?: return
        val index = draftPois.size
        val name = poiName.ifBlank { "poi_${index + 1}" }
        val record = buildPoiRecord(name, sessionId)
        draftPois.add(record)
    }

    fun setHomebase(name: String, sessionName: String = "") {
        val state = robotState.value
        
        // Auto-start session if not started
        if (activeFoiSessionId == null) {
            newSession(sessionName)
        }
        
        val frameName = name.ifBlank { "homebase" }
        val thumbnail = thumbnailProvider?.invoke()
        val record = buildFoiRecord(frameName, "homebase", 0, state, thumbnail)
        draftHomebase = record
        _hasHomebase.value = true
    }

    fun saveSessions() {
        if (draftFois.isEmpty() && draftPois.isEmpty() && draftHomebase == null) {
            return // Nothing to save
        }
        
        val sessionName = activeFoiSessionName ?: generateSessionName()
        
        // Build combined FOI records (homebase first, then frames)
        val allFoiRecords = mutableListOf<JsonObject>()
        draftHomebase?.let { allFoiRecords.add(it) }
        allFoiRecords.addAll(draftFois)
        
        if (allFoiRecords.isNotEmpty()) {
            val sessionId = activeFoiSessionId ?: generateSessionId("foi")
            val session = buildFoiSession(
                sessionId = sessionId,
                sessionName = sessionName,
                robotName = robotName(),
                frameId = "odom",
                category = "default",
                records = allFoiRecords
            )
            sendLibraryCmd("save", LibraryTarget.FOI_SESSION, session = session)
        }
        
        if (draftPois.isNotEmpty()) {
            val sessionId = activePoiSessionId ?: generateSessionId("poi")
            val session = buildPoiSession(
                sessionId = sessionId,
                robotName = robotName(),
                frameId = "odom",
                records = draftPois.toList()
            )
            sendLibraryCmd("save", LibraryTarget.POI_SESSION, session = session)
        }
        
        // Clear all draft data
        draftFois.clear()
        draftPois.clear()
        draftHomebase = null
        activeFoiSessionId = null
        activeFoiSessionName = null
        activePoiSessionId = null
        _currentSessionName.value = null
        _currentFrameCount.value = 0
        _hasHomebase.value = false
        _draftFramesList.value = emptyList()
    }

    fun requestLibraryList(target: LibraryTarget) {
        sendLibraryCmd("list", target)
    }

    fun requestLibraryGet(target: LibraryTarget, sessionId: String) {
        sendLibraryCmd("get", target, sessionId = sessionId)
    }

    fun requestPoiSessionDetail(sessionId: String) {
        if (sessionId.isBlank()) return
        sendLibraryCmd("get", LibraryTarget.POI_SESSION, sessionId = sessionId)
    }

    fun deleteSession(target: LibraryTarget, sessionId: String) {
        sendLibraryCmd("delete", target, sessionId = sessionId)
    }

    fun fetchMapList() {
        viewModelScope.launch {
            try {
                val cmd = buildJsonObject {
                    put("type", "MapCmd")
                    put("action", "list")
                }
                sendControl(cmd)
                Log.d("RecomoControl", "Fetching map list")
            } catch (e: Exception) {
                Log.e("RecomoControl", "fetchMapList failed: ${e.message}")
            }
        }
    }

    fun selectMap(locationId: String, mapName: String? = null, threshold: Double = 0.3) {
        viewModelScope.launch {
            try {
                val prevLocation = _selectedMap.value
                val prevAsset = _selectedMapAsset.value
                val cmd = buildJsonObject {
                    put("type", "MapCmd")
                    put("action", "select")
                    put("location_id", locationId)
                    if (!mapName.isNullOrBlank()) {
                        put("map_name", mapName)
                    }
                    put("threshold", threshold)
                }
                sendControl(cmd)
                _selectedMap.value = locationId
                _currentFoiMap.value = locationId
                if (!mapName.isNullOrBlank()) {
                    _selectedMapAsset.value = mapName
                }
                Log.d("RecomoControl", "Selected location=$locationId map=$mapName (prev=$prevLocation/$prevAsset)")
                // Gateway MapCmd select now handles localization restart directly
                // when map changes — no need for separate prepare_localization call.
            } catch (e: Exception) {
                Log.e("RecomoControl", "selectMap failed: ${e.message}")
            }
        }
    }

    fun selectMapAsset(mapName: String, threshold: Double = 0.3) {
        val locationId = selectedMap.value ?: return
        selectMap(locationId = locationId, mapName = mapName, threshold = threshold)
    }

    fun checkAndStartLocalization() {
        viewModelScope.launch {
            try {
                // Check if localization is needed
                if (!_robotPoseOk.value) {
                    prepareLocalizationNodes()
                    Log.d("RecomoControl", "Preparing localization stack from selected map context")
                } else {
                    Log.d("RecomoControl", "Localization already running")
                }
            } catch (e: Exception) {
                Log.e("RecomoControl", "checkAndStartLocalization failed: ${e.message}")
            }
        }
    }

    private fun sendSafety(action: String) {
        val cmd = buildJsonObject {
            put("type", "SafetyCmd")
            put("action", action)
        }
        sendControl(cmd)
    }

    private fun sendLibraryCmd(
        action: String,
        target: LibraryTarget,
        session: JsonObject? = null,
        sessionId: String? = null
    ) {
        val cmd = buildJsonObject {
            put("type", "LibraryCmd")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("action", action)
            put("target", target.wireName())
            if (session != null) put("session", session)
            if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
        }
        sendControl(cmd)
    }

    private fun sendRunControl(
        action: String,
        sessionId: String? = null,
        sessionName: String? = null,
        trajectory: String? = null,
        deadman: Boolean = false,
        targetIndex: Int? = null,
        skipHomebase: Boolean? = null,
        runControlMode: RunControlMode? = null
    ) {
        val cmd = buildJsonObject {
            put("type", "RunControl")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", RUN_TTL_MS)
            put("action", action)
            put("deadman", deadman)
            val skipValue = skipHomebase ?: runState.value.skipHomebase
            put("skip_homebase", skipValue)
            val modeValue = runControlMode ?: runState.value.runControlMode
            put("run_control_mode", runControlModeWireName(modeValue))
            if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
            if (!sessionName.isNullOrBlank()) put("session_name", sessionName)
            if (!trajectory.isNullOrBlank()) put("trajectory", trajectory)
            if (targetIndex != null) put("target_foi_index", targetIndex)
        }
        sendControl(cmd)
    }

    private fun sendFixedPositionCmd(action: String, extra: JsonObject? = null) {
        val cmd = buildJsonObject {
            put("type", "FixedPositionCmd")
            put("action", action)
            extra?.forEach { (key, value) -> put(key, value) }
        }
        sendControl(cmd)
    }

    private fun sendControl(cmd: JsonObject) {
        viewModelScope.launch {
            gatewayClient.sendControl(cmd)
        }
    }

    private fun sendHeartbeat() {
        val cmd = buildJsonObject {
            put("type", "Heartbeat")
        }
        sendControl(cmd)
    }

    private fun startHeartbeat() {
        if (heartbeatJob != null) return
        heartbeatJob = viewModelScope.launch {
            while (true) {
                sendHeartbeat()
                delay(100)  // Decreased from 200ms for better comm reliability
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendArmJointPosition() {
        // Send absolute targets in gateway internal order: [elbow(j4), pitch(j5), yaw(j6)]
        // Must match sendArmJog velocity ordering (A-C3 fix — was previously reversed).
        val j4 = if (isJoint4Enabled()) armJointTarget[0] else 0.0
        val j5 = armJointTarget[1]
        val j6 = armJointTarget[2]
        val values = buildJsonArray {
            add(j4)  // elbow (joint4) — gateway index 0
            add(j5)  // base_pitch (joint5) — gateway index 1
            add(j6)  // base_yaw (joint6) — gateway index 2
        }
        val cmd = buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "arm")
            put("mode", "position")
            put("values", values)
        }
        lastArmCmdMs = System.currentTimeMillis()
        sendControl(cmd)
    }

    private fun buildPoiRecord(name: String, sessionId: String): JsonObject {
        val state = robotState.value
        val mapPose = state?.get("map_pose")?.jsonObject
        val mapOk = mapPose?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
        val x: Double
        val y: Double
        val yaw: Double
        val frameId: String
        if (mapOk) {
            val pos = mapPose?.get("position")?.jsonObject
            x = pos?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0
            y = pos?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0
            yaw = mapPose?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
            frameId = mapPose?.get("frame_id")?.jsonPrimitive?.contentOrNull ?: "map"
        } else {
            val base = baseStatus.value
            x = base.x
            y = base.y
            yaw = base.yaw
            frameId = "odom"
        }
        val now = System.currentTimeMillis()
        val pose = buildJsonObject {
            put("x", x)
            put("y", y)
            put("yaw", yaw)
        }
        return buildJsonObject {
            put("name", name)
            put("robot_name", robotName())
            put("session_id", sessionId)
            put("frame_id", frameId)
            put("pose_source", if (mapOk) "map_pose" else "odom_base")
            put("poi", pose)
            put("timestamp_ms", now)
        }
    }

    private fun buildFoiRecord(
        name: String,
        sessionRole: String,
        sequenceIndex: Int,
        state: JsonObject?,
        thumbnail: String? = null,
        timing: FrameTiming? = null
    ): JsonObject {
        val base = baseStatus.value
        val now = System.currentTimeMillis()
        val poi = buildJsonObject {
            put("x", base.x)
            put("y", base.y)
            put("yaw", base.yaw)
        }
        val camera = cameraPose.value
        val cameraPoseJson = if (camera.ok) {
            buildJsonObject {
                put("x", camera.x)
                put("y", camera.y)
                put("z", camera.z)
                put("qw", camera.qw)
                put("qx", camera.qx)
                put("qy", camera.qy)
                put("qz", camera.qz)
            }
        } else {
            null
        }
        
        // Extract robot_pose from map_pose field in state (from SLAM/localization)
        val robotPoseJson = state?.get("map_pose")?.jsonObject?.let { mapPose ->
            val ok = mapPose["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            if (ok) {
                val pos = mapPose["position"]?.jsonObject
                val quat = mapPose["orientation_quat"]?.jsonObject
                val yaw = mapPose["yaw"]?.jsonPrimitive?.doubleOrNull
                val frameId = mapPose["frame_id"]?.jsonPrimitive?.contentOrNull
                if (pos != null && quat != null) {
                    buildJsonObject {
                        if (frameId != null) put("frame_id", frameId)
                        put("position", buildJsonObject {
                            put("x", pos["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                            put("y", pos["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                            put("z", pos["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        })
                        put("orientation_quat", buildJsonObject {
                            put("w", quat["w"]?.jsonPrimitive?.doubleOrNull ?: 1.0)
                            put("x", quat["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                            put("y", quat["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                            put("z", quat["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        })
                        if (yaw != null) put("yaw", yaw)
                    }
                } else null
            } else null
        }
        
        return buildJsonObject {
            put("name", name)
            put("sequence_index", sequenceIndex)
            put("session_role", sessionRole)
            put("poi", poi)
            // arm_q is always 3 elements [joint4, joint5, joint6]
            val armQ = buildArmQ(state)
            put("arm_q", armQ)
            put("gimbal_q", buildGimbalQ(state))
            if (cameraPoseJson != null) put("camera_pose", cameraPoseJson)
            if (robotPoseJson != null) put("robot_pose", robotPoseJson)
            if (thumbnail != null) put("thumbnail", thumbnail)
            // Add timing parameters if provided
            if (timing?.dwellS != null) put("dwell_s", timing.dwellS)
            if (timing?.transitionS != null) put("transition_s", timing.transitionS)
            if (timing?.ease != null) put("ease", timing.ease)
            put("timestamp_ms", now)
        }
    }

    private fun buildArmQ(state: JsonObject?): JsonArray {
        val arm = state?.get("arm")?.jsonObject
        val names = arm?.get("joint_names")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val positions = arm?.get("q")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        if (names.isEmpty() && positions.size >= 3) {
            return buildJsonArray { add(positions[0]); add(positions[1]); add(positions[2]) }
        }
        val mapped = armPositionsFromState(state)
        val fallback = if (positions.size >= 3) {
            doubleArrayOf(positions[0], positions[1], positions[2])
        } else null
        val out = mapped ?: fallback ?: doubleArrayOf(0.0, 0.0, 0.0)
        return buildJsonArray { add(out[0]); add(out[1]); add(out[2]) }
    }

    private fun buildGimbalQ(state: JsonObject?): JsonArray {
        val gimbal = state?.get("gimbal")?.jsonObject
        val q = gimbal?.get("q")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        return if (q.size >= 3) {
            buildJsonArray { add(q[0]); add(q[1]); add(q[2]) }
        } else {
            buildJsonArray { add(0.0); add(0.0); add(0.0) }
        }
    }

    private fun buildFoiSession(
        sessionId: String,
        sessionName: String,
        robotName: String,
        frameId: String,
        category: String,
        records: List<JsonObject>
    ): JsonObject {
        return buildJsonObject {
            put("type", "FoiSession")
            put("session_id", sessionId)
            put("session_name", sessionName)
            put("robot_name", robotName)
            put("frame_id", frameId)
            put("category", category)
            put("timestamp_ms", System.currentTimeMillis())
            // Add map_name if selected
            currentFoiMap.value?.let { mapName ->
                put("map_name", mapName)
            }
            put("fois", buildJsonArray {
                records.forEach { add(it) }
            })
        }
    }

    private fun buildPoiSession(
        sessionId: String,
        robotName: String,
        frameId: String,
        records: List<JsonObject>
    ): JsonObject {
        return buildJsonObject {
            put("session_id", sessionId)
            put("robot_name", robotName)
            put("frame_id", frameId)
            put("timestamp_ms", System.currentTimeMillis())
            put("pois", buildJsonArray {
                records.forEach { add(it) }
            })
        }
    }

    private fun robotName(): String {
        if (currentRobotProfile != RobotProfile.CUSTOM) {
            return currentRobotProfile.robotName
        }
        val url = _gatewayUrl.value
        if (url.isBlank()) return "recomo"
        return try {
            val host = URI(url).host
            if (host.isNullOrBlank()) "recomo" else host
        } catch (e: Exception) {
            "recomo"
        }
    }

    private fun normalizeGatewayUrl(raw: String): String {
        val input = raw.trim()
        if (input.isBlank()) return input
        return try {
            val uri = URI(input)
            val host = uri.host ?: return input
            val scheme = if (uri.scheme.isNullOrBlank()) "ws" else uri.scheme
            val port = if (uri.port == -1 || uri.port == 9091) 9077 else uri.port
            val path = if (uri.path.isNullOrBlank()) "/control" else uri.path
            val normalizedPath = if (path == "/state") "/control" else path
            "$scheme://$host:$port$normalizedPath".trimEnd('/')
        } catch (e: Exception) {
            input
        }
    }

    private fun LibraryTarget.wireName(): String {
        return when (this) {
            LibraryTarget.POI_SESSION -> "poi_session"
            LibraryTarget.FOI_SESSION -> "foi_session"
        }
    }

    private fun stepMeters(fine: Double, normal: Double, leap: Double): Double {
        return when (_stepMode.value) {
            StepMode.FINE -> fine
            StepMode.NORMAL -> normal
            StepMode.LEAP -> leap
        }
    }

    fun armStepMeters(): Double {
        return stepMeters(
            stepSettings.value.armFineM,
            stepSettings.value.armNormalM,
            stepSettings.value.armLeapM
        )
    }

    private fun gimbalStepRad(): Double {
        val stepDeg = stepMeters(
            stepSettings.value.gimbalFineDeg,
            stepSettings.value.gimbalNormalDeg,
            stepSettings.value.gimbalLeapDeg
        )
        return stepDeg * PI / 180.0
    }

    private fun armJointStepRad(): Double {
        val stepDeg = stepMeters(
            stepSettings.value.armJointFineDeg,
            stepSettings.value.armJointNormalDeg,
            stepSettings.value.armJointLeapDeg
        )
        return stepDeg * PI / 180.0
    }

    // Velocity getters for hold-to-move
    private fun chassisVelocity(): Double {
        return stepMeters(
            stepSettings.value.chassisFineVel,
            stepSettings.value.chassisNormalVel,
            stepSettings.value.chassisLeapVel
        )
    }

    private fun armVelocityRadS(): Double {
        val velDeg = stepMeters(
            stepSettings.value.armFineVel,
            stepSettings.value.armNormalVel,
            stepSettings.value.armLeapVel
        )
        return velDeg * PI / 180.0
    }

    private fun gimbalVelocityRadS(): Double {
        val velDeg = stepMeters(
            stepSettings.value.gimbalFineVelDeg,
            stepSettings.value.gimbalNormalVelDeg,
            stepSettings.value.gimbalLeapVelDeg
        )
        return velDeg * PI / 180.0
    }

    // Hold-to-move: continuous velocity commands
    fun startChassisMove(vxDir: Int, vyDir: Int, wzDir: Int) {
        val vel = chassisVelocity()
        chassisMoveJob?.cancel()
        chassisMoveJob = viewModelScope.launch {
            while (isActive) {
                sendDrive(vxDir * vel, vyDir * vel, wzDir * vel, brake = false)
                delay(50) // 20Hz update rate
            }
        }
    }

    fun stopChassisMove() {
        chassisMoveJob?.cancel()
        chassisMoveJob = null
        sendDrive(0.0, 0.0, 0.0, brake = true)
    }

    fun startArmJointMove(jointIndex: Int, direction: Int) {
        val vel = armVelocityRadS() * direction
        armMoveJob?.cancel()
        armPulseJob?.cancel()
        armMoveJob = viewModelScope.launch {
            while (isActive) {
                // Send velocity for the specific joint [joint4, joint5, joint6]
                val j4Vel = if (jointIndex == 0) vel else 0.0
                val j5Vel = if (jointIndex == 1) vel else 0.0
                val j6Vel = if (jointIndex == 2) vel else 0.0
                sendArmJog(j4Vel, j5Vel, j6Vel)
                delay(50) // 20Hz
            }
        }
    }

    fun stopArmJointMove() {
        armMoveJob?.cancel()
        armMoveJob = null
        sendArmJog(0.0, 0.0, 0.0)
    }

    private var armPulseJob: Job? = null

    fun startGimbalMove(rollDir: Int, pitchDir: Int, yawDir: Int) {
        val vel = gimbalVelocityRadS()
        gimbalMoveJob?.cancel()
        gimbalMoveJob = viewModelScope.launch {
            while (isActive) {
                nudgeGimbalVelocity(rollDir * vel, pitchDir * vel, yawDir * vel)
                delay(50) // 20Hz
            }
        }
    }

    fun stopGimbalMove() {
        gimbalMoveJob?.cancel()
        gimbalMoveJob = null
        nudgeGimbalVelocity(0.0, 0.0, 0.0)
    }

    fun sendGimbalVelocityNormalized(roll: Double, pitch: Double, yaw: Double) {
        val vel = gimbalVelocityRadS()
        nudgeGimbalVelocity(roll * vel, pitch * vel, yaw * vel)
    }

    private fun nudgeGimbalVelocity(rollVel: Double, pitchVel: Double, yawVel: Double) {
        lastGimbalCmdMs = System.currentTimeMillis()
        val cmd = buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId.getAndIncrement())
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "gimbal")
            put("mode", "velocity")
            put("values", buildJsonArray {
                add(rollVel)
                add(pitchVel)
                add(yawVel)
            })
        }
        android.util.Log.d("RecomoControlViewModel", "Sending gimbal velocity: $cmd")
        sendControl(cmd)
    }

    // Gimbal velocity control for hold-to-move behavior (per-axis)
    private var gimbalVelocityJob: kotlinx.coroutines.Job? = null
    private val gimbalVelocityVector = doubleArrayOf(0.0, 0.0, 0.0)

    fun startGimbalVelocityControl(axis: Int, direction: Double) {
        if (axis !in 0..2) return
        
        android.util.Log.d("RecomoControlViewModel", "startGimbalVelocityControl: axis=$axis direction=$direction")
        
        // Update the specific axis direction
        gimbalVelocityVector[axis] = direction
        
        android.util.Log.d("RecomoControlViewModel", "gimbalVelocityVector: [${gimbalVelocityVector[0]}, ${gimbalVelocityVector[1]}, ${gimbalVelocityVector[2]}]")
        
        // Start or continue the velocity publishing job
        if (gimbalVelocityJob?.isActive != true) {
            android.util.Log.d("RecomoControlViewModel", "Starting gimbal velocity job")
            gimbalVelocityJob = viewModelScope.launch {
                while (isActive) {
                    sendGimbalVelocityNormalized(
                        gimbalVelocityVector[0],
                        gimbalVelocityVector[1],
                        gimbalVelocityVector[2]
                    )
                    delay(50) // 20Hz update rate
                }
            }
        }
    }

    fun stopGimbalVelocityControl(axis: Int) {
        if (axis !in 0..2) return
        
        android.util.Log.d("RecomoControlViewModel", "stopGimbalVelocityControl: axis=$axis")
        
        // Only zero out the specific axis
        gimbalVelocityVector[axis] = 0.0
        
        android.util.Log.d("RecomoControlViewModel", "gimbalVelocityVector after stop: [${gimbalVelocityVector[0]}, ${gimbalVelocityVector[1]}, ${gimbalVelocityVector[2]}]")
        
        // If all axes are now zero, stop the job
        if (gimbalVelocityVector.all { it == 0.0 }) {
            android.util.Log.d("RecomoControlViewModel", "All axes zero, canceling gimbal velocity job")
            gimbalVelocityJob?.cancel()
            gimbalVelocityJob = null
        }
    }

    private fun clampArmJoint(index: Int, value: Double): Double {
        val min = if (index in armJointMinRad.indices) armJointMinRad[index] else -PI
        val max = if (index in armJointMaxRad.indices) armJointMaxRad[index] else PI
        val clamped = value.coerceIn(min, max)
        if (clamped != value) {
            Log.d("RecomoControlViewModel", "Arm joint $index clamped: $value → $clamped (limits: $min to $max)")
        }
        return clamped
    }

    private fun isJoint4Enabled(): Boolean {
        return currentRobotProfile.isProto1Family()
    }

    private fun armJointFromState(index: Int): Double? {
        val arm = robotState.value?.get("arm")?.jsonObject ?: return null
        val names = arm["joint_names"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: return null
        val q = arm["q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: return null
        if (names.isEmpty()) return q.getOrNull(index)
        names.forEachIndexed { i, name ->
            if (armJointIndexFromName(name) == index) {
                return q.getOrNull(i)
            }
        }
        return null
    }

    private fun armPositionsFromState(state: JsonObject?): DoubleArray? {
        val arm = state?.get("arm")?.jsonObject ?: return null
        val names = arm["joint_names"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val positions = arm["q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        if (positions.size < 3) return null
        if (names.isEmpty()) {
            return doubleArrayOf(positions[0], positions[1], positions[2])
        }
        val mapped = DoubleArray(3) { Double.NaN }
        names.forEachIndexed { idx, name ->
            val jointIndex = armJointIndexFromName(name) ?: return@forEachIndexed
            val value = positions.getOrNull(idx) ?: return@forEachIndexed
            mapped[jointIndex] = value
        }
        return if (mapped.any { it.isNaN() }) null else mapped
    }

    private fun armJointIndexFromName(name: String): Int? {
        val key = name.lowercase()
        return when {
            key.contains("elbow") || key.contains("joint4") -> 0
            key.contains("base_pitch") || key.contains("joint5") -> 1
            key.contains("base_yaw") || key.contains("joint6") -> 2
            else -> null
        }
    }
}
