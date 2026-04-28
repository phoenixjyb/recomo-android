package com.recomo.user.phoneteach.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recomo.common.settings.Bitrate
import com.recomo.common.settings.Resolution

/**
 * Capture settings screen for Phone Teach. Backed by [CaptureSettingsViewModel] which delegates
 * to :common's CaptureSettingsRepository.
 */
@Composable
fun CaptureSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: CaptureSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showFrameRateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFF3F3F3),
            fontWeight = FontWeight.SemiBold
        )

        SectionHeader(title = "Recording Quality")

        SettingRow(
            icon = Icons.Default.Videocam,
            title = "Resolution",
            value = settings.resolution.displayName,
            onClick = { showResolutionDialog = true }
        )
        SettingRow(
            icon = Icons.Default.Speed,
            title = "Bitrate",
            value = settings.bitrate.displayName,
            onClick = { showBitrateDialog = true }
        )
        SettingRow(
            icon = Icons.Default.Videocam,
            title = "Frame rate",
            value = "${settings.frameRate} fps",
            onClick = { showFrameRateDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader(title = "Upload")

        // Server URL (editable text field)
        var urlText by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131313),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color(0xFFBFBFBF),
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Server URL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEFEFEF)
                    )
                }
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://…") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.updateServerUrl(urlText) }) {
                        Text("Save URL")
                    }
                }
                Text(
                    text = "Cloud endpoint will be migrated once the official server is ready. For now point at the V3DR Lake test server.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7A7A7A)
                )
            }
        }

        SettingSwitchRow(
            icon = Icons.Default.CloudUpload,
            title = "Auto upload",
            subtitle = "Queue upload immediately after each recording",
            checked = settings.autoUpload,
            onCheckedChange = { viewModel.updateAutoUpload(it) }
        )
        SettingSwitchRow(
            icon = Icons.Default.Wifi,
            title = "WiFi only",
            subtitle = "Defer uploads when on cellular",
            checked = settings.wifiOnlyUpload,
            onCheckedChange = { viewModel.updateWifiOnlyUpload(it) }
        )
    }

    if (showResolutionDialog) {
        PickerDialog(
            title = "Resolution",
            options = Resolution.entries.toList(),
            selected = settings.resolution,
            labelOf = { it.displayName },
            onDismiss = { showResolutionDialog = false },
            onSelect = {
                viewModel.updateResolution(it)
                showResolutionDialog = false
            }
        )
    }
    if (showBitrateDialog) {
        PickerDialog(
            title = "Bitrate",
            options = Bitrate.entries.toList(),
            selected = settings.bitrate,
            labelOf = { it.displayName },
            onDismiss = { showBitrateDialog = false },
            onSelect = {
                viewModel.updateBitrate(it)
                showBitrateDialog = false
            }
        )
    }
    if (showFrameRateDialog) {
        PickerDialog(
            title = "Frame rate",
            options = listOf(24, 30, 60),
            selected = settings.frameRate,
            labelOf = { "$it fps" },
            onDismiss = { showFrameRateDialog = false },
            onSelect = {
                viewModel.updateFrameRate(it)
                showFrameRateDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF7A7A7A),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF131313),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFBFBFBF),
                modifier = Modifier.width(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFEFEFEF)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9A9A9A)
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF131313),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFBFBFBF),
                modifier = Modifier.width(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFEFEFEF)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7A7A7A)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF2D6CDF)
                )
            )
        }
    }
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = labelOf(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
