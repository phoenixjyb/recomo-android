package com.recomo.user.ui.screens.run

data class RunChecklistUiState(
    val gatewayReady: Boolean = false,
    val gatewayDetail: String = "Disconnected",
    val robotReady: Boolean = false,
    val robotDetail: String = "Idle",
    val localizationReady: Boolean = false,
    val localizationDetail: String = "Not ready",
    val sessionReady: Boolean = false,
    val sessionDetail: String = "No session",
    val safetyReady: Boolean = true,
    val safetyDetail: String = "Clear"
) {
    val readyCount: Int
        get() = listOf(gatewayReady, robotReady, localizationReady, sessionReady, safetyReady).count { it }

    val totalCount: Int
        get() = 5

    val allReady: Boolean
        get() = readyCount == totalCount
}
