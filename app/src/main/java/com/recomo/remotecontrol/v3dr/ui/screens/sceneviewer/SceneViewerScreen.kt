package com.recomo.remotecontrol.v3dr.ui.screens.sceneviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus
import com.recomo.remotecontrol.v3dr.data.model.SceneReconstruction
import com.recomo.remotecontrol.v3dr.ui.components.Filament3DView
import com.recomo.remotecontrol.v3dr.ui.components.LightingSettings
import com.recomo.remotecontrol.v3dr.ui.components.CameraSettings
import com.recomo.remotecontrol.v3dr.ui.components.ViewerSettingsPanel

/**
 * 3D Scene Viewer Screen
 * Displays reconstructed 3D scenes from uploaded recordings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneViewerScreen(
    sceneId: String,
    onNavigateBack: () -> Unit,
    viewModel: SceneViewerViewModel = hiltViewModel()
) {
    val scene by viewModel.sceneReconstruction.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(sceneId) {
        // Check if sceneId is a UUID (scene_id) or a session_id
        val isUuid = sceneId.contains("-") && sceneId.length > 32
        if (isUuid) {
            viewModel.loadScene(sceneId)
        } else {
            // It's a session ID
            viewModel.loadSceneForSession(sceneId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Scene Viewer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Reset camera button (when scene is loaded)
                    if (scene?.status == ReconstructionStatus.COMPLETED) {
                        // View source video button
                        scene?.sourceVideoFileName?.let {
                            IconButton(onClick = { /* TODO: Open video player */ }) {
                                Icon(
                                    Icons.Default.VideoLibrary,
                                    contentDescription = "View Source Video"
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.resetCamera() }) {
                            Icon(
                                Icons.Default.CenterFocusStrong,
                                contentDescription = "Reset Camera"
                            )
                        }
                        IconButton(onClick = { /* TODO: Share scene */ }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> LoadingView()
                errorMessage != null -> ErrorView(errorMessage!!, onRetry = { viewModel.retry() })
                scene == null -> EmptySceneView()
                scene!!.status == ReconstructionStatus.PENDING -> QueuedView()
                scene!!.status == ReconstructionStatus.PROCESSING -> ProcessingView()
                scene!!.status == ReconstructionStatus.FAILED -> {
                    ErrorView(
                        scene!!.errorMessage ?: "Reconstruction failed",
                        onRetry = { viewModel.retry() }
                    )
                }
                scene!!.status == ReconstructionStatus.COMPLETED -> {
                    // Check if model needs to be downloaded
                    if (scene!!.localCachePath != null) {
                        // Model is cached locally - show 3D viewer
                        Scene3DView(
                            modelPath = scene!!.localCachePath!!,
                            scene = scene!!,
                            onResetCamera = { viewModel.resetCamera() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (downloadProgress.isDownloading) {
                        // Model is downloading
                        DownloadingView(downloadProgress.progress)
                    } else {
                        // Model needs to be downloaded
                        DownloadPromptView(
                            fileSize = scene!!.fileSizeBytes,
                            onDownload = { viewModel.downloadModel() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Loading scene information...")
        }
    }
}

@Composable
private fun EmptySceneView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.ViewInAr,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "No 3D Scene Available",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "This recording hasn't been uploaded or processed yet. Upload it to create a 3D reconstruction.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QueuedView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Queued for Processing",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Your 3D reconstruction is in the queue. Processing will begin shortly.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ProcessingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                strokeWidth = 6.dp
            )
            Text(
                "Processing 3D Reconstruction",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Running COLMAP reconstruction pipeline. This may take 10-30 minutes depending on video length.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DownloadingView(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.size(80.dp),
                strokeWidth = 6.dp
            )
            Text(
                "Downloading 3D Model",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DownloadPromptView(
    fileSize: Long,
    onDownload: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "3D Scene Ready",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Download size: ${formatFileSize(fileSize)}",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "Download the 3D model to view it in the viewer.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Model")
            }
        }
    }
}

@Composable
private fun ErrorView(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                "Error",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun Scene3DView(
    modelPath: String,
    scene: SceneReconstruction,
    onResetCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lightingSettings by remember { mutableStateOf(LightingSettings()) }
    var cameraSettings by remember { mutableStateOf(CameraSettings()) }
    var settingsKey by remember { mutableStateOf(0) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 3D Model Viewer with Filament
        key(settingsKey) {
            Filament3DView(
                modelPath = modelPath,
                lightingSettings = lightingSettings,
                cameraSettings = cameraSettings,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Settings panel
        ViewerSettingsPanel(
            lightingSettings = lightingSettings,
            onLightingChanged = { 
                lightingSettings = it
                settingsKey++  // Trigger re-creation
            },
            cameraSettings = cameraSettings,
            onCameraChanged = { 
                cameraSettings = it
                settingsKey++  // Trigger re-creation
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
        
        // Overlay: Model info card
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "3D Model",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "${scene.vertexCount} vertices • ${scene.faceCount} faces",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                
                // Source video info
                scene.sourceVideoFileName?.let { videoName ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Source Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = videoName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    scene.sourceVideoResolution?.let { res ->
                        Text(
                            text = "$res • ${scene.sourceVideoDurationSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
        
        // Overlay: Controls hint
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "• Drag: Rotate",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
                Text(
                    text = "• Pinch: Zoom",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
        
        // Reset camera button
        FloatingActionButton(
            onClick = onResetCamera,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset Camera")
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
