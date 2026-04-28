package com.recomo.common.capture.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.*
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import com.recomo.common.capture.model.Telemetry
import com.recomo.common.capture.model.CameraIntrinsics
import com.recomo.common.capture.model.CropRegion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Camera2Controller(
    context: Context,
    private val backgroundHandler: Handler,
    private val onTelemetry: (Telemetry) -> Unit = {},
    private val onFrameTimestamp: ((frameIdx: Long, timestampNs: Long) -> Unit)? = null
) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var repeatingRequestBuilder: CaptureRequest.Builder? = null
    private var telemetryCallback: CameraCaptureSession.CaptureCallback? = null
    var zoomRange: android.util.Range<Float>? = null
        private set
    var sensorOrientation: Int = 0
        private set
    @Volatile private var preferredFacing: Int = CameraCharacteristics.LENS_FACING_BACK
    @Volatile private var outputWidth: Int = 0
    @Volatile private var outputHeight: Int = 0
    private var intrinsicsCalculator: IntrinsicsCalculator? = null
    enum class AfMode { CONTINUOUS, LOCKED }
    @Volatile private var currentAfMode: AfMode = AfMode.CONTINUOUS

    companion object {
        private const val TAG = "Camera2Controller"

        fun selectPreviewSize(
            context: Context,
            preferredFacing: Int,
            desiredWidth: Int,
            desiredHeight: Int,
            deviceRotationDegrees: Int,
            isNaturalLandscape: Boolean,
            reason: String
        ): Size {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = when (preferredFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> findFrontCameraId(cameraManager)
                else -> findBackCameraId(cameraManager)
            }
            if (cameraId == null) {
                Log.w(TAG, "[$reason] No camera found for facing=$preferredFacing; using ${desiredWidth}x${desiredHeight}")
                return Size(desiredWidth, desiredHeight)
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = streamConfigMap?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
                ?.toList()
                .orEmpty()
            if (sizes.isEmpty()) {
                Log.w(TAG, "[$reason] No SurfaceTexture sizes for cameraId=$cameraId; using ${desiredWidth}x${desiredHeight}")
                return Size(desiredWidth, desiredHeight)
            }

            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val adjustedSensorOrientation = if (isNaturalLandscape) {
                (sensorOrientation + 270) % 360
            } else {
                sensorOrientation
            }
            val rotationDegrees = if (preferredFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                (adjustedSensorOrientation + deviceRotationDegrees) % 360
            } else {
                (adjustedSensorOrientation - deviceRotationDegrees + 360) % 360
            }
            val shouldSwap = rotationDegrees == 90 || rotationDegrees == 270
            val targetWidth = if (shouldSwap) desiredHeight else desiredWidth
            val targetHeight = if (shouldSwap) desiredWidth else desiredHeight
            val targetAspect = if (targetWidth > 0 && targetHeight > 0) {
                targetWidth.toFloat() / targetHeight.toFloat()
            } else {
                null
            }
            val desiredArea = if (targetWidth > 0 && targetHeight > 0) {
                targetWidth * targetHeight
            } else {
                null
            }

            val best = sizes.minWithOrNull { a, b ->
                val aspectDeltaA = targetAspect?.let { kotlin.math.abs(it - a.width.toFloat() / a.height.toFloat()) } ?: 0f
                val aspectDeltaB = targetAspect?.let { kotlin.math.abs(it - b.width.toFloat() / b.height.toFloat()) } ?: 0f
                if (aspectDeltaA != aspectDeltaB) {
                    return@minWithOrNull aspectDeltaA.compareTo(aspectDeltaB)
                }
                val areaDeltaA = desiredArea?.let { kotlin.math.abs((a.width * a.height) - it) } ?: -(a.width * a.height)
                val areaDeltaB = desiredArea?.let { kotlin.math.abs((b.width * b.height) - it) } ?: -(b.width * b.height)
                if (areaDeltaA != areaDeltaB) {
                    return@minWithOrNull areaDeltaA.compareTo(areaDeltaB)
                }
                // Prefer larger if aspect/area tie
                (b.width * b.height).compareTo(a.width * a.height)
            } ?: sizes.first()

            Log.i(
                TAG,
                "[$reason] Selected preview size ${best.width}x${best.height} for cameraId=$cameraId, " +
                    "desired=${desiredWidth}x${desiredHeight}, target=${targetWidth}x${targetHeight}, " +
                    "rotation=$rotationDegrees, naturalLandscape=$isNaturalLandscape, sizes=${sizes.size}"
            )
            return Size(best.width, best.height)
        }

        private fun findBackCameraId(cameraManager: CameraManager): String? {
            if (cameraManager.cameraIdList.contains("0")) {
                val ch = cameraManager.getCameraCharacteristics("0")
                if (ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return "0"
            }
            var fallback: String? = null
            for (cameraId in cameraManager.cameraIdList) {
                val ch = cameraManager.getCameraCharacteristics(cameraId)
                val facing = ch.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (fallback == null) fallback = cameraId
                    val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                    if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                        return cameraId
                    }
                }
            }
            return fallback
        }

        private fun findFrontCameraId(cameraManager: CameraManager): String? {
            var fallback: String? = null
            for (cameraId in cameraManager.cameraIdList) {
                val ch = cameraManager.getCameraCharacteristics(cameraId)
                val facing = ch.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (fallback == null) fallback = cameraId
                    val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                    if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                        return cameraId
                    }
                }
            }
            return fallback
        }
    }

    fun isOpen(): Boolean = cameraDevice != null

    fun setPreferredFacing(facing: Int) {
        preferredFacing = facing
    }

    private class IntrinsicsCalculator(characteristics: CameraCharacteristics) {
        private val activeArray: Rect? = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        private val fxActive: Float?
        private val fyActive: Float?
        private val cxActive: Float?
        private val cyActive: Float?

        init {
            var fx: Float? = null
            var fy: Float? = null
            var cx: Float? = null
            var cy: Float? = null
            val arrayRect = activeArray
            if (arrayRect != null) {
                val lens = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
                if (lens != null && lens.size >= 4) {
                    fx = lens[0]
                    fy = lens[1]
                    cx = lens[2]
                    cy = lens[3]
                } else {
                    val focalLength = characteristics
                        .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        ?.firstOrNull()
                    val phys = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    if (focalLength != null && phys != null) {
                        val aw = arrayRect.width().toFloat()
                        val ah = arrayRect.height().toFloat()
                        fx = (focalLength * (aw / phys.width)).toFloat()
                        fy = (focalLength * (ah / phys.height)).toFloat()
                        cx = arrayRect.left + aw / 2f
                        cy = arrayRect.top + ah / 2f
                    }
                }
            }
            fxActive = fx
            fyActive = fy
            cxActive = cx
            cyActive = cy
        }

        fun compute(
            cropRegion: Rect?,
            outWidth: Int,
            outHeight: Int,
            zoomRatio: Float?
        ): CameraIntrinsics? {
            val arrayRect = activeArray ?: return null
            val fx = fxActive ?: return null
            val fy = fyActive ?: return null
            val cx = cxActive ?: return null
            val cy = cyActive ?: return null
            if (outWidth <= 0 || outHeight <= 0) return null
            val crop = clampCrop(cropRegion, arrayRect)
            val cw = crop.width().toFloat()
            val ch = crop.height().toFloat()
            if (cw <= 0f || ch <= 0f) return null
            val sx = outWidth.toFloat() / cw
            val sy = outHeight.toFloat() / ch
            val fxOut = fx * sx
            val fyOut = fy * sy
            val cxOut = (cx - crop.left) * sx
            val cyOut = (cy - crop.top) * sy
            val k = listOf(
                fxOut, 0f, cxOut,
                0f, fyOut, cyOut,
                0f, 0f, 1f
            )
            val p = listOf(
                fxOut, 0f, cxOut, 0f,
                0f, fyOut, cyOut, 0f,
                0f, 0f, 1f, 0f
            )
            return CameraIntrinsics(
                width = outWidth,
                height = outHeight,
                fx = fxOut,
                fy = fyOut,
                cx = cxOut,
                cy = cyOut,
                k = k,
                p = p,
                zoomRatio = zoomRatio,
                crop = CropRegion(
                    left = crop.left,
                    top = crop.top,
                    right = crop.right,
                    bottom = crop.bottom
                )
            )
        }

        private fun clampCrop(candidate: Rect?, bounds: Rect): Rect {
            if (candidate == null) return Rect(bounds)
            val left = candidate.left.coerceIn(bounds.left, bounds.right - 1)
            val top = candidate.top.coerceIn(bounds.top, bounds.bottom - 1)
            val right = candidate.right.coerceIn(left + 1, bounds.right)
            val bottom = candidate.bottom.coerceIn(top + 1, bounds.bottom)
            return Rect(left, top, right, bottom)
        }
    }

    fun setOutputDimensions(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            outputWidth = width
            outputHeight = height
        } else {
            Log.w(TAG, "Ignoring invalid output dimensions: ${width}x${height}")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openCamera() {
        val cameraId = when (preferredFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> getFrontCameraId()
            else -> getBackCameraId()
        } ?: throw CameraAccessException(CameraAccessException.CAMERA_DISABLED, "No matching camera found")
        Log.d(TAG, "Opening camera: $cameraId")

        cameraDevice = suspendCancellableCoroutine { continuation ->
            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera ${camera.id} opened.")
                    if (continuation.isActive) continuation.resume(camera)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera ${camera.id} disconnected.")
                    camera.close(); cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    val errorMessage = "Camera ${camera.id} error: $error"
                    Log.e(TAG, errorMessage)
                    if (continuation.isActive) continuation.resumeWithException(CameraAccessException(error, errorMessage))
                    cameraDevice = null
                }
            }
            try {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            } catch (e: CameraAccessException) {
                continuation.resumeWithException(e)
            }
            continuation.invokeOnCancellation { close() }
        }

        val characteristics = cameraManager.getCameraCharacteristics(cameraDevice!!.id)
        zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        Log.i(TAG, "Camera zoom range: $zoomRange")
        Log.i(TAG, "Camera sensor orientation: $sensorOrientation°")
        intrinsicsCalculator = IntrinsicsCalculator(characteristics)
    }

    suspend fun startCaptureSession(previewSurface: Surface, encoderSurface: Surface?, fps: Int = 30, highSpeed: Boolean = false) {
        val device = cameraDevice ?: throw IllegalStateException("Camera not open")
        Log.d(TAG, "Starting capture session. encoderSurface: ${if (encoderSurface != null) "PROVIDED" else "NULL"}")

        captureSession = suspendCancellableCoroutine { continuation ->
            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Capture session configured.")
                    if (continuation.isActive) continuation.resume(session)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    val errorMessage = "Capture session configuration failed"
                    Log.e(TAG, errorMessage)
                    if (continuation.isActive) continuation.resumeWithException(RuntimeException(errorMessage))
                }
            }
            val surfaces = mutableListOf(previewSurface)
            encoderSurface?.let { 
                surfaces.add(it)
                Log.d(TAG, "✅ Encoder surface added to capture session")
            } ?: Log.w(TAG, "⚠️ No encoder surface - video encoding will NOT work!")
            
            if (highSpeed) {
                device.createConstrainedHighSpeedCaptureSession(surfaces, stateCallback, backgroundHandler)
            } else {
                device.createCaptureSession(surfaces, stateCallback, backgroundHandler)
            }
        }

        startRepeatingRequest(previewSurface, encoderSurface, fps, highSpeed)
    }

    private fun startRepeatingRequest(previewSurface: Surface, encoderSurface: Surface?, fps: Int, highSpeed: Boolean) {
        val session = captureSession ?: throw IllegalStateException("Session not started")
        val device = cameraDevice ?: throw IllegalStateException("Camera not open")

        try {
            // Use TEMPLATE_RECORD when encoder surface is present for proper video timestamps
            val template = if (encoderSurface != null) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
            Log.d(TAG, "Creating capture request with template: ${if (encoderSurface != null) "RECORD" else "PREVIEW"}")
            
            repeatingRequestBuilder = device.createCaptureRequest(template).apply {
                addTarget(previewSurface)
                encoderSurface?.let { 
                    addTarget(it)
                    Log.d(TAG, "✅ Encoder surface added as capture target")
                } ?: Log.w(TAG, "⚠️ No encoder surface in capture request!")
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(fps, fps))
                set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
            }
            telemetryCallback = object : CameraCaptureSession.CaptureCallback() {
                private var lastTsNs: Long = 0L
                private var lastEmitMs: Long = 0L
                private var fpsSmoothed: Float? = null
                private var frameCount: Long = 0
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    frameCount++
                    if (frameCount % 30L == 0L) {
                        Log.d(TAG, "📹 Frame $frameCount captured")
                    }
                    
                    // Calculate FPS on every frame for accuracy
                    val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L
                    if (ts > 0L) {
                        try {
                            onFrameTimestamp?.invoke(frameCount - 1, ts)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Frame timestamp callback error", t)
                        }
                    }
                    if (lastTsNs != 0L && ts > lastTsNs) {
                        val dt = ts - lastTsNs
                        val instFps = 1_000_000_000.0f / dt.toFloat()
                        fpsSmoothed = if (fpsSmoothed == null) instFps else (0.9f * fpsSmoothed!! + 0.1f * instFps)
                    }
                    lastTsNs = ts
                    
                    // Emit telemetry at 10 Hz to avoid overwhelming UI
                    val nowMs = SystemClock.elapsedRealtime()
                    if (nowMs - lastEmitMs < 100) return
                    lastEmitMs = nowMs
                    val zoomRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO) ?: zoomRange?.lower ?: 1.0f
                    val intrinsicsPayload = intrinsicsCalculator?.compute(
                        result.get(CaptureResult.SCALER_CROP_REGION),
                        outputWidth,
                        outputHeight,
                        zoomRatio
                    )
                    val telemetry = Telemetry(
                        af = result.get(CaptureResult.CONTROL_AF_STATE),
                        ae = result.get(CaptureResult.CONTROL_AE_STATE),
                        iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                        expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                        zoom = zoomRatio,
                        fps = fpsSmoothed,
                        cameraIntrinsics = intrinsicsPayload
                    )
                    try { onTelemetry(telemetry) } catch (t: Throwable) { Log.w(TAG, "Telemetry callback error", t) }
                }
            }
            if (highSpeed) {
                val hsSession = session as? CameraConstrainedHighSpeedCaptureSession
                val request = repeatingRequestBuilder!!.build()
                val burst = hsSession?.createHighSpeedRequestList(request)
                if (burst != null) {
                    session.setRepeatingBurst(burst, telemetryCallback, backgroundHandler)
                } else {
                    session.setRepeatingRequest(request, telemetryCallback, backgroundHandler)
                }
            } else {
                session.setRepeatingRequest(repeatingRequestBuilder!!.build(), telemetryCallback, backgroundHandler)
            }
            Log.d(TAG, "Repeating request started.")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start repeating request", e)
        }
    }

    fun setZoomRatio(zoom: Float) {
        Log.d(TAG, "🔍 setZoomRatio called: $zoom")
        val range = zoomRange ?: run {
            Log.w(TAG, "🔍 Zoom range is null")
            return
        }
        val builder = repeatingRequestBuilder ?: run {
            Log.w(TAG, "🔍 Repeating request builder is null")
            return
        }
        val session = captureSession ?: run {
            Log.w(TAG, "🔍 Capture session is null")
            return
        }
        val clampedZoom = zoom.coerceIn(range.lower, range.upper)
        Log.d(TAG, "🔍 Setting zoom ratio: $clampedZoom (range: ${range.lower}-${range.upper})")
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clampedZoom)
        try {
            session.setRepeatingRequest(builder.build(), telemetryCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to update zoom", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Camera session is closed, cannot update zoom", e)
        }
    }

    fun setAeLock(lock: Boolean) {
        val builder = repeatingRequestBuilder ?: return
        val session = captureSession ?: return
        builder.set(CaptureRequest.CONTROL_AE_LOCK, lock)
        try {
            session.setRepeatingRequest(builder.build(), telemetryCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to update AE lock", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Camera session is closed, cannot update AE lock", e)
        }
    }

    fun setAwbLock(lock: Boolean) {
        val builder = repeatingRequestBuilder ?: return
        val session = captureSession ?: return
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, lock)
        try {
            session.setRepeatingRequest(builder.build(), telemetryCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to update AWB lock", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Camera session is closed, cannot update AWB lock", e)
        }
    }

    fun setAfMode(mode: AfMode) {
        val builder = repeatingRequestBuilder ?: return
        val session = captureSession ?: return
        if (currentAfMode == mode) {
            Log.d(TAG, "AF mode already $mode")
            return
        }
        currentAfMode = mode
        try {
            when (mode) {
                AfMode.CONTINUOUS -> {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                    session.setRepeatingRequest(builder.build(), telemetryCallback, backgroundHandler)
                }
                AfMode.LOCKED -> {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                    session.capture(builder.build(), null, backgroundHandler)
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                    session.setRepeatingRequest(builder.build(), telemetryCallback, backgroundHandler)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to update AF mode", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Camera session is closed, cannot update AF mode", e)
        }
    }

    fun close() {
        try { captureSession?.close(); captureSession = null } catch (e: Exception) { Log.e(TAG, "Error closing capture session", e) }
        try { cameraDevice?.close(); cameraDevice = null } catch (e: Exception) { Log.e(TAG, "Error closing camera device", e) }
        Log.d(TAG, "Camera closed.")
    }

    private fun getBackCameraId(): String? {
        if (cameraManager.cameraIdList.contains("0")) {
            val ch = cameraManager.getCameraCharacteristics("0")
            if (ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return "0"
        }
        var fallback: String? = null
        for (cameraId in cameraManager.cameraIdList) {
            val ch = cameraManager.getCameraCharacteristics(cameraId)
            val facing = ch.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                if (fallback == null) fallback = cameraId
                val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                    return cameraId
                }
            }
        }
        return fallback
    }

    private fun getFrontCameraId(): String? {
        // Prefer a logical camera if available, otherwise first front camera
        var fallback: String? = null
        for (cameraId in cameraManager.cameraIdList) {
            val ch = cameraManager.getCameraCharacteristics(cameraId)
            val facing = ch.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                if (fallback == null) fallback = cameraId
                val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {
                    return cameraId
                }
            }
        }
        return fallback
    }
}
