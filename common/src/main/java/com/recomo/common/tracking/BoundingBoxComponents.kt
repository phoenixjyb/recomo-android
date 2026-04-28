package com.recomo.common.tracking

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.recomo.common.model.TrackingState

/**
 * Overlay for user-drawn bounding box (during drag).
 */
@Composable
fun BoundingBoxOverlay(
    box: Rect,
    state: TrackingState = TrackingState.DRAWING
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = trackingStateColor(state)
        val strokeWidth = 3.dp.toPx()
        val fillColor = color.copy(alpha = 0.2f)

        drawRect(
            color = fillColor,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height)
        )

        drawRect(
            color = color,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height),
            style = Stroke(width = strokeWidth)
        )

        drawCornerMarkers(box, color, 20.dp.toPx(), strokeWidth * 1.5f)
    }
}

/**
 * Animated overlay for tracked bounding box (from tracker feedback).
 */
@Composable
fun TrackedBoundingBox(
    box: Rect,
    state: TrackingState
) {
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val animatedLeft by animateFloatAsState(box.left, springSpec, label = "box_left")
    val animatedTop by animateFloatAsState(box.top, springSpec, label = "box_top")
    val animatedRight by animateFloatAsState(box.right, springSpec, label = "box_right")
    val animatedBottom by animateFloatAsState(box.bottom, springSpec, label = "box_bottom")

    val animatedBox = Rect(animatedLeft, animatedTop, animatedRight, animatedBottom)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = trackingStateColor(state)
        val strokeWidth = 4.dp.toPx()
        val fillAlpha = if (state == TrackingState.TRACKING) 0.15f else 0.25f

        drawRect(
            color = color.copy(alpha = fillAlpha),
            topLeft = Offset(animatedBox.left, animatedBox.top),
            size = Size(animatedBox.width, animatedBox.height)
        )

        if (state == TrackingState.PENDING) {
            drawRect(
                color = color,
                topLeft = Offset(animatedBox.left, animatedBox.top),
                size = Size(animatedBox.width, animatedBox.height),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                )
            )
        } else {
            drawRect(
                color = color,
                topLeft = Offset(animatedBox.left, animatedBox.top),
                size = Size(animatedBox.width, animatedBox.height),
                style = Stroke(width = strokeWidth)
            )
        }

        drawCornerMarkers(animatedBox, color, 25.dp.toPx(), strokeWidth * 1.8f)
    }
}

private fun trackingStateColor(state: TrackingState): Color = when (state) {
    TrackingState.DRAWING -> Color(0xFF4CAF50)  // Green
    TrackingState.PENDING -> Color(0xFFFFEB3B)  // Yellow
    TrackingState.TRACKING -> Color(0xFF00BCD4) // Cyan
    TrackingState.LOST -> Color(0xFFF44336)     // Red
    TrackingState.IDLE -> Color(0xFF9E9E9E)     // Gray
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerMarkers(
    box: Rect,
    color: Color,
    markerSize: Float,
    markerWidth: Float
) {
    // Top-left
    drawLine(color, Offset(box.left, box.top), Offset(box.left + markerSize, box.top), markerWidth)
    drawLine(color, Offset(box.left, box.top), Offset(box.left, box.top + markerSize), markerWidth)
    // Top-right
    drawLine(color, Offset(box.right, box.top), Offset(box.right - markerSize, box.top), markerWidth)
    drawLine(color, Offset(box.right, box.top), Offset(box.right, box.top + markerSize), markerWidth)
    // Bottom-left
    drawLine(color, Offset(box.left, box.bottom), Offset(box.left + markerSize, box.bottom), markerWidth)
    drawLine(color, Offset(box.left, box.bottom), Offset(box.left, box.bottom - markerSize), markerWidth)
    // Bottom-right
    drawLine(color, Offset(box.right, box.bottom), Offset(box.right - markerSize, box.bottom), markerWidth)
    drawLine(color, Offset(box.right, box.bottom), Offset(box.right, box.bottom - markerSize), markerWidth)
}
