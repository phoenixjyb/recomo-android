package com.recomo.common.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed settings for the phone-moco capture pipeline.
 *
 * Lives in :common because the same capture stack is shared between :app's engineering v3dr
 * overlay and :app-user's Phone Teach feature. VIO-specific settings (vioBackend, cloudSfmUrl)
 * remain in :app's v3dr/data/SettingsRepository.kt — not all consumers of CaptureSettingsRepository
 * care about VIO.
 *
 * Uses a separate DataStore file ("capture_settings") from :app's legacy "settings" file to keep
 * the two repositories decoupled. Existing dev users lose any pre-split capture preferences and
 * fall back to defaults — acceptable for this worktree.
 */
private val Context.captureSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "capture_settings")

data class CaptureSettings(
    val resolution: Resolution = Resolution.HD_1080P,
    val bitrate: Bitrate = Bitrate.HIGH,
    val frameRate: Int = 30,
    val serverUrl: String = "http://192.168.100.97:9100",
    val networkTag: NetworkTag = NetworkTag.ZEROTIER,
    val autoUpload: Boolean = false,
    val wifiOnlyUpload: Boolean = true
)

enum class Resolution(val width: Int, val height: Int, val displayName: String) {
    HD_1080P(1920, 1080, "1080p (1920x1080)"),
    HD_720P(1280, 720, "720p (1280x720)"),
    SD_480P(640, 480, "480p (640x480)")
}

enum class Bitrate(val value: Int, val displayName: String) {
    HIGH(8_388_608, "High (8 Mbps)"),
    MEDIUM(4_194_304, "Medium (4 Mbps)"),
    LOW(2_097_152, "Low (2 Mbps)")
}

@Singleton
class CaptureSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val BITRATE = stringPreferencesKey("bitrate")
        val FRAME_RATE = intPreferencesKey("frame_rate")
        val SERVER_URL = stringPreferencesKey("server_url")
        val NETWORK_TAG = stringPreferencesKey("network_tag")
        val AUTO_UPLOAD = booleanPreferencesKey("auto_upload")
        val WIFI_ONLY_UPLOAD = booleanPreferencesKey("wifi_only_upload")
    }

    val settingsFlow: Flow<CaptureSettings> = context.captureSettingsDataStore.data.map { preferences ->
        val networkTag = preferences[PreferencesKeys.NETWORK_TAG]?.let {
            try { NetworkTag.valueOf(it) } catch (e: Exception) { NetworkTag.ZEROTIER }
        } ?: NetworkTag.ZEROTIER

        val savedUrl = preferences[PreferencesKeys.SERVER_URL]
        val serverUrl = if (!savedUrl.isNullOrEmpty()) {
            savedUrl
        } else {
            NetworkProfiles.getByTag(networkTag)?.serverUrl ?: "http://192.168.100.97:9100"
        }

        CaptureSettings(
            resolution = preferences[PreferencesKeys.RESOLUTION]?.let {
                try { Resolution.valueOf(it) } catch (e: Exception) { Resolution.HD_1080P }
            } ?: Resolution.HD_1080P,
            bitrate = preferences[PreferencesKeys.BITRATE]?.let {
                try { Bitrate.valueOf(it) } catch (e: Exception) { Bitrate.HIGH }
            } ?: Bitrate.HIGH,
            frameRate = preferences[PreferencesKeys.FRAME_RATE] ?: 30,
            serverUrl = serverUrl,
            networkTag = networkTag,
            autoUpload = preferences[PreferencesKeys.AUTO_UPLOAD] ?: false,
            wifiOnlyUpload = preferences[PreferencesKeys.WIFI_ONLY_UPLOAD] ?: true
        )
    }

    suspend fun updateResolution(resolution: Resolution) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.RESOLUTION] = resolution.name
        }
    }

    suspend fun updateBitrate(bitrate: Bitrate) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.BITRATE] = bitrate.name
        }
    }

    suspend fun updateFrameRate(frameRate: Int) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.FRAME_RATE] = frameRate
        }
    }

    suspend fun updateServerUrl(url: String) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = url
            val profile = NetworkProfiles.getByUrl(url)
            preferences[PreferencesKeys.NETWORK_TAG] = (profile?.tag ?: NetworkTag.CUSTOM).name
        }
    }

    suspend fun updateNetworkProfile(profile: NetworkProfile) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.NETWORK_TAG] = profile.tag.name
            preferences[PreferencesKeys.SERVER_URL] = profile.serverUrl
        }
    }

    suspend fun updateAutoUpload(enabled: Boolean) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPLOAD] = enabled
        }
    }

    suspend fun updateWifiOnlyUpload(enabled: Boolean) {
        context.captureSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY_UPLOAD] = enabled
        }
    }

    /**
     * Get server URL as Flow for network module consumption.
     */
    fun getServerUrl(): Flow<String> = context.captureSettingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.SERVER_URL] ?: "http://192.168.100.97:9100"
    }
}
