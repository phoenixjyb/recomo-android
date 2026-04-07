package com.recomo.remotecontrol.camviewer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.recomo.remotecontrol.camviewer.data.model.AppSettings
import com.recomo.remotecontrol.camviewer.data.model.NetworkPreset
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import com.recomo.remotecontrol.camviewer.data.model.TrackingMode
import com.recomo.remotecontrol.camviewer.data.model.VideoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val NETWORK_PRESET = stringPreferencesKey("network_preset")
        val USE_TZTEK_ORIN = booleanPreferencesKey("use_tztek_orin")
        val ROBOT_PROFILE = stringPreferencesKey("robot_profile")
        val CAMERA_URL = stringPreferencesKey("camera_url")
        val ORIN_CAMERA_URL = stringPreferencesKey("orin_camera_url")
        val ORIN_TARGET_URL = stringPreferencesKey("orin_target_url")
        val ORIN_MEDIA_URL = stringPreferencesKey("orin_media_url")
        val ORIN_TRACKING_URL = stringPreferencesKey("orin_tracking_url")
        val PHONE_CONTROL_HOST = stringPreferencesKey("phone_control_host")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val SERVICE_CONTROL_PIN_ENABLED = booleanPreferencesKey("service_control_pin_enabled")
        val SERVICE_CONTROL_PIN = stringPreferencesKey("service_control_pin")
        val USE_WEBRTC = booleanPreferencesKey("use_webrtc")
        val SIGNALING_URL = stringPreferencesKey("signaling_url")
        val TRACKING_MODE = stringPreferencesKey("tracking_mode")
        val VIEW_ONLY = booleanPreferencesKey("view_only")
        val VIDEO_SOURCE = stringPreferencesKey("video_source")
        val TRACKING_TARGET_WIDTH = intPreferencesKey("tracking_target_width")
        val TRACKING_TARGET_HEIGHT = intPreferencesKey("tracking_target_height")
    }
    
    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val presetName = preferences[PreferencesKeys.NETWORK_PRESET] ?: NetworkPreset.ZEROTIER.name
        val preset = NetworkPreset.fromName(presetName)
        val storedUseTztek = preferences[PreferencesKeys.USE_TZTEK_ORIN] ?: true
        val profile = RobotProfile.fromName(preferences[PreferencesKeys.ROBOT_PROFILE], storedUseTztek)
        val resolvedUseTztek = profile.useTztekOrin ?: storedUseTztek
        val usePresetManagedUrls = preset != NetworkPreset.CUSTOM && profile.useTztekOrin != null
        val trackingMode = TrackingMode.fromName(preferences[PreferencesKeys.TRACKING_MODE])
        val videoSource = VideoSource.fromName(preferences[PreferencesKeys.VIDEO_SOURCE])

        AppSettings(
            networkPreset = preset,
            useTztekOrin = resolvedUseTztek,
            robotProfile = profile,
            cameraUrl = if (usePresetManagedUrls) preset.getPhoneVideoUrl()
            else preferences[PreferencesKeys.CAMERA_URL] ?: preset.getPhoneVideoUrl(),
            orinCameraUrl = if (usePresetManagedUrls) preset.getOrinCameraUrl(profile, resolvedUseTztek)
            else preferences[PreferencesKeys.ORIN_CAMERA_URL] ?: preset.getOrinCameraUrl(profile, resolvedUseTztek),
            orinTargetUrl = if (usePresetManagedUrls) preset.getOrinTargetUrl(profile, resolvedUseTztek)
            else preferences[PreferencesKeys.ORIN_TARGET_URL] ?: preset.getOrinTargetUrl(profile, resolvedUseTztek),
            orinMediaUrl = if (usePresetManagedUrls) preset.getOrinMediaUrl(profile, resolvedUseTztek)
            else preferences[PreferencesKeys.ORIN_MEDIA_URL] ?: preset.getOrinMediaUrl(profile, resolvedUseTztek),
            orinTrackingUrl = if (usePresetManagedUrls) preset.getOrinTrackingUrl(profile, resolvedUseTztek)
            else preferences[PreferencesKeys.ORIN_TRACKING_URL] ?: preset.getOrinTrackingUrl(profile, resolvedUseTztek),
            phoneControlHost = if (usePresetManagedUrls) preset.phoneIp
            else preferences[PreferencesKeys.PHONE_CONTROL_HOST] ?: preset.phoneIp,
            developerModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE] ?: false,
            serviceControlPinEnabled = preferences[PreferencesKeys.SERVICE_CONTROL_PIN_ENABLED] ?: false,
            serviceControlPin = preferences[PreferencesKeys.SERVICE_CONTROL_PIN] ?: "",
            useWebRTC = preferences[PreferencesKeys.USE_WEBRTC] ?: false,
            signalingUrl = if (usePresetManagedUrls) preset.getSignalingUrl(profile, resolvedUseTztek)
            else preferences[PreferencesKeys.SIGNALING_URL] ?: preset.getSignalingUrl(profile, resolvedUseTztek),
            trackingMode = trackingMode,
            viewOnly = preferences[PreferencesKeys.VIEW_ONLY] ?: false,
            videoSource = videoSource,
            trackingTargetWidth = preferences[PreferencesKeys.TRACKING_TARGET_WIDTH] ?: 720,
            trackingTargetHeight = preferences[PreferencesKeys.TRACKING_TARGET_HEIGHT] ?: 480
        )
    }
    
    suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NETWORK_PRESET] = settings.networkPreset.name
            preferences[PreferencesKeys.USE_TZTEK_ORIN] = settings.useTztekOrin
            preferences[PreferencesKeys.ROBOT_PROFILE] = settings.robotProfile.name
            preferences[PreferencesKeys.CAMERA_URL] = settings.cameraUrl
            preferences[PreferencesKeys.ORIN_CAMERA_URL] = settings.orinCameraUrl
            preferences[PreferencesKeys.ORIN_TARGET_URL] = settings.orinTargetUrl
            preferences[PreferencesKeys.ORIN_MEDIA_URL] = settings.orinMediaUrl
            preferences[PreferencesKeys.ORIN_TRACKING_URL] = settings.orinTrackingUrl
            preferences[PreferencesKeys.PHONE_CONTROL_HOST] = settings.phoneControlHost
            preferences[PreferencesKeys.DEVELOPER_MODE] = settings.developerModeEnabled
            preferences[PreferencesKeys.SERVICE_CONTROL_PIN_ENABLED] = settings.serviceControlPinEnabled
            preferences[PreferencesKeys.SERVICE_CONTROL_PIN] = settings.serviceControlPin
            preferences[PreferencesKeys.USE_WEBRTC] = settings.useWebRTC
            preferences[PreferencesKeys.SIGNALING_URL] = settings.signalingUrl
            preferences[PreferencesKeys.TRACKING_MODE] = settings.trackingMode.name
            preferences[PreferencesKeys.VIEW_ONLY] = settings.viewOnly
            preferences[PreferencesKeys.VIDEO_SOURCE] = settings.videoSource.name
            preferences[PreferencesKeys.TRACKING_TARGET_WIDTH] = settings.trackingTargetWidth
            preferences[PreferencesKeys.TRACKING_TARGET_HEIGHT] = settings.trackingTargetHeight
        }
    }
    
    suspend fun applyNetworkPreset(preset: NetworkPreset, useTztek: Boolean? = null) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NETWORK_PRESET] = preset.name
            if (preset != NetworkPreset.CUSTOM) {
                val storedUseTztek = preferences[PreferencesKeys.USE_TZTEK_ORIN] ?: true
                val profile = RobotProfile.fromName(preferences[PreferencesKeys.ROBOT_PROFILE], storedUseTztek)
                val effectiveUseTztek = profile.useTztekOrin ?: (useTztek ?: storedUseTztek)
                if (profile.useTztekOrin != null || useTztek != null) {
                    preferences[PreferencesKeys.USE_TZTEK_ORIN] = effectiveUseTztek
                }
                preferences[PreferencesKeys.CAMERA_URL] = preset.getPhoneVideoUrl()
                preferences[PreferencesKeys.ORIN_CAMERA_URL] = preset.getOrinCameraUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.ORIN_TARGET_URL] = preset.getOrinTargetUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.ORIN_MEDIA_URL] = preset.getOrinMediaUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.ORIN_TRACKING_URL] = preset.getOrinTrackingUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.PHONE_CONTROL_HOST] = preset.phoneIp
                preferences[PreferencesKeys.SIGNALING_URL] = preset.getSignalingUrl(profile, effectiveUseTztek)
            }
        }
    }

    suspend fun applyRobotProfile(profile: RobotProfile) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROBOT_PROFILE] = profile.name
            val presetName = preferences[PreferencesKeys.NETWORK_PRESET] ?: NetworkPreset.ZEROTIER.name
            val preset = NetworkPreset.fromName(presetName)
            val storedUseTztek = preferences[PreferencesKeys.USE_TZTEK_ORIN] ?: true
            val effectiveUseTztek = profile.useTztekOrin ?: storedUseTztek
            if (profile.useTztekOrin != null) {
                preferences[PreferencesKeys.USE_TZTEK_ORIN] = effectiveUseTztek
            }
            if (preset != NetworkPreset.CUSTOM) {
                preferences[PreferencesKeys.CAMERA_URL] = preset.getPhoneVideoUrl()
                preferences[PreferencesKeys.ORIN_CAMERA_URL] = preset.getOrinCameraUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.ORIN_TARGET_URL] = preset.getOrinTargetUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.ORIN_MEDIA_URL] = preset.getOrinMediaUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.ORIN_TRACKING_URL] = preset.getOrinTrackingUrl(profile, effectiveUseTztek)
                preferences[PreferencesKeys.PHONE_CONTROL_HOST] = preset.phoneIp
                preferences[PreferencesKeys.SIGNALING_URL] = preset.getSignalingUrl(profile, effectiveUseTztek)
            }
        }
    }
    
    suspend fun setDeveloperMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE] = enabled
        }
    }
    
    suspend fun setServiceControlPin(pin: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVICE_CONTROL_PIN] = pin
        }
    }
    
    suspend fun setVideoTransport(useWebRTC: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_WEBRTC] = useWebRTC
        }
    }
    
    suspend fun setVideoSource(source: VideoSource) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIDEO_SOURCE] = source.name
        }
    }
}
