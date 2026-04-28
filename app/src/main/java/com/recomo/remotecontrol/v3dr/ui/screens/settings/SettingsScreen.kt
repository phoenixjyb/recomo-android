package com.recomo.remotecontrol.v3dr.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.common.settings.Bitrate
import com.recomo.common.settings.NetworkProfile
import com.recomo.common.settings.NetworkProfiles
import com.recomo.common.settings.Resolution
import com.recomo.remotecontrol.v3dr.data.VioBackendType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVioDiagnostics: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showFrameRateDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showVioBackendDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recording Quality Section
            SectionHeader("Recording Quality")
            
            SettingClickableItem(
                title = "Resolution",
                value = settings.resolution.displayName,
                icon = Icons.Default.Videocam,
                onClick = { showResolutionDialog = true }
            )
            
            SettingClickableItem(
                title = "Bitrate",
                value = settings.bitrate.displayName,
                icon = Icons.Default.Speed,
                onClick = { showBitrateDialog = true }
            )
            
            SettingClickableItem(
                title = "Frame Rate",
                value = "${settings.frameRate} FPS",
                icon = Icons.Default.MonitorHeart,
                onClick = { showFrameRateDialog = true }
            )
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Upload Settings Section
            SectionHeader("Upload Settings")
            
            SettingClickableItem(
                title = "Network Profile",
                value = "${settings.networkTag.name} - ${settings.serverUrl}",
                icon = Icons.Default.Cloud,
                onClick = { showNetworkDialog = true }
            )
            
            // Test Connection Button
            Button(
                onClick = { viewModel.testServerConnection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test Server Connection")
            }
            
            SettingSwitchItem(
                title = "Auto Upload",
                description = "Automatically upload recordings after completion",
                icon = Icons.Default.CloudUpload,
                checked = settings.autoUpload,
                onCheckedChange = { viewModel.updateAutoUpload(it) }
            )
            
            SettingSwitchItem(
                title = "WiFi Only Upload",
                description = "Only upload when connected to WiFi",
                icon = Icons.Default.Wifi,
                checked = settings.wifiOnlyUpload,
                onCheckedChange = { viewModel.updateWifiOnlyUpload(it) }
            )
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Account Section
            SectionHeader("Account")
            
            SettingClickableItem(
                title = "Login / Account",
                value = "Sign in to upload recordings",
                icon = Icons.Default.AccountCircle,
                onClick = onNavigateToAuth
            )
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Storage Section
            SectionHeader("Storage")
            
            storageInfo?.let { info ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Size:", style = MaterialTheme.typography.bodyMedium)
                            Text("${info.totalSizeMB} MB", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Recording Sessions:", style = MaterialTheme.typography.bodyMedium)
                            Text("${info.sessions}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Files:", style = MaterialTheme.typography.bodyMedium)
                            Text("${info.totalFiles}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear All Recordings")
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Diagnostics Section
            SectionHeader("Diagnostics")

            SettingClickableItem(
                title = "VIO Dependency Diagnostics",
                value = "OpenVINS / OpenCV / Boost / Eigen",
                icon = Icons.Default.BugReport,
                onClick = onNavigateToVioDiagnostics
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // VIO Settings Section
            SectionHeader("VIO (Visual-Inertial Odometry)")

            SettingClickableItem(
                title = "VIO Backend",
                value = settings.vioBackend.displayName,
                icon = Icons.Default.Timeline,
                onClick = { showVioBackendDialog = true }
            )

            Text(
                text = settings.vioBackend.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (settings.vioBackend == VioBackendType.CLOUD_SFM) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Cloud SFM Server",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            settings.cloudSfmUrl,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // About Section
            SectionHeader("About")
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "V3DR - 3D Video Data Recorder",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Device: ${Build.MODEL}", style = MaterialTheme.typography.bodySmall)
                    Text("Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    
    // Dialogs
    if (showResolutionDialog) {
        ResolutionDialog(
            currentResolution = settings.resolution,
            onDismiss = { showResolutionDialog = false },
            onSelect = { 
                viewModel.updateResolution(it)
                showResolutionDialog = false
            }
        )
    }
    
    if (showBitrateDialog) {
        BitrateDialog(
            currentBitrate = settings.bitrate,
            onDismiss = { showBitrateDialog = false },
            onSelect = { 
                viewModel.updateBitrate(it)
                showBitrateDialog = false
            }
        )
    }
    
    if (showFrameRateDialog) {
        FrameRateDialog(
            currentFrameRate = settings.frameRate,
            onDismiss = { showFrameRateDialog = false },
            onSelect = { 
                viewModel.updateFrameRate(it)
                showFrameRateDialog = false
            }
        )
    }
    
    if (showNetworkDialog) {
        NetworkProfileDialog(
            currentProfile = NetworkProfiles.getByUrl(settings.serverUrl),
            currentUrl = settings.serverUrl,
            onDismiss = { showNetworkDialog = false },
            onSelectProfile = { 
                viewModel.updateNetworkProfile(it)
                showNetworkDialog = false
            },
            onCustomUrl = { url ->
                viewModel.updateServerUrl(url)
                showNetworkDialog = false
            }
        )
    }
    
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Recordings?") },
            text = { Text("This will permanently delete all recorded videos, IMU data, and metadata. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllRecordings { success ->
                            if (success) {
                                showClearDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVioBackendDialog) {
        VioBackendDialog(
            currentBackend = settings.vioBackend,
            onDismiss = { showVioBackendDialog = false },
            onSelect = {
                viewModel.updateVioBackend(it)
                showVioBackendDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingClickableItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ResolutionDialog(
    currentResolution: Resolution,
    onDismiss: () -> Unit,
    onSelect: (Resolution) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Resolution") },
        text = {
            Column {
                Resolution.entries.forEach { resolution ->
                    RadioButtonItem(
                        text = resolution.displayName,
                        selected = resolution == currentResolution,
                        onClick = { onSelect(resolution) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BitrateDialog(
    currentBitrate: Bitrate,
    onDismiss: () -> Unit,
    onSelect: (Bitrate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Bitrate") },
        text = {
            Column {
                Bitrate.entries.forEach { bitrate ->
                    RadioButtonItem(
                        text = bitrate.displayName,
                        selected = bitrate == currentBitrate,
                        onClick = { onSelect(bitrate) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FrameRateDialog(
    currentFrameRate: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val frameRates = listOf(30, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Frame Rate") },
        text = {
            Column {
                frameRates.forEach { fps ->
                    RadioButtonItem(
                        text = "$fps FPS",
                        selected = fps == currentFrameRate,
                        onClick = { onSelect(fps) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NetworkProfileDialog(
    currentProfile: NetworkProfile?,
    currentUrl: String,
    onDismiss: () -> Unit,
    onSelectProfile: (NetworkProfile) -> Unit,
    onCustomUrl: (String) -> Unit
) {
    var showCustomUrlInput by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf(currentUrl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Select a network profile or enter a custom URL",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Predefined network profiles
                NetworkProfiles.getAll().forEach { profile ->
                    RadioButtonItem(
                        text = "${profile.name}\n${profile.description}\n${profile.serverUrl}",
                        selected = currentProfile?.tag == profile.tag,
                        onClick = { onSelectProfile(profile) }
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Custom URL option
                if (!showCustomUrlInput) {
                    TextButton(
                        onClick = { showCustomUrlInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Custom URL")
                    }
                } else {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Custom Server URL") },
                        placeholder = { Text("http://your-server:8771") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomUrlInput = false }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = { onCustomUrl(customUrl) },
                            enabled = customUrl.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showCustomUrlInput) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = null
    )
}

@Composable
private fun ServerUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server URL") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("http://192.168.100.100:8771") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(url) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RadioButtonItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun VioBackendDialog(
    currentBackend: VioBackendType,
    onDismiss: () -> Unit,
    onSelect: (VioBackendType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select VIO Backend") },
        text = {
            Column {
                VioBackendType.entries.forEach { backend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(backend) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = backend == currentBackend,
                            onClick = { onSelect(backend) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                backend.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                backend.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = null
    )
}
