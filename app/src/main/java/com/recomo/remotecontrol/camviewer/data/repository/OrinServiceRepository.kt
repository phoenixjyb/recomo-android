package com.recomo.remotecontrol.camviewer.data.repository

import android.util.Log
import com.recomo.remotecontrol.camviewer.data.model.OrinRobotIdentity
import com.recomo.remotecontrol.camviewer.data.model.OrinServiceStatus
import com.recomo.remotecontrol.camviewer.data.model.ServiceControlResponse
import com.recomo.remotecontrol.camviewer.data.model.VideoManagementStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for controlling Orin services remotely
 */
@Singleton
class OrinServiceRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "OrinServiceRepo"
        private const val SERVICE_CONTROL_PORT = 8083
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000  // 60 seconds for start/stop operations
            connectTimeoutMillis = 15000  // 15 seconds to connect
            socketTimeoutMillis = 60000   // 60 seconds socket timeout
        }
    }

    private val _servicesStatus = MutableStateFlow<Map<String, OrinServiceStatus>>(emptyMap())
    val servicesStatus: Flow<Map<String, OrinServiceStatus>> = _servicesStatus.asStateFlow()

    private val _robotIdentity = MutableStateFlow<OrinRobotIdentity?>(null)
    val robotIdentity: Flow<OrinRobotIdentity?> = _robotIdentity.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: Flow<String?> = _error.asStateFlow()

    /**
     * Get base URL for service control API
     */
    private suspend fun getServiceControlUrl(): String {
        val settings = settingsRepository.settings.first()
        val signalingHost = extractHost(settings.signalingUrl)
        val orinHost = extractHost(settings.orinTargetUrl)
        val host = signalingHost ?: orinHost
        return "http://${host ?: "127.0.0.1"}:$SERVICE_CONTROL_PORT"
    }

    private fun extractHost(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val normalized = if (
            trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("ws://") ||
            trimmed.startsWith("wss://")
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return try {
            val uri = java.net.URI(normalized)
            uri.host ?: normalized.substringAfter("://").substringBefore("/").substringBefore(":")
        } catch (e: Exception) {
            trimmed.substringBefore("/").substringBefore(":")
        }
    }

    private fun HttpRequestBuilder.applyServicePinHeader(
        settings: com.recomo.remotecontrol.camviewer.data.model.AppSettings,
        logEnabled: Boolean = false
    ) {
        val shouldSendPin = settings.serviceControlPinEnabled && settings.serviceControlPin.isNotBlank()
        if (!shouldSendPin) {
            if (logEnabled) {
                Log.d(TAG, "PIN header disabled")
            }
            return
        }
        header("X-Service-PIN", settings.serviceControlPin)
        if (logEnabled) {
            Log.d(TAG, "PIN header added")
        }
    }

    /**
     * Fetch current service status
     */
    suspend fun fetchStatus(): Result<Map<String, OrinServiceStatus>> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/status"
            
            Log.d(TAG, "Fetching service status from: $url")
            
            val response = client.get(url)
            
            if (response.status.isSuccess()) {
                val statusMap: Map<String, OrinServiceStatus> = response.body()
                _servicesStatus.value = statusMap
                Log.d(TAG, "Service status updated: ${statusMap.keys}")
                Result.success(statusMap)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
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

    suspend fun fetchIdentity(): Result<OrinRobotIdentity> {
        return try {
            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/system/identity"

            Log.d(TAG, "Fetching robot identity from: $url")

            val response = client.get(url)
            if (response.status.isSuccess()) {
                val identity: OrinRobotIdentity = response.body()
                _robotIdentity.value = identity
                Result.success(identity)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to fetch identity: ${e.message}"
            _error.value = errorMsg
            Result.failure(e)
        }
    }

    /**
     * Start all Orin services
     */
    suspend fun startServices(): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/start"
            val settings = settingsRepository.settings.first()
            
            Log.d(TAG, "Starting services at: $url")
            Log.d(TAG, "PIN enabled: ${settings.serviceControlPinEnabled && settings.serviceControlPin.isNotBlank()}")
            
            val response = client.post(url) {
                applyServicePinHeader(settings, logEnabled = true)
            }
            
            Log.d(TAG, "Response status: ${response.status.value} ${response.status.description}")
            
            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Log.d(TAG, "Services started: ${controlResponse.message}")
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start services: ${e.message}"
            _error.value = errorMsg
            Log.e(TAG, errorMsg, e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Cause: ${e.cause?.message}")
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Stop all Orin services
     */
    suspend fun stopServices(): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/stop"
            val settings = settingsRepository.settings.first()
            
            Log.d(TAG, "Stopping services at: $url")
            Log.d(TAG, "PIN enabled: ${settings.serviceControlPinEnabled && settings.serviceControlPin.isNotBlank()}")
            
            val response = client.post(url) {
                applyServicePinHeader(settings, logEnabled = true)
            }
            
            Log.d(TAG, "Response status: ${response.status.value} ${response.status.description}")
            
            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Log.d(TAG, "Services stopped: ${controlResponse.message}")
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to stop services: ${e.message}"
            _error.value = errorMsg
            Log.e(TAG, errorMsg, e)
            Log.e(TAG, "Exception type: ${e.javaClass.name}")
            Log.e(TAG, "Cause: ${e.cause?.message}")
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Start a single Orin service
     */
    suspend fun startService(serviceId: String): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/$serviceId/start"
            val settings = settingsRepository.settings.first()

            Log.d(TAG, "Starting service $serviceId at: $url")

            val response = client.post(url) {
                applyServicePinHeader(settings)
            }

            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start service: ${e.message}"
            _error.value = errorMsg
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Stop a single Orin service
     */
    suspend fun stopService(serviceId: String): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/services/$serviceId/stop"
            val settings = settingsRepository.settings.first()

            Log.d(TAG, "Stopping service $serviceId at: $url")

            val response = client.post(url) {
                applyServicePinHeader(settings)
            }

            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to stop service: ${e.message}"
            _error.value = errorMsg
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

    /**
     * Start all recording topic services (odom, arm, gimbal)
     */
    suspend fun startRecordingTopics(): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/recording/start"
            val settings = settingsRepository.settings.first()

            Log.d(TAG, "Starting recording topics at: $url")

            val response = client.post(url) {
                applyServicePinHeader(settings)
            }

            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Log.d(TAG, "Recording topics started: ${controlResponse.message}")
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to start recording topics: ${e.message}"
            _error.value = errorMsg
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Stop all recording topic services (odom, arm, gimbal)
     */
    suspend fun stopRecordingTopics(): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/recording/stop"
            val settings = settingsRepository.settings.first()

            Log.d(TAG, "Stopping recording topics at: $url")

            val response = client.post(url) {
                applyServicePinHeader(settings)
            }

            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Log.d(TAG, "Recording topics stopped: ${controlResponse.message}")
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to stop recording topics: ${e.message}"
            _error.value = errorMsg
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Switch odom source (real robot_node vs fake cmd_vel)
     */
    suspend fun setOdomMode(mode: String): Result<ServiceControlResponse> {
        return try {
            _isLoading.value = true
            _error.value = null

            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/odom/mode"
            val settings = settingsRepository.settings.first()

            Log.d(TAG, "Setting odom mode to $mode at: $url")

            val response = client.post(url) {
                applyServicePinHeader(settings)
                contentType(ContentType.Application.Json)
                setBody(mapOf("mode" to mode))
            }

            if (response.status.isSuccess()) {
                val controlResponse: ServiceControlResponse = response.body()
                _servicesStatus.value = controlResponse.services
                Result.success(controlResponse)
            } else {
                val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                _error.value = errorMsg
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = "Failed to set odom mode: ${e.message}"
            _error.value = errorMsg
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun fetchVideoStatus(): Result<VideoManagementStatus> {
        return try {
            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/video/status"
            val response = client.get(url)
            if (response.status.isSuccess()) {
                val status: VideoManagementStatus = response.body()
                Result.success(status)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startVideoRecording(sessionName: String? = null): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val settings = settingsRepository.settings.first()
            val url = "$baseUrl/api/video/record/start"
            val response = client.post(url) {
                applyServicePinHeader(settings)
                contentType(ContentType.Application.Json)
                if (!sessionName.isNullOrBlank()) {
                    setBody(mapOf("session_name" to sessionName))
                } else {
                    setBody(emptyMap<String, String>())
                }
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopVideoRecording(): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val settings = settingsRepository.settings.first()
            val url = "$baseUrl/api/video/record/stop"
            val response = client.post(url) {
                applyServicePinHeader(settings)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retryFailedVideoUploads(): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val settings = settingsRepository.settings.first()
            val url = "$baseUrl/api/video/upload/retry_failed"
            val response = client.post(url) {
                applyServicePinHeader(settings)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerVideoUploadNow(): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val settings = settingsRepository.settings.first()
            val url = "$baseUrl/api/video/upload/trigger"
            val response = client.post(url) {
                applyServicePinHeader(settings)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requeueForCurrentDestination(): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val settings = settingsRepository.settings.first()
            val url = "$baseUrl/api/video/upload/requeue_for_current"
            val response = client.post(url) {
                applyServicePinHeader(settings)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setUploadPreset(preset: String): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val settings = settingsRepository.settings.first()
            val url = "$baseUrl/api/video/config/upload_preset"
            val response = client.post(url) {
                applyServicePinHeader(settings)
                contentType(ContentType.Application.Json)
                setBody(mapOf("preset" to preset))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch Orin system resource usage (CPU, GPU, memory, temperatures).
     * Endpoint: GET /api/system/resources
     * Returns null-body JsonObject on failure so callers handle gracefully.
     */
    suspend fun fetchSystemResources(): Result<JsonObject> {
        return try {
            val baseUrl = getServiceControlUrl()
            val url = "$baseUrl/api/system/resources"
            val response = client.get(url)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
