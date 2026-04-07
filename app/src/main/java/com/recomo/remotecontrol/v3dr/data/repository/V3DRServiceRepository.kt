package com.recomo.remotecontrol.v3dr.data.repository

import android.util.Log
import com.recomo.remotecontrol.v3dr.data.NetworkProfiles
import com.recomo.remotecontrol.v3dr.data.SettingsRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service status from V3DR Lake server control API
 */
data class V3DRServiceStatus(
    val name: String,
    val running: Boolean,
    val pid: Int? = null,
    @SerializedName("uptime_seconds") val uptimeSeconds: Float? = null,
    val port: Int? = null,
    @SerializedName("last_log_lines") val lastLogLines: List<String> = emptyList()
)

/**
 * Response from service control endpoints
 */
data class V3DRServiceControlResponse(
    val success: Boolean,
    val message: String,
    val services: Map<String, V3DRServiceStatus>
)

/**
 * Repository for controlling V3DR Lake server remotely on 4090 machine
 */
@Singleton
class V3DRServiceRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "V3DRServiceRepo"
        private const val SERVICE_CONTROL_PORT = 8085  // 4090 service control port (separate from Orin's 8083)
    }

    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _servicesStatus = MutableStateFlow<Map<String, V3DRServiceStatus>>(emptyMap())
    val servicesStatus: Flow<Map<String, V3DRServiceStatus>> = _servicesStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: Flow<String?> = _error.asStateFlow()

    /**
     * Get service control URL based on current server URL setting
     */
    private suspend fun getServiceControlUrl(): String {
        val appSettings = settingsRepository.settingsFlow.first()
        return NetworkProfiles.getServiceControlUrl(appSettings.serverUrl)
    }

    /**
     * Check if service control API is reachable
     */
    suspend fun isServiceControlAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServiceControlUrl()
            val request = Request.Builder()
                .url("$baseUrl/")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.d(TAG, "Service control not available: ${e.message}")
            false
        }
    }

    /**
     * Fetch current service status
     */
    suspend fun fetchStatus(): Result<Map<String, V3DRServiceStatus>> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/status"
            
            Log.d(TAG, "Fetching service status from: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful && body != null) {
                val type = object : TypeToken<Map<String, V3DRServiceStatus>>() {}.type
                val statusMap: Map<String, V3DRServiceStatus> = gson.fromJson(body, type)
                _servicesStatus.value = statusMap
                Log.d(TAG, "Service status updated: ${statusMap.keys}")
                Result.success(statusMap)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch status: ${e.message}"
            _error.value = errorMsg
            Log.e(TAG, errorMsg, e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Start V3DR Lake server
     */
    suspend fun startServer(pin: String = ""): Result<V3DRServiceControlResponse> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/start"
            
            Log.d(TAG, "Starting V3DR Lake server at: $url")
            
            val requestBuilder = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
            
            if (pin.isNotEmpty()) {
                requestBuilder.addHeader("X-Service-PIN", pin)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            
            Log.d(TAG, "Response status: ${response.code} ${response.message}")
            
            if (response.isSuccessful && body != null) {
                val controlResponse = gson.fromJson(body, V3DRServiceControlResponse::class.java)
                _servicesStatus.value = controlResponse.services
                Log.d(TAG, "Server started: ${controlResponse.message}")
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start server: ${e.message}"
            _error.value = errorMsg
            Log.e(TAG, errorMsg, e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Stop V3DR Lake server
     */
    suspend fun stopServer(pin: String = ""): Result<V3DRServiceControlResponse> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/stop"
            
            Log.d(TAG, "Stopping V3DR Lake server at: $url")
            
            val requestBuilder = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
            
            if (pin.isNotEmpty()) {
                requestBuilder.addHeader("X-Service-PIN", pin)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            
            Log.d(TAG, "Response status: ${response.code} ${response.message}")
            
            if (response.isSuccessful && body != null) {
                val controlResponse = gson.fromJson(body, V3DRServiceControlResponse::class.java)
                _servicesStatus.value = controlResponse.services
                Log.d(TAG, "Server stopped: ${controlResponse.message}")
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.code}: ${response.message}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to stop server: ${e.message}"
            _error.value = errorMsg
            Log.e(TAG, errorMsg, e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
}
