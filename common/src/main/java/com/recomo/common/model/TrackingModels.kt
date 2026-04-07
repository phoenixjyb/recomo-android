package com.recomo.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Target ROI sent to Orin for subject tracking.
 */
@Serializable
data class TargetRoi(
    @SerialName("x_offset") val xOffset: Int,
    @SerialName("y_offset") val yOffset: Int,
    val width: Int,
    val height: Int
)

/**
 * Bounding box in tracking messages (integer pixel coordinates).
 * Separate from camviewer BoundingBox which uses float normalized coords.
 */
@Serializable
data class TrackingBoundingBox(
    @SerialName("x_offset") val xOffset: Int,
    @SerialName("y_offset") val yOffset: Int,
    val width: Int,
    val height: Int
)

/**
 * Subject tracking feedback from Orin.
 */
@Serializable
data class SubjectTracking(
    @SerialName("tracking_id") val trackingId: String,
    val state: String,
    val bbox: TrackingBoundingBox,
    val confidence: Float
)
