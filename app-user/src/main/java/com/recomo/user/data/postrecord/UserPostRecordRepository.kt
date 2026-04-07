package com.recomo.user.data.postrecord

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.recomo.user.data.media.UserMediaManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class UserPostRecordRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaManager: UserMediaManager
) {
    private val _recordings = MutableStateFlow<List<UserPostRecordItem>>(emptyList())
    val recordings: StateFlow<List<UserPostRecordItem>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _isLoading.value = true
            _error.value = null
            _recordings.value = scanRecordings()
        }.onFailure { throwable ->
            _error.value = throwable.message ?: "Unable to load takes"
        }.also {
            _isLoading.value = false
        }
    }

    suspend fun deleteRecording(item: UserPostRecordItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (item.file.exists()) {
                item.file.delete()
            }
            _recordings.value = _recordings.value.filterNot { it.id == item.id }
        }.onFailure { throwable ->
            _error.value = throwable.message ?: "Unable to delete take"
        }
    }

    fun recordingsDirectory(): File = mediaManager.directoryFor(UserMediaManager.Category.Recordings)

    private fun scanRecordings(): List<UserPostRecordItem> {
        val directory = recordingsDirectory()
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.filter { it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull(::toRecordingItem)
            ?.toList()
            ?: emptyList()
    }

    private fun toRecordingItem(file: File): UserPostRecordItem? {
        if (!file.exists()) return null
        val metadata = readMetadata(file)
        return UserPostRecordItem(
            id = file.absolutePath,
            file = file,
            recordedAtMs = file.lastModified(),
            durationMs = metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            frameRate = metadata.frameRate,
            codecLabel = metadata.codecLabel,
            fileSizeBytes = file.length()
        )
    }

    private fun readMetadata(file: File): RecordingMetadata {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }
            RecordingMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull(),
                codecLabel = readCodecLabel(file)
            )
        } catch (_: Exception) {
            RecordingMetadata(codecLabel = readCodecLabel(file))
        } finally {
            retriever?.release()
        }
    }

    private fun readCodecLabel(file: File): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount)
                .firstNotNullOfOrNull { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: return@firstNotNullOfOrNull null
                    if (!mime.startsWith("video/")) return@firstNotNullOfOrNull null
                    when (mime) {
                        MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265"
                        MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264"
                        else -> mime.removePrefix("video/").uppercase()
                    }
                }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private data class RecordingMetadata(
        val durationMs: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val frameRate: Float? = null,
        val codecLabel: String? = null
    )

    private companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "mov", "mkv")
    }
}
