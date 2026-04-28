package com.recomo.user.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import android.net.Uri
import com.recomo.common.model.AppSettings
import com.recomo.common.model.NetworkPreset
import com.recomo.common.model.RobotProfile
import com.recomo.common.model.VideoSource
import com.recomo.user.BuildConfig
import com.recomo.user.data.ee.EESpeedMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val robotProfile = stringPreferencesKey("user_robot_profile")
        val networkPreset = stringPreferencesKey("user_network_preset")
        val chatServerUrl = stringPreferencesKey("user_chat_server_url")
        val sessionFolderPath = stringPreferencesKey("user_session_folder_path")
        val sceneViewerFolderPath = stringPreferencesKey("user_sceneviewer_folder_path")
        val customGatewayUrl = stringPreferencesKey("user_custom_gateway_url")
        val useWebRTC = booleanPreferencesKey("user_use_webrtc")
        val videoSource = stringPreferencesKey("user_video_source")
        val eePositionSpeedMode = stringPreferencesKey("user_ee_position_speed_mode")
        val eePositionDryRun = booleanPreferencesKey("user_ee_position_dry_run")

        // AI chat direct-transport (Option A) — dark until cloud public IP lands.
        val chatDirectEnabled = booleanPreferencesKey("user_chat_direct_enabled")
        val chatDirectBaseUrl = stringPreferencesKey("user_chat_direct_base_url")
        val chatDirectAuthToken = stringPreferencesKey("user_chat_direct_auth_token")

        // Voice input engine: "SYSTEM" (default, Android SpeechRecognizer)
        // or "WHISPER" (sherpa-onnx, offline, needs model download).
        val voiceEngine = stringPreferencesKey("user_voice_engine")
        val voiceModel = stringPreferencesKey("user_voice_model")

        // Speed tier overrides. Stored as chassis m/s; arm and gimbal scale
        // proportionally from the per-tier default ratio. A value of 0 (or
        // absent) means "use the built-in default".
        val speedSlowMps = floatPreferencesKey("user_speed_slow_mps")
        val speedNormalMps = floatPreferencesKey("user_speed_normal_mps")
        val speedFastMps = floatPreferencesKey("user_speed_fast_mps")

        // Studio Dance countdown duration in seconds (default 3)
        val countdownDurationSeconds = intPreferencesKey("user_countdown_duration_seconds")
    }

    val appSettings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val storedProfile = RobotProfile.fromName(preferences[Keys.robotProfile], false)
        val preset = NetworkPreset.fromName(
            preferences[Keys.networkPreset] ?: NetworkPreset.RECOMO_LOCAL.name
        )
        // When no robot has been explicitly selected, default to dogwood-01 so that
        // orinCameraUrl (and all other Orin URLs) are populated with a valid address.
        // This prevents the video ViewModel from connecting to a blank or wrong URL.
        val profile = if (storedProfile == RobotProfile.NONE) {
            RobotProfile.RECOMO_PROTO1_DOGWOOD01
        } else {
            storedProfile
        }
        val useWebRTC = preferences[Keys.useWebRTC] ?: false
        val videoSource = VideoSource.fromName(preferences[Keys.videoSource])
        val customUrl = preferences[Keys.customGatewayUrl].orEmpty().trim()
        val base = AppSettings.fromPreset(
            preset = preset,
            robotProfile = profile
        )
        // Only apply custom URL override when CUSTOM profile is selected
        val customHost = if (profile == RobotProfile.CUSTOM) {
            customUrl.takeIf { it.isNotBlank() }?.let { extractHost(it) }
        } else null
        if (customHost != null) {
            base.copy(
                useWebRTC = useWebRTC,
                videoSource = videoSource,
                signalingUrl = "ws://$customHost:9077",
                webrtcSignalingUrl = "ws://$customHost:${NetworkPreset.WEBRTC_SIGNALING_PORT}",
                orinCameraUrl = "ws://$customHost:${NetworkPreset.ORIN_CAMERA_WS_PORT}"
            )
        } else {
            base.copy(useWebRTC = useWebRTC, videoSource = videoSource)
        }
    }

    private fun extractHost(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        // Ensure scheme so Uri.parse treats it as authority, not path
        val normalized = if (trimmed.contains("://")) trimmed else "ws://$trimmed"
        return try {
            Uri.parse(normalized).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // Fallback: strip scheme, port, path
            normalized.substringAfter("://").substringBefore("/").substringBefore(":").takeIf { it.isNotBlank() }
        }
    }

    val chatServerUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.chatServerUrl].orEmpty().ifBlank { BuildConfig.DEFAULT_CHAT_SERVER_URL }
    }

    val sessionFolderPath: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.sessionFolderPath].orEmpty().trim()
    }

    val sceneViewerFolderPath: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.sceneViewerFolderPath].orEmpty().trim()
    }

    val customGatewayUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.customGatewayUrl].orEmpty().trim()
    }

    /** Selected EE teleop speed preset (fine / normal / leap). Default NORMAL. */
    val eePositionSpeedMode: Flow<EESpeedMode> = dataStore.data.map { preferences ->
        EESpeedMode.fromName(preferences[Keys.eePositionSpeedMode])
    }

    /**
     * Whether the EE IK controller should start in dry-run mode. Default **true** —
     * known velocity amplification bug (see memory/project_camera_ik.md) means
     * hardware motion must be opt-in, not opt-out.
     */
    val eePositionDryRun: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.eePositionDryRun] ?: true
    }

    suspend fun selectRobot(profile: RobotProfile, preset: NetworkPreset) {
        dataStore.edit { preferences ->
            preferences[Keys.robotProfile] = profile.name
            preferences[Keys.networkPreset] = preset.name
        }
    }

    suspend fun updateChatServerUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.chatServerUrl] = url.trim()
        }
    }

    suspend fun updateSessionFolderPath(path: String) {
        dataStore.edit { preferences ->
            preferences[Keys.sessionFolderPath] = path.trim()
        }
    }

    suspend fun updateSceneViewerFolderPath(path: String) {
        dataStore.edit { preferences ->
            preferences[Keys.sceneViewerFolderPath] = path.trim()
        }
    }

    suspend fun updateCustomGatewayUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.customGatewayUrl] = url.trim()
        }
    }

    suspend fun updateUseWebRTC(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.useWebRTC] = enabled
        }
    }

    suspend fun updateVideoSource(source: VideoSource) {
        dataStore.edit { preferences ->
            preferences[Keys.videoSource] = source.name
        }
    }

    suspend fun updateEEPositionSpeedMode(mode: EESpeedMode) {
        dataStore.edit { preferences ->
            preferences[Keys.eePositionSpeedMode] = mode.name
        }
    }

    suspend fun updateEEPositionDryRun(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.eePositionDryRun] = enabled
        }
    }

    // ── AI chat direct transport ─────────────────────────────────────
    // See docs/AI_CHAT_DIRECT_PLAN.md. These feed ChatTransportFactory
    // once wanqiang's public endpoint is confirmed; defaults keep the
    // Termux bridge path active.

    val chatDirectEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.chatDirectEnabled] ?: true // default ON — cloud endpoint is stable
    }

    val chatDirectBaseUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.chatDirectBaseUrl].orEmpty().trim()
            .ifBlank { DEFAULT_DIRECT_CLOUD_URL }
    }

    companion object {
        const val DEFAULT_DIRECT_CLOUD_URL = "http://115.191.11.164:9060"
    }

    val chatDirectAuthToken: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.chatDirectAuthToken].orEmpty()
    }

    suspend fun updateChatDirectEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.chatDirectEnabled] = enabled
        }
    }

    suspend fun updateChatDirectBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.chatDirectBaseUrl] = url.trim()
        }
    }

    suspend fun updateChatDirectAuthToken(token: String) {
        dataStore.edit { preferences ->
            preferences[Keys.chatDirectAuthToken] = token.trim()
        }
    }

    // ── Voice input ──────────────────────────────────────────────────

    val voiceEngine: Flow<com.recomo.common.chat.voice.VoiceEngine> = dataStore.data.map { prefs ->
        when (prefs[Keys.voiceEngine]?.uppercase()) {
            "WHISPER" -> com.recomo.common.chat.voice.VoiceEngine.WHISPER
            else -> com.recomo.common.chat.voice.VoiceEngine.SYSTEM
        }
    }

    suspend fun updateVoiceEngine(engine: com.recomo.common.chat.voice.VoiceEngine) {
        dataStore.edit { preferences ->
            preferences[Keys.voiceEngine] = engine.name
        }
    }

    val voiceModel: Flow<com.recomo.common.chat.voice.WhisperModel> = dataStore.data.map { prefs ->
        com.recomo.common.chat.voice.WhisperModel.fromModelId(prefs[Keys.voiceModel])
    }

    suspend fun updateVoiceModel(model: com.recomo.common.chat.voice.WhisperModel) {
        dataStore.edit { preferences ->
            preferences[Keys.voiceModel] = model.modelId
        }
    }

    // ── Speed tier overrides ────────────────────────────────────────

    /** User-configured chassis speed (m/s) per tier. 0 = use default. */
    data class SpeedTierOverrides(
        val slowMps: Float = 0f,
        val normalMps: Float = 0f,
        val fastMps: Float = 0f
    )

    val speedTierOverrides: Flow<SpeedTierOverrides> = dataStore.data.map { prefs ->
        SpeedTierOverrides(
            slowMps = prefs[Keys.speedSlowMps] ?: 0f,
            normalMps = prefs[Keys.speedNormalMps] ?: 0f,
            fastMps = prefs[Keys.speedFastMps] ?: 0f
        )
    }

    suspend fun updateSpeedTier(slow: Float, normal: Float, fast: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.speedSlowMps] = slow
            preferences[Keys.speedNormalMps] = normal
            preferences[Keys.speedFastMps] = fast
        }
    }

    // ── Studio Dance ────────────────────────────────────────────────

    /** Countdown duration in seconds before synced run starts. Default 3. */
    val countdownDurationSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.countdownDurationSeconds] ?: 3
    }

    suspend fun updateCountdownDuration(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.countdownDurationSeconds] = seconds.coerceIn(1, 10)
        }
    }
}
