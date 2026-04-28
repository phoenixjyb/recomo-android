package com.recomo.user.ui.screens.smartfollow

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.SubjectTracking
import com.recomo.common.model.TargetRoi
import com.recomo.common.network.OrinGatewayClient
import com.recomo.user.control.UserOperationMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "SmartFollowVM"

@HiltViewModel
class SmartFollowViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {

    private val _state = MutableStateFlow<SmartFollowState>(SmartFollowState.Idle)

    val uiState: StateFlow<SmartFollowUiState> = combine(
        _state,
        gatewayClient.subjectTracking,
        gatewayClient.connectionState,
        gatewayClient.robotState
    ) { state, tracking, connection, robotState ->
        val isConnected = connection is ConnectionState.Connected
        val followActive = robotState?.get("run")
            ?.jsonObject?.get("follow_active")
            ?.jsonPrimitive?.booleanOrNull ?: false
        val pncFsmState = robotState?.get("run")
            ?.jsonObject?.get("pnc_fsm_state")
            ?.jsonPrimitive?.intOrNull ?: 0
        val robotPoseOk = (robotState?.get("topic_health")?.jsonObject?.get("robot_pose_ok")
            ?: robotState?.get("robot_pose_ok"))?.jsonPrimitive?.booleanOrNull ?: false
        val currentMap = robotState?.get("maps")?.jsonObject
            ?.get("current_map")?.jsonPrimitive?.contentOrNull

        SmartFollowUiState(
            state = state,
            subjectTracking = tracking,
            isConnected = isConnected,
            followActive = followActive,
            pncFsmState = pncFsmState,
            robotPoseOk = robotPoseOk,
            currentMap = currentMap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmartFollowUiState())

    init {
        // React to tracking feedback from gateway
        viewModelScope.launch {
            gatewayClient.subjectTracking.collect { tracking ->
                if (tracking != null) processTrackingFeedback(tracking)
            }
        }
        // React to PnC FSM state changes
        viewModelScope.launch {
            gatewayClient.robotState.collect { robotState ->
                val fsmState = robotState?.get("run")
                    ?.jsonObject?.get("pnc_fsm_state")
                    ?.jsonPrimitive?.intOrNull ?: return@collect
                processPncState(fsmState)
            }
        }
        // React to disconnection
        viewModelScope.launch {
            gatewayClient.connectionState.collect { connection ->
                if (connection is ConnectionState.Disconnected || connection is ConnectionState.Error) {
                    val current = _state.value
                    if (current !is SmartFollowState.Idle && current !is SmartFollowState.Error) {
                        _state.value = SmartFollowState.Error("Disconnected")
                    }
                }
            }
        }
    }

    // ── Lifecycle ──

    fun onEnterScreen() {
        Log.d(TAG, "Entering Smart Follow screen")
        sendModeCmd(UserOperationMode.SubjectFollowing)
        _state.value = SmartFollowState.Idle
    }

    fun onExitScreen() {
        Log.d(TAG, "Exiting Smart Follow screen")
        val current = _state.value
        if (current is SmartFollowState.Following || current is SmartFollowState.Paused) {
            sendFollowCmd("stop")
        }
        sendModeCmd(UserOperationMode.Manual)
        _state.value = SmartFollowState.Idle
    }

    // ── User actions ──

    fun onRoiSelected(roi: TargetRoi) {
        Log.d(TAG, "ROI selected: (${roi.xOffset},${roi.yOffset}) ${roi.width}x${roi.height}")
        viewModelScope.launch {
            gatewayClient.sendTargetRoi(roi)
        }
        _state.value = SmartFollowState.Pending
    }

    fun startFollow(maxSpeed: Double = 0.8, followDistance: Double = 2.0) {
        val current = _state.value
        if (current !is SmartFollowState.Tracking) {
            Log.w(TAG, "Cannot start follow from state: $current")
            return
        }
        Log.d(TAG, "Starting follow: maxSpeed=$maxSpeed, followDistance=$followDistance")
        sendFollowCmd("start", maxSpeed, followDistance)
        _state.value = SmartFollowState.Following(current.confidence)
    }

    fun stopFollow() {
        Log.d(TAG, "Stopping follow")
        sendFollowCmd("stop")
        _state.value = SmartFollowState.Idle
    }

    fun pauseFollow() {
        val current = _state.value
        if (current !is SmartFollowState.Following) return
        Log.d(TAG, "Pausing follow")
        sendFollowCmd("stop")
        _state.value = SmartFollowState.Paused
    }

    fun resumeFollow() {
        val current = _state.value
        if (current !is SmartFollowState.Paused) return
        Log.d(TAG, "Resuming follow")
        sendFollowCmd("start")
        _state.value = SmartFollowState.Following()
    }

    fun reselectTarget() {
        Log.d(TAG, "Re-selecting target")
        val current = _state.value
        if (current is SmartFollowState.Following || current is SmartFollowState.Paused) {
            sendFollowCmd("stop")
        }
        _state.value = SmartFollowState.Idle
    }

    // ── Internal state machine ──

    private fun processTrackingFeedback(tracking: SubjectTracking) {
        val current = _state.value
        when (tracking.state) {
            "tracking" -> when (current) {
                is SmartFollowState.Pending -> {
                    _state.value = SmartFollowState.Tracking(tracking.confidence)
                }
                is SmartFollowState.Tracking -> {
                    _state.value = SmartFollowState.Tracking(tracking.confidence)
                }
                is SmartFollowState.Following -> {
                    _state.value = SmartFollowState.Following(tracking.confidence)
                }
                else -> Unit
            }
            "lost" -> when (current) {
                is SmartFollowState.Following -> {
                    Log.w(TAG, "Target lost while following — auto-pausing PnC")
                    sendFollowCmd("stop")
                    _state.value = SmartFollowState.LostWhileFollowing
                }
                is SmartFollowState.Pending,
                is SmartFollowState.Tracking -> {
                    _state.value = SmartFollowState.Lost
                }
                else -> Unit
            }
            "pending" -> when (current) {
                is SmartFollowState.Pending -> Unit // stay in Pending
                else -> Unit
            }
        }
    }

    private fun processPncState(fsmState: Int) {
        val current = _state.value
        when (fsmState) {
            4 -> { // Finished
                if (current is SmartFollowState.Following) {
                    _state.value = SmartFollowState.Arrived
                }
            }
            1 -> { // Running — confirm following
                if (current is SmartFollowState.Paused) {
                    // External resume (e.g., from another client)
                    _state.value = SmartFollowState.Following()
                }
            }
        }
    }

    // ── Gateway commands ──

    private fun sendFollowCmd(
        action: String,
        maxSpeed: Double = 0.8,
        followDistance: Double = 2.0
    ) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "FollowCmd")
                    put("action", action)
                    if (action == "start") {
                        put("max_speed", maxSpeed.coerceIn(0.1, 2.0))
                        put("follow_distance", followDistance.coerceIn(0.5, 10.0))
                    }
                }
            )
        }
    }

    private fun sendModeCmd(mode: UserOperationMode) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "ModeCmd")
                    put("mode", mode.wireValue)
                }
            )
        }
    }
}
