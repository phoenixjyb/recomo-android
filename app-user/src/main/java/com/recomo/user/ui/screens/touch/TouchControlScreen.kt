package com.recomo.user.ui.screens.touch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.user.ui.theme.StudioChrome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TouchBackground = StudioChrome.background
private val TouchPanel = StudioChrome.panel
private val TouchPanelSoft = StudioChrome.panelSoft
private val TouchBorder = StudioChrome.panelBorder
private val TouchBrand = StudioChrome.accentBlue
private val TouchAccent = StudioChrome.accentPurple
private val TouchSuccess = StudioChrome.success
private val TouchWarning = StudioChrome.warning
private val TouchDanger = Color(0xFFFF5E7A)

@Composable
fun TouchControlScreen(
    state: TouchControlWorkspaceState,
    modifier: Modifier = Modifier,
    title: String,
    slowLabel: String,
    normalLabel: String,
    fastLabel: String,
    chassisTitle: String,
    chassisSubtitle: String,
    armTitle: String,
    armSubtitle: String,
    gimbalTitle: String,
    gimbalSubtitle: String,
    activeLabel: String,
    connectionLabel: String,
    safetyLabel: String,
    estopLabel: String,
    clearEstopLabel: String,
    freezeLabel: String,
    unfreezeLabel: String,
    yawLabel: String,
    armHomeLabel: String,
    gimbalHomeLabel: String,
    stopLabel: String,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onSpeedModeChange: (TouchControlSpeedMode) -> Unit,
    onChassisForwardTap: () -> Unit,
    onChassisForwardHoldStart: () -> Unit,
    onChassisForwardHoldEnd: () -> Unit,
    onChassisBackTap: () -> Unit,
    onChassisBackHoldStart: () -> Unit,
    onChassisBackHoldEnd: () -> Unit,
    onChassisLeftTap: () -> Unit,
    onChassisLeftHoldStart: () -> Unit,
    onChassisLeftHoldEnd: () -> Unit,
    onChassisRightTap: () -> Unit,
    onChassisRightHoldStart: () -> Unit,
    onChassisRightHoldEnd: () -> Unit,
    onChassisRotateLeftTap: () -> Unit,
    onChassisRotateLeftHoldStart: () -> Unit,
    onChassisRotateLeftHoldEnd: () -> Unit,
    onChassisRotateRightTap: () -> Unit,
    onChassisRotateRightHoldStart: () -> Unit,
    onChassisRotateRightHoldEnd: () -> Unit,
    onArmJointTap: (jointIndex: Int, positive: Boolean) -> Unit,
    onArmJointHoldStart: (jointIndex: Int, positive: Boolean) -> Unit,
    onArmJointHoldEnd: () -> Unit,
    onGimbalTap: (axis: Int, positive: Boolean) -> Unit,
    onGimbalHoldStart: (axis: Int, positive: Boolean) -> Unit,
    onGimbalHoldEnd: (axis: Int) -> Unit,
    onEmergencyStopTap: () -> Unit,
    onFreezeToggleTap: () -> Unit,
    onArmHome: () -> Unit,
    onGimbalHome: () -> Unit,
    onStopAll: () -> Unit,
    previewContent: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioChrome.screenBackgroundBrush)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            ControlPanel(
                title = chassisTitle,
                subtitle = chassisSubtitle,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                SafetyActionRow(
                    state = state,
                    estopLabel = estopLabel,
                    clearEstopLabel = clearEstopLabel,
                    freezeLabel = freezeLabel,
                    unfreezeLabel = unfreezeLabel,
                    onEmergencyStopTap = onEmergencyStopTap,
                    onFreezeToggleTap = onFreezeToggleTap
                )
                Spacer(modifier = Modifier.height(14.dp))
                ChassisControlGrid(
                    enabled = state.connected && !state.estopActive && !state.freezeAllActive,
                    onForwardTap = onChassisForwardTap,
                    onForwardHoldStart = onChassisForwardHoldStart,
                    onForwardHoldEnd = onChassisForwardHoldEnd,
                    onBackTap = onChassisBackTap,
                    onBackHoldStart = onChassisBackHoldStart,
                    onBackHoldEnd = onChassisBackHoldEnd,
                    onLeftTap = onChassisLeftTap,
                    onLeftHoldStart = onChassisLeftHoldStart,
                    onLeftHoldEnd = onChassisLeftHoldEnd,
                    onRightTap = onChassisRightTap,
                    onRightHoldStart = onChassisRightHoldStart,
                    onRightHoldEnd = onChassisRightHoldEnd,
                    onRotateLeftTap = onChassisRotateLeftTap,
                    onRotateLeftHoldStart = onChassisRotateLeftHoldStart,
                    onRotateLeftHoldEnd = onChassisRotateLeftHoldEnd,
                    onRotateRightTap = onChassisRotateRightTap,
                    onRotateRightHoldStart = onChassisRotateRightHoldStart,
                    onRotateRightHoldEnd = onChassisRotateRightHoldEnd
                )
                Spacer(modifier = Modifier.height(14.dp))
                TouchInfoRow(
                    label = yawLabel,
                    value = state.baseYawDeg?.let { "${it.format1()}°" } ?: "--"
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                TouchTopBar(
                    title = title,
                    state = state,
                    slowLabel = slowLabel,
                    normalLabel = normalLabel,
                    fastLabel = fastLabel,
                    activeLabel = activeLabel,
                    onBack = onBack,
                    onToggleGrid = onToggleGrid,
                    onSpeedModeChange = onSpeedModeChange
                )
                TouchPreviewPane(
                    state = state,
                    modifier = Modifier.weight(1f),
                    previewContent = previewContent
                )
                TouchBottomDock(
                    armHomeLabel = armHomeLabel,
                    gimbalHomeLabel = gimbalHomeLabel,
                    stopLabel = stopLabel,
                    onArmHome = onArmHome,
                    onGimbalHome = onGimbalHome,
                    onStopAll = onStopAll
                )
            }

            Column(
                modifier = Modifier
                    .width(344.dp)
                    .fillMaxHeight()
                    .background(TouchPanel)
                    .border(1.dp, TouchBorder)
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TouchStatusCard(
                    activeLabel = activeLabel,
                    connectionLabel = connectionLabel,
                    safetyLabel = safetyLabel,
                    state = state
                )
                ControlPanel(title = armTitle, subtitle = armSubtitle) {
                    AxisPairList(
                        labels = listOf("J4", "J5", "J6"),
                        valuesDeg = state.armJointAnglesDeg,
                        negativeSymbol = "−",
                        positiveSymbol = "+",
                        accent = TouchBrand,
                        onTap = onArmJointTap,
                        onHoldStart = onArmJointHoldStart,
                        onHoldEnd = { _ -> onArmJointHoldEnd() }
                    )
                }
                ControlPanel(title = gimbalTitle, subtitle = gimbalSubtitle) {
                    AxisPairList(
                        labels = listOf("P", "T", "R"),
                        valuesDeg = listOf(
                            state.gimbalAnglesDeg.getOrNull(2),
                            state.gimbalAnglesDeg.getOrNull(1),
                            state.gimbalAnglesDeg.getOrNull(0)
                        ),
                        negativeSymbol = "←",
                        positiveSymbol = "→",
                        accent = TouchAccent,
                        onTap = onGimbalTap,
                        onHoldStart = onGimbalHoldStart,
                        onHoldEnd = onGimbalHoldEnd
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchTopBar(
    title: String,
    state: TouchControlWorkspaceState,
    slowLabel: String,
    normalLabel: String,
    fastLabel: String,
    activeLabel: String,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onSpeedModeChange: (TouchControlSpeedMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(StudioChrome.topBar)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(StudioChrome.radiusSm),
                color = TouchPanelSoft,
                border = BorderStroke(1.dp, TouchBorder)
            ) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = title,
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactSpeedToggle(
                current = state.speedMode,
                slowLabel = slowLabel,
                normalLabel = normalLabel,
                fastLabel = fastLabel,
                onSpeedModeChange = onSpeedModeChange
            )
            Surface(
                onClick = onToggleGrid,
                shape = RoundedCornerShape(StudioChrome.radiusSm),
                color = if (state.showGrid) TouchBrand.copy(alpha = 0.14f) else TouchPanelSoft,
                border = BorderStroke(1.dp, if (state.showGrid) TouchBrand.copy(alpha = 0.30f) else TouchBorder)
            ) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.GridOn,
                        contentDescription = null,
                        tint = if (state.showGrid) TouchBrand else Color(0x80FFFFFF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            StatusPill(
                text = activeLabel,
                tone = if (state.connected && !state.estopActive) TouchSuccess else TouchWarning
            )
        }
    }
}

@Composable
private fun CompactSpeedToggle(
    current: TouchControlSpeedMode,
    slowLabel: String,
    normalLabel: String,
    fastLabel: String,
    onSpeedModeChange: (TouchControlSpeedMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(StudioChrome.radiusSm))
            .background(Color(0x14FFFFFF))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            TouchControlSpeedMode.Slow to slowLabel,
            TouchControlSpeedMode.Normal to normalLabel,
            TouchControlSpeedMode.Fast to fastLabel
        ).forEach { (mode, label) ->
            Surface(
                onClick = { onSpeedModeChange(mode) },
                shape = RoundedCornerShape(StudioChrome.radiusXs),
                color = if (current == mode) TouchBrand else Color.Transparent
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (current == mode) Color.White else Color(0x66FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TouchPreviewPane(
    state: TouchControlWorkspaceState,
    modifier: Modifier = Modifier,
    previewContent: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Preserve the stream's native 16:9 aspect; black letterbox fills the
        // rest. Grid overlay lives inside so it aligns to the video frame,
        // not to the letterboxed container.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            previewContent()
        if (state.showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val thirdX = size.width / 3f
                val thirdY = size.height / 3f
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(thirdX * i, 0f),
                        end = Offset(thirdX * i, size.height)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, thirdY * i),
                        end = Offset(size.width, thirdY * i)
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.20f),
                    radius = 16.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.20f),
                    start = Offset(center.x - 24.dp.toPx(), center.y),
                    end = Offset(center.x + 24.dp.toPx(), center.y),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.20f),
                    start = Offset(center.x, center.y - 24.dp.toPx()),
                    end = Offset(center.x, center.y + 24.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        }
    }
}

@Composable
private fun ControlPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = TouchPanel,
        border = BorderStroke(1.dp, TouchBorder),
        shape = RoundedCornerShape(StudioChrome.radiusSheet)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = Color(0xE6FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = Color(0x4DFFFFFF),
                        fontSize = 10.sp
                    )
                }
                content()
            }
        )
    }
}

@Composable
private fun ChassisControlGrid(
    enabled: Boolean,
    onForwardTap: () -> Unit,
    onForwardHoldStart: () -> Unit,
    onForwardHoldEnd: () -> Unit,
    onBackTap: () -> Unit,
    onBackHoldStart: () -> Unit,
    onBackHoldEnd: () -> Unit,
    onLeftTap: () -> Unit,
    onLeftHoldStart: () -> Unit,
    onLeftHoldEnd: () -> Unit,
    onRightTap: () -> Unit,
    onRightHoldStart: () -> Unit,
    onRightHoldEnd: () -> Unit,
    onRotateLeftTap: () -> Unit,
    onRotateLeftHoldStart: () -> Unit,
    onRotateLeftHoldEnd: () -> Unit,
    onRotateRightTap: () -> Unit,
    onRotateRightHoldStart: () -> Unit,
    onRotateRightHoldEnd: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HoldControlButton("⟲", "LT", enabled, TouchBrand, onTap = onRotateLeftTap, onHoldStart = onRotateLeftHoldStart, onHoldEnd = onRotateLeftHoldEnd)
            HoldControlButton("↑", "F", enabled, TouchBrand, onTap = onForwardTap, onHoldStart = onForwardHoldStart, onHoldEnd = onForwardHoldEnd)
            HoldControlButton("⟳", "RT", enabled, TouchBrand, onTap = onRotateRightTap, onHoldStart = onRotateRightHoldStart, onHoldEnd = onRotateRightHoldEnd)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HoldControlButton("←", "L", enabled, TouchBrand, onTap = onLeftTap, onHoldStart = onLeftHoldStart, onHoldEnd = onLeftHoldEnd)
            HoldControlButton("↓", "B", enabled, TouchBrand, onTap = onBackTap, onHoldStart = onBackHoldStart, onHoldEnd = onBackHoldEnd)
            HoldControlButton("→", "R", enabled, TouchBrand, onTap = onRightTap, onHoldStart = onRightHoldStart, onHoldEnd = onRightHoldEnd)
        }
    }
}

@Composable
private fun SafetyActionRow(
    state: TouchControlWorkspaceState,
    estopLabel: String,
    clearEstopLabel: String,
    freezeLabel: String,
    unfreezeLabel: String,
    onEmergencyStopTap: () -> Unit,
    onFreezeToggleTap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onEmergencyStopTap,
            enabled = !state.estopActive || state.canClearEstop,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.estopActive) TouchDanger.copy(alpha = 0.22f) else TouchDanger,
                contentColor = Color.White,
                disabledContainerColor = TouchDanger.copy(alpha = 0.10f),
                disabledContentColor = Color.White.copy(alpha = 0.48f)
            )
        ) {
            Text(
                text = if (state.estopActive) clearEstopLabel else estopLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onFreezeToggleTap,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.freezeAllActive) TouchWarning.copy(alpha = 0.18f) else TouchPanelSoft,
                contentColor = if (state.freezeAllActive) TouchWarning else Color(0xCCFFFFFF)
            )
        ) {
            Text(
                text = if (state.freezeAllActive) unfreezeLabel else freezeLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AxisPairList(
    labels: List<String>,
    valuesDeg: List<Double?>,
    negativeSymbol: String,
    positiveSymbol: String,
    accent: Color,
    onTap: (index: Int, positive: Boolean) -> Unit,
    onHoldStart: (index: Int, positive: Boolean) -> Unit,
    onHoldEnd: (index: Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Surface(
                color = TouchPanelSoft,
                border = BorderStroke(1.dp, TouchBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = label,
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(22.dp)
                    )
                    HoldControlButton(
                        symbol = negativeSymbol,
                        label = "",
                        enabled = true,
                        accent = accent,
                        compact = true,
                        onTap = { onTap(index, false) },
                        onHoldStart = { onHoldStart(index, false) },
                        onHoldEnd = { onHoldEnd(index) }
                    )
                    Text(
                        text = valuesDeg.getOrNull(index)?.let { "${it.format1()}°" } ?: "--",
                        modifier = Modifier.weight(1f),
                        color = Color(0xCCFFFFFF),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    HoldControlButton(
                        symbol = positiveSymbol,
                        label = "",
                        enabled = true,
                        accent = accent,
                        compact = true,
                        onTap = { onTap(index, true) },
                        onHoldStart = { onHoldStart(index, true) },
                        onHoldEnd = { onHoldEnd(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldControlButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    accent: Color,
    compact: Boolean = false,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var holdStarted by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val size = if (compact) 42.dp else 64.dp
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(if (compact) 14.dp else 20.dp),
        color = if (pressed) accent.copy(alpha = 0.18f) else TouchPanelSoft,
        border = BorderStroke(1.dp, if (pressed) accent.copy(alpha = 0.35f) else TouchBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        holdStarted = false
                        holdJob?.cancel()
                        holdJob = scope.launch {
                            delay(180)
                            holdStarted = true
                            onHoldStart()
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { !it.pressed }) break
                        }

                        holdJob?.cancel()
                        if (holdStarted) onHoldEnd() else onTap()
                        pressed = false
                        holdStarted = false
                    }
                }
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = symbol,
                    color = if (enabled) Color.White else Color(0x40FFFFFF),
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        color = if (enabled) Color(0xB3FFFFFF) else Color(0x40FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchStatusCard(
    activeLabel: String,
    connectionLabel: String,
    safetyLabel: String,
    state: TouchControlWorkspaceState
) {
    Surface(
        color = TouchPanelSoft,
        border = BorderStroke(1.dp, TouchBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                text = activeLabel,
                tone = if (state.connected && !state.estopActive) TouchSuccess else TouchWarning
            )
            TouchInfoRow(
                label = connectionLabel,
                value = when {
                    state.connected -> "LINKED"
                    state.connecting -> "CONNECTING"
                    else -> "OFFLINE"
                }
            )
            TouchInfoRow(
                label = safetyLabel,
                value = when {
                    state.estopActive -> "E-STOP"
                    state.freezeAllActive -> "FROZEN"
                    state.deadmanOk -> "CLEAR"
                    else -> "HOLD"
                }
            )
        }
    }
}

@Composable
private fun TouchBottomDock(
    armHomeLabel: String,
    gimbalHomeLabel: String,
    stopLabel: String,
    onArmHome: () -> Unit,
    onGimbalHome: () -> Unit,
    onStopAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(TouchPanel)
            .border(1.dp, TouchBorder)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onArmHome,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x12FFFFFF),
                contentColor = Color(0xCCFFFFFF)
            )
        ) {
            Text(armHomeLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = onGimbalHome,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TouchAccent.copy(alpha = 0.14f),
                contentColor = TouchAccent
            )
        ) {
            Text(gimbalHomeLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = onStopAll,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TouchDanger.copy(alpha = 0.16f),
                contentColor = TouchDanger
            )
        ) {
            Text(stopLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TouchInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0x4DFFFFFF),
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusPill(text: String, tone: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .border(1.dp, tone.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone)
        )
        Text(
            text = text,
            color = Color(0xB3FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun Double.format1(): String = String.format("%.1f", this)
