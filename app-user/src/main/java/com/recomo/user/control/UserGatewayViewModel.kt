package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.PI
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@HiltViewModel
class UserGatewayViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {
    val connectionStatus: StateFlow<UserConnectionStatus> = gatewayClient.connectionState
        .map { state ->
            when (state) {
                is ConnectionState.Connected -> UserConnectionStatus.Connected
                is ConnectionState.Connecting -> UserConnectionStatus.Connecting
                is ConnectionState.Disconnected, is ConnectionState.Error -> UserConnectionStatus.Disconnected
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserConnectionStatus.Disconnected)

    val stateHz: StateFlow<Double?> = gatewayClient.stateHz
        .map { hz -> hz.takeIf { it > 0.0 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lastStateAgeMs: StateFlow<Long?> = gatewayClient.lastStateAtMs
        .map { timestamp ->
            timestamp.takeIf { it > 0L }?.let { System.currentTimeMillis() - it }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val basePose: StateFlow<UserBasePose?> = gatewayClient.robotState
        .map { state ->
            val base = state?.get("base")?.jsonObject ?: return@map null
            val x = base["x"]?.jsonPrimitive?.doubleOrNull ?: return@map null
            val y = base["y"]?.jsonPrimitive?.doubleOrNull ?: return@map null
            val yawRad = base["yaw"]?.jsonPrimitive?.doubleOrNull ?: return@map null
            UserBasePose(
                x = x,
                y = y,
                yawDeg = yawRad * 180.0 / PI
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val safetyFlags: StateFlow<UserSafetyFlags?> = gatewayClient.robotState
        .map { state ->
            val safety = state?.get("safety")?.jsonObject ?: return@map null
            UserSafetyFlags(
                estop = safety["estop"]?.jsonPrimitive?.booleanOrNull ?: false,
                deadmanOk = safety["deadman_ok"]?.jsonPrimitive?.booleanOrNull ?: false,
                commOk = safety["comm_ok"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasMapPose: StateFlow<Boolean> = gatewayClient.robotState
        .map { state ->
            val mapPose = state?.get("map_pose")?.jsonObject ?: return@map false
            mapPose["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val trackingSummary: StateFlow<UserTrackingSummary?> = gatewayClient.subjectTracking
        .map { tracking ->
            tracking?.let {
                UserTrackingSummary(
                    label = "Tracking",
                    state = "${it.state} ${(it.confidence * 100).toInt()}%"
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun connect(baseUrl: String) {
        viewModelScope.launch {
            gatewayClient.connect(baseUrl)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            gatewayClient.disconnect()
        }
    }

    fun isConnected(): Boolean = gatewayClient.isConnected()
}
