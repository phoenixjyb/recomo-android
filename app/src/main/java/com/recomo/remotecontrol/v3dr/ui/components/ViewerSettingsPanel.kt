package com.recomo.remotecontrol.v3dr.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Settings panel for adjusting 3D viewer parameters
 */
@Composable
fun ViewerSettingsPanel(
    lightingSettings: LightingSettings,
    onLightingChanged: (LightingSettings) -> Unit,
    cameraSettings: CameraSettings,
    onCameraChanged: (CameraSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        // Toggle button
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Settings, "Settings")
        }
        
        if (expanded) {
            Card(
                modifier = Modifier.padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .width(300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Viewer Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Divider()
                    
                    // Lighting section
                    Text("Lighting", style = MaterialTheme.typography.titleSmall)
                    
                    LightingSlider(
                        label = "Main Light",
                        value = lightingSettings.mainLightIntensity,
                        onValueChange = { onLightingChanged(lightingSettings.copy(mainLightIntensity = it)) },
                        range = 0f..200000f
                    )
                    
                    LightingSlider(
                        label = "Fill Light",
                        value = lightingSettings.fillLightIntensity,
                        onValueChange = { onLightingChanged(lightingSettings.copy(fillLightIntensity = it)) },
                        range = 0f..200000f
                    )
                    
                    LightingSlider(
                        label = "Back Light",
                        value = lightingSettings.backLightIntensity,
                        onValueChange = { onLightingChanged(lightingSettings.copy(backLightIntensity = it)) },
                        range = 0f..200000f
                    )
                    
                    LightingSlider(
                        label = "Ambient",
                        value = lightingSettings.ambientIntensity,
                        onValueChange = { onLightingChanged(lightingSettings.copy(ambientIntensity = it)) },
                        range = 0f..200000f
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Shadows")
                        Switch(
                            checked = lightingSettings.enableShadows,
                            onCheckedChange = { onLightingChanged(lightingSettings.copy(enableShadows = it)) }
                        )
                    }
                    
                    Divider()
                    
                    // Camera section
                    Text("Camera", style = MaterialTheme.typography.titleSmall)
                    
                    CameraSlider(
                        label = "Field of View",
                        value = cameraSettings.fieldOfView.toFloat(),
                        onValueChange = { onCameraChanged(cameraSettings.copy(fieldOfView = it.toDouble())) },
                        range = 20f..120f,
                        unit = "°"
                    )
                    
                    CameraSlider(
                        label = "Distance",
                        value = cameraSettings.initialDistance,
                        onValueChange = { onCameraChanged(cameraSettings.copy(initialDistance = it)) },
                        range = 0.5f..10f,
                        unit = "m"
                    )
                    
                    // Reset button
                    Button(
                        onClick = {
                            onLightingChanged(LightingSettings())
                            onCameraChanged(CameraSettings())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset to Defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun LightingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "${(value / 1000).toInt()}k",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CameraSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "${value.toInt()}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
