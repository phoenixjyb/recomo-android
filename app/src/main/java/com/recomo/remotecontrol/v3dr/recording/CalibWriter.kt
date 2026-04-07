package com.recomo.remotecontrol.v3dr.recording

import android.os.SystemClock
import android.util.Log
import com.recomo.remotecontrol.v3dr.data.model.CameraIntrinsics
import com.recomo.remotecontrol.v3dr.data.model.CropRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Writes VIO calibration snapshot (calib.json) for a session.
 */
class CalibWriter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private var wrote = false

    companion object {
        private const val TAG = "CalibWriter"
    }

    @Serializable
    data class VioCalib(
        @SerialName("schema_version") val schemaVersion: Int = 1,
        @SerialName("created_at_ns") val createdAtNs: Long,
        val camera: CameraCalib,
        val imu: ImuCalib? = null,
        val notes: String? = null
    )

    @Serializable
    data class CameraCalib(
        val width: Int,
        val height: Int,
        val fx: Float? = null,
        val fy: Float? = null,
        val cx: Float? = null,
        val cy: Float? = null,
        @SerialName("k") val k: List<Float> = emptyList(),
        @SerialName("p") val p: List<Float> = emptyList(),
        @SerialName("dist_model") val distModel: String = "radtan",
        @SerialName("zoom_ratio") val zoomRatio: Float? = null,
        val crop: CropRegion? = null,
        @SerialName("sensor_orientation") val sensorOrientation: Int? = null,
        val source: String = "camera2"
    )

    @Serializable
    data class ImuCalib(
        @SerialName("noise_gyr") val noiseGyro: List<Float>? = null,
        @SerialName("noise_acc") val noiseAccel: List<Float>? = null,
        @SerialName("bias_gyr") val biasGyro: List<Float>? = null,
        @SerialName("bias_acc") val biasAccel: List<Float>? = null,
        @SerialName("rate_hz") val rateHz: Int? = null
    )

    fun writeIfNeeded(
        file: File,
        intrinsics: CameraIntrinsics?,
        fallbackWidth: Int,
        fallbackHeight: Int,
        sensorOrientation: Int?
    ) {
        if (wrote) return
        val camera = if (intrinsics != null) {
            CameraCalib(
                width = intrinsics.width,
                height = intrinsics.height,
                fx = intrinsics.fx,
                fy = intrinsics.fy,
                cx = intrinsics.cx,
                cy = intrinsics.cy,
                k = intrinsics.k,
                p = intrinsics.p,
                zoomRatio = intrinsics.zoomRatio,
                crop = intrinsics.crop,
                sensorOrientation = sensorOrientation
            )
        } else {
            CameraCalib(
                width = fallbackWidth,
                height = fallbackHeight,
                fx = null,
                fy = null,
                cx = null,
                cy = null,
                k = emptyList(),
                p = emptyList(),
                zoomRatio = null,
                crop = null,
                sensorOrientation = sensorOrientation
            )
        }

        val payload = VioCalib(
            createdAtNs = SystemClock.elapsedRealtimeNanos(),
            camera = camera,
            notes = if (intrinsics == null) "intrinsics_unavailable" else null
        )

        try {
            file.writeText(json.encodeToString(payload))
            wrote = true
            Log.i(TAG, "Calibration written: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write calib file", e)
        }
    }

    fun ensureWritten(
        file: File,
        fallbackWidth: Int,
        fallbackHeight: Int,
        sensorOrientation: Int?
    ) {
        if (!wrote) {
            writeIfNeeded(file, null, fallbackWidth, fallbackHeight, sensorOrientation)
        }
    }
}
