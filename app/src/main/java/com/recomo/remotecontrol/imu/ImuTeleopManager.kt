package com.recomo.remotecontrol.imu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * IMU-based camera teleop manager.
 *
 * Reads TYPE_GAME_ROTATION_VECTOR sensor, computes orientation delta from
 * a calibrated neutral pose, applies low-pass filtering, dead-zone, velocity
 * clamping, and emits ImuTeleopCommand at a rate-limited cadence.
 *
 * Usage:
 *   1. Call [start] with a Context to register the sensor listener.
 *   2. Call [calibrate] when the user holds the tablet in their neutral grip.
 *   3. Call [setActive] to begin/stop emitting commands (deadman button).
 *   4. Collect from [commandFlow] to send commands to the gateway.
 *   5. Observe [state] for UI display.
 *   6. Call [stop] to unregister the sensor.
 */
class ImuTeleopManager {

    companion object {
        private const val TAG = "ImuTeleopManager"

        // Filter
        private const val FILTER_CUTOFF_HZ = 2.5f   // low-pass cutoff
        private const val SENSOR_RATE_HZ = 100f      // expected sensor sample rate

        // Dead zone: ignore deltas smaller than this (radians, ~1 degree)
        private const val DEAD_ZONE_RAD = 0.017f

        // Velocity clamps
        private const val MAX_GIMBAL_VEL_RAD_S = 1.0   // rad/s per axis
        private const val MAX_EE_VEL_M_S = 0.15         // m/s per axis

        // Send rate
        private const val SEND_RATE_HZ = 25
        private const val SEND_INTERVAL_MS = 1000L / SEND_RATE_HZ

        // Safety: auto-stop if accumulated delta exceeds this (radians, ~60 degrees)
        private const val MAX_DELTA_RAD = 1.05f

        // Mapping from orientation delta (rad) to velocity
        // These are base multipliers; user sensitivity scales them further.
        private const val GIMBAL_GAIN = 2.0    // rad delta/s → gimbal rad/s
        private const val EE_POS_GAIN = 0.3    // rad delta → m/s

        // Sign flips — set to -1.0 to invert an axis (empirical, tuned during testing)
        private const val SIGN_PITCH = 1.0
        private const val SIGN_ROLL = 1.0
        private const val SIGN_YAW = 1.0
    }

    // ── Public configuration (StateFlow for thread-safe reads + Compose recomposition) ─

    /** Overall speed multiplier (0.1 – 1.0). Default 0.5. */
    val sensitivity = MutableStateFlow(0.5f)

    /** How much goes to gimbal vs EE position (0.0 = all position, 1.0 = all gimbal). */
    val gimbalMix = MutableStateFlow(0.5f)

    // ── Output flows ───────────────────────────────────────────────────────

    /** Rate-limited commands (collect in ViewModel to dispatch to gateway). */
    private val _commandFlow = MutableSharedFlow<ImuTeleopCommand>(extraBufferCapacity = 1)
    val commandFlow: SharedFlow<ImuTeleopCommand> = _commandFlow

    /** Observable state for UI. */
    private val _state = MutableStateFlow(ImuTeleopState())
    val state: StateFlow<ImuTeleopState> = _state

    // ── Internal state ─────────────────────────────────────────────────────

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null

    private val filterPitch = LowPassFilter(FILTER_CUTOFF_HZ, SENSOR_RATE_HZ)
    private val filterRoll = LowPassFilter(FILTER_CUTOFF_HZ, SENSOR_RATE_HZ)
    private val filterYaw = LowPassFilter(FILTER_CUTOFF_HZ, SENSOR_RATE_HZ)

    /** Calibration rotation matrix (3x3 stored as float[9]). Null = not calibrated. */
    private var calMatrix: FloatArray? = null

    /** Is the deadman pressed (commands should flow)? */
    @Volatile private var active = false

    /** Coroutine scope for rate-limited sending. */
    private var sendScope: CoroutineScope? = null
    private var sendJob: Job? = null

    /** Latest filtered delta (updated on every sensor event). */
    @Volatile private var latestDelta = OrientationDelta()

    /** Tracking send rate for UI display. */
    @Volatile private var sendCount = 0
    @Volatile private var lastRateCalcMs = 0L
    @Volatile private var displayHz = 0f

    /** Number of sensor events received since start — used to gate calibration. */
    @Volatile private var sensorEventCount = 0

    /** Minimum sensor events before calibration is considered valid. */
    private val MIN_SENSOR_EVENTS_FOR_CAL = 5

    // Temp arrays — ALL accessed under synchronized(sensorLock)
    private val sensorLock = Any()
    private val tmpRotMatrix = FloatArray(9)
    private val tmpOrientation = FloatArray(3)
    private val tmpDeltaMatrix = FloatArray(9)
    private val tmpCalInverse = FloatArray(9)

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Register the rotation vector sensor. Call from onCreate/onResume.
     * @return true if sensor is available.
     */
    fun start(context: Context): Boolean {
        // Guard against double-registration (e.g. multiple onResume calls)
        if (sensorManager != null) {
            Log.d(TAG, "Already started, skipping")
            return true
        }

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm

        rotationSensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (rotationSensor == null) {
            Log.w(TAG, "TYPE_GAME_ROTATION_VECTOR not available on this device")
            _state.value = _state.value.copy(sensorAvailable = false)
            return false
        }

        sm.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        _state.value = _state.value.copy(sensorAvailable = true)

        sendScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        startSendLoop()

        Log.i(TAG, "IMU teleop sensor started")
        return true
    }

    /** Unregister sensor and stop sending. Call from onPause/onDestroy. */
    fun stop() {
        sendJob?.cancel()
        sendScope?.cancel()
        sendScope = null
        sensorManager?.unregisterListener(sensorListener)
        sensorManager = null
        rotationSensor = null
        active = false
        sensorEventCount = 0
        calMatrix = null
        sendCount = 0
        lastRateCalcMs = 0L
        displayHz = 0f
        _state.value = ImuTeleopState(sensorAvailable = false)
        Log.i(TAG, "IMU teleop sensor stopped")
    }

    // ── Controls ───────────────────────────────────────────────────────────

    /** Set the deadman state. Commands only flow when active=true. */
    fun setActive(isActive: Boolean) {
        if (isActive && !active) {
            // Require enough sensor events for a stable reading before calibration
            if (sensorEventCount < MIN_SENSOR_EVENTS_FOR_CAL) {
                Log.w(TAG, "Sensor not ready yet ($sensorEventCount/${MIN_SENSOR_EVENTS_FOR_CAL} events) — ignoring deadman press")
                _state.value = _state.value.copy(sensorWarmingUp = true)
                return
            }
            if (calMatrix == null) {
                // Auto-calibrate on first deadman press
                calibrate()
            }
        }
        active = isActive
        if (!isActive) {
            // Emit a zero command immediately to stop the robot
            _commandFlow.tryEmit(ImuTeleopCommand())
        }
        _state.value = _state.value.copy(isActive = isActive, sensorWarmingUp = false)
    }

    /** Capture current orientation as the neutral (zero) reference. */
    fun calibrate() {
        if (sensorEventCount < MIN_SENSOR_EVENTS_FOR_CAL) {
            Log.w(TAG, "Cannot calibrate — sensor not ready ($sensorEventCount/${MIN_SENSOR_EVENTS_FOR_CAL} events)")
            return
        }
        // Take a snapshot of the current rotation matrix under lock
        synchronized(sensorLock) {
            calMatrix = tmpRotMatrix.copyOf()
        }
        filterPitch.reset()
        filterRoll.reset()
        filterYaw.reset()
        latestDelta = OrientationDelta()
        _state.value = _state.value.copy(isCalibrated = true, sensorWarmingUp = false, lastDelta = OrientationDelta())
        Log.i(TAG, "Calibrated: neutral orientation captured (after $sensorEventCount sensor events)")
    }

    // ── Sensor listener ────────────────────────────────────────────────────

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

            // Track sensor readiness (C2/I6 fix)
            if (sensorEventCount < MIN_SENSOR_EVENTS_FOR_CAL) {
                sensorEventCount++
                if (sensorEventCount == MIN_SENSOR_EVENTS_FOR_CAL) {
                    Log.i(TAG, "Sensor warmed up ($MIN_SENSOR_EVENTS_FOR_CAL events received)")
                    _state.value = _state.value.copy(sensorWarmingUp = false)
                }
            } else {
                sensorEventCount++ // keep counting, no overflow risk at Int.MAX_VALUE ~= 248 days at 100Hz
            }

            // All matrix operations under one lock to prevent data races (C1 fix)
            val rawYaw: Float
            val rawPitch: Float
            val rawRoll: Float
            synchronized(sensorLock) {
                SensorManager.getRotationMatrixFromVector(tmpRotMatrix, event.values)

                val cal = calMatrix ?: return  // Not calibrated yet

                // Compute R_delta = R_cal^T * R_cur
                transposeMatrix3x3(cal, tmpCalInverse)
                multiplyMatrix3x3(tmpCalInverse, tmpRotMatrix, tmpDeltaMatrix)

                // Extract Euler angles from R_delta
                SensorManager.getOrientation(tmpDeltaMatrix, tmpOrientation)
                rawYaw = tmpOrientation[0]
                rawPitch = tmpOrientation[1]
                rawRoll = tmpOrientation[2]
            }

            // Apply low-pass filter (single-threaded: only sensor thread calls these)
            val filtPitch = filterPitch.filter(rawPitch)
            val filtRoll = filterRoll.filter(rawRoll)
            val filtYaw = filterYaw.filter(rawYaw)

            // Apply dead zone
            val dPitch = if (abs(filtPitch) > DEAD_ZONE_RAD) filtPitch else 0f
            val dRoll = if (abs(filtRoll) > DEAD_ZONE_RAD) filtRoll else 0f
            val dYaw = if (abs(filtYaw) > DEAD_ZONE_RAD) filtYaw else 0f

            // Safety: auto-stop if delta too large
            if (abs(dPitch) > MAX_DELTA_RAD || abs(dRoll) > MAX_DELTA_RAD || abs(dYaw) > MAX_DELTA_RAD) {
                if (active) {
                    Log.w(TAG, "Delta exceeds ${Math.toDegrees(MAX_DELTA_RAD.toDouble())}° — auto-stop")
                    setActive(false)
                }
                return
            }

            // Apply sign flips (TODO: verify sign constants on target tablet orientation — M4)
            latestDelta = OrientationDelta(
                pitch = (dPitch * SIGN_PITCH).toFloat(),
                roll = (dRoll * SIGN_ROLL).toFloat(),
                yaw = (dYaw * SIGN_YAW).toFloat()
            )

            _state.value = _state.value.copy(lastDelta = latestDelta, sendHz = displayHz)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            if (accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE) {
                Log.w(TAG, "Sensor accuracy dropped to $accuracy — consider recalibrating")
            }
        }
    }

    // ── Rate-limited send loop ─────────────────────────────────────────────

    private fun startSendLoop() {
        sendCount = 0
        lastRateCalcMs = System.currentTimeMillis()
        displayHz = 0f

        sendJob = sendScope?.launch {
            while (isActive) {
                delay(SEND_INTERVAL_MS)
                if (!active) continue

                val d = latestDelta
                val s = sensitivity.value.toDouble()
                val alpha = gimbalMix.value.toDouble()

                // Split delta into gimbal velocity and EE position velocity
                val gimbalPitch = alpha * GIMBAL_GAIN * s * d.pitch
                val gimbalRoll = alpha * GIMBAL_GAIN * s * d.roll
                val gimbalYaw = alpha * GIMBAL_GAIN * s * d.yaw

                val posAlpha = 1.0 - alpha
                // Mapping: pitch → forward/back (dz), roll → left/right (dx)
                // Note: yaw is consumed entirely by gimbal — no EE lateral contribution
                val eeDz = posAlpha * EE_POS_GAIN * s * d.pitch     // tilt forward → move forward
                val eeDx = posAlpha * EE_POS_GAIN * s * d.roll      // tilt right → move right
                val eeDy = 0.0  // up/down not mapped from rotation (would need accelerometer)

                // Clamp
                val cmd = ImuTeleopCommand(
                    eeDx = eeDx.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S),
                    eeDy = eeDy,
                    eeDz = eeDz.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S),
                    eeFrame = "camera",
                    gimbalRollVel = gimbalRoll.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S),
                    gimbalPitchVel = gimbalPitch.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S),
                    gimbalYawVel = gimbalYaw.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S)
                )

                _commandFlow.tryEmit(cmd)

                // Track send rate
                sendCount++
                val now = System.currentTimeMillis()
                if (now - lastRateCalcMs >= 1000) {
                    displayHz = sendCount.toFloat()
                    sendCount = 0
                    lastRateCalcMs = now
                }
            }
        }
    }

    // ── Matrix utilities (avoid allocations) ───────────────────────────────

    /** Transpose a 3x3 matrix stored as float[9]. */
    private fun transposeMatrix3x3(src: FloatArray, dst: FloatArray) {
        dst[0] = src[0]; dst[1] = src[3]; dst[2] = src[6]
        dst[3] = src[1]; dst[4] = src[4]; dst[5] = src[7]
        dst[6] = src[2]; dst[7] = src[5]; dst[8] = src[8]
    }

    /** Multiply two 3x3 matrices: dst = a * b. */
    private fun multiplyMatrix3x3(a: FloatArray, b: FloatArray, dst: FloatArray) {
        dst[0] = a[0]*b[0] + a[1]*b[3] + a[2]*b[6]
        dst[1] = a[0]*b[1] + a[1]*b[4] + a[2]*b[7]
        dst[2] = a[0]*b[2] + a[1]*b[5] + a[2]*b[8]
        dst[3] = a[3]*b[0] + a[4]*b[3] + a[5]*b[6]
        dst[4] = a[3]*b[1] + a[4]*b[4] + a[5]*b[7]
        dst[5] = a[3]*b[2] + a[4]*b[5] + a[5]*b[8]
        dst[6] = a[6]*b[0] + a[7]*b[3] + a[8]*b[6]
        dst[7] = a[6]*b[1] + a[7]*b[4] + a[8]*b[7]
        dst[8] = a[6]*b[2] + a[7]*b[5] + a[8]*b[8]
    }
}
