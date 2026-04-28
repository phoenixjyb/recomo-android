package com.recomo.remotecontrol.v3dr.ui.screens.recording

import android.app.Application
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.util.DisplayMetrics
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.capture.camera.Camera2Controller
import com.recomo.common.capture.model.Telemetry
import com.recomo.common.capture.recording.VideoEncoder
import com.recomo.common.capture.recording.VideoRecorder
import com.recomo.common.capture.recording.ImuLogger
import com.recomo.common.capture.recording.MetadataCollector
import com.recomo.common.capture.recording.FrameTimestampWriter
import com.recomo.common.capture.recording.CalibWriter
import com.recomo.common.capture.recording.SessionManifestWriter
import com.recomo.common.capture.arcore.ArCorePoseRecorder
import com.recomo.remotecontrol.v3dr.data.SettingsRepository
import com.recomo.remotecontrol.v3dr.data.VioBackendType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private val _cameraFacing = MutableStateFlow(CameraCharacteristics.LENS_FACING_BACK)
    val cameraFacing: StateFlow<Int> = _cameraFacing.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _aeLocked = MutableStateFlow(false)
    val aeLocked: StateFlow<Boolean> = _aeLocked.asStateFlow()

    private val _awbLocked = MutableStateFlow(false)
    val awbLocked: StateFlow<Boolean> = _awbLocked.asStateFlow()

    private val _afMode = MutableStateFlow(Camera2Controller.AfMode.CONTINUOUS)
    val afMode: StateFlow<Camera2Controller.AfMode> = _afMode.asStateFlow()

    private val _sensorOrientation = MutableStateFlow(0)
    val sensorOrientation: StateFlow<Int> = _sensorOrientation.asStateFlow()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _previewSize = MutableStateFlow(Size(1920, 1080))
    val previewSize: StateFlow<Size> = _previewSize.asStateFlow()

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var camera2Controller: Camera2Controller? = null
    private var currentSurface: Surface? = null
    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var previewSizeBeforeRecording: Size? = null

    // Recording components
    private var videoEncoder: VideoEncoder? = null
    private var videoRecorder: VideoRecorder? = null
    private var imuLogger: ImuLogger? = null
    private var metadataCollector: MetadataCollector? = null
    private var frameTimestampWriter: FrameTimestampWriter? = null
    private var calibWriter: CalibWriter? = null
    private var manifestWriter: SessionManifestWriter? = null
    private var arCorePoseRecorder: ArCorePoseRecorder? = null
    private var recordingStartTimeMs: Long = 0L
    private var recordingWidth: Int = 0
    private var recordingHeight: Int = 0
    private var isArCoreEnabled: Boolean = false

    private var sessionCalibFile: File? = null
    private var sessionFrameTimestampsFile: File? = null
    private var sessionMetadataFile: File? = null
    private var sessionManifestFile: File? = null
    private var sessionTrajectoryFile: File? = null

    // ARCore availability state
    private val _arCoreAvailability = MutableStateFlow(ArCorePoseRecorder.ArCoreAvailability.NOT_SUPPORTED)
    val arCoreAvailability: StateFlow<ArCorePoseRecorder.ArCoreAvailability> = _arCoreAvailability.asStateFlow()

    companion object {
        private const val TAG = "CameraViewModel"
    }

    init {
        initializeCameraThread()
        initializeRecordingComponents()
        checkArCoreAvailability()
    }

    private fun initializeCameraThread() {
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    private fun initializeRecordingComponents() {
        videoRecorder = VideoRecorder(getApplication())
        imuLogger = ImuLogger(getApplication(), targetHz = 100)
        metadataCollector = MetadataCollector()
        frameTimestampWriter = FrameTimestampWriter()
        calibWriter = CalibWriter()
        manifestWriter = SessionManifestWriter()
        arCorePoseRecorder = ArCorePoseRecorder(getApplication())
    }

    private fun checkArCoreAvailability() {
        viewModelScope.launch {
            val availability = arCorePoseRecorder?.checkAvailability() 
                ?: ArCorePoseRecorder.ArCoreAvailability.NOT_SUPPORTED
            _arCoreAvailability.value = availability
            Log.i(TAG, "ARCore availability: $availability")
        }
    }

    private fun handleTelemetry(telemetry: Telemetry) {
        _telemetry.value = telemetry
        if (_recordingState.value is RecordingState.Recording) {
            val calibFile = sessionCalibFile
            if (calibFile != null) {
                calibWriter?.writeIfNeeded(
                    file = calibFile,
                    intrinsics = telemetry.cameraIntrinsics,
                    fallbackWidth = recordingWidth,
                    fallbackHeight = recordingHeight,
                    sensorOrientation = _sensorOrientation.value
                )
            }
        }
    }

    fun openCamera(previewSurface: Surface, width: Int, height: Int) {
        currentSurface = previewSurface
        val displayInfo = getDisplayInfo()
        val desiredPreview = Camera2Controller.selectPreviewSize(
            context = getApplication(),
            preferredFacing = _cameraFacing.value,
            desiredWidth = width,
            desiredHeight = height,
            deviceRotationDegrees = displayInfo.rotationDegrees,
            isNaturalLandscape = displayInfo.isNaturalLandscape,
            reason = "openCamera"
        )
        if (_previewSize.value != desiredPreview) {
            Log.i(TAG, "Preview size updated (openCamera): ${_previewSize.value.width}x${_previewSize.value.height} -> ${desiredPreview.width}x${desiredPreview.height}")
            _previewSize.value = desiredPreview
        }
        currentWidth = desiredPreview.width
        currentHeight = desiredPreview.height
        
        viewModelScope.launch {
            try {
                _cameraState.value = CameraState.Opening
                
                val handler = cameraHandler ?: throw IllegalStateException("Camera handler not initialized")
                
                camera2Controller = Camera2Controller(
                    context = getApplication(),
                    backgroundHandler = handler,
                    onTelemetry = { telemetry ->
                        Log.d(TAG, "📊 Telemetry: FPS=${telemetry.fps}, ISO=${telemetry.iso}, Zoom=${telemetry.zoom}")
                        handleTelemetry(telemetry)
                    },
                    onFrameTimestamp = { frameIdx, timestampNs ->
                        frameTimestampWriter?.record(frameIdx, timestampNs)
                    }
                ).apply {
                    setPreferredFacing(_cameraFacing.value)
                    setOutputDimensions(currentWidth, currentHeight)
                    openCamera()
                    startCaptureSession(
                        previewSurface = previewSurface,
                        encoderSurface = null, // No encoder for preview-only mode
                        fps = 30
                    )
                }
                
                _sensorOrientation.value = camera2Controller?.sensorOrientation ?: 0
                
                _cameraState.value = CameraState.Opened(
                    zoomRange = camera2Controller?.zoomRange
                )
                
                // Reset zoom to 1.0x when camera opens
                setZoom(1.0f)
                
                Log.d(TAG, "Camera opened successfully with sensor orientation: ${_sensorOrientation.value}°")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open camera", e)
                _cameraState.value = CameraState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun closeCamera() {
        camera2Controller?.close()
        camera2Controller = null
        _cameraState.value = CameraState.Idle
        _telemetry.value = null
        Log.d(TAG, "Camera closed")
    }

    fun switchCamera() {
        val currentFacing = _cameraFacing.value
        val newFacing = if (currentFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        _cameraFacing.value = newFacing
        Log.d(TAG, "Camera facing switched to: ${if (newFacing == CameraCharacteristics.LENS_FACING_BACK) "BACK" else "FRONT"}")
        
        // Reopen camera with new facing
        val surface = currentSurface
        if (surface != null && currentWidth > 0 && currentHeight > 0) {
            closeCamera()
            openCamera(surface, currentWidth, currentHeight)
        }
    }

    fun setZoom(zoom: Float) {
        camera2Controller?.setZoomRatio(zoom)
        _zoomRatio.value = zoom
    }

    fun setAeLock(lock: Boolean) {
        camera2Controller?.setAeLock(lock)
        _aeLocked.value = lock
    }

    fun setAwbLock(lock: Boolean) {
        camera2Controller?.setAwbLock(lock)
        _awbLocked.value = lock
    }

    fun setAfMode(mode: Camera2Controller.AfMode) {
        camera2Controller?.setAfMode(mode)
        _afMode.value = mode
    }

    // Recording functions
    
    fun startRecording(clipName: String = "v3dr_clip") {
        if (_recordingState.value !is RecordingState.Idle) {
            Log.w(TAG, "Recording already in progress")
            return
        }
        
        viewModelScope.launch {
            try {
                _recordingState.value = RecordingState.Starting
                
                val context = getApplication<Application>()
                val timestamp = System.currentTimeMillis()
                val dateFormat = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                val timeString = dateFormat.format(java.util.Date(timestamp))
                val sessionName = "session_${timeString}"
                
                // Create session directory in app's private external files directory
                // This avoids Android scoped storage restrictions
                val appMoviesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                val v3drDir = File(appMoviesDir, "V3DR")
                v3drDir.mkdirs()
                
                val sessionDir = File(v3drDir, sessionName)
                val dirCreated = sessionDir.mkdirs()
                Log.d(TAG, "Session directory: ${sessionDir.absolutePath}, created: $dirCreated, exists: ${sessionDir.exists()}")
                
                // Read settings
                val settings = settingsRepository.settingsFlow.first()
                recordingWidth = settings.resolution.width
                recordingHeight = settings.resolution.height
                Log.d(TAG, "Applying settings: ${settings.resolution.displayName}, ${settings.bitrate.displayName}, ${settings.frameRate} fps")

                // Keep preview size matched to the view; recording size is handled by encoder surface.
                previewSizeBeforeRecording = _previewSize.value
                
                // Initialize session files
                val videoFile = File(sessionDir, "video.mp4")
                val imuFile = File(sessionDir, "imu.csv")
                val frameTimestampsFile = File(sessionDir, "frame_timestamps.csv")
                val metadataFile = File(sessionDir, "metadata.jsonl")
                val calibFile = File(sessionDir, "calib.json")
                
                sessionCalibFile = calibFile
                sessionFrameTimestampsFile = frameTimestampsFile
                sessionMetadataFile = metadataFile
                sessionManifestFile = File(sessionDir, "manifest.json")

                // Reset per-session helpers
                calibWriter = CalibWriter()
                frameTimestampWriter = FrameTimestampWriter()
                
                // Initialize video encoder with settings
                videoEncoder = VideoEncoder(viewModelScope).apply {
                    configure(
                        width = settings.resolution.width,
                        height = settings.resolution.height,
                        fps = settings.frameRate,
                        bitrate = settings.bitrate.value,
                        codec = "h265"
                    )
                    start()
                    setMuxerSink(videoRecorder)
                }
                
                // Start video recording inside session directory
                videoRecorder?.startRecordingTo(videoFile)
                
                // Start IMU logging and timestamps
                Log.d(TAG, "IMU file path: ${imuFile.absolutePath}")
                imuLogger?.start(imuFile)
                frameTimestampWriter?.start(frameTimestampsFile)
                
                // Start metadata collection
                Log.d(TAG, "Metadata file path: ${metadataFile.absolutePath}")
                metadataCollector?.start(metadataFile)

                // Start ARCore pose recording if ARCore backend is selected and available
                isArCoreEnabled = false
                Log.d(TAG, "VIO backend: ${settings.vioBackend}, ARCore availability: ${_arCoreAvailability.value}")
                if (settings.vioBackend == VioBackendType.ARCORE &&
                    _arCoreAvailability.value == ArCorePoseRecorder.ArCoreAvailability.READY) {
                    val trajectoryFile = File(sessionDir, "trajectory_${ArCorePoseRecorder.ALGORITHM_ID}_tum.txt")
                    sessionTrajectoryFile = trajectoryFile
                    Log.d(TAG, "Starting ARCore pose recording to: ${trajectoryFile.absolutePath}")
                    isArCoreEnabled = arCorePoseRecorder?.start(trajectoryFile, viewModelScope) == true
                    if (isArCoreEnabled) {
                        Log.i(TAG, "ARCore pose recording started: ${trajectoryFile.absolutePath}")
                    } else {
                        Log.w(TAG, "Failed to start ARCore pose recording")
                        sessionTrajectoryFile = null
                    }
                } else {
                    Log.d(TAG, "ARCore not started: backend=${settings.vioBackend}, availability=${_arCoreAvailability.value}")
                }
                
                // Restart camera session with encoder surface
                val encoderSurface = videoEncoder?.inputSurface
                if (encoderSurface != null && currentSurface != null) {
                    camera2Controller?.close()
                    
                    val handler = cameraHandler ?: throw IllegalStateException("Camera handler not initialized")
                    camera2Controller = Camera2Controller(
                        context = context,
                        backgroundHandler = handler,
                        onTelemetry = { telemetry ->
                            handleTelemetry(telemetry)
                            // Record metadata with IMU snapshot
                            metadataCollector?.recordFrame(
                                cameraTelemetry = telemetry,
                                imuTelemetry = imuLogger?.getCurrentTelemetry()
                            )
                        },
                        onFrameTimestamp = { frameIdx, timestampNs ->
                            frameTimestampWriter?.record(frameIdx, timestampNs)
                        }
                    ).apply {
                        setPreferredFacing(_cameraFacing.value)
                        // Telemetry/intrinsics should align to recording dimensions.
                        setOutputDimensions(recordingWidth, recordingHeight)
                        openCamera()
                        startCaptureSession(
                            previewSurface = currentSurface!!,
                            encoderSurface = encoderSurface,
                            fps = 30
                        )
                    }
                    
                    // Restore camera settings
                    setZoom(_zoomRatio.value)
                }
                
                recordingStartTimeMs = System.currentTimeMillis()
                _recordingState.value = RecordingState.Recording(
                    sessionDir = sessionDir,
                    videoFile = videoFile,
                    imuFile = imuFile,
                    frameTimestampsFile = frameTimestampsFile,
                    calibFile = calibFile,
                    metadataFile = metadataFile
                )
                
                // Start duration timer
                startDurationTimer()
                
                Log.i(TAG, "Recording started: $sessionDir")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
                stopRecording()
            }
        }
    }
    
    fun stopRecording() {
        viewModelScope.launch {
            try {
                _recordingState.value = RecordingState.Stopping
                
                // Stop ARCore pose recording first
                var trajectoryFile: File? = null
                if (isArCoreEnabled) {
                    trajectoryFile = arCorePoseRecorder?.stop()
                    if (trajectoryFile != null) {
                        val poseCount = arCorePoseRecorder?.getPoseCount() ?: 0
                        Log.i(TAG, "ARCore pose recording stopped: $poseCount poses saved")
                    }
                    isArCoreEnabled = false
                }
                
                // Stop metadata collection first
                val metadataFile = metadataCollector?.stop()

                // Stop IMU logging
                val imuFile = imuLogger?.stop()

                // Stop frame timestamp logging
                val frameTimestampsFile = frameTimestampWriter?.stop()

                // Stop video encoder
                videoEncoder?.stop()
                videoEncoder = null
                
                // Stop video recorder (finalizes MP4)
                val videoFile = videoRecorder?.stopRecording()
                
                // Restart camera without encoder surface
                if (currentSurface != null) {
                    camera2Controller?.close()
                    previewSizeBeforeRecording?.let { size ->
                        if (_previewSize.value != size) {
                            Log.i(TAG, "Preview size restored (stopRecording): ${_previewSize.value.width}x${_previewSize.value.height} -> ${size.width}x${size.height}")
                            _previewSize.value = size
                        }
                        currentWidth = size.width
                        currentHeight = size.height
                    }
                    openCamera(currentSurface!!, currentWidth, currentHeight)
                }
                
                val state = _recordingState.value
                if (state is RecordingState.Recording) {
                    val calibFile = sessionCalibFile
                    if (calibFile != null) {
                        calibWriter?.ensureWritten(
                            file = calibFile,
                            fallbackWidth = recordingWidth,
                            fallbackHeight = recordingHeight,
                            sensorOrientation = _sensorOrientation.value
                        )
                    }
                    val allFiles = listOfNotNull(videoFile, imuFile, frameTimestampsFile, calibFile, metadataFile, trajectoryFile)
                    val manifestFile = manifestWriter?.write(
                        sessionDir = state.sessionDir,
                        files = allFiles
                    )
                    _recordingState.value = RecordingState.Completed(
                        sessionDir = state.sessionDir,
                        videoFile = videoFile,
                        imuFile = imuFile,
                        frameTimestampsFile = frameTimestampsFile,
                        calibFile = calibFile,
                        metadataFile = metadataFile,
                        manifestFile = manifestFile,
                        durationMs = _recordingDuration.value,
                        trajectoryFile = trajectoryFile
                    )
                    Log.i(TAG, "Recording completed!")
                    Log.i(TAG, "  Session: ${state.sessionDir.absolutePath}")
                    Log.i(TAG, "  Duration: ${_recordingDuration.value / 1000}s")
                    if (trajectoryFile != null) {
                        Log.i(TAG, "  Trajectory: ${trajectoryFile.absolutePath}")
                    }
                } else {
                    _recordingState.value = RecordingState.Idle
                }
                
                _recordingDuration.value = 0L
                sessionCalibFile = null
                sessionFrameTimestampsFile = null
                sessionMetadataFile = null
                sessionManifestFile = null
                sessionTrajectoryFile = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun startDurationTimer() {
        viewModelScope.launch {
            while (_recordingState.value is RecordingState.Recording) {
                _recordingDuration.value = System.currentTimeMillis() - recordingStartTimeMs
                kotlinx.coroutines.delay(100) // Update every 100ms
            }
        }
    }

    private data class DisplayInfo(
        val rotationDegrees: Int,
        val isNaturalLandscape: Boolean
    )

    @Suppress("DEPRECATION")
    private fun getDisplayInfo(): DisplayInfo {
        val windowManager = getApplication<Application>()
            .getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val rotation = display.rotation
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        val isNaturalLandscape = when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> metrics.widthPixels > metrics.heightPixels
            Surface.ROTATION_90, Surface.ROTATION_270 -> metrics.widthPixels < metrics.heightPixels
            else -> metrics.widthPixels > metrics.heightPixels
        }
        val rotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return DisplayInfo(rotationDegrees, isNaturalLandscape)
    }

    override fun onCleared() {
        super.onCleared()
        closeCamera()
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
        Log.d(TAG, "ViewModel cleared")
    }

    sealed class CameraState {
        object Idle : CameraState()
        object Opening : CameraState()
        data class Opened(val zoomRange: android.util.Range<Float>?) : CameraState()
        data class Error(val message: String) : CameraState()
    }

    sealed class RecordingState {
        object Idle : RecordingState()
        object Starting : RecordingState()
        data class Recording(
            val sessionDir: File,
            val videoFile: File?,
            val imuFile: File,
            val frameTimestampsFile: File,
            val calibFile: File,
            val metadataFile: File
        ) : RecordingState()
        object Stopping : RecordingState()
        data class Completed(
            val sessionDir: File,
            val videoFile: File?,
            val imuFile: File?,
            val frameTimestampsFile: File?,
            val calibFile: File?,
            val metadataFile: File?,
            val manifestFile: File?,
            val durationMs: Long,
            val trajectoryFile: File? = null  // ARCore trajectory if recorded
        ) : RecordingState()
        data class Error(val message: String) : RecordingState()
    }
}
