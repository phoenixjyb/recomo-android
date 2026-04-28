package com.recomo.user.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.recomo.common.model.VideoSource
import com.recomo.user.R
import com.recomo.user.ui.screens.UserRobotOption
import com.recomo.user.ui.screens.system.GatewayControlCard
import com.recomo.user.ui.screens.system.GatewayControlCardUiState
import com.recomo.user.ui.screens.system.GatewayServiceCard
import com.recomo.user.ui.screens.system.GatewayServiceCardUiState
import com.recomo.user.ui.screens.system.ActuatorCard
import com.recomo.user.ui.screens.system.ActuatorCardUiState
import com.recomo.user.ui.screens.system.AutonomyCard
import com.recomo.user.ui.screens.system.AutonomyCardUiState
import com.recomo.user.ui.screens.system.CloudCard
import com.recomo.user.ui.screens.system.CloudCardUiState
import com.recomo.user.ui.screens.system.SceneCard
import com.recomo.user.ui.screens.system.SceneCardUiState
import com.recomo.user.ui.screens.system.SystemInfoCard
import com.recomo.user.ui.screens.system.SystemInfoCardUiState
import com.recomo.user.ui.screens.system.SystemLoadCard
import com.recomo.user.ui.screens.system.SystemLoadCardUiState

@Composable
fun SettingsPanel(
    state: SettingsUiState,
    availableRobots: List<UserRobotOption>,
    selectedRobot: UserRobotOption,
    systemInfoState: SystemInfoCardUiState,
    systemLoadState: SystemLoadCardUiState,
    gatewayControlState: GatewayControlCardUiState,
    gatewayServiceState: GatewayServiceCardUiState,
    actuatorCardState: ActuatorCardUiState = ActuatorCardUiState(),
    sceneCardState: SceneCardUiState = SceneCardUiState(),
    autonomyCardState: AutonomyCardUiState = AutonomyCardUiState(),
    cloudCardState: CloudCardUiState = CloudCardUiState(),
    modifier: Modifier = Modifier,
    onConnectOrReconnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    onRefreshGatewayServiceClick: () -> Unit = {},
    onStartGatewayServiceClick: () -> Unit = {},
    onStopGatewayServiceClick: () -> Unit = {},
    onSetPncAuthorityAutoClick: () -> Unit = {},
    onSetPncAuthorityLocalClick: () -> Unit = {},
    onSetPncAuthorityExternalClick: () -> Unit = {},
    onPreparePerceptionClick: () -> Unit = {},
    onDismissPerceptionClick: () -> Unit = {},
    onPrepareLocalizationClick: () -> Unit = {},
    onDismissLocalizationClick: () -> Unit = {},
    onPreparePncClick: () -> Unit = {},
    onDismissPncClick: () -> Unit = {},
    onPrepareActuatorsClick: () -> Unit = {},
    onDismissActuatorsClick: () -> Unit = {},
    onPrepareSensorsClick: () -> Unit = {},
    onDismissSensorsClick: () -> Unit = {},
    onPrepareSceneClick: () -> Unit = {},
    onDismissSceneClick: () -> Unit = {},
    onSelectRobot: (UserRobotOption) -> Unit = {},
    onUseWebRTCChange: (Boolean) -> Unit = {},
    onVideoSourceChange: (VideoSource) -> Unit = {},
    onChatServerUrlChange: (String) -> Unit = {},
    onChatDirectEnabledChange: (Boolean) -> Unit = {},
    onChatDirectBaseUrlChange: (String) -> Unit = {},
    onChatDirectAuthTokenChange: (String) -> Unit = {},
    onVoiceEngineChange: (com.recomo.common.chat.voice.VoiceEngine) -> Unit = {},
    onVoiceModelChange: (com.recomo.common.chat.voice.WhisperModel) -> Unit = {},
    onDownloadWhisperModel: () -> Unit = {},
    onSessionFolderPathChange: (String) -> Unit = {},
    onSceneViewerFolderPathChange: (String) -> Unit = {},
    onSpeedTierChange: (slow: Float, normal: Float, fast: Float) -> Unit = { _, _, _ -> },
    onCountdownDurationChange: (Int) -> Unit = {}
) {
    var pairingOpen by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderRow(
                statusLabel = state.gatewayStatusLabel,
                isConnected = state.isConnected,
                isConnecting = state.isConnecting
            )

            LanguageToggleCard()

            PairingSection(
                state = state,
                availableRobots = availableRobots,
                selectedRobot = selectedRobot,
                isExpanded = pairingOpen,
                onToggle = { pairingOpen = !pairingOpen },
                onSelectRobot = onSelectRobot,
                onConnectOrReconnectClick = onConnectOrReconnectClick,
                onDisconnectClick = onDisconnectClick
            )

            InfoBlock(
                title = stringResource(R.string.settings_robot),
                rows = listOf(
                    stringResource(R.string.settings_robot_label) to state.robotLabel,
                    stringResource(R.string.settings_robot_profile) to state.robotProfileLabel,
                    stringResource(R.string.settings_network_preset) to state.networkPresetLabel
                )
            )

            SystemInfoCard(
                state = systemInfoState,
                modifier = Modifier.fillMaxWidth()
            )

            SystemLoadCard(
                state = systemLoadState,
                modifier = Modifier.fillMaxWidth()
            )

            GatewayControlCard(
                state = gatewayControlState,
                onGatewayUrlChange = {},
                onConnectClick = onConnectOrReconnectClick,
                onDisconnectClick = onDisconnectClick,
                modifier = Modifier.fillMaxWidth()
            )

            GatewayServiceCard(
                state = gatewayServiceState,
                onRefreshClick = onRefreshGatewayServiceClick,
                onStartClick = onStartGatewayServiceClick,
                onStopClick = onStopGatewayServiceClick,
                modifier = Modifier.fillMaxWidth()
            )

            ActuatorCard(
                state = actuatorCardState,
                onPrepareClick = onPrepareActuatorsClick,
                onDismissClick = onDismissActuatorsClick,
                modifier = Modifier.fillMaxWidth()
            )

            SceneCard(
                state = sceneCardState,
                onPrepareSensorsClick = onPrepareSensorsClick,
                onDismissSensorsClick = onDismissSensorsClick,
                onPrepareLocalizationClick = onPrepareLocalizationClick,
                onDismissLocalizationClick = onDismissLocalizationClick,
                onPreparePerceptionClick = onPreparePerceptionClick,
                onDismissPerceptionClick = onDismissPerceptionClick,
                onPrepareAllClick = onPrepareSceneClick,
                onDismissAllClick = onDismissSceneClick,
                modifier = Modifier.fillMaxWidth()
            )

            AutonomyCard(
                state = autonomyCardState,
                onPreparePncClick = onPreparePncClick,
                onDismissPncClick = onDismissPncClick,
                onAutoClick = onSetPncAuthorityAutoClick,
                onLocalClick = onSetPncAuthorityLocalClick,
                onExternalClick = onSetPncAuthorityExternalClick,
                modifier = Modifier.fillMaxWidth()
            )

            CloudCard(
                state = cloudCardState,
                modifier = Modifier.fillMaxWidth()
            )

            VideoSourceCard(
                currentSource = state.videoSource,
                hdmiAvailable = state.hdmiDeviceAvailable,
                onSourceChange = onVideoSourceChange
            )

            SpeedSettingsCard(
                slowMps = state.speedSlowMps,
                normalMps = state.speedNormalMps,
                fastMps = state.speedFastMps,
                onSpeedTierChange = onSpeedTierChange
            )

            // Studio Dance countdown duration
            CountdownDurationCard(
                currentSeconds = state.countdownDurationSeconds,
                onDurationChange = onCountdownDurationChange
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_chat_server),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    DeferredTextField(
                        initial = state.chatServerUrl,
                        onCommit = onChatServerUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.settings_chat_server_hint),
                        placeholder = stringResource(R.string.settings_chat_server_placeholder)
                    )
                }
            }

            // Direct cloud transport (Option A) — experimental alternative to
            // the WS bridge. See docs/AI_CHAT_DIRECT_PLAN.md.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Direct cloud (experimental)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "启用后跳过 Termux 桥，AI 聊天直接走公网 REST",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = state.chatDirectEnabled,
                            onCheckedChange = onChatDirectEnabledChange
                        )
                    }

                    DeferredTextField(
                        initial = state.chatDirectBaseUrl,
                        onCommit = onChatDirectBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.chatDirectEnabled,
                        label = "Cloud REST base URL",
                        placeholder = "http://PLACEHOLDER:9999"
                    )

                    DeferredTextField(
                        initial = state.chatDirectAuthToken,
                        onCommit = onChatDirectAuthTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.chatDirectEnabled,
                        label = "Auth token (optional)",
                        placeholder = "Bearer token, leave blank if none"
                    )

                    Text(
                        text = "切换后需要重启 App 以生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Voice input engine selection
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Voice Input / 语音输入",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.voiceEngine == com.recomo.common.chat.voice.VoiceEngine.WHISPER)
                                    "Whisper (offline)" else "System (default)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Whisper: 更好的中文识别，完全离线，需下载 ~75 MB 模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = state.voiceEngine == com.recomo.common.chat.voice.VoiceEngine.WHISPER,
                            onCheckedChange = { checked ->
                                onVoiceEngineChange(
                                    if (checked) com.recomo.common.chat.voice.VoiceEngine.WHISPER
                                    else com.recomo.common.chat.voice.VoiceEngine.SYSTEM
                                )
                            }
                        )
                    }

                    // Model picker + download — only shown when Whisper is selected
                    if (state.voiceEngine == com.recomo.common.chat.voice.VoiceEngine.WHISPER) {
                        // Model selector chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.recomo.common.chat.voice.WhisperModel.entries.forEach { model ->
                                val selected = state.voiceModel == model
                                Surface(
                                    onClick = { onVoiceModelChange(model) },
                                    color = if (selected) Color(0xFF2962FF) else Color(0xFF252530),
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, if (selected) Color(0xFF5C8AFF) else Color(0xFF3A3A45))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = model.displayName,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = model.sizeLabel,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        val dlState = state.whisperDownloadState
                        when (dlState) {
                            is com.recomo.common.chat.voice.WhisperModelRepository.DownloadState.Idle -> {
                                OutlinedButton(onClick = onDownloadWhisperModel) {
                                    Text("Download Whisper model / 下载语音模型")
                                }
                            }
                            is com.recomo.common.chat.voice.WhisperModelRepository.DownloadState.InProgress -> {
                                Column {
                                    val pct = if (dlState.bytesTotal > 0)
                                        (dlState.bytesDone * 100 / dlState.bytesTotal).toInt() else 0
                                    Text(
                                        "Downloading… ${dlState.bytesDone / 1_000_000} / ${dlState.bytesTotal / 1_000_000} MB ($pct%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = {
                                            if (dlState.bytesTotal > 0) dlState.bytesDone.toFloat() / dlState.bytesTotal
                                            else 0f
                                        },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = Color(0xFF2962FF),
                                        trackColor = Color(0xFF333333)
                                    )
                                }
                            }
                            is com.recomo.common.chat.voice.WhisperModelRepository.DownloadState.Ready -> {
                                Text(
                                    "Model ready / 模型已就绪",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF66BB6A)
                                )
                            }
                            is com.recomo.common.chat.voice.WhisperModelRepository.DownloadState.Failed -> {
                                Column {
                                    Text(
                                        "Download failed: ${dlState.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFEF5350)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(onClick = onDownloadWhisperModel) {
                                        Text("Retry / 重试")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_session_folder_path),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = state.sessionFolderPath,
                        onValueChange = onSessionFolderPathChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_session_folder_path)) },
                        placeholder = { Text(stringResource(R.string.settings_session_folder_path_placeholder)) }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SceneViewer 场景文件夹",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "SPZ 场景文件所在的绝对路径。留空使用应用私有目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = state.sceneViewerFolderPath,
                        onValueChange = onSceneViewerFolderPathChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Scene folder path") },
                        placeholder = { Text("/sdcard/Download/RecomoScenes") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingSection(
    state: SettingsUiState,
    availableRobots: List<UserRobotOption>,
    selectedRobot: UserRobotOption,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelectRobot: (UserRobotOption) -> Unit,
    onConnectOrReconnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.settings_pairing_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_pairing_summary,
                            selectedRobot.label,
                            selectedRobot.preset.name
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                AssistChip(
                    onClick = onToggle,
                    label = {
                        Text(
                            text = if (isExpanded) {
                                stringResource(R.string.settings_pairing_hide)
                            } else {
                                stringResource(R.string.settings_pairing_show)
                            }
                        )
                    }
                )
            }

            if (isExpanded) {
                Text(
                    text = stringResource(R.string.settings_pairing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.settings_pairing_live_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.gatewayStatusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xB3FFFFFF)
                        )
                    }

                    if (state.canDisconnect) {
                        OutlinedButton(onClick = onDisconnectClick) {
                            Text(stringResource(R.string.connection_disconnect))
                        }
                    } else {
                        Button(
                            onClick = onConnectOrReconnectClick,
                            enabled = !state.isConnecting
                        ) {
                            Text(
                                if (state.canConnect) {
                                    stringResource(R.string.connection_connect)
                                } else {
                                    stringResource(R.string.connection_reconnect)
                                }
                            )
                        }
                    }
                }

                availableRobots.forEach { robot ->
                    Card(
                        onClick = { onSelectRobot(robot) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (robot == selectedRobot) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(
                            width = if (robot == selectedRobot) 1.dp else 0.5.dp,
                            color = if (robot == selectedRobot) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0x14FFFFFF)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(robot.label, style = MaterialTheme.typography.titleSmall)
                                if (robot == selectedRobot) {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(stringResource(R.string.settings_selected))
                                        }
                                    )
                                }
                            }
                            Text(
                                text = "${robot.profile.name} · ${robot.preset.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0x80FFFFFF)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    statusLabel: String,
    isConnected: Boolean,
    isConnecting: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x80FFFFFF)
            )
        }

        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    text = when {
                        isConnecting -> "Connecting"
                        isConnected -> "Connected"
                        else -> statusLabel
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        )
    }
}

@Composable
private fun InfoBlock(
    title: String,
    rows: List<Pair<String, String>>
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(color = Color(0x12FFFFFF))

            rows.forEachIndexed { index, row ->
                KeyValueRow(label = row.first, value = row.second)
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = Color(0x0FFFFFFF))
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0x80FFFFFF)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value.ifBlank { stringResource(R.string.settings_not_set) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LanguageToggleCard() {
    val currentLocale = remember {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (appLocales.isEmpty) "system" else appLocales.toLanguageTags().split(",").firstOrNull() ?: "system"
    }
    var selected by rememberSaveable { mutableStateOf(currentLocale) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "en" to "EN",
                    "zh" to "中文",
                    "system" to "Auto"
                ).forEach { (code, label) ->
                    val isSelected = selected == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF00A3FF) else Color.Transparent)
                            .clickable {
                                selected = code
                                val localeList = if (code == "system") {
                                    LocaleListCompat.getEmptyLocaleList()
                                } else {
                                    LocaleListCompat.forLanguageTags(code)
                                }
                                AppCompatDelegate.setApplicationLocales(localeList)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Color.White else Color(0x66FFFFFF),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSourceCard(
    currentSource: VideoSource,
    hdmiAvailable: Boolean,
    onSourceChange: (VideoSource) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Robot Camera",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Streams from the robot's camera. Phone Teach uses the device's own camera separately.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x88FFFFFF)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                data class SourceOption(val source: VideoSource, val label: String, val enabled: Boolean)
                val options = listOf(
                    SourceOption(VideoSource.WS_PHONE, "WS Phone", true),
                    SourceOption(VideoSource.WS_ORIN, "WS Orin", true),
                    SourceOption(VideoSource.WEBRTC_ORIN, "WebRTC", true),
                    SourceOption(VideoSource.HDMI_USB, "HDMI", true)
                )
                options.forEach { option ->
                    val isSelected = currentSource == option.source
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF00A3FF) else Color.Transparent)
                            .clickable(enabled = option.enabled) { onSourceChange(option.source) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                isSelected -> Color.White
                                !option.enabled -> Color(0x33FFFFFF)
                                else -> Color(0x66FFFFFF)
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (currentSource == VideoSource.HDMI_USB) {
                Text(
                    text = if (hdmiAvailable) "HDMI capture device detected" else "No HDMI device — plug in USB capture card",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hdmiAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }
        }
    }
}

/**
 * [OutlinedTextField] that edits locally and persists only on focus-lost.
 * Avoids the cursor-jumping bug caused by DataStore round-trip recomposition
 * during every keystroke.
 */
@Composable
private fun DeferredTextField(
    initial: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
    placeholder: String = ""
) {
    var localText by rememberSaveable(initial) { mutableStateOf(initial) }
    // Commit when the composable leaves (e.g. Settings panel closed
    // while field still focused) so the value is never silently lost.
    val currentCommit by rememberUpdatedState(onCommit)
    val currentText by rememberUpdatedState(localText)
    val currentInitial by rememberUpdatedState(initial)
    DisposableEffect(Unit) {
        onDispose {
            if (currentText != currentInitial) {
                currentCommit(currentText)
            }
        }
    }
    OutlinedTextField(
        value = localText,
        onValueChange = { localText = it },
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused && localText != initial) {
                onCommit(localText)
            }
        },
        singleLine = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (localText != initial) onCommit(localText)
        })
    )
}

// ── Speed settings card ─────────────────────────────────────────────

private const val SPEED_MIN = 0.05f
private const val SPEED_MAX = 1.50f

// Built-in defaults (match TouchControlSpeedMode enum values)
private const val DEFAULT_SLOW_MPS = 0.18f
private const val DEFAULT_NORMAL_MPS = 0.35f
private const val DEFAULT_FAST_MPS = 0.55f

@Composable
private fun SpeedSettingsCard(
    slowMps: Float,
    normalMps: Float,
    fastMps: Float,
    onSpeedTierChange: (slow: Float, normal: Float, fast: Float) -> Unit
) {
    // Use persisted value if > 0; otherwise fall back to built-in default.
    var slow by rememberSaveable(slowMps) {
        mutableStateOf(if (slowMps > 0f) slowMps else DEFAULT_SLOW_MPS)
    }
    var normal by rememberSaveable(normalMps) {
        mutableStateOf(if (normalMps > 0f) normalMps else DEFAULT_NORMAL_MPS)
    }
    var fast by rememberSaveable(fastMps) {
        mutableStateOf(if (fastMps > 0f) fastMps else DEFAULT_FAST_MPS)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Speed / 速度",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Chassis max speed per tier. Arm & gimbal scale proportionally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            SpeedTierSlider(
                label = "Slow",
                value = slow,
                defaultValue = DEFAULT_SLOW_MPS,
                onValueChange = { slow = it },
                onValueChangeFinished = { onSpeedTierChange(slow, normal, fast) }
            )
            SpeedTierSlider(
                label = "Normal",
                value = normal,
                defaultValue = DEFAULT_NORMAL_MPS,
                onValueChange = { normal = it },
                onValueChangeFinished = { onSpeedTierChange(slow, normal, fast) }
            )
            SpeedTierSlider(
                label = "Fast",
                value = fast,
                defaultValue = DEFAULT_FAST_MPS,
                onValueChange = { fast = it },
                onValueChangeFinished = { onSpeedTierChange(slow, normal, fast) }
            )

            // Reset to defaults
            OutlinedButton(
                onClick = {
                    slow = DEFAULT_SLOW_MPS
                    normal = DEFAULT_NORMAL_MPS
                    fast = DEFAULT_FAST_MPS
                    onSpeedTierChange(DEFAULT_SLOW_MPS, DEFAULT_NORMAL_MPS, DEFAULT_FAST_MPS)
                }
            ) {
                Text("Reset to defaults")
            }
        }
    }
}

@Composable
private fun SpeedTierSlider(
    label: String,
    value: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "%.2f m/s".format(value),
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != defaultValue) Color(0xFF00A3FF) else Color(0x80FFFFFF),
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = SPEED_MIN..SPEED_MAX,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CountdownDurationCard(
    currentSeconds: Int,
    onDurationChange: (Int) -> Unit
) {
    val options = listOf(3, 5, 10)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Studio Dance Countdown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Seconds before synced music+motion start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { seconds ->
                    val selected = currentSeconds == seconds
                    Surface(
                        onClick = { onDurationChange(seconds) },
                        color = if (selected) Color(0xFF0084E8) else Color(0x10FFFFFF),
                        shape = RoundedCornerShape(12.dp),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF0084E8))
                                 else BorderStroke(1.dp, Color(0x20FFFFFF))
                    ) {
                        Text(
                            text = "${seconds}s",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
