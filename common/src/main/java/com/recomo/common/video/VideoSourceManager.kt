package com.recomo.common.video

import android.content.Context
import android.util.Log
import android.view.Surface
import com.recomo.common.model.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages video source switching between WebSocket variants and HDMI-USB pipelines.
 * Handles pipeline lifecycle. Persistence of the selected source is the caller's
 * responsibility (ViewModel / SettingsRepository).
 */
class VideoSourceManager(
    private val context: Context,
    initialSource: VideoSource = VideoSource.WS_ORIN
) {
    companion object {
        private const val TAG = "VideoSourceManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentPipeline: VideoPipeline? = null
    private var currentSurface: Surface? = null
    private var pipelineStateJob: Job? = null
    private var pipelineErrorJob: Job? = null

    private val _currentSource = MutableStateFlow(initialSource)
    val currentSource: StateFlow<VideoSource> = _currentSource.asStateFlow()

    private val _connectionState = MutableStateFlow(PipelineState.DISCONNECTED)
    val connectionState: StateFlow<PipelineState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hdmiDeviceAvailable = MutableStateFlow(false)
    val hdmiDeviceAvailable: StateFlow<Boolean> = _hdmiDeviceAvailable.asStateFlow()

    // UVC pipeline for device detection, Camera2 pipeline for actual streaming
    private val uvcPipeline by lazy { UvcVideoPipeline(context) }
    private val camera2Pipeline by lazy { HdmiUsbVideoPipeline(context) }

    init {
        Log.i(TAG, "VideoSourceManager initializing with source=$initialSource")
        checkHdmiDeviceAvailability()
    }

    /**
     * Check if HDMI capture device is available.
     * Uses UVC pipeline for detection (more reliable than Camera2).
     */
    fun checkHdmiDeviceAvailability() {
        Log.i(TAG, "checkHdmiDeviceAvailability() called")
        _hdmiDeviceAvailable.value = uvcPipeline.isDeviceAvailable()
        Log.i(TAG, "HDMI device available: ${_hdmiDeviceAvailable.value}")

        uvcPipeline.getDeviceInfo()?.let {
            Log.i(TAG, "Device info: $it")
        }
    }

    /**
     * Switch to a different video source.
     * @param source The target video source
     * @param surface The surface to render video to (required for HDMI, optional for WebSocket)
     */
    fun switchSource(source: VideoSource, surface: Surface? = null) {
        Log.i(TAG, "Switching video source from ${_currentSource.value} to $source")

        // Stop current pipeline and cancel stale collector jobs
        pipelineStateJob?.cancel()
        pipelineErrorJob?.cancel()
        currentPipeline?.stop()
        currentPipeline = null

        _currentSource.value = source
        currentSurface = surface

        when (source) {
            VideoSource.WS_PHONE, VideoSource.WS_ORIN, VideoSource.WEBRTC_ORIN -> {
                // WS and WebRTC pipelines are managed by the video ViewModel
                _connectionState.value = PipelineState.DISCONNECTED
                _errorMessage.value = null
                Log.i(TAG, "Switched to $source source (managed by ViewModel)")
            }

            VideoSource.HDMI_USB -> {
                if (surface == null) {
                    // Surface not ready yet — source is marked, pipeline will start
                    // when start() or switchSource() is called again with a surface.
                    Log.i(TAG, "HDMI_USB selected but no surface yet — deferring pipeline start")
                    _connectionState.value = PipelineState.DISCONNECTED
                    _errorMessage.value = null
                    return
                }

                if (!_hdmiDeviceAvailable.value) {
                    checkHdmiDeviceAvailability()
                    if (!_hdmiDeviceAvailable.value) {
                        _errorMessage.value = "HDMI capture device not connected"
                        _connectionState.value = PipelineState.ERROR
                        return
                    }
                }

                // Use UVC pipeline for HDMI capture.
                // Camera2 doesn't expose USB capture cards on most Samsung tablets.
                currentPipeline = uvcPipeline
                uvcPipeline.start(surface)

                // Forward pipeline state
                pipelineStateJob = scope.launch {
                    uvcPipeline.connectionState.collect { state ->
                        _connectionState.value = state
                    }
                }
                pipelineErrorJob = scope.launch {
                    uvcPipeline.errorMessage.collect { error ->
                        _errorMessage.value = error
                    }
                }
            }
        }
    }

    /**
     * Start the current video source pipeline.
     */
    fun start(surface: Surface) {
        currentSurface = surface
        when (_currentSource.value) {
            VideoSource.WS_PHONE, VideoSource.WS_ORIN, VideoSource.WEBRTC_ORIN -> {
                Log.d(TAG, "${_currentSource.value} source - start handled by ViewModel")
            }
            VideoSource.HDMI_USB -> {
                if (currentPipeline == null) {
                    switchSource(VideoSource.HDMI_USB, surface)
                } else {
                    currentPipeline?.start(surface)
                }
            }
        }
    }

    /**
     * Stop the current video pipeline.
     */
    fun stop() {
        Log.i(TAG, "Stopping video source manager")
        currentPipeline?.stop()
        _connectionState.value = PipelineState.DISCONNECTED
    }

    fun isRunning(): Boolean = currentPipeline?.isRunning() == true

    fun isHdmiSource(): Boolean = _currentSource.value == VideoSource.HDMI_USB

    fun isWebSocketSource(): Boolean =
        _currentSource.value == VideoSource.WS_ORIN ||
            _currentSource.value == VideoSource.WS_PHONE
}
