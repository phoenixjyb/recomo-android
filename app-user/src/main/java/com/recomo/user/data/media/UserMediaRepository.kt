package com.recomo.user.data.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.recomo.user.data.UserSettingsRepository
import java.io.File

@Singleton
class UserMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSettingsRepository: UserSettingsRepository,
    private val orinMediaClient: UserOrinMediaClient,
    private val mediaManager: UserMediaManager
) {
    private val _mediaItems = MutableStateFlow<List<UserMediaItem>>(emptyList())
    val mediaItems: StateFlow<List<UserMediaItem>> = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private var lastFilter: UserMediaFilter? = null

    suspend fun fetchMediaList(
        page: Int = 0,
        pageSize: Int = 120,
        filter: UserMediaFilter? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _isLoading.value = true
            _error.value = null
            lastFilter = filter
            val baseUrl = userSettingsRepository.appSettings.first().orinMediaUrl
            val result = orinMediaClient.listMedia(baseUrl, page, pageSize, filter)
            result.onSuccess { response ->
                _mediaItems.value = if (page == 0) response.items else _mediaItems.value + response.items
            }.onFailure { throwable ->
                _error.value = throwable.message ?: "Failed to load media"
                throw throwable
            }
            Unit
        }.also {
            _isLoading.value = false
        }
    }

    fun downloadMedia(mediaItem: UserMediaItem) =
        orinMediaClient.downloadMedia(mediaItem.downloadUrl, destinationFor(mediaItem))
            .flowOn(Dispatchers.IO)

    fun setDownloadProgress(mediaId: String, progress: Float) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            if (progress >= 1f) remove(mediaId) else put(mediaId, progress)
        }
    }

    fun getDownloadProgress(mediaId: String): Float? = _downloadProgress.value[mediaId]

    fun isDownloaded(mediaItem: UserMediaItem): Boolean {
        val file = destinationFor(mediaItem)
        return file.exists() && file.length() > 0L
    }

    fun getLocalFile(mediaItem: UserMediaItem): File? {
        val file = destinationFor(mediaItem)
        return file.takeIf { it.exists() }
    }

    fun localSize(mediaItem: UserMediaItem): Long = getLocalFile(mediaItem)?.length() ?: 0L

    fun downloadsDirectory(): File = mediaManager.directoryFor(UserMediaManager.Category.Downloads)

    suspend fun deleteLocalMedia(mediaItem: UserMediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            destinationFor(mediaItem).takeIf { it.exists() }?.delete()
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                remove(mediaItem.id)
            }
            Unit
        }
    }

    suspend fun refresh(): Result<Unit> = fetchMediaList(filter = lastFilter)

    /**
     * Scan local Recordings/ directory and return as UserMediaItems.
     * These are tablet-side recordings — not from Orin.
     */
    suspend fun getLocalRecordings(): List<UserMediaItem> = withContext(Dispatchers.IO) {
        val dir = mediaManager.directoryFor(UserMediaManager.Category.Recordings)
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("mp4", "mkv", "mov") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                UserMediaItem(
                    id = "local_${file.name}",
                    filename = file.name,
                    type = UserMediaType.VIDEO,
                    timestamp = file.lastModified(),
                    size = file.length(),
                    downloadUrl = "file://${file.absolutePath}"
                )
            } ?: emptyList()
    }

    private fun destinationFor(mediaItem: UserMediaItem): File =
        File(downloadsDirectory(), mediaItem.filename)
}
