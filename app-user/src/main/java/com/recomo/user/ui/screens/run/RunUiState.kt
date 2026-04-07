package com.recomo.user.ui.screens.run

data class RunPhaseChipUiState(
    val label: String,
    val active: Boolean = false,
    val complete: Boolean = false
)

data class RunWorkspaceUiState(
    val trajectoryText: String = "",
    val statusLabel: String = "Ready",
    val guidanceLabel: String? = null,
    val progress: Float? = null,
    val elapsedLabel: String? = null,
    val remainingLabel: String? = null,
    val selectedTrajectoryLabel: String? = null,
    val loadedTrajectory: String? = null,
    val loadedTrajectoryMatchesSelection: Boolean = false,
    val loadedTrajectoryDetail: String? = null,
    val phaseChips: List<RunPhaseChipUiState> = emptyList(),
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val hasError: Boolean = false,
    val estopActive: Boolean = false,
    val canLoad: Boolean = true,
    val canRun: Boolean = true,
    val canInit: Boolean = true,
    val canPause: Boolean = true,
    val canStop: Boolean = true,
    val canHome: Boolean = true
)
