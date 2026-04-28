package com.recomo.user.ui.components.ee

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.user.control.EETeleopMode
import com.recomo.user.control.UserEEPositionUiState
import com.recomo.user.control.UserEEPositionViewModel
import com.recomo.user.data.ee.EESpeedMode
import com.recomo.user.imu.ImuTeleopState

// ── Heights ─────────────────────────────────────────────────────────────────
private val ROW_H = 26.dp       // toolbar, speed, mode rows
private val BTN_H = 38.dp       // direction + gimbal buttons
private val GMBL_H = 34.dp      // gimbal buttons (slightly smaller)
private val DEADMAN_H = 56.dp   // IMU deadman
private val GAP = 4.dp          // inter-row gap

// ── Public entry ────────────────────────────────────────────────────────────

@Composable
fun EEPositionControlPanel(
    viewModel: UserEEPositionViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val imuState by viewModel.imuState.collectAsState()
    val imuSensitivity by viewModel.imuSensitivity.collectAsState()
    val imuGimbalMix by viewModel.imuGimbalMix.collectAsState()

    DisposableEffect(state.teleopMode) {
        if (state.teleopMode == EETeleopMode.IMU) viewModel.initImuTeleop()
        else viewModel.stopImuTeleop()
        onDispose {
            viewModel.stopEEJog()
            viewModel.stopGimbalJog()
            viewModel.stopImuTeleop()
            if (viewModel.uiState.value.ikRunning) viewModel.stopIkController()
        }
    }

    Surface(
        modifier = modifier,
        color = Color(0xFF1C1C1C),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(GAP)
        ) {
            // ── Row 1: IK status + Start/Stop + DRY + mode toggle ───────
            ToolbarRow(
                state = state,
                onStartIk = { viewModel.startIkController() },
                onStopIk = { viewModel.stopIkController() },
                onToggleDryRun = { viewModel.setDryRun(!state.dryRun) },
                onTeleopModeChange = { viewModel.setTeleopMode(it) }
            )

            if (state.teleopMode == EETeleopMode.BUTTONS) {
                // ── Row 2: Speed selector ───────────────────────────────
                SpeedRow(state = state, onSpeedModeChange = { viewModel.setSpeedMode(it) })
                // ── Rows 3-4: EE direction 3x2 ─────────────────────────
                val canJog = state.connected && state.ikRunning
                DirectionRow(
                    btns = listOf(
                        DirSpec("Fwd", Icons.Filled.ArrowUpward, 0.0, 0.0, +1.0, "camera"),
                        DirSpec("Left", Icons.AutoMirrored.Filled.ArrowBack, -1.0, 0.0, 0.0, "camera"),
                        DirSpec("Up", Icons.Filled.KeyboardArrowUp, 0.0, 0.0, +1.0, "world")
                    ),
                    enabled = canJog,
                    onJogStart = { dx, dy, dz, f -> viewModel.startEEJog(dx, dy, dz, f) },
                    onJogStop = { viewModel.stopEEJog() }
                )
                DirectionRow(
                    btns = listOf(
                        DirSpec("Bkwd", Icons.Filled.ArrowDownward, 0.0, 0.0, -1.0, "camera"),
                        DirSpec("Right", Icons.AutoMirrored.Filled.ArrowForward, +1.0, 0.0, 0.0, "camera"),
                        DirSpec("Down", Icons.Filled.KeyboardArrowDown, 0.0, 0.0, -1.0, "world")
                    ),
                    enabled = canJog,
                    onJogStart = { dx, dy, dz, f -> viewModel.startEEJog(dx, dy, dz, f) },
                    onJogStop = { viewModel.stopEEJog() }
                )
                // ── Row 5: Gimbal yaw/pitch ─────────────────────────────
                GimbalRow(
                    enabled = state.connected,
                    onStart = { r, p, y -> viewModel.startGimbalJog(r, p, y) },
                    onStop = { viewModel.stopGimbalJog() }
                )
            } else {
                // ── IMU mode ────────────────────────────────────────────
                ImuPanel(
                    state = state,
                    imuState = imuState,
                    sensitivity = imuSensitivity,
                    gimbalMix = imuGimbalMix,
                    onDeadmanDown = { viewModel.setImuActive(true) },
                    onDeadmanUp = { viewModel.setImuActive(false) },
                    onCalibrate = { viewModel.calibrateImu() },
                    onSensChange = { viewModel.setImuSensitivity(it) },
                    onMixChange = { viewModel.setImuGimbalMix(it) }
                )
            }
        }
    }
}

// ── Row 1: merged toolbar ───────────────────────────────────────────────────

@Composable
private fun ToolbarRow(
    state: UserEEPositionUiState,
    onStartIk: () -> Unit,
    onStopIk: () -> Unit,
    onToggleDryRun: () -> Unit,
    onTeleopModeChange: (EETeleopMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ROW_H),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Surface(
            modifier = Modifier.size(7.dp),
            shape = RoundedCornerShape(4.dp),
            color = if (state.ikRunning) Color(0xFF4CAF50) else Color(0xFF666666)
        ) {}
        Text(
            text = if (state.ikRunning) "IK" else "IK Off",
            fontSize = 9.sp,
            color = if (state.ikRunning) Color(0xFF4CAF50) else Color(0xFF999999)
        )
        // DRY/LIVE badge
        if (state.ikRunning) {
            Surface(
                color = if (state.dryRun) Color(0xFFFF9800) else Color(0xFFF44336),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = if (state.dryRun) "DRY" else "LIVE",
                    fontSize = 8.sp,
                    color = if (state.dryRun) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        // Start/Stop
        FilledTonalButton(
            onClick = { if (state.ikRunning) onStopIk() else onStartIk() },
            enabled = state.connected && !state.ikBusy,
            modifier = Modifier.height(ROW_H),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (state.ikRunning) Color(0xFF5D2020) else Color(0xFF1B3A1B)
            )
        ) {
            Text(
                text = when {
                    state.ikBusy -> "…"
                    state.ikRunning -> "Stop"
                    else -> "Start"
                },
                fontSize = 10.sp
            )
        }
        // DRY toggle (only when stopped)
        if (!state.ikRunning) {
            TextButton(
                onClick = onToggleDryRun,
                modifier = Modifier.height(ROW_H),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (state.dryRun) "DRY" else "LIVE!",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.dryRun) Color(0xFFFF9800) else Color(0xFFF44336)
                )
            }
        }
        // Spacer pushes mode toggles to the right
        Box(modifier = Modifier.weight(1f))
        // Buttons / IMU mode chips
        FilledTonalButton(
            onClick = { onTeleopModeChange(EETeleopMode.BUTTONS) },
            modifier = Modifier.height(ROW_H),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (state.teleopMode == EETeleopMode.BUTTONS)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) { Text("Btn", fontSize = 9.sp) }
        FilledTonalButton(
            onClick = { onTeleopModeChange(EETeleopMode.IMU) },
            modifier = Modifier.height(ROW_H),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (state.teleopMode == EETeleopMode.IMU)
                    Color(0xFF1B3A5A) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) { Text("IMU", fontSize = 9.sp) }
    }
}

// ── Row 2: speed selector ───────────────────────────────────────────────────

@Composable
private fun SpeedRow(
    state: UserEEPositionUiState,
    onSpeedModeChange: (EESpeedMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ROW_H),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            EESpeedMode.FINE to "Fine",
            EESpeedMode.NORMAL to "Norm",
            EESpeedMode.LEAP to "Leap"
        ).forEach { (mode, label) ->
            FilledTonalButton(
                onClick = { onSpeedModeChange(mode) },
                modifier = Modifier.weight(1f).height(ROW_H),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (state.speedMode == mode)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text(label, fontSize = 9.sp) }
        }
        Text(
            text = "%.2f".format(state.currentVelocityMs),
            fontSize = 8.sp,
            color = Color(0xFF888888)
        )
    }
}

// ── Direction rows (EE position) ────────────────────────────────────────────

private data class DirSpec(
    val label: String,
    val icon: ImageVector,
    val dx: Double, val dy: Double, val dz: Double,
    val frame: String
)

@Composable
private fun DirectionRow(
    btns: List<DirSpec>,
    enabled: Boolean,
    onJogStart: (Double, Double, Double, String) -> Unit,
    onJogStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        btns.forEach { spec ->
            HoldButton(
                label = spec.label,
                icon = spec.icon,
                enabled = enabled,
                onDown = { onJogStart(spec.dx, spec.dy, spec.dz, spec.frame) },
                onUp = onJogStop,
                height = BTN_H,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Gimbal row ──────────────────────────────────────────────────────────────

@Composable
private fun GimbalRow(
    enabled: Boolean,
    onStart: (Double, Double, Double) -> Unit,
    onStop: () -> Unit
) {
    // Gimbal: Roll (tilt L/R), Pitch (tilt U/D), Yaw (pan L/R) — 6 buttons in one row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        HoldButton("◄Roll", null, enabled, { onStart(+1.0, 0.0, 0.0) }, onStop, GMBL_H, Modifier.weight(1f),
            bg = Color(0xFF1E2A3A))
        HoldButton("Roll►", null, enabled, { onStart(-1.0, 0.0, 0.0) }, onStop, GMBL_H, Modifier.weight(1f),
            bg = Color(0xFF1E2A3A))
        HoldButton("▲Pit", null, enabled, { onStart(0.0, +1.0, 0.0) }, onStop, GMBL_H, Modifier.weight(1f),
            bg = Color(0xFF1E2A3A))
        HoldButton("▼Pit", null, enabled, { onStart(0.0, -1.0, 0.0) }, onStop, GMBL_H, Modifier.weight(1f),
            bg = Color(0xFF1E2A3A))
        HoldButton("◄Yaw", null, enabled, { onStart(0.0, 0.0, +1.0) }, onStop, GMBL_H, Modifier.weight(1f),
            bg = Color(0xFF1E2A3A))
        HoldButton("Yaw►", null, enabled, { onStart(0.0, 0.0, -1.0) }, onStop, GMBL_H, Modifier.weight(1f),
            bg = Color(0xFF1E2A3A))
    }
}

// ── IMU sub-panel ───────────────────────────────────────────────────────────

@Composable
private fun ImuPanel(
    state: UserEEPositionUiState,
    imuState: ImuTeleopState,
    sensitivity: Float,
    gimbalMix: Float,
    onDeadmanDown: () -> Unit,
    onDeadmanUp: () -> Unit,
    onCalibrate: () -> Unit,
    onSensChange: (Float) -> Unit,
    onMixChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        // Status + calibrate
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusText = when {
                !imuState.sensorAvailable -> "Sensor N/A"
                imuState.sensorWarmingUp -> "Warming…"
                !imuState.isCalibrated -> "Press deadman"
                imuState.isActive -> "ACTIVE"
                else -> "Calibrated"
            }
            val statusColor = when {
                !imuState.sensorAvailable -> Color(0xFFFF6666)
                imuState.isActive -> Color(0xFF4CAF50)
                imuState.isCalibrated -> Color(0xFFAACCFF)
                else -> Color(0xFFFFDD88)
            }
            Text(statusText, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            if (imuState.sendHz > 0f) {
                Text("%.0f Hz".format(imuState.sendHz), fontSize = 8.sp, color = Color(0xFF777777))
            }
            TextButton(
                onClick = onCalibrate,
                enabled = imuState.sensorAvailable && !imuState.sensorWarmingUp,
                modifier = Modifier.height(22.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) { Text("Cal", fontSize = 9.sp) }
        }
        // Deadman
        DeadmanButton(
            enabled = imuState.sensorAvailable && state.connected && state.ikRunning,
            isActive = imuState.isActive,
            onDown = onDeadmanDown,
            onUp = onDeadmanUp
        )
        // Sliders
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
            Text("Sens", fontSize = 8.sp, color = Color(0xFF888888))
            Slider(sensitivity, onSensChange, valueRange = 0.1f..1.0f,
                modifier = Modifier.weight(1f).height(20.dp))
            Text("%.1f".format(sensitivity), fontSize = 8.sp, color = Color(0xFF888888))
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
            Text("EE↔Gmbl", fontSize = 8.sp, color = Color(0xFF888888))
            Slider(gimbalMix, onMixChange, valueRange = 0.0f..1.0f,
                modifier = Modifier.weight(1f).height(20.dp))
            Text("%.1f".format(gimbalMix), fontSize = 8.sp, color = Color(0xFF888888))
        }
        // Delta readout
        Text(
            "Δ P%+.1f° R%+.1f° Y%+.1f°".format(
                Math.toDegrees(imuState.lastDelta.pitch.toDouble()),
                Math.toDegrees(imuState.lastDelta.roll.toDouble()),
                Math.toDegrees(imuState.lastDelta.yaw.toDouble())
            ),
            fontSize = 8.sp,
            color = Color(0xFF555555)
        )
    }
}

@Composable
private fun DeadmanButton(
    enabled: Boolean,
    isActive: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) onDown() else onUp()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DEADMAN_H)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) break
                    }
                    isPressed = false
                }
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            shape = RoundedCornerShape(8.dp),
            color = when {
                !enabled -> Color(0xFF2A2A2A)
                isActive -> Color(0xFF1B5E20)
                else -> Color(0xFF1B3A5A)
            },
            border = BorderStroke(2.dp, if (isActive) Color(0xFF4CAF50) else Color(0xFF4A9EFF))
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        !enabled -> "IMU disabled"
                        isActive -> "TILT TO MOVE"
                        else -> "HOLD DEADMAN"
                    },
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Shared hold-to-jog button ───────────────────────────────────────────────

@Composable
private fun HoldButton(
    label: String,
    icon: ImageVector?,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) onDown() else onUp()
    }

    FilledTonalButton(
        onClick = { },
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer else bg
        ),
        modifier = modifier
            .height(height)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) break
                    }
                    isPressed = false
                }
            },
        contentPadding = PaddingValues(2.dp)
    ) {
        if (icon != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, Modifier.size(16.dp))
                Text(label, fontSize = 8.sp)
            }
        } else {
            Text(label, fontSize = 9.sp)
        }
    }
}
