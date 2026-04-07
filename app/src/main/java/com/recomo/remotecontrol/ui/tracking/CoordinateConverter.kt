package com.recomo.remotecontrol.ui.tracking

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.recomo.remotecontrol.config.TrackingConfig
import com.recomo.remotecontrol.tracking.TargetRoi
import kotlin.math.max
import kotlin.math.min

/**
 * Helper functions for coordinate conversion between screen space and target resolution.
 */
object CoordinateConverter {
    
    /**
     * Normalize screen coordinates to 0.0-1.0 range relative to video content area.
     * 
     * @param screenStart Drag start position in screen pixels
     * @param screenEnd Drag end position in screen pixels
     * @param contentArea Video content area (accounts for letterboxing)
     * @return Normalized rect with values in 0.0-1.0 range
     */
    fun normalizeToContent(
        screenStart: Offset,
        screenEnd: Offset,
        contentArea: Rect
    ): Rect {
        // Clamp to content area
        val startClamped = Offset(
            x = screenStart.x.coerceIn(contentArea.left, contentArea.right),
            y = screenStart.y.coerceIn(contentArea.top, contentArea.bottom)
        )
        val endClamped = Offset(
            x = screenEnd.x.coerceIn(contentArea.left, contentArea.right),
            y = screenEnd.y.coerceIn(contentArea.top, contentArea.bottom)
        )
        
        // Normalize to 0.0-1.0 relative to content
        val left = ((min(startClamped.x, endClamped.x) - contentArea.left) / contentArea.width).coerceIn(0f, 1f)
        val top = ((min(startClamped.y, endClamped.y) - contentArea.top) / contentArea.height).coerceIn(0f, 1f)
        val right = ((max(startClamped.x, endClamped.x) - contentArea.left) / contentArea.width).coerceIn(0f, 1f)
        val bottom = ((max(startClamped.y, endClamped.y) - contentArea.top) / contentArea.height).coerceIn(0f, 1f)
        
        return Rect(left, top, right, bottom)
    }
    
    /**
     * Convert normalized rect to target resolution (e.g., 720x480) for ROS2 message.
     * 
     * @param normalizedRect Rect with values in 0.0-1.0 range
     * @return TargetRoi with pixel coordinates in target resolution
     */
    fun normalizedToTargetRoi(
        normalizedRect: Rect,
        targetWidth: Int = TrackingConfig.TARGET_WIDTH,
        targetHeight: Int = TrackingConfig.TARGET_HEIGHT
    ): TargetRoi {
        val safeTargetWidth = targetWidth.coerceAtLeast(2)
        val safeTargetHeight = targetHeight.coerceAtLeast(2)
        val xOffset = (normalizedRect.left * safeTargetWidth).toInt().coerceIn(0, safeTargetWidth - 1)
        val yOffset = (normalizedRect.top * safeTargetHeight).toInt().coerceIn(0, safeTargetHeight - 1)
        val widthRaw = ((normalizedRect.right - normalizedRect.left) * safeTargetWidth).toInt()
        val heightRaw = ((normalizedRect.bottom - normalizedRect.top) * safeTargetHeight).toInt()
        val width = widthRaw.coerceIn(1, safeTargetWidth - xOffset)
        val height = heightRaw.coerceIn(1, safeTargetHeight - yOffset)
        
        return TargetRoi(
            xOffset = xOffset,
            yOffset = yOffset,
            width = width,
            height = height
        )
    }
    
    /**
     * Convert target ROI (in target resolution) back to screen coordinates for display.
     * 
     * @param roi TargetRoi with pixel coordinates in target resolution
     * @param contentArea Video content area in screen pixels
     * @return Rect in screen coordinates
     */
    fun targetRoiToScreen(
        roi: TargetRoi,
        contentArea: Rect,
        targetWidth: Int = TrackingConfig.TARGET_WIDTH,
        targetHeight: Int = TrackingConfig.TARGET_HEIGHT
    ): Rect {
        // First normalize from target resolution to 0.0-1.0
        val normX = roi.xOffset.toFloat() / targetWidth
        val normY = roi.yOffset.toFloat() / targetHeight
        val normWidth = roi.width.toFloat() / targetWidth
        val normHeight = roi.height.toFloat() / targetHeight
        
        // Then scale to screen coordinates
        return Rect(
            left = contentArea.left + normX * contentArea.width,
            top = contentArea.top + normY * contentArea.height,
            right = contentArea.left + (normX + normWidth) * contentArea.width,
            bottom = contentArea.top + (normY + normHeight) * contentArea.height
        )
    }
    
    /**
     * Normalize a single point (for tap gestures).
     */
    fun normalizePoint(point: Offset, contentArea: Rect): Offset {
        val clamped = Offset(
            x = point.x.coerceIn(contentArea.left, contentArea.right),
            y = point.y.coerceIn(contentArea.top, contentArea.bottom)
        )
        
        return Offset(
            x = ((clamped.x - contentArea.left) / contentArea.width).coerceIn(0f, 1f),
            y = ((clamped.y - contentArea.top) / contentArea.height).coerceIn(0f, 1f)
        )
    }
    
    /**
     * Create screen rect from two points.
     */
    fun createScreenRect(start: Offset, end: Offset): Rect {
        return Rect(
            left = min(start.x, end.x),
            top = min(start.y, end.y),
            right = max(start.x, end.x),
            bottom = max(start.y, end.y)
        )
    }
}
