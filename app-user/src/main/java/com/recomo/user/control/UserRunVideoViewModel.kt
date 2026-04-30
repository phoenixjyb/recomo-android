package com.recomo.user.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.Telemetry
import com.recomo.common.model.VideoFrame
import com.recomo.common.model.VideoFrameFormat
import com.recomo.common.model.VideoSource
import com.recomo.common.network.WebRTCReceiver
import com.recomo.common.video.PipelineState
import com.recomo.common.video.VideoDecoder
import com.recomo.common.video.VideoSourceManager
import com.recomo.user.R
import com.recomo.user.data.postrecord.UserPostRecordRepository
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.data.video.UserVideoRecorder
import com.recomo.user.data.video.UserVideoStreamClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val JPEG_FRAME_INTERVAL_MS = 120L
private const val FRAME_STALE_TIMEOUT_MS = 2000L

@HiltViewModel
class UserRunVideoViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userSettingsRepository: UserSettingsRepository,
    private val videoRecorder: UserVideoRecorder,
    private val postRecordRepository: UserPostRecordRepository,
    json: Json
) : ViewModel() {
    private data class DecoderConfig(
        val codecMime: String = MediaFormat.MIMETYPE_VIDEO_HEVC,
        val width: Int = 1920,
        val height: Int = 1080
    )

    private val videoDecoder = VideoDecoder()
    private val videoStreamClient = UserVideoStreamClient(json)
    val videoSourceManager = VideoSourceManager(appContext)
    private val _active = MutableStateFlow(false)
    private val _cameraUrl = MutableStateFlow("")
    private val _latestBitmap = MutableStateFlow<Bitmap?>(null)
    private val _frameFormat = MutableStateFlow<VideoFrameFormat?>(null)
    private val _lastFrameAtMs = MutableStateFlow(0L)
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _recordingElapsedMs = MutableStateFlow(0L)
    private val _recordingError = MutableStateFlow<String?>(null)
    private val _cameraPermissionError = MutableStateFlow<String?>(null)

    private var connectJob: Job? = null
    private var frameStaleJob: Job? = null
    private var recordingTickerJob: Job? = null
    private var activeStreamUrl: String? = null
    private var surface: Surface? = null
    private var isDecoderInitialized = false
    private var decoderConfig = DecoderConfig()
    private var lastBitmapDecodeAtMs = 0L
    private var hasReceivedKeyframe = false
    private var recordingStartedAtMs = 0L
    private var latestTelemetry: Telemetry? = null
    private var webrtcReceiver: WebRTCReceiver? = null
    private val webrtcInitMutex = Mutex()
    private var webrtcSignalingUrl: String? = null

    private data class VideoUiInputs(
        val cameraUrl: String,
        val connectionState: ConnectionState,
        val telemetry: Telemetry?,
        val frameFormat: VideoFrameFormat?,
        val lastFrameAtMs: Long,
        val isRecording: Boolean,
        val recordingElapsedMs: Long,
        val recordingError: String?,
        val videoSource: VideoSource,
        val hdmiPipelineState: PipelineState,
        val hdmiErrorMessage: String?,
        val cameraPermissionError: String?
    )

    private data class StreamInputs(
        val cameraUrl: String,
        val connectionState: ConnectionState,
        val telemetry: Telemetry?,
        val frameFormat: VideoFrameFormat?,
        val lastFrameAtMs: Long
    )

    private data class RuntimeInputs(
        val isRecording: Boolean,
        val recordingElapsedMs: Long,
        val recordingError: String?,
        val videoSource: VideoSource,
        val hdmiPipelineState: PipelineState
    )

    private data class HdmiInputs(
        val hdmiErrorMessage: String?,
        val cameraPermissionError: String?
    )

    val latestBitmap: StateFlow<Bitmap?> = _latestBitmap.asStateFlow()

    /**
     * Capture the current live frame as a base64-encoded JPEG thumbnail for
     * keyframe records. Matches `:app` `VideoViewModel.captureThumbnailBase64`:
     * scales to 160 px wide (preserving aspect ratio), JPEG quality 70,
     * NO_WRAP base64 encoding.
     *
     * Returns null if no decoded frame is available. The JPEG-path sources
     * (WS Phone, HDMI USB) populate `_latestBitmap`; H.264/H.265 paths render
     * directly to the surface and return null here — the `FoiRecord.thumbnail`
     * field is optional so null is acceptable downstream.
     */
    fun captureThumbnailBase64(): String? {
        val source = _latestBitmap.value ?: return null
        return try {
            val targetWidth = 160
            val targetHeight = (source.height.toFloat() / source.width * targetWidth).toInt()
                .coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            val stream = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            if (scaled !== source) {
                scaled.recycle()
            }
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("UserRunVideoVM", "captureThumbnailBase64 failed", e)
            null
        }
    }

    val state: StateFlow<UserRunVideoUiState>
        get() = uiState

    val uiState: StateFlow<UserRunVideoUiState> = combine(
        combine(
            _cameraUrl,
            videoStreamClient.connectionState,
            videoStreamClient.telemetry,
            _frameFormat,
            _lastFrameAtMs
        ) { cameraUrl, connectionState, telemetry, frameFormat, lastFrameAtMs ->
            StreamInputs(
                cameraUrl = cameraUrl,
                connectionState = connectionState,
                telemetry = telemetry,
                frameFormat = frameFormat,
                lastFrameAtMs = lastFrameAtMs
            )
        },
        combine(
            _isRecording,
            _recordingElapsedMs,
            _recordingError,
            videoSourceManager.currentSource,
            videoSourceManager.connectionState
        ) { isRecording, recordingElapsedMs, recordingError, currentSource, hdmiState ->
            RuntimeInputs(
                isRecording = isRecording,
                recordingElapsedMs = recordingElapsedMs,
                recordingError = recordingError,
                videoSource = currentSource,
                hdmiPipelineState = hdmiState
            )
        },
        combine(videoSourceManager.errorMessage, _cameraPermissionError) { hdmiErrorMessage, cameraPermissionError ->
            HdmiInputs(
                hdmiErrorMessage = hdmiErrorMessage,
                cameraPermissionError = cameraPermissionError
            )
        }
    ) { stream, runtime, hdmi ->
        VideoUiInputs(
            cameraUrl = stream.cameraUrl,
            connectionState = stream.connectionState,
            telemetry = stream.telemetry,
            frameFormat = stream.frameFormat,
            lastFrameAtMs = stream.lastFrameAtMs,
            isRecording = runtime.isRecording,
            recordingElapsedMs = runtime.recordingElapsedMs,
            recordingError = runtime.recordingError,
            videoSource = runtime.videoSource,
            hdmiPipelineState = runtime.hdmiPipelineState,
            hdmiErrorMessage = hdmi.hdmiErrorMessage,
            cameraPermissionError = hdmi.cameraPermissionError
        )
    }.map { inputs ->
        val isHdmi = inputs.videoSource == VideoSource.HDMI_USB
        val isWebRTC = inputs.videoSource == VideoSource.WEBRTC_ORIN
        val hdmiError = inputs.cameraPermissionError ?: inputs.hdmiErrorMessage
        val isHdmiActive = isHdmi && inputs.hdmiPipelineState == PipelineState.CONNECTED
        val isHdmiError = isHdmi && (inputs.hdmiPipelineState == PipelineState.ERROR || hdmiError != null)
        // WS stream: check WS client connected + frames flowing
        val wsStreamHealthy = inputs.connectionState is ConnectionState.Connected &&
            inputs.lastFrameAtMs > 0L
        // WebRTC: WS client is disconnected, but frames still update _lastFrameAtMs via onVideoFrame callback
        val webrtcStreamHealthy = isWebRTC && inputs.lastFrameAtMs > 0L
        val streamHealthy = isHdmiActive || wsStreamHealthy || webrtcStreamHealthy
        val canRecord = (wsStreamHealthy || webrtcStreamHealthy)

        val sourceLabel = when (inputs.videoSource) {
            VideoSource.WS_PHONE -> "WS Phone"
            VideoSource.WS_ORIN -> "WS Orin"
            VideoSource.WEBRTC_ORIN -> "WebRTC Orin"
            VideoSource.HDMI_USB -> "HDMI USB"
        }

        UserRunVideoUiState(
            cameraUrl = if (isHdmi) "HDMI USB" else inputs.cameraUrl,
            sourceLabel = sourceLabel,
            connectionState = inputs.connectionState,
            detailState = when {
                isHdmiActive -> UserRunVideoDetailState.ReceivingFrames
                isHdmiError -> UserRunVideoDetailState.Error
                isHdmi -> UserRunVideoDetailState.WaitingFrames
                wsStreamHealthy || webrtcStreamHealthy -> UserRunVideoDetailState.ReceivingFrames
                isWebRTC -> UserRunVideoDetailState.WaitingFrames
                inputs.connectionState is ConnectionState.Error -> UserRunVideoDetailState.Error
                inputs.connectionState is ConnectionState.Connected -> UserRunVideoDetailState.WaitingFrames
                inputs.cameraUrl.isBlank() -> UserRunVideoDetailState.NoUrl
                else -> UserRunVideoDetailState.Idle
            },
            errorMessage = if (isHdmi) {
                hdmiError
            } else {
                (inputs.connectionState as? ConnectionState.Error)?.message
            },
            resolutionLabel = if (isHdmiActive) "1920×1080" else inputs.telemetry?.resolution?.let { "${it.width}×${it.height}" } ?: "--",
            videoWidth = if (isHdmiActive) 1920 else inputs.telemetry?.resolution?.width?.takeIf { it > 0 } ?: 0,
            videoHeight = if (isHdmiActive) 1080 else inputs.telemetry?.resolution?.height?.takeIf { it > 0 } ?: 0,
            codecLabel = if (isHdmiActive) "UVC" else inputs.telemetry?.codec?.ifBlank { null } ?: when (inputs.frameFormat) {
                VideoFrameFormat.JPEG -> "JPEG"
                VideoFrameFormat.H26X -> "H.26x"
                null -> null
            },
            fpsLabel = if (isHdmiActive) "30 fps" else inputs.telemetry?.fps?.takeIf { it > 0f }?.let { String.format("%2.0f fps", it) },
            showBitmapFrame = !isHdmi && inputs.frameFormat == VideoFrameFormat.JPEG,
            showSurfaceFrame = isHdmi || inputs.frameFormat != VideoFrameFormat.JPEG,
            canReconnect = !isHdmi && inputs.cameraUrl.isNotBlank(),
            isStreaming = streamHealthy,
            canRecord = canRecord || inputs.isRecording,
            isRecording = inputs.isRecording,
            recordingElapsedLabel = if (inputs.isRecording) formatRecordingElapsed(inputs.recordingElapsedMs) else null,
            recordingErrorMessage = inputs.recordingError
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserRunVideoUiState()
    )

    init {
        viewModelScope.launch {
            userSettingsRepository.appSettings.collect { settings ->
                val source = settings.videoSource
                val wasNonWs = videoSourceManager.isHdmiSource()
                val resolvedUrl = when (source) {
                    VideoSource.WS_PHONE -> settings.cameraUrl
                    else -> settings.orinCameraUrl
                }
                _cameraUrl.value = resolvedUrl

                // Stop any previous pipeline that doesn't match the new source
                when (source) {
                    VideoSource.WS_PHONE, VideoSource.WS_ORIN -> {
                        videoSourceManager.stop()
                        shutdownWebRTC()
                        if (_active.value) {
                            ensureConnected(resolvedUrl, force = wasNonWs || activeStreamUrl != resolvedUrl)
                        }
                    }
                    VideoSource.WEBRTC_ORIN -> {
                        videoSourceManager.stop()
                        disconnectStream()
                        if (settings.webrtcSignalingUrl.isNotBlank()) {
                            initializeWebRTC(settings.webrtcSignalingUrl)
                        }
                    }
                    VideoSource.HDMI_USB -> {
                        _cameraPermissionError.value = null
                        disconnectStream()
                        shutdownWebRTC()
                        // Release decoder so it doesn't hold the Surface — UVC needs exclusive access
                        videoDecoder.release()
                        isDecoderInitialized = false
                        hasReceivedKeyframe = false
                        val s = surface
                        if (s != null) {
                            videoSourceManager.switchSource(VideoSource.HDMI_USB, s)
                        } else {
                            videoSourceManager.switchSource(VideoSource.HDMI_USB, null)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            videoStreamClient.telemetry.collect { telemetry ->
                latestTelemetry = telemetry
                telemetry?.let(::updateDecoderConfig)
            }
        }

        viewModelScope.launch {
            videoStreamClient.videoFrames
                .filterNotNull()
                .collect { frame ->
                    _frameFormat.value = frame.format
                    _lastFrameAtMs.value = System.currentTimeMillis()
                    scheduleFrameStaleTimeout()
                    if (_isRecording.value) {
                        when (frame.format) {
                            VideoFrameFormat.H26X -> videoRecorder.writeFrame(frame.data)
                            VideoFrameFormat.JPEG -> videoRecorder.writeJpegFrame(frame.data)
                        }
                    }
                    when (frame.format) {
                        VideoFrameFormat.JPEG -> renderBitmapFrame(frame)
                        VideoFrameFormat.H26X -> decodeSurfaceFrame(frame)
                    }
                }
        }
    }

    fun setActive(active: Boolean) {
        if (_active.value == active) return
        _active.value = active
        if (active) {
            viewModelScope.launch { ensureConnected(_cameraUrl.value, force = false) }
        } else {
            disconnectStream()
        }
    }

    fun reconnect() {
        _active.value = true
        viewModelScope.launch {
            ensureConnected(_cameraUrl.value, force = true)
        }
    }

    fun disconnect() {
        disconnectStream()
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    fun ensureConnected() {
        setActive(true)
    }

    fun initializeSurface(holder: SurfaceHolder) {
        val frame = holder.surfaceFrame
        onSurfaceReady(
            surface = holder.surface,
            width = frame.width().coerceAtLeast(1),
            height = frame.height().coerceAtLeast(1)
        )
    }

    fun releaseSurface() {
        onSurfaceDestroyed()
    }

    fun onSurfaceReady(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        // If HDMI USB is selected but pipeline wasn't started (surface was null at settings time),
        // start it now that the surface is available.
        if (videoSourceManager.isHdmiSource()) {
            if (!videoSourceManager.isRunning()) {
                Log.i("UserRunVideoVM", "Surface ready, starting deferred HDMI pipeline")
                videoSourceManager.start(surface)
            }
            return // UVC has exclusive surface access — don't initialize decoder
        }
        if (_frameFormat.value != VideoFrameFormat.JPEG) {
            initializeDecoder(
                decoderConfig.copy(
                    width = if (decoderConfig.width > 0) decoderConfig.width else width,
                    height = if (decoderConfig.height > 0) decoderConfig.height else height
                )
            )
        }
    }

    fun onSurfaceDestroyed() {
        this.surface = null
        if (videoSourceManager.isHdmiSource()) {
            videoSourceManager.stop()
        }
        viewModelScope.launch {
            videoDecoder.release()
            isDecoderInitialized = false
            hasReceivedKeyframe = false
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (!videoSourceManager.isHdmiSource()) {
            _cameraPermissionError.value = null
            return
        }

        if (!granted) {
            videoSourceManager.stop()
            _cameraPermissionError.value =
                appContext.getString(R.string.run_live_preview_camera_permission_required)
            return
        }

        _cameraPermissionError.value = null
        videoSourceManager.checkHdmiDeviceAvailability()

        val currentSurface = surface
        if (currentSurface != null && !videoSourceManager.isRunning()) {
            Log.i("UserRunVideoVM", "Camera permission granted, retrying HDMI pipeline")
            videoSourceManager.start(currentSurface)
        }
    }

    private suspend fun ensureConnected(url: String, force: Boolean) {
        val normalized = url.trim()
        if (!_active.value) return
        if (normalized.isBlank()) return
        if (!force && activeStreamUrl == normalized && connectJob?.isActive == true) return
        if (!force && videoStreamClient.isActiveFor(normalized)) return

        disconnectStream()
        activeStreamUrl = normalized
        connectJob = viewModelScope.launch {
            videoStreamClient.connect(normalized)
        }
    }

    private fun disconnectStream() {
        // Note: WebRTC is NOT shut down here — it's managed by the settings flow
        // in init{} and only shut down in onCleared() or when useWebRTC is toggled off.
        if (_isRecording.value) {
            stopRecording()
        }
        activeStreamUrl = null
        connectJob?.cancel()
        connectJob = null
        frameStaleJob?.cancel()
        frameStaleJob = null
        _lastFrameAtMs.value = 0L
        _frameFormat.value = null
        _latestBitmap.value = null
        viewModelScope.launch {
            videoStreamClient.disconnect()
        }
    }

    fun startRecording() {
        val frameFormat = _frameFormat.value
        if (frameFormat == null) {
            _recordingError.value = "No video stream detected."
            return
        }
        val telemetry = latestTelemetry
        val codec = when (frameFormat) {
            VideoFrameFormat.JPEG -> "mjpeg"
            VideoFrameFormat.H26X -> telemetry?.codec?.takeIf { it.isNotBlank() } ?: when (decoderConfig.codecMime) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
                else -> "h265"
            }
        }
        val width = telemetry?.resolution?.width?.takeIf { it > 0 } ?: if (frameFormat == VideoFrameFormat.JPEG) 1920 else decoderConfig.width
        val height = telemetry?.resolution?.height?.takeIf { it > 0 } ?: if (frameFormat == VideoFrameFormat.JPEG) 1080 else decoderConfig.height
        val fps = telemetry?.fps?.toInt()?.takeIf { it > 0 } ?: 30
        viewModelScope.launch {
            _recordingError.value = null
            val filePath = videoRecorder.startRecording(
                UserVideoRecorder.StartConfig(
                    codec = codec,
                    width = width,
                    height = height,
                    fps = fps
                )
            )
            if (filePath != null) {
                recordingStartedAtMs = System.currentTimeMillis()
                _recordingElapsedMs.value = 0L
                _isRecording.value = true
                startRecordingTicker()
            } else {
                _recordingError.value = "Unable to start local recording."
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val result = videoRecorder.stopRecording()
            stopRecordingTicker()
            _isRecording.value = false
            _recordingElapsedMs.value = 0L
            if (result != null) {
                postRecordRepository.refresh()
                _recordingError.value = null
            } else {
                _recordingError.value = "Unable to finalize the recorded file."
            }
        }
    }

    private fun startRecordingTicker() {
        recordingTickerJob?.cancel()
        recordingTickerJob = viewModelScope.launch {
            while (_isRecording.value) {
                _recordingElapsedMs.value = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)
                delay(1_000)
            }
        }
    }

    private fun stopRecordingTicker() {
        recordingTickerJob?.cancel()
        recordingTickerJob = null
    }

    private fun scheduleFrameStaleTimeout() {
        frameStaleJob?.cancel()
        frameStaleJob = viewModelScope.launch {
            delay(FRAME_STALE_TIMEOUT_MS)
            _lastFrameAtMs.value = 0L
        }
    }

    private fun renderBitmapFrame(frame: VideoFrame) {
        if (System.currentTimeMillis() - lastBitmapDecodeAtMs < JPEG_FRAME_INTERVAL_MS) return
        lastBitmapDecodeAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            val bitmap = BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size)
            if (bitmap != null) {
                _latestBitmap.value = bitmap
            }
        }
    }

    private fun decodeSurfaceFrame(frame: VideoFrame) {
        _latestBitmap.value = null
        surface ?: return
        viewModelScope.launch {
            if (!isDecoderInitialized) {
                initializeDecoder(decoderConfig)
                return@launch
            }
            if (frame.isKeyframe) {
                hasReceivedKeyframe = true
            }
            if (!hasReceivedKeyframe) {
                videoStreamClient.sendRawCommand("{\"cmd\":\"requestKeyFrame\"}")
                return@launch
            }
            videoDecoder.decodeFrame(frame.data, frame.timestamp * 1_000)
        }
    }

    private fun updateDecoderConfig(telemetry: Telemetry) {
        var codecMime = decoderConfig.codecMime
        val codec = telemetry.codec.lowercase()
        if (codec.contains("h264")) {
            codecMime = MediaFormat.MIMETYPE_VIDEO_AVC
        } else if (codec.contains("h265") || codec.contains("hevc")) {
            codecMime = MediaFormat.MIMETYPE_VIDEO_HEVC
        }

        val resolution = telemetry.resolution
        val updated = DecoderConfig(
            codecMime = codecMime,
            width = resolution?.width?.takeIf { it > 0 } ?: decoderConfig.width,
            height = resolution?.height?.takeIf { it > 0 } ?: decoderConfig.height
        )
        if (updated == decoderConfig) return
        decoderConfig = updated
        if (_frameFormat.value == VideoFrameFormat.H26X && surface != null) {
            initializeDecoder(updated)
        }
    }

    private fun initializeDecoder(config: DecoderConfig) {
        val targetSurface = surface ?: return
        viewModelScope.launch {
            videoDecoder.release()
            hasReceivedKeyframe = false
            videoDecoder.initialize(
                codecType = config.codecMime,
                width = config.width,
                height = config.height,
                surface = targetSurface
            )
            isDecoderInitialized = true
        }
    }

    private suspend fun initializeWebRTC(signalingUrl: String) {
        val trimmedUrl = signalingUrl.trim()
        if (trimmedUrl.isBlank()) return

        webrtcInitMutex.withLock {
            val existing = webrtcReceiver
            if (existing != null && webrtcSignalingUrl == trimmedUrl) {
                when (existing.connectionState.value) {
                    is ConnectionState.Connected,
                    is ConnectionState.Connecting -> return
                    else -> { /* reinitialize */ }
                }
            }

            existing?.close()
            webrtcReceiver = null
            webrtcSignalingUrl = trimmedUrl

            try {
                val receiver = WebRTCReceiver(
                    context = appContext,
                    signalingUrl = trimmedUrl,
                    roomId = "camcontrol-room",
                    peerId = buildPeerId("user"),
                    scope = viewModelScope,
                    onVideoFrame = { frameData ->
                        _frameFormat.value = VideoFrameFormat.JPEG
                        _lastFrameAtMs.value = System.currentTimeMillis()
                        scheduleFrameStaleTimeout()
                        if (_isRecording.value) {
                            viewModelScope.launch {
                                videoRecorder.writeJpegFrame(frameData)
                            }
                        }
                        val frame = VideoFrame(
                            data = frameData,
                            timestamp = System.currentTimeMillis(),
                            isKeyframe = true
                        )
                        viewModelScope.launch { renderBitmapFrame(frame) }
                    }
                )
                receiver.initialize()
                receiver.onKeyframeNeeded = { receiver.requestKeyframe() }
                receiver.connect()
                webrtcReceiver = receiver
                Log.i("UserRunVideoVM", "WebRTC receiver connected to $trimmedUrl")
            } catch (e: Exception) {
                Log.e("UserRunVideoVM", "WebRTC init failed: ${e.message}", e)
            }
        }
    }

    private fun shutdownWebRTC() {
        webrtcReceiver?.close()
        webrtcReceiver = null
        webrtcSignalingUrl = null
    }

    private fun buildPeerId(prefix: String): String {
        val androidId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Exception) { "unknown" }
        val raw = "$prefix-${Build.MANUFACTURER}-${Build.MODEL}-${androidId.take(6)}"
        return raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(64)
    }

    override fun onCleared() {
        super.onCleared()
        videoSourceManager.stop()
        shutdownWebRTC()
        disconnectStream()
        videoStreamClient.close()
        viewModelScope.launch {
            videoDecoder.release()
        }
    }
}

private fun formatRecordingElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format("%02d:%02d", minutes, seconds)
}

data class UserRunVideoUiState(
    val cameraUrl: String = "",
    val sourceLabel: String = "",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val detailState: UserRunVideoDetailState = UserRunVideoDetailState.Idle,
    val errorMessage: String? = null,
    val resolutionLabel: String = "--",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val codecLabel: String? = null,
    val fpsLabel: String? = null,
    val showBitmapFrame: Boolean = false,
    val showSurfaceFrame: Boolean = true,
    val canReconnect: Boolean = false,
    val isStreaming: Boolean = false,
    val canRecord: Boolean = false,
    val isRecording: Boolean = false,
    val recordingElapsedLabel: String? = null,
    val recordingErrorMessage: String? = null
)

enum class UserRunVideoDetailState {
    Idle,
    NoUrl,
    WaitingFrames,
    ReceivingFrames,
    Error
}
