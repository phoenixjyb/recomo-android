package com.recomo.remotecontrol.v3dr.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.recomo.common.settings.Bitrate
import com.recomo.common.settings.CaptureSettingsRepository
import com.recomo.common.settings.NetworkProfile
import com.recomo.common.settings.NetworkTag
import com.recomo.common.settings.Resolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy v3dr settings facade.
 *
 * Delegates capture-side fields (resolution, bitrate, frameRate, serverUrl, networkTag,
 * autoUpload, wifiOnlyUpload) to com.recomo.common.settings.CaptureSettingsRepository, which
 * lives in :common and is shared with :app-user's Phone Teach feature. VIO-specific fields
 * (vioBackend, cloudSfmUrl) are engineering-app-only and owned directly here.
 *
 * This facade exists to minimize churn on existing v3dr consumers that expect a single
 * SettingsRepository with both capture and VIO knobs. New code in :app-user should inject
 * CaptureSettingsRepository directly; new VIO-specific code can inject this facade.
 *
 * See memory/project_app_user_phone_moco_migration.md for the :common-first migration plan.
 */
private val Context.vioSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureSettingsRepository: CaptureSettingsRepository
) {
    private object PreferencesKeys {
        val VIO_BACKEND = stringPreferencesKey("vio_backend")
        val CLOUD_SFM_URL = stringPreferencesKey("cloud_sfm_url")
    }

    /**
     * Unified settings flow for legacy v3dr UI consumers that expect AppSettings.
     * Combines capture-side state from :common with VIO-side state from :app.
     */
    val settingsFlow: Flow<AppSettings> = combine(
        captureSettingsRepository.settingsFlow,
        context.vioSettingsDataStore.data
    ) { capture, preferences ->
        val vioBackend = preferences[PreferencesKeys.VIO_BACKEND]?.let {
            try { VioBackendType.valueOf(it) } catch (e: Exception) { VioBackendType.CLOUD_SFM }
        } ?: VioBackendType.CLOUD_SFM

        AppSettings(
            resolution = capture.resolution,
            bitrate = capture.bitrate,
            frameRate = capture.frameRate,
            serverUrl = capture.serverUrl,
            networkTag = capture.networkTag,
            autoUpload = capture.autoUpload,
            wifiOnlyUpload = capture.wifiOnlyUpload,
            vioBackend = vioBackend,
            cloudSfmUrl = preferences[PreferencesKeys.CLOUD_SFM_URL] ?: "http://192.168.100.100:7000"
        )
    }

    // --- Delegated capture updaters (forward to :common) ---
    suspend fun updateResolution(resolution: Resolution) = captureSettingsRepository.updateResolution(resolution)
    suspend fun updateBitrate(bitrate: Bitrate) = captureSettingsRepository.updateBitrate(bitrate)
    suspend fun updateFrameRate(frameRate: Int) = captureSettingsRepository.updateFrameRate(frameRate)
    suspend fun updateServerUrl(url: String) = captureSettingsRepository.updateServerUrl(url)
    suspend fun updateNetworkProfile(profile: NetworkProfile) = captureSettingsRepository.updateNetworkProfile(profile)
    suspend fun updateAutoUpload(enabled: Boolean) = captureSettingsRepository.updateAutoUpload(enabled)
    suspend fun updateWifiOnlyUpload(enabled: Boolean) = captureSettingsRepository.updateWifiOnlyUpload(enabled)
    fun getServerUrl(): Flow<String> = captureSettingsRepository.getServerUrl()

    // --- Own VIO updaters (stay in :app) ---
    suspend fun updateVioBackend(backend: VioBackendType) {
        context.vioSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.VIO_BACKEND] = backend.name
        }
    }

    suspend fun updateCloudSfmUrl(url: String) {
        context.vioSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.CLOUD_SFM_URL] = url
        }
    }

    fun getCloudSfmUrl(): Flow<String> = context.vioSettingsDataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLOUD_SFM_URL] ?: "http://192.168.100.100:7000"
    }
}
