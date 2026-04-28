package com.recomo.common.video

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Video pipeline for HDMI-to-USB capture devices using Camera2 API.
 * Detects external cameras (UVC devices) and streams video directly to a Surface.
 *
 * Note: Some UVC devices may not be exposed via Camera2 API and require
 * the UVCCamera library for full support.
 */
class HdmiUsbVideoPipeline(
    private val context: Context
) : VideoPipeline {

    companion object {
        private const val TAG = "HdmiUsbPipeline"
        // USB Video Class codes
        private const val USB_CLASS_VIDEO = 14
        private const val USB_SUBCLASS_VIDEO_CONTROL = 1
    }

    private val _connectionState = MutableStateFlow(PipelineState.DISCONNECTED)
    override val connectionState: StateFlow<PipelineState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var surface: Surface? = null

    override fun start(surface: Surface) {
        if (_connectionState.value == PipelineState.CONNECTED ||
            _connectionState.value == PipelineState.CONNECTING) {
            Log.w(TAG, "Pipeline already running or connecting")
            return
        }

        this.surface = surface
        _connectionState.value = PipelineState.CONNECTING
        _errorMessage.value = null

        // Check camera permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            _errorMessage.value = "Camera permission not granted"
            _connectionState.value = PipelineState.ERROR
            return
        }

        // Start camera thread
        cameraThread = HandlerThread("HdmiCameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findExternalCamera(cameraManager)

            if (cameraId == null) {
                _errorMessage.value = "No HDMI capture device found"
                _connectionState.value = PipelineState.ERROR
                return
            }

            Log.i(TAG, "Opening external camera: $cameraId")
            cameraManager.openCamera(cameraId, stateCallback, cameraHandler)

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening camera", e)
            _errorMessage.value = "Camera permission denied"
            _connectionState.value = PipelineState.ERROR
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            _errorMessage.value = "Failed to open camera: ${e.message}"
            _connectionState.value = PipelineState.ERROR
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping HDMI-USB pipeline")

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing capture session", e)
        }
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for camera thread", e)
        }
        cameraThread = null
        cameraHandler = null

        surface = null
        _connectionState.value = PipelineState.DISCONNECTED
    }

    override fun isRunning(): Boolean {
        return _connectionState.value == PipelineState.CONNECTED
    }

    private fun findExternalCamera(cameraManager: CameraManager): String? {
        val cameraList = cameraManager.cameraIdList
        Log.i(TAG, "Camera2 API found ${cameraList.size} cameras: ${cameraList.joinToString()}")

        var fallbackCameraId: String? = null

        for (cameraId in cameraList) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val facingName = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                    else -> "UNKNOWN($facing)"
                }

                Log.i(TAG, "Camera $cameraId: facing=$facingName")

                // Primary detection: LENS_FACING_EXTERNAL
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    Log.i(TAG, "Found EXTERNAL camera: $cameraId")
                    logCameraCapabilities(cameraManager, cameraId)
                    return cameraId
                }

                // Fallback: cameras with high numeric IDs might be USB (e.g., ID > 10)
                val idNum = cameraId.toIntOrNull()
                if (idNum != null && idNum > 10) {
                    Log.i(TAG, "Camera $cameraId has high ID, potential USB device")
                    fallbackCameraId = cameraId
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking camera $cameraId", e)
            }
        }

        // Use fallback if found
        if (fallbackCameraId != null) {
            Log.i(TAG, "Using fallback camera with high ID: $fallbackCameraId")
            logCameraCapabilities(cameraManager, fallbackCameraId)
            return fallbackCameraId
        }

        Log.w(TAG, "No external/USB camera found in Camera2 API")
        Log.i(TAG, "Note: UVC devices may require UVCCamera library if not exposed via Camera2")
        return null
    }

    private fun findUvcUsbDevice(): UsbDevice? {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList

            Log.i(TAG, "USB enumeration found ${deviceList.size} devices")

            for ((name, device) in deviceList) {
                Log.i(TAG, "USB device: $name, vendor=${device.vendorId}, product=${device.productId}, class=${device.deviceClass}")

                if (device.deviceClass == USB_CLASS_VIDEO) {
                    Log.i(TAG, "Found UVC device by class: $name")
                    return device
                }

                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    if (intf.interfaceClass == USB_CLASS_VIDEO) {
                        Log.i(TAG, "Found UVC device by interface: $name, interface=$i")
                        return device
                    }
                }
            }

            Log.w(TAG, "No UVC device found via USB enumeration")
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating USB devices", e)
        }
        return null
    }

    /**
     * Check if an external camera (HDMI capture device) is available.
     * Checks both Camera2 API and USB enumeration.
     */
    fun isDeviceAvailable(): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camera2Result = findExternalCamera(cameraManager) != null

            if (camera2Result) {
                Log.i(TAG, "isDeviceAvailable() = true (Camera2 API)")
                return true
            }

            val usbResult = findUvcUsbDevice() != null
            Log.i(TAG, "isDeviceAvailable() = $usbResult (USB enumeration)")
            usbResult
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device availability", e)
            false
        }
    }

    private fun logCameraCapabilities(cameraManager: CameraManager, cameraId: String) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            configs?.outputFormats?.forEach { format ->
                val sizes = configs.getOutputSizes(format)
                Log.d(TAG, "Camera $cameraId format $format: ${sizes?.joinToString { "${it.width}x${it.height}" }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging camera capabilities", e)
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "Camera opened: ${camera.id}")
            cameraDevice = camera
            createCaptureSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
            _connectionState.value = PipelineState.DISCONNECTED
        }

        override fun onError(camera: CameraDevice, error: Int) {
            val errorMsg = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                ERROR_CAMERA_DISABLED -> "Camera disabled"
                ERROR_CAMERA_DEVICE -> "Camera device error"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown error $error"
            }
            Log.e(TAG, "Camera error: $errorMsg")
            camera.close()
            cameraDevice = null
            _errorMessage.value = errorMsg
            _connectionState.value = PipelineState.ERROR
        }
    }

    private fun createCaptureSession(camera: CameraDevice) {
        val targetSurface = surface ?: run {
            Log.e(TAG, "No surface available for capture session")
            _errorMessage.value = "No display surface"
            _connectionState.value = PipelineState.ERROR
            return
        }

        try {
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(targetSurface)

            // Configure for low latency preview
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            camera.createCaptureSession(
                listOf(targetSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.i(TAG, "Capture session configured")
                        captureSession = session

                        try {
                            session.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                cameraHandler
                            )
                            _connectionState.value = PipelineState.CONNECTED
                            Log.i(TAG, "HDMI-USB pipeline connected and streaming")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting repeating request", e)
                            _errorMessage.value = "Failed to start capture: ${e.message}"
                            _connectionState.value = PipelineState.ERROR
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                        _errorMessage.value = "Camera configuration failed"
                        _connectionState.value = PipelineState.ERROR
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
            _errorMessage.value = "Failed to create capture session: ${e.message}"
            _connectionState.value = PipelineState.ERROR
        }
    }
}
