package com.recomo.user.phoneteach.cloud

import android.util.Log
import com.recomo.common.auth.AuthRepository
import com.recomo.common.settings.CaptureSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight OkHttp client for the phone-moco cloud backend.
 *
 * Endpoints consumed:
 *   GET /api/v1/moco/sessions/{name}/status     → [SessionCloudStatus]
 *   GET /api/v1/moco/sessions/{name}/trajectory  → TUM file download
 *
 * Uses [AuthRepository] for Bearer token and [CaptureSettingsRepository] for base URL.
 */
@Singleton
class PhoneTeachCloudClient @Inject constructor(
    private val authRepository: AuthRepository,
    private val captureSettingsRepository: CaptureSettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "PhoneTeachCloud"
    }

    private suspend fun baseUrl(): String =
        captureSettingsRepository.getServerUrl().first().trimEnd('/')

    private fun bearerToken(): String? = authRepository.getAuthToken()

    /**
     * Poll the cloud processing status for a given session.
     */
    suspend fun getSessionStatus(sessionName: String): SessionCloudStatus = withContext(Dispatchers.IO) {
        val token = bearerToken()
            ?: return@withContext SessionCloudStatus.Error("Not authenticated")

        try {
            val url = "${baseUrl()}/api/v1/moco/sessions/$sessionName/status"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                Log.w(TAG, "Status check failed: ${response.code} ${response.message}")
                return@withContext SessionCloudStatus.Error("HTTP ${response.code}")
            }

            val json = JSONObject(body)
            val status = json.optString("status", "unknown")
            val hasTrajectory = json.optBoolean("has_trajectory", false)
            val error = json.optString("error", null)
            val completedAt = json.optString("completed_at", null)

            SessionCloudStatus.Ok(
                status = status,
                hasTrajectory = hasTrajectory,
                completedAt = completedAt,
                error = error
            )
        } catch (e: Exception) {
            Log.e(TAG, "Status check exception for $sessionName", e)
            SessionCloudStatus.Error(e.message ?: "Network error")
        }
    }

    /**
     * Download the VIO trajectory TUM file from the cloud and save to [destFile].
     * Returns true on success, false on failure.
     */
    suspend fun downloadTrajectory(sessionName: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val token = bearerToken() ?: return@withContext false

        try {
            val url = "${baseUrl()}/api/v1/moco/sessions/$sessionName/trajectory"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Trajectory download failed: ${response.code} ${response.message}")
                return@withContext false
            }

            val bytes = response.body?.bytes() ?: return@withContext false
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(bytes)
            Log.i(TAG, "Trajectory downloaded: ${destFile.absolutePath} (${bytes.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Trajectory download exception for $sessionName", e)
            false
        }
    }
}

sealed class SessionCloudStatus {
    data class Ok(
        val status: String,        // "uploaded", "processing", "completed", "failed"
        val hasTrajectory: Boolean,
        val completedAt: String?,
        val error: String?
    ) : SessionCloudStatus()

    data class Error(val message: String) : SessionCloudStatus()
}
