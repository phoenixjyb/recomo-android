package com.recomo.user.ui.screens.system

enum class GatewayConnectionStatus {
    Disconnected,
    Connecting,
    Connected,
    Error
}

data class SystemInfoCardUiState(
    val systemName: String = "App User",
    val environmentLabel: String = "Gateway-linked",
    val gatewayLabel: String = "—",
    val connectionStatus: GatewayConnectionStatus = GatewayConnectionStatus.Disconnected,
    val deviceLabel: String? = null,
    val appVersion: String? = null,
    val stateHzLabel: String? = null,
    val lastStateAgeLabel: String? = null,
    val safetyLabel: String? = null,
    val note: String? = null
)

data class GatewayControlCardUiState(
    val gatewayUrl: String = "",
    val gatewayUrlHint: String = "http://192.168.0.1:8080",
    val gatewayUrlEditable: Boolean = false,
    val connectionStatus: GatewayConnectionStatus = GatewayConnectionStatus.Disconnected,
    val statusLabel: String = "Disconnected",
    val isConnecting: Boolean = false,
    val connectButtonLabel: String = "Connect",
    val disconnectButtonLabel: String = "Disconnect",
    val primaryActionEnabled: Boolean = true,
    val secondaryActionEnabled: Boolean = true,
    val errorMessage: String? = null,
    val lastConnectedLabel: String? = null
)

data class GatewayServiceCardUiState(
    val serviceLabel: String = "Unknown",
    val apiLabel: String = "Unknown",
    val isBusy: Boolean = false,
    val canRefresh: Boolean = true,
    val canStart: Boolean = false,
    val canStop: Boolean = false,
    val errorMessage: String? = null
)

data class RobotPreparationCardUiState(
    val statusLabel: String = "Idle",
    val stateStreamLabel: String = "Unknown",
    val isBusy: Boolean = false,
    val canPrepare: Boolean = true,
    val canDismiss: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

data class PncAuthorityCardUiState(
    val authorityLabel: String = "auto",
    val isBusy: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

data class StackLifecycleCardUiState(
    val contextLabel: String = "— / —",
    val modeLabel: String = "motion_replica",
    val isBusy: Boolean = false,
    val canPrepareLocalization: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

// ── Tier-based UI state models ──────────────────────────────────

data class TierComponentStatus(
    val label: String,
    val ok: Boolean
)

data class ActuatorCardUiState(
    val statusLabel: String = "Idle",
    val stateStreamLabel: String = "Unknown",
    val components: List<TierComponentStatus> = emptyList(),
    val isBusy: Boolean = false,
    val canPrepare: Boolean = true,
    val canDismiss: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

data class SceneCardUiState(
    val contextLabel: String = "— / —",
    val sensors: List<TierComponentStatus> = emptyList(),
    val localization: List<TierComponentStatus> = emptyList(),
    val perception: List<TierComponentStatus> = emptyList(),
    val sensorsReady: Boolean = false,
    val localizationReady: Boolean = false,
    val perceptionReady: Boolean = false,
    val isBusy: Boolean = false,
    val canPrepareLocalization: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

data class AutonomyCardUiState(
    val pncModeLabel: String = "none",
    val authorityLabel: String = "auto",
    val fsmStateLabel: String = "—",
    val ready: Boolean = false,
    val sceneReady: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

data class CloudCardUiState(
    val videoManagementOk: Boolean = false,
    val chatServerOk: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

data class SystemLoadCardUiState(
    val batteryLabel: String = "—",
    val cpuLabel: String = "—",
    val gpuLabel: String = "—",
    val memoryLabel: String = "—",
    val note: String? = null
)
