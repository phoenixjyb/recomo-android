package com.recomo.user.data.studiodance

import android.content.Context
import com.recomo.user.data.media.UserMediaManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Studio Dance music files in persistent external storage.
 *
 * Music files are downloaded from cloud once and persist across app reinstalls
 * under `getExternalFilesDir(null)/Recomo/StudioDanceMusic/`.
 */
@Singleton
class StudioDanceMusicRepository @Inject constructor(
    private val mediaManager: UserMediaManager,
    @ApplicationContext private val context: Context
) {
    private val http by lazy {
        HttpClient(CIO) {
            engine { requestTimeout = 60_000 }
        }
    }

    /**
     * Check if a music file is available locally.
     * Falls back to bundled assets for built-in test files.
     */
    fun isMusicAvailable(fileName: String): Boolean {
        if (mediaManager.fileExists(UserMediaManager.Category.StudioDanceMusic, fileName)) return true
        // Check bundled assets as fallback (for pipeline-test files)
        return runCatching { context.assets.open("studio_dance/$fileName").close(); true }.getOrDefault(false)
    }

    /**
     * Get local File reference for a music file.
     * If not yet in persistent storage, copies from bundled assets first (pipeline-test files).
     */
    fun musicFile(fileName: String): File {
        val localFile = mediaManager.fileFor(UserMediaManager.Category.StudioDanceMusic, fileName)
        if (localFile.exists() && localFile.length() > 0L) return localFile
        // Bootstrap: copy bundled asset to persistent storage on first access
        runCatching {
            context.assets.open("studio_dance/$fileName").use { input ->
                localFile.parentFile?.mkdirs()
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return localFile
    }

    /**
     * Download from cloud if not present locally. Returns local File on success.
     * Safe to call multiple times — skips download if file already exists.
     */
    suspend fun ensureMusic(fileName: String, cloudBaseUrl: String): Result<File> =
        withContext(Dispatchers.IO) {
            val localFile = musicFile(fileName)
            if (localFile.exists() && localFile.length() > 0L) {
                return@withContext Result.success(localFile)
            }
            runCatching {
                val url = "$cloudBaseUrl/studio_dance_music/$fileName"
                val bytes = http.get(url).readBytes()
                localFile.parentFile?.mkdirs()
                localFile.writeBytes(bytes)
                localFile
            }
        }

    /** List all locally available music files. */
    suspend fun listLocalMusic(): List<File> =
        mediaManager.listFiles(
            UserMediaManager.Category.StudioDanceMusic,
            extensions = setOf("mp3", "wav", "ogg", "m4a")
        )
}
