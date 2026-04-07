package com.recomo.user.ui.screens.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.recomo.user.ui.screens.control.HomeOperationsUiState
import com.recomo.user.ui.screens.map.MapLocalizationUiState

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RunOperationsCockpitCard(
    operationsState: HomeOperationsUiState,
    mapUiState: MapLocalizationUiState,
    modifier: Modifier = Modifier,
    onReconnect: () -> Unit,
    onRefreshGateway: () -> Unit,
    onPrepareRobot: () -> Unit,
    onDismissRobot: () -> Unit,
    onOpenMapMatch: () -> Unit,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit
) {
    val unsetLocation = stringResource(R.string.run_cockpit_location_unset)
    val unsetMap = stringResource(R.string.run_cockpit_map_unset)

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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.run_cockpit_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.run_cockpit_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
                Text(
                    text = mapUiState.localizationStatus,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mapUiState.robotPoseOk) Color(0xFF1BC47D) else Color(0xFFF5C451)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CockpitChip(
                    label = stringResource(R.string.run_cockpit_link_label),
                    value = operationsState.gatewayLabel
                )
                CockpitChip(
                    label = stringResource(R.string.run_cockpit_stack_label),
                    value = operationsState.serviceLabel
                )
                CockpitChip(
                    label = stringResource(R.string.run_cockpit_scene_label),
                    value = mapUiState.selectedLocation?.ifBlank { null } ?: unsetLocation
                )
                CockpitChip(
                    label = stringResource(R.string.map_asset),
                    value = mapUiState.selectedMapAsset?.ifBlank { null } ?: unsetMap
                )
                CockpitChip(
                    label = stringResource(R.string.run_cockpit_pose_label),
                    value = if (mapUiState.robotPoseOk) {
                        stringResource(R.string.map_localized)
                    } else {
                        stringResource(R.string.map_not_ready)
                    },
                    accent = if (mapUiState.robotPoseOk) Color(0xFF1BC47D) else Color(0xFFF5C451)
                )
            }

            operationsState.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }

            mapUiState.backendActionMessage?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReconnect,
                    enabled = operationsState.canReconnect
                ) {
                    Text(stringResource(R.string.connection_reconnect))
                }
                OutlinedButton(
                    onClick = onRefreshGateway,
                    enabled = operationsState.canRefreshGateway
                ) {
                    Text(stringResource(R.string.home_refresh))
                }
                Button(
                    onClick = onPrepareRobot,
                    enabled = operationsState.canPrepareRobot,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF))
                ) {
                    Text(stringResource(R.string.system_robot_prep))
                }
                OutlinedButton(
                    onClick = onDismissRobot,
                    enabled = operationsState.canDismissRobot
                ) {
                    Text(stringResource(R.string.system_robot_dismiss))
                }
                OutlinedButton(
                    onClick = onOpenMapMatch,
                    enabled = !mapUiState.isBusy
                ) {
                    Text(stringResource(R.string.map_match_title))
                }
                Button(
                    onClick = onPrepareLocalization,
                    enabled = !mapUiState.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B5EFF))
                ) {
                    Text(stringResource(R.string.map_prepare))
                }
                OutlinedButton(
                    onClick = onDismissLocalization,
                    enabled = !mapUiState.isBusy
                ) {
                    Text(stringResource(R.string.map_dismiss))
                }
            }
        }
    }
}

@Composable
private fun CockpitChip(
    label: String,
    value: String,
    accent: Color = Color(0xCCFFFFFF)
) {
    Surface(
        color = Color(0x10FFFFFF),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
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
                color = accent
            )
        }
    }
}
