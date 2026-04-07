package com.recomo.user.ui.screens.control

data class ControlOverviewUiState(
    val robotName: String,
    val connectionLabel: String,
    val isConnected: Boolean,
    val stateHz: Double? = null,
    val lastStateAgeMs: Long? = null,
    val basePose: ControlBasePoseUiState? = null,
    val safety: ControlSafetyUiState? = null,
    val mapPoseAvailable: Boolean = false,
    val trackingSummary: ControlTrackingSummaryUiState? = null
)

data class ControlBasePoseUiState(
    val x: Double,
    val y: Double,
    val yawDeg: Double
)

data class ControlSafetyUiState(
    val estop: Boolean,
    val deadmanOk: Boolean,
    val commOk: Boolean
)

data class ControlTrackingSummaryUiState(
    val label: String,
    val state: String
)

data class HomeOperationsUiState(
    val gatewayLabel: String,
    val serviceLabel: String,
    val robotLabel: String,
    val note: String? = null,
    val canReconnect: Boolean = true,
    val canRefreshGateway: Boolean = true,
    val canPrepareRobot: Boolean = true,
    val canDismissRobot: Boolean = false
)

data class RunStatusUiState(
    val title: String = "Run",
    val statusLabel: String,
    val progress: Float? = null,
    val elapsedLabel: String? = null,
    val remainingLabel: String? = null,
    val loadedTrajectory: String? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val hasError: Boolean = false
)
