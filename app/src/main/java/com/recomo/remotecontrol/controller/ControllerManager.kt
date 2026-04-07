package com.recomo.remotecontrol.controller

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.recomo.remotecontrol.settings.ControllerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class ControllerManager(
    private val defaultProfile: ControllerProfile = ControllerProfile.default()
) {
    private val _state = MutableStateFlow(ControllerState())
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    private val _deviceInfo = MutableStateFlow(ControllerDeviceInfo())
    val deviceInfo: StateFlow<ControllerDeviceInfo> = _deviceInfo.asStateFlow()

    private var settings: ControllerSettings = ControllerSettings()
    private var activeProfile: ControllerProfile = defaultProfile
    private var activeDeviceId: Int? = null
    private var triggerAxisInverted: Boolean? = null

    fun updateSettings(settings: ControllerSettings) {
        this.settings = settings
    }

    fun resetState(now: Long = SystemClock.uptimeMillis()) {
        _state.value = ControllerState(timestampMs = now)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!settings.enabled) return false
        if (!isGamepad(event)) return false
        selectProfile(event.device)
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val now = SystemClock.uptimeMillis()
        updateDeviceInfo(event.device, now)
        val button = activeProfile.buttonForKeyCode(event.keyCode)
        if (button == null) {
            _state.value = _state.value.copy(
                lastKeyCode = event.keyCode,
                lastKeyName = KeyEvent.keyCodeToString(event.keyCode),
                lastKeyScanCode = event.scanCode,
                lastKeyAction = event.action,
                lastKeyEventAtMs = now,
                timestampMs = now
            )
            return false
        }

        _state.value = updateButton(_state.value, button, pressed, now).copy(
            lastKeyCode = event.keyCode,
            lastKeyName = KeyEvent.keyCodeToString(event.keyCode),
            lastKeyScanCode = event.scanCode,
            lastKeyAction = event.action,
            lastKeyEventAtMs = now
        )
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!settings.enabled) return false
        if (!isGamepad(event)) return false
        val now = SystemClock.uptimeMillis()
        selectProfile(event.device)
        updateDeviceInfo(event.device, now)

        val deadzone = settings.deadzone
        val triggerDeadzone = settings.triggerDeadzone
        val current = _state.value

        val lx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X), deadzone)
        val ly = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y), deadzone)
        val rx = applyDeadzone(
            selectAxis(
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RX)
            ),
            deadzone
        )
        val ry = applyDeadzone(
            selectAxis(
                event.getAxisValue(MotionEvent.AXIS_RZ),
                event.getAxisValue(MotionEvent.AXIS_RY)
            ),
            deadzone
        )

        var l2Raw = normalizeTrigger(
            selectAxis(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)
            )
        )
        var r2Raw = normalizeTrigger(
            selectAxis(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)
            )
        )
        if (activeProfile.label == "BSP-D9" &&
            triggerAxisInverted == null &&
            !current.l2Button &&
            !current.r2Button
        ) {
            if (l2Raw > 0.9f && r2Raw > 0.9f) {
                triggerAxisInverted = true
            } else if (l2Raw < 0.1f && r2Raw < 0.1f) {
                triggerAxisInverted = false
            }
        }
        if (triggerAxisInverted == true) {
            l2Raw = 1f - l2Raw
            r2Raw = 1f - r2Raw
        }

        val l2Axis = applyTriggerDeadzone(l2Raw, triggerDeadzone)
        val r2Axis = applyTriggerDeadzone(r2Raw, triggerDeadzone)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val hatSupported = event.device?.getMotionRange(MotionEvent.AXIS_HAT_X, event.source) != null ||
            event.device?.getMotionRange(MotionEvent.AXIS_HAT_Y, event.source) != null

        val dpadLeft = if (hatSupported) hatX <= -HAT_THRESHOLD else current.dpadLeft
        val dpadRight = if (hatSupported) hatX >= HAT_THRESHOLD else current.dpadRight
        val dpadUp = if (hatSupported) hatY <= -HAT_THRESHOLD else current.dpadUp
        val dpadDown = if (hatSupported) hatY >= HAT_THRESHOLD else current.dpadDown

        val l2 = if (current.l2Button && l2Axis == 0f) 1f else l2Axis
        val r2 = if (current.r2Button && r2Axis == 0f) 1f else r2Axis

        _state.value = current.copy(
            leftX = lx,
            leftY = ly,
            rightX = rx,
            rightY = ry,
            l2 = l2,
            r2 = r2,
            hatX = hatX,
            hatY = hatY,
            dpadLeft = dpadLeft,
            dpadRight = dpadRight,
            dpadUp = dpadUp,
            dpadDown = dpadDown,
            timestampMs = now
        )
        return true
    }

    private fun updateDeviceInfo(device: InputDevice?, now: Long) {
        val name = device?.name
        val id = device?.id
        val vendorId = device?.vendorId
        val productId = device?.productId
        _deviceInfo.value = ControllerDeviceInfo(
            connected = true,
            deviceId = id,
            deviceName = name,
            vendorId = vendorId,
            productId = productId,
            profileName = activeProfile.label,
            lastEventAtMs = now
        )
    }

    fun detectDeviceInfo(): ControllerDeviceInfo? {
        val devices = InputDevice.getDeviceIds()
            .map { InputDevice.getDevice(it) }
            .filterNotNull()
            .filter { isGamepadSource(it.sources) }

        val preferred = devices.firstOrNull {
            val name = it.name.lowercase()
            name.contains("bsp") && name.contains("d9")
        } ?: devices.firstOrNull()

        return preferred?.let { device ->
            val profile = ControllerProfile.forDevice(device)
            ControllerDeviceInfo(
                connected = true,
                deviceId = device.id,
                deviceName = device.name,
                vendorId = device.vendorId,
                productId = device.productId,
                profileName = profile.label,
                lastEventAtMs = _deviceInfo.value.lastEventAtMs
            )
        }
    }

    private fun selectProfile(device: InputDevice?) {
        val deviceId = device?.id
        if (deviceId != activeDeviceId) {
            activeProfile = ControllerProfile.forDevice(device)
            activeDeviceId = deviceId
            triggerAxisInverted = null
        }
    }

    private fun updateButton(
        current: ControllerState,
        button: ControllerButton,
        pressed: Boolean,
        now: Long
    ): ControllerState {
        return when (button) {
            ControllerButton.A -> current.copy(a = pressed, timestampMs = now)
            ControllerButton.B -> current.copy(b = pressed, timestampMs = now)
            ControllerButton.X -> current.copy(x = pressed, timestampMs = now)
            ControllerButton.Y -> current.copy(y = pressed, timestampMs = now)
            ControllerButton.L1 -> current.copy(l1 = pressed, timestampMs = now)
            ControllerButton.L2 -> current.copy(
                l2Button = pressed,
                l2 = if (pressed) 1f else 0f,
                timestampMs = now
            )
            ControllerButton.R1 -> current.copy(r1 = pressed, timestampMs = now)
            ControllerButton.R2 -> current.copy(
                r2Button = pressed,
                r2 = if (pressed) 1f else 0f,
                timestampMs = now
            )
            ControllerButton.L3 -> current.copy(l3 = pressed, timestampMs = now)
            ControllerButton.R3 -> current.copy(r3 = pressed, timestampMs = now)
            ControllerButton.SELECT -> current.copy(select = pressed, timestampMs = now)
            ControllerButton.START -> current.copy(start = pressed, timestampMs = now)
            ControllerButton.HOME -> current.copy(home = pressed, timestampMs = now)
            ControllerButton.TURBO -> current.copy(turbo = pressed, timestampMs = now)
            ControllerButton.M1 -> current.copy(m1 = pressed, timestampMs = now)
            ControllerButton.M2 -> current.copy(m2 = pressed, timestampMs = now)
            ControllerButton.DPAD_UP -> current.copy(dpadUp = pressed, timestampMs = now)
            ControllerButton.DPAD_DOWN -> current.copy(dpadDown = pressed, timestampMs = now)
            ControllerButton.DPAD_LEFT -> current.copy(dpadLeft = pressed, timestampMs = now)
            ControllerButton.DPAD_RIGHT -> current.copy(dpadRight = pressed, timestampMs = now)
        }
    }

    private fun isGamepad(event: KeyEvent): Boolean {
        return isGamepadSource(event.source)
    }

    private fun isGamepad(event: MotionEvent): Boolean {
        return isGamepadSource(event.source)
    }

    private fun isGamepadSource(source: Int): Boolean {
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD ||
            source and InputDevice.SOURCE_CLASS_JOYSTICK == InputDevice.SOURCE_CLASS_JOYSTICK ||
            source and InputDevice.SOURCE_CLASS_BUTTON == InputDevice.SOURCE_CLASS_BUTTON
    }

    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        if (abs(value) < deadzone) return 0f
        return value
    }

    private fun applyTriggerDeadzone(value: Float, deadzone: Float): Float {
        if (value < deadzone) return 0f
        val scaled = (value - deadzone) / (1f - deadzone)
        return scaled.coerceIn(0f, 1f)
    }

    private fun selectAxis(primary: Float, fallback: Float): Float {
        return if (abs(primary) >= abs(fallback)) primary else fallback
    }

    private fun normalizeTrigger(value: Float): Float {
        val normalized = if (value < 0f) (value + 1f) / 2f else value
        return normalized.coerceIn(0f, 1f)
    }

    companion object {
        private const val HAT_THRESHOLD = 0.5f
    }
}
