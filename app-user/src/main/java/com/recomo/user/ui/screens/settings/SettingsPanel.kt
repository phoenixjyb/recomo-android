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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
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
    onChatServerUrlChange: (String) -> Unit = {},
    onSessionFolderPathChange: (String) -> Unit = {}
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

                    OutlinedTextField(
                        value = state.chatServerUrl,
                        onValueChange = onChatServerUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_chat_server_hint)) },
                        placeholder = { Text(stringResource(R.string.settings_chat_server_placeholder)) }
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
