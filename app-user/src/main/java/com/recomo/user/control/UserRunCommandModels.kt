package com.recomo.user.control

import com.recomo.common.preview.TrajectoryPreview

enum class SceneType {
    SimpleTrack,
    LivePnC,
    Unknown
}

enum class UserTrajectoryHandoffReadiness {
    Ready,
    Pending,
    Blocked,
    Unknown
}

data class UserTrajectoryHandoffState(
    val sourceName: String = "",
    val sessionId: String = "",
    val trajectoryId: String = "",
    val readiness: UserTrajectoryHandoffReadiness = UserTrajectoryHandoffReadiness.Unknown,
    val note: String? = null
)

data class UserLocalSessionPreviewState(
    val sessionId: String,
    val sessionName: String,
    val frameCount: Int,
    val preview: TrajectoryPreview
)

data class UserRunCommandState(
    val selectedTrajectory: String? = null,
    val loadedSessionId: String? = null,
    val loadedSessionName: String? = null,
    val statusLabel: String = "Ready",
    val progress: Float? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isIniting: Boolean = false,
    val isHoming: Boolean = false,
    val estopActive: Boolean = false,
    val sceneType: SceneType = SceneType.Unknown,
    val connectionStatus: UserConnectionStatus = UserConnectionStatus.Disconnected,
    val executorState: Int = 0,
    val stage2ExecutionStatus: String = "idle"
)
