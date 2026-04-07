package com.recomo.remotecontrol.camviewer.ui.screens.video

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.camviewer.data.model.VideoSource
import com.recomo.remotecontrol.camviewer.video.VideoSurfaceView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun VideoScreen(
    viewModel: VideoViewModel = hiltViewModel(),
    showTelemetryOverlay: Boolean = true,
    showConnectionControls: Boolean = true,
    connectionControlsEndPadding: Dp = 8.dp
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.latestTelemetry.collectAsState()
    val latency by viewModel.latency.collectAsState()
    val e2eLatencyMs by viewModel.e2eLatencyMs.collectAsState()
    val wsRttMs by viewModel.wsRttMs.collectAsState()
    val reconnectInMs by viewModel.wsReconnectInMs.collectAsState()
    val developerModeEnabled by viewModel.developerModeEnabled.collectAsState(initial = false)
    val isRecording by viewModel.isRecording.collectAsState()
    val trackingUpdate by viewModel.trackingUpdates.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isLocalTrackingActive by viewModel.isLocalTrackingActive.collectAsState(initial = false)
    val trackingProcessing by viewModel.trackingProcessing.collectAsState(initial = false)
    val viewOnly by viewModel.viewOnlyEnabled.collectAsState(initial = false)
    val videoSource by viewModel.videoSource.collectAsState()
    val surfaceRecreationTrigger by viewModel.surfaceRecreationTrigger.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Auto-connect on launch
    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(Unit) {
        viewModel.trackingNotice.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    // Storage permission for recording to public Movies folder
    var hasStoragePermission by remember { mutableStateOf(true) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
        if (isGranted) {
            viewModel.toggleRecording()
        }
    }
    
    var videoSurfaceView by remember { mutableStateOf<VideoSurfaceView?>(null) }
    var tapPosition by remember { mutableStateOf<Offset?>(null) }
    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentPosition by remember { mutableStateOf<Offset?>(null) }
    var videoSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var captureInFlight by remember { mutableStateOf(false) }

    val frameWidth = telemetry?.resolution?.width?.takeIf { it > 0 } ?: 1920
    val frameHeight = telemetry?.resolution?.height?.takeIf { it > 0 } ?: 1080
    val videoContentRect = remember(videoSize, frameWidth, frameHeight) {
        computeVideoContentRect(videoSize, frameWidth, frameHeight)
    }

    val trackingActiveState = rememberUpdatedState(isLocalTrackingActive)
    val connectionStateState = rememberUpdatedState(connectionState)
    val trackingProcessingState = rememberUpdatedState(trackingProcessing)
    val captureInFlightState = rememberUpdatedState(captureInFlight)
    
    // Reusable bitmap for frame capture (tracking)
    // Using a fixed size for now, matching default decoder size
    val captureBitmap = remember { 
        android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888)
    }
    
    // Frame capture loop for local tracking
    LaunchedEffect(videoSurfaceView) {
        while (isActive) {
            val shouldCapture =
                trackingActiveState.value &&
                    connectionStateState.value is ConnectionState.Connected &&
                    !trackingProcessingState.value &&
                    !captureInFlightState.value &&
                    viewModel.shouldCaptureTrackingFrame()
            videoSurfaceView?.let { view ->
                if (shouldCapture) {
                    captureInFlight = true
                    view.captureFrame(captureBitmap) { success ->
                        captureInFlight = false
                        if (success) {
                            viewModel.processFrame(captureBitmap)
                            viewModel.updateLastFrame(captureBitmap)
                        }
                    }
                }
            }
            delay(if (shouldCapture) 120 else 250) // ~8 FPS during tracking, minimal idle load
        }
    }
    
    // Periodic frame capture for thumbnails (even when not tracking)
    LaunchedEffect(videoSurfaceView) {
        val thumbnailBitmap = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888)
        while (isActive) {
            if (connectionStateState.value is ConnectionState.Connected && !trackingActiveState.value) {
                videoSurfaceView?.captureFrame(thumbnailBitmap) { success ->
                    if (success) {
                        viewModel.updateLastFrame(thumbnailBitmap)
                    }
                }
            }
            delay(1000) // Update thumbnail once per second when not tracking
        }
    }
    
    // Calculate bounding box from drag positions
    val boundingBox = remember(dragStartPosition, dragCurrentPosition) {
        if (dragStartPosition != null && dragCurrentPosition != null) {
            val start = dragStartPosition!!
            val current = dragCurrentPosition!!
            Rect(
                left = min(start.x, current.x),
                top = min(start.y, current.y),
                right = max(start.x, current.x),
                bottom = max(start.y, current.y)
            )
        } else null
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Video Surface - key forces recreation when switching video sources
        key(surfaceRecreationTrigger) {
            AndroidView(
                factory = { context ->
                    VideoSurfaceView(context).apply {
                        videoSurfaceView = this
                        
                        // Make surface view render behind the UI elements
                        setZOrderMediaOverlay(false)
                        
                        setOnSurfaceReadyListener { holder ->
                            viewModel.initializeDecoder(
                                surface = holder.surface,
                                width = 1920,
                                height = 1080
                            )
                            // Avoid duplicate connect attempts when the stream is already active.
                            if (connectionState !is ConnectionState.Connected &&
                                connectionState !is ConnectionState.Connecting
                            ) {
                                viewModel.connect()
                            }
                        }

                        setOnSurfaceDestroyedListener {
                            // Pause rendering (stop decoder) but keep network connections
                            // alive. This avoids disconnect/reconnect churn and frozen
                            // frames when the surface briefly goes away.
                            viewModel.pauseRendering()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        // Capture video size for coordinate calculations
                        videoSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                        android.util.Log.d("VideoScreen", "Video size changed: $videoSize")
                    }
                    .then(if (viewOnly) Modifier else Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for first touch down
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPosition = down.position
                            var totalDrag = 0f
                            var isDragging = false
                            
                            // Track movement
                            do {
                                val event = awaitPointerEvent()
                                val dragChange = event.changes.firstOrNull()
                                
                                if (dragChange != null) {
                                val dragAmount = dragChange.position - downPosition
                                totalDrag = kotlin.math.sqrt(
                                    dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                )
                                
                                // If moved more than 10 pixels, it's a drag
                                if (totalDrag > 10f && !isDragging) {
                                    isDragging = true
                                    // Clear tap position
                                    tapPosition = null
                                    // Start drag
                                    dragStartPosition = downPosition
                                }
                                
                                if (isDragging) {
                                    dragCurrentPosition = dragChange.position
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        
                        // Pointer released
                        if (isDragging) {
                            // Send bounding box
                            val start = dragStartPosition
                            val end = dragCurrentPosition
                            
                            if (start != null && end != null) {
                                val content = videoContentRect ?: Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                                if (content.width > 0f && content.height > 0f) {
                                    val startClamped = Offset(
                                        start.x.coerceIn(content.left, content.right),
                                        start.y.coerceIn(content.top, content.bottom)
                                    )
                                    val endClamped = Offset(
                                        end.x.coerceIn(content.left, content.right),
                                        end.y.coerceIn(content.top, content.bottom)
                                    )

                                    // Calculate normalized bounding box (0.0-1.0) relative to video content
                                    val left = ((min(startClamped.x, endClamped.x) - content.left) / content.width).coerceIn(0f, 1f)
                                    val top = ((min(startClamped.y, endClamped.y) - content.top) / content.height).coerceIn(0f, 1f)
                                    val right = ((max(startClamped.x, endClamped.x) - content.left) / content.width).coerceIn(0f, 1f)
                                    val bottom = ((max(startClamped.y, endClamped.y) - content.top) / content.height).coerceIn(0f, 1f)
                                
                                    val width = right - left
                                    val height = bottom - top
                                
                                    // Only send if box has meaningful size (> 1% of video content)
                                    if (width > 0.01f && height > 0.01f) {
                                        viewModel.sendTargetROI(left, top, width, height)
                                    }
                                }
                            }
                            
                            // Clear drag state immediately after sending
                            dragStartPosition = null
                            dragCurrentPosition = null
                        } else {
                            // It was a tap
                            val content = videoContentRect ?: Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                            val normalizedX = ((downPosition.x - content.left) / content.width).coerceIn(0f, 1f)
                            val normalizedY = ((downPosition.y - content.top) / content.height).coerceIn(0f, 1f)
                            
                            tapPosition = downPosition
                            
                            // Send target coordinates
                            viewModel.sendTargetCoordinates(normalizedX, normalizedY)
                            
                            // Clear tap indicator after delay
                            scope.launch {
                                delay(1000)
                                tapPosition = null
                            }
                        }
                    }
                })
        )
        } // End of key(surfaceRecreationTrigger)
        
        // Tap indicator (crosshair)
        tapPosition?.let { position ->
            TapCrosshair(position)
        }
        
        // Bounding box preview during drag
        boundingBox?.let { box ->
            BoundingBoxOverlay(box, state = com.recomo.remotecontrol.camviewer.data.model.TrackingState.DRAWING)
        }
        
        // Tracked object bounding box
        trackingUpdate?.let { update ->
            android.util.Log.d("VideoScreen", "TrackingUpdate received: state=${update.state}, bbox=${update.bbox}, videoSize=$videoSize")
            update.bbox?.let { bbox ->
                val trackingState = when (update.state) {
                    "pending" -> com.recomo.remotecontrol.camviewer.data.model.TrackingState.PENDING
                    "tracking" -> com.recomo.remotecontrol.camviewer.data.model.TrackingState.TRACKING
                    "lost" -> com.recomo.remotecontrol.camviewer.data.model.TrackingState.LOST
                    else -> com.recomo.remotecontrol.camviewer.data.model.TrackingState.IDLE
                }
                
                // Convert normalized coordinates to screen coordinates
                val content = videoContentRect
                if (content != null && content.width > 0f && content.height > 0f) {
                    android.util.Log.d("VideoScreen", "Drawing tracking box for state=${update.state}")
                    val screenBox = Rect(
                        left = content.left + bbox.x * content.width,
                        top = content.top + bbox.y * content.height,
                        right = content.left + (bbox.x + bbox.width) * content.width,
                        bottom = content.top + (bbox.y + bbox.height) * content.height
                    )
                    
                    TrackedBoundingBox(
                        box = screenBox,
                        state = trackingState,
                        confidence = update.confidence
                    )
                }
            }
        }
        
        if (showTelemetryOverlay) {
            TelemetryOverlay(
                telemetry = telemetry,
                latency = latency,
                e2eLatencyMs = e2eLatencyMs,
                wsRttMs = wsRttMs,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }

        if (showConnectionControls) {
            ConnectionControls(
                connectionState = connectionState,
                isRecording = isRecording,
                reconnectInMs = reconnectInMs,
                videoSource = videoSource,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() },
                onToggleRecording = { viewModel.toggleRecording() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = connectionControlsEndPadding)
            )
        }
        
        // Developer mode camera controls overlay (bottom)
        CameraControlOverlay(
            isVisible = developerModeEnabled,
            onZoomChange = { viewModel.setZoom(it) },
            onAeLockChange = { viewModel.setAeLock(it) },
            onAwbLockChange = { viewModel.setAwbLock(it) },
            onAfModeChange = { viewModel.setAfMode(it) },
            onCameraSwitch = { viewModel.switchCamera(it) },
            onBitrateChange = { viewModel.setBitrate(it) },
            onCodecChange = { viewModel.setCodec(it) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.85f)  // Leave 15% space on the right
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun TapCrosshair(position: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val crosshairSize = 40.dp.toPx()
        val color = Color.Yellow
        val strokeWidth = 3.dp.toPx()
        
        // Horizontal line
        drawLine(
            color = color,
            start = Offset(position.x - crosshairSize, position.y),
            end = Offset(position.x + crosshairSize, position.y),
            strokeWidth = strokeWidth
        )
        
        // Vertical line
        drawLine(
            color = color,
            start = Offset(position.x, position.y - crosshairSize),
            end = Offset(position.x, position.y + crosshairSize),
            strokeWidth = strokeWidth
        )
        
        // Center circle
        drawCircle(
            color = color,
            radius = 8.dp.toPx(),
            center = position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
    }
}

private fun computeVideoContentRect(viewSize: Size, videoWidth: Int, videoHeight: Int): Rect? {
    if (viewSize.width <= 0f || viewSize.height <= 0f) return null
    if (videoWidth <= 0 || videoHeight <= 0) return null

    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val viewAspect = viewSize.width / viewSize.height

    return if (viewAspect > videoAspect) {
        val contentHeight = viewSize.height
        val contentWidth = contentHeight * videoAspect
        val left = (viewSize.width - contentWidth) / 2f
        Rect(left, 0f, left + contentWidth, viewSize.height)
    } else {
        val contentWidth = viewSize.width
        val contentHeight = contentWidth / videoAspect
        val top = (viewSize.height - contentHeight) / 2f
        Rect(0f, top, viewSize.width, top + contentHeight)
    }
}

@Composable
fun BoundingBoxOverlay(
    box: Rect,
    state: com.recomo.remotecontrol.camviewer.data.model.TrackingState = com.recomo.remotecontrol.camviewer.data.model.TrackingState.DRAWING
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Color based on state
        val color = when (state) {
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.DRAWING -> Color.Green
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.PENDING -> Color.Yellow
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.TRACKING -> Color.Cyan
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.LOST -> Color.Red
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.IDLE -> Color.Gray
        }
        
        val strokeWidth = 3.dp.toPx()
        val fillColor = color.copy(alpha = 0.2f)
        
        // Draw filled rectangle
        drawRect(
            color = fillColor,
            topLeft = Offset(box.left, box.top),
            size = androidx.compose.ui.geometry.Size(box.width, box.height)
        )
        
        // Draw border
        drawRect(
            color = color,
            topLeft = Offset(box.left, box.top),
            size = androidx.compose.ui.geometry.Size(box.width, box.height),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        
        // Draw corner markers (for better visibility)
        val markerSize = 20.dp.toPx()
        
        // Top-left corner
        drawLine(color, Offset(box.left, box.top), Offset(box.left + markerSize, box.top), strokeWidth * 1.5f)
        drawLine(color, Offset(box.left, box.top), Offset(box.left, box.top + markerSize), strokeWidth * 1.5f)
        
        // Top-right corner
        drawLine(color, Offset(box.right, box.top), Offset(box.right - markerSize, box.top), strokeWidth * 1.5f)
        drawLine(color, Offset(box.right, box.top), Offset(box.right, box.top + markerSize), strokeWidth * 1.5f)
        
        // Bottom-left corner
        drawLine(color, Offset(box.left, box.bottom), Offset(box.left + markerSize, box.bottom), strokeWidth * 1.5f)
        drawLine(color, Offset(box.left, box.bottom), Offset(box.left, box.bottom - markerSize), strokeWidth * 1.5f)
        
        // Bottom-right corner
        drawLine(color, Offset(box.right, box.bottom), Offset(box.right - markerSize, box.bottom), strokeWidth * 1.5f)
        drawLine(color, Offset(box.right, box.bottom), Offset(box.right, box.bottom - markerSize), strokeWidth * 1.5f)
    }
}

@Composable
fun TrackedBoundingBox(
    box: Rect,
    state: com.recomo.remotecontrol.camviewer.data.model.TrackingState,
    confidence: Float
) {
    // Use animated values for smooth transitions
    val animatedLeft by androidx.compose.animation.core.animateFloatAsState(
        targetValue = box.left,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "box_left"
    )
    val animatedTop by androidx.compose.animation.core.animateFloatAsState(
        targetValue = box.top,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "box_top"
    )
    val animatedRight by androidx.compose.animation.core.animateFloatAsState(
        targetValue = box.right,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "box_right"
    )
    val animatedBottom by androidx.compose.animation.core.animateFloatAsState(
        targetValue = box.bottom,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "box_bottom"
    )
    
    val animatedBox = Rect(
        left = animatedLeft,
        top = animatedTop,
        right = animatedRight,
        bottom = animatedBottom
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Color based on state
        val color = when (state) {
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.PENDING -> Color.Yellow
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.TRACKING -> Color.Cyan
            com.recomo.remotecontrol.camviewer.data.model.TrackingState.LOST -> Color.Red
            else -> Color.Gray
        }
        
        val strokeWidth = 4.dp.toPx()
        val fillAlpha = if (state == com.recomo.remotecontrol.camviewer.data.model.TrackingState.TRACKING) 0.15f else 0.25f
        val fillColor = color.copy(alpha = fillAlpha)
        
        // Draw filled rectangle
        drawRect(
            color = fillColor,
            topLeft = Offset(animatedBox.left, animatedBox.top),
            size = androidx.compose.ui.geometry.Size(animatedBox.width, animatedBox.height)
        )
        
        // Draw border with dashed style for pending
        if (state == com.recomo.remotecontrol.camviewer.data.model.TrackingState.PENDING) {
            // Dashed border for pending state
            drawRect(
                color = color,
                topLeft = Offset(animatedBox.left, animatedBox.top),
                size = androidx.compose.ui.geometry.Size(animatedBox.width, animatedBox.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(20f, 10f), 0f
                    )
                )
            )
        } else {
            // Solid border for tracking/lost
            drawRect(
                color = color,
                topLeft = Offset(animatedBox.left, animatedBox.top),
                size = androidx.compose.ui.geometry.Size(animatedBox.width, animatedBox.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }
        
        // Draw corner markers (thicker for tracked state)
        val markerSize = 25.dp.toPx()
        val markerWidth = strokeWidth * 1.8f
        
        // Top-left corner
        drawLine(color, Offset(animatedBox.left, animatedBox.top), Offset(animatedBox.left + markerSize, animatedBox.top), markerWidth)
        drawLine(color, Offset(animatedBox.left, animatedBox.top), Offset(animatedBox.left, animatedBox.top + markerSize), markerWidth)
        
        // Top-right corner
        drawLine(color, Offset(animatedBox.right, animatedBox.top), Offset(animatedBox.right - markerSize, animatedBox.top), markerWidth)
        drawLine(color, Offset(animatedBox.right, animatedBox.top), Offset(animatedBox.right, animatedBox.top + markerSize), markerWidth)
        
        // Bottom-left corner
        drawLine(color, Offset(animatedBox.left, animatedBox.bottom), Offset(animatedBox.left + markerSize, animatedBox.bottom), markerWidth)
        drawLine(color, Offset(animatedBox.left, animatedBox.bottom), Offset(animatedBox.left, animatedBox.bottom - markerSize), markerWidth)
        
        // Bottom-right corner
        drawLine(color, Offset(animatedBox.right, animatedBox.bottom), Offset(animatedBox.right - markerSize, animatedBox.bottom), markerWidth)
        drawLine(color, Offset(animatedBox.right, animatedBox.bottom), Offset(animatedBox.right, animatedBox.bottom - markerSize), markerWidth)
        
        // Draw confidence text if tracking
        if (state == com.recomo.remotecontrol.camviewer.data.model.TrackingState.TRACKING && confidence > 0) {
            // Use Compose Canvas text drawing instead
            // Note: For better text rendering, consider using a Text composable overlay
        }
    }
}

@Composable
fun TelemetryOverlay(
    telemetry: com.recomo.remotecontrol.camviewer.data.model.Telemetry?,
    latency: Long,
    e2eLatencyMs: Long?,
    wsRttMs: Long?,
    modifier: Modifier = Modifier
) {
    // Keep and display the last known telemetry instead of clearing to null
    var cachedTelemetry by remember { mutableStateOf<com.recomo.remotecontrol.camviewer.data.model.Telemetry?>(null) }
    var cachedLatency by remember { mutableLongStateOf(0L) }
    var cachedE2E by remember { mutableStateOf<Long?>(null) }
    var cachedRtt by remember { mutableStateOf<Long?>(null) }

    // Always show the freshest available data, falling back to the last good sample
    val displayTelemetry = telemetry ?: cachedTelemetry
    val displayLatency = if (latency > 0) latency else cachedLatency
    val displayE2E = e2eLatencyMs ?: cachedE2E
    val displayRtt = wsRttMs ?: cachedRtt

    // Update immediately when new telemetry arrives
    LaunchedEffect(telemetry) {
        telemetry?.let { cachedTelemetry = it }
    }
    // Always reflect the latest latency (it already includes per-frame updates)
    LaunchedEffect(latency) {
        if (latency > 0) {
            cachedLatency = latency
        }
    }

    LaunchedEffect(e2eLatencyMs) {
        if (e2eLatencyMs != null) {
            cachedE2E = e2eLatencyMs
        }
    }

    LaunchedEffect(wsRttMs) {
        if (wsRttMs != null) {
            cachedRtt = wsRttMs
        }
    }
    
    // Always show fixed size box
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp)
            .widthIn(min = 150.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = displayTelemetry?.let { "FPS: ${String.format("%.1f", it.fps)}" } ?: "FPS: --",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = displayTelemetry?.let { "Bitrate: ${it.bitrateKbps / 1000} Mbps" } ?: "Bitrate: --",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = displayTelemetry?.resolution?.let { "Resolution: ${it.width}x${it.height}" } ?: "Resolution: --",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = displayTelemetry?.let { "Codec: ${it.codec}" } ?: "Codec: --",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = if (displayLatency > 0) "Pipeline: ${displayLatency}ms" else "Pipeline: --",
            color = if (displayLatency > 0 && displayLatency < 100) Color.Green 
                   else if (displayLatency >= 100 && displayLatency < 200) Color.Yellow 
                   else if (displayLatency >= 200) Color.Red
                   else Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = displayE2E?.let { "E2E(est): ${it}ms" } ?: "E2E(est): --",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = displayRtt?.let { "WS RTT: ${it}ms" } ?: "WS RTT: --",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ConnectionControls(
    connectionState: ConnectionState,
    isRecording: Boolean,
    reconnectInMs: Long?,
    videoSource: VideoSource = VideoSource.WEBSOCKET,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    // For HDMI source, show simplified status (no WebSocket connect/disconnect controls)
    val isHdmiSource = videoSource == VideoSource.HDMI_USB
    val isOrinWebSocketSource = videoSource == VideoSource.WEBSOCKET_ORIN
    val canRecord = !isHdmiSource && !isOrinWebSocketSource
    val compactButtonSize = 32.dp
    val compactIconSize = 20.dp
    
    Card(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Consume clicks to prevent pass-through to video surface */ },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isHdmiSource) {
                    // HDMI source: show simple status indicator
                    Box(
                        modifier = Modifier.size(compactButtonSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "HDMI Connected",
                            tint = Color(0xFF4CAF50), // Green
                            modifier = Modifier.size(compactIconSize)
                        )
                    }
                    Text(
                        text = "HDMI",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    // WebSocket source: show connect/disconnect controls
                    when (connectionState) {
                        is ConnectionState.Disconnected -> {
                            IconButton(
                                onClick = onConnect,
                                modifier = Modifier.size(compactButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Connect",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(compactIconSize)
                                )
                            }
                        }
                        is ConnectionState.Connecting -> {
                            Box(
                                modifier = Modifier.size(compactButtonSize),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(compactIconSize),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        is ConnectionState.Connected -> {
                            IconButton(
                                onClick = onDisconnect,
                                modifier = Modifier.size(compactButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Disconnect",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(compactIconSize)
                                )
                            }
                        }
                        is ConnectionState.Error -> {
                            IconButton(
                                onClick = onConnect,
                                modifier = Modifier.size(compactButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(compactIconSize)
                                )
                            }
                        }
                    }
                }

                // Record button (only shown when connected, for WebSocket source only)
                if (canRecord && connectionState is ConnectionState.Connected) {
                    IconButton(
                        onClick = onToggleRecording,
                        modifier = Modifier.size(compactButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RadioButtonChecked,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(compactIconSize)
                        )
                    }
                }
            }

            // Auto-reconnect message (WebSocket only)
            if (!isHdmiSource && reconnectInMs != null && connectionState !is ConnectionState.Connected) {
                val seconds = ((reconnectInMs + 999) / 1000).coerceAtLeast(1)
                Text(
                    text = "Auto-reconnect in ${seconds}s",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isOrinWebSocketSource && connectionState is ConnectionState.Connected) {
                Text(
                    text = "Orin JPEG stream",
                    color = Color(0xFF9ED9FF),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
