package com.recomo.user.ui.screens.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.R
import com.recomo.user.ui.screens.map.MapLocalizationUiState

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ControlOverviewPanel(
    state: ControlOverviewUiState,
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
                Column {
                    Text(
                        text = state.robotName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.connectionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isConnected) Color(0xFF66D2FF) else Color(0xFFEF5350)
                    )
                }

                StatusBadge(
                    label = if (state.isConnected) "Online" else "Offline",
                    accent = if (state.isConnected) Color(0xFF66D2FF) else Color(0xFFEF5350)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip("Hz", state.stateHz?.let { "%.1f".format(it) } ?: "—")
                MetricChip("Age", state.lastStateAgeMs?.let { "${it}ms" } ?: "—")
                MetricChip("Map", if (state.mapPoseAvailable) "Ready" else "No pose")
                state.trackingSummary?.let {
                    MetricChip(it.label, it.state)
                }
            }

            state.basePose?.let { pose ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBlock("X", "%.2f".format(pose.x), Modifier.weight(1f))
                    MetricBlock("Y", "%.2f".format(pose.y), Modifier.weight(1f))
                    MetricBlock("Yaw", "%.1f".format(pose.yawDeg), Modifier.weight(1f))
                }
            }

            state.safety?.let { safety ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniFlag("E-Stop", safety.estop, Color(0xFFEF5350))
                    MiniFlag("Deadman", safety.deadmanOk, Color(0xFF66D2FF))
                    MiniFlag("Comms", safety.commOk, Color(0xFF66BB6A))
                }
            }
        }
    }
}

@Composable
fun RunStatusPanel(
    state: RunStatusUiState,
    modifier: Modifier = Modifier,
    onRun: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null
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
                Column {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.hasError) Color(0xFFEF5350) else Color(0xFFEDEDED)
                    )
                }
                StatusBadge(
                    label = when {
                        state.hasError -> stringResource(R.string.status_error)
                        state.isPaused -> stringResource(R.string.status_paused)
                        state.isRunning -> stringResource(R.string.status_running)
                        else -> stringResource(R.string.run_ready)
                    },
                    accent = when {
                        state.hasError -> Color(0xFFEF5350)
                        state.isPaused -> Color(0xFFF5C451)
                        state.isRunning -> Color(0xFF66D2FF)
                        else -> Color(0xFF66BB6A)
                    }
                )
            }

            state.loadedTrajectory?.let { trajectory ->
                Text(
                    text = trajectory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            state.progress?.let { progress ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRun != null) {
                    Button(onClick = onRun) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(if (state.isPaused) R.string.control_resume else R.string.control_run))
                    }
                }
                if (onPause != null) {
                    OutlinedButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.control_pause))
                    }
                }
                if (onStop != null) {
                    OutlinedButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.control_stop))
                    }
                }
            }

            state.elapsedLabel?.let { elapsed ->
                Text(
                    text = stringResource(R.string.control_elapsed, elapsed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x80FFFFFF)
                )
            }
            state.remainingLabel?.let { remaining ->
                Text(
                    text = stringResource(R.string.control_remaining, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x80FFFFFF)
                )
            }
        }
    }
}

@Composable
fun HomeOperationsPanel(
    state: HomeOperationsUiState,
    modifier: Modifier = Modifier,
    onReconnect: () -> Unit = {},
    onRefreshGateway: () -> Unit = {},
    onPrepareRobot: () -> Unit = {},
    onDismissRobot: () -> Unit = {}
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
                text = stringResource(R.string.home_operations),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip(stringResource(R.string.home_gateway), state.gatewayLabel)
                MetricChip(stringResource(R.string.system_service), state.serviceLabel)
                MetricChip(stringResource(R.string.home_robot), state.robotLabel)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReconnect,
                    enabled = state.canReconnect
                ) {
                    Text(stringResource(R.string.connection_reconnect))
                }
                OutlinedButton(
                    onClick = onRefreshGateway,
                    enabled = state.canRefreshGateway
                ) {
                    Text(stringResource(R.string.action_refresh))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrepareRobot,
                    enabled = state.canPrepareRobot
                ) {
                    Text(stringResource(R.string.system_robot_prep))
                }
                OutlinedButton(
                    onClick = onDismissRobot,
                    enabled = state.canDismissRobot
                ) {
                    Text(stringResource(R.string.system_robot_dismiss))
                }
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
@OptIn(ExperimentalLayoutApi::class)
fun HomeReadinessPanel(
    robotLabel: String,
    overviewState: ControlOverviewUiState,
    operationsState: HomeOperationsUiState,
    mapUiState: MapLocalizationUiState,
    modifier: Modifier = Modifier,
    onOpenRun: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onRefreshGateway: () -> Unit = {},
    onPrepareRobot: () -> Unit = {},
    onDismissRobot: () -> Unit = {},
    onOpenMapMatch: () -> Unit = {}
) {
    val sceneUnset = stringResource(R.string.run_cockpit_location_unset)
    val mapUnset = stringResource(R.string.run_cockpit_map_unset)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.home_preflight_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
                StatusBadge(
                    label = if (overviewState.isConnected) {
                        stringResource(R.string.status_online)
                    } else {
                        stringResource(R.string.status_offline)
                    },
                    accent = if (overviewState.isConnected) Color(0xFF66D2FF) else Color(0xFFEF5350)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(stringResource(R.string.home_robot), robotLabel)
                MetricChip(stringResource(R.string.home_gateway), operationsState.gatewayLabel)
                MetricChip(stringResource(R.string.system_service), operationsState.serviceLabel)
                MetricChip(
                    stringResource(R.string.run_cockpit_scene_label),
                    mapUiState.selectedLocation?.ifBlank { null } ?: sceneUnset
                )
                MetricChip(
                    stringResource(R.string.map_asset),
                    mapUiState.selectedMapAsset?.ifBlank { null } ?: mapUnset
                )
                MetricChip(
                    stringResource(R.string.home_localization),
                    if (mapUiState.robotPoseOk) stringResource(R.string.map_localized) else stringResource(R.string.map_not_ready)
                )
                overviewState.stateHz?.let { MetricChip("Hz", "%.1f".format(it)) }
                overviewState.lastStateAgeMs?.let { MetricChip("Age", "${it}ms") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenRun,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.home_open_run))
                }
                OutlinedButton(onClick = onOpenMapMatch, enabled = !mapUiState.isBusy) {
                    Text(stringResource(R.string.map_match_title))
                }
                OutlinedButton(onClick = onRefreshGateway, enabled = operationsState.canRefreshGateway) {
                    Text(stringResource(R.string.home_refresh))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrepareRobot, enabled = operationsState.canPrepareRobot) {
                    Text(stringResource(R.string.system_robot_prep))
                }
                OutlinedButton(onClick = onDismissRobot, enabled = operationsState.canDismissRobot) {
                    Text(stringResource(R.string.system_robot_dismiss))
                }
                OutlinedButton(onClick = onReconnect, enabled = operationsState.canReconnect) {
                    Text(stringResource(R.string.connection_reconnect))
                }
            }

            operationsState.note?.takeIf { it.isNotBlank() }?.let { note ->
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
private fun StatusBadge(
    label: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (accent == Color(0xFFEF5350)) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
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
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MiniFlag(
    label: String,
    enabled: Boolean,
    accent: Color
) {
    Surface(
        color = if (enabled) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.24f) else Color(0x10FFFFFF))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else Color(0x80FFFFFF)
        )
    }
}
