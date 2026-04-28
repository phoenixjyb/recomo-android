package com.recomo.user.phoneteach.ui.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.common.upload.UploadProgress
import com.recomo.common.upload.UploadStatus
import com.recomo.user.phoneteach.ui.library.PhoneTeachSession
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download

/**
 * Upload screen for Phone Teach. Lists every session on disk with its live upload state and
 * an action to queue / retry / cancel via :common's [com.recomo.common.upload.UploadRepository].
 *
 * Server endpoint is read from Settings (CaptureSettingsRepository.serverUrl) — currently
 * pointed at the V3DR Lake test server until the production cloud endpoint lands.
 */
@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
    onPreviewTrajectory: (java.io.File) -> Unit = {},
    viewModel: UploadViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val cloudStatuses by viewModel.cloudStatuses.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Upload",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF3F3F3),
                    fontWeight = FontWeight.SemiBold
                )
                val activeCount = uploads.values.count { it.isInProgress }
                val completedCount = uploads.values.count { it.isCompleted }
                Text(
                    text = "$activeCount active · $completedCount completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A9A9A)
                )
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFFBFBFBF)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Endpoint-not-ready banner (placeholder until cloud migration lands)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0x338A6F1F),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Color(0xFFE0B050)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Production upload endpoint not live yet. Uploads will queue against the V3DR Lake test server and fail-loud until the server migration completes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE0B050)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                sessions.isEmpty() && !isScanning -> {
                    EmptyUploadState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = sessions, key = { it.id }) { session ->
                            val progress = uploads[session.sessionDir.name]
                            val cloudStatus = cloudStatuses[session.sessionDir.name]
                            UploadRow(
                                session = session,
                                progress = progress,
                                cloudStatus = cloudStatus,
                                onUpload = { viewModel.queueUpload(session) },
                                onCancel = { viewModel.cancelUpload(session) },
                                onRetry = { viewModel.retryUpload(session) },
                                onCheckCloud = { viewModel.pollCloudStatus(session) },
                                onDownloadTrajectory = { viewModel.downloadTrajectory(session) },
                                onPreviewTrajectory = {
                                    val tumFile = java.io.File(session.sessionDir, "trajectory_vio_tum.txt")
                                    if (tumFile.exists()) onPreviewTrajectory(tumFile)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyUploadState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = null,
            tint = Color(0xFF4A4A4A),
            modifier = Modifier.size(72.dp)
        )
        Text(
            text = "No sessions to upload",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFBFBFBF)
        )
        Text(
            text = "Record a session in Capture first, then come back to upload it.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7A7A7A)
        )
    }
}

@Composable
private fun UploadRow(
    session: PhoneTeachSession,
    progress: UploadProgress?,
    cloudStatus: CloudStatus?,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onCheckCloud: () -> Unit,
    onDownloadTrajectory: () -> Unit,
    onPreviewTrajectory: () -> Unit = {}
) {
    val status = progress?.status ?: UploadStatus.NOT_UPLOADED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF131313),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(status = status)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.sessionName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEFEFEF),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = "${session.formattedDate} · ${session.formattedSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9A9A9A)
                    )
                }
                UploadActionButton(
                    status = status,
                    onUpload = onUpload,
                    onCancel = onCancel,
                    onRetry = onRetry
                )
            }

            if (status == UploadStatus.UPLOADING || status == UploadStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress?.progress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2D6CDF)
                )
                val pct = ((progress?.progress ?: 0f) * 100).toInt()
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9A9A9A),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            val errorMessage = progress?.errorMessage
            if (status == UploadStatus.FAILED && errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCF6E6E)
                )
            }

            // ─── Cloud processing status + trajectory download ─────────
            if (status == UploadStatus.COMPLETED || cloudStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        cloudStatus == null -> {
                            // Upload done but haven't checked cloud yet
                            OutlinedButton(onClick = onCheckCloud) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check cloud")
                            }
                        }
                        cloudStatus.status == "processing" || cloudStatus.status == "uploaded" -> {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFFE0B050), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Processing on cloud…",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE0B050)
                            )
                        }
                        cloudStatus.status == "completed" && cloudStatus.hasTrajectory -> {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF1F8A3F), modifier = Modifier.size(18.dp))
                            if (cloudStatus.trajectoryDownloaded) {
                                Text(
                                    text = "Trajectory saved locally",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1F8A3F),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = onPreviewTrajectory,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D6CDF))
                                ) {
                                    Text("Preview")
                                }
                            } else {
                                Text(
                                    text = "Trajectory ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1F8A3F),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = onDownloadTrajectory,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F8A3F))
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download")
                                }
                            }
                        }
                        cloudStatus.status == "failed" -> {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFCF6E6E), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Cloud processing failed: ${cloudStatus.error ?: "unknown"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCF6E6E),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = onCheckCloud) { Text("Retry") }
                        }
                        cloudStatus.status == "error" -> {
                            Text(
                                text = "Cloud check failed: ${cloudStatus.error ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9A9A9A)
                            )
                            OutlinedButton(onClick = onCheckCloud) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: UploadStatus) {
    val (icon, tint) = when (status) {
        UploadStatus.NOT_UPLOADED -> Icons.Default.CloudUpload to Color(0xFF7A7A7A)
        UploadStatus.PENDING -> Icons.Default.HourglassEmpty to Color(0xFFE0B050)
        UploadStatus.UPLOADING -> Icons.Default.Upload to Color(0xFF2D6CDF)
        UploadStatus.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF1F8A3F)
        UploadStatus.FAILED -> Icons.Default.ErrorOutline to Color(0xFFCF6E6E)
    }
    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun UploadActionButton(
    status: UploadStatus,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    when (status) {
        UploadStatus.NOT_UPLOADED -> Button(
            onClick = onUpload,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D6CDF))
        ) { Text("Upload") }

        UploadStatus.PENDING,
        UploadStatus.UPLOADING -> OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Cancel")
        }

        UploadStatus.COMPLETED -> Text(
            text = "Done",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF1F8A3F),
            fontWeight = FontWeight.Medium
        )

        UploadStatus.FAILED -> OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Retry")
        }
    }
}

