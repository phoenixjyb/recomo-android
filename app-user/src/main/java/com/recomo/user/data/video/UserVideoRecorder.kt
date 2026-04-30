package com.recomo.user.data.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.recomo.user.data.media.UserMediaManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "UserVideoRecorder"

@Singleton
class UserVideoRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaManager: UserMediaManager
) {
    data class StartConfig(
        val codec: String,   // "h264", "h265", or "mjpeg"
        val width: Int,
        val height: Int,
        val fps: Int
    )

    private val mutex = Mutex()
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var currentFile: File? = null
    @Volatile private var isRecording = false
    @Volatile private var bytesWritten = 0L
    private var muxerStarted = false
    private var startTimeUs = 0L
    private var config = StartConfig(codec = "h265", width = 1920, height = 1080, fps = 30)

    // MJPEG encoding state
    private var encoder: MediaCodec? = null
    private var isMjpegMode = false

    suspend fun startRecording(startConfig: StartConfig): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isRecording) return@withLock null
            try {
                config = startConfig
                isMjpegMode = startConfig.codec.lowercase(Locale.US) == "mjpeg"
                val outputDir = mediaManager.directoryFor(UserMediaManager.Category.Recordings)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                currentFile = File(outputDir, "video_$timestamp.mp4")
                muxer = MediaMuxer(currentFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                isRecording = true
                muxerStarted = false
                bytesWritten = 0L
                trackIndex = -1
                startTimeUs = 0L

                if (isMjpegMode) {
                    val w = startConfig.width.coerceAtLeast(16)
                    val h = startConfig.height.coerceAtLeast(16)
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                        setInteger(MediaFormat.KEY_FRAME_RATE, startConfig.fps.coerceAtLeast(1))
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    }
                    encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                        configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        start()
                    }
                }

                Log.i(TAG, "Started recording to ${currentFile!!.absolutePath} mode=${if (isMjpegMode) "mjpeg→h264" else startConfig.codec}")
                currentFile!!.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                cleanupLocked()
                null
            }
        }
    }

    /** Write a raw H.26x frame (NAL units). */
    suspend fun writeFrame(data: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isRecording || muxer == null || isMjpegMode) return@withLock
            try {
                if (!muxerStarted) {
                    if (!isKeyFrame(data, config.codec)) return@withLock
                    val format = extractMediaFormat(data, config) ?: return@withLock
                    trackIndex = muxer!!.addTrack(format)
                    muxer!!.start()
                    muxerStarted = true
                    startTimeUs = System.nanoTime() / 1000
                }

                if (muxerStarted && trackIndex >= 0) {
                    val bufferInfo = MediaCodec.BufferInfo().apply {
                        offset = 0
                        size = data.size
                        presentationTimeUs = (System.nanoTime() / 1000) - startTimeUs
                        flags = if (isKeyFrame(data, config.codec)) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    }
                    muxer!!.writeSampleData(trackIndex, ByteBuffer.wrap(data), bufferInfo)
                    bytesWritten += data.size.toLong()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write frame", e)
            }
        }
    }

    /** Write a JPEG frame — decode to bitmap, encode to H.264, mux. */
    suspend fun writeJpegFrame(jpegData: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isRecording || muxer == null || !isMjpegMode || encoder == null) return@withLock
            try {
                val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size) ?: return@withLock
                val enc = encoder!!
                val w = config.width.coerceAtLeast(16)
                val h = config.height.coerceAtLeast(16)
                val scaled = if (bitmap.width != w || bitmap.height != h) {
                    Bitmap.createScaledBitmap(bitmap, w, h, true).also { bitmap.recycle() }
                } else bitmap

                // Feed input
                val inputIndex = enc.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = enc.getInputBuffer(inputIndex)!!
                    val yuvData = bitmapToNv12(scaled, w, h)
                    inputBuffer.clear()
                    inputBuffer.put(yuvData)
                    val pts = if (startTimeUs == 0L) {
                        startTimeUs = System.nanoTime() / 1000
                        0L
                    } else {
                        (System.nanoTime() / 1000) - startTimeUs
                    }
                    enc.queueInputBuffer(inputIndex, 0, yuvData.size, pts, 0)
                }
                scaled.recycle()

                // Drain output
                drainEncoder(enc)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write JPEG frame", e)
            }
        }
    }

    private fun drainEncoder(enc: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = enc.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer!!.addTrack(enc.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                }
                outputIndex >= 0 -> {
                    val outputBuffer = enc.getOutputBuffer(outputIndex)!!
                    if (muxerStarted && trackIndex >= 0 && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer!!.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        bytesWritten += bufferInfo.size.toLong()
                    }
                    enc.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> break
            }
        }
    }

    private fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val yuvSize = width * height * 3 / 2
        val yuv = ByteArray(yuvSize)
        var yIndex = 0
        var uvIndex = width * height
        for (j in 0 until height) {
            for (i in 0 until width) {
                val argb = pixels[j * width + i]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    suspend fun stopRecording(): Pair<String, Long>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isRecording) return@withLock null
            return@withLock try {
                // Signal encoder EOS and drain remaining
                if (isMjpegMode && encoder != null) {
                    val enc = encoder!!
                    val inputIndex = enc.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        enc.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                    drainEncoder(enc)
                    enc.stop()
                    enc.release()
                    encoder = null
                }
                if (muxerStarted && muxer != null) muxer?.stop()
                muxer?.release()
                val file = currentFile
                val fileSize = file?.length() ?: 0L
                resetStateLocked()
                if (file != null && file.exists() && fileSize > 0L) {
                    // Copy to shared storage (Movies/Recomo/) so it survives uninstall
                    // and appears in system gallery/Files app
                    copyToSharedStorage(file)
                    Pair(file.absolutePath, fileSize)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                cleanupLocked()
                null
            }
        }
    }

    /**
     * Copy recorded file to shared Movies/Recomo/ via MediaStore.
     * The original app-private file is kept as well.
     */
    private fun copyToSharedStorage(file: File) {
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Recomo")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Copied recording to shared storage: Movies/Recomo/${file.name}")
            } else {
                Log.w(TAG, "Failed to insert into MediaStore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to shared storage", e)
            // Non-fatal — app-private copy still exists
        }
    }

    fun isRecording(): Boolean = isRecording

    fun getBytesWritten(): Long = bytesWritten

    private fun extractMediaFormat(data: ByteArray, startConfig: StartConfig): MediaFormat? {
        return try {
            val codecLower = startConfig.codec.lowercase(Locale.US)
            val mime = if (codecLower.contains("264") || codecLower.contains("avc")) {
                MediaFormat.MIMETYPE_VIDEO_AVC
            } else {
                MediaFormat.MIMETYPE_VIDEO_HEVC
            }
            val format = MediaFormat.createVideoFormat(
                mime,
                startConfig.width.coerceAtLeast(16),
                startConfig.height.coerceAtLeast(16)
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, startConfig.fps.coerceAtLeast(1))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            when (mime) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> {
                    val (sps, pps) = extractAvcCsd(data)
                    if (sps != null) format.setByteBuffer("csd-0", ByteBuffer.wrap(withStartCode(sps)))
                    if (pps != null) format.setByteBuffer("csd-1", ByteBuffer.wrap(withStartCode(pps)))
                }
                MediaFormat.MIMETYPE_VIDEO_HEVC -> {
                    extractHevcCsd(data)?.let { format.setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
                }
            }
            format
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract media format", e)
            null
        }
    }

    private fun extractAvcCsd(data: ByteArray): Pair<ByteArray?, ByteArray?> {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        forEachNalUnit(data, codec = "h264") { nal, nalType ->
            when (nalType) {
                7 -> if (sps == null) sps = nal
                8 -> if (pps == null) pps = nal
            }
        }
        return sps to pps
    }

    private fun extractHevcCsd(data: ByteArray): ByteArray? {
        val csdUnits = mutableListOf<ByteArray>()
        forEachNalUnit(data, codec = "h265") { nal, nalType ->
            if (nalType in 32..34) {
                csdUnits += nal
            }
        }
        if (csdUnits.isEmpty()) return null
        val totalSize = csdUnits.sumOf { it.size + 4 }
        val combined = ByteArray(totalSize)
        var offset = 0
        csdUnits.forEach { nal ->
            combined[offset++] = 0
            combined[offset++] = 0
            combined[offset++] = 0
            combined[offset++] = 1
            System.arraycopy(nal, 0, combined, offset, nal.size)
            offset += nal.size
        }
        return combined
    }

    private fun withStartCode(nal: ByteArray): ByteArray {
        val prefixed = ByteArray(nal.size + 4)
        prefixed[0] = 0
        prefixed[1] = 0
        prefixed[2] = 0
        prefixed[3] = 1
        System.arraycopy(nal, 0, prefixed, 4, nal.size)
        return prefixed
    }

    private fun forEachNalUnit(
        data: ByteArray,
        codec: String,
        onNal: (nalWithoutStartCode: ByteArray, nalType: Int) -> Unit
    ) {
        var i = 0
        while (i < data.size - 3) {
            val startCodeSize = when {
                i + 3 < data.size &&
                    data[i] == 0.toByte() &&
                    data[i + 1] == 0.toByte() &&
                    data[i + 2] == 0.toByte() &&
                    data[i + 3] == 1.toByte() -> 4
                data[i] == 0.toByte() &&
                    data[i + 1] == 0.toByte() &&
                    data[i + 2] == 1.toByte() -> 3
                else -> 0
            }
            if (startCodeSize == 0) {
                i++
                continue
            }
            val nalStart = i + startCodeSize
            var nalEnd = nalStart
            while (nalEnd < data.size - 3) {
                val nextStart = data[nalEnd] == 0.toByte() &&
                    data[nalEnd + 1] == 0.toByte() &&
                    (data[nalEnd + 2] == 1.toByte() ||
                        (nalEnd + 3 < data.size &&
                            data[nalEnd + 2] == 0.toByte() &&
                            data[nalEnd + 3] == 1.toByte()))
                if (nextStart) break
                nalEnd++
            }
            if (nalStart < nalEnd) {
                val nal = data.copyOfRange(nalStart, nalEnd)
                val h264Type = nal.first().toInt() and 0x1F
                val h265Type = (nal.first().toInt() and 0x7E) shr 1
                onNal(nal, if (codec.lowercase(Locale.US).contains("264")) h264Type else h265Type)
            }
            i = nalEnd
        }
    }

    private fun isKeyFrame(data: ByteArray, codec: String): Boolean {
        val codecLower = codec.lowercase(Locale.US)
        var foundKeyframe = false
        forEachNalUnit(data, codecLower) { _, nalType ->
            if (codecLower.contains("264") || codecLower.contains("avc")) {
                if (nalType == 5 || nalType == 7 || nalType == 8) {
                    foundKeyframe = true
                }
            } else {
                if (nalType in 16..21 || (nalType in 32..34 && data.size > 50000)) {
                    foundKeyframe = true
                }
            }
        }
        return foundKeyframe
    }

    private fun cleanupLocked() {
        try {
            if (isMjpegMode && encoder != null) {
                encoder?.stop()
                encoder?.release()
                encoder = null
            }
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing muxer/encoder", e)
        }
        resetStateLocked()
    }

    private fun resetStateLocked() {
        muxer = null
        encoder = null
        trackIndex = -1
        currentFile = null
        isRecording = false
        bytesWritten = 0L
        muxerStarted = false
        startTimeUs = 0L
        isMjpegMode = false
    }
}
