package com.recomo.user.phoneteach.ui.library

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI model describing a single phone-moco capture session on disk.
 *
 * Backed by a session directory at:
 *   getExternalFilesDir(DIRECTORY_MOVIES)/PhoneTeach/session_<yyyyMMdd_HHmmss>/
 *
 * Populated by [LibraryViewModel.scanSessions] from filesystem state; no persistent database.
 */
data class PhoneTeachSession(
    val sessionDir: File,
    val videoFile: File,
    val sessionName: String = sessionDir.name,
    val videoSizeBytes: Long = videoFile.length(),
    val totalSizeBytes: Long = sessionDir.walk().filter { it.isFile }.sumOf { it.length() },
    val createdAtMs: Long = videoFile.lastModified(),
    val thumbnailPath: String? = null,
    val hasArCoreTrajectory: Boolean = File(sessionDir, "trajectory_arcore_tum.txt").exists(),
    val hasImu: Boolean = File(sessionDir, "imu.csv").exists(),
    val hasCalib: Boolean = File(sessionDir, "calib.json").exists(),
    val hasMetadata: Boolean = File(sessionDir, "metadata.jsonl").exists(),
    val hasManifest: Boolean = File(sessionDir, "manifest.json").exists()
) {
    val id: String get() = sessionDir.absolutePath

    val formattedSize: String
        get() {
            val mb = totalSizeBytes / (1024.0 * 1024.0)
            return if (mb < 1) {
                "%.1f KB".format(totalSizeBytes / 1024.0)
            } else {
                "%.1f MB".format(mb)
            }
        }

    val formattedDate: String
        get() {
            val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return format.format(Date(createdAtMs))
        }
}
