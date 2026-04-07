package com.recomo.remotecontrol.ui

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.recomo.remotecontrol.controller.ControllerDeviceInfo
import com.recomo.remotecontrol.controller.ControllerState
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun ControllerTestTool(
    state: ControllerState,
    deviceInfo: ControllerDeviceInfo,
    testMode: Boolean
) {
    val isD9 = remember(deviceInfo.profileName) {
        deviceInfo.profileName?.contains("bsp-d9", ignoreCase = true) == true
    }
    val includeThumbButtons = !isD9
    val includeSpecialButtons = !isD9
    val steps = remember(deviceInfo.profileName) { buildSteps(includeThumbButtons, includeSpecialButtons) }
    val results = remember(steps) { mutableStateListOf<ControllerTestResult?>().apply {
        repeat(steps.size) { add(null) }
    } }
    var currentIndex by remember { mutableStateOf(0) }
    var prevState by remember { mutableStateOf(state) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(steps) {
        currentIndex = 0
        saveStatus = null
        results.indices.forEach { results[it] = null }
    }

    LaunchedEffect(state, currentIndex, testMode) {
        if (!testMode) return@LaunchedEffect
        if (currentIndex >= steps.size) return@LaunchedEffect

        val step = steps[currentIndex]
        val now = SystemClock.uptimeMillis()
        val current = state

        val recorded = when (step.type) {
            StepType.KEY -> {
                val code = current.lastKeyCode
                val isNewDown = code != null &&
                    current.lastKeyAction == KeyEvent.ACTION_DOWN &&
                    current.lastKeyEventAtMs > prevState.lastKeyEventAtMs
                if (isNewDown) {
                    ControllerTestResult(
                        label = step.label,
                        keyCode = code,
                        keyName = current.lastKeyName,
                        scanCode = current.lastKeyScanCode,
                        action = "DOWN",
                        timestampMs = now,
                        axes = emptyMap()
                    )
                } else {
                    null
                }
            }
            StepType.DPAD -> {
                val pressed = when (step.dpad) {
                    DpadDir.UP -> current.dpadUp && !prevState.dpadUp
                    DpadDir.DOWN -> current.dpadDown && !prevState.dpadDown
                    DpadDir.LEFT -> current.dpadLeft && !prevState.dpadLeft
                    DpadDir.RIGHT -> current.dpadRight && !prevState.dpadRight
                    null -> false
                }
                if (pressed) {
                    ControllerTestResult(
                        label = step.label,
                        keyCode = null,
                        keyName = null,
                        scanCode = null,
                        action = "DPAD",
                        timestampMs = now,
                        axes = mapOf(
                            "hatX" to current.hatX,
                            "hatY" to current.hatY
                        )
                    )
                } else {
                    null
                }
            }
            StepType.AXIS -> {
                val recordedAxis = when (step.axis) {
                    AxisGroup.LEFT_STICK -> {
                        val prevMag = sqrt(prevState.leftX * prevState.leftX + prevState.leftY * prevState.leftY)
                        val mag = sqrt(current.leftX * current.leftX + current.leftY * current.leftY)
                        mag >= AXIS_THRESHOLD && prevMag < AXIS_THRESHOLD
                    }
                    AxisGroup.RIGHT_STICK -> {
                        val prevMag = sqrt(prevState.rightX * prevState.rightX + prevState.rightY * prevState.rightY)
                        val mag = sqrt(current.rightX * current.rightX + current.rightY * current.rightY)
                        mag >= AXIS_THRESHOLD && prevMag < AXIS_THRESHOLD
                    }
                    AxisGroup.TRIGGERS -> {
                        val prevMax = maxOf(prevState.l2, prevState.r2)
                        val currMax = maxOf(current.l2, current.r2)
                        currMax >= TRIGGER_THRESHOLD && prevMax < TRIGGER_THRESHOLD
                    }
                    null -> false
                }
                if (recordedAxis) {
                    val axes = when (step.axis) {
                        AxisGroup.LEFT_STICK -> mapOf("lx" to current.leftX, "ly" to current.leftY)
                        AxisGroup.RIGHT_STICK -> mapOf("rx" to current.rightX, "ry" to current.rightY)
                        AxisGroup.TRIGGERS -> mapOf("l2" to current.l2, "r2" to current.r2)
                        null -> emptyMap()
                    }
                    ControllerTestResult(
                        label = step.label,
                        keyCode = null,
                        keyName = null,
                        scanCode = null,
                        action = "AXIS",
                        timestampMs = now,
                        axes = axes
                    )
                } else {
                    null
                }
            }
        }

        if (recorded != null) {
            results[currentIndex] = recorded
            currentIndex += 1
        }

        prevState = current
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Game Controller Test Tool", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (testMode) "Follow the order below and press each control once." else "Enable test mode to start.",
            style = MaterialTheme.typography.bodySmall
        )
        val stepLabel = if (currentIndex < steps.size) {
            "Step ${currentIndex + 1}/${steps.size}: ${steps[currentIndex].label}"
        } else {
            "All steps completed"
        }
        Text(stepLabel, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Tips: For stick steps, push in any direction past ~60%. For triggers, press L2 or R2 past ~50%.",
            style = MaterialTheme.typography.bodySmall
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { index, step ->
                val result = results[index]
                val status = when {
                    result != null -> "✓"
                    index == currentIndex -> "→"
                    else -> "·"
                }
                val detail = result?.let {
                    buildResultLine(it)
                } ?: ""
                Text("$status ${step.label} $detail", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                if (currentIndex < steps.size) {
                    val recorded = buildManualResult(steps[currentIndex], state)
                    results[currentIndex] = recorded
                    currentIndex += 1
                }
            }) { Text("Capture + Next") }
            Button(onClick = {
                if (currentIndex < steps.size) {
                    currentIndex += 1
                }
            }) { Text("Next (skip)") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                if (currentIndex > 0) {
                    currentIndex -= 1
                    results[currentIndex] = null
                }
            }) { Text("Back") }
            Button(onClick = {
                results.indices.forEach { results[it] = null }
                currentIndex = 0
                prevState = state
                saveStatus = null
            }) { Text("Reset") }
            Button(onClick = {
                val report = buildReport(steps, results, deviceInfo)
                clipboard.setText(AnnotatedString(report))
            }) { Text("Copy report") }
            Button(onClick = {
                val report = buildReport(steps, results, deviceInfo)
                saveStatus = if (saveReport(context, report)) {
                    "Saved to app files as controller_test_report.txt"
                } else {
                    "Save failed"
                }
            }) { Text("Save report") }
        }
        if (saveStatus != null) {
            Text(saveStatus ?: "", style = MaterialTheme.typography.bodySmall)
        }
        if (isD9) {
            Text(
                text = "Note: L3/R3 + HOME/TURBO/M1/M2 steps omitted for BSP-D9 (Android mode).",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class ControllerTestStep(
    val label: String,
    val type: StepType,
    val dpad: DpadDir? = null,
    val axis: AxisGroup? = null
)

private data class ControllerTestResult(
    val label: String,
    val keyCode: Int?,
    val keyName: String?,
    val scanCode: Int?,
    val action: String,
    val timestampMs: Long,
    val axes: Map<String, Float>
)

private enum class StepType { KEY, DPAD, AXIS }
private enum class DpadDir { UP, DOWN, LEFT, RIGHT }
private enum class AxisGroup { LEFT_STICK, RIGHT_STICK, TRIGGERS }

private fun buildSteps(
    includeThumbButtons: Boolean,
    includeSpecialButtons: Boolean
): List<ControllerTestStep> {
    val steps = mutableListOf(
        ControllerTestStep("A", StepType.KEY),
        ControllerTestStep("B", StepType.KEY),
        ControllerTestStep("X", StepType.KEY),
        ControllerTestStep("Y", StepType.KEY),
        ControllerTestStep("L1", StepType.KEY),
        ControllerTestStep("L2", StepType.KEY),
        ControllerTestStep("R1", StepType.KEY),
        ControllerTestStep("R2", StepType.KEY)
    )
    if (includeThumbButtons) {
        steps.add(ControllerTestStep("L3", StepType.KEY))
        steps.add(ControllerTestStep("R3", StepType.KEY))
    }
    steps.addAll(
        listOf(
            ControllerTestStep("SELECT", StepType.KEY),
            ControllerTestStep("START", StepType.KEY)
        )
    )
    if (includeSpecialButtons) {
        steps.addAll(
            listOf(
                ControllerTestStep("HOME", StepType.KEY),
                ControllerTestStep("TURBO", StepType.KEY),
                ControllerTestStep("M1", StepType.KEY),
                ControllerTestStep("M2", StepType.KEY)
            )
        )
    }
    steps.addAll(
        listOf(
            ControllerTestStep("D-pad Up", StepType.DPAD, dpad = DpadDir.UP),
            ControllerTestStep("D-pad Down", StepType.DPAD, dpad = DpadDir.DOWN),
            ControllerTestStep("D-pad Left", StepType.DPAD, dpad = DpadDir.LEFT),
            ControllerTestStep("D-pad Right", StepType.DPAD, dpad = DpadDir.RIGHT),
            ControllerTestStep("Left stick move", StepType.AXIS, axis = AxisGroup.LEFT_STICK),
            ControllerTestStep("Right stick move", StepType.AXIS, axis = AxisGroup.RIGHT_STICK),
            ControllerTestStep("Triggers squeeze", StepType.AXIS, axis = AxisGroup.TRIGGERS)
        )
    )
    return steps
}

private fun buildResultLine(result: ControllerTestResult): String {
    val key = result.keyName ?: result.keyCode?.toString() ?: ""
    val scan = result.scanCode?.let { "scan=$it" } ?: ""
    val axis = if (result.axes.isNotEmpty()) {
        result.axes.entries.joinToString(
            prefix = "[",
            postfix = "]"
        ) { (k, v) -> "$k=${String.format("%.2f", v)}" }
    } else {
        ""
    }
    val base = listOf(key, scan, axis).filter { it.isNotBlank() }.joinToString(" ")
    return if (base.isBlank() && result.axes.isEmpty()) "(no key event)" else base
}

private fun buildReport(
    steps: List<ControllerTestStep>,
    results: List<ControllerTestResult?>,
    deviceInfo: ControllerDeviceInfo
): String {
    val builder = StringBuilder()
    builder.append("Controller test report\n")
    builder.append("Device: ").append(deviceInfo.deviceName ?: "--").append("\n")
    builder.append("Device id: ").append(deviceInfo.deviceId ?: "--").append("\n")
    if (deviceInfo.vendorId != null && deviceInfo.productId != null) {
        builder.append("VID: ").append(deviceInfo.vendorId).append(" PID: ").append(deviceInfo.productId)
            .append("\n")
    }
    builder.append("Profile: ").append(deviceInfo.profileName ?: "--").append("\n")
    builder.append("\n")
    steps.forEachIndexed { index, step ->
        val result = results[index]
        builder.append(step.label).append(": ")
        if (result == null) {
            builder.append("(missing)")
        } else {
            val key = result.keyName ?: result.keyCode?.toString() ?: ""
            val scan = result.scanCode?.let { " scan=$it" } ?: ""
            builder.append(key)
            if (key.isBlank() && result.axes.isEmpty()) {
                builder.append("(no key event)")
            }
            if (scan.isNotBlank()) {
                builder.append(" ").append(scan)
            }
            if (result.axes.isNotEmpty()) {
                val axis = result.axes.entries.joinToString(
                    prefix = " [",
                    postfix = "]"
                ) { (k, v) -> "$k=${String.format("%.2f", v)}" }
                builder.append(axis)
            }
        }
        builder.append("\n")
    }
    return builder.toString()
}

private fun saveReport(context: Context, report: String): Boolean {
    return try {
        context.openFileOutput("controller_test_report.txt", Context.MODE_PRIVATE).use { stream ->
            stream.write(report.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun buildManualResult(
    step: ControllerTestStep,
    state: ControllerState
): ControllerTestResult {
    val now = SystemClock.uptimeMillis()
    val recentKey = state.lastKeyAction == KeyEvent.ACTION_DOWN &&
        now - state.lastKeyEventAtMs <= KEY_CAPTURE_WINDOW_MS
    val axes = when (step.type) {
        StepType.KEY -> emptyMap()
        StepType.DPAD -> mapOf("hatX" to state.hatX, "hatY" to state.hatY)
        StepType.AXIS -> when (step.axis) {
            AxisGroup.LEFT_STICK -> mapOf("lx" to state.leftX, "ly" to state.leftY)
            AxisGroup.RIGHT_STICK -> mapOf("rx" to state.rightX, "ry" to state.rightY)
            AxisGroup.TRIGGERS -> mapOf("l2" to state.l2, "r2" to state.r2)
            null -> emptyMap()
        }
    }
    return ControllerTestResult(
        label = step.label,
        keyCode = if (step.type == StepType.KEY && recentKey) state.lastKeyCode else null,
        keyName = if (step.type == StepType.KEY && recentKey) state.lastKeyName else null,
        scanCode = if (step.type == StepType.KEY && recentKey) state.lastKeyScanCode else null,
        action = "MANUAL",
        timestampMs = now,
        axes = axes
    )
}

private const val AXIS_THRESHOLD = 0.6f
private const val TRIGGER_THRESHOLD = 0.5f
private const val KEY_CAPTURE_WINDOW_MS = 600L
