package com.recomo.common.model

/**
 * Available video input sources for the app.
 */
enum class VideoSource {
    /**
     * H.265 video stream from phone camera via WebSocket (bypass Orin, wireless video converter).
     */
    WS_PHONE,

    /**
     * WebSocket JPEG stream from Orin camera bridge (port 9091).
     */
    WS_ORIN,

    /**
     * WebRTC stream from Orin (low-latency, signaling server required).
     */
    WEBRTC_ORIN,

    /**
     * Direct video capture from HDMI-to-USB capture device (UVC).
     */
    HDMI_USB;

    /** Legacy aliases for backwards-compatible DataStore reads. */
    companion object {
        // Keep old names working so persisted settings don't break.
        @Suppress("unused")
        val WEBSOCKET = WS_PHONE
        @Suppress("unused")
        val WEBSOCKET_ORIN = WS_ORIN

        fun fromName(name: String?): VideoSource {
            return when (name?.uppercase()) {
                "HDMI_USB" -> HDMI_USB
                "WEBRTC_ORIN" -> WEBRTC_ORIN
                "WEBSOCKET_ORIN", "WS_ORIN" -> WS_ORIN
                "WEBSOCKET", "WS_PHONE" -> WS_PHONE
                else -> WS_ORIN  // default to Orin WS
            }
        }
    }
}
