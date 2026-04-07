package com.recomo.remotecontrol.settings

data class StepSettings(
    // Position step sizes (for tap)
    val chassisFineM: Double = 0.02,
    val chassisNormalM: Double = 0.05,
    val chassisLeapM: Double = 0.15,
    val armFineM: Double = 0.01,
    val armNormalM: Double = 0.02,
    val armLeapM: Double = 0.05,
    val armJointFineDeg: Double = 1.0,
    val armJointNormalDeg: Double = 5.0,
    val armJointLeapDeg: Double = 10.0,
    val gimbalFineDeg: Double = 1.0,
    val gimbalNormalDeg: Double = 5.0,
    val gimbalLeapDeg: Double = 10.0,
    val yawFineDeg: Double = 5.0,
    val yawNormalDeg: Double = 10.0,
    val yawLeapDeg: Double = 20.0,
    // Velocity values (for hold) - m/s for chassis, deg/s for arm/gimbal
    val chassisFineVel: Double = 0.05,
    val chassisNormalVel: Double = 0.15,
    val chassisLeapVel: Double = 0.40,
    val armFineVel: Double = 9.0,
    val armNormalVel: Double = 25.0,
    val armLeapVel: Double = 50.0,
    val gimbalFineVelDeg: Double = 10.0,
    val gimbalNormalVelDeg: Double = 30.0,
    val gimbalLeapVelDeg: Double = 60.0,
    // EE Position velocities (m/s) - for hold-to-move
    val eePositionFineVel: Double = 0.02,
    val eePositionNormalVel: Double = 0.05,
    val eePositionLeapVel: Double = 0.15
)

object StepSettingsBounds {
    // Position bounds
    const val CHASSIS_MIN_M = 0.005
    const val CHASSIS_MAX_M = 0.30
    const val ARM_MIN_M = 0.005
    const val ARM_MAX_M = 0.10
    const val ARM_JOINT_MIN_DEG = 0.5
    const val ARM_JOINT_MAX_DEG = 30.0
    const val GIMBAL_MIN_DEG = 0.5
    const val GIMBAL_MAX_DEG = 30.0
    const val YAW_MIN_DEG = 1.0
    const val YAW_MAX_DEG = 45.0
    // Velocity bounds
    const val CHASSIS_MIN_VEL = 0.01
    const val CHASSIS_MAX_VEL = 2.00
    const val ARM_MIN_VEL = 1.0
    const val ARM_MAX_VEL = 90.0
    const val GIMBAL_MIN_VEL_DEG = 1.0
    const val GIMBAL_MAX_VEL_DEG = 90.0

    // EE Position velocity bounds (m/s)
    const val EE_POSITION_MIN_VEL = 0.01
    const val EE_POSITION_MAX_VEL = 0.50
}
