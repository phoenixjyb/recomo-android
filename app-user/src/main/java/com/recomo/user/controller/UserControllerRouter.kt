package com.recomo.user.controller

import android.os.SystemClock
import com.recomo.common.controller.ControllerManager
import com.recomo.common.controller.ControllerSettings
import com.recomo.common.controller.ControllerState
import com.recomo.user.control.UserTouchControlViewModel
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Maps Bluetooth controller input to user-app gateway commands.
 *
 * Simplified compared to the engineering app's ControllerRouter:
 * - Left stick  → chassis translate (vx/vy)
 * - Right stick X → chassis yaw (turn), Right stick Y → gimbal pitch
 * - L2/R2       → chassis yaw (alternative)
 * - D-pad       → arm joint nudge (joint selected by L3/R3 cycle)
 * - L1          → deadman (must hold for motion)
 * - A           → arm homing
 * - B           → gimbal homing
 * - START hold  → e-stop
 * - Y hold      → clear e-stop
 */
class UserControllerRouter(
    private val viewModel: UserTouchControlViewModel
) {
    private var settings = ControllerSettings()
    private var prevState = ControllerState()
    private var selectedArmJoint = 0
    private var lastChassisActive = false
    private var lastGimbalActive = false
    private var startHoldStart: Long? = null
    private var startHoldTriggered = false
    private var homeHoldStart: Long? = null
    private var homeHoldTriggered = false
    private var yHoldStart: Long? = null
    private var yHoldTriggered = false

    fun updateSettings(settings: ControllerSettings) {
        this.settings = settings
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
        val connected = settings.enabled &&
            deviceInfo.lastEventAtMs > 0L &&
            (now - deviceInfo.lastEventAtMs) <= CONTROLLER_TIMEOUT_MS

        // Handle hold actions (e-stop / clear) regardless of deadman
        handleHoldActions(state, now)

        // Reset state on controller disconnect
        if (!connected && state.timestampMs > 0L && now - state.timestampMs > CONTROLLER_TIMEOUT_MS) {
            controllerManager.resetState(now)
            prevState = ControllerState(timestampMs = now)
            lastChassisActive = false
            lastGimbalActive = false
        }

        val deadman = state.l1
        val allowMotion = settings.enabled && connected && deadman

        if (!allowMotion) {
            stopAllMotion()
            // Handle non-motion buttons even without deadman
            handleNonMotionButtons(state)
            prevState = state
            return
        }

        // Arm joint selection via L3/R3
        if (justPressed(state.l3, prevState.l3)) {
            selectedArmJoint = (selectedArmJoint - 1 + ARM_JOINTS).mod(ARM_JOINTS)
        }
        if (justPressed(state.r3, prevState.r3)) {
            selectedArmJoint = (selectedArmJoint + 1) % ARM_JOINTS
        }

        // Chassis: left stick (translate) + right stick X / triggers (yaw)
        val vx = scaledAxis(state.leftY, settings.stickSensitivity, settings.invertChassisY)
        val vy = -scaledAxis(state.leftX, settings.stickSensitivity, invert = false)
        val yawStick = scaledAxis(state.rightX, settings.stickSensitivity, invert = false)
        val yawTrigger = scaledAxis(state.r2 - state.l2, settings.stickSensitivity, invert = false)
        val wz = if (abs(yawStick) >= abs(yawTrigger)) yawStick else yawTrigger
        if (abs(vx) > 0 || abs(vy) > 0 || abs(wz) > 0) {
            viewModel.startChassisMove(
                vxDir = if (vx > 0.3) 1 else if (vx < -0.3) -1 else 0,
                vyDir = if (vy > 0.3) 1 else if (vy < -0.3) -1 else 0,
                wzDir = if (wz > 0.3) 1 else if (wz < -0.3) -1 else 0
            )
            lastChassisActive = true
        } else {
            stopChassisIfNeeded()
        }

        // Gimbal: right stick Y (pitch only — yaw is on chassis)
        val gimbalPitch = scaledAxis(state.rightY, settings.gimbalSensitivity, settings.invertGimbalY)
        if (abs(gimbalPitch) > 0) {
            viewModel.startGimbalVelocityControl(1, gimbalPitch)
            lastGimbalActive = true
        } else {
            stopGimbalIfNeeded()
        }

        // Arm: D-pad nudge on selected joint
        if (justPressed(state.dpadUp, prevState.dpadUp)) viewModel.nudgeArmJointPositive(selectedArmJoint)
        if (justPressed(state.dpadDown, prevState.dpadDown)) viewModel.nudgeArmJointNegative(selectedArmJoint)

        // Homing: A = arm home, B = gimbal home
        if (justPressed(state.a, prevState.a)) viewModel.sendArmHomingPose()
        if (justPressed(state.b, prevState.b)) viewModel.sendGimbalHomingPose()

        prevState = state
    }

    private fun handleHoldActions(state: ControllerState, now: Long) {
        // START hold OR HOME hold → e-stop (START for D9, HOME for generic)
        val estopButton = state.start || state.home
        val prevEstopButton = prevState.start || prevState.home
        if (estopButton) {
            if (!prevEstopButton) {
                startHoldStart = now
                startHoldTriggered = false
            }
            val start = startHoldStart ?: now
            if (!startHoldTriggered && now - start >= HOLD_ESTOP_MS) {
                viewModel.estop()
                startHoldTriggered = true
            }
        } else {
            startHoldStart = null
            startHoldTriggered = false
        }

        // Y hold → clear e-stop
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
        } else {
            yHoldStart = null
            yHoldTriggered = false
        }
    }

    private fun handleNonMotionButtons(state: ControllerState) {
        // Allow homing without deadman for convenience
        if (justPressed(state.a, prevState.a)) viewModel.sendArmHomingPose()
        if (justPressed(state.b, prevState.b)) viewModel.sendGimbalHomingPose()
    }

    private fun stopAllMotion() {
        stopChassisIfNeeded()
        stopGimbalIfNeeded()
    }

    private fun stopChassisIfNeeded() {
        if (!lastChassisActive) return
        viewModel.stopChassisMove()
        lastChassisActive = false
    }

    private fun stopGimbalIfNeeded() {
        if (!lastGimbalActive) return
        viewModel.stopGimbalVelocityControl(1)
        viewModel.stopGimbalVelocityControl(2)
        lastGimbalActive = false
    }

    private fun justPressed(current: Boolean, previous: Boolean) = current && !previous

    private fun scaledAxis(value: Float, sensitivity: Float, invert: Boolean): Double {
        val deadzone = 0.25f
        val absValue = abs(value)
        if (absValue < deadzone) return 0.0
        val sign = if (value > 0) 1f else -1f
        val normalized = (absValue - deadzone) / (1f - deadzone)
        val adjusted = if (invert) -normalized * sign else normalized * sign
        return (adjusted * sensitivity).coerceIn(-1f, 1f).toDouble()
    }

    companion object {
        private const val LOOP_MS = 50L
        private const val CONTROLLER_TIMEOUT_MS = 2000L
        private const val HOLD_ESTOP_MS = 1500L
        private const val HOLD_CLEAR_MS = 1500L
        private const val ARM_JOINTS = 3
    }
}
