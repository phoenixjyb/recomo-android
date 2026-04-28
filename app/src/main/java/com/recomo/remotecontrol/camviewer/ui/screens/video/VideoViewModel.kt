package com.recomo.remotecontrol.camviewer.ui.screens.video

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaFormat
import android.os.SystemClock
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.camviewer.data.model.Telemetry
import com.recomo.remotecontrol.camviewer.data.model.TrackingMode
import com.recomo.remotecontrol.camviewer.data.model.TrackingUpdate
import com.recomo.remotecontrol.camviewer.data.repository.SettingsRepository
import com.recomo.remotecontrol.camviewer.network.CamControlWebSocketClient
import com.recomo.remotecontrol.camviewer.network.OrinTargetClient
import com.recomo.remotecontrol.camviewer.network.PhoneCameraClient
import com.recomo.remotecontrol.camviewer.network.TrackingFeedbackClient
import com.recomo.common.model.ConnectionState as CommonConnectionState
import com.recomo.common.network.WebRTCReceiver
import com.recomo.remotecontrol.camviewer.video.VideoDecoder
import com.recomo.remotecontrol.camviewer.video.VideoRecorder
import com.recomo.remotecontrol.camviewer.tracking.ObjectTracker
import com.recomo.remotecontrol.camviewer.tracking.TargetRoi
import com.recomo.remotecontrol.camviewer.tracking.TrackingStatus
import com.recomo.remotecontrol.camviewer.tracking.TrackingState as LocalTrackingState
import com.recomo.remotecontrol.camviewer.data.model.BoundingBox
import com.recomo.remotecontrol.camviewer.data.model.VideoFrame
import com.recomo.remotecontrol.camviewer.data.model.VideoFrameFormat
import com.recomo.remotecontrol.camviewer.data.model.VideoSource
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

private const val TAG = "VideoViewModel"
private const val TRACKING_DEFAULT_PORT = 8084
private const val FRAME_BUFFER_CAPACITY = 30
private const val FRAME_BUFFER_HIGH_WATERMARK = 24
private const val FRAME_BUFFER_LOW_WATERMARK = 10
private const val DEFAULT_TARGET_BITRATE_BPS = 3_000_000
private const val ADAPTIVE_BITRATE_MIN_BPS = 2_000_000
private const val TRACKING_TARGET_BITRATE_BPS = 2_000_000
private const val ADAPTIVE_BITRATE_STEP_BPS = 1_000_000
private const val ADAPTIVE_BITRATE_COOLDOWN_MS = 5_000L
private const val ADAPTIVE_BITRATE_HIGH_COUNT = 2
private const val ADAPTIVE_BITRATE_LOW_COUNT = 6
private const val WS_RECONNECT_INITIAL_MS = 1_500L
private const val WS_RECONNECT_MAX_MS = 15_000L
private const val STALL_FORCE_RECONNECT_MS = 5_000L
private const val ORIN_CAMERA_WS_PORT = 9091

enum class VideoTransport {
    WEBSOCKET,
    WEBRTC
}

private fun CommonConnectionState.toLocalConnectionState(): ConnectionState = when (this) {
    CommonConnectionState.Disconnected -> ConnectionState.Disconnected
    CommonConnectionState.Connecting -> ConnectionState.Connecting
    CommonConnectionState.Connected -> ConnectionState.Connected
    is CommonConnectionState.Error -> ConnectionState.Error(message)
}

/**
 * ViewModel for video streaming screen.
 * Manages WebSocket/WebRTC connection, video decoding, and telemetry display.
 */
@HiltViewModel
class VideoViewModel @Inject constructor(
    application: Application,
    private val webSocketClient: CamControlWebSocketClient,
    private val videoDecoder: VideoDecoder,
    private val videoRecorder: VideoRecorder,
    private val settingsRepository: SettingsRepository,
    private val orinTargetClient: OrinTargetClient,
    private val phoneCameraClient: PhoneCameraClient,
    private val trackingFeedbackClient: TrackingFeedbackClient,
    val videoSourceManager: com.recomo.remotecontrol.camviewer.video.VideoSourceManager
) : AndroidViewModel(application) {
    
    // Unified connection state for both WebSocket and WebRTC modes
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _wsReconnectInMs = MutableStateFlow<Long?>(null)
    val wsReconnectInMs: StateFlow<Long?> = _wsReconnectInMs.asStateFlow()
    val telemetry: StateFlow<Telemetry?> = webSocketClient.telemetry
    private val _latestTelemetry = MutableStateFlow<Telemetry?>(null)
    val latestTelemetry: StateFlow<Telemetry?> = _latestTelemetry.asStateFlow()

    // Measured stream stats derived from actual frame flow.
    // These are used to drive the on-screen HUD since phone-side telemetry can be stale or incorrect.
    private val _measuredFps = MutableStateFlow(0f)
    private val _measuredBitrateKbps = MutableStateFlow(0)
    
    private val _latency = MutableStateFlow(0L)
    val latency: StateFlow<Long> = _latency.asStateFlow()

    private val _e2eLatencyMs = MutableStateFlow<Long?>(null)
    val e2eLatencyMs: StateFlow<Long?> = _e2eLatencyMs.asStateFlow()

    private val _wsRttMs = MutableStateFlow<Long?>(null)
    val wsRttMs: StateFlow<Long?> = _wsRttMs.asStateFlow()

    private val _serverToClientOffsetMs = MutableStateFlow<Long?>(null)
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordingFile = MutableStateFlow<String?>(null)
    val recordingFile: StateFlow<String?> = _recordingFile.asStateFlow()
    
    // Video transport
    private var videoTransport: VideoTransport = VideoTransport.WEBSOCKET
    private var webrtcReceiver: WebRTCReceiver? = null
    private val webrtcInitMutex = Mutex()
    private var webrtcSignalingUrl: String? = null
    
    // Tracking feedback (filtered per tracking cycle)
    private val _activeTrackingId = MutableStateFlow<String?>(null)
    private val _trackingStartTimeMs = MutableStateFlow<Long?>(null)
    private val _trackingUpdates = MutableStateFlow<TrackingUpdate?>(null)
    val trackingUpdates: StateFlow<TrackingUpdate?> = _trackingUpdates.asStateFlow()
    val isTrackingConnected = trackingFeedbackClient.isConnected
    private val _trackingProcessing = MutableStateFlow(false)
    val trackingProcessing: StateFlow<Boolean> = _trackingProcessing.asStateFlow()
    private val _trackingNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val trackingNotice = _trackingNotice.asSharedFlow()
    private val trackingModeState: StateFlow<TrackingMode> = settingsRepository.settings
        .map { it.trackingMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TrackingMode.CAMCONTROL)
    private val viewOnlyState: StateFlow<Boolean> = settingsRepository.settings
        .map { it.viewOnly }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val trackingMode: StateFlow<TrackingMode> = trackingModeState
    val viewOnlyEnabled: StateFlow<Boolean> = viewOnlyState
    val isLocalTrackingActive: StateFlow<Boolean> = _activeTrackingId
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // Video source selection
    val videoSource: StateFlow<com.recomo.remotecontrol.camviewer.data.model.VideoSource> = videoSourceManager.currentSource
    val hdmiDeviceAvailable: StateFlow<Boolean> = videoSourceManager.hdmiDeviceAvailable
    
    // Trigger to force surface recreation when switching sources
    private val _surfaceRecreationTrigger = MutableStateFlow(0)
    val surfaceRecreationTrigger: StateFlow<Int> = _surfaceRecreationTrigger.asStateFlow()
    
    private data class DecoderConfig(
        val codecMime: String,
        val width: Int,
        val height: Int
    )

    private var decodingJob: Job? = null
    private var processingJob: Job? = null
    private var timeSyncJob: Job? = null
    private var stallMonitorJob: Job? = null
    private var wsReconnectJob: Job? = null
    private var wsReconnectDelayMs = WS_RECONNECT_INITIAL_MS
    private var autoReconnectEnabled = true
    private var lastReconnectAttemptMs = 0L
    private var decoderInitJob: Job? = null
    private var surface: Surface? = null
    private var isDecoderInitialized = false
    private var hasReceivedKeyframe = false
    @Volatile
    private var currentDecoderConfig = DecoderConfig(
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        1920,
        1080
    )
    @Volatile
    private var desiredDecoderConfig = currentDecoderConfig
    @Volatile
    private var lastFrameReceivedMs = 0L
    @Volatile
    private var lastFrameDecodedMs = 0L
    @Volatile
    private var decodeFailureCount = 0
    @Volatile
    private var lastKeyframeRequestMs = 0L
    
    // Frame buffer channel to decouple receiving from decoding
    private val frameChannel = Channel<VideoFrame>(capacity = FRAME_BUFFER_CAPACITY)
    private val bufferedFrameCount = AtomicInteger(0)
    private var adaptiveBitrateJob: Job? = null
    @Volatile
    private var targetBitrateBps = DEFAULT_TARGET_BITRATE_BPS
    @Volatile
    private var maxAdaptiveBitrateBps = DEFAULT_TARGET_BITRATE_BPS
    @Volatile
    private var lastBitrateUpdateMs = 0L
    private var trackingBitrateOverrideActive = false
    private var trackingBitrateBaselineBps: Int? = null
    
    // Local Object Tracker
    private var objectTracker: ObjectTracker? = null
    private var isTrackerInitialized = false

    private val trackingInFlight = AtomicBoolean(false)
    @Volatile private var remoteTrackingEnabled = false
    @Volatile private var remoteTrackingAllowed: Boolean? = null

    init {
        // Initialize ObjectTracker
        viewModelScope.launch(Dispatchers.IO) {
            try {
                objectTracker = ObjectTracker(application)
                if (objectTracker?.initialize() == true) {
                    isTrackerInitialized = true
                    Log.i(TAG, "Local ObjectTracker initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize Local ObjectTracker")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ObjectTracker", e)
            }
        }

        // Listen to tracking updates from Orin (via TrackingFeedbackClient)
        viewModelScope.launch {
            trackingFeedbackClient.trackingUpdates.collect { update ->
                if (update == null) {
                    return@collect
                }

                if (trackingModeState.value != TrackingMode.LOCAL) {
                    return@collect
                }

                val startMs = _trackingStartTimeMs.value
                if (startMs == null) {
                    return@collect
                }

                val expectedId = _activeTrackingId.value ?: return@collect
                val matchesId = update.trackingId == expectedId
                val receivedAt = System.currentTimeMillis()
                val matchesTime = receivedAt >= startMs

                if (matchesId && matchesTime) {
                    Log.d(TAG, "TrackingFeedback update: $update")
                    _trackingUpdates.value = update
                }
            }
        }
        
        // Listen to tracking updates from camcontroller (via WebSocket) when remote tracking is enabled.
        viewModelScope.launch {
            webSocketClient.trackingStatus.collect { update ->
                if (update == null) {
                    return@collect
                }

                if (trackingModeState.value != TrackingMode.CAMCONTROL) {
                    return@collect
                }

                val startMs = _trackingStartTimeMs.value
                if (startMs == null) {
                    return@collect
                }

                val receivedAt = System.currentTimeMillis()
                val matchesTime = receivedAt >= startMs

                if (matchesTime) {
                    Log.d(TAG, "WebSocket tracking update: state=${update.state}, bbox=${update.bbox}")
                    _trackingUpdates.value = update
                }
            }
        }

        viewModelScope.launch {
            webSocketClient.trackingControl.collect { allowed ->
                remoteTrackingAllowed = allowed
            }
        }

        viewModelScope.launch {
            trackingModeState.collect { mode ->
                when (mode) {
                    TrackingMode.LOCAL -> {
                        disableRemoteTracking("mode_local")
                    }
                    TrackingMode.CAMCONTROL -> {
                        stopLocalTracking("mode_camcontrol")
                    }
                    TrackingMode.OFF -> {
                        stopLocalTracking("mode_off")
                        disableRemoteTracking("mode_off")
                        resetTrackingCycle()
                    }
                }
            }
        }

        viewModelScope.launch {
            viewOnlyState.collect { enabled ->
                if (enabled) {
                    resetTrackingCycle()
                }
            }
        }

        // Keep a stable, non-null telemetry reference for UI to avoid flashing placeholders
        viewModelScope.launch {
            webSocketClient.telemetry.collect { incoming ->
                incoming?.let { telem ->
                    maybeUpdateDecoderConfig(telem)
                    _latestTelemetry.value = applyMeasuredStreamStats(mergeTelemetry(_latestTelemetry.value, telem))
                }
            }
        }

        // Forward WebSocket connection state to unified state (for WebSocket mode)
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                // Only update if we're in WebSocket mode
                if (videoTransport == VideoTransport.WEBSOCKET) {
                    _connectionState.value = state
                }
                
                when (state) {
                    is ConnectionState.Connected -> {
                        wsReconnectJob?.cancel()
                        wsReconnectJob = null
                        wsReconnectDelayMs = WS_RECONNECT_INITIAL_MS
                        _wsReconnectInMs.value = null
                        viewModelScope.launch {
                            if (trackingModeState.value == TrackingMode.CAMCONTROL) {
                                setRemoteTrackingEnabled(true, reason = "ws_connected")
                            } else {
                                setRemoteTrackingEnabled(false, reason = "ws_connected")
                            }
                        }
                        val decoderReady = if (requiresCodecDecoder()) isDecoderInitialized else surface != null
                        if (videoTransport == VideoTransport.WEBSOCKET && decoderReady && decodingJob == null) {
                            Log.i(TAG, "WebSocket connected - starting video decoding")
                            startDecoding()
                        }

                        if (videoTransport == VideoTransport.WEBSOCKET && timeSyncJob == null) {
                            timeSyncJob = viewModelScope.launch {
                                // Take a few quick samples then settle into a slower cadence.
                                repeat(3) {
                                    try { webSocketClient.requestTimeSync() } catch (_: Throwable) {}
                                    delay(250)
                                }
                                while (true) {
                                    try { webSocketClient.requestTimeSync() } catch (_: Throwable) {}
                                    delay(2000)
                                }
                            }
                        }

                            if (videoTransport == VideoTransport.WEBSOCKET && stallMonitorJob == null) {
                                stallMonitorJob = viewModelScope.launch {
                                    while (true) {
                                        delay(1000)
                                        if (videoTransport != VideoTransport.WEBSOCKET) {
                                            continue
                                        }
                                        val decoderRunning = if (requiresCodecDecoder()) isDecoderInitialized else surface != null
                                        if (decodingJob == null || !decoderRunning) {
                                            continue
                                        }
                                        val last = lastFrameReceivedMs
                                        val now = SystemClock.elapsedRealtime()
                                        if (last > 0 && now - last > 1500) {
                                            requestKeyFrame("stall")
                                        }
                                        if (last > 0 && now - last > STALL_FORCE_RECONNECT_MS) {
                                            forceWebSocketReconnect("stall")
                                        }
                                    }
                                }
                            }
                    }
                    is ConnectionState.Disconnected -> {
                        if (videoTransport == VideoTransport.WEBSOCKET && decodingJob != null) {
                            Log.i(TAG, "WebSocket disconnected - stopping video decoding")
                            stopDecoding()
                        }

                        timeSyncJob?.cancel()
                        timeSyncJob = null
                        stallMonitorJob?.cancel()
                        stallMonitorJob = null
                        _wsRttMs.value = null
                        _serverToClientOffsetMs.value = null
                        _e2eLatencyMs.value = null
                        scheduleWebSocketReconnect("disconnected")
                    }
                    is ConnectionState.Error -> {
                        scheduleWebSocketReconnect("error")
                    }
                    else -> { /* Connecting state - do nothing */ }
                }
            }
        }

        // Track time sync estimates from the WebSocket client
        viewModelScope.launch {
            webSocketClient.timeSync.collect { ts ->
                if (ts != null) {
                    _wsRttMs.value = ts.rttMs
                    _serverToClientOffsetMs.value = ts.serverToClientOffsetMs
                }
            }
        }

        // Observe settings changes and reconnect if camera URL changes.
        viewModelScope.launch {
            try {
                // Initialize lastCameraUrl from current settings to avoid false positives
                val initialSettings = settingsRepository.settings.first()
                val initial = resolveWebSocketUrl(initialSettings)
                var lastCameraUrl: String? = initial

                settingsRepository.settings
                    .map { settings -> resolveWebSocketUrl(settings) }
                    .debounce(300)
                    .collect { newUrl ->
                        try {
                            if (newUrl.isNullOrBlank()) return@collect
                            if (newUrl == lastCameraUrl) return@collect

                            Log.i(TAG, "Camera URL changed from $lastCameraUrl to $newUrl, reconnecting...")
                            lastCameraUrl = newUrl

                            // Always disconnect first so a stale in-flight connect can't block URL switch.
                            try { webSocketClient.disconnect() } catch (_: Exception) {}

                            // Reconnect using current transport
                            if (videoTransport == VideoTransport.WEBSOCKET) {
                                try { webSocketClient.connect(newUrl) } catch (e: Exception) {
                                    Log.e(TAG, "Failed to reconnect websocket to $newUrl: ${e.message}")
                                }
                            } else {
                                // WebRTC uses signalingUrl; cameraUrl is only for control/telemetry.
                                try { webSocketClient.connect(newUrl) } catch (e: Exception) {
                                    Log.e(TAG, "Failed to reconnect control websocket to $newUrl: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error handling cameraUrl change: ${e.message}")
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to observe settings for cameraUrl: ${e.message}")
            }
        }

        // Observe signaling URL changes separately to avoid duplicate WebRTC init on camera URL flips.
        viewModelScope.launch {
            try {
                val initial = settingsRepository.settings.first().signalingUrl
                var lastSignalingUrl: String? = initial

                settingsRepository.settings
                    .map { it.signalingUrl }
                    .debounce(300)
                    .collect { newUrl ->
                        if (newUrl.isNullOrBlank()) return@collect
                        if (newUrl == lastSignalingUrl) return@collect

                        Log.i(TAG, "Signaling URL changed from $lastSignalingUrl to $newUrl")
                        lastSignalingUrl = newUrl

                        if (videoTransport == VideoTransport.WEBRTC) {
                            try {
                                initializeWebRTC(newUrl)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to reinitialize WebRTC for new signaling URL: ${e.message}")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to observe settings for signalingUrl: ${e.message}")
            }
        }
        
        // Observe video source changes and switch pipeline when source changes
        viewModelScope.launch {
            videoSourceManager.currentSource.collect { source ->
                val currentSurface = surface
                Log.i(TAG, "Video source changed to: $source, surface available: ${currentSurface != null}")
                if (source == com.recomo.remotecontrol.camviewer.data.model.VideoSource.HDMI_USB && currentSurface != null) {
                    Log.i(TAG, "HDMI source detected with surface available - releasing decoder and starting UVC pipeline")
                    // Stop WebSocket decoding and release decoder to free the surface
                    stopDecoding()
                    videoDecoder.release()
                    isDecoderInitialized = false
                    // Start HDMI pipeline
                    videoSourceManager.switchSource(source, currentSurface)
                } else if (
                    source == com.recomo.remotecontrol.camviewer.data.model.VideoSource.WEBSOCKET ||
                    source == com.recomo.remotecontrol.camviewer.data.model.VideoSource.WEBSOCKET_ORIN
                ) {
                    Log.i(TAG, "WebSocket source detected - triggering surface recreation")
                    // Stop UVC pipeline if running
                    videoSourceManager.switchSource(source, null)
                    // Trigger surface recreation - the UI will destroy and recreate the SurfaceView
                    // This is necessary because UVC native code doesn't properly release the surface
                    _surfaceRecreationTrigger.value++
                }
            }
        }
    }
    
    private fun resolveDecoderConfig(fallbackWidth: Int, fallbackHeight: Int): DecoderConfig {
        val desired = desiredDecoderConfig
        val width = if (desired.width > 0) desired.width else fallbackWidth
        val height = if (desired.height > 0) desired.height else fallbackHeight
        return DecoderConfig(desired.codecMime, width, height)
    }

    private fun isOrinWebSocketSource(source: VideoSource = videoSourceManager.currentSource.value): Boolean {
        return source == VideoSource.WEBSOCKET_ORIN
    }

    private fun requiresCodecDecoder(source: VideoSource = videoSourceManager.currentSource.value): Boolean {
        return source != VideoSource.WEBSOCKET_ORIN
    }

    private fun deriveOrinWebSocketUrl(orinTargetUrl: String): String {
        return try {
            val uri = URI(orinTargetUrl.trim())
            val host = uri.host ?: return "ws://127.0.0.1:$ORIN_CAMERA_WS_PORT"
            "ws://$host:$ORIN_CAMERA_WS_PORT"
        } catch (_: Exception) {
            "ws://127.0.0.1:$ORIN_CAMERA_WS_PORT"
        }
    }

    private fun resolveWebSocketUrl(settings: com.recomo.remotecontrol.camviewer.data.model.AppSettings): String {
        return if (isOrinWebSocketSource()) {
            settings.orinCameraUrl.ifBlank { deriveOrinWebSocketUrl(settings.orinTargetUrl) }
        } else {
            settings.cameraUrl
        }
    }

    private fun maybeUpdateDecoderConfig(telemetry: Telemetry) {
        val current = desiredDecoderConfig
        var codecMime = current.codecMime
        val codecValue = telemetry.codec.trim().lowercase()

        if (codecValue.contains("h264")) {
            codecMime = MediaFormat.MIMETYPE_VIDEO_AVC
        } else if (codecValue.contains("h265") || codecValue.contains("hevc")) {
            codecMime = MediaFormat.MIMETYPE_VIDEO_HEVC
        }

        var width = current.width
        var height = current.height
        telemetry.resolution?.let { res ->
            if (res.width > 0 && res.height > 0) {
                width = res.width
                height = res.height
            }
        }

        if (codecMime == current.codecMime && width == current.width && height == current.height) {
            return
        }

        val updated = DecoderConfig(codecMime, width, height)
        desiredDecoderConfig = updated
        if (isDecoderInitialized && surface != null && updated != currentDecoderConfig) {
            reinitializeDecoder(updated, "telemetry")
        }
    }

    private fun reinitializeDecoder(config: DecoderConfig, reason: String) {
        if (surface == null) {
            return
        }
        decoderInitJob?.cancel()
        decoderInitJob = viewModelScope.launch {
            try {
                Log.i(TAG, "Reinitializing decoder ($reason): ${config.codecMime} ${config.width}x${config.height}")
                stopDecoding()
                hasReceivedKeyframe = false
                lastFrameReceivedMs = 0L
                lastFrameDecodedMs = 0L
                decodeFailureCount = 0
                lastKeyframeRequestMs = 0L
                videoDecoder.initialize(
                    codecType = config.codecMime,
                    width = config.width,
                    height = config.height,
                    surface = surface
                )
                currentDecoderConfig = config
                isDecoderInitialized = true
                if (_connectionState.value is ConnectionState.Connected && decodingJob == null) {
                    startDecoding()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reinitialize decoder", e)
            }
        }
    }

    private fun requestKeyFrame(reason: String) {
        if (isOrinWebSocketSource()) {
            return
        }
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip keyframe request ($reason)")
            return
        }
        when (videoTransport) {
            VideoTransport.WEBSOCKET -> {
                viewModelScope.launch {
                    try {
                        Log.w(TAG, "Requesting keyframe ($reason)")
                        webSocketClient.sendRawCommand("{\"cmd\":\"requestKeyFrame\"}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to request keyframe ($reason): ${e.message}")
                    }
                }
            }
            VideoTransport.WEBRTC -> {
                Log.w(TAG, "Requesting WebRTC keyframe ($reason)")
                webrtcReceiver?.requestKeyframe()
            }
        }
    }

    /**
     * Initialize video decoder with surface
     */
    fun initializeDecoder(surface: Surface, width: Int = 1920, height: Int = 1080) {
        val config = resolveDecoderConfig(width, height)
        Log.i(TAG, "Initializing decoder: ${config.codecMime} ${config.width}x${config.height}")
        this.surface = surface
        
        // Check if HDMI source is selected - start UVC pipeline instead of WebSocket
        val currentSource = videoSourceManager.currentSource.value
        Log.i(TAG, "Current video source: $currentSource")
        
        if (currentSource == com.recomo.remotecontrol.camviewer.data.model.VideoSource.HDMI_USB) {
            Log.i(TAG, "HDMI source selected - releasing decoder and starting UVC pipeline")
            // Release any existing decoder to free the surface for UVC
            viewModelScope.launch {
                stopDecoding()
                videoDecoder.release()
                isDecoderInitialized = false
                Log.i(TAG, "Decoder released, starting UVC pipeline")
                videoSourceManager.switchSource(currentSource, surface)
            }
            return
        }

        if (currentSource == com.recomo.remotecontrol.camviewer.data.model.VideoSource.WEBSOCKET_ORIN) {
            Log.i(TAG, "Orin WebSocket source selected - using JPEG rendering without MediaCodec")
            viewModelScope.launch {
                stopDecoding()
                videoDecoder.release()
                isDecoderInitialized = false
                if (_connectionState.value is ConnectionState.Connected && decodingJob == null) {
                    startDecoding()
                }
            }
            return
        }
        
        // WebSocket source - initialize decoder
        decoderInitJob?.cancel()
        decoderInitJob = viewModelScope.launch {
            try {
                videoDecoder.initialize(
                    codecType = config.codecMime,
                    width = config.width,
                    height = config.height,
                    surface = surface
                )
                currentDecoderConfig = config
                desiredDecoderConfig = config
                isDecoderInitialized = true
                Log.i(TAG, "Decoder initialized successfully")
                // If we're already connected, start decoding immediately.
                if (_connectionState.value is ConnectionState.Connected && decodingJob == null) {
                    Log.i(TAG, "Decoder initialized while connected - starting decoding")
                    startDecoding()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize decoder", e)
            }
        }
    }

    /**
     * Pause rendering/decoding while keeping network connections alive.
     * Used when the surface is destroyed to avoid tearing and MediaCodec errors.
     */
    fun pauseRendering() {
        Log.i(TAG, "Pausing rendering (stop decoding) while preserving connections")
        viewModelScope.launch {
            stopDecoding()
            lastFrameReceivedMs = 0L
            lastFrameDecodedMs = 0L
            decodeFailureCount = 0
            lastKeyframeRequestMs = 0L
            try {
                videoDecoder.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to flush decoder during pause: ${e.message}")
            }
        }
    }
    
    /**
     * Connect to CamControl server
     */
    fun connect() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val desiredTransport = if (settings.useWebRTC) VideoTransport.WEBRTC else VideoTransport.WEBSOCKET
            val streamWsUrl = resolveWebSocketUrl(settings)
            val state = _connectionState.value
            val alreadyActive = desiredTransport == VideoTransport.WEBSOCKET &&
                webSocketClient.isActiveFor(streamWsUrl)

            if (alreadyActive && (state is ConnectionState.Connected || state is ConnectionState.Connecting)) {
                Log.d(TAG, "connect() no-op: websocket already active for $streamWsUrl (state=$state)")
                if (_connectionState.value is ConnectionState.Connected && decodingJob == null) {
                    startDecoding()
                }
                return@launch
            }

            resetTrackingCycle()
            remoteTrackingEnabled = false
            autoReconnectEnabled = true
            wsReconnectJob?.cancel()
            wsReconnectJob = null
            wsReconnectDelayMs = WS_RECONNECT_INITIAL_MS
            _wsReconnectInMs.value = null

            // Determine transport mode
            videoTransport = desiredTransport
            Log.i(TAG, "📡 Video transport mode: $videoTransport")

            // Send tracking cycle signal in background, don't wait for it
            launch {
                try {
                    sendTrackingCycleSignal(action = "reset", reason = "app_connect")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send tracking cycle signal during connect: ${e.message}")
                }
            }

            // Start tracking feedback connection first so it isn't blocked by the video WebSocket
            connectToTrackingFeedback(settings.orinTrackingUrl)

            when (videoTransport) {
                VideoTransport.WEBSOCKET -> {
                    launch(Dispatchers.IO) {
                        webSocketClient.connect(streamWsUrl)
                    }
                    // Note: startDecoding() will be called automatically by connection state observer when connected
                }
                VideoTransport.WEBRTC -> {
                    // Connect WebSocket for control/telemetry
                    launch(Dispatchers.IO) {
                        webSocketClient.connect(streamWsUrl)
                    }
                    initializeWebRTC(settings.webrtcSignalingUrl)
                }
            }
        }
    }
    
    /**
     * Initialize and connect WebRTC receiver
     */
    private suspend fun initializeWebRTC(signalingUrl: String) {
        val trimmedUrl = signalingUrl.trim()
        if (trimmedUrl.isBlank()) {
            Log.w(TAG, "Signaling URL is blank, skipping WebRTC init")
            return
        }

        webrtcInitMutex.withLock {
            val existing = webrtcReceiver
            if (existing != null && webrtcSignalingUrl == trimmedUrl) {
                when (existing.connectionState.value) {
                    is CommonConnectionState.Connected,
                    is CommonConnectionState.Connecting -> {
                        Log.i(TAG, "WebRTC receiver already active for $trimmedUrl, skipping init")
                        return
                    }
                    else -> {
                        Log.i(TAG, "WebRTC receiver in ${existing.connectionState.value}, reinitializing")
                    }
                }
            }

            if (existing != null) {
                existing.close()
                webrtcReceiver = null
            }

            webrtcSignalingUrl = trimmedUrl
            Log.i(TAG, "Initializing WebRTC receiver...")
            _connectionState.value = ConnectionState.Connecting

            try {
                webrtcReceiver = WebRTCReceiver(
                    context = getApplication(),
                    signalingUrl = trimmedUrl,
                    roomId = "camcontrol-room",
                    peerId = buildPeerId("camviewer"),
                    scope = viewModelScope,
                    onVideoFrame = onVideoFrame@{ frameData ->
                        // Route WebRTC frames into the same queue to avoid decoder backlog.
                        if (!isDecoderInitialized) {
                            if (!hasReceivedKeyframe) {
                                Log.w(TAG, "WebRTC frame received but decoder not initialized yet (${frameData.size} bytes)")
                            }
                            return@onVideoFrame
                        }

                        val isKeyframe = !hasReceivedKeyframe
                        if (!hasReceivedKeyframe) {
                            Log.i(TAG, "Received first WebRTC frame (${frameData.size} bytes), starting decoding")
                            hasReceivedKeyframe = true
                        }

                        _latency.value = 0 // TODO: Implement timestamp in WebRTC frames
                        enqueueFrame(
                            VideoFrame(
                                data = frameData,
                                timestamp = System.currentTimeMillis(),
                                isKeyframe = isKeyframe
                            ),
                            source = "webrtc"
                        )
                    }
                )

                webrtcReceiver?.initialize()

                // Wire up keyframe request callback
                webrtcReceiver?.onKeyframeNeeded = {
                    Log.i(TAG, "🔑 Requesting keyframe due to frame drops")
                    webrtcReceiver?.requestKeyframe()
                }

                // Observe WebRTC connection state
                webrtcReceiver?.let { receiver ->
                    viewModelScope.launch {
                        receiver.connectionState.collect { rtcState ->
                            if (videoTransport == VideoTransport.WEBRTC) {
                                _connectionState.value = rtcState.toLocalConnectionState()
                            }
                        }
                    }
                }

                webrtcReceiver?.connect()

                Log.i(TAG, "WebRTC connection established")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WebRTC: ${e.message}", e)
                Log.e(TAG, "Make sure signaling server is running at: $trimmedUrl")
                _connectionState.value = ConnectionState.Error("WebRTC failed: ${e.message}")
            }
        }
    }

    private fun buildPeerId(prefix: String): String {
        val context = getApplication<Application>()
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        val manufacturer = Build.MANUFACTURER ?: "android"
        val model = Build.MODEL ?: "device"
        val raw = "$prefix-$manufacturer-$model-${androidId.take(6)}"
        return raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(64)
    }
    
    /**
     * Connect to tracking feedback WebSocket server
     */
    private fun connectToTrackingFeedback(trackingUrl: String) {
        val sanitizedUrl = trackingUrl.trim()
        Log.d(TAG, "connectToTrackingFeedback called with URL: '$trackingUrl'")
        
        if (sanitizedUrl.isBlank()) {
            Log.w(TAG, "Tracking URL is blank, skipping tracking feedback connection")
            return
        }
        
        val uri = try {
            if (sanitizedUrl.contains("://")) {
                URI(sanitizedUrl)
            } else {
                URI("ws://$sanitizedUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid tracking URL: $trackingUrl", e)
            return
        }
        
        val host = uri.host ?: uri.authority?.substringBefore(":")
        if (host.isNullOrBlank()) {
            Log.w(TAG, "Could not parse host from tracking URL: $trackingUrl")
            return
        }
        
        val port = if (uri.port in 1..65535) uri.port else TRACKING_DEFAULT_PORT
        
        Log.i(TAG, "Parsed tracking feedback: host=$host, port=$port")
        
        viewModelScope.launch {
            try {
                trackingFeedbackClient.connect(host, port)
                Log.i(TAG, "Starting tracking feedback connection to $host:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start tracking feedback connection", e)
            }
        }
    }

    private fun scheduleWebSocketReconnect(reason: String) {
        if (!autoReconnectEnabled || wsReconnectJob != null) {
            return
        }
        wsReconnectJob = viewModelScope.launch {
            val delayMs = wsReconnectDelayMs.coerceAtLeast(WS_RECONNECT_INITIAL_MS)
            Log.w(TAG, "WebSocket reconnect in ${delayMs}ms ($reason)")
            var remainingMs = delayMs
            _wsReconnectInMs.value = remainingMs
            while (remainingMs > 0 && autoReconnectEnabled && _connectionState.value !is ConnectionState.Connected) {
                val step = minOf(remainingMs, 250L)
                delay(step)
                remainingMs -= step
                _wsReconnectInMs.value = remainingMs
            }
            _wsReconnectInMs.value = null
            if (autoReconnectEnabled && _connectionState.value !is ConnectionState.Connected) {
                val settings = settingsRepository.settings.first()
                val url = resolveWebSocketUrl(settings)
                if (url.isBlank()) {
                    Log.w(TAG, "Camera URL is blank; skip reconnect")
                } else if (!webSocketClient.isActiveFor(url)) {
                    launch(Dispatchers.IO) {
                        try {
                            webSocketClient.connect(url)
                        } catch (e: Exception) {
                            Log.w(TAG, "Reconnect failed: ${e.message}")
                        }
                    }
                }
                wsReconnectDelayMs = (wsReconnectDelayMs * 2).coerceAtMost(WS_RECONNECT_MAX_MS)
            }
            wsReconnectJob = null
        }
    }

    private fun forceWebSocketReconnect(reason: String) {
        if (!autoReconnectEnabled || videoTransport != VideoTransport.WEBSOCKET) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastReconnectAttemptMs < STALL_FORCE_RECONNECT_MS) {
            return
        }
        lastReconnectAttemptMs = now
        Log.w(TAG, "Force reconnect ($reason)")
        viewModelScope.launch {
            try {
                webSocketClient.disconnect()
            } catch (_: Exception) {}
            scheduleWebSocketReconnect(reason)
        }
    }

    /**
     * Disconnect from server
     */
    fun disconnect() {
        Log.d(TAG, "Disconnect called, current transport: $videoTransport")
        // Log a lightweight stack trace to help identify the caller path that
        // is triggering frequent disconnects during normal playback.
        val stack = Throwable("disconnect-stack").stackTrace
        try {
            val preview = stack.take(6).joinToString(" <- ") { it.toString() }
            Log.d(TAG, "Disconnect stack (top frames): $preview")
        } catch (_: Exception) {}
        viewModelScope.launch {
            autoReconnectEnabled = false
            wsReconnectJob?.cancel()
            wsReconnectJob = null
            _wsReconnectInMs.value = null
            // Send tracking cycle signal in background, don't wait for it
            launch {
                try {
                    sendTrackingCycleSignal(action = "stop", reason = "disconnect")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send tracking cycle signal during disconnect: ${e.message}")
                }
            }
            resetTrackingCycle()
            remoteTrackingEnabled = false
            hasReceivedKeyframe = false
            
            when (videoTransport) {
                VideoTransport.WEBSOCKET -> {
                    // Note: stopDecoding() will be called automatically by connection state observer
                    webSocketClient.disconnect()
                }
                VideoTransport.WEBRTC -> {
                    webrtcReceiver?.close()
                    webrtcReceiver = null
                    webrtcSignalingUrl = null
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
            
            trackingFeedbackClient.disconnect()
            Log.d(TAG, "Disconnect completed")
        }
    }
    
    /**
     * Start decoding video frames
     */
    private fun startDecoding() {
        val needsDecoder = requiresCodecDecoder()
        if (needsDecoder && !isDecoderInitialized) {
            Log.w(TAG, "Cannot start decoding - decoder not initialized")
            return
        }
        if (!needsDecoder && surface == null) {
            Log.w(TAG, "Cannot start Orin JPEG stream - surface not ready")
            return
        }
        
        Log.i(TAG, "Starting video decoding pipeline")
        hasReceivedKeyframe = false
        lastFrameReceivedMs = 0L
        lastFrameDecodedMs = 0L
        decodeFailureCount = 0
        lastKeyframeRequestMs = 0L
        
        // Stop any existing jobs
        decodingJob?.cancel()
        processingJob?.cancel()
        
        // Job 1: Receive frames from WebSocket and put into channel (non-blocking)
        if (videoTransport == VideoTransport.WEBSOCKET) {
            decodingJob = viewModelScope.launch(Dispatchers.Default) {
                webSocketClient.videoFrames
                    .filterNotNull()
                    .collect { frame ->
                        lastFrameReceivedMs = SystemClock.elapsedRealtime()
                        if (frame.format == VideoFrameFormat.H26X) {
                            // Wait for first keyframe before buffering
                            if (!hasReceivedKeyframe) {
                                if (frame.isKeyframe) {
                                    Log.i(TAG, "Received first keyframe, starting decoding")
                                    hasReceivedKeyframe = true
                                } else {
                                    val nowElapsedMs = SystemClock.elapsedRealtime()
                                    if (nowElapsedMs - lastKeyframeRequestMs > 1000) {
                                        lastKeyframeRequestMs = nowElapsedMs
                                        requestKeyFrame("waiting_for_keyframe")
                                    }
                                    Log.d(TAG, "Skipping frame until keyframe arrives")
                                    return@collect
                                }
                            }
                        }

                        enqueueFrame(frame, source = "websocket")
                    }
            }
        } else {
            decodingJob = null
        }
        
        // Job 2: Process frames from channel and decode (can be slow without blocking receive)
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            var frameCount = 0

            // Rolling 1-second window for measured FPS/bitrate.
            var windowStartMs = SystemClock.elapsedRealtime()
            var framesInWindow = 0
            var bytesInWindow = 0L

            for (frame in frameChannel) {
                val remaining = bufferedFrameCount.decrementAndGet()
                if (remaining < 0) {
                    bufferedFrameCount.set(0)
                }
                frameCount++
                if (frameCount % 30 == 0) { // Log every 30 frames
                    Log.d(TAG, "Decoded $frameCount frames, latest size: ${frame.data.size} bytes, keyframe: ${frame.isKeyframe}")
                }

                framesInWindow++
                bytesInWindow += frame.data.size.toLong()

                val nowElapsedMs = SystemClock.elapsedRealtime()
                val windowElapsedMs = nowElapsedMs - windowStartMs
                if (windowElapsedMs >= 1000L) {
                    val fps = (framesInWindow * 1000f) / windowElapsedMs.toFloat()
                    val bitrateKbps = if (windowElapsedMs > 0) {
                        // kbps == (bits / ms) because: (bits/ms) == (kbits/s)
                        ((bytesInWindow * 8L) / windowElapsedMs).toInt().coerceAtLeast(0)
                    } else {
                        0
                    }

                    _measuredFps.value = fps
                    _measuredBitrateKbps.value = bitrateKbps

                    // Refresh the HUD even if phone-side telemetry is missing/stale.
                    _latestTelemetry.value = applyMeasuredStreamStats(_latestTelemetry.value ?: Telemetry())

                    windowStartMs = nowElapsedMs
                    framesInWindow = 0
                    bytesInWindow = 0L
                }
                
                // Calculate latency
                val now = System.currentTimeMillis()
                _latency.value = now - frame.timestamp

                // E2E(est): now - (phone timestamp projected into tablet clock).
                // Uses the latest telemetry sample as an approximation of the sender timestamp.
                val telem = _latestTelemetry.value
                val offset = _serverToClientOffsetMs.value
                if (telem != null && offset != null && telem.timestampMs > 0L) {
                    val phoneTsInClientClock = telem.timestampMs + offset
                    val e2e = now - phoneTsInClientClock
                    _e2eLatencyMs.value = e2e
                } else {
                    _e2eLatencyMs.value = null
                }
                
                // Write frame to recorder if recording
                if (_isRecording.value && frame.format == VideoFrameFormat.H26X) {
                    videoRecorder.writeFrame(frame.data)
                }
                
                // Decode/render frame (can block without affecting receive)
                val success = when (frame.format) {
                    VideoFrameFormat.H26X -> {
                        val timestampUs = frame.timestamp * 1000 // Convert ms to us
                        videoDecoder.decodeFrame(frame.data, timestampUs)
                    }
                    VideoFrameFormat.JPEG -> renderJpegFrame(frame.data)
                }
                
                if (!success && frameCount <= 10) {
                    Log.w(TAG, "Failed to decode frame $frameCount")
                }
                if (success) {
                    lastFrameDecodedMs = SystemClock.elapsedRealtime()
                    decodeFailureCount = 0
                } else {
                    decodeFailureCount++
                    if (decodeFailureCount >= 10) {
                        decodeFailureCount = 0
                        requestKeyFrame("decode_fail")
                    }
                }
            }
        }

        startAdaptiveBitrateMonitor()
    }

    private fun renderJpegFrame(jpegBytes: ByteArray): Boolean {
        val outputSurface = surface ?: return false
        return try {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return false
            _latestTelemetry.value = applyMeasuredStreamStats(
                Telemetry(
                    timestampMs = System.currentTimeMillis(),
                    resolution = com.recomo.remotecontrol.camviewer.data.model.Resolution(bitmap.width, bitmap.height),
                    codec = "jpeg"
                )
            )
            val canvas = outputSurface.lockCanvas(null)
            try {
                val destRect = Rect(0, 0, canvas.width, canvas.height)
                canvas.drawBitmap(bitmap, null, destRect, null)
            } finally {
                outputSurface.unlockCanvasAndPost(canvas)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to render JPEG frame: ${e.message}")
            false
        }
    }

    private fun startAdaptiveBitrateMonitor() {
        adaptiveBitrateJob?.cancel()
        adaptiveBitrateJob = viewModelScope.launch(Dispatchers.Default) {
            var highCount = 0
            var lowCount = 0

            while (true) {
                delay(1000)
                if (!isDecoderInitialized || processingJob == null) {
                    highCount = 0
                    lowCount = 0
                    continue
                }

                val backlog = bufferedFrameCount.get()
                when {
                    backlog >= FRAME_BUFFER_HIGH_WATERMARK -> {
                        highCount++
                        lowCount = 0
                    }
                    backlog <= FRAME_BUFFER_LOW_WATERMARK -> {
                        lowCount++
                        highCount = 0
                    }
                    else -> {
                        highCount = 0
                        lowCount = 0
                    }
                }

                val now = SystemClock.elapsedRealtime()
                if (highCount >= ADAPTIVE_BITRATE_HIGH_COUNT &&
                    now - lastBitrateUpdateMs > ADAPTIVE_BITRATE_COOLDOWN_MS) {
                    updateBitrateTarget(
                        targetBitrateBps - ADAPTIVE_BITRATE_STEP_BPS,
                        reason = "backlog_high",
                        clampToAdaptiveRange = true
                    )
                    highCount = 0
                } else if (lowCount >= ADAPTIVE_BITRATE_LOW_COUNT &&
                    now - lastBitrateUpdateMs > ADAPTIVE_BITRATE_COOLDOWN_MS) {
                    updateBitrateTarget(
                        targetBitrateBps + ADAPTIVE_BITRATE_STEP_BPS,
                        reason = "backlog_low",
                        clampToAdaptiveRange = true
                    )
                    lowCount = 0
                }
            }
        }
    }

    private fun clampAdaptiveBitrate(bitrate: Int): Int {
        val min = ADAPTIVE_BITRATE_MIN_BPS
        val max = maxAdaptiveBitrateBps.coerceAtLeast(min)
        return bitrate.coerceIn(min, max)
    }

    private fun updateBitrateTarget(bitrate: Int, reason: String, clampToAdaptiveRange: Boolean) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip bitrate update ($reason)")
            return
        }
        val applied = if (clampToAdaptiveRange) {
            clampAdaptiveBitrate(bitrate)
        } else {
            bitrate.coerceAtLeast(ADAPTIVE_BITRATE_MIN_BPS)
        }

        if (applied == targetBitrateBps && clampToAdaptiveRange) {
            return
        }

        targetBitrateBps = applied
        if (!clampToAdaptiveRange) {
            maxAdaptiveBitrateBps = applied.coerceAtLeast(ADAPTIVE_BITRATE_MIN_BPS)
        }
        lastBitrateUpdateMs = SystemClock.elapsedRealtime()

        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Bitrate update ($reason): ${applied / 1_000_000}Mbps on phone ${settings.phoneControlHost}")
                phoneCameraClient.setBitrate(settings.phoneControlHost, applied)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting bitrate ($reason)", e)
            }
        }
    }

    private fun trimBacklogIfNeeded(source: String) {
        val backlog = bufferedFrameCount.get()
        if (backlog < FRAME_BUFFER_HIGH_WATERMARK) {
            return
        }

        var dropped = 0
        while (bufferedFrameCount.get() > FRAME_BUFFER_LOW_WATERMARK && frameChannel.tryReceive().isSuccess) {
            bufferedFrameCount.decrementAndGet()
            dropped++
        }

        if (bufferedFrameCount.get() < 0) {
            bufferedFrameCount.set(0)
        }

        if (dropped > 0) {
            Log.w(TAG, "Trimmed $dropped frames due to backlog ($source)")
            requestKeyFrameThrottled("buffer_trim")
        }
    }

    private fun requestKeyFrameThrottled(reason: String, minIntervalMs: Long = 1000L) {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (nowElapsedMs - lastKeyframeRequestMs < minIntervalMs) {
            return
        }
        lastKeyframeRequestMs = nowElapsedMs
        requestKeyFrame(reason)
    }

    private fun enqueueFrame(frame: VideoFrame, source: String) {
        val sendResult = frameChannel.trySend(frame)
        if (sendResult.isSuccess) {
            bufferedFrameCount.incrementAndGet()
            trimBacklogIfNeeded(source)
            return
        }

        // Buffer full: drop stale frames and keep the newest to preserve low latency.
        var dropped = 0
        while (frameChannel.tryReceive().isSuccess) {
            dropped++
        }
        if (dropped > 0) {
            bufferedFrameCount.set(0)
            Log.w(TAG, "Dropped $dropped frames due to backlog ($source)")
        }

        if (!frame.isKeyframe) {
            requestKeyFrameThrottled("buffer_full")
        }

        val retry = frameChannel.trySend(frame)
        if (retry.isSuccess) {
            bufferedFrameCount.incrementAndGet()
        }
    }

    /**
     * Merge incoming telemetry with last known values to avoid flashing default/empty fields.
     * Prefer fresh non-zero/non-empty fields; keep previous otherwise.
     */
    private fun mergeTelemetry(
        current: Telemetry?,
        incoming: Telemetry
    ): Telemetry {
        if (current == null) return incoming

        return incoming.copy(
            fps = if (incoming.fps > 0f) incoming.fps else current.fps,
            bitrateKbps = if (incoming.bitrateKbps > 0) incoming.bitrateKbps else current.bitrateKbps,
            resolution = incoming.resolution ?: current.resolution,
            codec = incoming.codec.ifBlank { current.codec },
            trackingData = incoming.trackingData ?: current.trackingData,
            cameraIntrinsics = incoming.cameraIntrinsics ?: current.cameraIntrinsics
        )
    }

    private fun applyMeasuredStreamStats(telemetry: Telemetry): Telemetry {
        val fps = _measuredFps.value
        val bitrateKbps = _measuredBitrateKbps.value

        return telemetry.copy(
            fps = if (fps > 0f) fps else telemetry.fps,
            bitrateKbps = if (bitrateKbps > 0) bitrateKbps else telemetry.bitrateKbps
        )
    }
    
    /**
     * Stop decoding
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun stopDecoding() {
        decodingJob?.cancel()
        decodingJob = null
        processingJob?.cancel()
        processingJob = null
        adaptiveBitrateJob?.cancel()
        adaptiveBitrateJob = null
        hasReceivedKeyframe = false
        lastFrameReceivedMs = 0L
        lastFrameDecodedMs = 0L
        decodeFailureCount = 0
        lastKeyframeRequestMs = 0L
        
        // Clear any buffered frames
        while (!frameChannel.isEmpty) {
            frameChannel.tryReceive()
        }
        bufferedFrameCount.set(0)
    }

    /**
    * Clear tracking UI state and optionally mark a new tracking cycle start.
    */
    private fun resetTrackingCycle(startMs: Long? = null, clearOverrides: Boolean = true) {
        trackingFeedbackClient.clearTrackingUpdate()
        _trackingUpdates.value = null
        _activeTrackingId.value = null
        _trackingStartTimeMs.value = startMs
        if (clearOverrides) {
            applyTrackingOverrides(active = false)
        }
    }
    
    /**
     * Send camera control command
     */
    fun sendCommand(command: String, params: Map<String, String> = emptyMap()) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip command=$command")
            return
        }
        viewModelScope.launch {
            webSocketClient.sendCommand(command, params)
        }
    }
    
    private var setTargetJob: Job? = null

    /**
     * Send target coordinates (x, y normalized 0.0-1.0)
     */
    fun sendTargetCoordinates(x: Float, y: Float) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: ignoring tap target")
            return
        }
        // Cancel any pending target setting job to prevent spamming
        setTargetJob?.cancel()
        
        setTargetJob = viewModelScope.launch {
            // Debounce slightly to filter out accidental double taps or noise
            delay(50)

            val mode = trackingModeState.value
            if (mode == TrackingMode.OFF) {
                Log.i(TAG, "Tracking disabled; ignoring tap target")
                return@launch
            }
            
            val cycleStart = System.currentTimeMillis()
            resetTrackingCycle(cycleStart, clearOverrides = false)

            try {
                val (adjX, adjY) = if (mode == TrackingMode.CAMCONTROL) {
                    adjustNormalizedForRemote(x, y)
                } else {
                    Pair(x, y)
                }
                val roi = buildDefaultTapRoi(adjX, adjY)
                when (mode) {
                    TrackingMode.LOCAL -> {
                        startLocalTracking(roi, cycleStart)
                    }
                    TrackingMode.CAMCONTROL -> {
                        startRemoteTracking(roi, cycleStart)
                    }
                    TrackingMode.OFF -> {
                        Log.i(TAG, "Tracking disabled; ignoring tap target")
                    }
                }
                
                // Removed: Send to Orin
                /*
                val settings = settingsRepository.settings.first()
                val result = orinTargetClient.sendTargetCoordinates(...)
                */
            } catch (e: Exception) {
                Log.e(TAG, "Error sending target coordinates", e)
                resetTrackingCycle()
            }
        }
    }
    
    /**
     * Process a video frame for local tracking
     */
    fun processFrame(bitmap: android.graphics.Bitmap) {
        if (!isTrackerInitialized || objectTracker == null) return

        if (!shouldCaptureTrackingFrame()) {
            return
        }
        
        // Drop frame if previous frame is still processing
        if (!trackingInFlight.compareAndSet(false, true)) {
            return
        }
        
        _trackingProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val startTime = System.currentTimeMillis()
                val status = objectTracker!!.processFrame(bitmap)
                val duration = System.currentTimeMillis() - startTime
                if (duration > 30) {
                    Log.w(TAG, "Tracking processing took ${duration}ms")
                }
                
                // Convert TrackingStatus to TrackingUpdate
                val stateStr = when (status.state) {
                    LocalTrackingState.TRACKING -> "tracking"
                    LocalTrackingState.LOST -> "lost"
                    LocalTrackingState.PENDING -> "pending"
                }
                
                val bbox = if (status.bbox.width > 0 && status.bbox.height > 0) {
                    // Convert pixel coordinates back to normalized coordinates
                    // Assuming bitmap size matches video size (1920x1080 usually)
                    val width = bitmap.width.toFloat()
                    val height = bitmap.height.toFloat()
                    
                    BoundingBox(
                        x = status.bbox.xOffset / width,
                        y = status.bbox.yOffset / height,
                        width = status.bbox.width / width,
                        height = status.bbox.height / height
                    )
                } else null
                
                val update = TrackingUpdate(
                    trackingId = status.trackingId,
                    state = stateStr,
                    bbox = bbox,
                    confidence = status.confidence,
                    timestamp = status.timestampMs
                )
                
                _trackingUpdates.value = update
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame for tracking", e)
            } finally {
                trackingInFlight.set(false)
                _trackingProcessing.value = false
            }
        }
    }

    fun shouldCaptureTrackingFrame(): Boolean {
        if (viewOnlyState.value) {
            return false
        }
        if (trackingModeState.value != TrackingMode.LOCAL) {
            return false
        }

        if (bufferedFrameCount.get() >= FRAME_BUFFER_HIGH_WATERMARK) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        val lastReceived = lastFrameReceivedMs
        if (lastReceived > 0L && now - lastReceived > 700L) {
            return false
        }
        val lastDecoded = lastFrameDecodedMs
        if (lastDecoded > 0L && now - lastDecoded > 700L) {
            return false
        }

        return true
    }

    private fun applyTrackingOverrides(active: Boolean) {
        if (active == trackingBitrateOverrideActive) {
            return
        }
        trackingBitrateOverrideActive = active

        if (active) {
            if (trackingBitrateBaselineBps == null) {
                trackingBitrateBaselineBps = maxAdaptiveBitrateBps
            }
            updateBitrateTarget(
                TRACKING_TARGET_BITRATE_BPS,
                reason = "tracking_start",
                clampToAdaptiveRange = false
            )
        } else {
            val baseline = trackingBitrateBaselineBps
            trackingBitrateBaselineBps = null
            if (baseline != null) {
                updateBitrateTarget(
                    baseline,
                    reason = "tracking_stop",
                    clampToAdaptiveRange = false
                )
            }
        }
    }

    private fun buildDefaultTapRoi(x: Float, y: Float): TargetRoi {
        val (videoWidth, videoHeight) = trackingFrameSize()
        val boxSize = 0.1f // 10% of screen
        val width = boxSize * videoWidth
        val height = boxSize * videoHeight
        val xOffset = (x * videoWidth - width / 2).toInt().coerceIn(0, videoWidth - width.toInt())
        val yOffset = (y * videoHeight - height / 2).toInt().coerceIn(0, videoHeight - height.toInt())
        return TargetRoi(xOffset, yOffset, width.toInt(), height.toInt())
    }

    private fun buildRoiFromNormalized(x: Float, y: Float, width: Float, height: Float): TargetRoi {
        val (videoWidth, videoHeight) = trackingFrameSize()
        val xOffset = (x * videoWidth).toInt()
        val yOffset = (y * videoHeight).toInt()
        val roiWidth = (width * videoWidth).toInt()
        val roiHeight = (height * videoHeight).toInt()
        return TargetRoi(xOffset, yOffset, roiWidth, roiHeight)
    }

    private fun trackingFrameSize(): Pair<Int, Int> {
        val mode = trackingModeState.value
        if (mode == TrackingMode.CAMCONTROL) {
            val res = _latestTelemetry.value?.resolution
            if (res != null && res.width > 0 && res.height > 0) {
                return Pair(res.width, res.height)
            }
        }
        val config = desiredDecoderConfig
        val width = if (config.width > 0) config.width else 1920
        val height = if (config.height > 0) config.height else 1080
        return Pair(width, height)
    }

    private fun adjustNormalizedForRemote(x: Float, y: Float): Pair<Float, Float> {
        return Pair(x, y)
    }

    private fun adjustNormalizedForRemote(x: Float, y: Float, width: Float, height: Float): android.graphics.RectF {
        return android.graphics.RectF(x, y, x + width, y + height)
    }

    private suspend fun startLocalTracking(roi: TargetRoi, cycleStart: Long) {
        if (!isTrackerInitialized || objectTracker == null) {
            Log.w(TAG, "Local tracker not initialized, cannot track.")
            return
        }

        objectTracker!!.stopTracking()
        objectTracker!!.setTargetRoi(roi, forceNew = true)
        val trackingId = "local-${System.currentTimeMillis()}"
        _activeTrackingId.value = trackingId
        _trackingStartTimeMs.value = cycleStart
        applyTrackingOverrides(active = true)
        Log.i(TAG, "Target ROI set for local tracking: $roi")
    }

    private suspend fun startRemoteTracking(roi: TargetRoi, cycleStart: Long) {
        if (trackingModeState.value != TrackingMode.CAMCONTROL) {
            return
        }

        if (remoteTrackingAllowed == false) {
            Log.w(TAG, "CamControl tracking disabled on phone; ignoring target")
            emitTrackingNotice("CamControl tracking is disabled on the phone")
            return
        }

        setRemoteTrackingEnabled(true, reason = "target_start")
        val payload = """{"cmd":"setTargetRoi","x_offset":${roi.xOffset},"y_offset":${roi.yOffset},"width":${roi.width},"height":${roi.height}}"""
        webSocketClient.sendRawCommand(payload)
        _trackingStartTimeMs.value = cycleStart
        applyTrackingOverrides(active = true)
        Log.i(TAG, "Target ROI sent for camcontrol tracking: $roi")
    }

    private fun stopLocalTracking(reason: String) {
        if (_activeTrackingId.value == null) {
            return
        }
        Log.i(TAG, "Stopping local tracking: $reason")
        viewModelScope.launch {
            try { objectTracker?.stopTracking() } catch (_: Exception) {}
        }
        resetTrackingCycle()
    }

    private fun disableRemoteTracking(reason: String) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip remote tracking disable ($reason)")
            return
        }
        if (!remoteTrackingEnabled) {
            return
        }
        viewModelScope.launch {
            setRemoteTrackingEnabled(false, reason)
            webSocketClient.sendRawCommand("""{"cmd":"stopTracking"}""")
        }
    }

    private suspend fun setRemoteTrackingEnabled(enabled: Boolean, reason: String) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip remote tracking toggle ($reason)")
            return
        }
        if (remoteTrackingEnabled == enabled) {
            return
        }
        if (enabled && remoteTrackingAllowed == false) {
            Log.w(TAG, "CamControl tracking disabled on phone; refusing enable")
            emitTrackingNotice("CamControl tracking is disabled on the phone")
            return
        }
        remoteTrackingEnabled = enabled
        val payload = """{"cmd":"setTrackingEnabled","enabled":$enabled}"""
        Log.d(TAG, "Remote tracking ${if (enabled) "enabled" else "disabled"} ($reason)")
        webSocketClient.sendRawCommand(payload)
        if (!enabled) {
            applyTrackingOverrides(active = false)
        }
    }

    private fun emitTrackingNotice(message: String) {
        _trackingNotice.tryEmit(message)
    }

    /**
     * Send target ROI (Region of Interest) with bounding box
     * Routes to local or camcontrol tracking based on settings
     * 
     * @param x Top-left x coordinate (normalized 0.0-1.0)
     * @param y Top-left y coordinate (normalized 0.0-1.0)
     * @param width ROI width (normalized 0.0-1.0)
     * @param height ROI height (normalized 0.0-1.0)
     */
    fun sendTargetROI(x: Float, y: Float, width: Float, height: Float) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: ignoring ROI")
            return
        }
        // Cancel any pending target setting job
        setTargetJob?.cancel()
        
        setTargetJob = viewModelScope.launch {
            // Debounce slightly
            delay(50)

            val mode = trackingModeState.value
            if (mode == TrackingMode.OFF) {
                Log.i(TAG, "Tracking disabled; ignoring ROI")
                return@launch
            }
            
            val cycleStart = System.currentTimeMillis()
            resetTrackingCycle(cycleStart, clearOverrides = false)

            try {
                _trackingUpdates.value = TrackingUpdate(
                    trackingId = "pending",
                    state = "pending",
                    bbox = BoundingBox(x, y, width, height),
                    confidence = 0f,
                    timestamp = cycleStart
                )

                val rect = if (mode == TrackingMode.CAMCONTROL) {
                    adjustNormalizedForRemote(x, y, width, height)
                } else {
                    android.graphics.RectF(x, y, x + width, y + height)
                }
                val roi = buildRoiFromNormalized(rect.left, rect.top, rect.width(), rect.height())
                when (mode) {
                    TrackingMode.LOCAL -> {
                        startLocalTracking(roi, cycleStart)
                    }
                    TrackingMode.CAMCONTROL -> {
                        startRemoteTracking(roi, cycleStart)
                    }
                    TrackingMode.OFF -> {
                        Log.i(TAG, "Tracking disabled; ignoring ROI")
                    }
                }
                
                // Removed: Send to Orin (Primary)
                /*
                val settings = settingsRepository.settings.first()
                if (settings.orinTargetUrl.isNotBlank()) {
                    // ... existing code ...
                }
                */
            } catch (e: Exception) {
                Log.e(TAG, "Error sending target ROI", e)
                resetTrackingCycle()
            }
        }
    }
    
    /**
     * Developer mode state from settings
     */
    val developerModeEnabled: Flow<Boolean> = settingsRepository.settings
        .map { it.developerModeEnabled }
    
    /**
     * Camera control commands (developer mode features)
     * Send commands to phone's camera via WebSocket
     */
    fun setZoom(value: Float) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip zoom change")
            return
        }
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Setting zoom to $value on phone ${settings.phoneControlHost}")
                phoneCameraClient.setZoom(settings.phoneControlHost, value)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting zoom", e)
            }
        }
    }
    
    fun setAeLock(enabled: Boolean) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip AE lock")
            return
        }
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Setting AE lock to $enabled on phone ${settings.phoneControlHost}")
                phoneCameraClient.setAeLock(settings.phoneControlHost, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting AE lock", e)
            }
        }
    }
    
    fun setAwbLock(enabled: Boolean) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip AWB lock")
            return
        }
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Setting AWB lock to $enabled on phone ${settings.phoneControlHost}")
                phoneCameraClient.setAwbLock(settings.phoneControlHost, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting AWB lock", e)
            }
        }
    }

    fun setAfMode(mode: String) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip AF mode change")
            return
        }
        viewModelScope.launch {
            try {
                val normalized = if (mode.equals("locked", true)) "locked" else "continuous"
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Setting AF mode to $normalized on phone ${settings.phoneControlHost}")
                phoneCameraClient.setAfMode(settings.phoneControlHost, normalized)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting AF mode", e)
            }
        }
    }
    
    fun switchCamera(facing: String) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip camera switch")
            return
        }
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Switching camera to $facing on phone ${settings.phoneControlHost}")
                phoneCameraClient.switchCamera(settings.phoneControlHost, facing)
            } catch (e: Exception) {
                Log.e(TAG, "Error switching camera", e)
            }
        }
    }
    
    fun setBitrate(bitrate: Int) {
        updateBitrateTarget(bitrate, reason = "manual", clampToAdaptiveRange = false)
    }
    
    fun setCodec(codec: String) {
        if (viewOnlyState.value) {
            Log.i(TAG, "View-only: skip codec change")
            return
        }
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                Log.d(TAG, "Setting codec to $codec on phone ${settings.phoneControlHost}")
                phoneCameraClient.setCodec(settings.phoneControlHost, codec)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting codec", e)
            }
        }
    }
    
    /**
     * Start recording video to file
     */
    fun startRecording() {
        if (_isRecording.value) {
            Log.w(TAG, "Already recording")
            return
        }
        
        viewModelScope.launch {
            try {
                // Determine codec from current stream (default to h265)
                val codec = "h265" // TODO: Track actual codec from stream
                val filePath = videoRecorder.startRecording(codec)
                if (filePath != null) {
                    _isRecording.value = true
                    _recordingFile.value = filePath
                    Log.i(TAG, "Started recording to: $filePath")
                } else {
                    Log.e(TAG, "Failed to start recording")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
            }
        }
    }
    
    /**
     * Stop recording video
     */
    fun stopRecording() {
        if (!_isRecording.value) {
            Log.w(TAG, "Not recording")
            return
        }
        
        viewModelScope.launch {
            try {
                val result = videoRecorder.stopRecording()
                _isRecording.value = false
                
                if (result != null) {
                    val (filePath, bytes) = result
                    Log.i(TAG, "Stopped recording: $filePath (${bytes / 1024 / 1024} MB)")
                    _recordingFile.value = filePath
                } else {
                    _recordingFile.value = null
                    Log.e(TAG, "Failed to stop recording")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                _isRecording.value = false
                _recordingFile.value = null
            }
        }
    }
    
    /**
     * Toggle recording on/off
     */
    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * Set video source (WebSocket or HDMI-USB)
     */
    fun setVideoSource(source: com.recomo.remotecontrol.camviewer.data.model.VideoSource) {
        Log.i(TAG, "Setting video source to: $source, surface available: ${surface != null}")
        
        // Stop current pipeline first
        if (source == com.recomo.remotecontrol.camviewer.data.model.VideoSource.HDMI_USB) {
            // For HDMI, we need to stop WebSocket if running
            stopDecoding()
        }
        
        // Switch source (will save preference even if surface is null)
        videoSourceManager.switchSource(source, surface)
        
        // If switching to websocket sources and we have a surface, reconnect stream path.
        if (
            (source == com.recomo.remotecontrol.camviewer.data.model.VideoSource.WEBSOCKET ||
                source == com.recomo.remotecontrol.camviewer.data.model.VideoSource.WEBSOCKET_ORIN) &&
            surface != null
        ) {
            viewModelScope.launch {
                try {
                    webSocketClient.disconnect()
                } catch (_: Exception) {}
                connect()
                if (!requiresCodecDecoder(source) && _connectionState.value is ConnectionState.Connected) {
                    startDecoding()
                }
            }
        }
    }
    
    /**
     * Check if HDMI device is available
     */
    fun checkHdmiDevice() {
        videoSourceManager.checkHdmiDeviceAvailability()
    }
    
    /**
     * Release resources
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            sendTrackingCycleSignal(action = "stop", reason = "viewmodel_cleared")
            stopDecoding()
            videoDecoder.release()
            webSocketClient.disconnect()
            webrtcReceiver?.close()
            webrtcSignalingUrl = null
            trackingFeedbackClient.disconnect()
        }
    }

    /**
     * Notify Orin/ROS about tracking lifecycle state.
     */
    private suspend fun sendTrackingCycleSignal(
        action: String,
        trackingId: String? = null,
        reason: String? = null
    ) {
        if (viewOnlyState.value) {
            return
        }
        try {
            val settings = settingsRepository.settings.first()
            val result = orinTargetClient.sendTrackingCycle(
                baseUrl = settings.orinTargetUrl,
                action = action,
                trackingId = trackingId,
                reason = reason
            )
            if (result.isSuccess) {
                Log.i(TAG, "Tracking cycle signal sent: action=$action, trackingId=${result.getOrNull()}, reason=$reason")
            } else {
                Log.w(TAG, "Failed to send tracking cycle signal: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tracking cycle signal", e)
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Thumbnail capture for FOI recording
    // ─────────────────────────────────────────────────────────────────────────────
    
    private val _lastCapturedFrame = MutableStateFlow<android.graphics.Bitmap?>(null)
    
    /**
     * Called from VideoScreen when a frame is captured for tracking.
     * We keep a copy for thumbnail purposes.
     */
    fun updateLastFrame(bitmap: android.graphics.Bitmap) {
        // Create a scaled-down copy for thumbnails (160x90 for 16:9 aspect)
        val thumbnailWidth = 160
        val thumbnailHeight = (bitmap.height.toFloat() / bitmap.width * thumbnailWidth).toInt()
        val thumbnail = android.graphics.Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
        _lastCapturedFrame.value = thumbnail
    }
    
    /**
     * Capture current frame as base64-encoded JPEG thumbnail.
     * Returns null if no frame is available.
     */
    fun captureThumbnailBase64(): String? {
        val bitmap = _lastCapturedFrame.value ?: return null
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode thumbnail", e)
            null
        }
    }
}
