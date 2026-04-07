package com.recomo.remotecontrol.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepSettingsRepository @Inject constructor(
    @RecomoStepStore private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        // Position step sizes
        val CHASSIS_FINE_M = doublePreferencesKey("chassis_fine_m")
        val CHASSIS_NORMAL_M = doublePreferencesKey("chassis_normal_m")
        val CHASSIS_LEAP_M = doublePreferencesKey("chassis_leap_m")
        val ARM_FINE_M = doublePreferencesKey("arm_fine_m")
        val ARM_NORMAL_M = doublePreferencesKey("arm_normal_m")
        val ARM_LEAP_M = doublePreferencesKey("arm_leap_m")
        val ARM_JOINT_FINE_DEG = doublePreferencesKey("arm_joint_fine_deg")
        val ARM_JOINT_NORMAL_DEG = doublePreferencesKey("arm_joint_normal_deg")
        val ARM_JOINT_LEAP_DEG = doublePreferencesKey("arm_joint_leap_deg")
        val GIMBAL_FINE_DEG = doublePreferencesKey("gimbal_fine_deg")
        val GIMBAL_NORMAL_DEG = doublePreferencesKey("gimbal_normal_deg")
        val GIMBAL_LEAP_DEG = doublePreferencesKey("gimbal_leap_deg")
        val YAW_FINE_DEG = doublePreferencesKey("yaw_fine_deg")
        val YAW_NORMAL_DEG = doublePreferencesKey("yaw_normal_deg")
        val YAW_LEAP_DEG = doublePreferencesKey("yaw_leap_deg")
        // Velocity settings
        val CHASSIS_FINE_VEL = doublePreferencesKey("chassis_fine_vel")
        val CHASSIS_NORMAL_VEL = doublePreferencesKey("chassis_normal_vel")
        val CHASSIS_LEAP_VEL = doublePreferencesKey("chassis_leap_vel")
        val ARM_FINE_VEL = doublePreferencesKey("arm_fine_vel")
        val ARM_NORMAL_VEL = doublePreferencesKey("arm_normal_vel")
        val ARM_LEAP_VEL = doublePreferencesKey("arm_leap_vel")
        val GIMBAL_FINE_VEL_DEG = doublePreferencesKey("gimbal_fine_vel_deg")
        val GIMBAL_NORMAL_VEL_DEG = doublePreferencesKey("gimbal_normal_vel_deg")
        val GIMBAL_LEAP_VEL_DEG = doublePreferencesKey("gimbal_leap_vel_deg")
    }

    val stepSettings: Flow<StepSettings> = dataStore.data.map { prefs ->
        StepSettings(
            // Position step sizes
            chassisFineM = prefs[Keys.CHASSIS_FINE_M] ?: StepSettings().chassisFineM,
            chassisNormalM = prefs[Keys.CHASSIS_NORMAL_M] ?: StepSettings().chassisNormalM,
            chassisLeapM = prefs[Keys.CHASSIS_LEAP_M] ?: StepSettings().chassisLeapM,
            armFineM = prefs[Keys.ARM_FINE_M] ?: StepSettings().armFineM,
            armNormalM = prefs[Keys.ARM_NORMAL_M] ?: StepSettings().armNormalM,
            armLeapM = prefs[Keys.ARM_LEAP_M] ?: StepSettings().armLeapM,
            armJointFineDeg = prefs[Keys.ARM_JOINT_FINE_DEG] ?: StepSettings().armJointFineDeg,
            armJointNormalDeg = prefs[Keys.ARM_JOINT_NORMAL_DEG] ?: StepSettings().armJointNormalDeg,
            armJointLeapDeg = prefs[Keys.ARM_JOINT_LEAP_DEG] ?: StepSettings().armJointLeapDeg,
            gimbalFineDeg = prefs[Keys.GIMBAL_FINE_DEG] ?: StepSettings().gimbalFineDeg,
            gimbalNormalDeg = prefs[Keys.GIMBAL_NORMAL_DEG] ?: StepSettings().gimbalNormalDeg,
            gimbalLeapDeg = prefs[Keys.GIMBAL_LEAP_DEG] ?: StepSettings().gimbalLeapDeg,
            yawFineDeg = prefs[Keys.YAW_FINE_DEG] ?: StepSettings().yawFineDeg,
            yawNormalDeg = prefs[Keys.YAW_NORMAL_DEG] ?: StepSettings().yawNormalDeg,
            yawLeapDeg = prefs[Keys.YAW_LEAP_DEG] ?: StepSettings().yawLeapDeg,
            // Velocity settings
            chassisFineVel = prefs[Keys.CHASSIS_FINE_VEL] ?: StepSettings().chassisFineVel,
            chassisNormalVel = prefs[Keys.CHASSIS_NORMAL_VEL] ?: StepSettings().chassisNormalVel,
            chassisLeapVel = prefs[Keys.CHASSIS_LEAP_VEL] ?: StepSettings().chassisLeapVel,
            armFineVel = prefs[Keys.ARM_FINE_VEL] ?: StepSettings().armFineVel,
            armNormalVel = prefs[Keys.ARM_NORMAL_VEL] ?: StepSettings().armNormalVel,
            armLeapVel = prefs[Keys.ARM_LEAP_VEL] ?: StepSettings().armLeapVel,
            gimbalFineVelDeg = prefs[Keys.GIMBAL_FINE_VEL_DEG] ?: StepSettings().gimbalFineVelDeg,
            gimbalNormalVelDeg = prefs[Keys.GIMBAL_NORMAL_VEL_DEG] ?: StepSettings().gimbalNormalVelDeg,
            gimbalLeapVelDeg = prefs[Keys.GIMBAL_LEAP_VEL_DEG] ?: StepSettings().gimbalLeapVelDeg
        )
    }

    suspend fun updateSettings(settings: StepSettings) {
        dataStore.edit { prefs ->
            // Position step sizes
            prefs[Keys.CHASSIS_FINE_M] = clamp(settings.chassisFineM, StepSettingsBounds.CHASSIS_MIN_M, StepSettingsBounds.CHASSIS_MAX_M)
            prefs[Keys.CHASSIS_NORMAL_M] = clamp(settings.chassisNormalM, StepSettingsBounds.CHASSIS_MIN_M, StepSettingsBounds.CHASSIS_MAX_M)
            prefs[Keys.CHASSIS_LEAP_M] = clamp(settings.chassisLeapM, StepSettingsBounds.CHASSIS_MIN_M, StepSettingsBounds.CHASSIS_MAX_M)
            prefs[Keys.ARM_FINE_M] = clamp(settings.armFineM, StepSettingsBounds.ARM_MIN_M, StepSettingsBounds.ARM_MAX_M)
            prefs[Keys.ARM_NORMAL_M] = clamp(settings.armNormalM, StepSettingsBounds.ARM_MIN_M, StepSettingsBounds.ARM_MAX_M)
            prefs[Keys.ARM_LEAP_M] = clamp(settings.armLeapM, StepSettingsBounds.ARM_MIN_M, StepSettingsBounds.ARM_MAX_M)
            prefs[Keys.ARM_JOINT_FINE_DEG] = clamp(
                settings.armJointFineDeg,
                StepSettingsBounds.ARM_JOINT_MIN_DEG,
                StepSettingsBounds.ARM_JOINT_MAX_DEG
            )
            prefs[Keys.ARM_JOINT_NORMAL_DEG] = clamp(
                settings.armJointNormalDeg,
                StepSettingsBounds.ARM_JOINT_MIN_DEG,
                StepSettingsBounds.ARM_JOINT_MAX_DEG
            )
            prefs[Keys.ARM_JOINT_LEAP_DEG] = clamp(
                settings.armJointLeapDeg,
                StepSettingsBounds.ARM_JOINT_MIN_DEG,
                StepSettingsBounds.ARM_JOINT_MAX_DEG
            )
            prefs[Keys.GIMBAL_FINE_DEG] = clamp(settings.gimbalFineDeg, StepSettingsBounds.GIMBAL_MIN_DEG, StepSettingsBounds.GIMBAL_MAX_DEG)
            prefs[Keys.GIMBAL_NORMAL_DEG] = clamp(settings.gimbalNormalDeg, StepSettingsBounds.GIMBAL_MIN_DEG, StepSettingsBounds.GIMBAL_MAX_DEG)
            prefs[Keys.GIMBAL_LEAP_DEG] = clamp(settings.gimbalLeapDeg, StepSettingsBounds.GIMBAL_MIN_DEG, StepSettingsBounds.GIMBAL_MAX_DEG)
            prefs[Keys.YAW_FINE_DEG] = clamp(settings.yawFineDeg, StepSettingsBounds.YAW_MIN_DEG, StepSettingsBounds.YAW_MAX_DEG)
            prefs[Keys.YAW_NORMAL_DEG] = clamp(settings.yawNormalDeg, StepSettingsBounds.YAW_MIN_DEG, StepSettingsBounds.YAW_MAX_DEG)
            prefs[Keys.YAW_LEAP_DEG] = clamp(settings.yawLeapDeg, StepSettingsBounds.YAW_MIN_DEG, StepSettingsBounds.YAW_MAX_DEG)
            // Velocity settings
            prefs[Keys.CHASSIS_FINE_VEL] = clamp(settings.chassisFineVel, StepSettingsBounds.CHASSIS_MIN_VEL, StepSettingsBounds.CHASSIS_MAX_VEL)
            prefs[Keys.CHASSIS_NORMAL_VEL] = clamp(settings.chassisNormalVel, StepSettingsBounds.CHASSIS_MIN_VEL, StepSettingsBounds.CHASSIS_MAX_VEL)
            prefs[Keys.CHASSIS_LEAP_VEL] = clamp(settings.chassisLeapVel, StepSettingsBounds.CHASSIS_MIN_VEL, StepSettingsBounds.CHASSIS_MAX_VEL)
            prefs[Keys.ARM_FINE_VEL] = clamp(settings.armFineVel, StepSettingsBounds.ARM_MIN_VEL, StepSettingsBounds.ARM_MAX_VEL)
            prefs[Keys.ARM_NORMAL_VEL] = clamp(settings.armNormalVel, StepSettingsBounds.ARM_MIN_VEL, StepSettingsBounds.ARM_MAX_VEL)
            prefs[Keys.ARM_LEAP_VEL] = clamp(settings.armLeapVel, StepSettingsBounds.ARM_MIN_VEL, StepSettingsBounds.ARM_MAX_VEL)
            prefs[Keys.GIMBAL_FINE_VEL_DEG] = clamp(settings.gimbalFineVelDeg, StepSettingsBounds.GIMBAL_MIN_VEL_DEG, StepSettingsBounds.GIMBAL_MAX_VEL_DEG)
            prefs[Keys.GIMBAL_NORMAL_VEL_DEG] = clamp(settings.gimbalNormalVelDeg, StepSettingsBounds.GIMBAL_MIN_VEL_DEG, StepSettingsBounds.GIMBAL_MAX_VEL_DEG)
            prefs[Keys.GIMBAL_LEAP_VEL_DEG] = clamp(settings.gimbalLeapVelDeg, StepSettingsBounds.GIMBAL_MIN_VEL_DEG, StepSettingsBounds.GIMBAL_MAX_VEL_DEG)
        }
    }

    private fun clamp(value: Double, min: Double, max: Double): Double {
        return value.coerceIn(min, max)
    }
}
