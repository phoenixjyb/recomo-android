package com.recomo.user.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.recomo.user.R
import com.recomo.user.control.UserMediaGalleryFilter
import com.recomo.user.control.UserMediaGallerySort
import com.recomo.user.control.UserMediaGalleryUiState
import com.recomo.user.control.UserMediaGalleryViewMode
import com.recomo.user.data.media.UserMediaItem
import com.recomo.user.data.media.UserMediaType
import com.recomo.user.ui.theme.StudioChrome
import com.recomo.user.ui.theme.StudioMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GalleryBackground = StudioChrome.background
private val GalleryPanel = StudioChrome.panel
private val GalleryPanelBorder = StudioChrome.panelBorder
private val GallerySoftPanel = StudioChrome.panelSoft
private val GalleryBrand = StudioChrome.accentBlue
private val GallerySecondary = StudioChrome.accentPurple
private val GallerySuccess = StudioChrome.success

@Composable
fun MediaGalleryWorkspace(
    state: UserMediaGalleryUiState,
    selectedItem: UserMediaItem?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSelectItem: (UserMediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (UserMediaGalleryFilter) -> Unit,
    onSortChange: (UserMediaGallerySort) -> Unit,
    onViewModeChange: (UserMediaGalleryViewMode) -> Unit,
    onRefresh: () -> Unit,
    onDownload: (UserMediaItem) -> Unit,
    onDeleteLocal: (UserMediaItem) -> Unit,
    getLocalFile: (UserMediaItem) -> java.io.File? = { null }
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioChrome.screenBackgroundBrush)
    ) {
        val stacked = maxWidth < 1080.dp

        if (stacked) {
            Column(modifier = Modifier.fillMaxSize()) {
                GallerySidebar(
                    state = state,
                    stacked = true,
                    onBack = onBack,
                    onSearchQueryChange = onSearchQueryChange,
                    onFilterChange = onFilterChange
                )
                GalleryContent(
                    state = state,
                    selectedItem = selectedItem,
                    stacked = true,
                    modifier = Modifier.weight(1f),
                    onSelectItem = onSelectItem,
                    onClearSelection = onClearSelection,
                    onSortChange = onSortChange,
                    onViewModeChange = onViewModeChange,
                    onRefresh = onRefresh,
                    onDownload = onDownload,
                    onDeleteLocal = onDeleteLocal,
                    getLocalFile = getLocalFile
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                GallerySidebar(
                    state = state,
                    stacked = false,
                    onBack = onBack,
                    onSearchQueryChange = onSearchQueryChange,
                    onFilterChange = onFilterChange
                )
                GalleryContent(
                    state = state,
                    selectedItem = selectedItem,
                    stacked = false,
                    modifier = Modifier.weight(1f),
                    onSelectItem = onSelectItem,
                    onClearSelection = onClearSelection,
                    onSortChange = onSortChange,
                    onViewModeChange = onViewModeChange,
                    onRefresh = onRefresh,
                    onDownload = onDownload,
                    onDeleteLocal = onDeleteLocal,
                    getLocalFile = getLocalFile
                )
            }
        }
    }
}

@Composable
private fun GallerySidebar(
    state: UserMediaGalleryUiState,
    stacked: Boolean,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (UserMediaGalleryFilter) -> Unit
) {
    val modifier = if (stacked) {
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    } else {
        Modifier
            .width(230.dp)
            .fillMaxHeight()
    }
    Surface(
        modifier = modifier,
        color = GalleryPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, GalleryPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White.copy(alpha = 0.56f)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.route_media_gallery),
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioChrome.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.gallery_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioChrome.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = StudioChrome.textMuted
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = StudioChrome.textPrimary),
                placeholder = {
                    Text(
                        text = stringResource(R.string.gallery_search_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioChrome.textMuted
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            listOf(
                Triple(UserMediaGalleryFilter.All, Icons.Outlined.Folder, stringResource(R.string.gallery_filter_all)),
                Triple(UserMediaGalleryFilter.Videos, Icons.Outlined.Movie, stringResource(R.string.gallery_filter_videos)),
                Triple(UserMediaGalleryFilter.Images, Icons.Outlined.Image, stringResource(R.string.gallery_filter_images)),
                Triple(UserMediaGalleryFilter.Downloaded, Icons.Outlined.Download, stringResource(R.string.gallery_filter_downloaded))
            ).forEach { (filter, icon, label) ->
                val active = state.filter == filter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) GalleryBrand.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) GalleryBrand else Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) GalleryBrand else Color.White.copy(alpha = 0.46f),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = filterCount(filter, state).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                        color = Color.White.copy(alpha = 0.18f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                color = GallerySoftPanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, GalleryPanelBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gallery_storage_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.22f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        val ratio = if (state.totalBytes <= 0L) 0f else (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(GalleryBrand, GallerySecondary)))
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.gallery_storage_summary,
                            formatBytes(state.downloadedBytes),
                            formatBytes(state.totalBytes)
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                        color = Color.White.copy(alpha = 0.28f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryContent(
    state: UserMediaGalleryUiState,
    selectedItem: UserMediaItem?,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    onSelectItem: (UserMediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onSortChange: (UserMediaGallerySort) -> Unit,
    onViewModeChange: (UserMediaGalleryViewMode) -> Unit,
    onRefresh: () -> Unit,
    onDownload: (UserMediaItem) -> Unit,
    onDeleteLocal: (UserMediaItem) -> Unit,
    getLocalFile: (UserMediaItem) -> java.io.File? = { null }
) {
    if (stacked) {
        Column(modifier = modifier.fillMaxSize()) {
            GalleryMainPane(
                state = state,
                selectedItem = selectedItem,
                modifier = Modifier.weight(1f),
                onSelectItem = onSelectItem,
                onSortChange = onSortChange,
                onViewModeChange = onViewModeChange,
                onRefresh = onRefresh
            )
            selectedItem?.let {
                val isDownloaded = state.downloadedIds.contains(it.id)
                GalleryDetailPane(
                    item = it,
                    isDownloaded = isDownloaded,
                    progress = state.downloadProgress[it.id],
                    localFile = if (isDownloaded) getLocalFile(it) else null,
                    stacked = true,
                    onClose = onClearSelection,
                    onDownload = { onDownload(it) },
                    onDeleteLocal = { onDeleteLocal(it) }
                )
            }
        }
    } else {
        Row(modifier = modifier.fillMaxSize()) {
            GalleryMainPane(
                state = state,
                selectedItem = selectedItem,
                modifier = Modifier.weight(1f),
                onSelectItem = onSelectItem,
                onSortChange = onSortChange,
                onViewModeChange = onViewModeChange,
                onRefresh = onRefresh
            )
            selectedItem?.let {
                val isDownloaded = state.downloadedIds.contains(it.id)
                GalleryDetailPane(
                    item = it,
                    isDownloaded = isDownloaded,
                    progress = state.downloadProgress[it.id],
                    localFile = if (isDownloaded) getLocalFile(it) else null,
                    stacked = false,
                    onClose = onClearSelection,
                    onDownload = { onDownload(it) },
                    onDeleteLocal = { onDeleteLocal(it) }
                )
            }
        }
    }
}

@Composable
private fun GalleryMainPane(
    state: UserMediaGalleryUiState,
    selectedItem: UserMediaItem?,
    modifier: Modifier = Modifier,
    onSelectItem: (UserMediaItem) -> Unit,
    onSortChange: (UserMediaGallerySort) -> Unit,
    onViewModeChange: (UserMediaGalleryViewMode) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GalleryPanel)
                .border(1.dp, GalleryPanelBorder)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.gallery_items_count, state.items.size),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                color = Color.White.copy(alpha = 0.28f)
            )
            Spacer(modifier = Modifier.weight(1f))
            SortSegment(
                active = state.sort,
                onSortChange = onSortChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            ViewModeSegment(
                active = state.viewMode,
                onViewModeChange = onViewModeChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.gallery_refresh),
                    tint = Color.White.copy(alpha = 0.34f)
                )
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GalleryBrand)
                }
            }

            state.errorMessage != null && state.items.isEmpty() -> {
                EmptyGalleryState(
                    title = stringResource(R.string.gallery_error_title),
                    body = state.errorMessage
                )
            }

            state.items.isEmpty() -> {
                EmptyGalleryState(
                    title = stringResource(R.string.gallery_empty_title),
                    body = stringResource(R.string.gallery_empty_body)
                )
            }

            state.viewMode == UserMediaGalleryViewMode.Grid -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        MediaGridCard(
                            item = item,
                            selected = selectedItem?.id == item.id,
                            isDownloaded = state.downloadedIds.contains(item.id),
                            progress = state.downloadProgress[item.id],
                            onClick = { onSelectItem(item) }
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        MediaListRow(
                            item = item,
                            selected = selectedItem?.id == item.id,
                            isDownloaded = state.downloadedIds.contains(item.id),
                            progress = state.downloadProgress[item.id],
                            onClick = { onSelectItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridCard(
    item: UserMediaItem,
    selected: Boolean,
    isDownloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) GalleryBrand.copy(alpha = 0.06f) else GallerySoftPanel
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.dp else 0.8.dp,
            color = if (selected) GalleryBrand.copy(alpha = 0.24f) else GalleryPanelBorder
        ),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MediaThumbnail(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                isDownloaded = isDownloaded,
                progress = progress
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayMediaName(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = displayMediaMeta(item),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                    color = Color.White.copy(alpha = 0.18f)
                )
                Text(
                    text = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                    color = Color.White.copy(alpha = 0.12f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MediaListRow(
    item: UserMediaItem,
    selected: Boolean,
    isDownloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GalleryBrand.copy(alpha = 0.06f) else GallerySoftPanel)
            .border(
                1.dp,
                if (selected) GalleryBrand.copy(alpha = 0.24f) else GalleryPanelBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaThumbnail(
            item = item,
            modifier = Modifier
                .width(116.dp)
                .aspectRatio(16f / 9f),
            isDownloaded = isDownloaded,
            progress = progress
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = displayMediaName(item),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = displayMediaMeta(item),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                color = Color.White.copy(alpha = 0.18f)
            )
        }
        Text(
            text = formatTimestamp(item.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
            color = Color.White.copy(alpha = 0.18f)
        )
    }
}

@Composable
private fun MediaThumbnail(
    item: UserMediaItem,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean,
    progress: Float?
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        GalleryBrand.copy(alpha = 0.18f),
                        GallerySecondary.copy(alpha = 0.18f)
                    )
                )
            )
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.filename,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (item.type == UserMediaType.VIDEO) Icons.Outlined.Movie else Icons.Outlined.HideImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f))))
        )

        if (isDownloaded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = GallerySuccess.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GallerySuccess.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = GallerySuccess,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.gallery_downloaded_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = GallerySuccess
                    )
                }
            }
        } else if (progress != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GalleryPanelBorder)
            ) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                    color = Color.White.copy(alpha = 0.68f)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.resolution?.let { "${it.width}×${it.height}" } ?: "—",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                color = Color.White.copy(alpha = 0.46f)
            )
            item.fps?.let {
                Text(
                    text = "${it}fps",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
                    color = Color.White.copy(alpha = 0.46f)
                )
            }
        }
    }
}

@Composable
private fun SortSegment(
    active: UserMediaGallerySort,
    onSortChange: (UserMediaGallerySort) -> Unit
) {
    SegmentShell {
        listOf(
            UserMediaGallerySort.Date to stringResource(R.string.gallery_sort_date),
            UserMediaGallerySort.Size to stringResource(R.string.gallery_sort_size),
            UserMediaGallerySort.Name to stringResource(R.string.gallery_sort_name)
        ).forEach { (value, label) ->
            SegmentButton(
                active = active == value,
                label = label,
                onClick = { onSortChange(value) }
            )
        }
    }
}

@Composable
private fun ViewModeSegment(
    active: UserMediaGalleryViewMode,
    onViewModeChange: (UserMediaGalleryViewMode) -> Unit
) {
    SegmentShell {
        IconButton(onClick = { onViewModeChange(UserMediaGalleryViewMode.Grid) }) {
            Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = stringResource(R.string.gallery_view_grid),
                tint = if (active == UserMediaGalleryViewMode.Grid) GalleryBrand else Color.White.copy(alpha = 0.20f)
            )
        }
        IconButton(onClick = { onViewModeChange(UserMediaGalleryViewMode.List) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.List,
                contentDescription = stringResource(R.string.gallery_view_list),
                tint = if (active == UserMediaGalleryViewMode.List) GalleryBrand else Color.White.copy(alpha = 0.20f)
            )
        }
    }
}

@Composable
private fun SegmentShell(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GallerySoftPanel)
            .border(1.dp, GalleryPanelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SegmentButton(
    active: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) GalleryBrand.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) GalleryBrand else Color.White.copy(alpha = 0.28f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GalleryDetailPane(
    item: UserMediaItem,
    isDownloaded: Boolean,
    progress: Float?,
    localFile: java.io.File? = null,
    stacked: Boolean,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onDeleteLocal: () -> Unit
) {
    Surface(
        modifier = if (stacked) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        } else {
            Modifier
                .width(268.dp)
                .fillMaxHeight()
        },
        color = GalleryPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, GalleryPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gallery_info_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.76f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White.copy(alpha = 0.26f)
                    )
                }
            }

            if (localFile != null && localFile.exists() && item.type == UserMediaType.VIDEO) {
                com.recomo.user.ui.components.VideoPlayer(
                    file = localFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    autoPlay = true,
                    loop = true,
                    showControls = true
                )
            } else {
                MediaThumbnail(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    isDownloaded = isDownloaded,
                    progress = progress
                )
            }

            Text(
                text = displayMediaName(item),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.76f),
                fontWeight = FontWeight.Medium
            )

            DetailMetric(stringResource(R.string.gallery_detail_type), item.type.name)
            DetailMetric(stringResource(R.string.gallery_detail_date), formatTimestamp(item.timestamp))
            DetailMetric(stringResource(R.string.gallery_detail_duration), item.duration?.let(::formatDuration) ?: "—")
            DetailMetric(
                stringResource(R.string.gallery_detail_resolution),
                item.resolution?.let { "${it.width}×${it.height}" } ?: "—"
            )
            DetailMetric(stringResource(R.string.gallery_detail_fps), item.fps?.let { "${it}fps" } ?: "—")
            DetailMetric(stringResource(R.string.gallery_detail_size), formatBytes(item.size))
            DetailMetric(stringResource(R.string.gallery_detail_codec), item.codec ?: "—")

            if (progress != null && !isDownloaded) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = GalleryBrand
                )
            }

            Button(
                onClick = onDownload,
                enabled = !isDownloaded && progress == null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GalleryBrand,
                    contentColor = Color.White,
                    disabledContainerColor = GallerySoftPanel,
                    disabledContentColor = Color.White.copy(alpha = 0.22f)
                )
            ) {
                Text(
                    text = if (isDownloaded) stringResource(R.string.gallery_downloaded_badge) else stringResource(R.string.gallery_download_action)
                )
            }

            if (isDownloaded) {
                Button(
                    onClick = onDeleteLocal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GallerySoftPanel,
                        contentColor = Color.White.copy(alpha = 0.56f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.gallery_delete_local))
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(
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
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.18f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = StudioMono),
            color = Color.White.copy(alpha = 0.52f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun EmptyGalleryState(
    title: String,
    body: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.32f)
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.16f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
        }
    }
}

private fun filterCount(
    filter: UserMediaGalleryFilter,
    state: UserMediaGalleryUiState
): Int = when (filter) {
    UserMediaGalleryFilter.All -> state.allItems.size
    UserMediaGalleryFilter.Videos -> state.allItems.count { it.type == UserMediaType.VIDEO }
    UserMediaGalleryFilter.Images -> state.allItems.count { it.type == UserMediaType.IMAGE }
    UserMediaGalleryFilter.Downloaded -> state.allItems.count { state.downloadedIds.contains(it.id) }
}

private fun displayMediaName(item: UserMediaItem): String =
    item.filename.substringBeforeLast('.').replace('_', ' ')

private fun displayMediaMeta(item: UserMediaItem): String {
    val parts = buildList {
        add(if (item.type == UserMediaType.VIDEO) "VIDEO" else "IMAGE")
        item.duration?.let { add(formatDuration(it)) }
        item.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.US)) }
        add(formatBytes(item.size))
    }
    return parts.joinToString(" · ")
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kilo = 1024.0
    val mega = kilo * 1024.0
    val giga = mega * 1024.0
    return when {
        bytes >= giga -> String.format(Locale.US, "%.1f GB", bytes / giga)
        bytes >= mega -> String.format(Locale.US, "%.1f MB", bytes / mega)
        bytes >= kilo -> String.format(Locale.US, "%.1f KB", bytes / kilo)
        else -> "$bytes B"
    }
}

private fun formatDuration(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("M/d HH:mm", Locale.US).format(Date(timestamp))
