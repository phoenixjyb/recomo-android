package com.recomo.user.control

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.data.ee.EEStepSettings
import com.recomo.user.data.ee.EESpeedMode
import com.recomo.user.imu.ImuTeleopState
import com.recomo.user.imu.UserImuTeleopManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Input source for EE teleop — button grid or IMU tilt. */
enum class EETeleopMode { BUTTONS, IMU }

/**
 * UI state for the EE teleop panel on the Keypoint workspace.
 *
 * - `ikRunning` mirrors the gateway's `ee_ik_controller.running` broadcast —
 *   `ee_position_cmd` only has effect while this is true.
 * - `ikBusy` is a transient flag set while a start/stop request is in flight.
 * - `dryRun` defaults to **true** (known IK velocity amplification bug).
 * - `teleopMode` switches between button grid and IMU tilt sources.
 */
data class UserEEPositionUiState(
    val connected: Boolean = false,
    val ikRunning: Boolean = false,
    val ikBusy: Boolean = false,
    val dryRun: Boolean = true,
    val speedMode: EESpeedMode = EESpeedMode.NORMAL,
    val stepSettings: EEStepSettings = EEStepSettings(),
    val teleopMode: EETeleopMode = EETeleopMode.BUTTONS
) {
    val currentVelocityMs: Double get() = stepSettings.velocityFor(speedMode)
}

/**
 * ViewModel for the EE teleop panel shared by the Keypoint workspace.
 *
 * Responsibilities:
 *  - Start / stop the gateway-side IK controller (`ee_ik_controller_cmd`).
 *  - Drive button-mode teleop: while held, stream `ee_position_cmd` at 20 Hz.
 *  - Expose a one-shot `sendEEPositionCommand` for IMU-driven teleop (P6).
 *  - **Guarantee IK release on teardown** — `onCleared` fires a best-effort
 *    stop via `GlobalScope` so a crashed / swiped-away tab does not leave the
 *    arm locked under `ArmOwner = IK_CONTROLLER`. The workspace composable
 *    should also call [stopIkController] explicitly in a `DisposableEffect`
 *    for deterministic release on normal exit (belt-and-braces).
 */
@HiltViewModel
class UserEEPositionViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gatewayClient: OrinGatewayClient,
    private val settingsRepository: UserSettingsRepository
) : ViewModel() {

    private val ikBusy = MutableStateFlow(false)
    private val teleopMode = MutableStateFlow(EETeleopMode.BUTTONS)

    // ── IMU subsystem ────────────────────────────────────────────────────────
    private val imuManager = UserImuTeleopManager()

    /** Exposed for the panel to render deadman / calibration / delta HUD. */
    val imuState: StateFlow<ImuTeleopState> = imuManager.state
    val imuSensitivity: StateFlow<Float> = imuManager.sensitivity
    val imuGimbalMix: StateFlow<Float> = imuManager.gimbalMix

    /** Monotonic seq_id for JogCmd — matches :app behaviour. */
    private val jogSeqId = AtomicLong(1L)

    /** IMU gimbal JogCmd TTL in ms — matches :app `IMU_GIMBAL_TTL_MS`. */
    private val imuGimbalTtlMs = 120

    private var imuInitialized = false

    private val connected: StateFlow<Boolean> = gatewayClient.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val ikRunningFromGateway: StateFlow<Boolean> = gatewayClient.robotState
        .map { state: JsonObject? ->
            state?.get("ee_ik_controller")?.jsonObject
                ?.get("running")?.jsonPrimitive?.booleanOrNull ?: false
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<UserEEPositionUiState> = combine(
        connected,
        ikRunningFromGateway,
        ikBusy,
        settingsRepository.eePositionSpeedMode,
        settingsRepository.eePositionDryRun,
        teleopMode
    ) { values ->
        UserEEPositionUiState(
            connected = values[0] as Boolean,
            ikRunning = values[1] as Boolean,
            ikBusy = values[2] as Boolean,
            speedMode = values[3] as EESpeedMode,
            dryRun = values[4] as Boolean,
            stepSettings = EEStepSettings(),
            teleopMode = values[5] as EETeleopMode
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserEEPositionUiState())

    /** Active 20 Hz jog loop for button-mode teleop. */
    private var jogJob: Job? = null

    /** Collector for IMU command flow — cancelled on stop or mode switch. */
    private var imuCollectorJob: Job? = null

    // ── IK controller lifecycle ──────────────────────────────────────────────

    fun startIkController() {
        viewModelScope.launch {
            ikBusy.value = true
            try {
                val dry = uiState.value.dryRun
                gatewayClient.sendControl(
                    buildJsonObject {
                        put("type", "ee_ik_controller_cmd")
                        put("action", "start")
                        put("dry_run", dry)
                    }
                )
            } finally {
                ikBusy.value = false
            }
        }
    }

    fun stopIkController() {
        // Also stop any in-flight jog loop so we don't keep streaming
        // ee_position_cmd after the controller is gone.
        stopEEJog()
        viewModelScope.launch {
            ikBusy.value = true
            try {
                gatewayClient.sendControl(
                    buildJsonObject {
                        put("type", "ee_ik_controller_cmd")
                        put("action", "stop")
                    }
                )
            } finally {
                ikBusy.value = false
            }
        }
    }

    fun setDryRun(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateEEPositionDryRun(enabled)
        }
    }

    fun setSpeedMode(mode: EESpeedMode) {
        viewModelScope.launch {
            settingsRepository.updateEEPositionSpeedMode(mode)
        }
    }

    // ── Button-mode jog ──────────────────────────────────────────────────────

    /**
     * Start a 20 Hz loop that streams `ee_position_cmd` with the given
     * velocity vector. Intended for press-and-hold directional buttons.
     * The velocity magnitude is taken from `currentVelocityMs`; the caller
     * only passes axis directions (dx/dy/dz as `-1.0..+1.0` or axis-aligned).
     *
     * Gate: if the IK controller is not running, the loop no-ops and warns
     * via a best-effort start attempt (future enhancement — for now, caller
     * should ensure IK is running before enabling buttons).
     */
    fun startEEJog(dxDir: Double, dyDir: Double, dzDir: Double, frame: String) {
        stopEEJog()
        jogJob = viewModelScope.launch {
            while (isActive) {
                val state = uiState.value
                if (!state.connected || !state.ikRunning) {
                    // Bail silently — UI should already prevent this.
                    break
                }
                val v = state.currentVelocityMs
                sendEEPositionCommandInternal(dxDir * v, dyDir * v, dzDir * v, frame)
                delay(50)
            }
        }
    }

    fun stopEEJog() {
        jogJob?.cancel()
        jogJob = null
    }

    // ── Button-mode gimbal jog ──────────────────────────────────────────────

    private var gimbalJogJob: Job? = null

    /**
     * Start a 20 Hz gimbal velocity JogCmd loop for button-mode yaw/pitch.
     * Gimbal jog does NOT require IK controller — works independently.
     */
    fun startGimbalJog(rollDir: Double, pitchDir: Double, yawDir: Double) {
        stopGimbalJog()
        val gimbalVelRadS = 0.8  // ~46 deg/s — comfortable manual jog speed
        gimbalJogJob = viewModelScope.launch {
            while (isActive) {
                if (!connected.value) break
                sendGimbalVelocityCommand(
                    roll = rollDir * gimbalVelRadS,
                    pitch = pitchDir * gimbalVelRadS,
                    yaw = yawDir * gimbalVelRadS
                )
                delay(50)
            }
        }
    }

    fun stopGimbalJog() {
        gimbalJogJob?.cancel()
        gimbalJogJob = null
    }

    /**
     * One-shot `ee_position_cmd` send for IMU-driven teleop (P6). The caller
     * passes absolute velocities in m/s.
     */
    fun sendEEPositionCommand(dx: Double, dy: Double, dz: Double, frame: String) {
        viewModelScope.launch {
            sendEEPositionCommandInternal(dx, dy, dz, frame)
        }
    }

    private suspend fun sendEEPositionCommandInternal(
        dx: Double,
        dy: Double,
        dz: Double,
        frame: String
    ) {
        gatewayClient.sendControl(
            buildJsonObject {
                put("type", "ee_position_cmd")
                put("dx", dx)
                put("dy", dy)
                put("dz", dz)
                put("frame", frame)
            }
        )
    }

    /**
     * Fire a single gimbal velocity JogCmd. Schema matches `:app`
     * `initImuTeleop` branch: target=gimbal, mode=velocity, values=[roll,pitch,yaw],
     * deadman=true, ttl_ms=120. Gimbal jog does NOT require IK controller.
     */
    private suspend fun sendGimbalVelocityCommand(
        roll: Double,
        pitch: Double,
        yaw: Double
    ) {
        gatewayClient.sendControl(
            buildJsonObject {
                put("type", "JogCmd")
                put("seq_id", jogSeqId.getAndIncrement())
                put("timestamp_ms", System.currentTimeMillis())
                put("ttl_ms", imuGimbalTtlMs)
                put("deadman", true)
                put("target", "gimbal")
                put("mode", "velocity")
                put("values", buildJsonArray {
                    add(roll)
                    add(pitch)
                    add(yaw)
                })
            }
        )
    }

    // ── Mode switching ───────────────────────────────────────────────────────

    fun setTeleopMode(mode: EETeleopMode) {
        if (teleopMode.value == mode) return
        // Releasing the previous source must kill any in-flight motion.
        when (teleopMode.value) {
            EETeleopMode.BUTTONS -> { stopEEJog(); stopGimbalJog() }
            EETeleopMode.IMU -> setImuActive(false)
        }
        teleopMode.value = mode
    }

    // ── IMU teleop ───────────────────────────────────────────────────────────

    /**
     * Start the IMU sensor subsystem and the command collector. Idempotent.
     * Called by the panel composable when IMU mode is selected.
     */
    fun initImuTeleop() {
        if (imuInitialized) return
        if (!imuManager.start(appContext)) return
        imuInitialized = true
        imuCollectorJob?.cancel()
        imuCollectorJob = viewModelScope.launch {
            imuManager.commandFlow.collect { cmd ->
                // EE only flows while IK controller is running (matches :app guard).
                if (cmd.hasEeComponent && ikRunningFromGateway.value) {
                    sendEEPositionCommandInternal(cmd.eeDx, cmd.eeDy, cmd.eeDz, cmd.eeFrame)
                }
                // Gimbal jog is independent of IK.
                if (cmd.hasGimbalComponent) {
                    sendGimbalVelocityCommand(cmd.gimbalRollVel, cmd.gimbalPitchVel, cmd.gimbalYawVel)
                }
            }
        }
    }

    /** Stop the IMU sensor subsystem. Safe to call even if not initialized. */
    fun stopImuTeleop() {
        imuCollectorJob?.cancel()
        imuCollectorJob = null
        if (imuInitialized) {
            imuManager.stop()
            imuInitialized = false
        }
    }

    /** Deadman control for IMU mode — safe on release (zero command emitted). */
    fun setImuActive(active: Boolean) {
        imuManager.setActive(active)
    }

    /** Explicit recalibration — user taps "Calibrate" button. */
    fun calibrateImu() {
        imuManager.calibrate()
    }

    fun setImuSensitivity(value: Float) {
        imuManager.sensitivity.value = value.coerceIn(0.1f, 1.0f)
    }

    fun setImuGimbalMix(value: Float) {
        imuManager.gimbalMix.value = value.coerceIn(0.0f, 1.0f)
    }

    // ── Teardown ─────────────────────────────────────────────────────────────

    /**
     * Best-effort IK release when the ViewModel is destroyed. `viewModelScope`
     * is cancelled before `onCleared` returns, so we use `GlobalScope` for a
     * detached fire-and-forget. The UI also calls `stopIkController` via
     * `DisposableEffect` (P4) for deterministic release on normal exit.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        stopEEJog()
        stopGimbalJog()
        stopImuTeleop()
        // Snapshot whether IK was running — if not, skip the stop.
        val wasRunning = ikRunningFromGateway.value
        if (wasRunning) {
            GlobalScope.launch {
                withContext(NonCancellable) {
                    try {
                        gatewayClient.sendControl(
                            buildJsonObject {
                                put("type", "ee_ik_controller_cmd")
                                put("action", "stop")
                            }
                        )
                    } catch (_: Throwable) {
                        // Swallow — we're already tearing down.
                    }
                }
            }
        }
        super.onCleared()
    }
}
