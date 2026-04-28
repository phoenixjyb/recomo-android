package com.recomo.user.ui.screens.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recomo.common.model.RobotProfile
import com.recomo.user.R
import com.recomo.user.control.UserConnectionStatus
import com.recomo.user.ui.screens.UserMainViewModel
import com.recomo.user.ui.screens.UserRobotOption

internal fun String.toStudioLabel(): String =
    lowercase().split('_').joinToString(" ") { part ->
        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

private data class ConnectionStepUiState(
    val index: Int,
    val title: Int,
    val detail: Int,
    val complete: Boolean,
    val active: Boolean
)

@Composable
fun ConnectionScreen(
    shellViewModel: UserMainViewModel,
    connectionStatus: UserConnectionStatus
) {
    val robots by shellViewModel.availableRobots.collectAsState()
    val selectedRobot by shellViewModel.selectedRobot.collectAsState()
    val customGatewayUrl by shellViewModel.customGatewayUrl.collectAsState()
    val stepStates = remember(robots, selectedRobot, connectionStatus) {
        listOf(
            ConnectionStepUiState(
                index = 1,
                title = R.string.connection_step_network,
                detail = R.string.connection_step_network_desc,
                complete = robots.isNotEmpty(),
                active = robots.isEmpty()
            ),
            ConnectionStepUiState(
                index = 2,
                title = R.string.connection_step_device,
                detail = R.string.connection_step_device_desc,
                complete = robots.contains(selectedRobot),
                active = connectionStatus == UserConnectionStatus.Disconnected
            ),
            ConnectionStepUiState(
                index = 3,
                title = R.string.connection_step_auth,
                detail = R.string.connection_step_auth_desc,
                complete = connectionStatus == UserConnectionStatus.Connected,
                active = connectionStatus == UserConnectionStatus.Connecting
            ),
            ConnectionStepUiState(
                index = 4,
                title = R.string.connection_step_ready,
                detail = R.string.connection_step_ready_desc,
                complete = connectionStatus == UserConnectionStatus.Connected,
                active = connectionStatus == UserConnectionStatus.Connected
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
        val stacked = maxWidth < 960.dp
        val leftPanelModifier = if (stacked) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        } else {
            Modifier
                .width(340.dp)
                .fillMaxHeight()
        }
        val rightPanelModifier = if (stacked) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        } else {
            Modifier
                .fillMaxHeight()
        }

        val leftPanel: @Composable () -> Unit = {
            Surface(
                modifier = leftPanelModifier,
                color = Color(0xCC0C0C0C),
                border = BorderStroke(1.dp, Color(0x14FFFFFF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0x2200A3FF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    color = Color(0x1400A3FF),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "R",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.connection_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.connection_brand_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.28f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = stringResource(R.string.connection_steps),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.28f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    stepStates.forEachIndexed { index, step ->
                        ConnectionStepItem(
                            state = step,
                            showConnector = index < stepStates.lastIndex
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${selectedRobot.label} · ${selectedRobot.preset.name.toStudioLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.24f)
                    )
                }
            }
        }

        val rightPanel: @Composable () -> Unit = {
            Surface(
                modifier = rightPanelModifier,
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (stacked) 0.dp else 40.dp, vertical = if (stacked) 0.dp else 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (connectionStatus) {
                        UserConnectionStatus.Connecting -> {
                            Surface(
                                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                                color = Color(0x660C0C0C),
                                border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 36.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00A3FF),
                                        trackColor = Color.White.copy(alpha = 0.06f)
                                    )
                                    Text(
                                        text = stringResource(R.string.connection_connecting_to, selectedRobot.label),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = stringResource(R.string.connection_establishing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.40f),
                                        textAlign = TextAlign.Center
                                    )
                                    Surface(
                                        color = Color(0x10FFFFFF),
                                        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.connection_selected_robot),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.26f)
                                            )
                                            Text(
                                                text = selectedRobot.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = selectedRobot.preset.name.toStudioLabel(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.40f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        UserConnectionStatus.Connected -> {
                            Surface(
                                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                                color = Color(0x660C0C0C),
                                border = BorderStroke(1.dp, Color(0x181BC47D)),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 36.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(Color(0x141BC47D), CircleShape)
                                            .border(1.dp, Color(0x281BC47D), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "OK",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF1BC47D),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.connection_status_connected),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(R.string.connection_opening),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.40f)
                                    )
                                }
                            }
                        }

                        UserConnectionStatus.Disconnected -> {
                            Surface(
                                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                                color = Color.Transparent
                            ) {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = stringResource(R.string.connection_available),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = stringResource(R.string.connection_found, robots.size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.36f)
                                        )
                                    }

                                    robots.forEach { robot ->
                                        Card(
                                            onClick = { shellViewModel.selectRobot(robot) },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (robot == selectedRobot) Color(0x1200A3FF) else Color(0x080FFFFFF)
                                            ),
                                            border = BorderStroke(
                                                width = if (robot == selectedRobot) 1.dp else 0.6.dp,
                                                color = if (robot == selectedRobot) Color(0x3300A3FF) else Color(0x14FFFFFF)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = robot.label,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White
                                                )
                                                val robotSubtitle = if (robot.profile == RobotProfile.CUSTOM) {
                                                    customGatewayUrl.ifBlank { "ws://IP:9077" }
                                                } else {
                                                    robot.preset.name.toStudioLabel()
                                                }
                                                Text(
                                                    text = robotSubtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.36f)
                                                )
                                            }
                                        }
                                    }

                                    // Hoist local URL state so Connect button can
                                    // flush it to DataStore before connecting.
                                    var localUrl by remember { mutableStateOf(customGatewayUrl) }
                                    LaunchedEffect(customGatewayUrl) { localUrl = customGatewayUrl }

                                    if (selectedRobot.profile == RobotProfile.CUSTOM) {
                                        OutlinedTextField(
                                            value = localUrl,
                                            onValueChange = { localUrl = it },
                                            label = {
                                                Text(
                                                    text = "Gateway URL",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            placeholder = {
                                                Text(
                                                    text = "ws://192.168.x.x:9077",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.28f)
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { if (!it.isFocused) shellViewModel.updateCustomGatewayUrl(localUrl) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00A3FF),
                                                unfocusedBorderColor = Color(0x33FFFFFF),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = Color(0xFF00A3FF),
                                                focusedLabelColor = Color(0xFF00A3FF),
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.40f)
                                            )
                                        )
                                    }

                                    Surface(
                                        color = Color(0x080FFFFFF),
                                        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.connection_selected_robot),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.26f)
                                            )
                                            Text(
                                                text = selectedRobot.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            if (selectedRobot.profile == RobotProfile.CUSTOM) {
                                                Text(
                                                    text = customGatewayUrl.ifBlank { "ws://IP:9077" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.40f)
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val pending = if (selectedRobot.profile == RobotProfile.CUSTOM) localUrl else null
                                            shellViewModel.connect(pending)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00A3FF),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(stringResource(R.string.connection_connect))
                                    }

                                    TextButton(
                                        onClick = { shellViewModel.enterDemoMode() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.connection_demo_mode),
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (stacked) {
            Column(modifier = Modifier.fillMaxSize()) {
                leftPanel()
                Box(modifier = Modifier.weight(1f)) {
                    rightPanel()
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                leftPanel()
                Box(modifier = Modifier.weight(1f)) {
                    rightPanel()
                }
            }
        }
    }
}

@Composable
private fun ConnectionStepItem(
    state: ConnectionStepUiState,
    showConnector: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = when {
                            state.complete -> Color(0xFF0084E8)
                            state.active -> Color.Transparent
                            else -> Color.Transparent
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = if (state.complete) 0.dp else 1.5.dp,
                        color = when {
                            state.active -> Color(0xFF0084E8)
                            else -> Color.White.copy(alpha = 0.10f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        state.complete -> Color.White
                        state.active -> Color(0xFF00A3FF)
                        else -> Color.White.copy(alpha = 0.22f)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(if (state.complete) Color(0x400084E8) else Color.White.copy(alpha = 0.05f))
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(state.title),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    state.complete || state.active -> Color.White
                    else -> Color.White.copy(alpha = 0.26f)
                },
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(state.detail),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.24f)
            )
        }
    }
}
