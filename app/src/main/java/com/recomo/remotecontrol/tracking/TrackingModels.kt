package com.recomo.remotecontrol.tracking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Target ROI to send to Orin for tracking.
 * Coordinates are in target resolution (e.g., 720x480).
 */
@Serializable
data class TargetRoi(
    @SerialName("x_offset") val xOffset: Int,
    @SerialName("y_offset") val yOffset: Int,
    val width: Int,
    val height: Int
)

/**
 * Bounding box within a tracking message.
 */
@Serializable
data class BoundingBox(
    @SerialName("x_offset") val xOffset: Int,
    @SerialName("y_offset") val yOffset: Int,
    val width: Int,
    val height: Int
)

/**
 * Subject tracking feedback from Orin tracker.
 * Coordinates are in target resolution (e.g., 720x480).
 */
@Serializable
data class SubjectTracking(
    @SerialName("tracking_id") val trackingId: String,
    val state: String, // "pending", "tracking", "lost"
    val bbox: BoundingBox,
    val confidence: Float
)

/**
 * WebSocket message wrapper for target ROI.
 */
@Serializable
data class TargetRoiMessage(
    val type: String = "TargetRoi",
    @SerialName("x_offset") val xOffset: Int,
    @SerialName("y_offset") val yOffset: Int,
    val width: Int,
    val height: Int
)

/**
 * WebSocket message wrapper for subject tracking.
 */
@Serializable
data class SubjectTrackingMessage(
    val type: String,
    @SerialName("tracking_id") val trackingId: String,
    val state: String,
    val bbox: BoundingBox,
    val confidence: Float
)
