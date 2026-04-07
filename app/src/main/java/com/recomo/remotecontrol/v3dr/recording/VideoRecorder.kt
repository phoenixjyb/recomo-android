package com.recomo.remotecontrol.v3dr.recording

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * VideoRecorder muxes encoded video frames to MP4 file.
 * Implements VideoEncoder.MuxerSink to receive encoded frames.
 */
class VideoRecorder(private val context: Context) : VideoEncoder.MuxerSink {
    private var muxer: MediaMuxer? = null
    private var trackIndex: Int = -1
    private var isStarted = false
    private var outputFile: File? = null

    companion object { 
        private const val TAG = "VideoRecorder" 
    }

    /**
     * Start recording to a new MP4 file.
     * Saves to app's private external files directory (alongside IMU/metadata).
     * @param nameHint Optional name prefix for the output file
     * @param sessionDir Optional session directory to save the file in
     * @return The output file that will be created
     */
    fun startRecording(nameHint: String? = null, sessionDir: File? = null): File {
        if (muxer != null) stopRecording()
        
        val safeName = (nameHint?.ifBlank { null } ?: "v3dr_clip")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        
        // Don't add timestamp if nameHint already contains a timestamp (new format)
        // Old format: "v3dr_clip" -> add timestamp
        // New format: "v3dr_clip_20251118_204708" -> don't add timestamp
        val fileName = if (nameHint != null && nameHint.contains("_202")) {
            "${safeName}.mp4"
        } else {
            val timestamp = System.currentTimeMillis()
            "${safeName}_${timestamp}.mp4"
        }
        
        // Save to session directory if provided, otherwise V3DR root
        val targetDir = if (sessionDir != null && sessionDir.exists()) {
            sessionDir
        } else {
            val appMoviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val v3drDir = File(appMoviesDir, "V3DR")
            v3drDir.mkdirs()
            v3drDir
        }
        val file = File(targetDir, fileName)
        
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        outputFile = file
        
        trackIndex = -1
        isStarted = false
        
        Log.i(TAG, "Recording to ${file.absolutePath}")
        return file
    }

    /**
     * Start recording to an explicit file path (used for VIO session packaging).
     * @param file Target MP4 file
     */
    fun startRecordingTo(file: File): File {
        if (muxer != null) stopRecording()
        file.parentFile?.mkdirs()
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        outputFile = file
        trackIndex = -1
        isStarted = false
        Log.i(TAG, "Recording to ${file.absolutePath}")
        return file
    }

    /**
     * Stop recording and finalize the MP4 file.
     * @return The output file that was created, or null if recording wasn't started
     */
    fun stopRecording(): File? {
        try {
            if (isStarted) muxer?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "muxer stop error", t)
        }
        try {
            muxer?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "muxer release error", t)
        }
        
        val f = outputFile
        muxer = null
        outputFile = null
        trackIndex = -1
        isStarted = false
        
        if (f != null && f.exists()) {
            Log.i(TAG, "Recording saved: ${f.absolutePath} (${f.length()} bytes)")
        }
        
        return f
    }

    override fun onFormatChanged(format: MediaFormat) {
        val m = muxer ?: return
        if (trackIndex == -1) {
            trackIndex = m.addTrack(format)
            Log.d(TAG, "Video track added: index=$trackIndex, format=$format")
        }
        if (!isStarted && trackIndex >= 0) {
            m.start()
            isStarted = true
            Log.d(TAG, "Muxer started")
        }
    }

    override fun onSample(sample: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        if (!isStarted || trackIndex < 0) return
        try {
            m.writeSampleData(trackIndex, sample, bufferInfo)
        } catch (t: Throwable) {
            Log.w(TAG, "writeSampleData error", t)
        }
    }
}
