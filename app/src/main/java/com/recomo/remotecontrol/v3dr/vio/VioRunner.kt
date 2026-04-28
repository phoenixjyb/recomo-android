package com.recomo.remotecontrol.v3dr.vio

import android.util.Log
import com.recomo.remotecontrol.v3dr.data.VioBackendType
import com.recomo.common.capture.model.ImuDataSet
import com.recomo.remotecontrol.v3dr.vio.cloud.CloudSfmVioBackend
import com.recomo.remotecontrol.v3dr.vio.openvins.OpenVinsBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VioRunner(
    private var backendType: VioBackendType = VioBackendType.CLOUD_SFM,
    private var cloudSfmUrl: String = "http://192.168.100.100:7000"
) {
    companion object {
        private const val TAG = "VioRunner"
    }

    private val backend: VioBackend
        get() = when (backendType) {
            VioBackendType.CLOUD_SFM -> CloudSfmVioBackend(cloudSfmUrl)
            VioBackendType.OPENVINS -> OpenVinsBackend()
            VioBackendType.ARCORE -> StubVioBackend() // TODO: Implement ARCoreVioBackend
        }

    fun setBackend(type: VioBackendType, sfmUrl: String? = null) {
        backendType = type
        sfmUrl?.let { cloudSfmUrl = it }
    }

    suspend fun runOffline(session: VioSession): VioResult = withContext(Dispatchers.IO) {
        val currentBackend = backend
        
        if (!currentBackend.isAvailable()) {
            return@withContext VioResult(
                status = VioStatus.NOT_IMPLEMENTED,
                message = "Backend not available: ${currentBackend.displayName}"
            )
        }

        val imuDataSet = loadImu(session.imuFile)
            ?: return@withContext VioResult(
                status = VioStatus.FAILED,
                message = "Failed to parse IMU data"
            )

        val frameTimestamps = VioSyncBuilder().parseFrameTimestamps(session.frameTimestampsFile)
        if (frameTimestamps.isEmpty()) {
            return@withContext VioResult(
                status = VioStatus.FAILED,
                message = "No frame timestamps found"
            )
        }

        val syncPackets = VioSyncBuilder().buildSyncPackets(frameTimestamps, imuDataSet)
        val outputDir = File(session.sessionDir, "results")

        Log.d(TAG, "Running ${currentBackend.displayName} on ${syncPackets.size} frames")
        currentBackend.run(session, syncPackets, outputDir)
    }

    /**
     * Run VIO on cloud regardless of settings (for explicit cloud button)
     */
    suspend fun runOnCloud(session: VioSession, sfmUrl: String): VioResult = withContext(Dispatchers.IO) {
        val cloudBackend = CloudSfmVioBackend(sfmUrl)
        
        if (!cloudBackend.isAvailable()) {
            return@withContext VioResult(
                status = VioStatus.NOT_IMPLEMENTED,
                message = "Cloud SFM service not reachable"
            )
        }

        val imuDataSet = loadImu(session.imuFile)
            ?: return@withContext VioResult(
                status = VioStatus.FAILED,
                message = "Failed to parse IMU data"
            )

        val frameTimestamps = VioSyncBuilder().parseFrameTimestamps(session.frameTimestampsFile)
        if (frameTimestamps.isEmpty()) {
            return@withContext VioResult(
                status = VioStatus.FAILED,
                message = "No frame timestamps found"
            )
        }

        val syncPackets = VioSyncBuilder().buildSyncPackets(frameTimestamps, imuDataSet)
        val outputDir = File(session.sessionDir, "results")

        Log.d(TAG, "Running Cloud SFM on ${syncPackets.size} frames")
        cloudBackend.run(session, syncPackets, outputDir)
    }

    suspend fun checkDependencies(): VioDepsResult = withContext(Dispatchers.IO) {
        backend.checkDependencies()
    }

    fun getBackendInfo(): Pair<String, String> {
        val b = backend
        return b.displayName to b.version
    }

    private fun loadImu(file: File): ImuDataSet? {
        if (!file.exists()) return null
        val csvContent = file.readText()
        return ImuDataSet.fromCsvFile(csvContent)
    }
}
