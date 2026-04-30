package com.recomo.user.ui.screens.smartfollow

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.common.tracking.TrackingOverlay
import com.recomo.user.R
import com.recomo.user.ui.screens.common.VideoPreviewContent
import com.recomo.user.ui.theme.StudioChrome

/**
 * Route-level composable used in the UserMainScreen `when (route)` block.
 * Bridges between the shell (video surface callbacks, navigation) and the
 * self-contained SmartFollowScreen.
 */
@Composable
fun SmartFollowRoute(
    onBack: () -> Unit,
    showBitmapFrame: Boolean = false,
    videoBitmap: Bitmap? = null,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    isTabletRecording: Boolean = false,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit = {},
    onVideoSurfaceDestroyed: () -> Unit = {},
    onToggleTabletRecording: () -> Unit = {}
) {
    val viewModel: SmartFollowViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.onEnterScreen()
        onDispose { viewModel.onExitScreen() }
    }

    SmartFollowScreen(
        uiState = uiState,
        showBitmapFrame = showBitmapFrame,
        videoBitmap = videoBitmap,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        isTabletRecording = isTabletRecording,
        onVideoSurfaceReady = onVideoSurfaceReady,
        onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
        onRoiSelected = viewModel::onRoiSelected,
        onStartFollow = viewModel::startFollow,
        onStopFollow = viewModel::stopFollow,
        onPauseFollow = viewModel::pauseFollow,
        onResumeFollow = viewModel::resumeFollow,
        onReselectTarget = viewModel::reselectTarget,
        onSelectPreset = viewModel::selectCompositionPreset,
        onMaxSpeedChange = viewModel::updateMaxSpeed,
        onFollowDistanceChange = viewModel::updateFollowDistance,
        onToggleTabletRecording = onToggleTabletRecording,
        onBack = {
            viewModel.onExitScreen()
            onBack()
        }
    )
}

@Composable
fun SmartFollowScreen(
    uiState: SmartFollowUiState,
    showBitmapFrame: Boolean,
    videoBitmap: Bitmap?,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    isTabletRecording: Boolean = false,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    onRoiSelected: (com.recomo.common.model.TargetRoi) -> Unit,
    onStartFollow: () -> Unit,
    onStopFollow: () -> Unit,
    onPauseFollow: () -> Unit,
    onResumeFollow: () -> Unit,
    onReselectTarget: () -> Unit,
    onSelectPreset: (CompositionPreset) -> Unit,
    onMaxSpeedChange: (Double) -> Unit,
    onFollowDistanceChange: (Double) -> Unit,
    onToggleTabletRecording: () -> Unit = {},
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioChrome.background)
    ) {
        // ── Top bar ──
        SmartFollowTopBar(
            state = uiState.state,
            isTabletRecording = isTabletRecording,
            onToggleTabletRecording = onToggleTabletRecording,
            onBack = onBack
        )

        // ── Video feed + tracking overlay ──
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val viewWidth = constraints.maxWidth.toFloat()
            val viewHeight = constraints.maxHeight.toFloat()

            // Determine actual video aspect ratio from available sources
            val videoAspect = when {
                showBitmapFrame && videoBitmap != null ->
                    videoBitmap.width.toFloat() / videoBitmap.height.toFloat().coerceAtLeast(1f)
                videoWidth > 0 && videoHeight > 0 ->
                    videoWidth.toFloat() / videoHeight.toFloat()
                else -> 0f  // unknown — will fill entire view
            }

            // Content area: where the video actually appears (after aspect-ratio fit).
            // Both SurfaceView (with aspectRatio modifier) and Bitmap (ContentScale.Fit)
            // center the video within the view with letterbox/pillarbox.
            val videoContentArea = if (videoAspect > 0f) {
                val viewAspect = if (viewHeight > 0) viewWidth / viewHeight else videoAspect
                if (videoAspect > viewAspect) {
                    // Video wider — letterbox top/bottom
                    val contentHeight = viewWidth / videoAspect
                    val offsetY = (viewHeight - contentHeight) / 2f
                    Rect(0f, offsetY, viewWidth, offsetY + contentHeight)
                } else {
                    // Video taller — pillarbox left/right
                    val contentWidth = viewHeight * videoAspect
                    val offsetX = (viewWidth - contentWidth) / 2f
                    Rect(offsetX, 0f, offsetX + contentWidth, viewHeight)
                }
            } else {
                // No resolution info yet — fill entire view (best effort)
                Rect(0f, 0f, viewWidth, viewHeight)
            }

            VideoPreviewContent(
                showBitmapFrame = showBitmapFrame,
                videoBitmap = videoBitmap,
                videoAspectRatio = videoAspect,
                contentDescription = "Smart Follow camera feed",
                onVideoSurfaceReady = onVideoSurfaceReady,
                onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
                overlay = {
                    TrackingOverlay(
                        subjectTracking = uiState.subjectTracking,
                        onRoiSelected = onRoiSelected,
                        videoContentArea = videoContentArea,
                        isConnected = uiState.isConnected,
                        enabled = uiState.state is SmartFollowState.Idle ||
                                uiState.state is SmartFollowState.Lost ||
                                uiState.state is SmartFollowState.LostWhileFollowing ||
                                uiState.state is SmartFollowState.Arrived,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )

            // Draw hint overlay when idle
            if (uiState.state is SmartFollowState.Idle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.smart_follow_draw_hint),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ── Status strip ──
        SmartFollowStatusStrip(state = uiState)

        // ── Composition preset chips ──
        CompositionPresetRow(
            selected = uiState.selectedPreset,
            compositionQuality = uiState.compositionQuality,
            onSelect = onSelectPreset
        )

        // ── Follow parameters ──
        SmartFollowParameterPanel(
            maxSpeed = uiState.maxSpeed,
            followDistance = uiState.followDistance,
            onMaxSpeedChange = onMaxSpeedChange,
            onFollowDistanceChange = onFollowDistanceChange
        )

        // ── Control buttons ──
        SmartFollowControls(
            state = uiState.state,
            onStartFollow = onStartFollow,
            onStopFollow = onStopFollow,
            onPauseFollow = onPauseFollow,
            onResumeFollow = onResumeFollow,
            onReselectTarget = onReselectTarget,
            onExit = onBack
        )
    }
}

@Composable
private fun SmartFollowTopBar(
    state: SmartFollowState,
    isTabletRecording: Boolean,
    onToggleTabletRecording: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StudioChrome.panel
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = StudioChrome.textPrimary
                )
            }

            Text(
                text = stringResource(R.string.route_smart_follow),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = StudioChrome.textPrimary
            )

            Spacer(Modifier.weight(1f))

            // Tablet recording toggle
            IconButton(onClick = onToggleTabletRecording) {
                Icon(
                    Icons.Outlined.FiberManualRecord,
                    contentDescription = "Toggle tablet recording",
                    tint = if (isTabletRecording) StudioChrome.danger else StudioChrome.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // State badge
            val (badgeColor, badgeLabel) = stateVisual(state)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun SmartFollowStatusStrip(state: SmartFollowUiState) {
    val confidence = when (val s = state.state) {
        is SmartFollowState.Tracking -> s.confidence
        is SmartFollowState.Following -> s.confidence
        else -> state.subjectTracking?.confidence ?: 0f
    }
    val confidencePercent = (confidence * 100).toInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StudioChrome.panel
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (color, label) = stateVisual(state.state)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = StudioChrome.textPrimary,
                fontWeight = FontWeight.Medium
            )

            if (state.state is SmartFollowState.Tracking ||
                state.state is SmartFollowState.Following
            ) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.smart_follow_confidence, confidencePercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioChrome.textMuted
                )
            }

            Spacer(Modifier.weight(1f))

            // Localization status (informational)
            val locColor = if (state.robotPoseOk) StudioChrome.success else StudioChrome.textMuted
            val locLabel = if (state.robotPoseOk) "Loc" else "No Loc"
            val mapLabel = state.currentMap?.takeIf { it.isNotBlank() }

            if (mapLabel != null) {
                Text(
                    text = mapLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textMuted
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(locColor)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = locLabel,
                style = MaterialTheme.typography.labelSmall,
                color = locColor
            )

            if (state.followActive || state.followPncState != FollowPncState.Idle) {
                Spacer(Modifier.width(10.dp))
                val pncColor = when (state.followPncState) {
                    FollowPncState.Running -> StudioChrome.success
                    FollowPncState.LostTarget -> StudioChrome.danger
                    FollowPncState.Error -> StudioChrome.danger
                    FollowPncState.Arrived -> StudioChrome.accentPurple
                    FollowPncState.Paused -> StudioChrome.warning
                    else -> StudioChrome.textMuted
                }
                Text(
                    text = "PnC: ${state.followPncState.labelZh}",
                    style = MaterialTheme.typography.labelSmall,
                    color = pncColor
                )
            }
        }
    }
}

@Composable
private fun CompositionPresetRow(
    selected: CompositionPreset,
    compositionQuality: CompositionQualityState?,
    onSelect: (CompositionPreset) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = StudioChrome.background
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionPreset.entries.forEach { preset ->
                    val isSelected = preset == selected
                    Surface(
                        onClick = { onSelect(preset) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) StudioChrome.accentBlue.copy(alpha = 0.2f)
                                else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) StudioChrome.accentBlue else StudioChrome.panelBorder
                        )
                    ) {
                        Text(
                            text = preset.labelZh,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) StudioChrome.accentBlue else StudioChrome.textMuted,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Composition quality gauge
                compositionQuality?.let { q ->
                    val qualityColor = when {
                        q.qualityPct >= 80 -> StudioChrome.success
                        q.qualityPct >= 50 -> StudioChrome.warning
                        else -> StudioChrome.danger
                    }
                    Text(
                        text = "${q.qualityPct}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = qualityColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Preset description
            Spacer(Modifier.height(2.dp))
            Text(
                text = selected.descZh,
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textMuted.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun stateVisual(state: SmartFollowState): Pair<Color, String> = when (state) {
    is SmartFollowState.Idle -> StudioChrome.textMuted to stringResource(R.string.smart_follow_state_idle)
    is SmartFollowState.Selecting -> StudioChrome.accentBlue to stringResource(R.string.smart_follow_state_selecting)
    is SmartFollowState.Pending -> StudioChrome.warning to stringResource(R.string.smart_follow_state_pending)
    is SmartFollowState.Tracking -> Color(0xFF00BCD4) to stringResource(R.string.smart_follow_state_tracking)
    is SmartFollowState.Following -> StudioChrome.success to stringResource(R.string.smart_follow_state_following)
    is SmartFollowState.Paused -> StudioChrome.warning to stringResource(R.string.smart_follow_state_paused)
    is SmartFollowState.Arrived -> StudioChrome.accentPurple to stringResource(R.string.smart_follow_state_arrived)
    is SmartFollowState.Lost -> StudioChrome.danger to stringResource(R.string.smart_follow_state_lost)
    is SmartFollowState.LostWhileFollowing -> StudioChrome.danger to stringResource(R.string.smart_follow_state_lost)
    is SmartFollowState.Error -> StudioChrome.danger to stringResource(R.string.smart_follow_state_error)
}
