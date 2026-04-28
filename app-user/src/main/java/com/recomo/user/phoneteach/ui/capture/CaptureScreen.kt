package com.recomo.user.phoneteach.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.common.capture.arcore.ArCorePoseRecorder
import com.recomo.common.capture.camera.Camera2Controller

private const val TAG = "PhoneTeachCaptureScreen"

/**
 * Phone Teach (手机示教) capture screen.
 *
 * Single merged screen replacing v3dr's split RecordingScreen + ArCoreRecordingScreen pair.
 * Always records video + IMU + calib + metadata via Camera2. When ARCore is READY, also
 * attaches an ARCore 6-DoF pose track as a cloud-solver hint. If ARCore is unavailable the
 * session still records — the cloud solver runs full visual-inertial SfM from video + IMU.
 *
 * Embedded inside [com.recomo.user.phoneteach.PhoneTeachNavHost]; no onNavigateBack needed
 * because the inner nav host owns the tab strip.
 */
@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = hiltViewModel()
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
    val arCoreAvailability by viewModel.arCoreAvailability.collectAsStateWithLifecycle()

    val isRecording = recordingState is CaptureViewModel.RecordingState.Recording

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        // --- Camera preview (edge-to-edge) ---
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
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

        // --- Camera error ---
        if (cameraState is CaptureViewModel.CameraState.Error) {
            Text(
                text = (cameraState as CaptureViewModel.CameraState.Error).message,
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }

        // --- Top-left telemetry overlay ---
        telemetry?.let { tel ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
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

        // --- Top-right ARCore availability badge + switch camera ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArCoreAvailabilityBadge(availability = arCoreAvailability)
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                IconButton(onClick = { viewModel.switchCamera() }) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }

        // --- Bottom-left camera controls (zoom + locks) ---
        if (cameraState is CaptureViewModel.CameraState.Opened) {
            val zoomRange = (cameraState as CaptureViewModel.CameraState.Opened).zoomRange

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .width(300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = aeLocked,
                            onClick = { viewModel.setAeLock(!aeLocked) },
                            label = { Text("AE") }
                        )
                        FilterChip(
                            selected = awbLocked,
                            onClick = { viewModel.setAwbLock(!awbLocked) },
                            label = { Text("AWB") }
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
                            label = { Text("AF") }
                        )
                    }
                }
            }
        }

        // --- Bottom-center record button + timer ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

        // --- Completion toast at bottom ---
        val completed = recordingState as? CaptureViewModel.RecordingState.Completed
        if (completed != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp, start = 24.dp, end = 24.dp),
                color = Color(0xAA1F8A3F),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Session saved",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = completed.sessionDir.name,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (completed.trajectoryFile != null) {
                        Text(
                            text = "ARCore pose attached",
                            color = Color(0xFF89E0A2),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "Video + IMU only — cloud will solve pose",
                            color = Color(0xFFE0E089),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArCoreAvailabilityBadge(availability: ArCorePoseRecorder.ArCoreAvailability) {
    val (label, bg, fg) = when (availability) {
        ArCorePoseRecorder.ArCoreAvailability.READY -> Triple(
            "ARCore",
            Color(0xAA1F8A3F),
            Color.White
        )
        ArCorePoseRecorder.ArCoreAvailability.NEEDS_INSTALL -> Triple(
            "Install AR",
            Color(0xAA8A6F1F),
            Color.White
        )
        ArCorePoseRecorder.ArCoreAvailability.NOT_SUPPORTED -> Triple(
            "No ARCore",
            Color(0xAA555555),
            Color(0xFFBFBFBF)
        )
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}
