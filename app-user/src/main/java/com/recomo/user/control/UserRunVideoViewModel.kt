package com.recomo.user.control

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaFormat
import android.view.Surface
import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.Telemetry
import com.recomo.common.model.VideoFrame
import com.recomo.common.model.VideoFrameFormat
import com.recomo.common.video.VideoDecoder
import com.recomo.user.data.postrecord.UserPostRecordRepository
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.data.video.UserVideoRecorder
import com.recomo.user.data.video.UserVideoStreamClient
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.serialization.json.Json

private const val JPEG_FRAME_INTERVAL_MS = 120L
private const val FRAME_STALE_TIMEOUT_MS = 2000L

@HiltViewModel
class UserRunVideoViewModel @Inject constructor(
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
    private val _active = MutableStateFlow(false)
    private val _cameraUrl = MutableStateFlow("")
    private val _latestBitmap = MutableStateFlow<Bitmap?>(null)
    private val _frameFormat = MutableStateFlow<VideoFrameFormat?>(null)
    private val _lastFrameAtMs = MutableStateFlow(0L)
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _recordingElapsedMs = MutableStateFlow(0L)
    private val _recordingError = MutableStateFlow<String?>(null)

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

    private data class VideoUiInputs(
        val cameraUrl: String,
        val connectionState: ConnectionState,
        val telemetry: Telemetry?,
        val frameFormat: VideoFrameFormat?,
        val lastFrameAtMs: Long,
        val isRecording: Boolean,
        val recordingElapsedMs: Long,
        val recordingError: String?
    )

    val latestBitmap: StateFlow<Bitmap?> = _latestBitmap.asStateFlow()
    val state: StateFlow<UserRunVideoUiState>
        get() = uiState

    val uiState: StateFlow<UserRunVideoUiState> = combine(
        _cameraUrl,
        videoStreamClient.connectionState,
        videoStreamClient.telemetry,
        _frameFormat,
        _lastFrameAtMs,
        _isRecording,
        _recordingElapsedMs,
        _recordingError
    ) { values ->
        val cameraUrl = values[0] as String
        val connectionState = values[1] as ConnectionState
        val telemetry = values[2] as? Telemetry
        val frameFormat = values[3] as? VideoFrameFormat
        val lastFrameAtMs = values[4] as Long
        val isRecording = values[5] as Boolean
        val recordingElapsedMs = values[6] as Long
        val recordingError = values[7] as? String
        VideoUiInputs(
            cameraUrl = cameraUrl,
            connectionState = connectionState,
            telemetry = telemetry,
            frameFormat = frameFormat,
            lastFrameAtMs = lastFrameAtMs,
            isRecording = isRecording,
            recordingElapsedMs = recordingElapsedMs,
            recordingError = recordingError
        )
    }.map { inputs ->
        val streamHealthy = inputs.connectionState is ConnectionState.Connected &&
            inputs.lastFrameAtMs > 0L
        val canRecord = streamHealthy
        UserRunVideoUiState(
            cameraUrl = inputs.cameraUrl,
            connectionState = inputs.connectionState,
            detailState = when {
                inputs.connectionState is ConnectionState.Error -> UserRunVideoDetailState.Error
                streamHealthy -> UserRunVideoDetailState.ReceivingFrames
                inputs.connectionState is ConnectionState.Connected -> UserRunVideoDetailState.WaitingFrames
                inputs.cameraUrl.isBlank() -> UserRunVideoDetailState.NoUrl
                else -> UserRunVideoDetailState.Idle
            },
            errorMessage = (inputs.connectionState as? ConnectionState.Error)?.message,
            resolutionLabel = inputs.telemetry?.resolution?.let { "${it.width}×${it.height}" } ?: "--",
            codecLabel = inputs.telemetry?.codec?.ifBlank { null } ?: when (inputs.frameFormat) {
                VideoFrameFormat.JPEG -> "JPEG"
                VideoFrameFormat.H26X -> "H.26x"
                null -> null
            },
            fpsLabel = inputs.telemetry?.fps?.takeIf { it > 0f }?.let { String.format("%2.0f fps", it) },
            showBitmapFrame = inputs.frameFormat == VideoFrameFormat.JPEG,
            showSurfaceFrame = inputs.frameFormat != VideoFrameFormat.JPEG,
            canReconnect = inputs.cameraUrl.isNotBlank(),
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
                // Always use orinCameraUrl — this is the Orin's JPEG WS server at port 9091.
                // Do not fall back to cameraUrl (phone WebSocket) as it is a different service.
                val resolvedUrl = settings.orinCameraUrl
                _cameraUrl.value = resolvedUrl
                if (_active.value) {
                    ensureConnected(resolvedUrl, force = activeStreamUrl != resolvedUrl)
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
        viewModelScope.launch {
            videoDecoder.release()
            isDecoderInitialized = false
            hasReceivedKeyframe = false
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

    override fun onCleared() {
        super.onCleared()
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
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val detailState: UserRunVideoDetailState = UserRunVideoDetailState.Idle,
    val errorMessage: String? = null,
    val resolutionLabel: String = "--",
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
