package com.recomo.common.capture.recording

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Writes manifest.json for a VIO session package.
 */
class SessionManifestWriter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    companion object {
        private const val TAG = "SessionManifestWriter"
    }

    @Serializable
    data class Manifest(
        @SerialName("schema_version") val schemaVersion: Int = 1,
        @SerialName("session_id") val sessionId: String,
        @SerialName("created_at_ms") val createdAtMs: Long,
        val files: List<ManifestFile>
    )

    @Serializable
    data class ManifestFile(
        val name: String,
        @SerialName("size_bytes") val sizeBytes: Long,
        @SerialName("sha256") val sha256: String
    )

    fun write(sessionDir: File, files: List<File>, createdAtMs: Long = System.currentTimeMillis()): File {
        val manifestFile = File(sessionDir, "manifest.json")
        val manifest = Manifest(
            sessionId = sessionDir.name,
            createdAtMs = createdAtMs,
            files = files.filter { it.exists() }.map { file ->
                ManifestFile(
                    name = file.relativeTo(sessionDir).path,
                    sizeBytes = file.length(),
                    sha256 = sha256(file)
                )
            }
        )
        manifestFile.writeText(json.encodeToString(manifest))
        Log.i(TAG, "Manifest written: ${manifestFile.absolutePath}")
        return manifestFile
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
