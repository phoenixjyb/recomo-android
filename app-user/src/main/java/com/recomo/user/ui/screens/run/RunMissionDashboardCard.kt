package com.recomo.user.ui.screens.run

import android.graphics.Bitmap
import android.view.SurfaceHolder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.common.model.ConnectionState as VideoConnectionState
import com.recomo.user.R
import com.recomo.user.control.UserRunVideoDetailState
import com.recomo.user.control.UserRunVideoUiState
import com.recomo.user.data.trajectory.LocalTrajectorySessionSummary
import com.recomo.user.ui.screens.control.HomeOperationsUiState
import com.recomo.user.ui.screens.library.LibrarySessionSummaryUiItem
import com.recomo.user.ui.screens.library.displayMotionMeta
import com.recomo.user.ui.screens.library.displayMotionTitle
import com.recomo.user.ui.screens.map.MapLocalizationUiState

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RunMissionDashboardCard(
    operationsState: HomeOperationsUiState,
    mapUiState: MapLocalizationUiState,
    librarySessions: List<LibrarySessionSummaryUiItem>,
    workspaceState: RunWorkspaceUiState,
    checklistState: RunChecklistUiState,
    videoState: UserRunVideoUiState,
    videoBitmap: Bitmap?,
    handoffState: TrajectoryHandoffCardUiState?,
    localSessions: List<LocalTrajectorySessionSummary>,
    localSessionsTitle: String,
    localSessionsEmptyMessage: String,
    localPreviewActionLabel: String,
    modifier: Modifier = Modifier,
    onVideoSurfaceReady: (SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    onReconnectVideo: () -> Unit,
    onDisconnectVideo: () -> Unit,
    onReconnect: () -> Unit,
    onRefreshGateway: () -> Unit,
    onPrepareRobot: () -> Unit,
    onDismissRobot: () -> Unit,
    onOpenMapMatch: () -> Unit,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit,
    onAttachLibrarySession: (LibrarySessionSummaryUiItem) -> Unit,
    onOpenLibrary: () -> Unit,
    onTrajectoryChange: (String) -> Unit,
    onLoad: () -> Unit,
    onHandoffPrimaryAction: () -> Unit,
    onRefreshLocalSessions: () -> Unit,
    onAttachLocalSession: (LocalTrajectorySessionSummary) -> Unit,
    onPreviewLocalSession: (LocalTrajectorySessionSummary) -> Unit,
    onRun: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val pickerTitle = stringResource(R.string.run_picker_title)
    val pickerSubtitle = stringResource(R.string.run_picker_subtitle)
    val pickerOpenLibrary = stringResource(R.string.run_picker_open_library)
    val pickerEmpty = stringResource(R.string.run_picker_empty)
    val unsetLocation = stringResource(R.string.run_cockpit_location_unset)
    val unsetMap = stringResource(R.string.run_cockpit_map_unset)
    val previewTitle = stringResource(R.string.run_live_preview_title)

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF080808))
            ) {
                if (videoState.showBitmapFrame && videoBitmap != null) {
                    Image(
                        bitmap = videoBitmap.asImageBitmap(),
                        contentDescription = previewTitle,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AndroidView(
                        factory = { context ->
                            RunVideoSurfaceView(context).apply {
                                setZOrderMediaOverlay(false)
                                setOnSurfaceReadyListener(onVideoSurfaceReady)
                                setOnSurfaceDestroyedListener(onVideoSurfaceDestroyed)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = Color(0xB0000000),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x14FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = previewTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = when (val connection = videoState.connectionState) {
                                    is VideoConnectionState.Connected -> stringResource(R.string.connection_status_connected)
                                    is VideoConnectionState.Connecting -> stringResource(R.string.connection_status_connecting)
                                    is VideoConnectionState.Error -> videoState.errorMessage ?: connection.message
                                    else -> stringResource(R.string.connection_status_disconnected)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (videoState.connectionState) {
                                    is VideoConnectionState.Connected -> Color(0xFF1BC47D)
                                    is VideoConnectionState.Connecting -> Color(0xFFFFB74D)
                                    is VideoConnectionState.Error -> Color(0xFFEF5350)
                                    else -> Color(0xB3FFFFFF)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDisconnectVideo,
                            border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xB0000000),
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.connection_disconnect))
                        }
                        Button(
                            onClick = onReconnectVideo,
                            enabled = videoState.canReconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00A3FF),
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.connection_reconnect))
                        }
                    }
                }

                if (!videoState.isStreaming) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        videoState.detailState == UserRunVideoDetailState.ReceivingFrames -> Color(0xFF1BC47D)
                                        videoState.connectionState is VideoConnectionState.Connected -> Color(0xFF1BC47D)
                                        videoState.connectionState is VideoConnectionState.Connecting -> Color(0xFFFFB74D)
                                        videoState.connectionState is VideoConnectionState.Error -> Color(0xFFEF5350)
                                        else -> Color(0x66FFFFFF)
                                    }
                                )
                        )
                        Text(
                            text = when (videoState.detailState) {
                                UserRunVideoDetailState.NoUrl -> stringResource(R.string.run_live_preview_no_url)
                                UserRunVideoDetailState.WaitingFrames -> stringResource(R.string.run_live_preview_waiting_frames)
                                UserRunVideoDetailState.ReceivingFrames -> stringResource(R.string.run_live_preview_receiving)
                                UserRunVideoDetailState.Error -> stringResource(R.string.run_live_preview_error)
                                UserRunVideoDetailState.Idle -> when (videoState.connectionState) {
                                    is VideoConnectionState.Connecting -> stringResource(R.string.run_live_preview_connecting)
                                    else -> stringResource(R.string.run_live_preview_offline)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xE6FFFFFF)
                        )
                    }
                }

                when {
                    workspaceState.estopActive -> {
                        RunStateOverlay(
                            title = stringResource(R.string.run_estop),
                            detail = workspaceState.guidanceLabel ?: workspaceState.statusLabel,
                            accent = Color(0xFFEF4444),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    workspaceState.hasError -> {
                        RunStateOverlay(
                            title = workspaceState.statusLabel,
                            detail = workspaceState.guidanceLabel,
                            accent = Color(0xFFEF5350),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    workspaceState.isPaused -> {
                        RunStateOverlay(
                            title = stringResource(R.string.run_paused),
                            detail = workspaceState.guidanceLabel,
                            accent = Color(0xFFF5C451),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoMetricChip(
                        label = stringResource(R.string.run_live_preview_stream),
                        value = videoState.cameraUrl.ifBlank { "—" }
                    )
                    VideoMetricChip(
                        label = stringResource(R.string.run_live_preview_codec),
                        value = videoState.codecLabel ?: "—"
                    )
                    VideoMetricChip(
                        label = stringResource(R.string.run_live_preview_resolution),
                        value = videoState.resolutionLabel
                    )
                    VideoMetricChip(
                        label = stringResource(R.string.run_live_preview_fps),
                        value = videoState.fpsLabel ?: "—"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.run_cockpit_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = workspaceState.guidanceLabel ?: stringResource(R.string.run_cockpit_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = if (checklistState.allReady) {
                                stringResource(R.string.run_ready)
                            } else {
                                stringResource(R.string.run_loading)
                            }
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = if (checklistState.allReady) Color(0x141BC47D) else Color(0x10FFFFFF),
                        disabledLabelColor = if (checklistState.allReady) Color(0xFF1BC47D) else Color(0xB3FFFFFF)
                    )
                )
            }

            Text(
                text = workspaceState.statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (workspaceState.hasError) Color(0xFFEF5350) else Color(0xFFEDEDED)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MissionChip(stringResource(R.string.run_cockpit_link_label), operationsState.gatewayLabel)
                MissionChip(stringResource(R.string.run_cockpit_stack_label), operationsState.serviceLabel)
                MissionChip(
                    stringResource(R.string.run_cockpit_scene_label),
                    mapUiState.selectedLocation?.ifBlank { null } ?: unsetLocation
                )
                MissionChip(
                    stringResource(R.string.map_asset),
                    mapUiState.selectedMapAsset?.ifBlank { null } ?: unsetMap
                )
                MissionChip(
                    stringResource(R.string.run_cockpit_pose_label),
                    if (mapUiState.robotPoseOk) stringResource(R.string.map_localized) else stringResource(R.string.map_not_ready),
                    accent = if (mapUiState.robotPoseOk) Color(0xFF1BC47D) else Color(0xFFF5C451)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onReconnect, enabled = operationsState.canReconnect) {
                    Text(stringResource(R.string.connection_reconnect))
                }
                OutlinedButton(onClick = onRefreshGateway, enabled = operationsState.canRefreshGateway) {
                    Text(stringResource(R.string.home_refresh))
                }
                Button(
                    onClick = onPrepareRobot,
                    enabled = operationsState.canPrepareRobot,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF))
                ) {
                    Text(stringResource(R.string.system_robot_prep))
                }
                OutlinedButton(onClick = onDismissRobot, enabled = operationsState.canDismissRobot) {
                    Text(stringResource(R.string.system_robot_dismiss))
                }
                OutlinedButton(onClick = onOpenMapMatch, enabled = !mapUiState.isBusy) {
                    Text(stringResource(R.string.map_match_title))
                }
                Button(
                    onClick = onPrepareLocalization,
                    enabled = !mapUiState.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B5EFF))
                ) {
                    Text(stringResource(R.string.map_prepare))
                }
                OutlinedButton(onClick = onDismissLocalization, enabled = !mapUiState.isBusy) {
                    Text(stringResource(R.string.map_dismiss))
                }
            }

            HorizontalDivider(color = Color(0x10FFFFFF))

            handoffState?.let { handoff ->
                CompactHandoffSection(
                    state = handoff,
                    onPrimaryAction = onHandoffPrimaryAction
                )
                HorizontalDivider(color = Color(0x10FFFFFF))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChecklistPill(stringResource(R.string.run_checklist_gateway), checklistState.gatewayDetail, checklistState.gatewayReady)
                ChecklistPill(stringResource(R.string.run_checklist_robot), checklistState.robotDetail, checklistState.robotReady)
                ChecklistPill(stringResource(R.string.run_checklist_localization), checklistState.localizationDetail, checklistState.localizationReady)
                ChecklistPill(stringResource(R.string.run_checklist_session), checklistState.sessionDetail, checklistState.sessionReady)
                ChecklistPill(stringResource(R.string.run_checklist_safety), checklistState.safetyDetail, checklistState.safetyReady)
            }

            Text(
                text = if (checklistState.allReady) {
                    stringResource(R.string.run_checklist_ready, checklistState.readyCount, checklistState.totalCount)
                } else {
                    stringResource(R.string.run_checklist_pending, checklistState.readyCount, checklistState.totalCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (checklistState.allReady) Color(0xFF1BC47D) else Color(0xB3FFFFFF)
            )

            HorizontalDivider(color = Color(0x10FFFFFF))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = pickerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = pickerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
                TextButton(onClick = onOpenLibrary) {
                    Text(pickerOpenLibrary)
                }
            }

            if (librarySessions.isEmpty()) {
                Text(
                    text = pickerEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            } else {
                librarySessions.forEach { session ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = session.displayMotionTitle(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = session.displayMotionMeta(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xB3FFFFFF)
                            )
                        }
                        OutlinedButton(onClick = { onAttachLibrarySession(session) }) {
                            Text(stringResource(R.string.run_picker_attach))
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0x10FFFFFF))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localSessionsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onRefreshLocalSessions) {
                    Text(stringResource(R.string.library_local_sessions_refresh))
                }
            }

            if (localSessions.isEmpty()) {
                Text(
                    text = localSessionsEmptyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            } else {
                localSessions.take(3).forEach { session ->
                    LocalSessionCompactRow(
                        session = session,
                        previewButtonLabel = localPreviewActionLabel,
                        useButtonLabel = stringResource(R.string.run_use_local_session),
                        onPreview = { onPreviewLocalSession(session) },
                        onUse = { onAttachLocalSession(session) }
                    )
                }
            }

            HorizontalDivider(color = Color(0x10FFFFFF))

            if (workspaceState.phaseChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    workspaceState.phaseChips.forEach { chip ->
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
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = workspaceState.trajectoryText,
                onValueChange = onTrajectoryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.run_trajectory_label)) },
                singleLine = true
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                workspaceState.selectedTrajectoryLabel?.let { selected ->
                    MissionChip(
                        label = stringResource(R.string.run_selected_label),
                        value = selected
                    )
                }

                workspaceState.loadedTrajectory?.let { loaded ->
                    MissionChip(
                        label = stringResource(R.string.run_loaded_label),
                        value = loaded,
                        accent = if (workspaceState.loadedTrajectoryMatchesSelection) {
                            Color(0xFF1BC47D)
                        } else {
                            Color(0xFFF5C451)
                        }
                    )
                }

                workspaceState.loadedTrajectoryDetail?.let { detail ->
                    MissionChip(
                        label = stringResource(R.string.run_load),
                        value = detail,
                        accent = if (workspaceState.loadedTrajectoryMatchesSelection) {
                            Color(0xFF1BC47D)
                        } else {
                            Color(0xFFF5C451)
                        }
                    )
                }
            }

            if (workspaceState.progress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { workspaceState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(workspaceState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onLoad, enabled = workspaceState.canLoad) {
                    Text(stringResource(R.string.run_load))
                }
                Button(
                    onClick = onRun,
                    enabled = workspaceState.canRun,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (workspaceState.isPaused) stringResource(R.string.run_resume) else stringResource(R.string.run_run))
                }
                OutlinedButton(onClick = onPause, enabled = workspaceState.canPause) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.run_pause))
                }
                OutlinedButton(onClick = onStop, enabled = workspaceState.canStop) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.run_stop))
                }
                OutlinedButton(onClick = onHome, enabled = workspaceState.canHome) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.run_home))
                }
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (workspaceState.estopActive) Color(0xFFB91C1C) else Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (workspaceState.estopActive) {
                            stringResource(R.string.run_clear_estop)
                        } else {
                            stringResource(R.string.run_estop)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RunStateOverlay(
    title: String,
    detail: String?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC000000),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CompactHandoffSection(
    state: TrajectoryHandoffCardUiState,
    onPrimaryAction: () -> Unit
) {
    Surface(
        color = Color(0x10FFFFFF),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = stringResource(R.string.run_handoff_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.sourceName.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
                CompactReadinessBadge(readiness = state.readiness)
            }
            state.secondaryNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xCCFFFFFF)
                )
            }

            Button(
                onClick = onPrimaryAction,
                enabled = state.primaryButtonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A3FF),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF223341),
                    disabledContentColor = Color(0x66FFFFFF)
                )
            ) {
                Text(state.primaryButtonLabel)
            }
        }
    }
}

@Composable
private fun CompactReadinessBadge(
    readiness: TrajectoryHandoffReadiness
) {
    val accent = when (readiness) {
        TrajectoryHandoffReadiness.Ready -> Color(0xFF66BB6A)
        TrajectoryHandoffReadiness.Pending -> Color(0xFFF5C451)
        TrajectoryHandoffReadiness.Blocked -> Color(0xFFEF5350)
        TrajectoryHandoffReadiness.Unknown -> Color(0xFF90A4AE)
    }

    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Text(
            text = readiness.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
    }
}

@Composable
private fun LocalSessionCompactRow(
    session: LocalTrajectorySessionSummary,
    previewButtonLabel: String,
    useButtonLabel: String,
    onPreview: () -> Unit,
    onUse: () -> Unit
) {
    val sourceState = session.toLocalSessionListCardRowUiState()
    val accent = when (sourceState.sourceKind) {
        com.recomo.user.data.trajectory.LocalTrajectorySessionSourceKind.Assets -> Color(0xFF66D2FF)
        com.recomo.user.data.trajectory.LocalTrajectorySessionSourceKind.Filesystem -> Color(0xFF66BB6A)
    }

    Surface(
        color = Color(0x10FFFFFF),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = session.displayMotionTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = session.displayMotionMeta(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
                MissionChip(
                    label = stringResource(R.string.run_source_label),
                    value = sourceState.sourceLabel,
                    accent = accent
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview) {
                    Text(previewButtonLabel)
                }
                Button(
                    onClick = onUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White
                    )
                ) {
                    Text(useButtonLabel)
                }
            }
        }
    }
}

@Composable
private fun VideoMetricChip(
    label: String,
    value: String
) {
    Surface(
        color = Color(0xB0000000),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x99FFFFFF)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MissionChip(
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

@Composable
private fun ChecklistPill(
    label: String,
    detail: String,
    ready: Boolean
) {
    Surface(
        color = if (ready) Color(0x141BC47D) else Color(0x10FFFFFF),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (ready) Color(0x331BC47D) else Color(0x14FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (ready) Color(0xFF1BC47D) else Color(0xFFFFB74D),
                        shape = RoundedCornerShape(999.dp)
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelLarge,
                color = if (ready) Color(0xFF1BC47D) else Color(0xB3FFFFFF)
            )
        }
    }
}
