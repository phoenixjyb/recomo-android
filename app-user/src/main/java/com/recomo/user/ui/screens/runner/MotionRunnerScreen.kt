package com.recomo.user.ui.screens.runner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.user.R
import com.recomo.user.ui.screens.run.RunVideoSurfaceView
import com.recomo.user.ui.theme.StudioChrome
import com.recomo.user.ui.theme.StudioMono
import com.recomo.user.ui.theme.StudioSans

@Composable
fun MotionRunnerScreen(
    state: MotionRunnerUiState,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit,
    onOverlayAction: (String) -> Unit = onAction,
    onEmergencyStopHoldStart: () -> Unit,
    onEmergencyStopHoldEnd: () -> Unit,
    onSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioChrome.screenBackgroundVerticalBrush)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            MotionRunnerLeftRail(
                state = state,
                modifier = Modifier
                    .width(264.dp)
                    .fillMaxHeight(),
                onAction = onAction
            )
            MotionRunnerVideoPane(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onOverlayAction = onOverlayAction,
                onSurfaceReady = onSurfaceReady,
                onSurfaceDestroyed = onSurfaceDestroyed
            )
            MotionRunnerRightRail(
                state = state,
                modifier = Modifier
                    .width(192.dp)
                    .fillMaxHeight(),
                onAction = onAction,
                onEmergencyStopHoldStart = onEmergencyStopHoldStart,
                onEmergencyStopHoldEnd = onEmergencyStopHoldEnd
            )
        }
    }
}

@Composable
private fun MotionRunnerLeftRail(
    state: MotionRunnerUiState,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit
) {
    PanelSurface(
        modifier = modifier,
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.leftFooterActions.firstOrNull { it.icon == MotionRunnerActionIcon.Back }?.let { back ->
                        ActionIconButton(action = back, onClick = onAction)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.motionName,
                            color = Color(0xFFE9EDF2),
                            fontFamily = StudioSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.motionSubtitle,
                            color = Color(0x668C98A7),
                            fontFamily = StudioMono,
                            fontSize = 10.sp
                        )
                    }
                }

                StateBadge(
                    label = state.playbackLabel,
                    tone = state.playbackTone,
                    leadingPulse = state.playbackState == MotionRunnerPlaybackState.Running
                )

                state.playbackDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        color = Color(0x99E3E7EC),
                        fontFamily = StudioSans,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                ProgressBlock(
                    progress = state.progress,
                    elapsedLabel = state.elapsedLabel,
                    remainingLabel = state.remainingLabel,
                    keyframeLabel = state.keyframeLabel,
                    speedLabel = state.speedLabel,
                    metrics = state.leftMetrics
                )
            }

            Text(
                text = state.timelineTitle,
                color = Color(0x558C98A7),
                fontFamily = StudioMono,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.timelineItems) { item ->
                    TimelineRow(item = item)
                }
            }

            if (state.leftFooterActions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.leftFooterActions
                        .filterNot { it.icon == MotionRunnerActionIcon.Back }
                        .forEach { action ->
                            RunnerActionButton(
                                action = action,
                                modifier = Modifier.weight(1f),
                                onClick = onAction
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun MotionRunnerVideoPane(
    state: MotionRunnerUiState,
    modifier: Modifier = Modifier,
    onOverlayAction: (String) -> Unit,
    onSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val overlay = state.video.overlay
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(StudioChrome.topBar)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LivePill(label = state.video.headerPillLabel)
                state.video.headerCaption?.takeIf { it.isNotBlank() }?.let { caption ->
                    Text(
                        text = caption,
                        color = StudioChrome.textMuted.copy(alpha = 0.8f),
                        fontFamily = StudioMono,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                state.video.topMetrics.forEach { metric ->
                    TopMetric(metric = metric)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            VideoBackdrop(
                state = state.video,
                modifier = Modifier.fillMaxSize(),
                onSurfaceReady = onSurfaceReady,
                onSurfaceDestroyed = onSurfaceDestroyed
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.video.supportBadges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.video.supportBadges.forEach { badge ->
                            OverlayBadge(badge = badge)
                        }
                    }
                }
            }

            if (state.video.supportCards.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.video.supportCards.forEach { card ->
                        SupportCard(card = card)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = overlay != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (overlay != null) {
                    FeedOverlay(
                        overlay = overlay,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(overlayScrimColor(overlay.visual)),
                        onOverlayAction = onOverlayAction
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(StudioChrome.accentBlue.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(progressBrush(state.playbackTone))
                )
            }
        }
    }
}

@Composable
private fun MotionRunnerRightRail(
    state: MotionRunnerUiState,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit,
    onEmergencyStopHoldStart: () -> Unit,
    onEmergencyStopHoldEnd: () -> Unit
) {
    PanelSurface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.rightRailTitle,
                color = Color(0x66FF7A7A),
                fontFamily = StudioMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            EmergencyStopControl(
                state = state.safety,
                onHoldStart = onEmergencyStopHoldStart,
                onHoldEnd = onEmergencyStopHoldEnd
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.rightRailActions.forEach { action ->
                    RunnerActionButton(
                        action = action,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onAction
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.rightRailStats.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StudioChrome.radiusXl))
                        .background(StudioChrome.panelMuted)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.rightRailStats.forEach { metric ->
                        RailStat(metric = metric)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoBackdrop(
    state: MotionRunnerVideoUiState,
    modifier: Modifier = Modifier,
    onSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    Box(modifier = modifier.background(Color.Black)) {
        if (!state.showSurfaceFeed && state.bitmapFrame != null) {
            androidx.compose.foundation.Image(
                bitmap = state.bitmapFrame.asImageBitmap(),
                contentDescription = state.headerPillLabel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    RunVideoSurfaceView(context).apply {
                        setZOrderMediaOverlay(false)
                        setOnSurfaceReadyListener(onSurfaceReady)
                        setOnSurfaceDestroyedListener(onSurfaceDestroyed)
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x24000000), Color.Transparent, Color(0x40000000))
                    )
                )
        )

        if (state.bitmapFrame == null && !state.showSurfaceFeed) {
            EmptyFeedMessage(
                title = state.emptyTitle,
                detail = state.emptyDetail,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun FeedOverlay(
    overlay: MotionRunnerFeedOverlayUiState,
    modifier: Modifier = Modifier,
    onOverlayAction: (String) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = overlayIcon(overlay.visual),
            contentDescription = overlay.title,
            tint = overlayColor(overlay.visual),
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = overlay.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        overlay.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                color = Color(0xAACFD6E0),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        if (!overlay.actionId.isNullOrBlank() && !overlay.actionLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onOverlayAction(overlay.actionId) },
                enabled = overlay.actionEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A3FF),
                    contentColor = Color.White
                )
            ) {
                Text(overlay.actionLabel)
            }
        }
    }
}

@Composable
private fun EmergencyStopControl(
    state: MotionRunnerSafetyUiState,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.holdProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "runnerEstopProgress"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (state.isHolding) 0.92f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "runnerEstopScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = state.title,
            color = Color(0x88FF7A7A),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(CircleShape)
                .background(Color(0x12000000))
                .padding(8.dp)
        ) {
            CircularProgressRing(
                progress = animatedProgress,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(84.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            state.isLatched -> Color(0x55B91C1C)
                            state.isHolding -> Color(0xFFDC2626)
                            else -> Color(0xCCEF4444)
                        }
                    )
                    .holdable(
                        enabled = state.enabled && !state.isLatched,
                        onHoldStart = onHoldStart,
                        onHoldEnd = onHoldEnd
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = state.label,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = state.label,
                        color = Color.White,
                        fontFamily = StudioMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        state.hint?.takeIf { it.isNotBlank() }?.let { hint ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = hint,
                color = Color(0x669CA5B1),
                fontFamily = StudioSans,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CircularProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = 6.dp.toPx()
        val diameter = size.minDimension - stroke
        drawCircle(
            color = Color(0x22EF4444),
            radius = diameter / 2f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(Color(0xFFFF6B6B), Color(0xFFDC2626))),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

@Composable
private fun ProgressBlock(
    progress: Float,
    elapsedLabel: String,
    remainingLabel: String,
    keyframeLabel: String,
    speedLabel: String,
    metrics: List<MotionRunnerMetricUiState>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.runner_progress), color = Color(0x558C98A7), fontFamily = StudioMono, fontSize = 10.sp)
                Text("${(progress.coerceIn(0f, 1f) * 100f).toInt()}%", color = Color(0x99E9EDF2), fontFamily = StudioMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x1400A3FF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(progressBrush(MotionRunnerTone.Primary))
                )
            }
        }

        val fallback = listOf(
            MotionRunnerMetricUiState("Elapsed", elapsedLabel),
            MotionRunnerMetricUiState("Remaining", remainingLabel),
            MotionRunnerMetricUiState("Keyframe", keyframeLabel),
            MotionRunnerMetricUiState("Speed", speedLabel)
        )
        MetricGrid(metrics = if (metrics.isEmpty()) fallback else metrics)
    }
}

@Composable
private fun MetricGrid(metrics: List<MotionRunnerMetricUiState>) {
    val cells = remember(metrics) { metrics.take(4) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x110E141A),
                        border = BorderStroke(1.dp, Color(0x10FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(metric.label, color = Color(0x558C98A7), fontFamily = StudioMono, fontSize = 9.sp)
                            Text(
                                text = metric.value,
                                color = toneColor(metric.tone),
                                fontFamily = StudioMono,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TimelineRow(item: MotionRunnerTimelineItemUiState) {
    val pulse = rememberInfiniteTransition(label = "timelinePulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timelineAlpha"
    )

    Surface(
        color = if (item.isCurrent) Color(0x1200A3FF) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                item.isCurrent -> Color(0x2200A3FF)
                item.isComplete -> Color(0x141BC47D)
                else -> Color.Transparent
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            item.isCurrent -> Color(0xFF00A3FF)
                            item.isComplete -> Color(0x181BC47D)
                            else -> Color.Transparent
                        }
                    )
                    .then(
                        if (!item.isCurrent && !item.isComplete) Modifier
                            .background(Color.Transparent, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isComplete && !item.isCurrent) "✓" else item.indexLabel,
                    color = when {
                        item.isCurrent -> Color.White
                        item.isComplete -> Color(0xFF1BC47D)
                        else -> Color(0x558C98A7)
                    },
                    fontFamily = StudioMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
                Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.timeLabel,
                color = if (item.isCurrent) Color(0xBFE8F6FF) else Color(0x668C98A7),
                fontFamily = StudioMono,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            if (item.isCurrent) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A3FF).copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun StateBadge(
    label: String,
    tone: MotionRunnerTone,
    leadingPulse: Boolean
) {
    val pulse = rememberInfiniteTransition(label = "statePulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statePulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = toneContainerColor(tone),
        border = BorderStroke(1.dp, toneBorderColor(tone))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingPulse) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(toneColor(tone).copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = toneColor(tone),
                fontFamily = StudioMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LivePill(label: String) {
    val pulse = rememberInfiniteTransition(label = "livePill")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePillAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x18EF4444),
        border = BorderStroke(1.dp, Color(0x22EF4444))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = alpha))
            )
            Text(label, color = Color(0xFFFB7185), fontFamily = StudioMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TopMetric(metric: MotionRunnerMetricUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        metric.icon?.let { icon ->
            Icon(
                imageVector = metricIcon(icon),
                contentDescription = metric.label,
                tint = toneColor(metric.tone),
                modifier = Modifier.size(14.dp)
            )
        }
        Text(metric.value, color = Color(0xAACFD6E0), fontFamily = StudioMono, fontSize = 11.sp)
    }
}

@Composable
private fun OverlayBadge(badge: MotionRunnerSupportBadgeUiState) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = toneContainerColor(badge.tone),
        border = BorderStroke(1.dp, toneBorderColor(badge.tone))
    ) {
        Text(
            text = badge.label,
            color = toneColor(badge.tone),
            fontFamily = StudioMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun SupportCard(card: MotionRunnerSupportCardUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xA0101319),
        border = BorderStroke(1.dp, toneBorderColor(card.tone))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(card.title, color = Color(0x779CA5B1), fontFamily = StudioMono, fontSize = 10.sp)
            Text(card.value, color = toneColor(card.tone), fontFamily = StudioMono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            card.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(detail, color = Color(0x99E3E7EC), fontFamily = StudioSans, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RailStat(metric: MotionRunnerMetricUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metric.icon?.let { icon ->
                Icon(
                    imageVector = metricIcon(icon),
                    contentDescription = metric.label,
                    tint = Color(0x669CA5B1),
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(metric.label, color = Color(0x669CA5B1), fontFamily = StudioMono, fontSize = 10.sp)
        }
        Text(metric.value, color = toneColor(metric.tone), fontFamily = StudioMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RunnerActionButton(
    action: MotionRunnerActionUiState,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    val colors = when (action.style) {
        MotionRunnerActionStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00A3FF),
            contentColor = Color.White
        )
        MotionRunnerActionStyle.Danger -> ButtonDefaults.buttonColors(
            containerColor = Color(0xCCDC2626),
            contentColor = Color.White
        )
        MotionRunnerActionStyle.Subtle -> ButtonDefaults.buttonColors(
            containerColor = Color(0x10FFFFFF),
            contentColor = StudioChrome.textStrong
        )
        MotionRunnerActionStyle.Secondary -> ButtonDefaults.buttonColors(
            containerColor = Color(0x130F141A),
            contentColor = StudioChrome.textStrong
        )
    }
    val border = when (action.style) {
        MotionRunnerActionStyle.Primary -> null
        MotionRunnerActionStyle.Danger -> BorderStroke(1.dp, Color(0x33EF4444))
        else -> BorderStroke(1.dp, Color(0x14FFFFFF))
    }

    if (action.style == MotionRunnerActionStyle.Secondary || action.style == MotionRunnerActionStyle.Subtle) {
        OutlinedButton(
            onClick = { onClick(action.id) },
            enabled = action.enabled,
            modifier = modifier.heightIn(min = 38.dp),
            border = border ?: BorderStroke(1.dp, Color(0x14FFFFFF)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = StudioChrome.textStrong,
                disabledContentColor = StudioChrome.textStrong.copy(alpha = 0.4f)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ActionLabel(action = action)
        }
    } else {
        Button(
            onClick = { onClick(action.id) },
            enabled = action.enabled,
            modifier = modifier.heightIn(min = 38.dp),
            border = border,
            colors = when (action.style) {
                MotionRunnerActionStyle.Primary -> ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A3FF),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0x4400A3FF),
                    disabledContentColor = Color(0x99FFFFFF)
                )
                MotionRunnerActionStyle.Danger -> ButtonDefaults.buttonColors(
                    containerColor = Color(0xCCDC2626),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0x44DC2626),
                    disabledContentColor = Color(0x99FFFFFF)
                )
                else -> colors
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ActionLabel(action = action)
        }
    }
}

@Composable
private fun ActionLabel(action: MotionRunnerActionUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        action.icon?.let { icon ->
            Icon(
                imageVector = actionIcon(icon),
                contentDescription = action.label,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = action.label,
            fontFamily = StudioMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionIconButton(action: MotionRunnerActionUiState, onClick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x100F141A),
        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .holdable(
                enabled = action.enabled,
                onHoldStart = { onClick(action.id) },
                onHoldEnd = {}
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            action.icon?.let {
                Icon(
                    imageVector = actionIcon(it),
                    contentDescription = action.label,
                    tint = Color(0xCDE7EEF5),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyFeedMessage(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = title,
            tint = Color(0x1FFFFFFF),
            modifier = Modifier.size(56.dp)
        )
        Text(title, color = StudioChrome.textStrong, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        detail?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0x669CA5B1), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.holdable(
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
): Modifier {
    return this.then(
        Modifier.pointerInteropFilter { event ->
            if (!enabled) {
                return@pointerInteropFilter false
            }
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> onHoldStart()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL,
                android.view.MotionEvent.ACTION_OUTSIDE -> onHoldEnd()
            }
            true
        }
    )
}

@Composable
private fun PanelSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = StudioChrome.panel,
        border = BorderStroke(1.dp, StudioChrome.panelBorder)
    ) {
        content()
    }
}

private fun progressBrush(tone: MotionRunnerTone): Brush {
    return when (tone) {
        MotionRunnerTone.Success -> Brush.horizontalGradient(listOf(StudioChrome.success, Color(0xFF10B981)))
        MotionRunnerTone.Warning -> Brush.horizontalGradient(listOf(Color(0xFFFBBF24), StudioChrome.warning))
        MotionRunnerTone.Danger -> Brush.horizontalGradient(listOf(StudioChrome.dangerSoft, Color(0xFFDC2626)))
        else -> StudioChrome.brandGradient
    }
}

private fun toneColor(tone: MotionRunnerTone): Color = when (tone) {
    MotionRunnerTone.Primary -> Color(0xFF66D2FF)
    MotionRunnerTone.Success -> StudioChrome.success
    MotionRunnerTone.Warning -> Color(0xFFFBBF24)
    MotionRunnerTone.Danger -> StudioChrome.dangerSoft
    MotionRunnerTone.Neutral -> StudioChrome.textPrimary
}

private fun toneContainerColor(tone: MotionRunnerTone): Color = when (tone) {
    MotionRunnerTone.Primary -> StudioChrome.accentBlue.copy(alpha = 0.10f)
    MotionRunnerTone.Success -> StudioChrome.success.copy(alpha = 0.10f)
    MotionRunnerTone.Warning -> StudioChrome.warning.copy(alpha = 0.10f)
    MotionRunnerTone.Danger -> StudioChrome.danger.copy(alpha = 0.12f)
    MotionRunnerTone.Neutral -> StudioChrome.panelSoft
}

private fun toneBorderColor(tone: MotionRunnerTone): Color = when (tone) {
    MotionRunnerTone.Primary -> StudioChrome.accentBlue.copy(alpha = 0.20f)
    MotionRunnerTone.Success -> StudioChrome.success.copy(alpha = 0.20f)
    MotionRunnerTone.Warning -> StudioChrome.warning.copy(alpha = 0.20f)
    MotionRunnerTone.Danger -> StudioChrome.danger.copy(alpha = 0.20f)
    MotionRunnerTone.Neutral -> StudioChrome.panelBorder
}

private fun overlayScrimColor(visual: MotionRunnerOverlayVisual): Color = when (visual) {
    MotionRunnerOverlayVisual.Paused -> Color(0x55000000)
    MotionRunnerOverlayVisual.Completed -> Color(0x66000000)
    MotionRunnerOverlayVisual.Stopped -> Color(0x66A61B1B)
    MotionRunnerOverlayVisual.Error -> Color(0x77A61B1B)
}

private fun overlayIcon(visual: MotionRunnerOverlayVisual): ImageVector = when (visual) {
    MotionRunnerOverlayVisual.Paused -> Icons.Default.Pause
    MotionRunnerOverlayVisual.Completed -> Icons.Default.CheckCircle
    MotionRunnerOverlayVisual.Stopped -> Icons.Default.Warning
    MotionRunnerOverlayVisual.Error -> Icons.Default.Warning
}

private fun overlayColor(visual: MotionRunnerOverlayVisual): Color = when (visual) {
    MotionRunnerOverlayVisual.Paused -> Color(0xFFFBBF24)
    MotionRunnerOverlayVisual.Completed -> Color(0xFF66D2FF)
    MotionRunnerOverlayVisual.Stopped -> StudioChrome.dangerSoft
    MotionRunnerOverlayVisual.Error -> StudioChrome.dangerSoft
}

private fun metricIcon(icon: MotionRunnerMetricIcon): ImageVector = when (icon) {
    MotionRunnerMetricIcon.Signal -> Icons.Default.Wifi
    MotionRunnerMetricIcon.Battery -> Icons.Default.BatteryFull
    MotionRunnerMetricIcon.Latency -> Icons.Default.FlashOn
    MotionRunnerMetricIcon.Speed -> Icons.Default.FlashOn
    MotionRunnerMetricIcon.Clock -> Icons.Default.Timer
    MotionRunnerMetricIcon.Shield -> Icons.Default.Security
    MotionRunnerMetricIcon.Temperature -> Icons.Default.Thermostat
    MotionRunnerMetricIcon.Power -> Icons.Default.Warning
}

private fun actionIcon(icon: MotionRunnerActionIcon): ImageVector = when (icon) {
    MotionRunnerActionIcon.Back -> Icons.Default.ArrowBack
    MotionRunnerActionIcon.Play -> Icons.Default.PlayArrow
    MotionRunnerActionIcon.Pause -> Icons.Default.Pause
    MotionRunnerActionIcon.Stop -> Icons.Default.Stop
    MotionRunnerActionIcon.Home -> Icons.Default.Home
    MotionRunnerActionIcon.Restart -> Icons.Default.Refresh
    MotionRunnerActionIcon.Check -> Icons.Default.CheckCircle
    MotionRunnerActionIcon.Shield -> Icons.Default.Security
}
