package com.recomo.user.ui.screens.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.R

@Composable
fun RunWorkspace(
    state: RunWorkspaceUiState,
    modifier: Modifier = Modifier,
    onTrajectoryChange: (String) -> Unit,
    onLoad: () -> Unit,
    onRun: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0x14FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.run_workspace_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.hasError) Color(0xFFEF5350) else Color(0xFFEDEDED)
                )
                state.guidanceLabel?.let { guidance ->
                    Text(
                        text = guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                if (state.phaseChips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.phaseChips.forEach { chip ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        text = chip.label,
                                        color = when {
                                            chip.active -> Color.White
                                            chip.complete -> Color(0xFF1BC47D)
                                            else -> Color(0xB3FFFFFF)
                                        }
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when {
                                        chip.active -> Color(0xFF00A3FF)
                                        chip.complete -> Color(0x141BC47D)
                                        else -> Color(0x10FFFFFF)
                                    },
                                    disabledContainerColor = when {
                                        chip.active -> Color(0xFF00A3FF)
                                        chip.complete -> Color(0x141BC47D)
                                        else -> Color(0x10FFFFFF)
                                    },
                                    disabledLabelColor = when {
                                        chip.active -> Color.White
                                        chip.complete -> Color(0xFF1BC47D)
                                        else -> Color(0xB3FFFFFF)
                                    }
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        chip.active -> Color(0x3300A3FF)
                                        chip.complete -> Color(0x331BC47D)
                                        else -> Color(0x14FFFFFF)
                                    }
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.trajectoryText,
                    onValueChange = onTrajectoryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.run_trajectory_label)) },
                    singleLine = true
                )

                state.selectedTrajectoryLabel?.let { selected ->
                    Text(
                        text = "${stringResource(R.string.run_selected_label)}: $selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                state.loadedTrajectory?.let { loaded ->
                    Text(
                        text = "${stringResource(R.string.run_loaded_label)}: $loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.loadedTrajectoryMatchesSelection) Color(0xFF1BC47D) else Color(0xFFF5C451)
                    )
                }

                state.loadedTrajectoryDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.loadedTrajectoryMatchesSelection) Color(0xFF1BC47D) else Color(0xFFF5C451)
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
                    OutlinedButton(onClick = onLoad, enabled = state.canLoad) {
                        Text(stringResource(R.string.run_load))
                    }
                    Button(
                        onClick = onRun,
                        enabled = state.canRun,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A3FF),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isPaused) stringResource(R.string.run_resume) else stringResource(R.string.run_run))
                    }
                    OutlinedButton(onClick = onPause, enabled = state.canPause) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.run_pause))
                    }
                    OutlinedButton(onClick = onStop, enabled = state.canStop) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.run_stop))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onHome, enabled = state.canHome) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.run_home))
                    }
                    Button(
                        onClick = onEmergencyStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.estopActive) Color(0xFFB91C1C) else Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            if (state.estopActive) {
                                stringResource(R.string.run_clear_estop)
                            } else {
                                stringResource(R.string.run_estop)
                            }
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0x10FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.run_workspace_timing),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.control_elapsed, state.elapsedLabel ?: "--"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
                Text(
                    text = stringResource(R.string.control_remaining, state.remainingLabel ?: "--"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
                Text(
                    text = if (state.isRunning) stringResource(R.string.run_workspace_running) else if (state.isPaused) stringResource(R.string.run_workspace_paused) else stringResource(R.string.run_workspace_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        state.hasError -> Color(0xFFEF5350)
                        state.isRunning -> Color(0xFF66D2FF)
                        state.isPaused -> Color(0xFFF5C451)
                        else -> Color(0xFF66BB6A)
                    }
                )
            }
        }
    }
}
