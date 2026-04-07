package com.recomo.remotecontrol.v3dr.upload

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Background worker for uploading recordings to cloud storage
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        const val KEY_VIDEO_PATH = "video_path"
        const val KEY_IMU_PATH = "imu_path"
        const val KEY_METADATA_PATH = "metadata_path"
        const val KEY_FRAME_TIMESTAMPS_PATH = "frame_timestamps_path"
        const val KEY_CALIB_PATH = "calib_path"
        const val KEY_MANIFEST_PATH = "manifest_path"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_UPLOAD_URL = "upload_url"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_DEVICE_ID = "device_id"
        const val REMOTE_BASE_PATH = "/home/converge/data/v3drLake"
        private const val AUTH_PREFS_NAME = "v3dr_auth_prefs"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Access encrypted prefs to get auth token
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            applicationContext,
            AUTH_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getAuthToken(): String? {
        // First check input data, then fall back to secure prefs
        return inputData.getString(KEY_AUTH_TOKEN) 
            ?: securePrefs.getString("auth_token", null)
    }

    private fun getDeviceId(): String? {
        return inputData.getString(KEY_DEVICE_ID)
            ?: securePrefs.getString("device_id", null)
            ?: getAndroidId()
    }

    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "UploadWorker.doWork() started")
        try {
            val videoPath = inputData.getString(KEY_VIDEO_PATH) ?: return@withContext Result.failure()
            val imuPath = inputData.getString(KEY_IMU_PATH)
            val metadataPath = inputData.getString(KEY_METADATA_PATH)
            val frameTimestampsPath = inputData.getString(KEY_FRAME_TIMESTAMPS_PATH)
            val calibPath = inputData.getString(KEY_CALIB_PATH)
            val manifestPath = inputData.getString(KEY_MANIFEST_PATH)
            val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()
            val uploadUrl = inputData.getString(KEY_UPLOAD_URL) ?: return@withContext Result.failure()

            Log.d(TAG, "Starting upload for session: $sessionId")
            Log.d(TAG, "Upload URL: $uploadUrl")
            
            // Get auth token and device ID
            val authToken = getAuthToken()
            val deviceId = getDeviceId()
            
            if (authToken == null) {
                Log.e(TAG, "No auth token available - user not logged in")
                return@withContext Result.failure(
                    Data.Builder()
                        .putString("error", "Not authenticated. Please login first.")
                        .build()
                )
            }
            
            Log.d(TAG, "Using device ID: $deviceId")
            
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e(TAG, "Video file not found: $videoPath")
                return@withContext Result.failure()
            }

            // Build multipart request with server-expected field names
            // Server expects: session_name, device_id, video (required)
            val requestBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_name", sessionId)  // Server expects session_name
                .addFormDataPart("device_id", deviceId ?: "unknown")
                .addFormDataPart(
                    "video",
                    videoFile.name,
                    videoFile.asRequestBody("video/mp4".toMediaType())
                )

            // Add IMU data if exists
            if (imuPath != null) {
                val imuFile = File(imuPath)
                if (imuFile.exists()) {
                    requestBuilder.addFormDataPart(
                        "imu",
                        imuFile.name,
                        imuFile.asRequestBody("text/csv".toMediaType())
                    )
                }
            }

            // Add metadata if exists
            if (metadataPath != null) {
                val metadataFile = File(metadataPath)
                if (metadataFile.exists()) {
                    requestBuilder.addFormDataPart(
                        "metadata",
                        metadataFile.name,
                        metadataFile.asRequestBody("application/json".toMediaType())
                    )
                }
            }

            // Add frame timestamps if exists
            if (frameTimestampsPath != null) {
                val frameFile = File(frameTimestampsPath)
                if (frameFile.exists()) {
                    requestBuilder.addFormDataPart(
                        "frame_timestamps",
                        frameFile.name,
                        frameFile.asRequestBody("text/csv".toMediaType())
                    )
                }
            }

            // Add calibration if exists
            if (calibPath != null) {
                val calibFile = File(calibPath)
                if (calibFile.exists()) {
                    requestBuilder.addFormDataPart(
                        "calib",
                        calibFile.name,
                        calibFile.asRequestBody("application/json".toMediaType())
                    )
                }
            }

            // Add manifest if exists
            if (manifestPath != null) {
                val manifestFile = File(manifestPath)
                if (manifestFile.exists()) {
                    requestBuilder.addFormDataPart(
                        "manifest",
                        manifestFile.name,
                        manifestFile.asRequestBody("application/json".toMediaType())
                    )
                }
            }

            val requestBody = requestBuilder.build()
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $authToken")
                .post(requestBody)
                .build()

            // Execute upload
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Upload successful for session: $sessionId")
                Log.d(TAG, "Server response: $responseBody")
                
                val outputData = Data.Builder()
                    .putString("session_id", sessionId)
                    .putBoolean("success", true)
                    .build()
                
                Result.success(outputData)
            } else {
                val responseBody = response.body?.string()
                Log.e(TAG, "Upload failed with code: ${response.code}")
                Log.e(TAG, "Server error response: $responseBody")
                
                val outputData = Data.Builder()
                    .putString("session_id", sessionId)
                    .putString("error", "HTTP ${response.code}: ${response.message}")
                    .build()
                
                Result.retry()
            }

        } catch (e: IOException) {
            Log.e(TAG, "Upload failed with IO exception", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed with exception", e)
            Result.failure()
        }
    }
}
