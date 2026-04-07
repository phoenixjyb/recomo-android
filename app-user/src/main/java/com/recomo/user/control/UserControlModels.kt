package com.recomo.user.control

data class UserBasePose(
    val x: Double,
    val y: Double,
    val yawDeg: Double
)

data class UserSafetyFlags(
    val estop: Boolean,
    val deadmanOk: Boolean,
    val commOk: Boolean
)

data class UserTrackingSummary(
    val label: String,
    val state: String
)

enum class UserConnectionStatus {
    Disconnected,
    Connecting,
    Connected
}
