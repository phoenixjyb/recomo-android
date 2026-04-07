package com.recomo.remotecontrol.v3dr.ui.screens.upload

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.remotecontrol.v3dr.data.model.UploadProgress
import com.recomo.remotecontrol.v3dr.data.model.UploadStatus

/**
 * Upload management screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onNavigateBack: () -> Unit,
    viewModel: UploadViewModel = hiltViewModel()
) {
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val uploadUrl by viewModel.uploadUrl.collectAsStateWithLifecycle()

    var showUrlDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uploads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showUrlDialog = true }) {
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
        ) {
            // Upload URL status
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (uploadUrl.isNullOrBlank()) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uploadUrl.isNullOrBlank()) Icons.Default.Warning else Icons.Default.CloudUpload,
                        contentDescription = null
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uploadUrl.isNullOrBlank()) "Upload URL not configured" else "Upload URL configured",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!uploadUrl.isNullOrBlank()) {
                            Text(
                                text = uploadUrl!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Upload list
            if (uploads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "No uploads",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uploads, key = { it.recordingId }) { upload ->
                        UploadCard(
                            upload = upload,
                            onRetry = { viewModel.retryUpload(upload.recordingId) },
                            onCancel = { viewModel.cancelUpload(upload.recordingId) }
                        )
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        UploadUrlDialog(
            currentUrl = uploadUrl ?: "",
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                viewModel.setUploadUrl(url)
                showUrlDialog = false
            }
        )
    }
}

@Composable
private fun UploadCard(
    upload: UploadProgress,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = upload.recordingId.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Text(
                        text = getStatusText(upload.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = getStatusColor(upload.status)
                    )
                }

                when (upload.status) {
                    UploadStatus.UPLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    UploadStatus.FAILED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Refresh, "Retry", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    UploadStatus.PENDING, UploadStatus.NOT_UPLOADED -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(20.dp))
                        }
                    }
                    UploadStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Progress bar for uploading
            if (upload.status == UploadStatus.UPLOADING) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { upload.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(upload.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error message
            if (upload.status == UploadStatus.FAILED && upload.errorMessage != null) {
                Text(
                    text = upload.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UploadUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the server URL for uploads:")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("http://example.com/upload") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank()
            ) {
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
private fun getStatusText(status: UploadStatus): String {
    return when (status) {
        UploadStatus.NOT_UPLOADED -> "Not uploaded"
        UploadStatus.PENDING -> "Pending..."
        UploadStatus.UPLOADING -> "Uploading..."
        UploadStatus.COMPLETED -> "Completed"
        UploadStatus.FAILED -> "Failed"
    }
}

@Composable
private fun getStatusColor(status: UploadStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        UploadStatus.NOT_UPLOADED -> MaterialTheme.colorScheme.onSurfaceVariant
        UploadStatus.PENDING -> MaterialTheme.colorScheme.primary
        UploadStatus.UPLOADING -> MaterialTheme.colorScheme.primary
        UploadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        UploadStatus.FAILED -> MaterialTheme.colorScheme.error
    }
}
