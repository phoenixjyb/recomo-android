package com.recomo.remotecontrol.config

/**
 * Configuration for bounding box tracking and coordinate conversion.
 */
object TrackingConfig {
    /**
     * Target resolution for ROS2 target_roi messages.
     * Coordinates will be scaled to this resolution before sending to Orin.
     */
    const val TARGET_WIDTH = 720
    const val TARGET_HEIGHT = 480
    
    /**
     * Minimum drag distance in pixels to distinguish drag from tap.
     */
    const val MIN_DRAG_THRESHOLD_PX = 10
    
    /**
     * Tracking box visual configuration.
     */
    object Visual {
        const val STROKE_WIDTH_DP = 3f
        const val CORNER_MARKER_SIZE_DP = 12f
        
        // Alpha values for box fill (0.0 - 1.0)
        const val FILL_ALPHA_PENDING = 0.15f
        const val FILL_ALPHA_TRACKING = 0.20f
        const val FILL_ALPHA_LOST = 0.25f
        const val FILL_ALPHA_USER_DRAWING = 0.20f
    }
}
