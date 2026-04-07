package com.recomo.common.model

/**
 * Available video input sources for the app.
 */
enum class VideoSource {
    /**
     * Existing H.265 video stream from phone camera via WebSocket through Orin relay.
     * This is the default and original video input method.
     */
    WEBSOCKET,

    /**
     * WebSocket stream sourced from Orin UVC bridge (MJPEG/JPEG payloads).
     */
    WEBSOCKET_ORIN,
    
    /**
     * Direct video capture from HDMI-to-USB capture device (UVC).
     * Uses Camera2 API to access external camera device.
     */
    HDMI_USB;
    
    companion object {
        fun fromName(name: String?): VideoSource {
            return when (name?.uppercase()) {
                "HDMI_USB" -> HDMI_USB
                "WEBSOCKET_ORIN" -> WEBSOCKET_ORIN
                else -> WEBSOCKET
            }
        }
    }
}
