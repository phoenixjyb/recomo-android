package com.recomo.remotecontrol.v3dr.vio

import android.util.Log
import com.recomo.remotecontrol.v3dr.data.model.ImuDataSet
import com.recomo.remotecontrol.v3dr.data.model.ImuSample
import java.io.File

class VioSyncBuilder {
    companion object {
        private const val TAG = "VioSyncBuilder"
    }

    fun parseFrameTimestamps(file: File): List<FrameTimestamp> {
        if (!file.exists()) return emptyList()
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val startIndex = if (lines.first().startsWith("frame_idx")) 1 else 0
        val out = ArrayList<FrameTimestamp>(lines.size)
        for (i in startIndex until lines.size) {
            val parts = lines[i].split(',')
            if (parts.size < 2) continue
            val frameIdx = parts[0].trim().toLongOrNull() ?: continue
            val ts = parts[1].trim().toLongOrNull() ?: continue
            out.add(FrameTimestamp(frameIdx = frameIdx, timestampNs = ts))
        }
        return out
    }

    fun buildSyncPackets(
        frameTimestamps: List<FrameTimestamp>,
        imuDataSet: ImuDataSet
    ): List<SyncPacket> {
        if (frameTimestamps.isEmpty() || imuDataSet.samples.isEmpty()) return emptyList()

        val packets = ArrayList<SyncPacket>(frameTimestamps.size)
        val imuSamples = imuDataSet.samples
        var imuIdx = 0
        var prevTs = 0L  // Start from 0 to include all IMU before first frame

        for ((i, frame) in frameTimestamps.withIndex()) {
            val ts = frame.timestampNs
            val startTs = prevTs
            val segment = ArrayList<ImuSample>()
            while (imuIdx < imuSamples.size) {
                val sample = imuSamples[imuIdx]
                if (sample.timestampNs < startTs) {
                    imuIdx++
                    continue
                }
                if (sample.timestampNs > ts) break
                segment.add(sample)
                imuIdx++
            }
            packets.add(
                SyncPacket(
                    frameIdx = frame.frameIdx,
                    frameTimestampNs = ts,
                    imuSamples = segment
                )
            )
            prevTs = ts
        }

        Log.d(TAG, "Built ${packets.size} sync packets, first packet has ${packets.firstOrNull()?.imuSamples?.size ?: 0} IMU samples")
        return packets
    }
}
