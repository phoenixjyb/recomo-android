package com.recomo.remotecontrol.camviewer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Connection state for video streaming
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Video frame data from WebSocket
 */
enum class VideoFrameFormat {
    H26X,
    JPEG
}

data class VideoFrame(
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val isKeyframe: Boolean = false,
    val format: VideoFrameFormat = VideoFrameFormat.H26X
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VideoFrame
        if (!data.contentEquals(other.data)) return false
        if (timestamp != other.timestamp) return false
        if (isKeyframe != other.isKeyframe) return false
        if (format != other.format) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isKeyframe.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }
}

/**
 * Telemetry data sent with video frames
 */
@Serializable
data class Telemetry(
    @SerialName("frame_number") val frameNumber: Int = 0,
    @SerialName("timestamp_ms") val timestampMs: Long = 0,
    @SerialName("fps") val fps: Float = 0f,
    @SerialName("bitrate_kbps") val bitrateKbps: Int = 0,
    @SerialName("resolution") val resolution: Resolution? = null,
    @SerialName("codec") val codec: String = "",
    @SerialName("rotation_deg") val rotationDeg: Int? = null,
    @SerialName("tracking_data") val trackingData: TrackingData? = null,
    @SerialName("camera_intrinsics") val cameraIntrinsics: CameraIntrinsics? = null
)

@Serializable
data class Resolution(
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int
)

@Serializable
data class TrackingData(
    @SerialName("bbox") val bbox: BoundingBox? = null,
    @SerialName("confidence") val confidence: Float = 0f,
    @SerialName("track_id") val trackId: Int? = null
)

@Serializable
data class BoundingBox(
    @SerialName("x") val x: Float,
    @SerialName("y") val y: Float,
    @SerialName("width") val width: Float,
    @SerialName("height") val height: Float
)

@Serializable
data class CameraIntrinsics(
    val width: Int,
    val height: Int,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    @SerialName("k") val k: List<Float> = emptyList(),
    @SerialName("p") val p: List<Float> = emptyList(),
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

/**
 * Camera control commands
 */
@Serializable
data class CameraCommand(
    @SerialName("command") val command: String,
    @SerialName("params") val params: Map<String, String> = emptyMap()
)

/**
 * Target selection coordinates
 */
@Serializable
data class TargetCoordinates(
    @SerialName("x") val x: Float,
    @SerialName("y") val y: Float,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tracking state for user-initiated object tracking
 */
enum class TrackingState {
    /** User is drawing the bounding box */
    DRAWING,
    /** Bounding box sent to Orin, waiting for object detection */
    PENDING,
    /** Object detected and being tracked */
    TRACKING,
    /** Tracking lost (object moved out of frame or detection failed) */
    LOST,
    /** No active tracking */
    IDLE
}

/**
 * Source of tracking computation.
 */
enum class TrackingMode {
    OFF,
    LOCAL,
    CAMCONTROL;

    companion object {
        fun fromName(name: String?): TrackingMode {
            return values().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CAMCONTROL
        }
    }
}

/**
 * Interactive tracking box state
 */
data class InteractiveTrackingBox(
    /** Unique tracking ID from server */
    val trackingId: String,
    /** Current bounding box (normalized 0.0-1.0) */
    val boundingBox: BoundingBox,
    /** Current tracking state */
    val state: TrackingState,
    /** Confidence score (0.0-1.0) if tracking */
    val confidence: Float = 0f,
    /** Timestamp when tracking was initiated */
    val initiatedAt: Long = System.currentTimeMillis(),
    /** Timestamp of last update */
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

/**
 * Tracking update message from Orin WebSocket
 */
@Serializable
data class TrackingUpdate(
    @SerialName("tracking_id") val trackingId: String,
    @SerialName("state") val state: String, // "pending", "tracking", "lost"
    @SerialName("bbox") val bbox: BoundingBox? = null,
    @SerialName("confidence") val confidence: Float = 0f,
    @SerialName("timestamp") val timestamp: Long = 0
)

/**
 * Network preset options
 * Supports two Orin devices: TzTek Orin (default) and AGX Orin.
 */
enum class NetworkPreset(
    val displayName: String,
    val phoneIp: String,
    val orinTztekIp: String,
    val orinAgxIp: String,
    val tabletIp: String
) {
    ZEROTIER(
        displayName = "ZeroTier",
        phoneIp = "192.168.100.156",
        orinTztekIp = "192.168.100.130",
        orinAgxIp = "192.168.100.150",
        tabletIp = "192.168.100.159"
    ),
    T8SPACE(
        displayName = "T8Space",
        phoneIp = "172.16.30.19",
        orinTztekIp = "172.16.31.34",
        orinAgxIp = "172.16.31.74",
        tabletIp = "172.16.31.147"
    ),
    RECOMO_LOCAL(
        displayName = "Recomo WiFi",
        phoneIp = "192.168.10.105",
        orinTztekIp = "192.168.1.106",
        orinAgxIp = "192.168.10.100",
        tabletIp = "192.168.1.109"
    ),
    DEBUG_MAC(
        displayName = "Debug (Mac)",
        phoneIp = "192.168.100.156",
        orinTztekIp = "192.168.100.188",
        orinAgxIp = "192.168.100.188",
        tabletIp = "192.168.100.159"
    ),
    CUSTOM(
        displayName = "Custom",
        phoneIp = "",
        orinTztekIp = "",
        orinAgxIp = "",
        tabletIp = ""
    );
    
    /**
     * Get the active Orin IP based on selection
     * @param useTztek true for TzTek Orin (default), false for AGX Orin
     */
    fun getOrinIp(useTztek: Boolean = true): String {
        return if (useTztek) orinTztekIp else orinAgxIp
    }

    fun getOrinIp(profile: RobotProfile, useTztek: Boolean = true): String {
        if (profile != RobotProfile.CUSTOM) {
            return profile.getOrinIp(this)
        }
        return getOrinIp(useTztek)
    }
    
    companion object {
        const val PHONE_WS_PORT = 9090
        const val ORIN_CAMERA_WS_PORT = 9091
        const val ORIN_TARGET_PORT = 8082
        const val ORIN_MEDIA_PORT = 8081
        const val WEBRTC_SIGNALING_PORT = 9092
        
        fun fromName(name: String): NetworkPreset {
            return values().find { it.name == name } ?: ZEROTIER
        }
    }

    private fun buildWsUrl(host: String, port: Int): String {
        if (host.isBlank()) return ""
        return "ws://$host:$port"
    }

    private fun buildHttpUrl(host: String, port: Int): String {
        if (host.isBlank()) return ""
        return "http://$host:$port"
    }
    
    fun getPhoneVideoUrl(): String = "ws://$phoneIp:$PHONE_WS_PORT"
    fun getPhoneControlUrl(): String = "ws://$phoneIp:$PHONE_WS_PORT/control"
    fun getOrinCameraUrl(profile: RobotProfile? = null, useTztek: Boolean = true): String =
        buildWsUrl(if (profile != null) getOrinIp(profile, useTztek) else getOrinIp(useTztek), ORIN_CAMERA_WS_PORT)
    fun getOrinTargetUrl(profile: RobotProfile? = null, useTztek: Boolean = true): String =
        buildHttpUrl(if (profile != null) getOrinIp(profile, useTztek) else getOrinIp(useTztek), ORIN_TARGET_PORT)
    fun getOrinMediaUrl(profile: RobotProfile? = null, useTztek: Boolean = true): String =
        buildHttpUrl(if (profile != null) getOrinIp(profile, useTztek) else getOrinIp(useTztek), ORIN_MEDIA_PORT)
    fun getOrinTrackingUrl(profile: RobotProfile? = null, useTztek: Boolean = true): String =
        buildWsUrl(if (profile != null) getOrinIp(profile, useTztek) else getOrinIp(useTztek), 8084)
    fun getSignalingUrl(profile: RobotProfile? = null, useTztek: Boolean = true): String =
        buildWsUrl(if (profile != null) getOrinIp(profile, useTztek) else getOrinIp(useTztek), 9077)
    fun getWebRTCSignalingUrl(profile: RobotProfile? = null, useTztek: Boolean = true): String =
        buildWsUrl(if (profile != null) getOrinIp(profile, useTztek) else getOrinIp(useTztek), WEBRTC_SIGNALING_PORT)
}

/**
 * Robot profile options
 */
enum class RobotProfile(
    val displayName: String,
    val robotName: String,
    val unitId: String?,
    val productFamily: String?,
    val hardwareVariant: String?,
    val useTztekOrin: Boolean?,
    private val zeroTierOrinIp: String = "",
    private val t8spaceOrinIp: String = "",
    private val localOrinIp: String = "",
    private val debugMacOrinIp: String = ""
) {
    /** No robot selected — all IPs blank, gateway will not connect. */
    NONE(
        displayName = "Select robot\u2026",
        robotName = "",
        unitId = null,
        productFamily = null,
        hardwareVariant = null,
        useTztekOrin = false
    ),
    RECOMO_DEMO1(
        displayName = "recomoDemo1",
        robotName = "recomoDemo1",
        unitId = null,
        productFamily = null,
        hardwareVariant = null,
        useTztekOrin = true
    ),
    // Legacy enum kept for backward compatibility with saved preferences.
    // Maps to dogwood-01 (Orin 150) — the primary dev robot.
    // Hidden from selectableProfiles(); fromName() migrates to RECOMO_PROTO1_DOGWOOD01.
    @Deprecated("Use RECOMO_PROTO1_DOGWOOD01", replaceWith = ReplaceWith("RECOMO_PROTO1_DOGWOOD01"))
    RECOMO_PROTO1(
        displayName = "dogwood-01 (Orin 150)",
        robotName = "recomoProto1-dogwood01",
        unitId = "150-d01",
        productFamily = "recomoProto1",
        hardwareVariant = "dogwood",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.150",
        t8spaceOrinIp = "172.16.31.74",
        localOrinIp = "192.168.10.100",
        debugMacOrinIp = "192.168.100.188"
    ),
    RECOMO_PROTO1_DOGWOOD01(
        displayName = "dogwood-01 (Orin 150)",
        robotName = "recomoProto1-dogwood01",
        unitId = "150-d01",
        productFamily = "recomoProto1",
        hardwareVariant = "dogwood",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.150",
        t8spaceOrinIp = "172.16.31.74",
        localOrinIp = "192.168.10.100",
        debugMacOrinIp = "192.168.100.188"
    ),
    RECOMO_PROTO1_CEDAR01(
        displayName = "cedar-01 (Orin 162)",
        robotName = "recomoProto1-cedar01",
        unitId = "162-c01",
        productFamily = "recomoProto1",
        hardwareVariant = "cedar",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.162",
        t8spaceOrinIp = "172.16.30.173",
        localOrinIp = "192.168.10.100"
    ),
    RECOMO_PROTO1_ELM01(
        displayName = "elm-01 (Orin 161)",
        robotName = "recomoProto1-elm01",
        unitId = "161-e01",
        productFamily = "recomoProto1",
        hardwareVariant = "elm",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.161",
        t8spaceOrinIp = "172.16.30.126",
        localOrinIp = "192.168.10.100"
    ),
    RECOMO_PROTO1_TEST01(
        displayName = "test-01 (Orin 160)",
        robotName = "recomoProto1-test01",
        unitId = "160-t01",
        productFamily = "recomoProto1",
        hardwareVariant = "test",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.160",
        t8spaceOrinIp = "172.16.31.24",
        localOrinIp = "192.168.10.100"
    ),
    RECOMO_PROTO1_TEST02(
        displayName = "test-02 (Orin 162)",
        robotName = "recomoProto1-test02",
        unitId = "162-t02",
        productFamily = "recomoProto1",
        hardwareVariant = "test",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.162",
        localOrinIp = "192.168.10.100"
    ),
    VIRTUAL_SIM(
        displayName = "🤖 Virtual Sim (x86)",
        robotName = "virtualBot",
        unitId = "sim-20",
        productFamily = "recomoProto1",  // same URDF/kinematics
        hardwareVariant = "sim",
        useTztekOrin = false,
        zeroTierOrinIp = "192.168.100.100",  // x86 machine via ZeroTier
        t8spaceOrinIp = "172.16.31.22"       // T8Space LAN — lower latency
    ),
    CUSTOM(
        displayName = "Custom",
        robotName = "recomo",
        unitId = null,
        productFamily = null,
        hardwareVariant = null,
        useTztekOrin = null
    );

    fun isProto1Family(): Boolean = productFamily == "recomoProto1"

    fun getOrinIp(preset: NetworkPreset): String {
        return when (preset) {
            NetworkPreset.ZEROTIER -> zeroTierOrinIp
            NetworkPreset.T8SPACE -> t8spaceOrinIp
            NetworkPreset.RECOMO_LOCAL -> localOrinIp
            NetworkPreset.DEBUG_MAC -> debugMacOrinIp
            NetworkPreset.CUSTOM -> ""
        }
    }

    fun getRobotWifiSsid(): String? = unitId?.let { "recomo-$it" }

    companion object {
        fun selectableProfiles(): List<RobotProfile> {
            return listOf(NONE, RECOMO_PROTO1_DOGWOOD01, RECOMO_PROTO1_TEST01, RECOMO_PROTO1_TEST02, RECOMO_PROTO1_CEDAR01, RECOMO_PROTO1_ELM01, VIRTUAL_SIM, CUSTOM)
        }

        @Suppress("UNUSED_PARAMETER")
        fun fromName(name: String?, _fallbackUseTztek: Boolean = true): RobotProfile {
            val match = entries.find { it.name == name }
            if (match != null) {
                // Migrate legacy enum names to new fleet identity
                return when (match) {
                    RECOMO_DEMO1 -> RECOMO_PROTO1_DOGWOOD01
                    RECOMO_PROTO1 -> RECOMO_PROTO1_DOGWOOD01  // old cedar01@150 → now dogwood01@150
                    else -> match
                }
            }
            // Also match by legacy saved name string
            return when (name) {
                "RECOMO_PROTO1" -> RECOMO_PROTO1_DOGWOOD01
                "RECOMO_PROTO1_DOGWOOD02" -> RECOMO_PROTO1_CEDAR01  // dogwood02@162 → cedar01@162
                else -> NONE
            }
        }
    }
}

/**
 * Settings/preferences data
 */
data class AppSettings(
    val networkPreset: NetworkPreset = NetworkPreset.RECOMO_LOCAL,
    val useTztekOrin: Boolean = false,  // false for AGX Orin on recomoProto1
    val robotProfile: RobotProfile = RobotProfile.NONE,
    val cameraUrl: String = "",
    val orinCameraUrl: String = "",
    val orinTargetUrl: String = "",
    val orinMediaUrl: String = "",
    val orinTrackingUrl: String = "",
    val phoneControlHost: String = "",
    val developerModeEnabled: Boolean = false,
    val serviceControlPinEnabled: Boolean = false, // default off: no PIN header
    val serviceControlPin: String = "", // PIN for Orin service control (empty = no PIN)
    val useWebRTC: Boolean = false,
    val signalingUrl: String = "",
    val webrtcSignalingUrl: String = "",
    val trackingMode: TrackingMode = TrackingMode.CAMCONTROL,
    val viewOnly: Boolean = false,
    val videoSource: VideoSource = VideoSource.WEBSOCKET,  // Video input source
    val trackingTargetWidth: Int = 720,
    val trackingTargetHeight: Int = 480
) {
    companion object {
        fun fromPreset(
            preset: NetworkPreset,
            useTztekOrin: Boolean = false,
            robotProfile: RobotProfile = RobotProfile.NONE,
            developerModeEnabled: Boolean = false,
            serviceControlPinEnabled: Boolean = false,
            serviceControlPin: String = "",
            viewOnly: Boolean = false,
            videoSource: VideoSource = VideoSource.WEBSOCKET,
            trackingTargetWidth: Int = 720,
            trackingTargetHeight: Int = 480
        ): AppSettings {
            val resolvedUseTztek = robotProfile.useTztekOrin ?: useTztekOrin
            return AppSettings(
                networkPreset = preset,
                useTztekOrin = resolvedUseTztek,
                robotProfile = robotProfile,
                cameraUrl = preset.getPhoneVideoUrl(),
                orinCameraUrl = preset.getOrinCameraUrl(robotProfile, resolvedUseTztek),
                orinTargetUrl = preset.getOrinTargetUrl(robotProfile, resolvedUseTztek),
                orinMediaUrl = preset.getOrinMediaUrl(robotProfile, resolvedUseTztek),
                orinTrackingUrl = preset.getOrinTrackingUrl(robotProfile, resolvedUseTztek),
                phoneControlHost = preset.phoneIp,
                developerModeEnabled = developerModeEnabled,
                serviceControlPinEnabled = serviceControlPinEnabled,
                serviceControlPin = serviceControlPin,
                useWebRTC = false,
                signalingUrl = preset.getSignalingUrl(robotProfile, resolvedUseTztek),
                webrtcSignalingUrl = preset.getWebRTCSignalingUrl(robotProfile, resolvedUseTztek),
                trackingMode = TrackingMode.CAMCONTROL,
                viewOnly = viewOnly,
                videoSource = videoSource,
                trackingTargetWidth = trackingTargetWidth,
                trackingTargetHeight = trackingTargetHeight
            )
        }
    }
}
