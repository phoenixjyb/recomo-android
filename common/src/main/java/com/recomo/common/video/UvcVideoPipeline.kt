package com.recomo.common.video

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Video pipeline for HDMI-to-USB capture devices using UVCCamera library.
 * This provides direct access to UVC devices that may not be exposed via Camera2 API.
 */
class UvcVideoPipeline(
    private val context: Context
) : VideoPipeline {

    companion object {
        private const val TAG = "UvcVideoPipeline"
        private const val ACTION_USB_PERMISSION = "com.recomo.common.video.USB_PERMISSION"

        // USB Video Class code
        private const val USB_CLASS_VIDEO = 14
        // USB Miscellaneous class (composite UVC devices report this at device level)
        private const val USB_CLASS_MISC = 239

        // Default preview settings
        private const val DEFAULT_WIDTH = 1920
        private const val DEFAULT_HEIGHT = 1080
        private const val DEFAULT_FPS = 30
    }

    private val _connectionState = MutableStateFlow(PipelineState.DISCONNECTED)
    override val connectionState: StateFlow<PipelineState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var usbMonitor: USBMonitor? = null
    private var uvcCamera: UVCCamera? = null
    private var surface: Surface? = null
    private var currentDevice: UsbDevice? = null
    private var isPermissionRequested = false
    private var isReceiverRegistered = false

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    // USB permission broadcast receiver
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB permission result: device=$device, granted=$granted")

                    if (granted && device != null) {
                        openUvcDevice(device)
                    } else {
                        _errorMessage.value = "USB permission denied"
                        _connectionState.value = PipelineState.ERROR
                    }
                }
            }
        }
    }

    // USB monitor listener for device connect/disconnect events.
    // Callbacks arrive on USBMonitor's internal thread — marshal to Main for state safety.
    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) {
            Log.i(TAG, "USB device attached: ${device.deviceName}")
            mainHandler.post {
                if (uvcCamera != null) {
                    Log.d(TAG, "Camera already running, ignoring attach")
                    return@post
                }
                if (isUvcDevice(device)) {
                    Log.i(TAG, "UVC device detected, requesting permission")
                    usbMonitor?.requestPermission(device)
                }
            }
        }

        override fun onDettach(device: UsbDevice) {
            Log.i(TAG, "USB device detached: ${device.deviceName}")
            mainHandler.post {
                if (device == currentDevice) {
                    stopCamera()
                    _connectionState.value = PipelineState.DISCONNECTED
                }
            }
        }

        override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) {
            Log.i(TAG, "USB device connected: ${device.deviceName}")
            mainHandler.post {
                if (uvcCamera != null) {
                    Log.i(TAG, "Camera already running, ignoring duplicate onConnect")
                    return@post
                }
                currentDevice = device
                startCamera(ctrlBlock)
            }
        }

        override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
            Log.i(TAG, "USB device disconnected: ${device.deviceName}, currentDevice=${currentDevice?.deviceName}, cameraRunning=${uvcCamera != null}")
            mainHandler.post {
                // Only handle real disconnects — ignore USBMonitor re-enumeration noise
                if (device == currentDevice && _connectionState.value != PipelineState.CONNECTING) {
                    stopCamera()
                    currentDevice = null
                    _connectionState.value = PipelineState.DISCONNECTED
                }
            }
        }

        override fun onCancel(device: UsbDevice) {
            Log.w(TAG, "USB device permission cancelled: ${device.deviceName}")
            mainHandler.post {
                if (uvcCamera != null) {
                    Log.d(TAG, "Camera already running, ignoring cancel")
                    return@post
                }
                // Permission dialog was dismissed or auto-cancelled.
                // Try opening the device directly via USBMonitor — the system may have
                // already granted permission (Samsung stale-permission bug).
                Log.i(TAG, "Attempting USBMonitor.openDevice after cancel...")
                try {
                    val ctrlBlock = usbMonitor?.openDevice(device)
                    if (ctrlBlock != null) {
                        Log.i(TAG, "USBMonitor.openDevice succeeded, starting camera")
                        currentDevice = device
                        startCamera(ctrlBlock)
                    } else {
                        Log.e(TAG, "USBMonitor.openDevice returned null — no permission")
                        _errorMessage.value = "USB permission denied"
                        _connectionState.value = PipelineState.ERROR
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "USBMonitor.openDevice failed", e)
                    _errorMessage.value = "Failed to open USB device: ${e.message}"
                    _connectionState.value = PipelineState.ERROR
                }
            }
        }
    }

    override fun start(surface: Surface) {
        if (_connectionState.value == PipelineState.CONNECTED ||
            _connectionState.value == PipelineState.CONNECTING) {
            Log.w(TAG, "Pipeline already running or connecting")
            return
        }

        Log.i(TAG, "Starting UVC pipeline")
        this.surface = surface
        _connectionState.value = PipelineState.CONNECTING
        _errorMessage.value = null

        try {
            // Register USB permission receiver (only if not already registered)
            if (!isReceiverRegistered) {
                val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(usbPermissionReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(usbPermissionReceiver, intentFilter)
                }
                isReceiverRegistered = true
            }

            // Initialize USB monitor
            usbMonitor = USBMonitor(context, onDeviceConnectListener).also {
                it.register()
            }

            // Check for already connected UVC devices
            val uvcDevice = findUvcDevice()
            if (uvcDevice != null) {
                Log.i(TAG, "Found existing UVC device: ${uvcDevice.deviceName}, hasPermission=${usbManager.hasPermission(uvcDevice)}")
                // Always use USBMonitor.requestPermission — it handles both the
                // "already permitted" case (triggers onConnect) and the "need permission"
                // case (shows system dialog, then triggers onConnect).
                usbMonitor?.requestPermission(uvcDevice)
            } else {
                Log.w(TAG, "No UVC device found")
                _errorMessage.value = "No HDMI capture device found"
                _connectionState.value = PipelineState.ERROR
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting UVC pipeline", e)
            _errorMessage.value = "Failed to start: ${e.message}"
            _connectionState.value = PipelineState.ERROR
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping UVC pipeline")

        stopCamera()

        try {
            usbMonitor?.unregister()
            usbMonitor?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying USB monitor", e)
        }
        usbMonitor = null

        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbPermissionReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }

        surface = null
        currentDevice = null
        isPermissionRequested = false
        _connectionState.value = PipelineState.DISCONNECTED
    }

    override fun isRunning(): Boolean {
        return _connectionState.value == PipelineState.CONNECTED
    }

    /**
     * Check if a UVC device is available.
     */
    fun isDeviceAvailable(): Boolean {
        val result = findUvcDevice() != null
        Log.i(TAG, "isDeviceAvailable() = $result")
        return result
    }

    /**
     * Get the current UVC device info.
     */
    fun getDeviceInfo(): String? {
        val device = findUvcDevice() ?: return null
        return "Vendor: ${device.vendorId}, Product: ${device.productId}, Name: ${device.deviceName}"
    }

    private fun findUvcDevice(): UsbDevice? {
        val deviceList = usbManager.deviceList
        Log.d(TAG, "USB devices found: ${deviceList.size}")

        for ((name, device) in deviceList) {
            Log.d(TAG, "Checking USB device: $name, class=${device.deviceClass}, vendor=${device.vendorId}")
            if (isUvcDevice(device)) {
                Log.i(TAG, "Found UVC device: $name")
                return device
            }
        }

        return null
    }

    private fun isUvcDevice(device: UsbDevice): Boolean {
        // Check device class directly
        if (device.deviceClass == USB_CLASS_VIDEO) {
            return true
        }

        // Composite UVC devices (e.g. HDMI capture cards) report class 239 (Miscellaneous)
        // at device level but have UVC interfaces underneath
        if (device.deviceClass == USB_CLASS_MISC) {
            // Check interface classes — UVC interfaces have class 14
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == USB_CLASS_VIDEO) {
                    return true
                }
            }
            // Even without interface enumeration (may need permission), treat class 239
            // with known UVC vendor IDs as UVC devices
            Log.d(TAG, "Device class=239, interfaces=${device.interfaceCount}, vendor=${device.vendorId}")
            if (device.interfaceCount == 0) {
                // Can't enumerate interfaces without permission; assume UVC if class is Misc
                return true
            }
        }

        // Check interface classes for any device class
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == USB_CLASS_VIDEO) {
                return true
            }
        }

        return false
    }

    private fun requestUsbPermission(device: UsbDevice) {
        if (isPermissionRequested) {
            Log.d(TAG, "Permission already requested")
            return
        }

        isPermissionRequested = true
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun openUvcDevice(device: UsbDevice) {
        Log.i(TAG, "Opening UVC device: ${device.deviceName}")
        usbMonitor?.requestPermission(device)
    }

    private fun startCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        Log.i(TAG, "Starting UVC camera")

        val targetSurface = surface
        if (targetSurface == null) {
            Log.e(TAG, "No surface available")
            _errorMessage.value = "No display surface"
            _connectionState.value = PipelineState.ERROR
            return
        }

        try {
            uvcCamera = UVCCamera().apply {
                open(ctrlBlock)

                // Log supported sizes for debugging
                val supportedSizes = supportedSizeList
                Log.i(TAG, "Supported sizes: ${supportedSizes?.size ?: 0}")
                supportedSizes?.forEach { size ->
                    Log.d(TAG, "  Supported: ${size.width}x${size.height} type=${size.type} fps=${size.fps}")
                }

                // Try to set preview size, falling back to supported sizes
                try {
                    setPreviewSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG)
                    Log.i(TAG, "Set preview size: ${DEFAULT_WIDTH}x${DEFAULT_HEIGHT} MJPEG")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set MJPEG, trying YUYV", e)
                    try {
                        setPreviewSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, UVCCamera.FRAME_FORMAT_YUYV)
                        Log.i(TAG, "Set preview size: ${DEFAULT_WIDTH}x${DEFAULT_HEIGHT} YUYV")
                    } catch (e2: Exception) {
                        Log.w(TAG, "Failed to set 1080p, trying 720p", e2)
                        try {
                            setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_MJPEG)
                            Log.i(TAG, "Set preview size: 1280x720 MJPEG")
                        } catch (e3: Exception) {
                            Log.w(TAG, "Failed 720p MJPEG, trying 720p YUYV", e3)
                            setPreviewSize(1280, 720, UVCCamera.FRAME_FORMAT_YUYV)
                            Log.i(TAG, "Set preview size: 1280x720 YUYV")
                        }
                    }
                }

                setPreviewDisplay(targetSurface)
                Log.i(TAG, "Preview display set, calling startPreview...")
                startPreview()

                Log.i(TAG, "UVC camera started successfully")
                _connectionState.value = PipelineState.CONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting UVC camera", e)
            _errorMessage.value = "Failed to start camera: ${e.message}"
            _connectionState.value = PipelineState.ERROR
        }
    }

    private fun stopCamera() {
        Log.i(TAG, "Stopping UVC camera")

        try {
            uvcCamera?.apply {
                stopPreview()
                setPreviewDisplay(null as android.view.Surface?)  // Release the surface
                close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping UVC camera", e)
        }
        uvcCamera = null
    }
}
