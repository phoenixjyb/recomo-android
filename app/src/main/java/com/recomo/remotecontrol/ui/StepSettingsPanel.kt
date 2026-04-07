package com.recomo.remotecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.settings.StepSettings
import com.recomo.remotecontrol.settings.StepSettingsViewModel
import java.util.Locale
import androidx.compose.runtime.collectAsState

@Composable
fun StepSettingsPanel(
    viewModel: StepSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.stepSettings.collectAsState()
    var showSaved by remember { mutableStateOf(false) }

    // Step size fields
    var chassisFine by remember { mutableStateOf("") }
    var chassisNormal by remember { mutableStateOf("") }
    var chassisLeap by remember { mutableStateOf("") }
    var armFineCm by remember { mutableStateOf("") }
    var armNormalCm by remember { mutableStateOf("") }
    var armLeapCm by remember { mutableStateOf("") }
    var armJointFine by remember { mutableStateOf("") }
    var armJointNormal by remember { mutableStateOf("") }
    var armJointLeap by remember { mutableStateOf("") }
    var gimbalFine by remember { mutableStateOf("") }
    var gimbalNormal by remember { mutableStateOf("") }
    var gimbalLeap by remember { mutableStateOf("") }
    var yawFine by remember { mutableStateOf("") }
    var yawNormal by remember { mutableStateOf("") }
    var yawLeap by remember { mutableStateOf("") }

    // Velocity fields (for hold-to-move)
    var chassisFineVel by remember { mutableStateOf("") }
    var chassisNormalVel by remember { mutableStateOf("") }
    var chassisLeapVel by remember { mutableStateOf("") }
    var armFineVel by remember { mutableStateOf("") }
    var armNormalVel by remember { mutableStateOf("") }
    var armLeapVel by remember { mutableStateOf("") }
    var gimbalFineVel by remember { mutableStateOf("") }
    var gimbalNormalVel by remember { mutableStateOf("") }
    var gimbalLeapVel by remember { mutableStateOf("") }
    var eePositionFineVel by remember { mutableStateOf("") }
    var eePositionNormalVel by remember { mutableStateOf("") }
    var eePositionLeapVel by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        // Step sizes
        chassisFine = format(settings.chassisFineM, 3)
        chassisNormal = format(settings.chassisNormalM, 3)
        chassisLeap = format(settings.chassisLeapM, 3)
        armFineCm = format(settings.armFineM * 100.0, 1)
        armNormalCm = format(settings.armNormalM * 100.0, 1)
        armLeapCm = format(settings.armLeapM * 100.0, 1)
        armJointFine = format(settings.armJointFineDeg, 1)
        armJointNormal = format(settings.armJointNormalDeg, 1)
        armJointLeap = format(settings.armJointLeapDeg, 1)
        gimbalFine = format(settings.gimbalFineDeg, 1)
        gimbalNormal = format(settings.gimbalNormalDeg, 1)
        gimbalLeap = format(settings.gimbalLeapDeg, 1)
        yawFine = format(settings.yawFineDeg, 1)
        yawNormal = format(settings.yawNormalDeg, 1)
        yawLeap = format(settings.yawLeapDeg, 1)
        // Velocities
        chassisFineVel = format(settings.chassisFineVel, 2)
        chassisNormalVel = format(settings.chassisNormalVel, 2)
        chassisLeapVel = format(settings.chassisLeapVel, 2)
        armFineVel = format(settings.armFineVel, 2)
        armNormalVel = format(settings.armNormalVel, 2)
        armLeapVel = format(settings.armLeapVel, 2)
        gimbalFineVel = format(settings.gimbalFineVelDeg, 1)
        gimbalNormalVel = format(settings.gimbalNormalVelDeg, 1)
        gimbalLeapVel = format(settings.gimbalLeapVelDeg, 1)
        eePositionFineVel = format(settings.eePositionFineVel, 3)
        eePositionNormalVel = format(settings.eePositionNormalVel, 3)
        eePositionLeapVel = format(settings.eePositionLeapVel, 3)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Divider()
        Text(text = "Control Step Sizes (Tap)", style = MaterialTheme.typography.titleLarge)

        StepRow(
            title = "Chassis (m)",
            fine = chassisFine,
            normal = chassisNormal,
            leap = chassisLeap,
            onFineChange = { chassisFine = it },
            onNormalChange = { chassisNormal = it },
            onLeapChange = { chassisLeap = it }
        )

        StepRow(
            title = "Arm EE (cm)",
            fine = armFineCm,
            normal = armNormalCm,
            leap = armLeapCm,
            onFineChange = { armFineCm = it },
            onNormalChange = { armNormalCm = it },
            onLeapChange = { armLeapCm = it }
        )

        StepRow(
            title = "Arm Joint (deg)",
            fine = armJointFine,
            normal = armJointNormal,
            leap = armJointLeap,
            onFineChange = { armJointFine = it },
            onNormalChange = { armJointNormal = it },
            onLeapChange = { armJointLeap = it }
        )

        StepRow(
            title = "Gimbal (deg)",
            fine = gimbalFine,
            normal = gimbalNormal,
            leap = gimbalLeap,
            onFineChange = { gimbalFine = it },
            onNormalChange = { gimbalNormal = it },
            onLeapChange = { gimbalLeap = it }
        )

        StepRow(
            title = "Chassis Yaw (deg)",
            fine = yawFine,
            normal = yawNormal,
            leap = yawLeap,
            onFineChange = { yawFine = it },
            onNormalChange = { yawNormal = it },
            onLeapChange = { yawLeap = it }
        )

        Divider()
        Text(text = "Control Velocities (Hold)", style = MaterialTheme.typography.titleLarge)

        StepRow(
            title = "Chassis (m/s)",
            fine = chassisFineVel,
            normal = chassisNormalVel,
            leap = chassisLeapVel,
            onFineChange = { chassisFineVel = it },
            onNormalChange = { chassisNormalVel = it },
            onLeapChange = { chassisLeapVel = it }
        )

        StepRow(
            title = "Arm (deg/s)",
            fine = armFineVel,
            normal = armNormalVel,
            leap = armLeapVel,
            onFineChange = { armFineVel = it },
            onNormalChange = { armNormalVel = it },
            onLeapChange = { armLeapVel = it }
        )

        StepRow(
            title = "Gimbal (deg/s)",
            fine = gimbalFineVel,
            normal = gimbalNormalVel,
            leap = gimbalLeapVel,
            onFineChange = { gimbalFineVel = it },
            onNormalChange = { gimbalNormalVel = it },
            onLeapChange = { gimbalLeapVel = it }
        )

        StepRow(
            title = "EE Position (m/s)",
            fine = eePositionFineVel,
            normal = eePositionNormalVel,
            leap = eePositionLeapVel,
            onFineChange = { eePositionFineVel = it },
            onNormalChange = { eePositionNormalVel = it },
            onLeapChange = { eePositionLeapVel = it }
        )

        Button(
            onClick = {
                val updated = StepSettings(
                    // Step sizes
                    chassisFineM = parse(chassisFine, settings.chassisFineM),
                    chassisNormalM = parse(chassisNormal, settings.chassisNormalM),
                    chassisLeapM = parse(chassisLeap, settings.chassisLeapM),
                    armFineM = parse(armFineCm, settings.armFineM * 100.0) / 100.0,
                    armNormalM = parse(armNormalCm, settings.armNormalM * 100.0) / 100.0,
                    armLeapM = parse(armLeapCm, settings.armLeapM * 100.0) / 100.0,
                    armJointFineDeg = parse(armJointFine, settings.armJointFineDeg),
                    armJointNormalDeg = parse(armJointNormal, settings.armJointNormalDeg),
                    armJointLeapDeg = parse(armJointLeap, settings.armJointLeapDeg),
                    gimbalFineDeg = parse(gimbalFine, settings.gimbalFineDeg),
                    gimbalNormalDeg = parse(gimbalNormal, settings.gimbalNormalDeg),
                    gimbalLeapDeg = parse(gimbalLeap, settings.gimbalLeapDeg),
                    yawFineDeg = parse(yawFine, settings.yawFineDeg),
                    yawNormalDeg = parse(yawNormal, settings.yawNormalDeg),
                    yawLeapDeg = parse(yawLeap, settings.yawLeapDeg),
                    // Velocities
                    chassisFineVel = parse(chassisFineVel, settings.chassisFineVel),
                    chassisNormalVel = parse(chassisNormalVel, settings.chassisNormalVel),
                    chassisLeapVel = parse(chassisLeapVel, settings.chassisLeapVel),
                    armFineVel = parse(armFineVel, settings.armFineVel),
                    armNormalVel = parse(armNormalVel, settings.armNormalVel),
                    armLeapVel = parse(armLeapVel, settings.armLeapVel),
                    gimbalFineVelDeg = parse(gimbalFineVel, settings.gimbalFineVelDeg),
                    gimbalNormalVelDeg = parse(gimbalNormalVel, settings.gimbalNormalVelDeg),
                    gimbalLeapVelDeg = parse(gimbalLeapVel, settings.gimbalLeapVelDeg),
                    // EE Position velocities
                    eePositionFineVel = parse(eePositionFineVel, settings.eePositionFineVel),
                    eePositionNormalVel = parse(eePositionNormalVel, settings.eePositionNormalVel),
                    eePositionLeapVel = parse(eePositionLeapVel, settings.eePositionLeapVel)
                )
                viewModel.update(updated)
                showSaved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }

        if (showSaved) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showSaved = false
            }
            Text(
                text = "✓ Settings saved",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StepRow(
    title: String,
    fine: String,
    normal: String,
    leap: String,
    onFineChange: (String) -> Unit,
    onNormalChange: (String) -> Unit,
    onLeapChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StepField(
                label = "Fine",
                value = fine,
                onValueChange = onFineChange,
                modifier = Modifier.weight(1f)
            )
            StepField(
                label = "Normal",
                value = normal,
                onValueChange = onNormalChange,
                modifier = Modifier.weight(1f)
            )
            StepField(
                label = "Leap",
                value = leap,
                onValueChange = onLeapChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StepField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun format(value: Double, decimals: Int): String {
    return String.format(Locale.US, "%.${decimals}f", value)
}

private fun parse(text: String, fallback: Double): Double {
    return text.toDoubleOrNull() ?: fallback
}
