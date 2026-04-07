package com.recomo.remotecontrol.v3dr.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.v3dr.data.VioBackendType

/**
 * Home Screen - Main landing page for V3DR
 * Provides quick access to recording, library, and settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRecording: () -> Unit,
    onNavigateToArCoreRecording: () -> Unit = {},
    onNavigateToLibrary: () -> Unit,
    onNavigateToSceneList: () -> Unit = {},
    onNavigateToTrajectoryViewer: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val storageStats by viewModel.storageStats.collectAsState()
    val vioBackend by viewModel.vioBackend.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("V3DR - 3D Video Data Recorder") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App logo/title
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Welcome to V3DR",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Record videos with synchronized sensor data for 3D reconstruction",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Main action buttons
            Button(
                onClick = {
                    if (vioBackend == VioBackendType.ARCORE) {
                        onNavigateToArCoreRecording()
                    } else {
                        onNavigateToRecording()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Start Recording", style = MaterialTheme.typography.titleMedium)
                    if (vioBackend == VioBackendType.ARCORE) {
                        Text(
                            text = "ARCore mode",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            OutlinedButton(
                onClick = onNavigateToLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("My Recordings", style = MaterialTheme.typography.titleMedium)
            }
            
            OutlinedButton(
                onClick = onNavigateToSceneList,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.ViewInAr, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View 3D Scenes", style = MaterialTheme.typography.titleMedium)
            }
            
            OutlinedButton(
                onClick = onNavigateToTrajectoryViewer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Route, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Trajectory", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Stats Section
            StatsSection(
                recordingsCount = storageStats?.recordingsCount ?: 0,
                storageSizeMB = storageStats?.storageSizeMB ?: 0,
                onRefresh = { viewModel.loadStorageStats() }
            )
        }
    }
}

@Composable
private fun StatsSection(
    recordingsCount: Int,
    storageSizeMB: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stats",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh Stats",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard(
                title = recordingsCount.toString(),
                subtitle = "Recordings",
                icon = Icons.Default.Videocam,
                modifier = Modifier.weight(1f)
            )
            
            InfoCard(
                title = if (storageSizeMB > 1024) {
                    String.format("%.1f GB", storageSizeMB / 1024.0)
                } else {
                    "$storageSizeMB MB"
                },
                subtitle = "Storage Used",
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
