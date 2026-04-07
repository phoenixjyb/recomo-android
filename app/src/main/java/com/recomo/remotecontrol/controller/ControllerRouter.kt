package com.recomo.remotecontrol.controller

import android.os.SystemClock
import com.recomo.remotecontrol.settings.ControllerSettings
import com.recomo.remotecontrol.ui.ArmControlMode
import com.recomo.remotecontrol.ui.ControlMode
import com.recomo.remotecontrol.ui.ControlFocus
import com.recomo.remotecontrol.ui.RecomoControlViewModel
import com.recomo.remotecontrol.ui.RunStatus
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControllerRouter(
    private val viewModel: RecomoControlViewModel
) {
    private var settings: ControllerSettings = ControllerSettings()
    private var prevState: ControllerState = ControllerState()
    private var controlMode: ControlMode = ControlMode.FOCUS
    private var modeSelectActive = false
    private var modeCandidate: ControlMode = ControlMode.FOCUS
    private var tabSelectActive = false
    private var tabCandidate = 0
    private var selectedArmJointIdx = 0
    private var homeHoldStart: Long? = null
    private var homeHoldTriggered = false
    private var yHoldStart: Long? = null
    private var yHoldTriggered = false
    private var startHoldStart: Long? = null
    private var startHoldTriggered = false
    private var selectHoldStart: Long? = null
    private var selectHoldTriggered = false
    private var lastChassisActive = false
    private var lastArmActive = false
    private var lastGimbalActive = false

    fun updateSettings(settings: ControllerSettings) {
        this.settings = settings
        if (!settings.allowAllDof && controlMode == ControlMode.ALL_DOF) {
            controlMode = ControlMode.FOCUS
        }
    }

    fun start(scope: CoroutineScope, controllerManager: ControllerManager) {
        scope.launch {
            while (isActive) {
                tick(controllerManager)
                delay(LOOP_MS)
            }
        }
    }

    private fun tick(controllerManager: ControllerManager) {
        val now = SystemClock.uptimeMillis()
        val state = controllerManager.state.value
        val deviceInfo = controllerManager.deviceInfo.value
        val enabled = settings.enabled
        val testMode = settings.testMode
        val detected = controllerManager.detectDeviceInfo()
        val devicePresent = detected != null
        val profileName = deviceInfo.profileName ?: detected?.profileName
        val isD9 = profileName?.contains("bsp-d9", ignoreCase = true) == true
        val connected = enabled &&
            deviceInfo.lastEventAtMs > 0L &&
            (now - deviceInfo.lastEventAtMs) <= CONTROLLER_TIMEOUT_MS

        val focus = viewModel.controlFocus.value
        val armMode = viewModel.armControlMode.value
        val cineActive = controlMode == ControlMode.CINE
        val allDofActive = controlMode == ControlMode.ALL_DOF

        viewModel.updateControllerStatus(
            com.recomo.remotecontrol.ui.ControllerStatus(
                enabled = enabled,
                testMode = testMode,
                connected = connected,
                devicePresent = devicePresent,
                deviceName = deviceInfo.deviceName ?: detected?.deviceName,
                deviceId = deviceInfo.deviceId ?: detected?.deviceId,
                vendorId = deviceInfo.vendorId ?: detected?.vendorId,
                productId = deviceInfo.productId ?: detected?.productId,
                profileName = profileName,
                lastEventAtMs = deviceInfo.lastEventAtMs,
                controlMode = controlMode,
                modeSelectActive = modeSelectActive,
                modeCandidate = modeCandidate,
                cineActive = cineActive,
                allDofActive = allDofActive,
                tabSelectActive = tabSelectActive,
                focus = focus,
                armMode = armMode
            )
        )

        if (!testMode) {
            if (isD9) {
                handleD9HoldActions(state, now)
            } else {
                handleHoldActions(state, now)
            }
        }

        if (!connected && state.timestampMs > 0L && now - state.timestampMs > CONTROLLER_TIMEOUT_MS) {
            controllerManager.resetState(now)
            prevState = ControllerState(timestampMs = now)
            lastChassisActive = false
            lastArmActive = false
            lastGimbalActive = false
        }

        val deadman = state.l1
        handleSelectAndMode(state, now, deadman)
        if (modeSelectActive) {
            if (!testMode) {
                stopAllMotion()
            }
            prevState = state
            return
        }

        if (controlMode == ControlMode.FOCUS && justPressed(state.r1, prevState.r1) && !tabSelectActive) {
            viewModel.cycleControlFocus()
        }
        if (isD9) {
            handleD9Combos(state)
        } else {
            if (justPressed(state.m1, prevState.m1)) {
                viewModel.toggleArmControlMode()
            }
            if (justPressed(state.turbo, prevState.turbo)) {
                viewModel.cycleStepMode()
            }
        }

        val suppressTabActions = isD9 && deadman && state.select
        if (!tabSelectActive && !testMode && !suppressTabActions) {
            handleTabActions(state)
        }

        val safety = viewModel.safetyStatus.value
        val safetyOk = safety.commOk && !safety.estop && !safety.freezeAll
        val allowMotion = enabled && connected && deadman && safetyOk && !tabSelectActive && !modeSelectActive && !testMode

        if (!allowMotion) {
            if (!testMode) {
                stopAllMotion()
            }
            prevState = state
            return
        }

        when (controlMode) {
            ControlMode.FOCUS -> handleFocusedControl(state, focus, armMode)
            ControlMode.POSITION -> handlePosition(state, armMode)
            ControlMode.UPPER -> handleUpper(state, armMode)
            ControlMode.CINE -> handleCineDrive(state)
            ControlMode.ALL_DOF -> handleAllDof(state, armMode)
        }

        prevState = state
    }

    private fun handleHoldActions(state: ControllerState, now: Long) {
        if (state.home) {
            if (!prevState.home) {
                homeHoldStart = now
                homeHoldTriggered = false
            }
            val start = homeHoldStart ?: now
            if (!homeHoldTriggered && now - start >= HOLD_ESTOP_MS) {
                viewModel.sendEstop()
                homeHoldTriggered = true
            }
        } else {
            homeHoldStart = null
            homeHoldTriggered = false
        }

        if (state.y) {
            if (!prevState.y) {
                yHoldStart = now
                yHoldTriggered = false
            }
            val start = yHoldStart ?: now
            if (!yHoldTriggered && now - start >= HOLD_CLEAR_MS) {
                viewModel.clearEstop()
                yHoldTriggered = true
            }
        } else if (prevState.y && !yHoldTriggered) {
            handleYTap()
            yHoldStart = null
            yHoldTriggered = false
        } else if (!state.y) {
            yHoldStart = null
            yHoldTriggered = false
        }

    }

    private fun handleD9HoldActions(state: ControllerState, now: Long) {
        if (state.start) {
            if (!prevState.start) {
                startHoldStart = now
                startHoldTriggered = false
            }
            val start = startHoldStart ?: now
            if (!startHoldTriggered && now - start >= HOLD_ESTOP_MS) {
                viewModel.sendEstop()
                startHoldTriggered = true
            }
        } else {
            startHoldStart = null
            startHoldTriggered = false
        }

        if (state.y) {
            if (!prevState.y) {
                yHoldStart = now
                yHoldTriggered = false
            }
            val start = yHoldStart ?: now
            if (!yHoldTriggered && now - start >= HOLD_CLEAR_MS) {
                viewModel.clearEstop()
                yHoldTriggered = true
            }
        } else if (prevState.y && !yHoldTriggered) {
            handleYTap()
            yHoldStart = null
            yHoldTriggered = false
        } else if (!state.y) {
            yHoldStart = null
            yHoldTriggered = false
        }
    }

    private fun handleD9Combos(state: ControllerState) {
        if (modeSelectActive) return
        val modifier = state.l1 && state.select
        if (!modifier) return

        if (justPressed(state.x, prevState.x)) {
            viewModel.toggleArmControlMode()
        }
        if (justPressed(state.y, prevState.y)) {
            viewModel.cycleStepMode()
        }
    }

    private fun handleSelectAndMode(state: ControllerState, now: Long, deadman: Boolean) {
        if (deadman && !modeSelectActive && !tabSelectActive) {
            selectHoldStart = null
            selectHoldTriggered = false
            return
        }

        if (state.select) {
            if (!prevState.select) {
                selectHoldStart = now
                selectHoldTriggered = false
            }
            val start = selectHoldStart ?: now
            if (!selectHoldTriggered && now - start >= HOLD_MODE_SELECT_MS && !modeSelectActive) {
                modeSelectActive = true
                modeCandidate = controlMode
                tabSelectActive = false
                selectHoldTriggered = true
            }
        } else if (prevState.select && !selectHoldTriggered && !modeSelectActive) {
            if (tabSelectActive) {
                tabSelectActive = false
            } else {
                tabSelectActive = true
                tabCandidate = if (TAB_CYCLE.contains(viewModel.selectedTab.value)) {
                    viewModel.selectedTab.value
                } else {
                    TAB_CYCLE.first()
                }
            }
        }

        if (!state.select) {
            selectHoldStart = null
            selectHoldTriggered = false
        }

        if (modeSelectActive) {
            if (justPressed(state.dpadLeft, prevState.dpadLeft)) {
                modeCandidate = previousMode(modeCandidate)
            }
            if (justPressed(state.dpadRight, prevState.dpadRight)) {
                modeCandidate = nextMode(modeCandidate)
            }
            if (justPressed(state.a, prevState.a)) {
                controlMode = modeCandidate
                modeSelectActive = false
            }
            if (justPressed(state.b, prevState.b) || justPressed(state.select, prevState.select)) {
                modeSelectActive = false
            }
            return
        }

        if (tabSelectActive) {
            if (justPressed(state.dpadLeft, prevState.dpadLeft)) {
                tabCandidate = cycleTabCandidate(tabCandidate, -1)
            }
            if (justPressed(state.dpadRight, prevState.dpadRight)) {
                tabCandidate = cycleTabCandidate(tabCandidate, 1)
            }
            if (justPressed(state.a, prevState.a)) {
                viewModel.setSelectedTab(tabCandidate)
                tabSelectActive = false
            }
            if (justPressed(state.b, prevState.b) || justPressed(state.select, prevState.select)) {
                tabSelectActive = false
            }
        }
    }

    private fun handleTabActions(state: ControllerState) {
        when (viewModel.selectedTab.value) {
            TAB_TEACH -> {
                if (justPressed(state.a, prevState.a)) viewModel.recordFrame("", "")
                if (justPressed(state.b, prevState.b)) viewModel.newSession("")
                if (justPressed(state.x, prevState.x)) viewModel.saveSessions()
            }
            TAB_RUN -> {
                if (justPressed(state.a, prevState.a)) {
                    val status = viewModel.runState.value.status
                    if (state.l1) {
                        if (status == RunStatus.PAUSED) viewModel.resumeRun(deadman = true)
                        else viewModel.startRun(deadman = true)
                    }
                }
                if (justPressed(state.x, prevState.x)) viewModel.pauseRun()
                if (justPressed(state.b, prevState.b)) viewModel.stopRun()
                if (!state.l1) {
                    if (justPressed(state.dpadLeft, prevState.dpadLeft)) viewModel.prevFoi()
                    if (justPressed(state.dpadRight, prevState.dpadRight)) viewModel.nextFoi()
                }
            }
        }
    }

    private fun handleYTap() {
        if (tabSelectActive) return
        when (viewModel.selectedTab.value) {
            TAB_TEACH -> viewModel.setHomebase("", "")
            TAB_RUN -> viewModel.toggleSkipHomebase()
        }
    }

    private fun handleCineDrive(state: ControllerState) {
        val chassis = chassisAxes(state, useYawStick = false)
        val gimbal = gimbalAxesNoRoll(state)
        sendChassis(chassis.vx, chassis.vy, chassis.wz)
        sendGimbal(gimbal.roll, gimbal.pitch, gimbal.yaw)
        stopArmIfNeeded()
    }

    private fun handleAllDof(state: ControllerState, armMode: ArmControlMode) {
        val chassis = chassisAxes(state, useYawStick = false)
        val gimbal = gimbalAxesNoRoll(state)
        sendChassis(chassis.vx, chassis.vy, chassis.wz)
        sendGimbal(gimbal.roll, gimbal.pitch, gimbal.yaw)
        handleArmWithDpad(state, armMode, modifier = state.r1)
    }

    private fun handlePosition(state: ControllerState, armMode: ArmControlMode) {
        val chassis = chassisAxes(state, useYawStick = false)
        sendChassis(chassis.vx, chassis.vy, chassis.wz)
        handleArmWithRightStick(state, armMode)
        stopGimbalIfNeeded()
    }

    private fun handleUpper(state: ControllerState, armMode: ArmControlMode) {
        val gimbal = gimbalAxesNoRoll(state)
        handleArmControl(state, armMode)
        sendGimbal(gimbal.roll, gimbal.pitch, gimbal.yaw)
        stopChassisIfNeeded()
    }

    private fun handleFocusedControl(state: ControllerState, focus: ControlFocus, armMode: ArmControlMode) {
        when (focus) {
            ControlFocus.CHASSIS -> {
                val chassis = chassisAxes(state, useYawStick = true)
                sendChassis(chassis.vx, chassis.vy, chassis.wz)
                handleChassisSteps(state)
                stopArmIfNeeded()
                stopGimbalIfNeeded()
            }
            ControlFocus.GIMBAL -> {
                val gimbal = gimbalAxes(state)
                sendGimbal(gimbal.roll, gimbal.pitch, gimbal.yaw)
                handleGimbalSteps(state)
                stopChassisIfNeeded()
                stopArmIfNeeded()
            }
            ControlFocus.ARM -> {
                handleArmControl(state, armMode)
                stopChassisIfNeeded()
                stopGimbalIfNeeded()
            }
        }
    }

    private fun handleArmControl(state: ControllerState, armMode: ArmControlMode) {
        when (armMode) {
            ArmControlMode.EE_POSITION -> {
                val delta = armEeDelta(state)
                if (delta.dx != 0.0 || delta.dy != 0.0 || delta.dz != 0.0) {
                    viewModel.nudgeCameraGoalContinuous(delta.dx, delta.dy, delta.dz)
                }
                lastArmActive = true
            }
            ArmControlMode.JOINT_ANGLE -> {
                handleArmJointSelection(state)
                val axis = scaledAxis(state.leftY, settings.armSensitivity, settings.invertArmY)
                viewModel.sendArmJointVelocityNormalized(selectedArmJoint(), axis)
                if (justPressed(state.dpadUp, prevState.dpadUp)) viewModel.nudgeArmJointPositive(selectedArmJoint())
                if (justPressed(state.dpadDown, prevState.dpadDown)) viewModel.nudgeArmJointNegative(selectedArmJoint())
                lastArmActive = true
            }
        }
    }

    private fun handleArmWithRightStick(state: ControllerState, armMode: ArmControlMode) {
        when (armMode) {
            ArmControlMode.EE_POSITION -> {
                val verticalAxis = when {
                    state.dpadUp -> 1f
                    state.dpadDown -> -1f
                    else -> 0f
                }
                val delta = armEeDeltaFromAxes(state.rightY, state.rightX, verticalAxis)
                if (delta.dx != 0.0 || delta.dy != 0.0 || delta.dz != 0.0) {
                    viewModel.nudgeCameraGoalContinuous(delta.dx, delta.dy, delta.dz)
                    lastArmActive = true
                }
            }
            ArmControlMode.JOINT_ANGLE -> {
                handleArmJointSelection(state)
                val axis = scaledAxis(state.rightY, settings.armSensitivity, settings.invertArmY)
                viewModel.sendArmJointVelocityNormalized(selectedArmJoint(), axis)
                if (justPressed(state.dpadUp, prevState.dpadUp)) viewModel.nudgeArmJointPositive(selectedArmJoint())
                if (justPressed(state.dpadDown, prevState.dpadDown)) viewModel.nudgeArmJointNegative(selectedArmJoint())
                lastArmActive = true
            }
        }
    }

    private fun handleArmWithDpad(state: ControllerState, armMode: ArmControlMode, modifier: Boolean) {
        when (armMode) {
            ArmControlMode.EE_POSITION -> {
                val step = viewModel.armStepMeters()
                val up = justPressed(state.dpadUp, prevState.dpadUp)
                val down = justPressed(state.dpadDown, prevState.dpadDown)
                val left = justPressed(state.dpadLeft, prevState.dpadLeft)
                val right = justPressed(state.dpadRight, prevState.dpadRight)
                val dx = if (up) step else if (down) -step else 0.0
                val dy = if (left) step else if (right) -step else 0.0
                val dz = if (modifier) {
                    when {
                        up -> step
                        down -> -step
                        else -> 0.0
                    }
                } else {
                    0.0
                }
                if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
                    viewModel.nudgeCameraGoalContinuous(dx, dy, dz)
                }
                lastArmActive = true
            }
            ArmControlMode.JOINT_ANGLE -> {
                handleArmJointSelection(state)
                if (justPressed(state.dpadUp, prevState.dpadUp)) viewModel.nudgeArmJointPositive(selectedArmJoint())
                if (justPressed(state.dpadDown, prevState.dpadDown)) viewModel.nudgeArmJointNegative(selectedArmJoint())
                lastArmActive = true
            }
        }
    }

    private fun handleArmJointSelection(state: ControllerState) {
        val options = armJointOptions()
        if (justPressed(state.dpadLeft, prevState.dpadLeft)) {
            selectedArmJointIdx = (selectedArmJointIdx - 1 + options.size) % options.size
        }
        if (justPressed(state.dpadRight, prevState.dpadRight)) {
            selectedArmJointIdx = (selectedArmJointIdx + 1) % options.size
        }
    }

    private fun handleChassisSteps(state: ControllerState) {
        if (justPressed(state.dpadUp, prevState.dpadUp)) viewModel.sendChassisStep(1, 0)
        if (justPressed(state.dpadDown, prevState.dpadDown)) viewModel.sendChassisStep(-1, 0)
        if (justPressed(state.dpadLeft, prevState.dpadLeft)) viewModel.sendChassisStep(0, 1)
        if (justPressed(state.dpadRight, prevState.dpadRight)) viewModel.sendChassisStep(0, -1)
    }

    private fun handleGimbalSteps(state: ControllerState) {
        // D-pad Left/Right for Roll (step-based, no drift)
        if (justPressed(state.dpadLeft, prevState.dpadLeft)) viewModel.nudgeGimbalNegative(0)
        if (justPressed(state.dpadRight, prevState.dpadRight)) viewModel.nudgeGimbalPositive(0)
        // D-pad Up/Down for Pitch
        if (justPressed(state.dpadUp, prevState.dpadUp)) viewModel.nudgeGimbalPositive(1)
        if (justPressed(state.dpadDown, prevState.dpadDown)) viewModel.nudgeGimbalNegative(1)
    }

    private fun chassisAxes(state: ControllerState, useYawStick: Boolean): ChassisAxes {
        val vx = scaledAxis(state.leftY, settings.stickSensitivity, settings.invertChassisY)
        val vy = -scaledAxis(state.leftX, settings.stickSensitivity, invert = false)
        val yawTrigger = scaledAxis(state.r2 - state.l2, settings.stickSensitivity, invert = false)
        val yawStick = if (useYawStick) {
            scaledAxis(state.rightX, settings.stickSensitivity, invert = false)
        } else {
            0.0
        }
        val wz = if (abs(yawStick) >= abs(yawTrigger)) yawStick else yawTrigger
        return ChassisAxes(vx, vy, wz)
    }

    private fun gimbalAxes(state: ControllerState): GimbalAxes {
        val yaw = scaledAxis(state.rightX, settings.gimbalSensitivity, invert = false)
        val pitch = scaledAxis(state.rightY, settings.gimbalSensitivity, settings.invertGimbalY)
        val roll = 0.0  // Roll disabled in continuous mode - use D-pad L/R for step control
        return GimbalAxes(roll = roll, pitch = pitch, yaw = yaw)
    }

    private fun gimbalAxesNoRoll(state: ControllerState): GimbalAxes {
        val yaw = scaledAxis(state.rightX, settings.gimbalSensitivity, invert = false)
        val pitch = scaledAxis(state.rightY, settings.gimbalSensitivity, settings.invertGimbalY)
        return GimbalAxes(roll = 0.0, pitch = pitch, yaw = yaw)
    }

    private fun armEeDelta(state: ControllerState): ArmDelta {
        return armEeDeltaFromAxes(state.leftY, state.leftX, state.r2 - state.l2)
    }

    private fun armEeDeltaFromAxes(forwardAxis: Float, lateralAxis: Float, verticalAxis: Float): ArmDelta {
        val step = viewModel.armStepMeters() * EE_DELTA_SCALE
        val forward = scaledAxis(forwardAxis, settings.armSensitivity, settings.invertArmY)
        val lateral = scaledAxis(lateralAxis, settings.armSensitivity, invert = false)
        val vertical = scaledAxis(verticalAxis, settings.armSensitivity, invert = false)
        val dx = forward * step
        val dy = -lateral * step
        val dz = vertical * step
        return ArmDelta(dx, dy, dz)
    }

    private fun sendChassis(vx: Double, vy: Double, wz: Double) {
        viewModel.sendChassisVelocityNormalized(vx, vy, wz)
        lastChassisActive = true
    }

    private fun sendGimbal(roll: Double, pitch: Double, yaw: Double) {
        if (abs(roll) < GIMBAL_IDLE_EPS && abs(pitch) < GIMBAL_IDLE_EPS && abs(yaw) < GIMBAL_IDLE_EPS) {
            stopGimbalIfNeeded()
            return
        }
        viewModel.sendGimbalVelocityNormalized(roll, pitch, yaw)
        lastGimbalActive = true
    }

    private fun stopAllMotion() {
        stopChassisIfNeeded()
        stopArmIfNeeded()
        stopGimbalIfNeeded()
    }

    private fun stopChassisIfNeeded() {
        if (!lastChassisActive) return
        viewModel.sendDrive(0.0, 0.0, 0.0, brake = true)
        lastChassisActive = false
    }

    private fun stopArmIfNeeded() {
        if (!lastArmActive) return
        viewModel.sendArmJog(0.0, 0.0, 0.0)
        lastArmActive = false
    }

    private fun stopGimbalIfNeeded() {
        if (!lastGimbalActive) return
        viewModel.sendGimbalVelocityNormalized(0.0, 0.0, 0.0)
        lastGimbalActive = false
    }

    private fun justPressed(current: Boolean, previous: Boolean): Boolean {
        return current && !previous
    }

    private fun modeSequence(): List<ControlMode> {
        val modes = mutableListOf(
            ControlMode.FOCUS,
            ControlMode.POSITION,
            ControlMode.UPPER,
            ControlMode.CINE
        )
        if (settings.allowAllDof) {
            modes.add(ControlMode.ALL_DOF)
        }
        return modes
    }

    private fun nextMode(current: ControlMode): ControlMode {
        val modes = modeSequence()
        val index = modes.indexOf(current).takeIf { it >= 0 } ?: 0
        return modes[(index + 1) % modes.size]
    }

    private fun previousMode(current: ControlMode): ControlMode {
        val modes = modeSequence()
        val index = modes.indexOf(current).takeIf { it >= 0 } ?: 0
        return modes[(index - 1 + modes.size) % modes.size]
    }

    private fun scaledAxis(value: Float, sensitivity: Float, invert: Boolean): Double {
        val deadzone = 0.25f  // Increased deadzone to 25% to prevent stick drift
        val absValue = kotlin.math.abs(value)
        if (absValue < deadzone) return 0.0
        
        val sign = if (value > 0) 1f else -1f
        val normalized = (absValue - deadzone) / (1f - deadzone)  // Map deadzone-1.0 -> 0-1.0
        val adjusted = if (invert) -normalized * sign else normalized * sign
        val scaled = adjusted * sensitivity
        return scaled.coerceIn(-1f, 1f).toDouble()
    }

    private fun selectedArmJoint(): Int {
        val options = armJointOptions()
        if (selectedArmJointIdx !in options.indices) {
            selectedArmJointIdx = 0
        }
        return options[selectedArmJointIdx]
    }

    private fun armJointOptions(): IntArray {
        return if (viewModel.robotProfile.value.isProto1Family()) {
            intArrayOf(0, 1, 2)
        } else {
            intArrayOf(1, 2)
        }
    }

    private fun cycleTabCandidate(current: Int, step: Int): Int {
        val cycle = TAB_CYCLE
        val idx = cycle.indexOf(current).let { if (it >= 0) it else 0 }
        val next = (idx + step + cycle.size) % cycle.size
        return cycle[next]
    }

    private data class ChassisAxes(val vx: Double, val vy: Double, val wz: Double)
    private data class GimbalAxes(val roll: Double, val pitch: Double, val yaw: Double)
    private data class ArmDelta(val dx: Double, val dy: Double, val dz: Double)

    companion object {
        private const val LOOP_MS = 50L
        private const val CONTROLLER_TIMEOUT_MS = 2000L
        private const val HOLD_ESTOP_MS = 1500L
        private const val HOLD_CLEAR_MS = 1500L
        private const val HOLD_MODE_SELECT_MS = 700L
        private const val EE_DELTA_SCALE = 0.2
        private const val GIMBAL_IDLE_EPS = 0.1
        private const val TAB_TEACH = 0
        private const val TAB_RUN = 1
        private const val TAB_LIBRARY = 3
        private const val TAB_VIDEO_MANAGEMENT = 4
        private const val TAB_SETTINGS = 5
        private val TAB_CYCLE = intArrayOf(
            TAB_TEACH,
            TAB_RUN,
            TAB_LIBRARY,
            TAB_VIDEO_MANAGEMENT,
            TAB_SETTINGS
        )
    }
}
