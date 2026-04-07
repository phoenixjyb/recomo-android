package com.recomo.user.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recomo.user.R
import com.recomo.user.control.UserNavState
import com.recomo.user.control.UserOperationMode
import com.recomo.user.control.UserPoiItem
import com.recomo.user.control.UserPoiSession
import com.recomo.user.ui.theme.StudioChrome

@Composable
fun NavigationPanel(
    navState: UserNavState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectPoiSession: (UserPoiSession) -> Unit,
    onSelectPoiIndex: (Int) -> Unit,
    onGo: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onFollowToggle: (Boolean) -> Unit,
    onModeChange: (UserOperationMode) -> Unit,
    onRefreshSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(StudioChrome.panel)
                .border(1.dp, StudioChrome.panelBorder)
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_panel_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = StudioChrome.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Status indicator
            NavigationStatusRow(navState)

            Spacer(modifier = Modifier.height(10.dp))

            // Operation mode selector
            ModeSelector(
                currentMode = navState.operationMode,
                onModeChange = onModeChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // POI Session picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_panel_session),
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textMuted
                )
                IconButton(onClick = onRefreshSessions, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = StudioChrome.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            PoiSessionPicker(
                sessions = navState.poiSessions,
                selected = navState.selectedPoiSession,
                onSelect = onSelectPoiSession
            )

            Spacer(modifier = Modifier.height(12.dp))

            // POI list
            Text(
                text = stringResource(R.string.nav_panel_waypoints),
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            PoiList(
                pois = navState.currentPoiList,
                selectedIndex = navState.selectedPoiIndex,
                onSelect = onSelectPoiIndex,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Speed slider
            SpeedSlider(
                speed = navState.navSpeed,
                onSpeedChange = onSpeedChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Follow toggle
            FollowToggle(
                followActive = navState.followActive,
                onToggle = onFollowToggle
            )

            Spacer(modifier = Modifier.height(12.dp))

            // GO / STOP buttons
            NavigationActions(
                navState = navState,
                onGo = onGo,
                onStop = onStop,
                onPause = onPause,
                onResume = onResume
            )
        }
    }
}

@Composable
private fun NavigationStatusRow(navState: UserNavState) {
    val statusColor = when {
        !navState.isConnected -> StudioChrome.danger
        navState.navActive -> StudioChrome.accentBlue
        navState.followActive -> StudioChrome.accentPurple
        navState.arrivedPreserved -> StudioChrome.success
        else -> StudioChrome.textMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = navState.statusLabel,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        if (navState.duplicateFsmPublishers) {
            Text(
                text = stringResource(R.string.nav_panel_multi_pub),
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.danger,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PoiSessionPicker(
    sessions: List<UserPoiSession>,
    selected: UserPoiSession?,
    onSelect: (UserPoiSession) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = StudioChrome.panelSoft,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioChrome.panelBorder)
        ) {
            Text(
                text = selected?.sessionName?.ifBlank { selected.sessionId }
                    ?: "Select session...",
                style = MaterialTheme.typography.bodySmall,
                color = if (selected != null) Color.White else StudioChrome.textMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            if (sessions.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No sessions available",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioChrome.textMuted
                        )
                    },
                    onClick = { expanded = false }
                )
            } else {
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = session.sessionName.ifBlank { session.sessionId },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${session.poiCount} waypoints",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioChrome.textMuted
                                )
                            }
                        },
                        onClick = {
                            onSelect(session)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PoiList(
    pois: List<UserPoiItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pois.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioChrome.panelSoft)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.nav_panel_select_session),
                style = MaterialTheme.typography.bodySmall,
                color = StudioChrome.textMuted
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            itemsIndexed(pois) { index, poi ->
                val isSelected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) StudioChrome.accentBlue.copy(alpha = 0.15f)
                    else Color.Transparent,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(
                        1.dp,
                        StudioChrome.accentBlue.copy(alpha = 0.4f)
                    ) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = if (isSelected) StudioChrome.accentBlue else StudioChrome.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = poi.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "x=%.1f y=%.1f".format(poi.x, poi.y),
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioChrome.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSlider(
    speed: Double,
    onSpeedChange: (Double) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_panel_speed),
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textMuted
            )
            Text(
                text = "%.1f m/s".format(speed),
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.accentBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = speed.toFloat(),
            onValueChange = { onSpeedChange(it.toDouble()) },
            valueRange = 0.1f..1.5f,
            steps = 13,
            colors = SliderDefaults.colors(
                thumbColor = StudioChrome.accentBlue,
                activeTrackColor = StudioChrome.accentBlue,
                inactiveTrackColor = StudioChrome.panelSoft
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FollowToggle(
    followActive: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.nav_panel_follow_mode),
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textMuted
            )
            Text(
                text = if (followActive) "Active" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = if (followActive) StudioChrome.accentPurple else StudioChrome.textMuted
            )
        }
        Switch(
            checked = followActive,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = StudioChrome.accentPurple,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = StudioChrome.panelSoft,
                uncheckedThumbColor = StudioChrome.textMuted
            )
        )
    }
}

@Composable
private fun ModeSelector(
    currentMode: UserOperationMode,
    onModeChange: (UserOperationMode) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.nav_panel_mode),
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val modes = listOf(
                UserOperationMode.Manual to "Manual",
                UserOperationMode.MotionReplica to "Replica",
                UserOperationMode.SubjectFollowing to "Follow",
                UserOperationMode.FixedPosition to "Fixed"
            )
            modes.forEach { (mode, label) ->
                val isSelected = currentMode == mode
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeChange(mode) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) StudioChrome.accentBlue.copy(alpha = 0.15f) else Color.Transparent,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(
                        1.dp,
                        StudioChrome.accentBlue.copy(alpha = 0.4f)
                    ) else androidx.compose.foundation.BorderStroke(1.dp, StudioChrome.panelBorder)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) StudioChrome.accentBlue else StudioChrome.textMuted,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationActions(
    navState: UserNavState,
    onGo: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (navState.navActive) {
            // Show Pause/Resume + Stop when navigating
            Button(
                onClick = if (navState.fsmState.label == "Running") onPause else onResume,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.warning.copy(alpha = 0.15f),
                    contentColor = StudioChrome.warning
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = if (navState.fsmState.label == "Running") stringResource(R.string.nav_panel_pause) else stringResource(R.string.nav_panel_resume),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.danger.copy(alpha = 0.15f),
                    contentColor = StudioChrome.danger
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.nav_panel_stop),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Show GO button
            Button(
                onClick = onGo,
                enabled = navState.canGo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.accentBlue,
                    contentColor = Color.White,
                    disabledContainerColor = StudioChrome.accentBlue.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.nav_panel_go),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
