package com.recomo.remotecontrol.settings

data class ControllerSettings(
    val enabled: Boolean = true,
    val testMode: Boolean = false,
    val deadzone: Float = 0.18f,
    val triggerDeadzone: Float = 0.08f,
    val stickSensitivity: Float = 1.0f,
    val gimbalSensitivity: Float = 1.0f,
    val armSensitivity: Float = 1.0f,
    val invertChassisY: Boolean = true,
    val invertGimbalY: Boolean = true,
    val invertArmY: Boolean = true,
    val allowAllDof: Boolean = false
)
