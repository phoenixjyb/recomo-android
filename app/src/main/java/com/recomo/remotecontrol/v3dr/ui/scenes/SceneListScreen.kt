package com.recomo.remotecontrol.v3dr.ui.scenes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus
import com.recomo.remotecontrol.v3dr.data.model.SceneReconstruction
import java.text.SimpleDateFormat
import java.util.*

/**
 * Scene List Screen
 * Displays grid of available 3D reconstructions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneListScreen(
    viewModel: SceneListViewModel = hiltViewModel(),
    onSceneClick: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val isStartingServer by viewModel.isStartingServer.collectAsState()
    val serverMessage by viewModel.serverMessage.collectAsState()
    
    var showFilterMenu by remember { mutableStateOf(false) }
    var sceneToDelete by remember { mutableStateOf<SceneReconstruction?>(null) }

    // Show snackbar for server messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(serverMessage) {
        serverMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearServerMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Scenes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Auto-refresh toggle
                    IconButton(onClick = { viewModel.toggleAutoRefresh() }) {
                        Icon(
                            if (autoRefreshEnabled) Icons.Default.Refresh 
                            else Icons.Default.Autorenew,
                            "Auto-refresh",
                            tint = if (autoRefreshEnabled) 
                                MaterialTheme.colorScheme.primary 
                            else LocalContentColor.current
                        )
                    }
                    
                    // Filter button
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            "Filter",
                            tint = if (selectedFilter != null) 
                                MaterialTheme.colorScheme.primary 
                            else LocalContentColor.current
                        )
                    }
                    
                    // Filter dropdown menu
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Scenes") },
                            onClick = {
                                viewModel.filterByStatus(null)
                                showFilterMenu = false
                            },
                            leadingIcon = if (selectedFilter == null) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Completed") },
                            onClick = {
                                viewModel.filterByStatus(ReconstructionStatus.COMPLETED)
                                showFilterMenu = false
                            },
                            leadingIcon = if (selectedFilter == ReconstructionStatus.COMPLETED) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Processing") },
                            onClick = {
                                viewModel.filterByStatus(ReconstructionStatus.PROCESSING)
                                showFilterMenu = false
                            },
                            leadingIcon = if (selectedFilter == ReconstructionStatus.PROCESSING) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Pending") },
                            onClick = {
                                viewModel.filterByStatus(ReconstructionStatus.PENDING)
                                showFilterMenu = false
                            },
                            leadingIcon = if (selectedFilter == ReconstructionStatus.PENDING) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("Failed") },
                            onClick = {
                                viewModel.filterByStatus(ReconstructionStatus.FAILED)
                                showFilterMenu = false
                            },
                            leadingIcon = if (selectedFilter == ReconstructionStatus.FAILED) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                    }
                    
                    // Refresh button
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is SceneListUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                is SceneListUiState.Error -> {
                    val errorState = uiState as SceneListUiState.Error
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { viewModel.refresh() }) {
                                Text("Retry")
                            }
                            
                            // Show Start Server button if service control is available
                            if (errorState.canStartServer) {
                                Button(
                                    onClick = { viewModel.startServer() },
                                    enabled = !isStartingServer,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    if (isStartingServer) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(if (isStartingServer) "Starting..." else "Start Server")
                                }
                            }
                        }
                        
                        if (errorState.canStartServer) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Server control available - try starting the server remotely",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                is SceneListUiState.Success -> {
                    val scenes = (uiState as SceneListUiState.Success).scenes
                    
                    if (scenes.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No 3D scenes yet",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Record and upload a video to create your first 3D scene",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 180.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(scenes, key = { it.sceneId }) { scene ->
                                SceneCard(
                                    scene = scene,
                                    isDownloading = downloadProgress[scene.sceneId]?.isDownloading == true,
                                    downloadProgress = downloadProgress[scene.sceneId]?.progress ?: 0f,
                                    isCached = viewModel.isModelCached(scene.sceneId),
                                    onClick = { 
                                        // Download if not cached, otherwise view
                                        if (scene.status == ReconstructionStatus.COMPLETED) {
                                            if (!viewModel.isModelCached(scene.sceneId)) {
                                                viewModel.downloadAsset(scene.sceneId, "glb")
                                            } else {
                                                onSceneClick(scene.sceneId)
                                            }
                                        }
                                    },
                                    onDelete = { sceneToDelete = scene },
                                    onCheckStatus = { viewModel.checkStatus(scene.sceneId) },
                                    onDownloadGlb = { viewModel.downloadAsset(scene.sceneId, "glb") },
                                    onDownloadPreview = { viewModel.downloadAsset(scene.sceneId, "preview") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    sceneToDelete?.let { scene ->
        AlertDialog(
            onDismissRequest = { sceneToDelete = null },
            title = { Text("Delete Scene?") },
            text = { Text("This will permanently delete the 3D reconstruction. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteScene(scene.sceneId)
                        sceneToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sceneToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Scene card item
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneCard(
    scene: SceneReconstruction,
    isDownloading: Boolean,
    downloadProgress: Float,
    isCached: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCheckStatus: () -> Unit,
    onDownloadGlb: () -> Unit,
    onDownloadPreview: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable(enabled = scene.status == ReconstructionStatus.COMPLETED) { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            AsyncImage(
                model = scene.thumbnailUrl ?: scene.localCachePath,
                contentDescription = "Scene thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Status overlay
            StatusBadge(
                status = scene.status,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            // Download indicator
            if (isCached) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Cached",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    tint = Color.Green
                )
            }

            // Bottom info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                scene.createdAtMillis?.let { ts ->
                    Text(
                        text = formatTimestamp(ts),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (scene.status == ReconstructionStatus.PROCESSING || scene.status == ReconstructionStatus.PENDING) {
                    LinearProgressIndicator(
                        progress = (scene.progressPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // Download progress
            if (isDownloading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Processing scenes - check status button
            if (scene.status == ReconstructionStatus.PROCESSING || 
                scene.status == ReconstructionStatus.PENDING) {
                IconButton(
                    onClick = onCheckStatus,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Default.Refresh, "Check status")
                }
            }

            if (scene.status == ReconstructionStatus.COMPLETED) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onDownloadGlb, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("GLB")
                    }
                    IconButton(onClick = onDownloadPreview) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Download preview")
                    }
                }
            }

            // Menu button
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.MoreVert, "Options")
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}

/**
 * Status badge
 */
@Composable
private fun StatusBadge(
    status: ReconstructionStatus,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        ReconstructionStatus.COMPLETED -> "Ready" to Color(0xFF4CAF50)
        ReconstructionStatus.PROCESSING -> "Processing" to Color(0xFFFF9800)
        ReconstructionStatus.PENDING -> "Pending" to Color(0xFF2196F3)
        ReconstructionStatus.FAILED -> "Failed" to Color(0xFFF44336)
    }

    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Format timestamp
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
