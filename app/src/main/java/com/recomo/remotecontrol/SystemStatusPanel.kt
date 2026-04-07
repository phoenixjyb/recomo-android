package com.recomo.remotecontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.camviewer.data.model.NetworkPreset
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import com.recomo.remotecontrol.camviewer.data.model.TrackingMode
import com.recomo.remotecontrol.camviewer.ui.screens.settings.SettingsViewModel
import com.recomo.remotecontrol.ui.DriverLifecycleState
import com.recomo.remotecontrol.ui.OdomMode
import com.recomo.remotecontrol.ui.RecomoControlViewModel
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// ---------------------------------------------------------------------------
// GROUP: CONNECT
// Combines: gateway connection, gateway service, state stream,
//           signals/safety, topic health, prepare robot,
//           PnC authority, stack lifecycle, command topics
// ---------------------------------------------------------------------------

@Composable
fun GatewayGroupContent(viewModel: RecomoControlViewModel) {
    // --- connection state ---
    val connection by viewModel.connectionState.collectAsState()
    val gatewayUrl by viewModel.gatewayUrl.collectAsState()
    val lastStateAt by viewModel.lastStateAtMs.collectAsState()
    val stateHz by viewModel.stateHz.collectAsState()
    val gatewayService by viewModel.gatewayServiceStatus.collectAsState()
    val gatewayServiceBusy by viewModel.gatewayServiceBusy.collectAsState()
    val gatewayServiceError by viewModel.gatewayServiceError.collectAsState()
    val gatewayApiStatus by viewModel.gatewayApiStatus.collectAsState()
    val gatewayApiLatency by viewModel.gatewayApiLatencyMs.collectAsState()

    // --- robot state ---
    val robotState by viewModel.robotState.collectAsState()
    val safety by viewModel.safetyStatus.collectAsState()
    val driverLifecycle by viewModel.driverLifecycle.collectAsState()
    val recordingTopicsBusy by viewModel.recordingTopicsBusy.collectAsState()
    val recordingTopicsError by viewModel.recordingTopicsError.collectAsState()
    val recordingTopicsMessage by viewModel.recordingTopicsMessage.collectAsState()

    // --- stacks state ---
    val stackActionBusy by viewModel.stackActionBusy.collectAsState()
    val stackActionError by viewModel.stackActionError.collectAsState()
    val stackActionMessage by viewModel.stackActionMessage.collectAsState()
    val operationMode by viewModel.operationMode.collectAsState()
    val selectedLocation by viewModel.selectedMap.collectAsState()
    val selectedMapAsset by viewModel.selectedMapAsset.collectAsState()

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    val ageMs = if (lastStateAt > 0L) (nowMs - lastStateAt).coerceAtLeast(0L) else null
    val streamLabel = when {
        ageMs == null -> "--"
        ageMs < 800 -> "Live"
        ageMs < 2000 -> "Lag"
        else -> "Stale"
    }
    val svcLabel = when {
        gatewayServiceBusy -> "Working..."
        gatewayService?.running == true -> "Running${gatewayService?.pid?.let { " (pid $it)" } ?: ""}"
        gatewayService != null -> "Stopped"
        else -> "--"
    }
    val apiLabel = when {
        gatewayApiStatus == "OK" && gatewayApiLatency != null -> "OK ${gatewayApiLatency}ms"
        gatewayApiStatus == "OK" -> "OK"
        gatewayApiStatus == "ERR" -> "ERR"
        else -> "--"
    }

    val topicHealth = robotState?.get("topic_health")?.jsonObject
    val warnings = robotState?.get("safety")?.jsonObject
        ?.get("warnings")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    val baseOk = robotState?.get("base")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val armOk = robotState?.get("arm")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val gimbalOk = robotState?.get("gimbal")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val cameraOk = robotState?.get("camera")?.jsonObject?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
    val odomPubs = topicHealth?.get("odom_pubs")?.jsonPrimitive?.intOrNull ?: 0
    val imuPubs = topicHealth?.get("imu_pubs")?.jsonPrimitive?.intOrNull ?: 0
    val armPubs = topicHealth?.get("arm_state_pubs")?.jsonPrimitive?.intOrNull ?: 0
    val gimbalPubs = topicHealth?.get("gimbal_state_pubs")?.jsonPrimitive?.intOrNull ?: 0
    val odomDataOk = topicHealth?.get("odom_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val imuDataOk = topicHealth?.get("imu_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val armDataOk = topicHealth?.get("arm_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val gimbalDataOk = topicHealth?.get("gimbal_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val cameraDataOk = topicHealth?.get("camera_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val cameraTfOk = topicHealth?.get("camera_tf_ok")?.jsonPrimitive?.booleanOrNull ?: false
    val recordingLabel = when (driverLifecycle) {
        DriverLifecycleState.READY -> "Ready"
        DriverLifecycleState.PREPARING -> "Preparing"
        DriverLifecycleState.DISMISSING -> "Dismissing"
        DriverLifecycleState.IDLE -> "--"
    }
    val runState = robotState?.get("run")?.jsonObject
    val pncAuthority = runState?.get("pnc_authority")?.jsonPrimitive?.contentOrNull ?: "auto"
    val topics = robotState?.get("topics")?.jsonObject

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // --- Connection ---
        SectionHeader("Connection")
        StatusLine("Gateway", gatewayUrl.ifBlank { "--" })
        StatusLine("Status", connectionLabel(connection))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("RECONNECT", Modifier.weight(1f).height(28.dp)) { viewModel.reconnectGateway() }
            SecondaryButton("DISCONNECT", Modifier.weight(1f).height(28.dp)) { viewModel.disconnectGateway() }
        }

        // --- Gateway Service ---
        SectionHeader("Gateway Service", topPadding = 10.dp)
        StatusLine("Service", svcLabel)
        StatusLine("API", apiLabel)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("CHECK", Modifier.weight(1f).height(28.dp), enabled = !gatewayServiceBusy) { viewModel.refreshGatewayService() }
            SecondaryButton("START", Modifier.weight(1f).height(28.dp), enabled = !gatewayServiceBusy && gatewayService?.running != true) { viewModel.startGatewayService() }
            SecondaryButton("STOP", Modifier.weight(1f).height(28.dp), enabled = !gatewayServiceBusy && gatewayService?.running == true) { viewModel.stopGatewayService() }
        }
        if (!gatewayServiceError.isNullOrBlank()) {
            Text(gatewayServiceError ?: "", color = Color(0xFFFF6666), fontSize = 11.sp)
        }

        // --- State Stream ---
        SectionHeader("State Stream", topPadding = 10.dp)
        StatusLine("Age", ageMs?.let { "${it}ms" } ?: "--")
        StatusLine("Rate", if (stateHz > 0.0) String.format("%.1f Hz", stateHz) else "--")
        StatusLine("Stream", streamLabel)

        // --- Signals & Safety ---
        SectionHeader("Signals & Safety", topPadding = 10.dp)
        StatusLine("Drivers", "base:${flag(baseOk)} arm:${flag(armOk)} gimbal:${flag(gimbalOk)} cam:${flag(cameraOk)}")
        StatusLine("Safety", "estop:${flag(safety.estop)} freeze:${flag(safety.freezeAll)} comm:${flag(safety.commOk)}")
        StatusLine("Last warning", warnings.lastOrNull() ?: "None")

        // --- Topic Health ---
        SectionHeader("Topic Health", topPadding = 10.dp)
        RecordingTopicLine("odom_wheel", odomPubs, odomDataOk)
        RecordingTopicLine("chassis_imu", imuPubs, imuDataOk)
        RecordingTopicLine("arm_state", armPubs, armDataOk)
        RecordingTopicLine("gimbal_state", gimbalPubs, gimbalDataOk)
        StatusLine("Camera pose", "topic:${if (cameraDataOk) "OK" else "--"} tf:${if (cameraTfOk) "OK" else "--"}")

        // --- Prepare Robot ---
        SectionHeader("Prepare Robot", topPadding = 10.dp)
        StatusLine("Status", recordingLabel)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val canPrepare = !recordingTopicsBusy && driverLifecycle == DriverLifecycleState.IDLE
            val canDismiss = !recordingTopicsBusy &&
                (driverLifecycle == DriverLifecycleState.READY || driverLifecycle == DriverLifecycleState.PREPARING)
            SecondaryButton(if (recordingTopicsBusy) "..." else "PREPARE", Modifier.weight(1f).height(28.dp), enabled = canPrepare) { viewModel.startRecordingTopics() }
            SecondaryButton(if (recordingTopicsBusy) "..." else "DISMISS", Modifier.weight(1f).height(28.dp), enabled = canDismiss) { viewModel.stopRecordingTopics() }
        }
        if (!recordingTopicsError.isNullOrBlank()) Text(recordingTopicsError ?: "", color = Color(0xFFFF6666), fontSize = 11.sp)
        else if (!recordingTopicsMessage.isNullOrBlank()) Text(recordingTopicsMessage ?: "", color = Color(0xFF88FF88), fontSize = 11.sp)

        // --- Context ---
        SectionHeader("Context", topPadding = 10.dp)
        StatusLine("Mode", operationMode.name.lowercase())
        StatusLine("PnC authority", pncAuthority)
        StatusLine("Map ctx", "${selectedLocation ?: "--"} / ${selectedMapAsset ?: "--"}")

        // --- PnC Authority ---
        SectionHeader("PnC Authority", topPadding = 6.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("AUTO", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.setPncAuthority("auto") }
            SecondaryButton("LOCAL", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.setPncAuthority("local") }
            SecondaryButton("EXTERNAL", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.setPncAuthority("external") }
        }

        // --- Stack Lifecycle ---
        SectionHeader("Stack Lifecycle", topPadding = 6.dp)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(if (stackActionBusy) "..." else "PREP PERCEPTION", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.preparePerceptionNodes() }
            SecondaryButton("STOP", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.dismissPerceptionNodes() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(if (stackActionBusy) "..." else "PREP LOCALIZE", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy && !selectedLocation.isNullOrBlank()) { viewModel.prepareLocalizationNodes() }
            SecondaryButton("STOP", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.dismissLocalizationNodes() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(if (stackActionBusy) "..." else "PREP PNC", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.preparePncNodesForCurrentMode() }
            SecondaryButton("STOP", Modifier.weight(1f).height(28.dp), enabled = !stackActionBusy) { viewModel.dismissPncNodes() }
        }
        if (!stackActionError.isNullOrBlank()) Text(stackActionError ?: "", color = Color(0xFFFF6666), fontSize = 11.sp)
        else if (!stackActionMessage.isNullOrBlank()) Text(stackActionMessage ?: "", color = Color(0xFF88FF88), fontSize = 11.sp)

        // --- Command Topics ---
        SectionHeader("Command Topics", topPadding = 6.dp)
        TopicLine("base_cmd", topics?.get("base_cmd")?.jsonPrimitive?.contentOrNull ?: "/mobile_base/commands/velocity")
        TopicLine("arm_cmd", topics?.get("arm_cmd")?.jsonPrimitive?.contentOrNull ?: "/right_arm/joint_command")
        TopicLine("gimbal_cmd", topics?.get("gimbal_cmd")?.jsonPrimitive?.contentOrNull ?: "/gimbal/command")
        TopicLine("nav_goal", topics?.get("nav_goal")?.jsonPrimitive?.contentOrNull ?: "/goal_pose")
        TopicLine("target_roi", topics?.get("target_roi")?.jsonPrimitive?.contentOrNull ?: "/target_roi")
        TopicLine("pnc_param_srv", topics?.get("pnc_param_service")?.jsonPrimitive?.contentOrNull ?: "/wheeledrobot_cmdpubliser/set_parameters")
        TopicLine("ref_pose", topics?.get("reference_pose")?.jsonPrimitive?.contentOrNull ?: "/recomo/reference/ee_pose")
        TopicLine("ref_idx", topics?.get("reference_index")?.jsonPrimitive?.contentOrNull ?: "/recomo/reference/index")
    }
}

// ---------------------------------------------------------------------------
// GROUP: ROBOT
// Robot Profile, Network Preset, Orin Target, Camera Connection, Orin Connection
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotGroupContent(viewModel: RecomoControlViewModel) {
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.settings.collectAsState()
    val robotIdentity by viewModel.robotIdentity.collectAsState()
    val robotIdentityBusy by viewModel.robotIdentityBusy.collectAsState()
    val robotIdentityError by viewModel.robotIdentityError.collectAsState()

    var selectedRobot by remember { mutableStateOf(settings.robotProfile) }
    var showRobotMenu by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(settings.networkPreset) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var useTztekOrin by remember { mutableStateOf(settings.useTztekOrin) }
    var orinCameraUrl by remember { mutableStateOf(settings.orinCameraUrl) }
    var orinTargetUrl by remember { mutableStateOf(settings.orinTargetUrl) }
    var orinMediaUrl by remember { mutableStateOf(settings.orinMediaUrl) }
    var orinTrackingUrl by remember { mutableStateOf(settings.orinTrackingUrl) }
    var isUserEditing by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        if (!isUserEditing) {
            selectedRobot = settings.robotProfile
            selectedPreset = settings.networkPreset
            useTztekOrin = settings.useTztekOrin
            orinCameraUrl = settings.orinCameraUrl
            orinTargetUrl = settings.orinTargetUrl
            orinMediaUrl = settings.orinMediaUrl
            orinTrackingUrl = settings.orinTrackingUrl
        }
    }
    LaunchedEffect(settings) {
        if (isUserEditing) {
            kotlinx.coroutines.delay(500)
            isUserEditing = false
        }
    }
    LaunchedEffect(settings.orinTargetUrl, settings.signalingUrl) {
        viewModel.refreshRobotIdentity()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFFD0D0D0),
        unfocusedLabelColor = Color(0xFF888888),
        focusedBorderColor = Color(0xFF888888),
        unfocusedBorderColor = Color(0xFF555555),
        cursorColor = Color.White,
        disabledTextColor = Color(0xFF666666),
        disabledLabelColor = Color(0xFF555555),
        disabledBorderColor = Color(0xFF444444)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Robot Profile ---
        SectionHeader("Robot Profile")
        ExposedDropdownMenuBox(expanded = showRobotMenu, onExpandedChange = { showRobotMenu = it }) {
            OutlinedTextField(
                value = selectedRobot.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Robot", fontSize = 10.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRobotMenu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = fieldColors
            )
            ExposedDropdownMenu(expanded = showRobotMenu, onDismissRequest = { showRobotMenu = false }) {
                RobotProfile.selectableProfiles().forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.displayName) },
                        onClick = {
                            isUserEditing = true
                            selectedRobot = profile
                            val resolvedUseTztek = profile.useTztekOrin ?: useTztekOrin
                            if (profile.useTztekOrin != null) useTztekOrin = resolvedUseTztek
                            if (selectedPreset != NetworkPreset.CUSTOM) {
                                orinCameraUrl = selectedPreset.getOrinCameraUrl(profile, resolvedUseTztek)
                                orinTargetUrl = selectedPreset.getOrinTargetUrl(profile, resolvedUseTztek)
                                orinMediaUrl = selectedPreset.getOrinMediaUrl(profile, resolvedUseTztek)
                                orinTrackingUrl = selectedPreset.getOrinTrackingUrl(profile, resolvedUseTztek)
                            }
                            settingsVm.applyRobotProfile(profile)
                            showRobotMenu = false
                        }
                    )
                }
            }
        }

        // --- Network Preset ---
        SectionHeader("Network Preset", topPadding = 6.dp)
        ExposedDropdownMenuBox(expanded = showPresetMenu, onExpandedChange = { showPresetMenu = it }) {
            OutlinedTextField(
                value = selectedPreset.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Network", fontSize = 10.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPresetMenu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = fieldColors
            )
            ExposedDropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                NetworkPreset.values().forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                val presetOrinIp = preset.getOrinIp(selectedRobot, useTztekOrin).ifBlank { "--" }
                                Text(preset.displayName)
                                if (preset != NetworkPreset.CUSTOM) {
                                    Text(
                                        "Orin: $presetOrinIp",
                                        fontSize = 10.sp,
                                        color = Color(0xFF888888)
                                    )
                                }
                            }
                        },
                        onClick = {
                            isUserEditing = true
                            selectedPreset = preset
                            if (preset != NetworkPreset.CUSTOM) {
                                orinCameraUrl = preset.getOrinCameraUrl(selectedRobot, useTztekOrin)
                                orinTargetUrl = preset.getOrinTargetUrl(selectedRobot, useTztekOrin)
                                orinMediaUrl = preset.getOrinMediaUrl(selectedRobot, useTztekOrin)
                                orinTrackingUrl = preset.getOrinTrackingUrl(selectedRobot, useTztekOrin)
                            }
                            settingsVm.applyNetworkPreset(preset, useTztekOrin)
                            showPresetMenu = false
                        }
                    )
                }
            }
        }

        // Unit-managed target info
        SectionHeader("Robot Routing", topPadding = 6.dp)
        StatusLine("Orin Target", selectedPreset.getOrinIp(selectedRobot, useTztekOrin).ifBlank { "--" })
        StatusLine("Robot WiFi", selectedRobot.getRobotWifiSsid() ?: "--")

        // --- Orin Connection ---
        SectionHeader("Orin Connection", topPadding = 6.dp)
        OutlinedTextField(
            value = orinCameraUrl,
            onValueChange = { orinCameraUrl = it; selectedPreset = NetworkPreset.CUSTOM },
            label = { Text("Orin Camera WS URL", fontSize = 10.sp) },
            placeholder = { Text("ws://192.168.100.150:9091", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        OutlinedTextField(
            value = orinTargetUrl,
            onValueChange = { orinTargetUrl = it; selectedPreset = NetworkPreset.CUSTOM },
            label = { Text("Target API URL", fontSize = 10.sp) },
            placeholder = { Text("http://192.168.100.150:8082", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        OutlinedTextField(
            value = orinMediaUrl,
            onValueChange = { orinMediaUrl = it; selectedPreset = NetworkPreset.CUSTOM },
            label = { Text("Media API URL", fontSize = 10.sp) },
            placeholder = { Text("http://192.168.100.150:8081", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )
        OutlinedTextField(
            value = orinTrackingUrl,
            onValueChange = { orinTrackingUrl = it; selectedPreset = NetworkPreset.CUSTOM },
            label = { Text("Tracking WS URL", fontSize = 10.sp) },
            placeholder = { Text("ws://192.168.100.150:8084", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = selectedPreset == NetworkPreset.CUSTOM,
            colors = fieldColors
        )

        // --- Connected Robot Identity ---
        SectionHeader("Connected Robot Identity", topPadding = 6.dp)
        StatusLine(
            "State",
            when {
                robotIdentityBusy -> "Reading..."
                robotIdentity != null -> "OK"
                !robotIdentityError.isNullOrBlank() -> "ERR"
                else -> "--"
            }
        )
        StatusLine("Unit", robotIdentity?.robotUnitId?.ifBlank { "--" } ?: "--")
        StatusLine("Variant", robotIdentity?.hardwareVariant?.ifBlank { "--" } ?: "--")
        StatusLine(
            "Family",
            listOf(
                robotIdentity?.productFamily?.ifBlank { null },
                robotIdentity?.platformRev?.ifBlank { null }?.let { "rev $it" }
            ).filterNotNull().joinToString(" ").ifBlank { "--" }
        )
        StatusLine("Device", robotIdentity?.deviceId?.ifBlank { "--" } ?: "--")
        StatusLine("Serial", robotIdentity?.robotSn?.ifBlank { "--" } ?: "--")
        StatusLine("SSID", robotIdentity?.robotSsid?.ifBlank { "--" } ?: "--")
        StatusLine("LAN", robotIdentity?.localOrinIp?.ifBlank { "--" } ?: "--")
        StatusLine("ZeroTier", robotIdentity?.zerotierIp?.ifBlank { "--" } ?: "--")
        StatusLine("Domain", robotIdentity?.rosDomainId?.ifBlank { "--" } ?: "--")
        if (!robotIdentityError.isNullOrBlank()) {
            Text(robotIdentityError ?: "", color = Color(0xFFFF6666), fontSize = 11.sp)
        } else {
            val identitySourcePath = robotIdentity?.deviceInfoFile?.ifBlank { null }
                ?: robotIdentity?.identityEnvFile?.ifBlank { null }
            if (identitySourcePath != null) {
                Text(
                    "Identity source: ${robotIdentity?.identitySource ?: "unknown"} @ $identitySourcePath",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
        }

        // Save (for URL fields — dropdowns already auto-save)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            SecondaryButton("SAVE", Modifier.height(32.dp)) {
                isUserEditing = true
                settingsVm.updateSettings(
                    settings.copy(
                        networkPreset = selectedPreset,
                        useTztekOrin = useTztekOrin,
                        robotProfile = selectedRobot,
                        orinCameraUrl = orinCameraUrl,
                        orinTargetUrl = orinTargetUrl,
                        orinMediaUrl = orinMediaUrl,
                        orinTrackingUrl = orinTrackingUrl
                    )
                )
                savedMsg = "Saved"
            }
        }
        if (savedMsg.isNotBlank()) Text(savedMsg, color = Color(0xFF88FF88), fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------
// GROUP: VIDEO
// Video source, video mgmt service, video transport, security, advanced
// ---------------------------------------------------------------------------

@Composable
fun VideoSettingsGroupContent(viewModel: RecomoControlViewModel) {
    val videoMgmtSvcStatus by viewModel.videoManagementServiceStatus.collectAsState()
    val videoMgmtBusy by viewModel.videoManagementBusy.collectAsState()
    val videoMgmtError by viewModel.videoManagementError.collectAsState()
    val videoMgmtMessage by viewModel.videoManagementMessage.collectAsState()

    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.settings.collectAsState()

    var useWebRTC by remember { mutableStateOf(settings.useWebRTC) }
    var signalingUrl by remember { mutableStateOf(settings.signalingUrl) }
    var serviceControlPinEnabled by remember { mutableStateOf(settings.serviceControlPinEnabled) }
    var serviceControlPin by remember { mutableStateOf(settings.serviceControlPin) }
    var viewOnly by remember { mutableStateOf(settings.viewOnly) }
    var developerMode by remember { mutableStateOf(settings.developerModeEnabled) }
    var showPin by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        useWebRTC = settings.useWebRTC
        signalingUrl = settings.signalingUrl
        serviceControlPinEnabled = settings.serviceControlPinEnabled
        serviceControlPin = settings.serviceControlPin
        viewOnly = settings.viewOnly
        developerMode = settings.developerModeEnabled
    }

    val svcLabel = when {
        videoMgmtBusy -> "Working..."
        videoMgmtSvcStatus?.running == true -> "Running${videoMgmtSvcStatus?.pid?.let { " (pid $it)" } ?: ""}"
        videoMgmtSvcStatus != null -> "Stopped"
        else -> "--"
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFFD0D0D0),
        unfocusedLabelColor = Color(0xFF888888),
        focusedBorderColor = Color(0xFF888888),
        unfocusedBorderColor = Color(0xFF555555),
        cursorColor = Color.White
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // --- Video Source ---
        SectionHeader("Video Source")
        VideoSourcePanel()

        // --- Video Mgmt Service ---
        SectionHeader("Video Mgmt Service", topPadding = 10.dp)
        StatusLine("Service", svcLabel)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("START", Modifier.weight(1f).height(28.dp), enabled = !videoMgmtBusy && videoMgmtSvcStatus?.running != true) { viewModel.startVideoManagementService() }
            SecondaryButton("STOP", Modifier.weight(1f).height(28.dp), enabled = !videoMgmtBusy && videoMgmtSvcStatus?.running == true) { viewModel.stopVideoManagementService() }
        }
        if (!videoMgmtError.isNullOrBlank()) Text(videoMgmtError ?: "", color = Color(0xFFFF6666), fontSize = 11.sp)
        else if (!videoMgmtMessage.isNullOrBlank()) Text(videoMgmtMessage ?: "", color = Color(0xFF88FF88), fontSize = 11.sp)

        // --- Upload Destination ---
        SectionHeader("Upload Destination", topPadding = 10.dp)
        UploadPresetToggle(viewModel)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("REQUEUE ALL", Modifier.weight(1f).height(28.dp), enabled = !videoMgmtBusy) { viewModel.requeueForCurrentDestination() }
            SecondaryButton("RETRY FAILED", Modifier.weight(1f).height(28.dp), enabled = !videoMgmtBusy) { viewModel.retryFailedVideoUploads() }
        }

        // --- Video Transport ---
        SectionHeader("Video Transport", topPadding = 10.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("WebRTC", color = Color.White, fontSize = 12.sp)
                Text(
                    if (useWebRTC) "WebRTC (Low Latency)" else "WebSocket (Default)",
                    color = Color(0xFF888888), fontSize = 10.sp
                )
            }
            Switch(
                checked = useWebRTC,
                onCheckedChange = {
                    useWebRTC = it
                    settingsVm.updateSettings(settings.copy(useWebRTC = it))
                }
            )
        }
        if (useWebRTC) {
            OutlinedTextField(
                value = signalingUrl,
                onValueChange = { signalingUrl = it },
                label = { Text("Signaling Server", fontSize = 10.sp) },
                placeholder = { Text("ws://192.168.100.150:9077", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors
            )
        }

        // --- Security ---
        SectionHeader("Security", topPadding = 10.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("PIN Protection", color = Color.White, fontSize = 12.sp)
                Text(
                    if (serviceControlPinEnabled) "Enabled" else "Disabled (Default)",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = serviceControlPinEnabled,
                onCheckedChange = { serviceControlPinEnabled = it }
            )
        }
        OutlinedTextField(
            value = serviceControlPin,
            onValueChange = { serviceControlPin = it },
            label = { Text("Service Control PIN", fontSize = 10.sp) },
            placeholder = { Text("Set PIN when enabled", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = serviceControlPinEnabled,
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(
                        imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide PIN" else "Show PIN",
                        tint = Color.White
                    )
                }
            },
            colors = fieldColors
        )

        // --- Advanced ---
        SectionHeader("Advanced", topPadding = 10.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("View-only mode", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = viewOnly,
                onCheckedChange = {
                    viewOnly = it
                    settingsVm.updateSettings(settings.copy(viewOnly = it))
                }
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Developer Mode", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = developerMode,
                onCheckedChange = {
                    developerMode = it
                    settingsVm.updateSettings(settings.copy(developerModeEnabled = it))
                }
            )
        }

        // Save button for text fields (signalingUrl, serviceControlPin)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            SecondaryButton("SAVE", Modifier.height(32.dp)) {
                settingsVm.updateSettings(
                    settings.copy(
                        useWebRTC = useWebRTC,
                        signalingUrl = signalingUrl,
                        serviceControlPinEnabled = serviceControlPinEnabled,
                        serviceControlPin = serviceControlPin,
                        viewOnly = viewOnly,
                        developerModeEnabled = developerMode
                    )
                )
                savedMsg = "Saved"
            }
        }
        if (savedMsg.isNotBlank()) Text(savedMsg, color = Color(0xFF88FF88), fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------
// GROUP: CTRL extras
// Odom Source, Chassis Accel, Tracking Mode
// (ControllerSettingsPanel + StepSettingsPanel are added in SettingsOverlay)
// ---------------------------------------------------------------------------

@Composable
fun CtrlExtrasGroupContent(viewModel: RecomoControlViewModel) {
    val odomMode by viewModel.odomMode.collectAsState()
    val odomSwitchBusy by viewModel.odomSwitchBusy.collectAsState()
    val odomSwitchError by viewModel.odomSwitchError.collectAsState()
    val odomSwitchMessage by viewModel.odomSwitchMessage.collectAsState()
    val baseAccelInput by viewModel.baseAccelInput.collectAsState()
    val baseAngAccelInput by viewModel.baseAngAccelInput.collectAsState()
    var baseAccelMsg by remember { mutableStateOf("") }
    val cameraCanRecordEnabled by viewModel.cameraCanRecordEnabled.collectAsState()

    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.settings.collectAsState()
    var trackingMode by remember { mutableStateOf(settings.trackingMode) }
    LaunchedEffect(settings) { trackingMode = settings.trackingMode }

    val odomModeLabel = when (odomMode) {
        OdomMode.REAL -> "Real"
        OdomMode.FAKE -> "Fake"
        OdomMode.OFF -> "Off"
        OdomMode.CONFLICT -> "Conflict"
        OdomMode.UNKNOWN -> "--"
    }

    // Local toggle state — optimistic update on tap, synced from gateway when available
    var localCameraCanRecord by remember { mutableStateOf(cameraCanRecordEnabled) }
    LaunchedEffect(cameraCanRecordEnabled) { localCameraCanRecord = cameraCanRecordEnabled }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // --- Camera Recording (CAN) ---
        SectionHeader("Camera Recording")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (localCameraCanRecord) "Auto (via CAN)" else "Manual",
                    color = Color.White, fontSize = 13.sp
                )
                Text(
                    if (localCameraCanRecord) "Gateway triggers gimbal record start/stop"
                    else "CAN record commands suppressed — record manually on camera",
                    color = Color(0xFF888888), fontSize = 10.sp
                )
            }
            Switch(
                checked = localCameraCanRecord,
                onCheckedChange = {
                    localCameraCanRecord = it
                    viewModel.sendCameraCanRecordEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF2E7D32),
                    uncheckedThumbColor = Color(0xFFBDBDBD),
                    uncheckedTrackColor = Color(0xFF555555)
                )
            )
        }

        // --- Odometry Source ---
        SectionHeader("Odometry Source", topPadding = 10.dp)
        StatusLine("Mode", odomModeLabel)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(if (odomSwitchBusy) "..." else "USE REAL", Modifier.weight(1f).height(28.dp), enabled = !odomSwitchBusy && odomMode != OdomMode.REAL) { viewModel.setOdomMode(OdomMode.REAL) }
            SecondaryButton(if (odomSwitchBusy) "..." else "USE FAKE", Modifier.weight(1f).height(28.dp), enabled = !odomSwitchBusy && odomMode != OdomMode.FAKE) { viewModel.setOdomMode(OdomMode.FAKE) }
            SecondaryButton("REFRESH", Modifier.weight(1f).height(28.dp), enabled = !odomSwitchBusy) { viewModel.refreshOdomMode() }
        }
        if (!odomSwitchError.isNullOrBlank()) Text(odomSwitchError ?: "", color = Color(0xFFFF6666), fontSize = 11.sp)
        else if (!odomSwitchMessage.isNullOrBlank()) Text(odomSwitchMessage ?: "", color = Color(0xFF88FF88), fontSize = 11.sp)

        // --- Chassis Accel ---
        SectionHeader("Chassis Accel", topPadding = 10.dp)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = baseAccelInput,
                onValueChange = { viewModel.setBaseAccelInput(it) },
                label = { Text("m/s² (<=2.0)", fontSize = 10.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = baseAngAccelInput,
                onValueChange = { viewModel.setBaseAngAccelInput(it) },
                label = { Text("rad/s² (<=π)", fontSize = 10.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("APPLY", Modifier.weight(1f).height(28.dp)) {
                val lin = baseAccelInput.trim().toDoubleOrNull()
                val ang = baseAngAccelInput.trim().toDoubleOrNull()
                if (lin != null && ang != null) {
                    viewModel.sendBaseAccelTuning(lin.coerceIn(0.0, 2.0), ang.coerceIn(0.0, Math.PI))
                    baseAccelMsg = "Applied"
                } else baseAccelMsg = "Invalid value"
            }
            SecondaryButton("RESET", Modifier.weight(1f).height(28.dp)) {
                viewModel.sendBaseAccelTuning(0.8, 2.5)
                baseAccelMsg = "Reset"
            }
        }
        if (baseAccelMsg.isNotBlank()) Text(baseAccelMsg, color = Color(0xFF88FF88), fontSize = 11.sp)

        // --- Tracking Mode ---
        SectionHeader("Tracking Mode", topPadding = 10.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(
                TrackingMode.OFF to "Off",
                TrackingMode.LOCAL to "Local",
                TrackingMode.CAMCONTROL to "CamControl"
            ).forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = trackingMode == mode,
                        onClick = {
                            trackingMode = mode
                            settingsVm.updateSettings(settings.copy(trackingMode = mode))
                        }
                    )
                    Text(label, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Upload Preset segmented toggle
// ---------------------------------------------------------------------------

private data class PresetOption(val key: String, val label: String)

private val PRESET_OPTIONS = listOf(
    PresetOption("temp_http", "Local"),
    PresetOption("cos_testing", "COS Test"),
    PresetOption("cos_production", "COS Prod"),
)

@Composable
private fun UploadPresetToggle(viewModel: RecomoControlViewModel) {
    val currentPreset by viewModel.currentUploadPreset.collectAsState()
    val busy by viewModel.videoManagementBusy.collectAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(6.dp)),
    ) {
        PRESET_OPTIONS.forEachIndexed { index, option ->
            val selected = currentPreset == option.key
            val bgColor = if (selected) Color(0xFF3A6EA5) else Color.Transparent
            val textColor = if (selected) Color.White else Color(0xFF999999)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        bgColor,
                        when (index) {
                            0 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                            PRESET_OPTIONS.lastIndex -> RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
                    .clickable(enabled = !busy && !selected) { viewModel.setUploadPreset(option.key) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(option.label, color = textColor, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

@Composable
internal fun SectionHeader(title: String, topPadding: Dp = 0.dp) {
    Text(
        title,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = topPadding)
    )
}

@Composable
internal fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp)
        Text(value, color = Color(0xFFDDDDDD), fontSize = 12.sp)
    }
}

@Composable
internal fun TopicLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("$label: ", color = Color(0xFFBBBBBB), fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
        Text(value, color = Color(0xFFDDDDDD), fontSize = 11.sp)
    }
}

@Composable
internal fun RecordingTopicLine(label: String, pubCount: Int, hasData: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFBBBBBB), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("pubs:$pubCount", color = if (pubCount > 0) Color(0xFF88FF88) else Color(0xFFFF8888), fontSize = 11.sp)
            Text(if (hasData) "✓ data" else "✗ no data", color = if (hasData) Color(0xFF88FF88) else Color(0xFFFF8888), fontSize = 11.sp)
        }
    }
}

internal fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Connected -> "Connected"
    ConnectionState.Connecting -> "Connecting"
    ConnectionState.Disconnected -> "Disconnected"
    is ConnectionState.Error -> "Error"
}

internal fun flag(value: Boolean): String = if (value) "OK" else "--"
