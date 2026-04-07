package com.recomo.user.ui.screens.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.R

@Composable
fun SystemInfoCard(
    state: SystemInfoCardUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.systemName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.environmentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                StatusPill(
                    status = state.connectionStatus,
                    label = when (state.connectionStatus) {
                        GatewayConnectionStatus.Connected -> stringResource(R.string.status_online)
                        GatewayConnectionStatus.Connecting -> stringResource(R.string.connection_status_connecting)
                        GatewayConnectionStatus.Error -> stringResource(R.string.status_error)
                        GatewayConnectionStatus.Disconnected -> stringResource(R.string.status_offline)
                    }
                )
            }

            DetailLine(label = stringResource(R.string.home_gateway), value = state.gatewayLabel)
            state.deviceLabel?.takeIf { it.isNotBlank() }?.let {
                DetailLine(label = stringResource(R.string.system_device), value = it)
            }
            state.appVersion?.takeIf { it.isNotBlank() }?.let {
                DetailLine(label = stringResource(R.string.settings_version), value = it)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.stateHzLabel?.takeIf { it.isNotBlank() }?.let {
                    MetricChip(label = stringResource(R.string.system_state_rate), value = it)
                }
                state.lastStateAgeLabel?.takeIf { it.isNotBlank() }?.let {
                    MetricChip(label = stringResource(R.string.system_state_age), value = it)
                }
                state.safetyLabel?.takeIf { it.isNotBlank() }?.let {
                    MetricChip(label = stringResource(R.string.system_safety), value = it)
                }
            }

            state.note?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x10FFFFFF))
                ) {
                    Text(
                        text = note,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xCCFFFFFF)
                    )
                }
            }
        }
    }
}

@Composable
fun GatewayControlCard(
    state: GatewayControlCardUiState,
    onGatewayUrlChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.system_gateway_control),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                StatusPill(
                    status = state.connectionStatus,
                    label = when {
                        state.isConnecting -> stringResource(R.string.status_loading)
                        state.connectionStatus == GatewayConnectionStatus.Connected -> stringResource(R.string.connection_status_connected)
                        state.connectionStatus == GatewayConnectionStatus.Error -> stringResource(R.string.status_error)
                        else -> stringResource(R.string.connection_status_disconnected)
                    }
                )
            }

            OutlinedTextField(
                value = state.gatewayUrl,
                onValueChange = onGatewayUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = !state.gatewayUrlEditable,
                label = { Text(stringResource(R.string.connection_host_hint)) },
                placeholder = { Text(state.gatewayUrlHint) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnectClick,
                    enabled = state.primaryActionEnabled && !state.isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF223341),
                        disabledContentColor = Color(0x66FFFFFF)
                    )
                ) {
                    Text(state.connectButtonLabel)
                }
                OutlinedButton(
                    onClick = onDisconnectClick,
                    enabled = state.secondaryActionEnabled && !state.isConnecting
                ) {
                    Text(state.disconnectButtonLabel)
                }
            }

            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350)
                )
            }

            state.lastConnectedLabel?.takeIf { it.isNotBlank() }?.let { lastConnected ->
                Text(
                    text = stringResource(R.string.system_last_connected, lastConnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x80FFFFFF)
                )
            }
        }
    }
}

@Composable
fun GatewayServiceCard(
    state: GatewayServiceCardUiState,
    onRefreshClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_gateway_status),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = stringResource(R.string.system_service), value = state.serviceLabel)
            DetailLine(label = stringResource(R.string.system_api), value = state.apiLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshClick, enabled = state.canRefresh) {
                    Text(stringResource(R.string.action_refresh))
                }
                Button(onClick = onStartClick, enabled = state.canStart) {
                    Text(stringResource(R.string.system_gateway_start))
                }
                OutlinedButton(onClick = onStopClick, enabled = state.canStop) {
                    Text(stringResource(R.string.system_gateway_stop))
                }
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350)
                )
            }
        }
    }
}

@Composable
fun RobotPreparationCard(
    state: RobotPreparationCardUiState,
    onPrepareClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_robot_prep_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = stringResource(R.string.system_status), value = state.statusLabel)
            DetailLine(label = stringResource(R.string.system_state_stream), value = state.stateStreamLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrepareClick, enabled = state.canPrepare) {
                    Text(stringResource(R.string.system_robot_prep))
                }
                OutlinedButton(onClick = onDismissClick, enabled = state.canDismiss) {
                    Text(stringResource(R.string.system_robot_dismiss))
                }
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350)
                )
            } ?: state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }
        }
    }
}

@Composable
fun PncAuthorityCard(
    state: PncAuthorityCardUiState,
    onAutoClick: () -> Unit,
    onLocalClick: () -> Unit,
    onExternalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_pnc_authority_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = stringResource(R.string.system_status), value = state.authorityLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityButton(
                    label = stringResource(R.string.system_pnc_authority_auto),
                    selected = state.authorityLabel.equals("auto", ignoreCase = true),
                    enabled = !state.isBusy,
                    onClick = onAutoClick
                )
                AuthorityButton(
                    label = stringResource(R.string.system_pnc_authority_local),
                    selected = state.authorityLabel.equals("local", ignoreCase = true),
                    enabled = !state.isBusy,
                    onClick = onLocalClick
                )
                AuthorityButton(
                    label = stringResource(R.string.system_pnc_authority_external),
                    selected = state.authorityLabel.equals("external", ignoreCase = true),
                    enabled = !state.isBusy,
                    onClick = onExternalClick
                )
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350)
                )
            } ?: state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }
        }
    }
}

@Composable
fun StackLifecycleCard(
    state: StackLifecycleCardUiState,
    onPreparePerceptionClick: () -> Unit,
    onDismissPerceptionClick: () -> Unit,
    onPrepareLocalizationClick: () -> Unit,
    onDismissLocalizationClick: () -> Unit,
    onPreparePncClick: () -> Unit,
    onDismissPncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_stack_lifecycle_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = stringResource(R.string.system_map_context), value = state.contextLabel)
            DetailLine(label = stringResource(R.string.system_pnc_mode), value = state.modeLabel)

            LifecycleRow(
                prepareLabel = stringResource(R.string.system_prepare_perception),
                stopLabel = stringResource(R.string.system_stop),
                enabled = !state.isBusy,
                onPrepareClick = onPreparePerceptionClick,
                onStopClick = onDismissPerceptionClick
            )
            LifecycleRow(
                prepareLabel = stringResource(R.string.system_prepare_localization),
                stopLabel = stringResource(R.string.system_stop),
                enabled = !state.isBusy && state.canPrepareLocalization,
                stopEnabled = !state.isBusy,
                onPrepareClick = onPrepareLocalizationClick,
                onStopClick = onDismissLocalizationClick
            )
            LifecycleRow(
                prepareLabel = stringResource(R.string.system_prepare_pnc),
                stopLabel = stringResource(R.string.system_stop),
                enabled = !state.isBusy,
                onPrepareClick = onPreparePncClick,
                onStopClick = onDismissPncClick
            )

            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350)
                )
            } ?: state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }
        }
    }
}

// ── Tier-based cards ────────────────────────────────────────────

@Composable
fun ActuatorCard(
    state: ActuatorCardUiState,
    onPrepareClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actuators",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = "Status", value = state.statusLabel)
            DetailLine(label = "State Stream", value = state.stateStreamLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.components.forEach { component ->
                    ComponentDot(label = component.label, ok = component.ok)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrepareClick,
                    enabled = state.canPrepare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF223341),
                        disabledContentColor = Color(0x66FFFFFF)
                    )
                ) {
                    Text("Prepare")
                }
                OutlinedButton(onClick = onDismissClick, enabled = state.canDismiss) {
                    Text("Dismiss")
                }
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF5350))
            } ?: state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = Color(0xB3FFFFFF))
            }
        }
    }
}

@Composable
fun SceneCard(
    state: SceneCardUiState,
    onPrepareSensorsClick: () -> Unit,
    onDismissSensorsClick: () -> Unit,
    onPrepareLocalizationClick: () -> Unit,
    onDismissLocalizationClick: () -> Unit,
    onPreparePerceptionClick: () -> Unit,
    onDismissPerceptionClick: () -> Unit,
    onPrepareAllClick: () -> Unit,
    onDismissAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Scene",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = "Map Context", value = state.contextLabel)

            // Sensors sub-row
            TierSubRow(
                label = "Sensors",
                ready = state.sensorsReady,
                components = state.sensors,
                prepareLabel = "Prepare",
                enabled = !state.isBusy,
                onPrepareClick = onPrepareSensorsClick,
                onStopClick = onDismissSensorsClick
            )

            // Localization sub-row
            TierSubRow(
                label = "Localization",
                ready = state.localizationReady,
                components = state.localization,
                prepareLabel = "Prepare",
                enabled = !state.isBusy && state.canPrepareLocalization,
                stopEnabled = !state.isBusy,
                onPrepareClick = onPrepareLocalizationClick,
                onStopClick = onDismissLocalizationClick
            )

            // Perception sub-row
            TierSubRow(
                label = "Perception",
                ready = state.perceptionReady,
                components = state.perception,
                prepareLabel = "Prepare",
                enabled = !state.isBusy,
                onPrepareClick = onPreparePerceptionClick,
                onStopClick = onDismissPerceptionClick
            )

            // Scene-level convenience buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrepareAllClick,
                    enabled = !state.isBusy && state.canPrepareLocalization,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF223341),
                        disabledContentColor = Color(0x66FFFFFF)
                    )
                ) {
                    Text("Prepare All")
                }
                OutlinedButton(
                    onClick = onDismissAllClick,
                    enabled = !state.isBusy
                ) {
                    Text("Stop All")
                }
            }

            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF5350))
            } ?: state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = Color(0xB3FFFFFF))
            }
        }
    }
}

@Composable
fun AutonomyCard(
    state: AutonomyCardUiState,
    onPreparePncClick: () -> Unit,
    onDismissPncClick: () -> Unit,
    onAutoClick: () -> Unit,
    onLocalClick: () -> Unit,
    onExternalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Autonomy",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine(label = "PnC Mode", value = state.pncModeLabel)
            DetailLine(label = "FSM", value = state.fsmStateLabel)
            DetailLine(label = "Authority", value = state.authorityLabel)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPreparePncClick,
                    enabled = !state.isBusy && state.sceneReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF223341),
                        disabledContentColor = Color(0x66FFFFFF)
                    )
                ) {
                    Text("Prepare PnC")
                }
                OutlinedButton(
                    onClick = onDismissPncClick,
                    enabled = !state.isBusy
                ) {
                    Text("Stop PnC")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorityButton(
                    label = "Auto",
                    selected = state.authorityLabel.equals("auto", ignoreCase = true),
                    enabled = !state.isBusy,
                    onClick = onAutoClick
                )
                AuthorityButton(
                    label = "Local",
                    selected = state.authorityLabel.equals("local", ignoreCase = true),
                    enabled = !state.isBusy,
                    onClick = onLocalClick
                )
                AuthorityButton(
                    label = "External",
                    selected = state.authorityLabel.equals("external", ignoreCase = true),
                    enabled = !state.isBusy,
                    onClick = onExternalClick
                )
            }

            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF5350))
            } ?: state.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = Color(0xB3FFFFFF))
            }
        }
    }
}

@Composable
fun CloudCard(
    state: CloudCardUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cloud Services",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ComponentDot(label = "Video Mgmt", ok = state.videoManagementOk)
                ComponentDot(label = "Chat Server", ok = state.chatServerOk)
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF5350))
            }
        }
    }
}

@Composable
private fun ComponentDot(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = if (ok) Color(0xFF66BB6A) else Color(0xFF90A4AE)
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xB3FFFFFF)
        )
    }
}

@Composable
private fun TierSubRow(
    label: String,
    ready: Boolean,
    components: List<TierComponentStatus>,
    prepareLabel: String,
    enabled: Boolean,
    onPrepareClick: () -> Unit,
    onStopClick: () -> Unit,
    stopEnabled: Boolean = enabled
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = if (ready) Color(0xFF66BB6A) else Color(0xFF90A4AE)
                ) {}
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                components.forEach { c ->
                    ComponentDot(label = c.label, ok = c.ok)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPrepareClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A3FF),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF223341),
                    disabledContentColor = Color(0x66FFFFFF)
                )
            ) {
                Text(prepareLabel)
            }
            OutlinedButton(onClick = onStopClick, enabled = stopEnabled) {
                Text("Stop")
            }
        }
    }
}

@Composable
fun SystemLoadCard(
    state: SystemLoadCardUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.system_load_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip(label = stringResource(R.string.system_battery), value = state.batteryLabel)
                MetricChip(label = stringResource(R.string.system_cpu), value = state.cpuLabel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip(label = stringResource(R.string.system_gpu), value = state.gpuLabel)
                MetricChip(label = stringResource(R.string.system_memory), value = state.memoryLabel)
            }
            state.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0x80FFFFFF)
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0x80FFFFFF)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AuthorityButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00A3FF),
                contentColor = Color.White
            )
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled
        ) {
            Text(label)
        }
    }
}

@Composable
private fun LifecycleRow(
    prepareLabel: String,
    stopLabel: String,
    enabled: Boolean,
    onPrepareClick: () -> Unit,
    onStopClick: () -> Unit,
    stopEnabled: Boolean = enabled
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPrepareClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00A3FF),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF223341),
                disabledContentColor = Color(0x66FFFFFF)
            )
        ) {
            Text(prepareLabel)
        }
        OutlinedButton(
            onClick = onStopClick,
            enabled = stopEnabled
        ) {
            Text(stopLabel)
        }
    }
}

@Composable
private fun StatusPill(
    status: GatewayConnectionStatus,
    label: String
) {
    val accent = when (status) {
        GatewayConnectionStatus.Connected -> Color(0xFF66BB6A)
        GatewayConnectionStatus.Connecting -> Color(0xFFF5C451)
        GatewayConnectionStatus.Error -> Color(0xFFEF5350)
        GatewayConnectionStatus.Disconnected -> Color(0xFF90A4AE)
    }

    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}
