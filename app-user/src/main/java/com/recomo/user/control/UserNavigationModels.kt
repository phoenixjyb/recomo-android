package com.recomo.user.control

enum class UserOperationMode(val wireValue: String) {
    Manual("manual"),
    MotionReplica("motion_replica"),
    SubjectFollowing("subject_following"),
    FixedPosition("fixed_position");

    companion object {
        fun fromWire(value: String?): UserOperationMode =
            entries.firstOrNull { it.wireValue == value } ?: Manual
    }
}

enum class UserFsmState(val code: Int) {
    Init(0),
    Running(1),
    Exit(2),
    Demo(3),
    Finished(4);

    val label: String
        get() = when (this) {
            Init -> "Init"
            Running -> "Running"
            Exit -> "Exit"
            Demo -> "Demo"
            Finished -> "Arrived"
        }

    companion object {
        fun fromCode(code: Int): UserFsmState =
            entries.firstOrNull { it.code == code } ?: Init
    }
}

data class UserPoiItem(
    val index: Int,
    val name: String,
    val x: Double,
    val y: Double,
    val yaw: Double
)

data class UserPoiSession(
    val sessionId: String,
    val sessionName: String,
    val poiCount: Int
)

data class UserNavState(
    val operationMode: UserOperationMode = UserOperationMode.Manual,
    val navActive: Boolean = false,
    val navSessionId: String = "",
    val navSpeed: Double = 0.8,
    val fsmState: UserFsmState = UserFsmState.Init,
    val followActive: Boolean = false,
    val followMaxSpeed: Double = 1.0,
    val followDistance: Double = 2.0,
    val poiSessions: List<UserPoiSession> = emptyList(),
    val selectedPoiSession: UserPoiSession? = null,
    val selectedPoiIndex: Int = 0,
    val currentPoiList: List<UserPoiItem> = emptyList(),
    val statusLabel: String = "Idle",
    val isConnected: Boolean = false,
    val canGo: Boolean = false,
    val duplicateFsmPublishers: Boolean = false
) {
    val arrivedPreserved: Boolean
        get() = fsmState == UserFsmState.Finished && !navActive
}
