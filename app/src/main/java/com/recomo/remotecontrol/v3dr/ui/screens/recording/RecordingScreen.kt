package com.recomo.remotecontrol.v3dr.ui.screens.recording

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.common.capture.camera.Camera2Controller

private const val TAG = "RecordingScreen"

/**
 * Recording Screen - Main camera and recording interface with Camera2 integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val zoomRatio by viewModel.zoomRatio.collectAsStateWithLifecycle()
    val aeLocked by viewModel.aeLocked.collectAsStateWithLifecycle()
    val awbLocked by viewModel.awbLocked.collectAsStateWithLifecycle()
    val afMode by viewModel.afMode.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val previewSize by viewModel.previewSize.collectAsStateWithLifecycle()
    
    val isRecording = recordingState is CameraViewModel.RecordingState.Recording

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Video") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.switchCamera() }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera")
                    }
                    IconButton(onClick = { /* Camera settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Camera Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Full-screen Camera Preview (edge-to-edge, behind top bar)
            if (hasCameraPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    previewWidth = previewSize.width,
                    previewHeight = previewSize.height,
                    onSurfaceAvailable = { surface, width, height ->
                        viewModel.openCamera(surface, width, height)
                    },
                    onSurfaceDestroyed = {
                        viewModel.closeCamera()
                    }
                )
            } else {
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
            }

            // Error Display
            if (cameraState is CameraViewModel.CameraState.Error) {
                Text(
                    text = (cameraState as CameraViewModel.CameraState.Error).message,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }

            // Telemetry Overlay (top-left, account for top bar padding)
            telemetry?.let { tel ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = paddingValues.calculateTopPadding() + 8.dp, start = 8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "FPS: ${tel.fps?.let { "%.1f".format(it) } ?: "..."}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "ISO: ${tel.iso ?: "N/A"}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Zoom: ${"%.2f".format(tel.zoom)}x",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Orientation guidance indicator (account for top bar padding)
            if (isRecording) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding() + 16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Orientation Guide: Target", color = Color.White)
                    }
                }
            }

            // Camera Controls (bottom-left, semi-transparent, account for bottom padding)
            if (cameraState is CameraViewModel.CameraState.Opened) {
                val zoomRange = (cameraState as CameraViewModel.CameraState.Opened).zoomRange
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = paddingValues.calculateBottomPadding() + 16.dp, start = 16.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .width(300.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Zoom Control
                        if (zoomRange != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Zoom", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.width(50.dp)
                                )
                                Slider(
                                    value = zoomRatio,
                                    onValueChange = { viewModel.setZoom(it) },
                                    valueRange = zoomRange.lower..zoomRange.upper,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "%.2fx".format(zoomRatio),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.width(60.dp)
                                )
                            }
                        }

                        // Lock Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilterChip(
                                selected = aeLocked,
                                onClick = { viewModel.setAeLock(!aeLocked) },
                                label = { Text("AE Lock") }
                            )
                            FilterChip(
                                selected = awbLocked,
                                onClick = { viewModel.setAwbLock(!awbLocked) },
                                label = { Text("AWB Lock") }
                            )
                            FilterChip(
                                selected = afMode == Camera2Controller.AfMode.LOCKED,
                                onClick = {
                                    val newMode = if (afMode == Camera2Controller.AfMode.CONTINUOUS) {
                                        Camera2Controller.AfMode.LOCKED
                                    } else {
                                        Camera2Controller.AfMode.CONTINUOUS
                                    }
                                    viewModel.setAfMode(newMode)
                                },
                                label = { Text("AF Lock") }
                            )
                        }
                    }
                }
            }

            // Recording Controls (bottom-center, semi-transparent, account for bottom padding)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = paddingValues.calculateBottomPadding() + 16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Record button
                    FloatingActionButton(
                        onClick = { 
                            Log.d(TAG, "Record button clicked, isRecording=$isRecording")
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = if (isRecording) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    if (isRecording) {
                        val seconds = (recordingDuration / 1000) % 60
                        val minutes = (recordingDuration / 1000 / 60) % 60
                        val hours = recordingDuration / 1000 / 3600
                        Text(
                            text = if (hours > 0) {
                                "%02d:%02d:%02d".format(hours, minutes, seconds)
                            } else {
                                "%02d:%02d".format(minutes, seconds)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
