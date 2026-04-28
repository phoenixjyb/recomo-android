package com.recomo.user.ui.screens.smartfollow

import android.graphics.Bitmap
import androidx.compose.foundation.background
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
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit = {},
    onVideoSurfaceDestroyed: () -> Unit = {}
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
        onVideoSurfaceReady = onVideoSurfaceReady,
        onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
        onRoiSelected = viewModel::onRoiSelected,
        onStartFollow = viewModel::startFollow,
        onStopFollow = viewModel::stopFollow,
        onPauseFollow = viewModel::pauseFollow,
        onResumeFollow = viewModel::resumeFollow,
        onReselectTarget = viewModel::reselectTarget,
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
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    onRoiSelected: (com.recomo.common.model.TargetRoi) -> Unit,
    onStartFollow: () -> Unit,
    onStopFollow: () -> Unit,
    onPauseFollow: () -> Unit,
    onResumeFollow: () -> Unit,
    onReselectTarget: () -> Unit,
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
            onBack = onBack
        )

        // ── Video feed + tracking overlay ──
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Compute video content area for correct bbox coordinate conversion.
            // All our cameras are 16:9 (1920x1080 or 1280x720).
            val videoAspect = 16f / 9f
            val viewWidth = constraints.maxWidth.toFloat()
            val viewHeight = constraints.maxHeight.toFloat()
            val viewAspect = if (viewHeight > 0) viewWidth / viewHeight else videoAspect

            val videoContentArea = if (videoAspect > viewAspect) {
                // Video wider than view — letterbox top/bottom
                val contentHeight = viewWidth / videoAspect
                val offsetY = (viewHeight - contentHeight) / 2f
                Rect(0f, offsetY, viewWidth, offsetY + contentHeight)
            } else {
                // Video taller than view — pillarbox left/right
                val contentWidth = viewHeight * videoAspect
                val offsetX = (viewWidth - contentWidth) / 2f
                Rect(offsetX, 0f, offsetX + contentWidth, viewHeight)
            }

            VideoPreviewContent(
                showBitmapFrame = showBitmapFrame,
                videoBitmap = videoBitmap,
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

            if (state.pncFsmState > 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "FSM: ${state.pncFsmState}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textMuted
                )
            }
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
