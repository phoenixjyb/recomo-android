package com.recomo.remotecontrol.v3dr.ui.screens.recording

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.recomo.common.capture.arcore.ArCoreVideoRecorder
import com.google.ar.core.TrackingState
import java.io.File
import android.app.Activity
import android.os.Environment

private const val TAG = "ArCoreRecordingScreen"

/**
 * ARCore-based recording screen that uses ARCore's camera for pose tracking.
 * This provides real-time VIO capabilities through ARCore's tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArCoreRecordingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    var poseCount by remember { mutableIntStateOf(0) }
    var trackingState by remember { mutableStateOf(TrackingState.PAUSED) }
    var arCoreAvailability by remember { 
        mutableStateOf(ArCoreVideoRecorder.ArCoreAvailability.NOT_SUPPORTED) 
    }
    var sessionReady by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // ARCore recorder
    var recorder by remember { mutableStateOf<ArCoreVideoRecorder?>(null) }
    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    
    // Recording timer
    var recordingStartTime by remember { mutableStateOf(0L) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    // Request camera permission
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Timer for recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingStartTime = System.currentTimeMillis()
            while (isRecording) {
                recordingDuration = System.currentTimeMillis() - recordingStartTime
                poseCount = recorder?.getPoseCount() ?: 0
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    // Lifecycle handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    recorder?.resume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    recorder?.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    recorder?.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder?.release()
        }
    }
    
    fun startRecording() {
        val rec = recorder ?: return
        
        // Create session directory
        val timestamp = System.currentTimeMillis()
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        val timeString = dateFormat.format(java.util.Date(timestamp))
        val sessionName = "session_${timeString}"
        
        val appMoviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val v3drDir = File(appMoviesDir, "V3DR")
        val sessionDir = File(v3drDir, sessionName)
        
        if (rec.startRecording(sessionDir)) {
            isRecording = true
            Log.i(TAG, "Recording started: ${sessionDir.absolutePath}")
        } else {
            errorMessage = "Failed to start recording"
        }
    }
    
    fun stopRecording() {
        val rec = recorder ?: return
        
        val (videoFile, poseFile) = rec.stopRecording()
        isRecording = false
        
        if (videoFile != null) {
            Log.i(TAG, "Recording saved: ${videoFile.absolutePath}")
            Log.i(TAG, "Poses saved: ${poseFile?.absolutePath ?: "none"}")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("ARCore Recording")
                        Text(
                            text = "Real-time pose tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasCameraPermission) {
                // Permission required message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Camera permission required\nPlease grant camera access to continue",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // ARCore GLSurfaceView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        GLSurfaceView(ctx).also { view ->
                            view.preserveEGLContextOnPause = true
                            view.setEGLContextClientVersion(2)
                            view.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                            
                            val arRecorder = ArCoreVideoRecorder(activity, view).apply {
                                // Check availability
                                arCoreAvailability = checkAvailability()
                                
                                onSessionReady = {
                                    sessionReady = true
                                    Log.i(TAG, "ARCore session ready")
                                }
                                
                                onTrackingStateChanged = { state ->
                                    trackingState = state
                                }
                                
                                onError = { error ->
                                    errorMessage = error
                                    Log.e(TAG, "ARCore error: $error")
                                }
                            }
                            
                            view.setRenderer(arRecorder)
                            view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            
                            recorder = arRecorder
                            glSurfaceView = view
                        }
                    }
                )
                
                // Tracking status overlay (top-left)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (trackingState) {
                                    TrackingState.TRACKING -> Icons.Default.CheckCircle
                                    TrackingState.PAUSED -> Icons.Default.Pause
                                    TrackingState.STOPPED -> Icons.Default.Cancel
                                },
                                contentDescription = null,
                                tint = when (trackingState) {
                                    TrackingState.TRACKING -> Color.Green
                                    TrackingState.PAUSED -> Color.Yellow
                                    TrackingState.STOPPED -> Color.Red
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tracking: ${trackingState.name}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        if (isRecording) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Poses: $poseCount",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                // Recording duration (top-center)
                if (isRecording) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        color = Color.Red.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDuration(recordingDuration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Error message
                errorMessage?.let { error ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                
                // ARCore not available message
                if (arCoreAvailability != ArCoreVideoRecorder.ArCoreAvailability.READY) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (arCoreAvailability) {
                                    ArCoreVideoRecorder.ArCoreAvailability.NEEDS_INSTALL -> 
                                        "ARCore needs to be installed"
                                    else -> "ARCore is not supported on this device"
                                },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Bottom controls
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(24.dp)
                ) {
                    // Record button (center)
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                startRecording()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp),
                        enabled = sessionReady && 
                                  arCoreAvailability == ArCoreVideoRecorder.ArCoreAvailability.READY &&
                                  trackingState == TrackingState.TRACKING
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isRecording) Color.Red else Color.White,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isRecording) {
                                    // Stop icon
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        shape = MaterialTheme.shapes.small
                                    ) {}
                                } else {
                                    // Record icon
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.Red,
                                        shape = MaterialTheme.shapes.extraLarge
                                    ) {}
                                }
                            }
                        }
                    }
                    
                    // Info text
                    if (!sessionReady) {
                        Text(
                            text = "Initializing ARCore...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(top = 8.dp)
                        )
                    } else if (trackingState != TrackingState.TRACKING && !isRecording) {
                        Text(
                            text = "Move phone slowly to initialize tracking",
                            color = Color.Yellow,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
