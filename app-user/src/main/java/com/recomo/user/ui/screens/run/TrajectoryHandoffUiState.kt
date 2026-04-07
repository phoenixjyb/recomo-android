package com.recomo.user.ui.screens.run

enum class TrajectoryHandoffReadiness {
    Ready,
    Pending,
    Blocked,
    Unknown
}

data class TrajectoryHandoffCardUiState(
    val sourceName: String = "",
    val sessionId: String = "",
    val trajectoryId: String = "",
    val readiness: TrajectoryHandoffReadiness = TrajectoryHandoffReadiness.Unknown,
    val primaryButtonLabel: String = "Handoff",
    val primaryButtonEnabled: Boolean = true,
    val secondaryNote: String? = null
)
