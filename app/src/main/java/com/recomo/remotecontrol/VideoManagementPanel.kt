package com.recomo.remotecontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.ui.RecomoControlViewModel

@Composable
fun VideoManagementOverlay() {
    val viewModel: RecomoControlViewModel = hiltViewModel()
    val status by viewModel.videoManagementStatus.collectAsState()
    val busy by viewModel.videoManagementBusy.collectAsState()
    val error by viewModel.videoManagementError.collectAsState()
    val message by viewModel.videoManagementMessage.collectAsState()
    val service by viewModel.videoManagementServiceStatus.collectAsState()
    var sessionName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshGatewayService()
        viewModel.refreshVideoManagementStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(end = 96.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PanelBox(title = "VIDEO MANAGEMENT", titleSize = 12.sp) {
                VmStatusLine("Service", when {
                    service?.running == true -> "Running"
                    else -> "Stopped"
                })
                VmStatusLine("Recorder", if (status?.recorder?.recording == true) "Recording" else "Idle")
                VmStatusLine("Session", status?.recorder?.sessionId ?: "--")
                VmStatusLine("Frames", status?.recorder?.frameCount?.toString() ?: "0")
                VmStatusLine("Video file", status?.recorder?.videoPath ?: "--")
            }

            PanelBox(title = "STORAGE", titleSize = 12.sp) {
                VmStatusLine("Root", status?.storage?.dataRoot ?: "--")
                VmStatusLine("Used", formatBytes(status?.storage?.usedBytes ?: 0L))
                VmStatusLine("Free", formatBytes(status?.storage?.freeBytes ?: 0L))
                VmStatusLine("Total", formatBytes(status?.storage?.totalBytes ?: 0L))
                VmStatusLine("Sessions", (status?.files?.sessionCount ?: 0).toString())
                VmStatusLine("Videos", (status?.files?.videoCount ?: 0).toString())
                VmStatusLine("Face assets", (status?.files?.faceAssetCount ?: 0).toString())
            }

            PanelBox(title = "UPLOAD QUEUE", titleSize = 12.sp) {
                VmStatusLine("Pending", (status?.uploadQueue?.pending ?: 0).toString())
                VmStatusLine("Uploading", (status?.uploadQueue?.uploading ?: 0).toString())
                VmStatusLine("Completed", (status?.uploadQueue?.completed ?: 0).toString())
                VmStatusLine("Failed", (status?.uploadQueue?.failed ?: 0).toString())
                VmStatusLine("Dead", (status?.uploadQueue?.dead ?: 0).toString())
                VmStatusLine("Total", (status?.uploadQueue?.total ?: 0).toString())
            }

            PanelBox(title = "HEALTH", titleSize = 12.sp) {
                VmStatusLine("DB", if (status?.db?.ok == true) "OK" else "ERR")
                if (!status?.db?.reason.isNullOrBlank()) {
                    VmStatusLine("DB reason", status?.db?.reason ?: "")
                }
                VmStatusLine("COS", if (status?.serverHealth?.cos?.ok == true) "OK" else "ERR")
                VmStatusLine("Backend", if (status?.serverHealth?.backend?.ok == true) "OK" else "ERR")
                VmStatusLine("Worker scan", status?.worker?.lastScanAt ?: "--")
                VmStatusLine("Worker upload", status?.worker?.lastUploadAt ?: "--")
                if (!status?.worker?.latestUploadError.isNullOrBlank()) {
                    VmStatusLine("Upload error", status?.worker?.latestUploadError ?: "")
                }
            }

            PanelBox(title = "ACTIONS", titleSize = 12.sp) {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Session Name (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = if (busy) "BUSY..." else "START RECORD",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.startVideoRecording(sessionName)
                    }
                    SecondaryButton(
                        text = "STOP RECORD",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.stopVideoRecording()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = "START SERVICE",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.startVideoManagementService()
                    }
                    SecondaryButton(
                        text = "STOP SERVICE",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.stopVideoManagementService()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = "RETRY FAILED",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.retryFailedVideoUploads()
                    }
                    SecondaryButton(
                        text = "UPLOAD NOW",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.triggerVideoUploadNow()
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = "REQUEUE",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.requeueForCurrentDestination()
                    }
                    SecondaryButton(
                        text = "REFRESH",
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        viewModel.refreshGatewayService()
                        viewModel.refreshVideoManagementStatus()
                    }
                }
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error ?: "", color = Color(0xFFFFB4B4), fontSize = 11.sp)
                }
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = message ?: "", color = Color(0xFFBFEAC3), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatBytes(v: Long): String {
    if (v <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = v.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx += 1
    }
    return String.format("%.1f %s", value, units[idx])
}

@Composable
private fun VmStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color(0xFFB8B8B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}
