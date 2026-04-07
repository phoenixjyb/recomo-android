package com.recomo.remotecontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.remotecontrol.imu.ImuTeleopState
import com.recomo.remotecontrol.network.OrinGatewayClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * EE Position Control Panel
 *
 * Provides 6-button control for End Effector position:
 * - Forward/Backward (camera Z axis)
 * - Left/Right (camera X axis)
 * - Up/Down (world/odom Z axis)
 *
 * Buttons send continuous velocity commands while held.
 *
 * Layout is designed for tablet landscape — all controls fit within ~300-350dp height.
 * A 3x2 button grid (paired by axis) replaces the previous 3 stacked rows of 2.
 */
@Composable
fun EEPositionControlPanel(
    gatewayClient: OrinGatewayClient,
    isConnected: Boolean,
    frame: String = "camera", // "camera" or "world"
    stepSettings: com.recomo.remotecontrol.settings.StepSettings,
    ikRunning: Boolean = false,
    ikDryRun: Boolean = true,
    ikBusy: Boolean = false,
    onIkStart: (dryRun: Boolean) -> Unit = {},
    onIkStop: () -> Unit = {},
    // IMU teleop parameters
    imuTeleopState: ImuTeleopState = ImuTeleopState(),
    imuSensitivity: Float = 0.5f,
    imuGimbalMix: Float = 0.5f,
    onImuDeadmanDown: () -> Unit = {},
    onImuDeadmanUp: () -> Unit = {},
    onImuCalibrate: () -> Unit = {},
    onImuSensitivityChange: (Float) -> Unit = {},
    onImuGimbalMixChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    android.util.Log.d("EEPositionControlPanel", "Panel created/recomposed - isConnected=$isConnected, frame=$frame")
    val haptic = LocalHapticFeedback.current
    var speedMode by remember { mutableStateOf("normal") } // "fine", "normal", "leap"
    var localDryRun by remember { mutableStateOf(true) }  // Local dry_run toggle for next start
    var useImuMode by remember { mutableStateOf(false) }  // Buttons vs IMU toggle

    // Determine velocity based on mode
    val currentVelocity = when (speedMode) {
        "fine" -> stepSettings.eePositionFineVel
        "leap" -> stepSettings.eePositionLeapVel
        else -> stepSettings.eePositionNormalVel
    }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = Color(0xFF1C1C1C),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color(0x55FFFFFF))
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Row 1: Compact header — title + IK status + Start/Stop + DryRun ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text(
                text = "EE Position",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            // Status dot + badge
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = if (ikRunning) Color(0xFF4CAF50) else Color(0xFF666666)
            ) {}
            Text(
                text = if (ikRunning) "IK Active" else "IK Off",
                fontSize = 10.sp,
                color = if (ikRunning) Color(0xFF4CAF50) else Color(0xFF999999)
            )
            if (ikRunning) {
                val badgeColor = if (ikDryRun) Color(0xFFFF9800) else Color(0xFFF44336)
                val badgeText = if (ikDryRun) "DRY" else "LIVE"
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        color = if (ikDryRun) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            // Start / Stop button
            FilledTonalButton(
                onClick = {
                    if (ikRunning) onIkStop() else onIkStart(localDryRun)
                },
                enabled = isConnected && !ikBusy,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (ikRunning) Color(0xFF5D2020) else Color(0xFF1B3A1B)
                )
            ) {
                Text(
                    text = if (ikBusy) "..." else if (ikRunning) "Stop" else "Start",
                    fontSize = 11.sp
                )
            }

            // Compact DryRun toggle — only shown when IK is stopped
            if (!ikRunning) {
                TextButton(
                    onClick = { localDryRun = !localDryRun },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (localDryRun) "DRY" else "LIVE!",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (localDryRun) Color(0xFFFF9800) else Color(0xFFF44336)
                    )
                }
            }
        }

        // ── Row 2: Input mode toggle + Speed selector (button mode only) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Buttons / IMU toggle
            FilledTonalButton(
                onClick = { useImuMode = false },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (!useImuMode)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("Buttons", fontSize = 10.sp) }
            FilledTonalButton(
                onClick = { useImuMode = true },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (useImuMode)
                        Color(0xFF1B3A5A)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) { Text("IMU", fontSize = 10.sp) }

            if (!useImuMode) {
                Spacer(modifier = Modifier.width(4.dp))
                // Fine / Normal / Leap speed selector inline
                listOf("fine" to "Fine", "normal" to "Norm", "leap" to "Leap").forEach { (key, label) ->
                    FilledTonalButton(
                        onClick = {
                            android.util.Log.d("EEPositionControlPanel", "Speed mode: $key")
                            speedMode = key
                        },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (speedMode == key)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text(label, fontSize = 10.sp) }
                }
                // Speed value
                Text(
                    text = "${"%.2f".format(currentVelocity)}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }

        // ── Content area: 3x2 button grid (button mode) or IMU panel ──
        if (useImuMode) {
            ImuTeleopPanel(
                imuState = imuTeleopState,
                sensitivity = imuSensitivity,
                gimbalMix = imuGimbalMix,
                isConnected = isConnected,
                ikRunning = ikRunning,
                onDeadmanDown = onImuDeadmanDown,
                onDeadmanUp = onImuDeadmanUp,
                onCalibrate = onImuCalibrate,
                onSensitivityChange = onImuSensitivityChange,
                onGimbalMixChange = onImuGimbalMixChange
            )
        } else {
            // ── 3x2 button grid ──────────────────────────────────────────
            // Row 1: Forward (cam-Z+), Left (cam-X-), Up (world-Z+)
            // Row 2: Backward (cam-Z-), Right (cam-X+), Down (world-Z-)
            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Fwd/Bkwd", fontSize = 9.sp, color = Color(0xFF777777),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Left/Right", fontSize = 9.sp, color = Color(0xFF777777),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Up/Down", fontSize = 9.sp, color = Color(0xFF777777),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            // Top row: Forward, Left, Up
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EEDirectionButton(
                    label = "Fwd",
                    icon = Icons.Default.ArrowUpward,
                    dx = 0.0, dy = 0.0, dz = currentVelocity,
                    frame = frame,
                    gatewayClient = gatewayClient,
                    isConnected = isConnected,
                    onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.weight(1f)
                )
                EEDirectionButton(
                    label = "Left",
                    icon = Icons.Default.ArrowBack,
                    dx = -currentVelocity, dy = 0.0, dz = 0.0,
                    frame = frame,
                    gatewayClient = gatewayClient,
                    isConnected = isConnected,
                    onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.weight(1f)
                )
                EEDirectionButton(
                    label = "Up",
                    icon = Icons.Default.KeyboardArrowUp,
                    dx = 0.0, dy = 0.0, dz = currentVelocity,
                    frame = "world",
                    gatewayClient = gatewayClient,
                    isConnected = isConnected,
                    onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Bottom row: Backward, Right, Down
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EEDirectionButton(
                    label = "Bkwd",
                    icon = Icons.Default.ArrowDownward,
                    dx = 0.0, dy = 0.0, dz = -currentVelocity,
                    frame = frame,
                    gatewayClient = gatewayClient,
                    isConnected = isConnected,
                    onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.weight(1f)
                )
                EEDirectionButton(
                    label = "Right",
                    icon = Icons.Default.ArrowForward,
                    dx = currentVelocity, dy = 0.0, dz = 0.0,
                    frame = frame,
                    gatewayClient = gatewayClient,
                    isConnected = isConnected,
                    onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.weight(1f)
                )
                EEDirectionButton(
                    label = "Down",
                    icon = Icons.Default.KeyboardArrowDown,
                    dx = 0.0, dy = 0.0, dz = -currentVelocity,
                    frame = "world",
                    gatewayClient = gatewayClient,
                    isConnected = isConnected,
                    onPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    modifier = Modifier.weight(1f)
                )
            }
        } // end button mode

        // Connection warning
        if (!isConnected) {
            Text(
                text = "Not Connected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    } // Surface
}

/**
 * Single directional button for EE position control
 */
@Composable
private fun EEDirectionButton(
    label: String,
    icon: ImageVector,
    dx: Double,
    dy: Double,
    dz: Double,
    frame: String,
    gatewayClient: OrinGatewayClient,
    isConnected: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    // Send continuous commands while button held
    LaunchedEffect(isPressed) {
        android.util.Log.d("EEDirectionButton", "LaunchedEffect: isPressed=$isPressed, isConnected=$isConnected")
        if (isPressed && isConnected) {
            android.util.Log.d("EEDirectionButton", "Sending EE commands: dx=$dx, dy=$dy, dz=$dz, frame=$frame")
            while (isActive) {
                gatewayClient.sendEEPositionCommand(dx, dy, dz, frame)
                delay(50) // 20Hz update rate
            }
        }
    }

    FilledTonalButton(
        onClick = {
            // For press-and-hold, we handle via pointerInput instead
            // But we need a no-op onClick to make button enabled
        },
        enabled = isConnected,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = modifier
            .height(56.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    android.util.Log.d("EEDirectionButton", "Button pressed: $label")
                    isPressed = true
                    onPress()

                    // Wait for release
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) {
                            break
                        }
                    }

                    android.util.Log.d("EEDirectionButton", "Button released: $label")
                    isPressed = false
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isPressed) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * IMU Teleop sub-panel: deadman touch area, tilt indicator, sliders.
 */
@Composable
private fun ImuTeleopPanel(
    imuState: ImuTeleopState,
    sensitivity: Float,
    gimbalMix: Float,
    isConnected: Boolean,
    ikRunning: Boolean,
    onDeadmanDown: () -> Unit,
    onDeadmanUp: () -> Unit,
    onCalibrate: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onGimbalMixChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    if (!imuState.sensorAvailable) {
        Text(
            text = "IMU sensor unavailable on this device",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    // Whether this mode requires IK for EE commands (gimbalMix < 1.0 means some EE)
    val needsIk = gimbalMix < 0.99f
    val deadmanEnabled = isConnected && (!needsIk || ikRunning)

    // Warning: IK not running but EE commands would be needed
    if (needsIk && !ikRunning) {
        Surface(
            color = Color(0xFF3A2A00),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Start IK Controller for arm/chassis control. Gimbal-only works without IK.",
                fontSize = 10.sp,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(6.dp)
            )
        }
    }

    // Sensor warming up indicator
    if (imuState.sensorWarmingUp) {
        Text(
            text = "Stabilizing sensor...",
            fontSize = 10.sp,
            color = Color(0xFFFF9800),
            modifier = Modifier.padding(4.dp)
        )
    }

    // ── Deadman touch area — fills remaining vertical space ─────────────
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .then(
                if (deadmanEnabled) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeadmanDown()

                            // Wait for release
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.all { !it.pressed }) {
                                    break
                                }
                            }
                            onDeadmanUp()
                        }
                    }
                } else Modifier
            ),
        color = when {
            !deadmanEnabled -> Color(0xFF1A1A1A)
            imuState.isActive -> Color(0xFF1B4A1B)
            else -> Color(0xFF2A2A2A)
        },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            when {
                !deadmanEnabled -> Color(0xFF333333)
                imuState.isActive -> Color(0xFF4CAF50)
                else -> Color(0xFF555555)
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!deadmanEnabled) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Disabled",
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF444444)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (!isConnected) "NOT CONNECTED" else "START IK FIRST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666)
                )
            } else if (imuState.isActive) {
                // Show live tilt data
                val d = imuState.lastDelta
                val pitchDeg = "%.1f".format(Math.toDegrees(d.pitch.toDouble()))
                val rollDeg = "%.1f".format(Math.toDegrees(d.roll.toDouble()))
                val yawDeg = "%.1f".format(Math.toDegrees(d.yaw.toDouble()))
                Text(
                    text = "ACTIVE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "P:${pitchDeg}° R:${rollDeg}° Y:${yawDeg}°",
                    fontSize = 11.sp,
                    color = Color(0xFFCCCCCC),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                if (imuState.sendHz > 0) {
                    Text(
                        text = "Sending @ ${"%.0f".format(imuState.sendHz)} Hz",
                        fontSize = 9.sp,
                        color = Color(0xFF888888)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Hold to control",
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF888888)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "HOLD TO CONTROL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF888888)
                )
                if (!imuState.isCalibrated) {
                    Text(
                        text = "(auto-calibrates on first touch)",
                        fontSize = 9.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
    }

    // ── Sensitivity slider ──────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sensitivity", fontSize = 10.sp, color = Color(0xFF999999))
            Text("${"%.0f".format(sensitivity * 100)}%", fontSize = 10.sp, color = Color(0xFFCCCCCC))
        }
        Slider(
            value = sensitivity,
            onValueChange = onSensitivityChange,
            valueRange = 0.1f..1.0f,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }

    // ── Gimbal Mix slider ───────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Gimbal Mix", fontSize = 10.sp, color = Color(0xFF999999))
            Text(
                text = when {
                    gimbalMix < 0.15f -> "Position"
                    gimbalMix > 0.85f -> "Gimbal"
                    else -> "${"%.0f".format(gimbalMix * 100)}%"
                },
                fontSize = 10.sp,
                color = Color(0xFFCCCCCC)
            )
        }
        Slider(
            value = gimbalMix,
            onValueChange = onGimbalMixChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }

    // ── Recalibrate button ──────────────────────────────────────────────
    OutlinedButton(
        onClick = onCalibrate,
        modifier = Modifier.fillMaxWidth().height(30.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, Color(0xFF555555))
    ) {
        Text("Recalibrate", fontSize = 10.sp)
    }
}

// Extension function for OrinGatewayClient
suspend fun OrinGatewayClient.sendEEPositionCommand(
    dx: Double,
    dy: Double,
    dz: Double,
    frame: String
) {
    try {
        val message = buildJsonObject {
            put("type", "ee_position_cmd")
            put("dx", dx)
            put("dy", dy)
            put("dz", dz)
            put("frame", frame)
        }
        android.util.Log.d("OrinGatewayClient", "sendEEPositionCommand: calling sendControl with ee_position_cmd")
        sendControl(message)
    } catch (e: Exception) {
        android.util.Log.e("OrinGatewayClient", "sendEEPositionCommand failed", e)
    }
}
