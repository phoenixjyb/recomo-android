package com.recomo.user.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.recomo.user.R

@Composable
fun MotionLibraryWorkspace(
    state: MotionLibraryWorkspaceUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onCreateMotion: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onCategorySelected: (MotionLibrarySidebarCategoryUiState) -> Unit = {},
    onViewModeChange: (MotionLibraryViewMode) -> Unit = {},
    onEntryClick: (MotionLibraryEntryUiState) -> Unit = {},
    onRunEntry: (MotionLibraryEntryUiState) -> Unit = {},
    onEditEntry: (MotionLibraryEntryUiState) -> Unit = {},
    onToggleFavorite: (MotionLibraryEntryUiState) -> Unit = {},
    onDeleteEntry: (MotionLibraryEntryUiState) -> Unit = {},
    onSidebarActionClick: (MotionLibrarySidebarActionUiState) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
        MotionLibrarySidebar(
            state = state,
            onBack = onBack,
            onCreateMotion = onCreateMotion,
            onCategorySelected = onCategorySelected,
            onActionClick = onSidebarActionClick
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            MotionLibraryTopBar(
                state = state,
                onSearchQueryChange = onSearchQueryChange,
                onViewModeChange = onViewModeChange
            )
            MotionLibraryContent(
                state = state,
                onEntryClick = onEntryClick,
                onRunEntry = onRunEntry,
                onEditEntry = onEditEntry,
                onToggleFavorite = onToggleFavorite,
                onDeleteEntry = onDeleteEntry
            )
        }
    }
}

@Composable
private fun MotionLibrarySidebar(
    state: MotionLibraryWorkspaceUiState,
    onBack: () -> Unit,
    onCreateMotion: () -> Unit,
    onCategorySelected: (MotionLibrarySidebarCategoryUiState) -> Unit,
    onActionClick: (MotionLibrarySidebarActionUiState) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(248.dp)
            .fillMaxHeight(),
        color = Color(0xCC0C0C0C),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f)
                    )
                }
                Column {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.28f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onCreateMotion,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A3FF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = state.createLabel, fontWeight = FontWeight.SemiBold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.categoriesTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.18f),
                    fontWeight = FontWeight.Medium
                )
                state.categories.forEach { category ->
                    SidebarCategoryRow(
                        state = category,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }

            if (state.sidebarActions.isNotEmpty()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sidebarActions.forEach { action ->
                        SidebarActionRow(
                            state = action,
                            onClick = { onActionClick(action) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.storageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.28f)
                    )
                    Text(
                        text = state.storageUsageLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(state.storageFraction)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF00A3FF), Color(0xFF7B5EFF))
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionLibraryTopBar(
    state: MotionLibraryWorkspaceUiState,
    onSearchQueryChange: (String) -> Unit,
    onViewModeChange: (MotionLibraryViewMode) -> Unit
) {
    Surface(
        color = Color(0xCC0C0C0C),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.width(360.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.24f)
                    )
                },
                label = {
                    Text(
                        text = state.searchPlaceholder,
                        color = Color.White.copy(alpha = 0.36f)
                    )
                },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = state.resultCountLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.32f)
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ViewToggleChip(
                    selected = state.viewMode == MotionLibraryViewMode.Grid,
                    icon = Icons.Default.GridView,
                    onClick = { onViewModeChange(MotionLibraryViewMode.Grid) }
                )
                ViewToggleChip(
                    selected = state.viewMode == MotionLibraryViewMode.List,
                    icon = Icons.AutoMirrored.Filled.ViewList,
                    onClick = { onViewModeChange(MotionLibraryViewMode.List) }
                )
            }
        }
    }
}

@Composable
private fun MotionLibraryContent(
    state: MotionLibraryWorkspaceUiState,
    onEntryClick: (MotionLibraryEntryUiState) -> Unit,
    onRunEntry: (MotionLibraryEntryUiState) -> Unit,
    onEditEntry: (MotionLibraryEntryUiState) -> Unit,
    onToggleFavorite: (MotionLibraryEntryUiState) -> Unit,
    onDeleteEntry: (MotionLibraryEntryUiState) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(24.dp)
    ) {
        if (!state.hasContent) {
            EmptyLibraryState(
                title = state.emptyTitle,
                body = state.emptyBody
            )
            return
        }

        when (state.viewMode) {
            MotionLibraryViewMode.Grid -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        MotionGridCard(
                            state = entry,
                            onClick = { onEntryClick(entry) },
                            onRun = { onRunEntry(entry) },
                            onEdit = { onEditEntry(entry) },
                            onToggleFavorite = { onToggleFavorite(entry) },
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
            }

            MotionLibraryViewMode.List -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        MotionListRow(
                            state = entry,
                            onClick = { onEntryClick(entry) },
                            onRun = { onRunEntry(entry) },
                            onEdit = { onEditEntry(entry) },
                            onToggleFavorite = { onToggleFavorite(entry) },
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MotionGridCard(
    state: MotionLibraryEntryUiState,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.025f),
        border = BorderStroke(
            width = if (state.isSelected) 1.dp else 0.75.dp,
            color = if (state.isSelected) Color(0x6600A3FF) else Color.White.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(entryThumbnailBrush(state.thumbnailIcon))
            ) {
                if (state.thumbnailRes != null) {
                    Image(
                        painter = painterResource(state.thumbnailRes),
                        contentDescription = state.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.28f))
                ) {
                    Icon(
                        imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (state.isFavorite) Color(0xFFF4C84A) else Color.White.copy(alpha = 0.72f)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    if (state.thumbnailRes == null) {
                        Icon(
                            imageVector = sidebarIcon(state.thumbnailIcon),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.82f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = state.typeLabel.ifBlank { state.categoryLabel },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.subtitle.isNotBlank()) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.32f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataPill(state.primaryMeta)
                if (state.secondaryMeta.isNotBlank()) MetadataPill(state.secondaryMeta)
            }

            if (state.tertiaryMeta.isNotBlank()) {
                Text(
                    text = state.tertiaryMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.22f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRun,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0084E8),
                        contentColor = Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.library_ws_run), maxLines = 1)
                }
                Surface(
                    modifier = Modifier.clickable(onClick = onEdit),
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Surface(
                    modifier = Modifier.clickable(onClick = onDelete),
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionListRow(
    state: MotionLibraryEntryUiState,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.025f),
        border = BorderStroke(
            width = if (state.isSelected) 1.dp else 0.75.dp,
            color = if (state.isSelected) Color(0x6600A3FF) else Color.White.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 104.dp, height = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(entryThumbnailBrush(state.thumbnailIcon)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = sidebarIcon(state.thumbnailIcon),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.84f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(state.primaryMeta, state.secondaryMeta, state.timestampLabel)
                        .filter { it.isNotBlank() }
                        .joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.28f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (state.isFavorite) Color(0xFFF4C84A) else Color.White.copy(alpha = 0.28f)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.42f)
                )
            }
            IconButton(onClick = onRun) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF66D2FF)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.34f)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.03f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.56f),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.24f)
        )
    }
}

@Composable
private fun SidebarCategoryRow(
    state: MotionLibrarySidebarCategoryUiState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (state.isSelected) Color(0x1400A3FF) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = sidebarIcon(state.icon),
            contentDescription = null,
            tint = if (state.isSelected) Color(0xFF66D2FF) else Color.White.copy(alpha = 0.28f)
        )
        Text(
            text = state.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isSelected) Color(0xFFB8E9FF) else Color.White.copy(alpha = 0.48f),
            fontWeight = if (state.isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
        Text(
            text = state.count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.28f)
        )
    }
}

@Composable
private fun SidebarActionRow(
    state: MotionLibrarySidebarActionUiState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = sidebarIcon(state.icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.54f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.56f),
                fontWeight = FontWeight.Medium
            )
            if (state.supportingLabel.isNotBlank()) {
                Text(
                    text = state.supportingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.22f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.18f)
        )
    }
}

@Composable
private fun ViewToggleChip(
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.24f)
        )
    }
}

@Composable
internal fun MetadataPill(text: String) {
    if (text.isBlank()) return
    Surface(
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.46f)
        )
    }
}

internal fun entryThumbnailBrush(icon: MotionLibrarySidebarIcon): Brush =
    when (icon) {
        MotionLibrarySidebarIcon.Product ->
            Brush.linearGradient(listOf(Color(0xCC0084E8), Color(0xCC6C5CE7)))
        MotionLibrarySidebarIcon.Lifestyle ->
            Brush.linearGradient(listOf(Color(0xCCEF6C35), Color(0xCCE91E63)))
        MotionLibrarySidebarIcon.Interview ->
            Brush.linearGradient(listOf(Color(0xCC00BFA5), Color(0xCC0084E8)))
        MotionLibrarySidebarIcon.Cinematic ->
            Brush.linearGradient(listOf(Color(0xCC7B5EFF), Color(0xCC2C3E94)))
        MotionLibrarySidebarIcon.Tour ->
            Brush.linearGradient(listOf(Color(0xCC26A69A), Color(0xCC1E88E5)))
        else ->
            Brush.linearGradient(listOf(Color(0xCC2A2A2A), Color(0xCC121212)))
    }

internal fun sidebarIcon(icon: MotionLibrarySidebarIcon): ImageVector =
    when (icon) {
        MotionLibrarySidebarIcon.All -> Icons.Default.Collections
        MotionLibrarySidebarIcon.Favorite -> Icons.Default.Favorite
        MotionLibrarySidebarIcon.Product -> Icons.Default.CameraAlt
        MotionLibrarySidebarIcon.Lifestyle -> Icons.Default.Movie
        MotionLibrarySidebarIcon.Interview -> Icons.Default.Videocam
        MotionLibrarySidebarIcon.Cinematic -> Icons.Default.FolderOpen
        MotionLibrarySidebarIcon.Tour -> Icons.Default.Apps
        MotionLibrarySidebarIcon.Layers -> Icons.Default.Layers
        MotionLibrarySidebarIcon.Storage -> Icons.Default.Storage
        MotionLibrarySidebarIcon.Download -> Icons.Default.Download
    }
