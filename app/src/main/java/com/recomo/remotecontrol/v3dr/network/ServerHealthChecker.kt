package com.recomo.remotecontrol.v3dr.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to check server health and connectivity
 */
@Singleton
class ServerHealthChecker @Inject constructor(
    private val apiService: V3DRApiService
) {
    /**
     * Check if the server is reachable and responding
     */
    suspend fun checkHealth(): ServerHealthStatus = withContext(Dispatchers.IO) {
        try {
            Timber.d("Checking server health...")
            val response = apiService.healthCheck()
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Timber.i("Server is healthy: ${body.status}, version: ${body.version}")
                    ServerHealthStatus.Healthy(
                        status = body.status,
                        version = body.version,
                        timestamp = body.timestamp
                    )
                } else {
                    Timber.w("Health check returned null body")
                    ServerHealthStatus.Unhealthy("No response data")
                }
            } else {
                val error = "Health check failed: ${response.code()} ${response.message()}"
                Timber.e(error)
                ServerHealthStatus.Unhealthy(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Health check exception")
            ServerHealthStatus.Unreachable(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Test connection to scenes endpoint
     */
    suspend fun testScenesEndpoint(): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("Testing /api/v1/scenes endpoint...")
            val response = apiService.listScenes()
            val success = response.isSuccessful
            
            if (success) {
                Timber.i("Scenes endpoint is accessible")
            } else {
                Timber.e("Scenes endpoint returned ${response.code()}: ${response.message()}")
                Timber.e("Response body: ${response.errorBody()?.string()}")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "Failed to test scenes endpoint")
            false
        }
    }
}

sealed class ServerHealthStatus {
    data class Healthy(
        val status: String,
        val version: String?,
        val timestamp: Long
    ) : ServerHealthStatus()
    
    data class Unhealthy(val reason: String) : ServerHealthStatus()
    data class Unreachable(val error: String) : ServerHealthStatus()
}
