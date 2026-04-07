package com.recomo.remotecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.recomo.remotecontrol.controller.ControllerDeviceInfo
import com.recomo.remotecontrol.controller.ControllerState
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ControllerInputPanel(
    state: ControllerState,
    deviceInfo: ControllerDeviceInfo,
    testMode: Boolean
) {
    val useStartHold = deviceInfo.profileName?.contains("bsp-d9", ignoreCase = true) == true
    val homeHoldMs by holdTimer(if (useStartHold) state.start else state.home)
    val yHoldMs by holdTimer(state.y)
    Divider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Game Controller Input", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Axes", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = String.format(
                    Locale.US,
                    "LX %.2f  LY %.2f  RX %.2f  RY %.2f",
                    state.leftX,
                    state.leftY,
                    state.rightX,
                    state.rightY
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = String.format(Locale.US, "Triggers L2 %.2f  R2 %.2f", state.l2, state.r2),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "D-pad: ${dpadLabel(state)}",
            style = MaterialTheme.typography.bodySmall
        )
        val pressed = pressedButtons(state)
        Text(
            text = if (pressed.isBlank()) "Buttons: (none)" else "Buttons: $pressed",
            style = MaterialTheme.typography.bodySmall
        )
        if (state.lastKeyCode != null) {
            val scan = state.lastKeyScanCode?.let { " scan=$it" } ?: ""
            Text(
                text = "Last key: ${state.lastKeyName ?: state.lastKeyCode}$scan (${keyActionLabel(state.lastKeyAction)})",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "Deadman (L1): ${if (state.l1) "Pressed" else "Released"}",
            style = MaterialTheme.typography.bodySmall
        )
        if (testMode) {
            Text(
                text = "Motion blocked (test mode)",
                style = MaterialTheme.typography.bodySmall
            )
        }
        val estopLabel = if (useStartHold) {
            "START"
        } else {
            "HOME"
        }
        Text(
            text = "$estopLabel hold: ${formatMs(homeHoldMs)} / ${formatMs(HOLD_ESTOP_MS)}",
            style = MaterialTheme.typography.bodySmall
        )
        if (homeHoldMs >= HOLD_ESTOP_MS) {
            Text(
                text = if (testMode) "E-stop suppressed (test mode)" else "E-stop threshold reached",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "Y hold: ${formatMs(yHoldMs)} / ${formatMs(HOLD_CLEAR_MS)}",
            style = MaterialTheme.typography.bodySmall
        )
        if (yHoldMs >= HOLD_CLEAR_MS) {
            Text(
                text = if (testMode) "Clear suppressed (test mode)" else "Clear threshold reached",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (deviceInfo.deviceId != null) {
            Text(
                text = "Device id: ${deviceInfo.deviceId}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun holdTimer(pressed: Boolean) = produceState(0L, pressed) {
    if (!pressed) {
        value = 0L
        return@produceState
    }
    val start = android.os.SystemClock.uptimeMillis()
    while (true) {
        value = android.os.SystemClock.uptimeMillis() - start
        delay(50)
    }
}

private fun formatMs(value: Long): String {
    return String.format(Locale.US, "%.1fs", value / 1000.0)
}

private fun keyActionLabel(action: Int): String {
    return when (action) {
        android.view.KeyEvent.ACTION_DOWN -> "DOWN"
        android.view.KeyEvent.ACTION_UP -> "UP"
        else -> action.toString()
    }
}

private const val HOLD_ESTOP_MS = 1500L
private const val HOLD_CLEAR_MS = 1500L

private fun dpadLabel(state: ControllerState): String {
    val parts = mutableListOf<String>()
    if (state.dpadUp) parts.add("Up")
    if (state.dpadDown) parts.add("Down")
    if (state.dpadLeft) parts.add("Left")
    if (state.dpadRight) parts.add("Right")
    return if (parts.isEmpty()) "(none)" else parts.joinToString(" ")
}

private fun pressedButtons(state: ControllerState): String {
    val pressed = mutableListOf<String>()
    if (state.a) pressed.add("A")
    if (state.b) pressed.add("B")
    if (state.x) pressed.add("X")
    if (state.y) pressed.add("Y")
    if (state.l1) pressed.add("L1")
    if (state.l2Button || state.l2 > 0f) pressed.add("L2")
    if (state.r1) pressed.add("R1")
    if (state.r2Button || state.r2 > 0f) pressed.add("R2")
    if (state.l3) pressed.add("L3")
    if (state.r3) pressed.add("R3")
    if (state.select) pressed.add("Select")
    if (state.start) pressed.add("Start")
    if (state.home) pressed.add("Home")
    if (state.turbo) pressed.add("Turbo")
    if (state.m1) pressed.add("M1")
    if (state.m2) pressed.add("M2")
    return pressed.joinToString(" ")
}
