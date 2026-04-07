package com.recomo.remotecontrol.ui.tracking

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

/**
 * Tracking state for bounding box visualization.
 */
enum class TrackingState {
    DRAWING,    // User is actively drawing
    PENDING,    // Waiting for tracker to initialize
    TRACKING,   // Actively tracking target
    LOST,       // Lost track of target
    IDLE        // No tracking active
}

/**
 * Overlay for user-drawn bounding box (during drag).
 */
@Composable
fun BoundingBoxOverlay(
    box: Rect,
    state: TrackingState = TrackingState.DRAWING
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = when (state) {
            TrackingState.DRAWING -> Color(0xFF4CAF50) // Green
            TrackingState.PENDING -> Color(0xFFFFEB3B) // Yellow
            TrackingState.TRACKING -> Color(0xFF00BCD4) // Cyan
            TrackingState.LOST -> Color(0xFFF44336) // Red
            TrackingState.IDLE -> Color(0xFF9E9E9E) // Gray
        }
        
        val strokeWidth = 3.dp.toPx()
        val fillColor = color.copy(alpha = 0.2f)
        
        // Draw filled rectangle
        drawRect(
            color = fillColor,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height)
        )
        
        // Draw border
        drawRect(
            color = color,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height),
            style = Stroke(width = strokeWidth)
        )
        
        // Draw corner markers
        val markerSize = 20.dp.toPx()
        val markerWidth = strokeWidth * 1.5f
        
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
}

/**
 * Animated overlay for tracked bounding box (from tracker feedback).
 */
@Composable
fun TrackedBoundingBox(
    box: Rect,
    state: TrackingState
) {
    // Animate box position for smooth transitions
    val animatedLeft by animateFloatAsState(
        targetValue = box.left,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "box_left"
    )
    val animatedTop by animateFloatAsState(
        targetValue = box.top,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "box_top"
    )
    val animatedRight by animateFloatAsState(
        targetValue = box.right,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "box_right"
    )
    val animatedBottom by animateFloatAsState(
        targetValue = box.bottom,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "box_bottom"
    )
    
    val animatedBox = Rect(
        left = animatedLeft,
        top = animatedTop,
        right = animatedRight,
        bottom = animatedBottom
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val color = when (state) {
            TrackingState.PENDING -> Color(0xFFFFEB3B) // Yellow
            TrackingState.TRACKING -> Color(0xFF00BCD4) // Cyan
            TrackingState.LOST -> Color(0xFFF44336) // Red
            else -> Color(0xFF9E9E9E) // Gray
        }
        
        val strokeWidth = 4.dp.toPx()
        val fillAlpha = if (state == TrackingState.TRACKING) 0.15f else 0.25f
        val fillColor = color.copy(alpha = fillAlpha)
        
        // Draw filled rectangle
        drawRect(
            color = fillColor,
            topLeft = Offset(animatedBox.left, animatedBox.top),
            size = Size(animatedBox.width, animatedBox.height)
        )
        
        // Draw border (dashed for pending, solid otherwise)
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
        
        // Draw corner markers
        val markerSize = 25.dp.toPx()
        val markerWidth = strokeWidth * 1.8f
        
        // Top-left
        drawLine(color, Offset(animatedBox.left, animatedBox.top), Offset(animatedBox.left + markerSize, animatedBox.top), markerWidth)
        drawLine(color, Offset(animatedBox.left, animatedBox.top), Offset(animatedBox.left, animatedBox.top + markerSize), markerWidth)
        
        // Top-right
        drawLine(color, Offset(animatedBox.right, animatedBox.top), Offset(animatedBox.right - markerSize, animatedBox.top), markerWidth)
        drawLine(color, Offset(animatedBox.right, animatedBox.top), Offset(animatedBox.right, animatedBox.top + markerSize), markerWidth)
        
        // Bottom-left
        drawLine(color, Offset(animatedBox.left, animatedBox.bottom), Offset(animatedBox.left + markerSize, animatedBox.bottom), markerWidth)
        drawLine(color, Offset(animatedBox.left, animatedBox.bottom), Offset(animatedBox.left, animatedBox.bottom - markerSize), markerWidth)
        
        // Bottom-right
        drawLine(color, Offset(animatedBox.right, animatedBox.bottom), Offset(animatedBox.right - markerSize, animatedBox.bottom), markerWidth)
        drawLine(color, Offset(animatedBox.right, animatedBox.bottom), Offset(animatedBox.right, animatedBox.bottom - markerSize), markerWidth)
    }
}
