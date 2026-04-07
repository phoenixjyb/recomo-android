package com.recomo.remotecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.controller.ControllerDeviceInfo
import com.recomo.remotecontrol.controller.ControllerState
import com.recomo.remotecontrol.settings.ControllerSettings
import com.recomo.remotecontrol.settings.ControllerSettingsViewModel
import java.util.Locale

@Composable
fun ControllerSettingsPanel(
    recomoViewModel: RecomoControlViewModel,
    controllerState: ControllerState,
    controllerDeviceInfo: ControllerDeviceInfo,
    viewModel: ControllerSettingsViewModel = hiltViewModel()
) {
    val controllerStatus by recomoViewModel.controllerStatus.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showSaved by remember { mutableStateOf(false) }

    var enabled by remember { mutableStateOf(false) }
    var testMode by remember { mutableStateOf(false) }
    var deadzone by remember { mutableStateOf("") }
    var triggerDeadzone by remember { mutableStateOf("") }
    var stickSensitivity by remember { mutableStateOf("") }
    var gimbalSensitivity by remember { mutableStateOf("") }
    var armSensitivity by remember { mutableStateOf("") }
    var invertChassisY by remember { mutableStateOf(false) }
    var invertGimbalY by remember { mutableStateOf(false) }
    var invertArmY by remember { mutableStateOf(false) }
    var allowAllDof by remember { mutableStateOf(false) }
    var showTestTool by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        enabled = settings.enabled
        testMode = settings.testMode
        deadzone = format(settings.deadzone, 2)
        triggerDeadzone = format(settings.triggerDeadzone, 2)
        stickSensitivity = format(settings.stickSensitivity, 2)
        gimbalSensitivity = format(settings.gimbalSensitivity, 2)
        armSensitivity = format(settings.armSensitivity, 2)
        invertChassisY = settings.invertChassisY
        invertGimbalY = settings.invertGimbalY
        invertArmY = settings.invertArmY
        allowAllDof = settings.allowAllDof
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider()
        Text(text = "Game Controller", style = MaterialTheme.typography.titleLarge)

        val statusLabel = when {
            !controllerStatus.enabled -> "Disabled"
            controllerStatus.connected && controllerStatus.testMode -> "Connected (TEST)"
            controllerStatus.connected -> "Connected"
            controllerStatus.devicePresent -> "Paired (idle)"
            else -> "Disconnected"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Status", style = MaterialTheme.typography.bodyMedium)
            Text(statusLabel, style = MaterialTheme.typography.bodyMedium)
        }
        if (!controllerStatus.deviceName.isNullOrBlank()) {
            Text("Device: ${controllerStatus.deviceName}", style = MaterialTheme.typography.bodySmall)
        }
        if (!controllerStatus.profileName.isNullOrBlank()) {
            Text("Profile: ${controllerStatus.profileName}", style = MaterialTheme.typography.bodySmall)
        }

        ToggleRow("Enable game controller", enabled) { enabled = it }
        ToggleRow("Test mode (no robot commands)", testMode) { testMode = it }
        if (testMode) {
            Text(
                text = "Game controller test mode blocks all robot commands.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        ToggleRow("Allow All-DOF mode", allowAllDof) { allowAllDof = it }
        ToggleRow("Invert chassis Y", invertChassisY) { invertChassisY = it }
        ToggleRow("Invert gimbal Y", invertGimbalY) { invertGimbalY = it }
        ToggleRow("Invert arm Y", invertArmY) { invertArmY = it }

        NumberRow(
            label = "Deadzone",
            value = deadzone,
            onValueChange = { deadzone = it }
        )
        NumberRow(
            label = "Trigger deadzone",
            value = triggerDeadzone,
            onValueChange = { triggerDeadzone = it }
        )
        NumberRow(
            label = "Stick sensitivity",
            value = stickSensitivity,
            onValueChange = { stickSensitivity = it }
        )
        NumberRow(
            label = "Gimbal sensitivity",
            value = gimbalSensitivity,
            onValueChange = { gimbalSensitivity = it }
        )
        NumberRow(
            label = "Arm sensitivity",
            value = armSensitivity,
            onValueChange = { armSensitivity = it }
        )

        Button(
            onClick = {
                val updated = ControllerSettings(
                    enabled = enabled,
                    testMode = testMode,
                    deadzone = parse(deadzone, settings.deadzone),
                    triggerDeadzone = parse(triggerDeadzone, settings.triggerDeadzone),
                    stickSensitivity = parse(stickSensitivity, settings.stickSensitivity),
                    gimbalSensitivity = parse(gimbalSensitivity, settings.gimbalSensitivity),
                    armSensitivity = parse(armSensitivity, settings.armSensitivity),
                    invertChassisY = invertChassisY,
                    invertGimbalY = invertGimbalY,
                    invertArmY = invertArmY,
                    allowAllDof = allowAllDof
                )
                viewModel.update(updated)
                showSaved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Controller Settings")
        }

        if (showSaved) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showSaved = false
            }
            Text(
                text = "✓ Game controller settings saved",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ControllerInputPanel(
            state = controllerState,
            deviceInfo = controllerDeviceInfo,
            testMode = testMode
        )

        Button(
            onClick = { showTestTool = !showTestTool },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showTestTool) "Hide Controller Test Tool" else "Open Controller Test Tool")
        }

        if (showTestTool) {
            ControllerTestTool(
                state = controllerState,
                deviceInfo = controllerDeviceInfo,
                testMode = testMode
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun format(value: Float, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}

private fun parse(value: String, fallback: Float): Float {
    return value.toFloatOrNull() ?: fallback
}
