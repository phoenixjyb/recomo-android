package com.recomo.common.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.recomo.common.settings.CaptureSettingsRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for authentication with V3DR Lake server
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureSettingsRepository: CaptureSettingsRepository
) {
    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "v3dr_auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_JSON = "user_json"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_JSON = "device_json"
    }

    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Use encrypted shared preferences for secure storage
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _currentDevice = MutableStateFlow<Device?>(null)
    val currentDevice: StateFlow<Device?> = _currentDevice.asStateFlow()

    init {
        // Load saved auth state
        loadSavedAuth()
    }

    private fun loadSavedAuth() {
        val token = securePrefs.getString(KEY_AUTH_TOKEN, null)
        val userJson = securePrefs.getString(KEY_USER_JSON, null)
        val deviceJson = securePrefs.getString(KEY_DEVICE_JSON, null)

        if (token != null && userJson != null) {
            try {
                _currentUser.value = gson.fromJson(userJson, User::class.java)
                if (deviceJson != null) {
                    _currentDevice.value = gson.fromJson(deviceJson, Device::class.java)
                }
                _authState.value = AuthState.Authenticated(token)
                Log.d(TAG, "Restored auth state for user: ${_currentUser.value?.email}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore auth state", e)
                clearAuth()
            }
        } else {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    fun getAuthToken(): String? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.token
            else -> null
        }
    }

    fun getDeviceId(): String? {
        return _currentDevice.value?.deviceId 
            ?: securePrefs.getString(KEY_DEVICE_ID, null)
    }

    private suspend fun getBaseUrl(): String {
        return captureSettingsRepository.getServerUrl().first().ifEmpty {
            "http://192.168.100.97:9100"
        }.trimEnd('/')
    }

    /**
     * Register a new user account
     */
    suspend fun register(email: String, username: String, password: String): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = getBaseUrl()
                val request = RegisterRequest(email, username, password, username)
                val json = gson.toJson(request)

                val httpRequest = Request.Builder()
                    .url("$baseUrl/auth/register")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(httpRequest).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val authResponse = gson.fromJson(body, AuthResponse::class.java)
                    saveAuth(authResponse.token, authResponse.user)
                    Result.success(authResponse)
                } else {
                    val error = parseError(body)
                    Log.e(TAG, "Register failed: $error")
                    Result.failure(AuthException(error))
                }
            } catch (e: IOException) {
                Log.e(TAG, "Register network error", e)
                Result.failure(AuthException("Network error: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "Register error", e)
                Result.failure(e)
            }
        }

    /**
     * Login with email and password
     */
    suspend fun login(email: String, password: String): Result<AuthResponse> = 
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = getBaseUrl()
                val request = LoginRequest(email, password)
                val json = gson.toJson(request)

                val httpRequest = Request.Builder()
                    .url("$baseUrl/auth/login")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(httpRequest).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val authResponse = gson.fromJson(body, AuthResponse::class.java)
                    saveAuth(authResponse.token, authResponse.user)
                    Result.success(authResponse)
                } else {
                    val error = parseError(body)
                    Log.e(TAG, "Login failed: $error")
                    Result.failure(AuthException(error))
                }
            } catch (e: IOException) {
                Log.e(TAG, "Login network error", e)
                Result.failure(AuthException("Network error: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                Result.failure(e)
            }
        }

    /**
     * Register the current device after login
     */
    suspend fun registerDevice(): Result<DeviceRegisterResponse> = withContext(Dispatchers.IO) {
        val token = getAuthToken() ?: return@withContext Result.failure(
            AuthException("Not authenticated")
        )

        try {
            val baseUrl = getBaseUrl()
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val deviceSerial = getDeviceSerial()
            
            val request = DeviceRegisterRequest(
                deviceName = deviceName,
                deviceSerial = deviceSerial,
                deviceModel = Build.MODEL,
                deviceManufacturer = Build.MANUFACTURER,
                androidVersion = Build.VERSION.RELEASE,
                appVersion = getAppVersion()
            )
            val json = gson.toJson(request)

            val httpRequest = Request.Builder()
                .url("$baseUrl/auth/device/register")
                .addHeader("Authorization", "Bearer $token")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val deviceResponse = gson.fromJson(body, DeviceRegisterResponse::class.java)
                saveDevice(deviceResponse.device, deviceResponse.token)
                Result.success(deviceResponse)
            } else {
                val error = parseError(body)
                Log.e(TAG, "Device registration failed: $error")
                Result.failure(AuthException(error))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Device registration network error", e)
            Result.failure(AuthException("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Device registration error", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh the auth token
     */
    suspend fun refreshToken(): Result<String> = withContext(Dispatchers.IO) {
        val token = getAuthToken() ?: return@withContext Result.failure(
            AuthException("Not authenticated")
        )

        try {
            val baseUrl = getBaseUrl()

            val httpRequest = Request.Builder()
                .url("$baseUrl/auth/refresh")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val refreshResponse = gson.fromJson(body, TokenRefreshResponse::class.java)
                // Update saved token
                securePrefs.edit().putString(KEY_AUTH_TOKEN, refreshResponse.token).apply()
                _authState.value = AuthState.Authenticated(refreshResponse.token)
                Result.success(refreshResponse.token)
            } else {
                val error = parseError(body)
                Log.e(TAG, "Token refresh failed: $error")
                // If refresh fails, clear auth
                if (response.code == 401) {
                    clearAuth()
                }
                Result.failure(AuthException(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            Result.failure(e)
        }
    }

    /**
     * Logout and clear auth state
     */
    fun logout() {
        clearAuth()
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }

    private fun saveAuth(token: String, user: User) {
        securePrefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
        
        _currentUser.value = user
        _authState.value = AuthState.Authenticated(token)
        Log.d(TAG, "Saved auth for user: ${user.email}")
    }

    private fun saveDevice(device: Device, newToken: String) {
        securePrefs.edit()
            .putString(KEY_DEVICE_ID, device.deviceId)
            .putString(KEY_DEVICE_JSON, gson.toJson(device))
            .putString(KEY_AUTH_TOKEN, newToken)
            .apply()
        
        _currentDevice.value = device
        _authState.value = AuthState.Authenticated(newToken)
        Log.d(TAG, "Saved device: ${device.deviceName} (${device.deviceId})")
    }

    private fun clearAuth() {
        securePrefs.edit().clear().apply()
        _currentUser.value = null
        _currentDevice.value = null
        _authState.value = AuthState.NotAuthenticated
        Log.d(TAG, "Cleared auth state")
    }

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            val error = gson.fromJson(body, ErrorResponse::class.java)
            error.message ?: error.error ?: "Unknown error"
        } catch (e: Exception) {
            body
        }
    }

    private fun getDeviceSerial(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}

sealed class AuthState {
    object Unknown : AuthState()
    object NotAuthenticated : AuthState()
    data class Authenticated(val token: String) : AuthState()
}

class AuthException(message: String) : Exception(message)

private data class ErrorResponse(
    val error: String?,
    val message: String?
)
