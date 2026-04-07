package com.recomo.remotecontrol.v3dr.vio

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StubVioBackend : VioBackend {
    override val id: String = "stub"
    override val displayName: String = "Stub Backend"
    override val version: String = "0.1"

    override fun isAvailable(): Boolean = true

    override suspend fun checkDependencies(): VioDepsResult {
        return VioDepsResult(
            status = VioDepsStatus.AVAILABLE,
            message = "Stub backend has no native deps"
        )
    }

    override suspend fun run(
        session: VioSession,
        syncPackets: List<SyncPacket>,
        outputDir: File
    ): VioResult {
        outputDir.mkdirs()

        val trajectoryTum = File(outputDir, "trajectory_tum.txt")
        val trajectoryJson = File(outputDir, "trajectory.json")
        val metricsJson = File(outputDir, "metrics.json")
        val backendLog = File(outputDir, "backend_log.txt")

        trajectoryTum.writeText("# VIO backend not implemented.\n")
        trajectoryJson.writeText(
            Json { prettyPrint = true }.encodeToString(
                mapOf(
                    "status" to "not_implemented",
                    "message" to "Stub backend does not compute trajectory yet"
                )
            )
        )
        metricsJson.writeText(
            Json { prettyPrint = true }.encodeToString(
                mapOf(
                    "status" to "not_implemented",
                    "frames" to syncPackets.size,
                    "imu_samples" to syncPackets.sumOf { it.imuSamples.size }
                )
            )
        )
        backendLog.writeText(
            "Stub backend invoked for session ${session.sessionDir.name}. " +
                "No VIO computation performed.\n"
        )

        return VioResult(
            status = VioStatus.NOT_IMPLEMENTED,
            message = "Stub backend did not compute trajectory",
            outputDir = outputDir,
            outputs = mapOf(
                "trajectory_tum" to trajectoryTum,
                "trajectory_json" to trajectoryJson,
                "metrics" to metricsJson,
                "backend_log" to backendLog
            )
        )
    }
}
