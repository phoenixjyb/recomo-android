package com.recomo.remotecontrol.v3dr.vio

import com.recomo.remotecontrol.v3dr.data.model.ImuSample
import java.io.File

/**
 * Parsed VIO session package references.
 */
data class VioSession(
    val sessionDir: File,
    val videoFile: File,
    val imuFile: File,
    val frameTimestampsFile: File,
    val calibFile: File?,
    val manifestFile: File?
)

data class FrameTimestamp(
    val frameIdx: Long,
    val timestampNs: Long
)

data class SyncPacket(
    val frameIdx: Long,
    val frameTimestampNs: Long,
    val imuSamples: List<ImuSample>
)

enum class VioStatus {
    SUCCESS,
    FAILED,
    NOT_IMPLEMENTED
}

enum class VioDepsStatus {
    AVAILABLE,
    MISSING,
    NOT_AVAILABLE,
    ERROR
}

data class VioResult(
    val status: VioStatus,
    val message: String,
    val outputDir: File? = null,
    val outputs: Map<String, File> = emptyMap()
)

data class VioDepsResult(
    val status: VioDepsStatus,
    val message: String
)
