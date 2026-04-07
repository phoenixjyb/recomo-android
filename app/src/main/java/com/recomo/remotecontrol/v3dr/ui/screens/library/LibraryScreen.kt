package com.recomo.remotecontrol.v3dr.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Library Screen - View and manage recorded videos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onRecordingClick: (String) -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToSceneViewer: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var showCleanupResult by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Recordings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToUpload) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Uploads")
                    }
                    IconButton(onClick = { viewModel.loadRecordings() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clean up old format files") },
                            onClick = {
                                showMenu = false
                                showCleanupDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.CleaningServices, contentDescription = null)
                            }
                        )
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
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                recordings.isEmpty() -> {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    RecordingsList(
                        recordings = recordings,
                        onRecordingClick = onRecordingClick,
                        onNavigateToSceneViewer = onNavigateToSceneViewer,
                        onDeleteClick = { recording ->
                            viewModel.deleteRecording(recording)
                        },
                        onUploadClick = { recording ->
                            viewModel.uploadRecording(recording)
                        }
                    )
                }
            }
        }
    }
    
    // Cleanup confirmation dialog
    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text("Clean Up Old Format Files?") },
            text = { 
                Text("This will delete all recordings that are not in the new session directory format. These files may not have associated IMU data or metadata.\n\nThis action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanupDialog = false
                        viewModel.deleteOldFormatRecordings { deletedCount ->
                            showCleanupResult = deletedCount
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Cleanup result dialog
    showCleanupResult?.let { count ->
        AlertDialog(
            onDismissRequest = { showCleanupResult = null },
            title = { Text("Cleanup Complete") },
            text = { Text("Deleted $count old format file${if (count != 1) "s" else ""}.") },
            confirmButton = {
                TextButton(onClick = { showCleanupResult = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "No recordings yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Start recording to see your videos here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordingsList(
    recordings: List<Recording>,
    onRecordingClick: (String) -> Unit,
    onNavigateToSceneViewer: (String) -> Unit,
    onDeleteClick: (Recording) -> Unit,
    onUploadClick: (Recording) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(recordings, key = { it.id }) { recording ->
            RecordingCard(
                recording = recording,
                onClick = { 
                    val encodedPath = URLEncoder.encode(recording.id, StandardCharsets.UTF_8.toString())
                    onRecordingClick(encodedPath)
                },
                onView3DClick = { 
                    // Extract session ID from file path
                    // For new format: parent directory name (e.g., v3dr_clip_20241118_143045)
                    // For old format: filename without extension
                    val file = File(recording.filePath)
                    val sessionId = if (file.parentFile?.name?.startsWith("v3dr_") == true) {
                        file.parentFile?.name ?: file.nameWithoutExtension
                    } else {
                        file.nameWithoutExtension
                    }
                    onNavigateToSceneViewer(sessionId)
                },
                onDeleteClick = { onDeleteClick(recording) },
                onUploadClick = { onUploadClick(recording) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    onView3DClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Video thumbnail with type badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (recording.thumbnailPath != null && File(recording.thumbnailPath).exists()) {
                        AsyncImage(
                            model = recording.thumbnailPath,
                            contentDescription = "Video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Recording type badge (top-left)
                RecordingTypeBadge(
                    recording = recording,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )
                
                // Trajectory status badge (top-right)
                TrajectoryStatusBadge(
                    recording = recording,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }

            // Recording info at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = recording.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    fontSize = 9.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recording.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onView3DClick,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = "View 3D",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        IconButton(
                            onClick = onUploadClick,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete recording",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingTypeBadge(
    recording: Recording,
    modifier: Modifier = Modifier
) {
    val (icon, label, color) = when (recording.type) {
        RecordingType.ARCORE -> Triple(Icons.Default.ViewInAr, "AR", Color(0xFF4CAF50))
        RecordingType.CAMERA2_IMU -> Triple(Icons.Default.Videocam, "CAM", Color(0xFF2196F3))
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = Color.White
            )
            Text(
                text = label,
                fontSize = 8.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun TrajectoryStatusBadge(
    recording: Recording,
    modifier: Modifier = Modifier
) {
    val (icon, color, tooltip) = when {
        recording.hasTrajectory -> {
            val sourceLabel = when (recording.trajectorySource) {
                "arcore" -> "ARCore"
                "cloud_sfm" -> "Cloud"
                "openvins" -> "OpenVINS"
                else -> "VIO"
            }
            Triple(Icons.Default.Route, Color(0xFF4CAF50), sourceLabel)
        }
        recording.needsVioProcessing -> {
            Triple(Icons.Default.HourglassEmpty, Color(0xFFFF9800), "Needs VIO")
        }
        else -> return // No badge needed
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                modifier = Modifier.size(10.dp),
                tint = Color.White
            )
            Text(
                text = tooltip,
                fontSize = 7.sp,
                color = Color.White
            )
        }
    }
}
