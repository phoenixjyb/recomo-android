package com.recomo.user.imu

/**
 * Orientation delta from the calibrated neutral pose, in radians.
 *
 * Sign convention (tablet held in landscape, screen facing user):
 *   pitch > 0  → tablet top tilted away (forward tilt)
 *   roll  > 0  → tablet tilted right
 *   yaw   > 0  → tablet rotated clockwise (from above)
 *
 * Ported from `:app` `com.recomo.remotecontrol.imu.OrientationDelta`.
 */
data class OrientationDelta(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val yaw: Float = 0f
)

/**
 * Command produced by [UserImuTeleopManager] each tick.
 * Split into EE position velocity (camera frame) and gimbal angular velocity.
 */
data class ImuTeleopCommand(
    /** EE position velocity — camera frame, m/s */
    val eeDx: Double = 0.0,
    val eeDy: Double = 0.0,
    val eeDz: Double = 0.0,
    val eeFrame: String = "camera",
    /** Gimbal angular velocity, rad/s */
    val gimbalRollVel: Double = 0.0,
    val gimbalPitchVel: Double = 0.0,
    val gimbalYawVel: Double = 0.0
) {
    val hasEeComponent: Boolean
        get() = eeDx != 0.0 || eeDy != 0.0 || eeDz != 0.0

    val hasGimbalComponent: Boolean
        get() = gimbalRollVel != 0.0 || gimbalPitchVel != 0.0 || gimbalYawVel != 0.0
}

/**
 * Observable state of the IMU teleop subsystem (for UI display).
 */
data class ImuTeleopState(
    val isActive: Boolean = false,
    val isCalibrated: Boolean = false,
    val sensorAvailable: Boolean = false,
    val sensorWarmingUp: Boolean = false,
    val lastDelta: OrientationDelta = OrientationDelta(),
    val sendHz: Float = 0f
)
