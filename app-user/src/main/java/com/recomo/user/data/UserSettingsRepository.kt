package com.recomo.user.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.recomo.common.model.AppSettings
import com.recomo.common.model.NetworkPreset
import com.recomo.common.model.RobotProfile
import com.recomo.user.BuildConfig
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
        val customGatewayUrl = stringPreferencesKey("user_custom_gateway_url")
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
        AppSettings.fromPreset(
            preset = preset,
            robotProfile = profile
        )
    }

    val chatServerUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.chatServerUrl].orEmpty().ifBlank { BuildConfig.DEFAULT_CHAT_SERVER_URL }
    }

    val sessionFolderPath: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.sessionFolderPath].orEmpty().trim()
    }

    val customGatewayUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.customGatewayUrl].orEmpty().trim()
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

    suspend fun updateCustomGatewayUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[Keys.customGatewayUrl] = url.trim()
        }
    }
}
