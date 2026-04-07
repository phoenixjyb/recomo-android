package com.recomo.remotecontrol.camviewer.tracking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Tracking status message compatible with ROS2 /recomo/tracking topic format.
 * This data class is sent via WebSocket to all connected clients.
 */
@Serializable
data class TrackingStatus(
    val type: String = "tracking",
    
    @SerialName("tracking_id")
    val trackingId: String,
    
    val state: TrackingState,
    
    val bbox: TrackingBoundingBox,
    
    val confidence: Float,
    
    @SerialName("timestamp_ms")
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
enum class TrackingState {
    @SerialName("tracking")
    TRACKING,
    
    @SerialName("lost")
    LOST,
    
    @SerialName("pending")
    PENDING
}

@Serializable
data class TrackingBoundingBox(
    @SerialName("x_offset")
    val xOffset: Float,
    
    @SerialName("y_offset")
    val yOffset: Float,
    
    val width: Float,
    
    val height: Float
) {
    companion object {
        val EMPTY = TrackingBoundingBox(0f, 0f, 0f, 0f)
    }
}

/**
 * Target ROI (Region of Interest) command for initializing tracking.
 * Compatible with ROS2 sensor_msgs/msg/RegionOfInterest format.
 */
@Serializable
data class TargetRoi(
    @SerialName("x_offset")
    val xOffset: Int,
    
    @SerialName("y_offset")
    val yOffset: Int,
    
    val width: Int,
    
    val height: Int
) {
    fun isEmpty(): Boolean = width <= 0 || height <= 0
    
    fun toRect(): android.graphics.RectF {
        return android.graphics.RectF(
            xOffset.toFloat(),
            yOffset.toFloat(),
            (xOffset + width).toFloat(),
            (yOffset + height).toFloat()
        )
    }
}
