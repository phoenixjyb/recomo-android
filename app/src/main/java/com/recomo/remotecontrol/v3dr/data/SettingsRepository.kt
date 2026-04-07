package com.recomo.remotecontrol.v3dr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val resolution: Resolution = Resolution.HD_1080P,
    val bitrate: Bitrate = Bitrate.HIGH,
    val frameRate: Int = 30,
    val serverUrl: String = "http://192.168.100.100:8771",
    val networkTag: NetworkTag = NetworkTag.ZEROTIER,
    val autoUpload: Boolean = false,
    val wifiOnlyUpload: Boolean = true,
    val vioBackend: VioBackendType = VioBackendType.CLOUD_SFM,
    val cloudSfmUrl: String = "http://192.168.100.100:7000"
)

enum class VioBackendType(val displayName: String, val description: String) {
    CLOUD_SFM("Cloud SFM", "Upload to server for trajectory recovery (recommended)"),
    OPENVINS("OpenVINS", "On-device VIO (experimental, build disabled)"),
    ARCORE("ARCore", "Real-time pose recording (uses ARCore camera, not Camera2)")
}

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
class SettingsRepository @Inject constructor(
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
        val VIO_BACKEND = stringPreferencesKey("vio_backend")
        val CLOUD_SFM_URL = stringPreferencesKey("cloud_sfm_url")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val networkTag = preferences[PreferencesKeys.NETWORK_TAG]?.let { 
            try { NetworkTag.valueOf(it) } catch (e: Exception) { NetworkTag.ZEROTIER }
        } ?: NetworkTag.ZEROTIER
        
        // Get URL from profile or use saved custom URL
        val savedUrl = preferences[PreferencesKeys.SERVER_URL]
        val serverUrl = if (savedUrl != null && savedUrl.isNotEmpty()) {
            savedUrl
        } else {
            NetworkProfiles.getByTag(networkTag)?.serverUrl ?: "http://192.168.100.100:8771"
        }
        
        val vioBackend = preferences[PreferencesKeys.VIO_BACKEND]?.let {
            try { VioBackendType.valueOf(it) } catch (e: Exception) { VioBackendType.CLOUD_SFM }
        } ?: VioBackendType.CLOUD_SFM
        
        AppSettings(
            resolution = preferences[PreferencesKeys.RESOLUTION]?.let { 
                Resolution.valueOf(it) 
            } ?: Resolution.HD_1080P,
            bitrate = preferences[PreferencesKeys.BITRATE]?.let { 
                Bitrate.valueOf(it) 
            } ?: Bitrate.HIGH,
            frameRate = preferences[PreferencesKeys.FRAME_RATE] ?: 30,
            serverUrl = serverUrl,
            networkTag = networkTag,
            autoUpload = preferences[PreferencesKeys.AUTO_UPLOAD] ?: false,
            wifiOnlyUpload = preferences[PreferencesKeys.WIFI_ONLY_UPLOAD] ?: true,
            vioBackend = vioBackend,
            cloudSfmUrl = preferences[PreferencesKeys.CLOUD_SFM_URL] ?: "http://192.168.100.100:7000"
        )
    }

    suspend fun updateResolution(resolution: Resolution) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESOLUTION] = resolution.name
        }
    }

    suspend fun updateBitrate(bitrate: Bitrate) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BITRATE] = bitrate.name
        }
    }

    suspend fun updateFrameRate(frameRate: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FRAME_RATE] = frameRate
        }
    }

    suspend fun updateServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_URL] = url
            // If URL matches a profile, update the tag
            val profile = NetworkProfiles.getByUrl(url)
            if (profile != null) {
                preferences[PreferencesKeys.NETWORK_TAG] = profile.tag.name
            } else {
                preferences[PreferencesKeys.NETWORK_TAG] = NetworkTag.CUSTOM.name
            }
        }
    }
    
    suspend fun updateNetworkProfile(profile: NetworkProfile) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NETWORK_TAG] = profile.tag.name
            preferences[PreferencesKeys.SERVER_URL] = profile.serverUrl
        }
    }

    suspend fun updateAutoUpload(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPLOAD] = enabled
        }
    }

    suspend fun updateWifiOnlyUpload(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY_UPLOAD] = enabled
        }
    }

    suspend fun updateVioBackend(backend: VioBackendType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIO_BACKEND] = backend.name
        }
    }

    suspend fun updateCloudSfmUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOUD_SFM_URL] = url
        }
    }

    /**
     * Get server URL as Flow for network module
     */
    fun getServerUrl(): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SERVER_URL] ?: "http://192.168.100.100:8771"
    }

    /**
     * Get Cloud SFM URL as Flow
     */
    fun getCloudSfmUrl(): Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLOUD_SFM_URL] ?: "http://192.168.100.100:7000"
    }
}
