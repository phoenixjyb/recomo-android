package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.ui.screens.touch.TouchControlSpeedMode
import com.recomo.user.ui.screens.touch.TouchControlSpeedProfile
import com.recomo.user.ui.screens.touch.TouchControlWorkspaceState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.PI

@HiltViewModel
class UserTouchControlViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient,
    private val settingsRepo: UserSettingsRepository
) : ViewModel() {
    private val seqId = AtomicLong(1L)

    private val speedMode = MutableStateFlow(TouchControlSpeedMode.Normal)
    private val showGrid = MutableStateFlow(false)

    private var chassisMoveJob: Job? = null
    private var armMoveJob: Job? = null
    private var gimbalVelocityJob: Job? = null
    private val gimbalVelocityVector = doubleArrayOf(0.0, 0.0, 0.0)

    private val gatewaySnapshot = combine(
        gatewayClient.connectionState,
        gatewayClient.robotState
    ) { connectionState, robotState ->
        connectionState to robotState
    }

    val workspaceState: StateFlow<TouchControlWorkspaceState> = combine(
        gatewaySnapshot,
        speedMode,
        showGrid,
        settingsRepo.speedTierOverrides
    ) { gateway, currentSpeedMode, grid, speedOverrides ->
        val connectionState = gateway.first
        val robotState = gateway.second
        val safety = robotState?.get("safety")?.jsonObject
        val base = robotState?.get("base")?.jsonObject
        val arm = robotState?.get("arm")?.jsonObject
        val gimbal = robotState?.get("gimbal")?.jsonObject

        // Merge user-configured speed over the enum's built-in default.
        // User stores chassis m/s; arm and gimbal scale proportionally
        // from the default ratio so the user tunes one slider per tier.
        val userMps: Float = when (currentSpeedMode) {
            TouchControlSpeedMode.Slow -> speedOverrides.slowMps
            TouchControlSpeedMode.Normal -> speedOverrides.normalMps
            TouchControlSpeedMode.Fast -> speedOverrides.fastMps
        }
        val defaultProfile = currentSpeedMode.profile
        val effectiveProfile = if (userMps > 0f) {
            val scale = userMps.toDouble() / defaultProfile.baseVelocityMps
            TouchControlSpeedProfile(
                baseVelocityMps = userMps.toDouble(),
                armVelocityRadS = defaultProfile.armVelocityRadS * scale,
                gimbalVelocityRadS = defaultProfile.gimbalVelocityRadS * scale
            )
        } else {
            defaultProfile
        }

        TouchControlWorkspaceState(
            connected = connectionState is ConnectionState.Connected,
            connecting = connectionState is ConnectionState.Connecting,
            speedMode = currentSpeedMode,
            effectiveProfile = effectiveProfile,
            showGrid = grid,
            estopActive = safety?.get("estop")?.jsonPrimitive?.booleanOrNull ?: false,
            freezeAllActive = safety?.get("freeze_all")?.jsonPrimitive?.booleanOrNull ?: false,
            estopCooldownRemainingMs = safety?.get("estop_cooldown_remaining_ms")?.jsonPrimitive?.longOrNull ?: 0L,
            deadmanOk = safety?.get("deadman_ok")?.jsonPrimitive?.booleanOrNull ?: false,
            commOk = safety?.get("comm_ok")?.jsonPrimitive?.booleanOrNull ?: false,
            baseYawDeg = base?.get("yaw")?.jsonPrimitive?.doubleOrNull?.times(180.0 / PI),
            armJointAnglesDeg = reorderArmQ(arm),
            gimbalAnglesDeg = gimbal?.get("q")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
                ?: emptyList()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TouchControlWorkspaceState()
    )

    fun setSpeedMode(mode: TouchControlSpeedMode) {
        speedMode.value = mode
    }

    fun toggleGrid() {
        showGrid.value = !showGrid.value
    }

    fun estop() {
        sendSafety("estop")
    }

    fun clearEstop() {
        sendSafety("clear_estop")
    }

    fun freezeAll() {
        sendSafety("freeze_all")
    }

    fun unfreezeAll() {
        sendSafety("unfreeze_all")
    }

    fun sendChassisStep(dxDir: Int, dyDir: Int) {
        val profile = workspaceState.value.effectiveProfile
        pulseChassis(
            vx = dxDir.coerceIn(-1, 1) * profile.baseVelocityMps,
            vy = dyDir.coerceIn(-1, 1) * profile.baseVelocityMps,
            wz = 0.0
        )
    }

    fun sendChassisRotate(dir: Int) {
        val profile = workspaceState.value.effectiveProfile
        pulseChassis(
            vx = 0.0,
            vy = 0.0,
            wz = dir.coerceIn(-1, 1) * profile.baseVelocityMps
        )
    }

    fun startChassisMove(vxDir: Int, vyDir: Int, wzDir: Int) {
        val profile = workspaceState.value.effectiveProfile
        chassisMoveJob?.cancel()
        chassisMoveJob = viewModelScope.launch {
            while (isActive) {
                sendDrive(
                    vx = vxDir * profile.baseVelocityMps,
                    vy = vyDir * profile.baseVelocityMps,
                    wz = wzDir * profile.baseVelocityMps,
                    brake = false
                )
                delay(COMMAND_INTERVAL_MS)
            }
        }
    }

    fun stopChassisMove() {
        chassisMoveJob?.cancel()
        chassisMoveJob = null
        viewModelScope.launch {
            sendDrive(vx = 0.0, vy = 0.0, wz = 0.0, brake = true)
        }
    }

    fun nudgeArmJointPositive(index: Int) {
        pulseArmJoint(index, 1)
    }

    fun nudgeArmJointNegative(index: Int) {
        pulseArmJoint(index, -1)
    }

    fun startArmJointMove(jointIndex: Int, direction: Int) {
        val velocity = workspaceState.value.effectiveProfile.armVelocityRadS * direction.coerceIn(-1, 1)
        armMoveJob?.cancel()
        armMoveJob = viewModelScope.launch {
            while (isActive) {
                sendArmJogForJoint(jointIndex, velocity)
                delay(COMMAND_INTERVAL_MS)
            }
        }
    }

    fun stopArmJointMove() {
        armMoveJob?.cancel()
        armMoveJob = null
        viewModelScope.launch {
            sendArmJog(j4 = 0.0, j5 = 0.0, j6 = 0.0)
        }
    }

    fun nudgeGimbalPositive(axis: Int) {
        pulseGimbal(axis, 1.0)
    }

    fun nudgeGimbalNegative(axis: Int) {
        pulseGimbal(axis, -1.0)
    }

    fun startGimbalVelocityControl(axis: Int, direction: Double) {
        if (axis !in 0..2) return
        gimbalVelocityVector[axis] = direction.coerceIn(-1.0, 1.0)
        if (gimbalVelocityJob?.isActive == true) return
        gimbalVelocityJob = viewModelScope.launch {
            while (isActive) {
                sendGimbalVelocityNormalized(
                    roll = gimbalVelocityVector[0],
                    pitch = gimbalVelocityVector[1],
                    yaw = gimbalVelocityVector[2]
                )
                delay(COMMAND_INTERVAL_MS)
            }
        }
    }

    fun stopGimbalVelocityControl(axis: Int) {
        if (axis !in 0..2) return
        gimbalVelocityVector[axis] = 0.0
        if (gimbalVelocityVector.all { it == 0.0 }) {
            gimbalVelocityJob?.cancel()
            gimbalVelocityJob = null
        }
    }

    fun sendArmHomingPose() {
        viewModelScope.launch {
            val holdGimbal = workspaceState.value.gimbalAnglesDeg.takeIf { it.size >= 3 }?.take(3)
                ?: GIMBAL_HOMING_Q_DEG
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "ArmHomeCmd")
                    put("gimbal_q", buildJsonArray {
                        holdGimbal.forEach { add(JsonPrimitive(it)) }
                    })
                }
            )
        }
    }

    fun sendGimbalHomingPose() {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "JogCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("ttl_ms", 150)
                    put("deadman", true)
                    put("target", "gimbal")
                    put("mode", "position")
                    put("values", buildJsonArray {
                        GIMBAL_HOMING_Q_DEG.forEach { add(JsonPrimitive(it)) }
                    })
                }
            )
        }
    }

    private fun pulseChassis(vx: Double, vy: Double, wz: Double) {
        viewModelScope.launch {
            sendDrive(vx = vx, vy = vy, wz = wz, brake = false)
            delay(PULSE_DURATION_MS)
            sendDrive(vx = 0.0, vy = 0.0, wz = 0.0, brake = true)
        }
    }

    private fun pulseArmJoint(jointIndex: Int, direction: Int) {
        val velocity = workspaceState.value.effectiveProfile.armVelocityRadS * direction.coerceIn(-1, 1)
        viewModelScope.launch {
            sendArmJogForJoint(jointIndex, velocity)
            delay(PULSE_DURATION_MS)
            sendArmJog(j4 = 0.0, j5 = 0.0, j6 = 0.0)
        }
    }

    private fun pulseGimbal(axis: Int, direction: Double) {
        if (axis !in 0..2) return
        viewModelScope.launch {
            val vector = DoubleArray(3)
            vector[axis] = direction.coerceIn(-1.0, 1.0)
            sendGimbalVelocityNormalized(
                roll = vector[0],
                pitch = vector[1],
                yaw = vector[2]
            )
            delay(PULSE_DURATION_MS)
            sendGimbalVelocityNormalized(roll = 0.0, pitch = 0.0, yaw = 0.0)
        }
    }

    private suspend fun sendArmJogForJoint(jointIndex: Int, velocity: Double) {
        sendArmJog(
            j4 = if (jointIndex == 0) velocity else 0.0,
            j5 = if (jointIndex == 1) velocity else 0.0,
            j6 = if (jointIndex == 2) velocity else 0.0
        )
    }

    private suspend fun sendDrive(vx: Double, vy: Double, wz: Double, brake: Boolean) {
        gatewayClient.sendControl(
            buildJsonObject {
                put("type", "DriveCmd")
                put("seq_id", seqId.getAndIncrement())
                put("timestamp_ms", System.currentTimeMillis())
                put("ttl_ms", 150)
                put("deadman", true)
                put("vx", vx)
                put("vy", vy)
                put("wz", wz)
                put("brake", brake)
                put("smooth", true)
            }
        )
    }

    private suspend fun sendArmJog(j4: Double, j5: Double, j6: Double) {
        gatewayClient.sendControl(
            buildJsonObject {
                put("type", "JogCmd")
                put("seq_id", seqId.getAndIncrement())
                put("timestamp_ms", System.currentTimeMillis())
                put("ttl_ms", 150)
                put("deadman", true)
                put("target", "arm")
                put("mode", "velocity")
                put("values", buildJsonArray {
                    add(JsonPrimitive(j4))
                    add(JsonPrimitive(j5))
                    add(JsonPrimitive(j6))
                })
            }
        )
    }

    private suspend fun sendGimbalVelocityNormalized(roll: Double, pitch: Double, yaw: Double) {
        val vel = workspaceState.value.effectiveProfile.gimbalVelocityRadS
        gatewayClient.sendControl(
            buildJsonObject {
                put("type", "JogCmd")
                put("seq_id", seqId.getAndIncrement())
                put("timestamp_ms", System.currentTimeMillis())
                put("ttl_ms", 150)
                put("deadman", true)
                put("target", "gimbal")
                put("mode", "velocity")
                put("values", buildJsonArray {
                    add(JsonPrimitive(roll * vel))
                    add(JsonPrimitive(pitch * vel))
                    add(JsonPrimitive(yaw * vel))
                })
            }
        )
    }

    private fun sendSafety(action: String) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "SafetyCmd")
                    put("action", action)
                }
            )
        }
    }

    /**
     * Reorder raw arm.q (driver order: [yaw, pitch, elbow]) to UI order [J4=elbow, J5=pitch, J6=yaw].
     * Uses joint_names for reliable mapping; falls back to index swap if names are absent.
     */
    private fun reorderArmQ(arm: kotlinx.serialization.json.JsonObject?): List<Double> {
        val positions = arm?.get("q")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull?.let(Math::toDegrees) }
            ?: return emptyList()
        if (positions.size < 3) return positions
        val names = arm.get("joint_names")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        if (names.size >= 3) {
            val result = DoubleArray(3)
            names.forEachIndexed { idx, name ->
                val target = when {
                    name.contains("elbow", true) || name.contains("joint4", true) -> 0
                    name.contains("joint5", true) || name.contains("base_pitch", true) -> 1
                    name.contains("joint6", true) || name.contains("base_yaw", true) -> 2
                    else -> return@forEachIndexed
                }
                positions.getOrNull(idx)?.let { result[target] = it }
            }
            return result.toList()
        }
        // Fallback: driver publishes [yaw, pitch, elbow], swap to [elbow, pitch, yaw]
        return listOf(positions[2], positions[1], positions[0])
    }

    companion object {
        private const val COMMAND_INTERVAL_MS = 50L
        private const val PULSE_DURATION_MS = 180L
        private val ARM_HOMING_Q_DEG = listOf(0.0, 0.0, 0.0)
        private val GIMBAL_HOMING_Q_DEG = listOf(0.0, 0.0, 0.0)
    }
}
