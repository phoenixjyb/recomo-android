package com.recomo.common.chat.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

private const val TAG = "WhisperModelRepo"

/**
 * Manages on-device lifecycle of a sherpa-onnx Whisper model bundle.
 *
 * Bundle layout on disk (per [modelDir]):
 *   <filesDir>/whisper-models/<model_id>/
 *     encoder.int8.onnx
 *     decoder.int8.onnx
 *     tokens.txt
 *
 * The bundle is downloaded as a ZIP from [BASE_URL] on first use and
 * extracted in-place. Zip container is chosen over tar.bz2 because
 * Android has `ZipInputStream` in the platform; no 3rd-party deps.
 *
 * All network I/O happens on [Dispatchers.IO]. UI collects
 * [downloadState] to draw a progress bar.
 *
 * The URL is a compile-time constant for v1 so the spec is visible in
 * code review; promote to BuildConfig when we need per-flavour hosts.
 */
class WhisperModelRepository(
    private val context: Context,
    private val baseUrl: String = BASE_URL
) {

    /** Ambient download / load state. Collected by Settings UI. */
    sealed class DownloadState {
        data object Idle : DownloadState()
        data class InProgress(val bytesDone: Long, val bytesTotal: Long) : DownloadState()
        data class Failed(val reason: String) : DownloadState()
        data object Ready : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /** Absolute dir for the given [modelId]; callers pass into sherpa-onnx config. */
    fun modelDir(modelId: String = DEFAULT_MODEL_ID): File =
        File(context.filesDir, "whisper-models/$modelId")

    fun hasModel(modelId: String = DEFAULT_MODEL_ID): Boolean {
        val dir = modelDir(modelId)
        if (!dir.isDirectory) return false
        val model = WhisperModel.fromModelId(modelId)
        return when (model.engineType) {
            SttEngineType.WHISPER ->
                File(dir, "encoder.int8.onnx").exists() &&
                    File(dir, "decoder.int8.onnx").exists() &&
                    File(dir, "tokens.txt").exists()
            SttEngineType.SENSEVOICE ->
                File(dir, "model.int8.onnx").exists() &&
                    File(dir, "tokens.txt").exists()
        }
    }

    /**
     * Download + extract the [modelId] bundle. Safe to call when the
     * model already exists (fast no-op). Errors land on
     * [DownloadState.Failed] and the return value is false.
     */
    suspend fun ensureModel(modelId: String = DEFAULT_MODEL_ID): Boolean = withContext(Dispatchers.IO) {
        if (hasModel(modelId)) {
            _downloadState.value = DownloadState.Ready
            return@withContext true
        }
        val destDir = modelDir(modelId).also { it.mkdirs() }
        val url = URL("$baseUrl/$modelId.zip")
        Log.i(TAG, "downloading whisper bundle $modelId from $url")
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                requestMethod = "GET"
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                val reason = "HTTP ${conn.responseCode} for $url"
                _downloadState.value = DownloadState.Failed(reason)
                return@withContext false
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            _downloadState.value = DownloadState.InProgress(0, total)

            // Stream-unzip direct from HTTP to avoid holding the whole
            // bundle in RAM or on disk as a .zip file.
            val zipIn = ZipInputStream(conn.inputStream)
            var bytesDone = 0L
            var entry = zipIn.nextEntry
            val buf = ByteArray(64 * 1024)
            while (entry != null) {
                if (entry.isDirectory) {
                    File(destDir, entry.name).mkdirs()
                } else {
                    val out = File(destDir, entry.name).apply {
                        parentFile?.mkdirs()
                    }
                    FileOutputStream(out).use { fos ->
                        while (true) {
                            val n = zipIn.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            bytesDone += n
                            _downloadState.value = DownloadState.InProgress(bytesDone, total)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            if (!hasModel(modelId)) {
                _downloadState.value = DownloadState.Failed("Missing files after extract")
                return@withContext false
            }
            _downloadState.value = DownloadState.Ready
            Log.i(TAG, "whisper bundle $modelId ready at $destDir")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "model download failed: ${t.message}", t)
            _downloadState.value = DownloadState.Failed(t.message ?: "download failed")
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** Wipe a previously-downloaded bundle. Useful for re-downloads / tests. */
    fun deleteModel(modelId: String = DEFAULT_MODEL_ID): Boolean {
        _downloadState.value = DownloadState.Idle
        return modelDir(modelId).deleteRecursively()
    }

    companion object {
        /**
         * Default model. Tiny INT8 multilingual (~40 MB extracted) —
         * balances latency + quality for chat prompts on Tab-class
         * ARM64 devices. Swap via app-layer config when we want base/
         * SenseVoice-zh without touching this class.
         */
        const val DEFAULT_MODEL_ID: String = "whisper-tiny-int8-v1"

        /**
         * Our self-hosted GitLab at 115.190.112.4 (recomo-android-assets
         * project, Generic Packages API). Public project — no auth needed.
         * Pattern: `$BASE_URL/$modelId.zip`
         */
        const val BASE_URL: String =
            "http://115.190.112.4/api/v4/projects/50/packages/generic/whisper/v1"
    }
}
