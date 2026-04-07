package com.recomo.remotecontrol.camviewer.video

import android.content.Context
import android.util.Log
import android.view.Surface
import com.recomo.remotecontrol.camviewer.data.model.VideoSource
import com.recomo.remotecontrol.camviewer.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages video source switching between WebSocket variants and HDMI-USB pipelines.
 * Handles pipeline lifecycle and persists user selection.
 */
@Singleton
class VideoSourceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "VideoSourceManager"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var currentPipeline: VideoPipeline? = null
    private var currentSurface: Surface? = null
    private var pipelineStateJob: kotlinx.coroutines.Job? = null
    private var pipelineErrorJob: kotlinx.coroutines.Job? = null
    
    private val _currentSource = MutableStateFlow(VideoSource.WEBSOCKET)
    val currentSource: StateFlow<VideoSource> = _currentSource.asStateFlow()
    
    private val _connectionState = MutableStateFlow(PipelineState.DISCONNECTED)
    val connectionState: StateFlow<PipelineState> = _connectionState.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _hdmiDeviceAvailable = MutableStateFlow(false)
    val hdmiDeviceAvailable: StateFlow<Boolean> = _hdmiDeviceAvailable.asStateFlow()
    
    // UVC pipeline for HDMI capture devices
    private val uvcPipeline by lazy { UvcVideoPipeline(context) }
    
    // Legacy Camera2 detection for fallback
    private val hdmiPipeline by lazy { HdmiUsbVideoPipeline(context) }
    
    init {
        Log.i(TAG, "VideoSourceManager initializing...")
        // Load saved source and check device availability
        scope.launch {
            val settings = settingsRepository.settings.first()
            _currentSource.value = settings.videoSource
            Log.i(TAG, "Loaded video source setting: ${settings.videoSource}")
            checkHdmiDeviceAvailability()
        }
    }
    
    /**
     * Check if HDMI capture device is available.
     * Uses UVC pipeline for detection (more reliable than Camera2).
     */
    fun checkHdmiDeviceAvailability() {
        Log.i(TAG, "checkHdmiDeviceAvailability() called")
        // Try UVC detection first (more reliable for HDMI capture cards)
        _hdmiDeviceAvailable.value = uvcPipeline.isDeviceAvailable()
        Log.i(TAG, "HDMI device available: ${_hdmiDeviceAvailable.value}")
        
        // Log device info if available
        uvcPipeline.getDeviceInfo()?.let {
            Log.i(TAG, "Device info: $it")
        }
    }
    
    /**
     * Switch to a different video source.
     * @param source The target video source
     * @param surface The surface to render video to (required for HDMI, optional for WebSocket which uses its own surface)
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
        
        // Save preference
        scope.launch {
            settingsRepository.setVideoSource(source)
        }
        
        when (source) {
            VideoSource.WEBSOCKET -> {
                // WebSocket pipeline is managed by VideoViewModel
                // Just update state - the VideoViewModel will handle the actual connection
                _connectionState.value = PipelineState.DISCONNECTED
                _errorMessage.value = null
                Log.i(TAG, "Switched to WebSocket source (managed by VideoViewModel)")
            }
            VideoSource.WEBSOCKET_ORIN -> {
                // Orin WebSocket pipeline is also managed by VideoViewModel
                _connectionState.value = PipelineState.DISCONNECTED
                _errorMessage.value = null
                Log.i(TAG, "Switched to Orin WebSocket source (managed by VideoViewModel)")
            }
            
            VideoSource.HDMI_USB -> {
                if (surface == null) {
                    _errorMessage.value = "No surface provided for HDMI"
                    _connectionState.value = PipelineState.ERROR
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
                
                // Use UVC pipeline for HDMI capture
                currentPipeline = uvcPipeline
                uvcPipeline.start(surface)
                
                // Forward pipeline state (cancel previous collectors first)
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
     * @param surface The surface to render video to
     */
    fun start(surface: Surface) {
        currentSurface = surface
        
        when (_currentSource.value) {
            VideoSource.WEBSOCKET -> {
                // WebSocket handled by VideoViewModel
                Log.d(TAG, "WebSocket source - start handled by VideoViewModel")
            }
            VideoSource.WEBSOCKET_ORIN -> {
                // WebSocket handled by VideoViewModel
                Log.d(TAG, "Orin WebSocket source - start handled by VideoViewModel")
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
    
    /**
     * Check if the current pipeline is running.
     */
    fun isRunning(): Boolean {
        return currentPipeline?.isRunning() == true
    }
    
    /**
     * Check if using HDMI source.
     */
    fun isHdmiSource(): Boolean = _currentSource.value == VideoSource.HDMI_USB
    
    /**
     * Check if using WebSocket source.
     */
    fun isWebSocketSource(): Boolean =
        _currentSource.value == VideoSource.WEBSOCKET ||
            _currentSource.value == VideoSource.WEBSOCKET_ORIN
}
