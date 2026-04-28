package com.recomo.user.ui.screens.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.user.R
import com.recomo.user.ui.theme.RecomoUserTheme
import com.recomo.user.ui.theme.StudioChrome
import com.recomo.user.ui.theme.StudioMono
import kotlin.math.min

private val MainControlBackground = StudioChrome.background
private val MainControlPanel = StudioChrome.panel
private val MainControlPanelBorder = StudioChrome.panelBorder
private val MainControlPanelSoft = StudioChrome.panelSoft
private val MainControlBrand = StudioChrome.accentBlue
private val MainControlSecondary = StudioChrome.accentPurple
private val MainControlSuccess = StudioChrome.success
private val MainControlWarning = StudioChrome.warning
private val MainControlDanger = StudioChrome.danger

@Composable
fun MainControlScreen(
    state: MainControlUiState,
    modifier: Modifier = Modifier,
    previewContent: @Composable BoxScope.() -> Unit = {
        DefaultMainControlPreview(state.preview)
    },
    onToolClick: (MainControlToolKey) -> Unit = {},
    onShortcutClick: (MainControlShortcutKey) -> Unit = {},
    onTransportControlClick: (MainControlTransportControlKey) -> Unit = {},
    onPrimaryActionClick: () -> Unit = {},
    onRecentMotionClick: (String) -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(brush = StudioChrome.screenBackgroundBrush)
    ) {
        val showSidebar = maxWidth >= 920.dp && state.sidebar != null
        val railWidth = if (maxWidth < 720.dp) 52.dp else 56.dp
        val compactDock = maxWidth < 900.dp

        Row(modifier = Modifier.fillMaxSize()) {
            MainControlToolRail(
                primaryTools = state.primaryTools,
                secondaryTools = state.secondaryTools,
                width = railWidth,
                onToolClick = onToolClick
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                MainControlTelemetryStrip(header = state.header)

                MainControlPreviewStage(
                    state = state,
                    showFloatingSidebar = !showSidebar,
                    modifier = Modifier.weight(1f),
                    previewContent = previewContent
                )

                MainControlBottomDock(
                    state = state.bottomDock,
                    compact = compactDock,
                    onShortcutClick = onShortcutClick,
                    onTransportControlClick = onTransportControlClick,
                    onPrimaryActionClick = onPrimaryActionClick
                )
            }

            if (showSidebar) {
                MainControlSidebar(
                    state = state.sidebar,
                    modifier = Modifier.width(206.dp),
                    onRecentMotionClick = onRecentMotionClick
                )
            }
        }
    }
}

@Composable
private fun MainControlToolRail(
    primaryTools: List<MainControlToolButtonUiState>,
    secondaryTools: List<MainControlToolButtonUiState>,
    width: Dp,
    onToolClick: (MainControlToolKey) -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MainControlPanel)
            .border(1.dp, MainControlPanelBorder, RoundedCornerShape(topEnd = StudioChrome.radiusXl, bottomEnd = StudioChrome.radiusXl))
            .padding(vertical = 12.dp, horizontal = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(StudioChrome.radiusSm))
                .background(StudioChrome.brandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "R",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        primaryTools.forEach { tool ->
            MainControlToolButton(
                state = tool,
                onClick = { onToolClick(tool.key) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        secondaryTools.forEach { tool ->
            MainControlToolButton(
                state = tool,
                onClick = { onToolClick(tool.key) }
            )
        }
    }
}

@Composable
private fun MainControlToolButton(
    state: MainControlToolButtonUiState,
    onClick: () -> Unit
) {
    val background = if (state.selected) MainControlBrand.copy(alpha = 0.12f) else Color.Transparent
    val tint = if (state.selected) MainControlBrand else Color.White.copy(alpha = if (state.enabled) 0.34f else 0.16f)

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(StudioChrome.radiusSm))
            .background(background)
            .clickable(
                enabled = state.enabled,
                indication = null,
                interactionSource = MutableInteractionSource(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = state.key.icon(),
            contentDescription = state.label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MainControlTelemetryStrip(header: MainControlHeaderUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(StudioChrome.topBar)
            .border(1.dp, MainControlPanelBorder)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (header.recordingLabel != null) {
                MainControlPill(
                    label = header.recordingLabel,
                    tone = MainControlTone.Danger,
                    leadingDot = true
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MainControlSuccess)
                )
                Text(
                    text = header.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioChrome.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            header.telemetry.forEach { metric ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = metric.value,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = StudioMono),
                        color = metric.tone.color(),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = metric.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioChrome.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MainControlPreviewStage(
    state: MainControlUiState,
    showFloatingSidebar: Boolean,
    modifier: Modifier,
    previewContent: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        previewContent()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent, Color.Black.copy(alpha = 0.42f))
                    )
                )
        )

        if (state.preview.showReticle) {
            CenterReticle(modifier = Modifier.align(Alignment.Center))
        }

        if (state.preview.showGrid) {
            RuleOfThirdsGrid(modifier = Modifier.fillMaxSize())
        }

        val hasPreviewOverlay = state.preview.title.isNotBlank() ||
            !state.preview.detail.isNullOrBlank() ||
            !state.preview.statusLabel.isNullOrBlank()
        if (hasPreviewOverlay) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.preview.title.isNotBlank()) {
                    Text(
                        text = state.preview.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioChrome.textPrimary
                    )
                }
                state.preview.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = StudioMono),
                        color = StudioChrome.textTertiary
                    )
                }
                state.preview.statusLabel?.takeIf { it.isNotBlank() }?.let { status ->
                    MainControlPill(label = status, tone = MainControlTone.Success)
                }
            }
        }

        if (state.preview.cameraParams.isNotEmpty()) {
            CameraParameterStrip(
                params = state.preview.cameraParams,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 16.dp, end = 18.dp)
            )
        }

        HorizonIndicator(
            label = state.preview.horizonLabel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        if (showFloatingSidebar) {
            state.sidebar?.let { sidebar ->
                CompactSidebarOverlay(
                    state = sidebar,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MainControlBottomDock(
    state: MainControlBottomDockUiState,
    compact: Boolean,
    onShortcutClick: (MainControlShortcutKey) -> Unit,
    onTransportControlClick: (MainControlTransportControlKey) -> Unit,
    onPrimaryActionClick: () -> Unit
) {
    if (compact) {
        Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MainControlPanel)
                    .border(1.dp, MainControlPanelBorder)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShortcutRow(state.shortcuts, onShortcutClick)
            TransportRow(state.transportControls, onTransportControlClick)
            state.primaryAction?.let {
                PrimaryDockAction(
                    state = it,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPrimaryActionClick
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MainControlPanel)
                .border(1.dp, MainControlPanelBorder)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShortcutRow(
                shortcuts = state.shortcuts,
                onShortcutClick = onShortcutClick,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.weight(1f))

            TransportRow(
                controls = state.transportControls,
                onTransportControlClick = onTransportControlClick
            )

            Spacer(modifier = Modifier.weight(1f))

            state.primaryAction?.let {
                PrimaryDockAction(
                    state = it,
                    onClick = onPrimaryActionClick
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcuts: List<MainControlShortcutUiState>,
    onShortcutClick: (MainControlShortcutKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        shortcuts.forEach { shortcut ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = shortcut.enabled,
                        indication = null,
                        interactionSource = MutableInteractionSource(),
                        onClick = { onShortcutClick(shortcut.key) }
                    ),
                shape = RoundedCornerShape(14.dp),
                color = shortcut.tone.cardColor(),
                contentColor = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, shortcut.tone.borderColor())
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(shortcut.tone.iconBackgroundColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = shortcut.key.icon(),
                            contentDescription = shortcut.title,
                            tint = shortcut.tone.color(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = shortcut.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (shortcut.tone == MainControlTone.Brand) MainControlBrand.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = shortcut.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioChrome.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportRow(
    controls: List<MainControlTransportControlUiState>,
    onTransportControlClick: (MainControlTransportControlKey) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        controls.forEach { control ->
            if (control.key == MainControlTransportControlKey.Record) {
                RecordTransportButton(control = control, onClick = { onTransportControlClick(control.key) })
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(StudioChrome.radiusSm))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, MainControlPanelBorder, RoundedCornerShape(StudioChrome.radiusSm))
                        .clickable(
                            enabled = control.enabled,
                            indication = null,
                            interactionSource = MutableInteractionSource(),
                            onClick = { onTransportControlClick(control.key) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = control.key.icon(control.selected),
                        contentDescription = control.label,
                        tint = if (control.selected) MainControlBrand else Color.White.copy(alpha = 0.34f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordTransportButton(
    control: MainControlTransportControlUiState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (control.selected) MainControlDanger.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .clickable(
                enabled = control.enabled,
                indication = null,
                interactionSource = MutableInteractionSource(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (control.selected) 16.dp else 24.dp)
                .clip(if (control.selected) RoundedCornerShape(5.dp) else CircleShape)
                .background(MainControlDanger)
        )
    }
}

@Composable
private fun PrimaryDockAction(
    state: MainControlPrimaryActionUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        enabled = state.enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(StudioChrome.radiusLg))
                .background(StudioChrome.brandGradient)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                state.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioChrome.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MainControlSidebar(
    state: MainControlSidebarUiState?,
    modifier: Modifier,
    onRecentMotionClick: (String) -> Unit
) {
    if (state == null) return

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MainControlPanel)
            .border(1.dp, MainControlPanelBorder)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MainControlSuccess)
            )
        Text(
            text = state.connectionLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textTertiary,
            fontWeight = FontWeight.SemiBold
        )
        }

        Spacer(modifier = Modifier.height(10.dp))

        StatusMetricGrid(metrics = state.statusMetrics)

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.main_pose_guide),
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        JointGrid(joints = state.joints)

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.main_motions),
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.recentMotions.forEach { motion ->
                RecentMotionButton(state = motion, onClick = { onRecentMotionClick(motion.id) })
            }
        }
    }
}

@Composable
private fun CompactSidebarOverlay(
    state: MainControlSidebarUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(StudioChrome.radiusLg))
            .background(StudioChrome.surfaceScrim)
            .border(1.dp, MainControlPanelBorder, RoundedCornerShape(StudioChrome.radiusLg))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = state.connectionLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textTertiary
        )
        state.statusMetrics.take(3).forEach { metric ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.30f)
                )
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = StudioMono),
                    color = metric.tone.color(),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StatusMetricGrid(metrics: List<MainControlMetricUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { metric ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.02f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MainControlPanelSoft)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = metric.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.18f)
                            )
                            Text(
                                text = metric.value,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = StudioMono),
                                fontWeight = FontWeight.SemiBold,
                                color = metric.tone.color()
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun JointGrid(joints: List<MainControlJointUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        joints.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { joint ->
                    JointGauge(state = joint)
                }
            }
        }
    }
}

@Composable
private fun JointGauge(state: MainControlJointUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(42.dp).rotate(-90f)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = state.tone.color(),
                    startAngle = 0f,
                    sweepAngle = 360f * state.fillFraction.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = state.angleLabel,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = Color.White.copy(alpha = 0.62f),
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = state.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.24f)
        )
    }
}

@Composable
private fun RecentMotionButton(
    state: MainControlRecentMotionUiState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                indication = null,
                interactionSource = MutableInteractionSource(),
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(state.tone.iconBackgroundColor()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = state.title,
                tint = state.tone.color(),
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.detail,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun CameraParameterStrip(
    params: List<MainControlMetricUiState>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .border(1.dp, MainControlPanelSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        params.forEach { param ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = param.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.22f)
                )
                Text(
                    text = param.value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = StudioMono),
                    color = Color.White.copy(alpha = 0.78f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun HorizonIndicator(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.52f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MainControlPanelSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Canvas(modifier = Modifier.width(54.dp).height(24.dp)) {
                val centerY = size.height / 2f
                val centerX = size.width / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(4f, centerY),
                    end = Offset(centerX - 8.dp.toPx(), centerY),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(centerX + 8.dp.toPx(), centerY),
                    end = Offset(size.width - 4f, centerY),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = MainControlBrand.copy(alpha = 0.72f),
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawLine(
                    color = MainControlBrand.copy(alpha = 0.72f),
                    start = Offset(centerX - 4.dp.toPx(), centerY),
                    end = Offset(centerX + 4.dp.toPx(), centerY),
                    strokeWidth = 1.dp.toPx()
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.34f)
            )
        }
    }
}

@Composable
private fun CenterReticle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(64.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val short = 12.dp.toPx()
        val gap = 8.dp.toPx()
        val lineColor = Color.White.copy(alpha = 0.16f)

        drawLine(lineColor, Offset(center.x, 0f), Offset(center.x, short), strokeWidth = 1.dp.toPx())
        drawLine(lineColor, Offset(center.x, size.height - short), Offset(center.x, size.height), strokeWidth = 1.dp.toPx())
        drawLine(lineColor, Offset(0f, center.y), Offset(short, center.y), strokeWidth = 1.dp.toPx())
        drawLine(lineColor, Offset(size.width - short, center.y), Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
        drawCircle(Color.White.copy(alpha = 0.25f), radius = gap / 2f, center = center)
    }
}

@Composable
private fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val color = Color.White.copy(alpha = 0.08f)
        val stroke = 1.dp.toPx()
        val thirdsX = listOf(width / 3f, width * 2f / 3f)
        val thirdsY = listOf(height / 3f, height * 2f / 3f)

        thirdsX.forEach { x ->
            drawLine(color = color, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = stroke)
        }
        thirdsY.forEach { y ->
            drawLine(color = color, start = Offset(0f, y), end = Offset(width, y), strokeWidth = stroke)
        }
        thirdsX.forEach { x ->
            thirdsY.forEach { y ->
                drawCircle(Color.White.copy(alpha = 0.12f), radius = 2.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun DefaultMainControlPreview(preview: MainControlPreviewUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D182C), Color.Black),
                    radius = 1600f
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val vignetteAlpha = 0.22f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = vignetteAlpha),
                        Color.Transparent,
                        Color.Black.copy(alpha = vignetteAlpha)
                    )
                )
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = preview.title,
                    tint = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = preview.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center
            )
            preview.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.38f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MainControlPill(
    label: String,
    tone: MainControlTone,
    leadingDot: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tone.color().copy(alpha = 0.12f))
            .border(1.dp, tone.color().copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tone.color())
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
            color = tone.color(),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun MainControlToolKey.icon(): ImageVector =
    when (this) {
        MainControlToolKey.Grid -> Icons.Outlined.GridOn
        MainControlToolKey.Focus -> Icons.Outlined.CenterFocusStrong
        MainControlToolKey.Frame -> Icons.Outlined.CropFree
        MainControlToolKey.Lens -> Icons.Outlined.CameraAlt
        MainControlToolKey.Navigation -> Icons.Outlined.Navigation
        MainControlToolKey.Maps -> Icons.Outlined.Map
        MainControlToolKey.Gallery -> Icons.Outlined.PermMedia
        MainControlToolKey.Settings -> Icons.Outlined.Settings
    }

private fun MainControlShortcutKey.icon(): ImageVector =
    when (this) {
        MainControlShortcutKey.MotionLibrary -> Icons.Outlined.Layers
        MainControlShortcutKey.CreateMotion -> Icons.Outlined.AutoAwesome
        MainControlShortcutKey.SmartFollow -> Icons.Outlined.CenterFocusStrong
    }

private fun MainControlTransportControlKey.icon(selected: Boolean): ImageVector =
    when (this) {
        MainControlTransportControlKey.Reset -> Icons.Outlined.Replay
        MainControlTransportControlKey.PlayPause -> if (selected) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
        MainControlTransportControlKey.Record -> Icons.Outlined.FiberManualRecord
        MainControlTransportControlKey.Save -> Icons.Outlined.SaveAlt
        MainControlTransportControlKey.Stop -> Icons.Outlined.Stop
    }

private fun MainControlTone.color(): Color =
    when (this) {
        MainControlTone.Neutral -> Color.White.copy(alpha = 0.58f)
        MainControlTone.Brand -> MainControlBrand
        MainControlTone.Secondary -> MainControlSecondary
        MainControlTone.Success -> MainControlSuccess
        MainControlTone.Warning -> MainControlWarning
        MainControlTone.Danger -> MainControlDanger
    }

private fun MainControlTone.cardColor(): Color =
    when (this) {
        MainControlTone.Brand -> MainControlBrand.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.03f)
    }

private fun MainControlTone.borderColor(): Color =
    when (this) {
        MainControlTone.Brand -> MainControlBrand.copy(alpha = 0.16f)
        else -> MainControlPanelBorder
    }

private fun MainControlTone.iconBackgroundColor(): Color =
    when (this) {
        MainControlTone.Brand -> MainControlBrand.copy(alpha = 0.10f)
        MainControlTone.Secondary -> MainControlSecondary.copy(alpha = 0.10f)
        MainControlTone.Success -> MainControlSuccess.copy(alpha = 0.10f)
        MainControlTone.Warning -> MainControlWarning.copy(alpha = 0.10f)
        MainControlTone.Danger -> MainControlDanger.copy(alpha = 0.10f)
        MainControlTone.Neutral -> Color.White.copy(alpha = 0.05f)
    }

@Preview(widthDp = 1600, heightDp = 900, showBackground = true)
@Composable
private fun MainControlScreenDesktopPreview() {
    RecomoUserTheme {
        MainControlScreen(state = MainControlUiState.sample())
    }
}

@Preview(widthDp = 840, heightDp = 1280, showBackground = true)
@Composable
private fun MainControlScreenTabletPreview() {
    RecomoUserTheme {
        MainControlScreen(state = MainControlUiState.sample())
    }
}
