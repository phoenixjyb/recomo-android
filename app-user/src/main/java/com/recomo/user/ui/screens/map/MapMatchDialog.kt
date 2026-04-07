package com.recomo.user.ui.screens.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.recomo.user.R
import kotlinx.coroutines.delay

@Composable
fun MapMatchDialog(
    state: MapMatchDialogUiState,
    onDismiss: () -> Unit,
    onSelectMap: (String) -> Unit = {},
    onPrepareLocalization: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSkipMap: () -> Unit = {},
    onRunWithMap: () -> Unit = {},
    autoSelect: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .padding(24.dp),
            color = Color(0xFF141414),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                DialogHeader(motionName = state.motionName)

                // Phase content
                AnimatedContent(
                    targetState = state.phase,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "phase"
                ) { phase ->
                    val mapName = state.maps.firstOrNull { it.id == state.selectedMapId }?.name ?: ""
                    when (phase) {
                        MapMatchPhase.Detecting -> DetectingPhase()
                        MapMatchPhase.Select -> SelectPhase(
                            maps = state.maps,
                            selectedMapId = state.selectedMapId,
                            onSelectMap = onSelectMap,
                            autoSelect = autoSelect,
                            onAutoAdvance = onPrepareLocalization
                        )
                        MapMatchPhase.Preparing -> PreparingPhase(
                            mapName = mapName,
                            elapsedMs = state.prepareElapsedMs
                        )
                        MapMatchPhase.PosePublishing -> PosePublishingPhase(
                            mapName = mapName,
                            elapsedMs = state.prepareElapsedMs
                        )
                        MapMatchPhase.Stable -> StablePhase(
                            mapName = mapName,
                            stableAgeMs = state.poseStableAgeMs
                        )
                        MapMatchPhase.TimedOut -> TimedOutPhase(
                            elapsedMs = state.prepareElapsedMs
                        )
                        MapMatchPhase.Failed -> FailedPhase(
                            errorMessage = state.errorMessage
                        )
                    }
                }

                // Footer buttons
                DialogFooter(
                    phase = state.phase,
                    hasSelection = state.selectedMapId != null,
                    skipBlocked = state.skipBlocked,
                    skipBlockedReason = state.skipBlockedReason,
                    onCancel = onDismiss,
                    onSkipMap = onSkipMap,
                    onUseThisMap = onPrepareLocalization,
                    onRetry = onRetry,
                    onRunWithMap = onRunWithMap
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(motionName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0084E8), Color(0xFF7B5EFF)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(
                text = stringResource(R.string.map_match_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            if (motionName.isNotBlank()) {
                Text(
                    text = motionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetectingPhase() {
    val infiniteTransition = rememberInfiniteTransition(label = "detect")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF0084E8),
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation)
            )
        }
        Text(
            text = stringResource(R.string.map_match_detecting),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.map_match_detecting_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SelectPhase(
    maps: List<MapMatchOption>,
    selectedMapId: String?,
    onSelectMap: (String) -> Unit,
    autoSelect: Boolean = false,
    onAutoAdvance: () -> Unit = {}
) {
    // Auto-select: if only one map or a recommended map, pick it and advance
    LaunchedEffect(maps, autoSelect) {
        if (!autoSelect || maps.isEmpty()) return@LaunchedEffect
        val bestMap = when {
            maps.size == 1 -> maps.first()
            else -> maps.firstOrNull { it.isRecommended }
        }
        if (bestMap != null && selectedMapId != bestMap.id) {
            onSelectMap(bestMap.id)
            delay(300) // brief pause so UI shows selection
            onAutoAdvance()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "${maps.size} ${stringResource(R.string.map_match_select)}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = maps,
                key = { _, map -> map.id }
            ) { _, map ->
                MapOptionRow(
                    map = map,
                    isSelected = map.id == selectedMapId,
                    onClick = { onSelectMap(map.id) }
                )
            }
        }
    }
}

@Composable
private fun MapOptionRow(
    map: MapMatchOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) Color(0xFF0084E8).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) Color(0xFF0084E8).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Map icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF0084E8) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Map info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = map.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (map.isRecommended) {
                        Surface(
                            color = Color(0xFF34D399).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.map_match_recommended),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF34D399),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Radio
            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF0084E8) else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PreparingPhase(mapName: String, elapsedMs: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "prepare")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color(0xFF0084E8),
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
        )
        Text(
            text = stringResource(R.string.map_match_preparing),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.map_match_preparing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
        if (mapName.isNotBlank()) {
            Text(
                text = mapName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
        ElapsedLabel(elapsedMs)
    }
}

@Composable
private fun PosePublishingPhase(mapName: String, elapsedMs: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "pose_pub")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = null,
            tint = Color(0xFFFBBF24),
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
        )
        Text(
            text = stringResource(R.string.map_match_pose_publishing),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.map_match_pose_publishing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
        if (mapName.isNotBlank()) {
            Text(
                text = mapName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
        ElapsedLabel(elapsedMs)
    }
}

@Composable
private fun StablePhase(mapName: String, stableAgeMs: Long) {
    val animScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = 200f, dampingRatio = 0.6f),
        label = "ready_scale"
    )
    val stableSec = (stableAgeMs / 1000).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF34D399),
            modifier = Modifier
                .size(48.dp)
                .scale(animScale)
        )
        Text(
            text = stringResource(R.string.map_match_stable),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.map_match_stable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
        if (mapName.isNotBlank()) {
            Text(
                text = mapName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
        Text(
            text = stringResource(R.string.map_match_stable_age, stableSec),
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF34D399),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimedOutPhase(elapsedMs: Long) {
    val elapsedSec = (elapsedMs / 1000).toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFFF87171),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = stringResource(R.string.map_match_timed_out),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.map_match_timed_out_body, elapsedSec),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun FailedPhase(errorMessage: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color(0xFFF87171),
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = stringResource(R.string.map_match_failed),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF87171).copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ElapsedLabel(elapsedMs: Long) {
    val sec = (elapsedMs / 1000).toInt()
    if (sec > 0) {
        Text(
            text = "${sec}s",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun DialogFooter(
    phase: MapMatchPhase,
    hasSelection: Boolean,
    skipBlocked: Boolean,
    skipBlockedReason: String?,
    onCancel: () -> Unit,
    onSkipMap: () -> Unit,
    onUseThisMap: () -> Unit,
    onRetry: () -> Unit,
    onRunWithMap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Skip blocked reason (shown above buttons when skip is disabled)
        if (skipBlocked && !skipBlockedReason.isNullOrBlank()) {
            Text(
                text = skipBlockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFBBF24).copy(alpha = 0.7f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip link — disabled for LivePnC
            TextButton(
                onClick = onSkipMap,
                enabled = !skipBlocked
            ) {
                Text(
                    text = stringResource(R.string.map_match_skip),
                    color = Color.White.copy(alpha = if (skipBlocked) 0.15f else 0.4f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Cancel
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Phase-dependent action button
                when (phase) {
                    MapMatchPhase.Detecting -> {}
                    MapMatchPhase.Select -> {
                        Button(
                            onClick = onUseThisMap,
                            enabled = hasSelection,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0084E8),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF0084E8).copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.map_match_use_this),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    MapMatchPhase.Preparing, MapMatchPhase.PosePublishing -> {
                        // No action button while waiting — cancel is sufficient
                    }
                    MapMatchPhase.Stable -> {
                        Button(
                            onClick = onRunWithMap,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF34D399),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.map_match_run_with_map),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    MapMatchPhase.TimedOut, MapMatchPhase.Failed -> {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0084E8),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_retry),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
