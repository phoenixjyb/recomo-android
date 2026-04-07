package com.recomo.user.ui.screens.settings

data class SettingsUiState(
    val robotLabel: String = "",
    val robotProfileLabel: String = "",
    val networkPresetLabel: String = "",
    val chatServerUrl: String = "",
    val sessionFolderPath: String = "",
    val gatewayStatusLabel: String = "Disconnected",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false
) {
    val canConnect: Boolean get() = !isConnected && !isConnecting
    val canDisconnect: Boolean get() = isConnected && !isConnecting
}
