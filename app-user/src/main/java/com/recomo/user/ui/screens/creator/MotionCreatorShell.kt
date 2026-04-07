package com.recomo.user.ui.screens.creator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recomo.user.R
import com.recomo.user.ui.screens.library.MotionDetailItem
import com.recomo.user.ui.screens.library.MotionGridCard
import com.recomo.user.ui.screens.library.MotionLibraryEntryUiState
import com.recomo.user.ui.screens.library.MotionLibrarySidebarIcon

@Composable
fun MotionCreatorScreen(
    state: MotionCreatorShellUiState,
    onModeSelected: (MotionCreatorMode) -> Unit,
    onCopyStylePresetSelected: (MotionDetailItem) -> Unit = {},
    modifier: Modifier = Modifier,
    chatContent: @Composable BoxScope.() -> Unit = {
        MotionCreatorChatPlaceholder()
    },
    copyStyleContent: @Composable BoxScope.() -> Unit = {
        CopyStyleContent(onPresetSelected = onCopyStylePresetSelected)
    },
    keypointContent: @Composable BoxScope.() -> Unit = {
        KeypointModePlaceholder()
    },
    phoneTeachContent: @Composable BoxScope.() -> Unit = {
        PhoneTeachModePlaceholder()
    }
) {
    MotionCreatorShell(
        state = state,
        onModeSelected = onModeSelected,
        modifier = modifier
    ) { mode ->
        when (mode) {
            MotionCreatorMode.Chat -> chatContent()
            MotionCreatorMode.CopyStyle -> copyStyleContent()
            MotionCreatorMode.Keypoint -> keypointContent()
            MotionCreatorMode.Phone -> phoneTeachContent()
        }
    }
}

@Composable
fun MotionCreatorShell(
    state: MotionCreatorShellUiState,
    onModeSelected: (MotionCreatorMode) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(MotionCreatorMode) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070707),
                        Color(0xFF090909),
                        Color(0xFF0D0D0D)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MotionCreatorSidebar(
                state = state,
                onModeSelected = onModeSelected,
                modifier = Modifier
                    .width(248.dp)
                    .fillMaxHeight()
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = Color(0xCC0C0C0C),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0x14FFFFFF))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    MotionCreatorHeaderBar(
                        activeMode = state.activeMode,
                        activeModeLabel = state.modes.firstOrNull { it.mode == state.activeMode }?.title ?: state.activeMode.defaultLabel(),
                        statusLabel = state.statusLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = Color(0x0FFFFFFF))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        content = { content(state.activeMode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionCreatorSidebar(
    state: MotionCreatorShellUiState,
    onModeSelected: (MotionCreatorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC0C0C0C),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF00A3FF), Color(0xFF7B5EFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MC",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xB3FFFFFF)
                )
            }

            Surface(
                color = Color(0xFF121212),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.modes.forEach { item ->
                        MotionCreatorModeItem(
                            item = item,
                            selected = item.mode == state.activeMode,
                            onClick = { onModeSelected(item.mode) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                color = Color(0xFF111111),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF66D2FF)
                    )
                    Text(
                        text = state.guidanceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionCreatorModeItem(
    item: MotionCreatorModeItemUiState,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        Brush.linearGradient(listOf(Color(0x2200A3FF), Color(0x227B5EFF)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF121212), Color(0xFF121212)))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = item.enabled, onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.8.dp,
            color = if (selected) Color(0x3300A3FF) else Color(0x10FFFFFF)
        )
    ) {
        Row(
            modifier = Modifier
                .background(background)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) {
                            Brush.linearGradient(listOf(Color(0xFF00A3FF), Color(0xFF7B5EFF)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF151515)))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.shortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    item.badge?.let { badge ->
                        Surface(
                            color = Color(0x1FFF8C42),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC48D)
                            )
                        }
                    }
                }
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Color(0xFF66D2FF) else Color(0x99FFFFFF)
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x80FFFFFF)
                )
            }
        }
    }
}

@Composable
private fun MotionCreatorHeaderBar(
    activeMode: MotionCreatorMode,
    activeModeLabel: String = activeMode.defaultLabel(),
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = activeModeLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (activeMode) {
                    MotionCreatorMode.Chat -> stringResource(R.string.creator_mode_chat_desc)
                    MotionCreatorMode.CopyStyle -> stringResource(R.string.creator_mode_copy_desc)
                    MotionCreatorMode.Keypoint -> stringResource(R.string.creator_mode_keypoint_desc)
                    MotionCreatorMode.Phone -> stringResource(R.string.creator_mode_phone_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xB3FFFFFF)
            )
        }
        Surface(
            color = Color(0x121BC47D),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, Color(0x221BC47D))
        ) {
            Text(
                text = statusLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF8AE2B7)
            )
        }
    }
}

@Composable
private fun MotionCreatorChatPlaceholder() {
    MotionCreatorModePanel(
        eyebrow = "Chat Slot",
        title = "Drop the current ChatScreen here",
        description = "This shell is intentionally built so the main thread can slot the existing chat surface into AI Chat mode without reworking the rest of Motion Creator."
    ) {
        MotionCreatorFeatureCard(
            title = "Integration contract",
            body = "Pass ChatScreen as the chatContent slot. The shell will keep the left mode rail and content frame stable while preserving the current chat flow."
        )
        MotionCreatorFeatureCard(
            title = "Expected operator path",
            body = "Prompt -> preview -> handoff to Run. Copy Style, Keypoint, and Phone Teach stay parallel in the same workspace."
        )
    }
}

@Composable
fun CopyStyleContent(
    onPresetSelected: (MotionDetailItem) -> Unit = {}
) {
    val presets = defaultCopyStylePresets()
    val entries = presets.map { it.toMotionLibraryEntry() }

    MotionCreatorModePanel(
        eyebrow = stringResource(R.string.creator_copy_eyebrow),
        title = stringResource(R.string.creator_copy_style),
        description = stringResource(R.string.creator_copy_desc)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = entries,
                key = { entry: MotionLibraryEntryUiState -> entry.id }
            ) { entry ->
                val preset = presets.first { it.id == entry.id }
                MotionGridCard(
                    state = entry,
                    onClick = { onPresetSelected(preset.toMotionDetailItem()) },
                    onRun = { onPresetSelected(preset.toMotionDetailItem()) },
                    onEdit = {},
                    onToggleFavorite = {},
                    onDelete = {}
                )
            }
        }
    }
}

private fun MotionCreatorCopyPresetUiState.toMotionLibraryEntry(): MotionLibraryEntryUiState {
    val categoryLabel = when (sceneType) {
        "LivePnC" -> "Cinematic"
        else -> "Product"
    }
    return MotionLibraryEntryUiState(
        id = id,
        title = title,
        subtitle = description,
        typeLabel = "Preset",
        groupLabel = "Copy Style",
        categoryLabel = categoryLabel,
        primaryMeta = if (duration > 0) "${duration}s" else "",
        secondaryMeta = if (keyframes > 0) "$keyframes keyframes" else "",
        tertiaryMeta = sceneType,
        thumbnailIcon = if (sceneType == "LivePnC") MotionLibrarySidebarIcon.Cinematic
                        else MotionLibrarySidebarIcon.Product,
        thumbnailRes = thumbnailRes
    )
}

fun MotionCreatorCopyPresetUiState.toMotionDetailItem(): MotionDetailItem = MotionDetailItem(
    id = trajectoryId.ifBlank { id },
    title = title,
    subtitle = description,
    keyframes = keyframes,
    duration = duration,
    sceneType = sceneType,
    linkedMap = linkedMap,
    gradientStart = accentStart,
    gradientEnd = accentEnd,
    isPreset = true,
    thumbnailRes = thumbnailRes,
    trajectoryRes = trajectoryRes,
    videoFileName = videoFileName
)

@Composable
private fun KeypointModePlaceholder() {
    MotionCreatorModePanel(
        eyebrow = "Live Capture",
        title = "Keypoint",
        description = "Manual pose capture lane for operators who need explicit control over frame order, dwell, and transition timing."
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                color = Color(0xFF101010),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0x10FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0x1A00A3FF), Color(0xFF090909))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Camera and skeleton overlay land here",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFEDEDED),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MotionCreatorFeatureCard(
                    title = "Capture controls",
                    body = "Stage base pose, arm joints, and gimbal at each key moment. The panel is shaped so a later worker can wire real joint widgets into it."
                )
                MotionCreatorFeatureCard(
                    title = "Timeline intent",
                    body = "Sequence index, dwell, and transition settings belong in this right-hand lane. It stays visible while the live view owns the left side."
                )
                MotionCreatorFeatureCard(
                    title = "Operator guidance",
                    body = "Use this mode for explicit repeatable motion when AI chat is too open-ended and Copy Style is too coarse."
                )
            }
        }
    }
}

@Composable
private fun PhoneTeachModePlaceholder() {
    MotionCreatorModePanel(
        eyebrow = "Bridge Reserved",
        title = "Phone Teach",
        description = "A reserved lane for phone-as-viewfinder teaching once the IMU bridge and Orin-side mapping are ready."
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MotionCreatorFeatureCard(
                title = "Why it stays visible",
                body = "The shell keeps Phone Teach present so the navigation model matches the prototype even before the capture bridge is ready."
            )
            MotionCreatorFeatureCard(
                title = "Expected future contract",
                body = "Phone motion, camera framing, and time alignment can later map into the same trajectory handoff flow used by Chat and local sessions."
            )
            MotionCreatorFeatureCard(
                title = "Current status",
                body = "UI-ready lane only. No fake capture controls are exposed here."
            )
        }
    }
}

@Composable
private fun MotionCreatorModePanel(
    eyebrow: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF66D2FF)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xB3FFFFFF)
                )
            }
            content()
        }
    )
}

@Composable
private fun MotionCreatorFeatureCard(
    title: String,
    body: String
) {
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF66D2FF))
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xB3FFFFFF)
            )
        }
    }
}
