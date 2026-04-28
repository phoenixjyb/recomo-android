package com.recomo.user.data.system

import com.recomo.user.data.UserSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Singleton
class UserOrinServiceRepository @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    json: Json
) {
    companion object {
        private const val SERVICE_CONTROL_PORT = 8083
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        engine {
            proxy = null
        }
    }

    suspend fun fetchStatus(): Result<Map<String, UserOrinServiceStatus>> = runCatching {
        val response = client.get("${serviceBaseUrl()}/api/services/status")
        check(response.status.isSuccess()) {
            "HTTP ${response.status.value}: ${response.status.description}"
        }
        response.body()
    }

    suspend fun fetchIdentity(): Result<UserOrinRobotIdentity> = runCatching {
        val response = client.get("${serviceBaseUrl()}/api/system/identity")
        check(response.status.isSuccess()) {
            "HTTP ${response.status.value}: ${response.status.description}"
        }
        response.body()
    }

    suspend fun startService(serviceId: String): Result<UserServiceControlResponse> = runCatching {
        val response = client.post("${serviceBaseUrl()}/api/services/$serviceId/start")
        check(response.status.isSuccess()) {
            "HTTP ${response.status.value}: ${response.status.description}"
        }
        response.body()
    }

    suspend fun stopService(serviceId: String): Result<UserServiceControlResponse> = runCatching {
        val response = client.post("${serviceBaseUrl()}/api/services/$serviceId/stop")
        check(response.status.isSuccess()) {
            "HTTP ${response.status.value}: ${response.status.description}"
        }
        response.body()
    }

    suspend fun fetchSystemResources(): Result<JsonObject> = runCatching {
        val response = client.get("${serviceBaseUrl()}/api/system/resources")
        check(response.status.isSuccess()) {
            "HTTP ${response.status.value}: ${response.status.description}"
        }
        response.body()
    }

    private suspend fun serviceBaseUrl(): String {
        val signalingUrl = userSettingsRepository.appSettings.first().signalingUrl
        val host = extractHost(signalingUrl) ?: "127.0.0.1"
        return "http://$host:$SERVICE_CONTROL_PORT"
    }

    private fun extractHost(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val normalized = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
            else -> "http://$trimmed"
        }
        return runCatching {
            java.net.URI(normalized).host
                ?: normalized.substringAfter("://").substringBefore("/").substringBefore(":")
        }.getOrElse {
            trimmed.substringBefore("/").substringBefore(":")
        }
    }
}
