package com.recomo.user.data.ee

/**
 * Minimal EE teleop settings for the Keypoint workspace.
 *
 * Values are velocities in m/s emitted via `ee_position_cmd` while a direction
 * button is held (or the IMU deadman is pressed). Mirrors the
 * `eePosition{Fine,Normal,Leap}Vel` subset of `:app` `StepSettings` — we
 * intentionally ignore chassis / arm-joint / gimbal step fields because the
 * Keypoint workspace only uses end-effector teleop.
 */
data class EEStepSettings(
    val fineVel: Double = 0.02,
    val normalVel: Double = 0.05,
    val leapVel: Double = 0.15
) {
    fun velocityFor(mode: EESpeedMode): Double = when (mode) {
        EESpeedMode.FINE -> fineVel
        EESpeedMode.NORMAL -> normalVel
        EESpeedMode.LEAP -> leapVel
    }
}

enum class EESpeedMode {
    FINE, NORMAL, LEAP;

    companion object {
        fun fromName(name: String?): EESpeedMode =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NORMAL
    }
}

object EEStepSettingsBounds {
    const val MIN_VEL_M_S = 0.01
    const val MAX_VEL_M_S = 0.50
}
