package com.recomo.remotecontrol.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControllerSettingsRepository @Inject constructor(
    @RecomoControllerStore private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("controller_enabled")
        val TEST_MODE = booleanPreferencesKey("controller_test_mode")
        val DEADZONE = floatPreferencesKey("controller_deadzone")
        val TRIGGER_DEADZONE = floatPreferencesKey("controller_trigger_deadzone")
        val STICK_SENSITIVITY = floatPreferencesKey("controller_stick_sensitivity")
        val GIMBAL_SENSITIVITY = floatPreferencesKey("controller_gimbal_sensitivity")
        val ARM_SENSITIVITY = floatPreferencesKey("controller_arm_sensitivity")
        val INVERT_CHASSIS_Y = booleanPreferencesKey("controller_invert_chassis_y")
        val INVERT_GIMBAL_Y = booleanPreferencesKey("controller_invert_gimbal_y")
        val INVERT_ARM_Y = booleanPreferencesKey("controller_invert_arm_y")
        val ALLOW_ALL_DOF = booleanPreferencesKey("controller_allow_all_dof")
    }

    val settings: Flow<ControllerSettings> = dataStore.data.map { prefs ->
        ControllerSettings(
            enabled = prefs[Keys.ENABLED] ?: ControllerSettings().enabled,
            testMode = prefs[Keys.TEST_MODE] ?: ControllerSettings().testMode,
            deadzone = prefs[Keys.DEADZONE] ?: ControllerSettings().deadzone,
            triggerDeadzone = prefs[Keys.TRIGGER_DEADZONE] ?: ControllerSettings().triggerDeadzone,
            stickSensitivity = prefs[Keys.STICK_SENSITIVITY] ?: ControllerSettings().stickSensitivity,
            gimbalSensitivity = prefs[Keys.GIMBAL_SENSITIVITY] ?: ControllerSettings().gimbalSensitivity,
            armSensitivity = prefs[Keys.ARM_SENSITIVITY] ?: ControllerSettings().armSensitivity,
            invertChassisY = prefs[Keys.INVERT_CHASSIS_Y] ?: ControllerSettings().invertChassisY,
            invertGimbalY = prefs[Keys.INVERT_GIMBAL_Y] ?: ControllerSettings().invertGimbalY,
            invertArmY = prefs[Keys.INVERT_ARM_Y] ?: ControllerSettings().invertArmY,
            allowAllDof = prefs[Keys.ALLOW_ALL_DOF] ?: ControllerSettings().allowAllDof
        )
    }

    suspend fun update(settings: ControllerSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = settings.enabled
            prefs[Keys.TEST_MODE] = settings.testMode
            prefs[Keys.DEADZONE] = clamp(settings.deadzone, 0.0f, 0.9f)
            prefs[Keys.TRIGGER_DEADZONE] = clamp(settings.triggerDeadzone, 0.0f, 0.9f)
            prefs[Keys.STICK_SENSITIVITY] = clamp(settings.stickSensitivity, 0.1f, 2.0f)
            prefs[Keys.GIMBAL_SENSITIVITY] = clamp(settings.gimbalSensitivity, 0.1f, 2.0f)
            prefs[Keys.ARM_SENSITIVITY] = clamp(settings.armSensitivity, 0.1f, 2.0f)
            prefs[Keys.INVERT_CHASSIS_Y] = settings.invertChassisY
            prefs[Keys.INVERT_GIMBAL_Y] = settings.invertGimbalY
            prefs[Keys.INVERT_ARM_Y] = settings.invertArmY
            prefs[Keys.ALLOW_ALL_DOF] = settings.allowAllDof
        }
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return value.coerceIn(min, max)
    }
}
