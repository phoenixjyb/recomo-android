package com.recomo.remotecontrol.v3dr.data.repository

import android.content.Context
import com.recomo.remotecontrol.v3dr.data.SettingsRepository
import com.recomo.remotecontrol.v3dr.data.model.ModelDownloadProgress
import com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus
import com.recomo.remotecontrol.v3dr.data.model.SceneReconstruction
import com.recomo.remotecontrol.v3dr.network.V3DRApiService
import com.recomo.remotecontrol.v3dr.network.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing 3D scenes and reconstructions
 * Handles API calls, caching, and download management
 */
@Singleton
class SceneRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: V3DRApiService,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "SceneRepository"
        private const val MODELS_CACHE_DIR = "models_cache"
        private const val THUMBNAILS_CACHE_DIR = "scene_thumbnails"
    }

    // Scene list
    private val _scenes = MutableStateFlow<List<SceneReconstruction>>(emptyList())
    val scenes: StateFlow<List<SceneReconstruction>> = _scenes.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Download progress tracking
    private val _downloadProgress = MutableStateFlow<Map<String, ModelDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, ModelDownloadProgress>> = _downloadProgress.asStateFlow()

    /**
     * Get models cache directory
     */
    private fun getModelsCacheDir(): File {
        val cacheDir = File(context.cacheDir, MODELS_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Get thumbnails cache directory
     */
    private fun getThumbnailsCacheDir(): File {
        val cacheDir = File(context.cacheDir, THUMBNAILS_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Fetch list of available scenes from server
     */
    suspend fun fetchScenes(
        page: Int = 0,
        limit: Int = 50,
        statusFilter: ReconstructionStatus? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _isLoading.value = true
            _error.value = null

            Timber.d("Fetching scenes from server (page=$page, limit=$limit)")

            val statusParam = when (statusFilter) {
                ReconstructionStatus.PENDING -> "pending"
                ReconstructionStatus.PROCESSING -> "processing"
                ReconstructionStatus.COMPLETED -> "completed"
                ReconstructionStatus.FAILED -> "failed"
                null -> null
            }

            val response = apiService.listScenes(status = statusParam)

            if (response.isSuccessful) {
                val scenesData = response.body()
                if (scenesData != null) {
                    val base = settingsRepository.settingsFlow.first().serverUrl
                    val domainScenes = scenesData.scenes.map { it.toDomain(base) }
                    _scenes.value = domainScenes
                    Timber.d("Fetched ${domainScenes.size} scenes")
                } else {
                    Timber.w("Response body is null")
                    _error.value = "No data received from server"
                }
            } else {
                val errorMsg = "Failed to fetch scenes: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                _error.value = errorMsg
                throw Exception(errorMsg)
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error fetching scenes")
            _error.value = exception.message ?: "Unknown error"
        }.also {
            _isLoading.value = false
        }
    }

    /**
     * Get details for a specific scene
     */
    suspend fun getSceneDetails(sceneId: String): Result<SceneReconstruction> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Fetching details for scene: $sceneId")

            val response = apiService.getScene(sceneId)

            if (response.isSuccessful) {
                val sceneDetail = response.body()
                if (sceneDetail != null) {
                    val base = settingsRepository.settingsFlow.first().serverUrl
                    sceneDetail.scene.toDomain(base)
                } else {
                    throw Exception("Scene details not found")
                }
            } else {
                throw Exception("Failed to fetch scene details: ${response.code()}")
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error fetching scene details for $sceneId")
        }
    }

    /**
     * Get scene by session ID
     */
    suspend fun getSceneBySession(sessionId: String): Result<SceneReconstruction> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Fetching scene for session: $sessionId")

            val response = apiService.getSceneBySession(sessionId)

            if (response.isSuccessful) {
                val sceneDetail = response.body()
                if (sceneDetail != null) {
                    val base = settingsRepository.settingsFlow.first().serverUrl
                    sceneDetail.scene.toDomain(base)
                } else {
                    throw Exception("Scene not found for session")
                }
            } else {
                throw Exception("Failed to fetch scene for session: ${response.code()}")
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error fetching scene for session $sessionId")
        }
    }

    /**
     * Create a reconstruction job for a session
     */
    suspend fun createScene(sessionId: String, method: String = "colmap", quality: String = "draft", sceneName: String? = null): Result<SceneReconstruction> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Creating scene for session: $sessionId")
            val response = apiService.createScene(
                com.recomo.remotecontrol.v3dr.network.CreateSceneRequest(
                    session_id = sessionId,
                    method = method,
                    quality = quality,
                    scene_name = sceneName
                )
            )
            if (response.isSuccessful) {
                val body = response.body() ?: throw Exception("Empty response")
                val base = settingsRepository.settingsFlow.first().serverUrl
                body.scene.toDomain(base)
            } else {
                throw Exception("Create scene failed: ${response.code()}")
            }
        }.onFailure { Timber.e(it, "Error creating scene for session %s", sessionId) }
    }

    /**
     * Upload video for 3D reconstruction
     */
    suspend fun uploadVideo(
        sessionId: String,
        videoFile: File,
        metadataFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            _isLoading.value = true
            _error.value = null

            Timber.d("Uploading video: ${videoFile.name}, session: $sessionId")

            // Create request bodies
            val sessionIdBody = sessionId.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val videoRequestBody = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            val videoPart = MultipartBody.Part.createFormData(
                "video",
                videoFile.name,
                videoRequestBody
            )

            val metadataRequestBody = metadataFile.asRequestBody("application/json".toMediaTypeOrNull())
            val metadataPart = MultipartBody.Part.createFormData(
                "metadata",
                metadataFile.name,
                metadataRequestBody
            )

            val response = apiService.uploadVideo(
                sessionId = sessionIdBody,
                video = videoPart,
                metadata = metadataPart
            )

            if (response.isSuccessful) {
                val uploadResult = response.body()
                if (uploadResult?.success == true && uploadResult.scene_id != null) {
                    Timber.d("Upload successful, scene_id: ${uploadResult.scene_id}")
                    uploadResult.scene_id
                } else {
                    throw Exception(uploadResult?.message ?: "Upload failed")
                }
            } else {
                val errorMsg = "Upload failed: ${response.code()} ${response.message()}"
                Timber.e(errorMsg)
                throw Exception(errorMsg)
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error uploading video")
            _error.value = exception.message ?: "Upload failed"
        }.also {
            _isLoading.value = false
        }
    }

    /**
     * Poll reconstruction status
     */
    suspend fun checkReconstructionStatus(sceneId: String): Result<ReconstructionStatus> = 
        withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Checking reconstruction status for: $sceneId")

            val response = apiService.getScene(sceneId)

            if (response.isSuccessful) {
                val statusData = response.body()
                val status = statusData?.scene?.status?.lowercase()
                when (status) {
                    "pending" -> ReconstructionStatus.PENDING
                    "processing" -> ReconstructionStatus.PROCESSING
                    "completed" -> ReconstructionStatus.COMPLETED
                    "failed" -> ReconstructionStatus.FAILED
                    else -> ReconstructionStatus.PENDING
                }
            } else {
                throw Exception("Failed to check status: ${response.code()}")
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error checking reconstruction status for $sceneId")
        }
    }

    /**
     * Download 3D asset (glb/splat/point_cloud/preview)
     */
    suspend fun downloadAsset(
        sceneId: String,
        asset: String = "glb"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Downloading asset for scene: $sceneId, asset: $asset")

            // Initialize progress tracking
            _downloadProgress.value = _downloadProgress.value + (sceneId to ModelDownloadProgress(
                sceneId = sceneId,
                isDownloading = true
            ))

            val response = apiService.downloadAsset(sceneId, asset)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val outputFile = File(getModelsCacheDir(), "$sceneId.$asset")
                    val totalBytes = body.contentLength()

                    body.byteStream().use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                // Update progress
                                val progress = if (totalBytes > 0) {
                                    (totalBytesRead.toFloat() / totalBytes.toFloat())
                                } else 0f

                                _downloadProgress.value = _downloadProgress.value + (sceneId to ModelDownloadProgress(
                                    sceneId = sceneId,
                                    progress = progress,
                                    bytesDownloaded = totalBytesRead,
                                    totalBytes = totalBytes,
                                    isDownloading = true
                                ))
                            }
                        }
                    }

                    // Mark as completed
                    _downloadProgress.value = _downloadProgress.value + (sceneId to ModelDownloadProgress(
                        sceneId = sceneId,
                        progress = 1f,
                        bytesDownloaded = outputFile.length(),
                        totalBytes = outputFile.length(),
                        isDownloading = false,
                        isCompleted = true
                    ))

                    Timber.d("Model downloaded successfully: ${outputFile.absolutePath}")
                    outputFile
                } else {
                    throw Exception("Response body is null")
                }
            } else {
                throw Exception("Download failed: ${response.code()}")
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error downloading model for $sceneId")
            _downloadProgress.value = _downloadProgress.value + (sceneId to ModelDownloadProgress(
                sceneId = sceneId,
                isDownloading = false,
                isCompleted = false,
                errorMessage = exception.message
            ))
        }
    }

    /**
     * Download model (alias for downloadAsset)
     */
    suspend fun downloadModel(sceneId: String): Result<File> = downloadAsset(sceneId, "glb")

    /**
     * Download thumbnail
     */
    suspend fun downloadThumbnail(sceneId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Downloading thumbnail for scene: $sceneId")

            val response = apiService.downloadAsset(sceneId, "thumbnail")

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val outputFile = File(getThumbnailsCacheDir(), "$sceneId.jpg")
                    body.byteStream().use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Timber.d("Thumbnail downloaded: ${outputFile.absolutePath}")
                    outputFile
                } else {
                    throw Exception("Response body is null")
                }
            } else {
                throw Exception("Thumbnail download failed: ${response.code()}")
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error downloading thumbnail for $sceneId")
        }
    }

    /**
     * Delete scene from server
     */
    suspend fun deleteScene(sceneId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Deleting scene: $sceneId")

            val response = apiService.deleteScene(sceneId)

            if (response.isSuccessful) {
                val deleteResult = response.body()
                if (deleteResult?.success == true) {
                    // Remove from local list
                    _scenes.value = _scenes.value.filter { it.sceneId != sceneId }
                    
                    // Delete local cache
                    val modelFile = File(getModelsCacheDir(), "$sceneId.glb")
                    if (modelFile.exists()) {
                        modelFile.delete()
                    }
                    val thumbnailFile = File(getThumbnailsCacheDir(), "$sceneId.jpg")
                    if (thumbnailFile.exists()) {
                        thumbnailFile.delete()
                    }

                    Timber.d("Scene deleted successfully")
                } else {
                    throw Exception(deleteResult?.message ?: "Delete failed")
                }
            } else {
                throw Exception("Delete failed: ${response.code()}")
            }
        }.onFailure { exception ->
            Timber.e(exception, "Error deleting scene $sceneId")
        }
    }

    /**
     * Get local cached model file if exists
     */
    fun getCachedModel(sceneId: String, format: String = "glb"): File? {
        val file = File(getModelsCacheDir(), "$sceneId.$format")
        return if (file.exists()) file else null
    }

    /**
     * Get local cached thumbnail if exists
     */
    fun getCachedThumbnail(sceneId: String): File? {
        val file = File(getThumbnailsCacheDir(), "$sceneId.jpg")
        return if (file.exists()) file else null
    }

    /**
     * Clear all cached models
     */
    suspend fun clearCache(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Timber.d("Clearing models cache")
            getModelsCacheDir().listFiles()?.forEach { it.delete() }
            getThumbnailsCacheDir().listFiles()?.forEach { it.delete() }
            Unit
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
}
