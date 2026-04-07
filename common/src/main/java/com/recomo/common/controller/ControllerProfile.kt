package com.recomo.common.controller

import android.view.InputDevice
import android.view.KeyEvent

class ControllerProfile private constructor(
    val label: String,
    private val keyMap: Map<ControllerButton, Set<Int>>
) {
    fun buttonForKeyCode(keyCode: Int): ControllerButton? {
        return keyMap.entries.firstOrNull { (_, codes) -> keyCode in codes }?.key
    }

    companion object {
        fun default(): ControllerProfile {
            return ControllerProfile(
                label = "Generic",
                mapOf(
                    ControllerButton.A to setOf(KeyEvent.KEYCODE_BUTTON_A),
                    ControllerButton.B to setOf(KeyEvent.KEYCODE_BUTTON_B),
                    ControllerButton.X to setOf(KeyEvent.KEYCODE_BUTTON_X),
                    ControllerButton.Y to setOf(KeyEvent.KEYCODE_BUTTON_Y),
                    ControllerButton.L1 to setOf(KeyEvent.KEYCODE_BUTTON_L1),
                    ControllerButton.L2 to setOf(KeyEvent.KEYCODE_BUTTON_L2),
                    ControllerButton.R1 to setOf(KeyEvent.KEYCODE_BUTTON_R1),
                    ControllerButton.R2 to setOf(KeyEvent.KEYCODE_BUTTON_R2),
                    ControllerButton.L3 to setOf(KeyEvent.KEYCODE_BUTTON_THUMBL),
                    ControllerButton.R3 to setOf(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    ControllerButton.SELECT to setOf(
                        KeyEvent.KEYCODE_BUTTON_SELECT,
                        KeyEvent.KEYCODE_BACK
                    ),
                    ControllerButton.START to setOf(KeyEvent.KEYCODE_BUTTON_START),
                    ControllerButton.HOME to setOf(KeyEvent.KEYCODE_BUTTON_MODE),
                    ControllerButton.TURBO to setOf(
                        KeyEvent.KEYCODE_BUTTON_1,
                        KeyEvent.KEYCODE_BUTTON_4
                    ),
                    ControllerButton.M1 to setOf(
                        KeyEvent.KEYCODE_BUTTON_2,
                        KeyEvent.KEYCODE_BUTTON_5
                    ),
                    ControllerButton.M2 to setOf(
                        KeyEvent.KEYCODE_BUTTON_3,
                        KeyEvent.KEYCODE_BUTTON_6
                    ),
                    ControllerButton.DPAD_UP to setOf(KeyEvent.KEYCODE_DPAD_UP),
                    ControllerButton.DPAD_DOWN to setOf(KeyEvent.KEYCODE_DPAD_DOWN),
                    ControllerButton.DPAD_LEFT to setOf(KeyEvent.KEYCODE_DPAD_LEFT),
                    ControllerButton.DPAD_RIGHT to setOf(KeyEvent.KEYCODE_DPAD_RIGHT)
                )
            )
        }

        fun d9(): ControllerProfile {
            return ControllerProfile(
                label = "BSP-D9",
                mapOf(
                    ControllerButton.A to setOf(
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_BUTTON_C
                    ),
                    ControllerButton.B to setOf(KeyEvent.KEYCODE_BUTTON_Y),
                    ControllerButton.X to setOf(KeyEvent.KEYCODE_BUTTON_X),
                    ControllerButton.Y to setOf(KeyEvent.KEYCODE_BUTTON_B),
                    ControllerButton.L1 to setOf(KeyEvent.KEYCODE_BUTTON_L1),
                    ControllerButton.L2 to setOf(KeyEvent.KEYCODE_BUTTON_L2),
                    ControllerButton.R1 to setOf(KeyEvent.KEYCODE_BUTTON_R1),
                    ControllerButton.R2 to setOf(KeyEvent.KEYCODE_BUTTON_R2),
                    ControllerButton.L3 to setOf(KeyEvent.KEYCODE_BUTTON_THUMBL),
                    ControllerButton.R3 to setOf(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    ControllerButton.SELECT to setOf(
                        KeyEvent.KEYCODE_BUTTON_SELECT,
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_MENU
                    ),
                    ControllerButton.START to setOf(
                        KeyEvent.KEYCODE_BUTTON_START,
                        KeyEvent.KEYCODE_BUTTON_10
                    ),
                    ControllerButton.DPAD_UP to setOf(KeyEvent.KEYCODE_DPAD_UP),
                    ControllerButton.DPAD_DOWN to setOf(KeyEvent.KEYCODE_DPAD_DOWN),
                    ControllerButton.DPAD_LEFT to setOf(KeyEvent.KEYCODE_DPAD_LEFT),
                    ControllerButton.DPAD_RIGHT to setOf(KeyEvent.KEYCODE_DPAD_RIGHT)
                )
            )
        }

        fun forDevice(device: InputDevice?): ControllerProfile {
            val name = device?.name?.lowercase() ?: return default()
            return if (name.contains("bsp") && name.contains("d9")) d9() else default()
        }
    }
}
