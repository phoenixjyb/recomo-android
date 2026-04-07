package com.recomo.remotecontrol.v3dr.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Telemetry(
    // Camera metadata
    val af: Int? = null,
    val ae: Int? = null,
    val iso: Int? = null,
    @SerialName("exp_ns")
    val expNs: Long? = null,
    val zoom: Float? = null,
    
    // Video stream metadata (for viewer)
    @SerialName("frame_number")
    val frameNumber: Int = 0,
    @SerialName("timestamp_ms")
    val timestampMs: Long = System.currentTimeMillis(),
    val fps: Float? = null,
    @SerialName("bitrate_kbps")
    val bitrateKbps: Int = 0,
    val resolution: Resolution? = null,
    val codec: String = "",
    @SerialName("tracking_data")
    val trackingData: TrackingData? = null,
    @SerialName("camera_intrinsics")
    val cameraIntrinsics: CameraIntrinsics? = null
)

@Serializable
data class ImuVector(
    val x: Float,
    val y: Float,
    val z: Float
)

@Serializable
data class ImuTelemetry(
    val type: String = "imu",
    @SerialName("timestamp_ns") val timestampNs: Long,
    @SerialName("wall_time_ms") val wallTimeMs: Long,
    val accel: ImuVector? = null,
    @SerialName("accel_accuracy") val accelAccuracy: Int? = null,
    val gyro: ImuVector? = null,
    @SerialName("gyro_accuracy") val gyroAccuracy: Int? = null,
    val frame: String = "phone_imu"
)

@Serializable
data class CameraIntrinsics(
    val width: Int,
    val height: Int,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    @SerialName("k") val k: List<Float>,
    @SerialName("p") val p: List<Float>,
    @SerialName("zoom_ratio") val zoomRatio: Float? = null,
    val crop: CropRegion? = null
)

@Serializable
data class CropRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class Resolution(
    val width: Int,
    val height: Int
)

@Serializable
data class TrackingData(
    val bbox: BoundingBox? = null,
    val confidence: Float = 0f,
    @SerialName("track_id")
    val trackId: Int? = null
)

@Serializable
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
