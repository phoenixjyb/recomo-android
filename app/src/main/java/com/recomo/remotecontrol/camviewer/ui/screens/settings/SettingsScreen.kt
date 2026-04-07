package com.recomo.remotecontrol.camviewer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.camviewer.data.model.AppSettings
import com.recomo.remotecontrol.camviewer.data.model.NetworkPreset
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import com.recomo.remotecontrol.camviewer.data.model.TrackingMode
import com.recomo.remotecontrol.camviewer.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )
    
    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
        }
    }
    
    fun applyNetworkPreset(preset: NetworkPreset, useTztek: Boolean? = null) {
        viewModelScope.launch {
            settingsRepository.applyNetworkPreset(preset, useTztek)
        }
    }

    fun applyRobotProfile(profile: RobotProfile) {
        viewModelScope.launch {
            settingsRepository.applyRobotProfile(profile)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    extraContent: (@Composable () -> Unit)? = null
) {
    val settings by viewModel.settings.collectAsState()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.onSurface
    )
    
    var selectedPreset by remember { mutableStateOf(settings.networkPreset) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var selectedRobot by remember { mutableStateOf(settings.robotProfile) }
    var showRobotMenu by remember { mutableStateOf(false) }
    var useTztekOrin by remember { mutableStateOf(settings.useTztekOrin) }
    var cameraUrl by remember { mutableStateOf(settings.cameraUrl) }
    var orinCameraUrl by remember { mutableStateOf(settings.orinCameraUrl) }
    var orinTargetUrl by remember { mutableStateOf(settings.orinTargetUrl) }
    var orinMediaUrl by remember { mutableStateOf(settings.orinMediaUrl) }
    var orinTrackingUrl by remember { mutableStateOf(settings.orinTrackingUrl) }
    var phoneControlHost by remember { mutableStateOf(settings.phoneControlHost) }
    var viewOnly by remember { mutableStateOf(settings.viewOnly) }
    var developerMode by remember { mutableStateOf(settings.developerModeEnabled) }
    var serviceControlPinEnabled by remember { mutableStateOf(settings.serviceControlPinEnabled) }
    var serviceControlPin by remember { mutableStateOf(settings.serviceControlPin) }
    var useWebRTC by remember { mutableStateOf(settings.useWebRTC) }
    var signalingUrl by remember { mutableStateOf(settings.signalingUrl) }
    var trackingMode by remember { mutableStateOf(settings.trackingMode) }
    var trackingTargetWidthText by remember { mutableStateOf(settings.trackingTargetWidth.toString()) }
    var trackingTargetHeightText by remember { mutableStateOf(settings.trackingTargetHeight.toString()) }
    var showPin by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    
    // Track if user is actively changing settings to prevent race condition
    var isUserEditing by remember { mutableStateOf(false) }
    
    // Update local state when settings change (but not during user edits)
    LaunchedEffect(settings) {
        if (!isUserEditing) {
            selectedPreset = settings.networkPreset
            selectedRobot = settings.robotProfile
            useTztekOrin = settings.useTztekOrin
            cameraUrl = settings.cameraUrl
            orinCameraUrl = settings.orinCameraUrl
            orinTargetUrl = settings.orinTargetUrl
            orinMediaUrl = settings.orinMediaUrl
            orinTrackingUrl = settings.orinTrackingUrl
            phoneControlHost = settings.phoneControlHost
            serviceControlPinEnabled = settings.serviceControlPinEnabled
            trackingTargetWidthText = settings.trackingTargetWidth.toString()
            trackingTargetHeightText = settings.trackingTargetHeight.toString()
        }
    }
    
    // Reset editing flag after a short delay when settings change
    LaunchedEffect(settings) {
        if (isUserEditing) {
            kotlinx.coroutines.delay(500) // Allow DataStore write to complete
            isUserEditing = false
        }
        viewOnly = settings.viewOnly
        developerMode = settings.developerModeEnabled
        serviceControlPin = settings.serviceControlPin
        useWebRTC = settings.useWebRTC
        signalingUrl = settings.signalingUrl
        trackingMode = settings.trackingMode
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Divider()

        // Robot Profile Section
        Text(
            text = "Robot Profile",
            style = MaterialTheme.typography.titleLarge
        )

        ExposedDropdownMenuBox(
            expanded = showRobotMenu,
            onExpandedChange = { showRobotMenu = it }
        ) {
            OutlinedTextField(
                value = selectedRobot.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Robot") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRobotMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = fieldColors
            )

            ExposedDropdownMenu(
                expanded = showRobotMenu,
                onDismissRequest = { showRobotMenu = false }
            ) {
                RobotProfile.selectableProfiles().forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.displayName) },
                        onClick = {
                            isUserEditing = true
                            selectedRobot = profile
                            val resolvedUseTztek = profile.useTztekOrin ?: useTztekOrin
                            if (profile.useTztekOrin != null) {
                                useTztekOrin = resolvedUseTztek
                            }
                            if (selectedPreset != NetworkPreset.CUSTOM) {
                                cameraUrl = selectedPreset.getPhoneVideoUrl()
                                orinCameraUrl = selectedPreset.getOrinCameraUrl(profile, resolvedUseTztek)
                                orinTargetUrl = selectedPreset.getOrinTargetUrl(profile, resolvedUseTztek)
                                orinMediaUrl = selectedPreset.getOrinMediaUrl(profile, resolvedUseTztek)
                                orinTrackingUrl = selectedPreset.getOrinTrackingUrl(profile, resolvedUseTztek)
                                phoneControlHost = selectedPreset.phoneIp
                                signalingUrl = selectedPreset.getSignalingUrl(profile, resolvedUseTztek)
                            }
                            viewModel.applyRobotProfile(profile)
                            showRobotMenu = false
                        }
                    )
                }
            }
        }

        Text(
            text = "Select the robot configuration to align Orin targets and metadata",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Network Preset Section
        Text(
            text = "Network Preset",
            style = MaterialTheme.typography.titleLarge
        )
        
        ExposedDropdownMenuBox(
            expanded = showPresetMenu,
            onExpandedChange = { showPresetMenu = it }
        ) {
            OutlinedTextField(
                value = selectedPreset.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Network") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPresetMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = fieldColors
            )
            
            ExposedDropdownMenu(
                expanded = showPresetMenu,
                onDismissRequest = { showPresetMenu = false }
            ) {
                NetworkPreset.values().forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.displayName)
                if (preset != NetworkPreset.CUSTOM) {
                    Text(
                        text = "Phone: ${preset.phoneIp}, Orin: ${preset.getOrinIp(selectedRobot, useTztekOrin)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
                        },
                        onClick = {
                            isUserEditing = true
                            selectedPreset = preset
                            // Immediately update local fields to reflect the chosen preset
                            if (preset != NetworkPreset.CUSTOM) {
                                cameraUrl = preset.getPhoneVideoUrl()
                                orinCameraUrl = preset.getOrinCameraUrl(selectedRobot, useTztekOrin)
                                orinTargetUrl = preset.getOrinTargetUrl(selectedRobot, useTztekOrin)
                                orinMediaUrl = preset.getOrinMediaUrl(selectedRobot, useTztekOrin)
                                orinTrackingUrl = preset.getOrinTrackingUrl(selectedRobot, useTztekOrin)
                                phoneControlHost = preset.phoneIp
                                signalingUrl = preset.getSignalingUrl(selectedRobot, useTztekOrin)
                            }
                            viewModel.applyNetworkPreset(preset, useTztekOrin)
                            showPresetMenu = false
                        }
                    )
                }
            }
        }
        
        Text(
            text = "Select network configuration preset or use Custom for manual entry",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Orin Selection Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Orin Target",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (useTztekOrin) {
                        "TzTek Orin (${selectedPreset.orinTztekIp})"
                    } else {
                        "AGX Orin (${selectedPreset.orinAgxIp})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useTztekOrin,
                onCheckedChange = { newValue ->
                    isUserEditing = true
                    useTztekOrin = newValue
                    // Auto-update Orin URLs when switching Orins
                    if (selectedPreset != NetworkPreset.CUSTOM) {
                        orinCameraUrl = selectedPreset.getOrinCameraUrl(selectedRobot, newValue)
                        orinTargetUrl = selectedPreset.getOrinTargetUrl(selectedRobot, newValue)
                        orinMediaUrl = selectedPreset.getOrinMediaUrl(selectedRobot, newValue)
                        orinTrackingUrl = selectedPreset.getOrinTrackingUrl(selectedRobot, newValue)
                        signalingUrl = selectedPreset.getSignalingUrl(selectedRobot, newValue)
                        // Immediately apply network preset with the new Orin selection
                        viewModel.applyNetworkPreset(selectedPreset, newValue)
                    }
                },
                enabled = selectedRobot.useTztekOrin == null
            )
        }
        
        Divider()
        
        // Camera Connection Section
        Text(
            text = "Camera Connection",
            style = MaterialTheme.typography.titleLarge
        )
        
        OutlinedTextField(
            value = cameraUrl,
            onValueChange = { 
                cameraUrl = it
                selectedPreset = NetworkPreset.CUSTOM
            },
            label = { Text("Camera WebSocket URL") },
            placeholder = { Text("ws://192.168.100.156:9090") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        
        Text(
            text = "Enter the WebSocket URL of the CamControl phone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = phoneControlHost,
            onValueChange = { 
                phoneControlHost = it
                selectedPreset = NetworkPreset.CUSTOM
            },
            label = { Text("Phone Control Host") },
            placeholder = { Text("192.168.100.156") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        
        Text(
            text = "IP/host used for developer controls sent to the phone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Divider()
        
        // Orin Connection Section
        Text(
            text = "Orin Connection",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = orinCameraUrl,
            onValueChange = {
                orinCameraUrl = it
                selectedPreset = NetworkPreset.CUSTOM
            },
            label = { Text("Orin Camera WebSocket URL") },
            placeholder = { Text("ws://192.168.100.150:9091") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        
        OutlinedTextField(
            value = orinTargetUrl,
            onValueChange = { 
                orinTargetUrl = it
                selectedPreset = NetworkPreset.CUSTOM
            },
            label = { Text("Target API URL") },
            placeholder = { Text("http://192.168.100.150:8082") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        
        OutlinedTextField(
            value = orinMediaUrl,
            onValueChange = { 
                orinMediaUrl = it
                selectedPreset = NetworkPreset.CUSTOM
            },
            label = { Text("Media API URL") },
            placeholder = { Text("http://192.168.100.150:8081") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        
        OutlinedTextField(
            value = orinTrackingUrl,
            onValueChange = { 
                orinTrackingUrl = it
                selectedPreset = NetworkPreset.CUSTOM
            },
            label = { Text("Tracking WebSocket URL") },
            placeholder = { Text("ws://192.168.100.150:8084") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        
        Text(
            text = "Enter Orin URLs for camera WebSocket (9091), target selection, tracking feedback (8084), and media retrieval",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Divider()
        
        // Developer Mode
        Text(
            text = "Advanced",
            style = MaterialTheme.typography.titleLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View-only mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Disable control commands; receive video/telemetry only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewOnly,
                onCheckedChange = { viewOnly = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Developer Mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Enable camera control panel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = developerMode,
                onCheckedChange = { developerMode = it }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tracking Mode",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Choose where tracking runs (local fallback available)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = trackingMode == TrackingMode.OFF,
                    onClick = { trackingMode = TrackingMode.OFF }
                )
                Text(text = "Off")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = trackingMode == TrackingMode.LOCAL,
                    onClick = { trackingMode = TrackingMode.LOCAL }
                )
                Text(text = "Local")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = trackingMode == TrackingMode.CAMCONTROL,
                    onClick = { trackingMode = TrackingMode.CAMCONTROL }
                )
                Text(text = "CamControl")
            }
        }
        
        // WebRTC Transport Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Video Transport",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (useWebRTC) "WebRTC (Low Latency)" else "WebSocket (Default)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useWebRTC,
                onCheckedChange = { useWebRTC = it }
            )
        }
        
        // Signaling URL (only show if WebRTC enabled)
        if (useWebRTC) {
            OutlinedTextField(
                value = signalingUrl,
                onValueChange = { signalingUrl = it },
                label = { Text("Signaling Server") },
                placeholder = { Text("ws://192.168.100.150:9077") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("WebSocket URL for WebRTC signaling")
                },
                colors = fieldColors
            )
        }

        if (developerMode) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tracking Target Resolution",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Resolution used for /target_roi and /recomo/subject_tracking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = trackingTargetWidthText,
                    onValueChange = {
                        isUserEditing = true
                        trackingTargetWidthText = it
                    },
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = trackingTargetHeightText,
                    onValueChange = {
                        isUserEditing = true
                        trackingTargetHeightText = it
                    },
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors
                )
            }
        }
        
        Divider()
        
        // Security Section
        Text(
            text = "Security",
            style = MaterialTheme.typography.titleLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable PIN protection",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = serviceControlPinEnabled,
                onCheckedChange = { serviceControlPinEnabled = it }
            )
        }
        
        OutlinedTextField(
            value = serviceControlPin,
            onValueChange = { serviceControlPin = it },
            label = { Text("Service Control PIN") },
            placeholder = { Text("Set PIN when enabled") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = serviceControlPinEnabled,
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(
                        imageVector = if (showPin) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                    )
                }
            },
            supportingText = {
                Text(
                    if (serviceControlPinEnabled)
                        "PIN required for service start/stop"
                    else
                        "PIN protection disabled (default)"
                )
            },
            colors = fieldColors
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Save Button
        Button(
            onClick = {
                val parsedWidth = trackingTargetWidthText.toIntOrNull()?.coerceAtLeast(1)
                    ?: settings.trackingTargetWidth
                val parsedHeight = trackingTargetHeightText.toIntOrNull()?.coerceAtLeast(1)
                    ?: settings.trackingTargetHeight
                viewModel.updateSettings(
                    AppSettings(
                        networkPreset = selectedPreset,
                        useTztekOrin = useTztekOrin,
                        robotProfile = selectedRobot,
                        cameraUrl = cameraUrl,
                        orinCameraUrl = orinCameraUrl,
                        orinTargetUrl = orinTargetUrl,
                        orinMediaUrl = orinMediaUrl,
                        orinTrackingUrl = orinTrackingUrl,
                        phoneControlHost = phoneControlHost,
                        viewOnly = viewOnly,
                        developerModeEnabled = developerMode,
                        serviceControlPinEnabled = serviceControlPinEnabled,
                        serviceControlPin = serviceControlPin,
                        useWebRTC = useWebRTC,
                        signalingUrl = signalingUrl,
                        trackingMode = trackingMode,
                        trackingTargetWidth = parsedWidth,
                        trackingTargetHeight = parsedHeight
                    )
                )
                showSaved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }
        
        // Saved confirmation
        if (showSaved) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showSaved = false
            }
            Text(
                text = "✓ Settings saved successfully",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (extraContent != null) {
            extraContent()
        }
    }
}
