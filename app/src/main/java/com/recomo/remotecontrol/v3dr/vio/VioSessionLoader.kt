package com.recomo.remotecontrol.v3dr.vio

import android.util.Log
import java.io.File

class VioSessionLoader {
    companion object {
        private const val TAG = "VioSessionLoader"
    }

    fun loadFromRecordingPath(recordingPath: String): Result<VioSession> {
        val videoFile = File(recordingPath)
        if (!videoFile.exists()) {
            return Result.failure(IllegalStateException("Video file not found: $recordingPath"))
        }
        val sessionDir = videoFile.parentFile
            ?: return Result.failure(IllegalStateException("Session directory not found"))

        val imuFile = File(sessionDir, "imu.csv")
        val frameFile = File(sessionDir, "frame_timestamps.csv")
        val calibFile = File(sessionDir, "calib.json").takeIf { it.exists() }
        val manifestFile = File(sessionDir, "manifest.json").takeIf { it.exists() }

        if (!imuFile.exists()) {
            return Result.failure(IllegalStateException("IMU file missing: ${imuFile.absolutePath}"))
        }
        if (!frameFile.exists()) {
            return Result.failure(IllegalStateException("Frame timestamps missing: ${frameFile.absolutePath}"))
        }

        val session = VioSession(
            sessionDir = sessionDir,
            videoFile = videoFile,
            imuFile = imuFile,
            frameTimestampsFile = frameFile,
            calibFile = calibFile,
            manifestFile = manifestFile
        )

        Log.d(TAG, "Loaded session: ${sessionDir.name}")
        return Result.success(session)
    }
}
