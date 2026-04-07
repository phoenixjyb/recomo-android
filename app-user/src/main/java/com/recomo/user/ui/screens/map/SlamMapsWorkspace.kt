package com.recomo.user.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recomo.user.R
import com.recomo.user.ui.theme.StudioChrome

private val SlamBackground = StudioChrome.background
private val SlamPanel = StudioChrome.panel
private val SlamPanelBorder = StudioChrome.panelBorder
private val SlamSoft = StudioChrome.panelSoft
private val SlamBrand = StudioChrome.accentBlue
private val SlamAccent = StudioChrome.accentPurple
private val SlamSuccess = StudioChrome.success
private val SlamWarning = StudioChrome.warning

private enum class SlamMapsMode {
    List,
    Scan,
    Upload
}

@Composable
fun SlamMapsWorkspace(
    state: MapLocalizationUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectLocation: (String) -> Unit,
    onSelectMap: (String) -> Unit,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit,
    onOpenMapMatch: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(SlamMapsMode.List) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredLocations = remember(state.availableLocations, query) {
        filterItems(state.availableLocations, query)
    }
    val filteredMaps = remember(state.availableMapAssets, query) {
        filterItems(state.availableMapAssets, query)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioChrome.screenBackgroundBrush)
    ) {
        val stacked = maxWidth < 1180.dp

        if (stacked) {
            Column(modifier = Modifier.fillMaxSize()) {
                SlamSidebar(
                    mode = mode,
                    query = query,
                    stacked = true,
                    onBack = onBack,
                    onQueryChange = { query = it },
                    onRefresh = onRefresh,
                    onModeChange = { mode = it }
                )
                SlamMainContent(
                    state = state,
                    mode = mode,
                    filteredLocations = filteredLocations,
                    filteredMaps = filteredMaps,
                    stacked = true,
                    modifier = Modifier.weight(1f),
                    onSelectLocation = onSelectLocation,
                    onSelectMap = onSelectMap,
                    onPrepareLocalization = onPrepareLocalization,
                    onDismissLocalization = onDismissLocalization,
                    onOpenMapMatch = onOpenMapMatch
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                SlamSidebar(
                    mode = mode,
                    query = query,
                    stacked = false,
                    onBack = onBack,
                    onQueryChange = { query = it },
                    onRefresh = onRefresh,
                    onModeChange = { mode = it }
                )
                SlamMainContent(
                    state = state,
                    mode = mode,
                    filteredLocations = filteredLocations,
                    filteredMaps = filteredMaps,
                    stacked = false,
                    modifier = Modifier.weight(1f),
                    onSelectLocation = onSelectLocation,
                    onSelectMap = onSelectMap,
                    onPrepareLocalization = onPrepareLocalization,
                    onDismissLocalization = onDismissLocalization,
                    onOpenMapMatch = onOpenMapMatch
                )
            }
        }
    }
}

@Composable
private fun SlamSidebar(
    mode: SlamMapsMode,
    query: String,
    stacked: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onModeChange: (SlamMapsMode) -> Unit
) {
    val modifier = if (stacked) {
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    } else {
        Modifier
            .width(280.dp)
            .fillMaxHeight()
    }
    Surface(
        modifier = modifier,
        color = SlamPanel,
        border = BorderStroke(1.dp, SlamPanelBorder)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.route_slam_maps),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.slam_maps_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.22f)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.map_refresh),
                        tint = Color.White.copy(alpha = 0.46f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SlamModeChip(
                    label = stringResource(R.string.slam_maps_view_list),
                    selected = mode == SlamMapsMode.List,
                    icon = Icons.Outlined.Map,
                    onClick = { onModeChange(SlamMapsMode.List) }
                )
                SlamModeChip(
                    label = stringResource(R.string.slam_maps_view_scan),
                    selected = mode == SlamMapsMode.Scan,
                    icon = Icons.Outlined.Radar,
                    onClick = { onModeChange(SlamMapsMode.Scan) }
                )
                SlamModeChip(
                    label = stringResource(R.string.slam_maps_view_upload),
                    selected = mode == SlamMapsMode.Upload,
                    icon = Icons.Outlined.CloudUpload,
                    onClick = { onModeChange(SlamMapsMode.Upload) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SlamSoft,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SlamPanelBorder)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        if (query.isBlank()) {
                            Text(
                                text = stringResource(R.string.slam_maps_search_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.18f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

@Composable
private fun SlamModeChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (selected) SlamBrand.copy(alpha = 0.08f) else SlamSoft,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) SlamBrand.copy(alpha = 0.18f) else SlamPanelBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) SlamBrand else Color.White.copy(alpha = 0.36f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun SlamMainContent(
    state: MapLocalizationUiState,
    mode: SlamMapsMode,
    filteredLocations: List<String>,
    filteredMaps: List<String>,
    stacked: Boolean,
    modifier: Modifier,
    onSelectLocation: (String) -> Unit,
    onSelectMap: (String) -> Unit,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit,
    onOpenMapMatch: () -> Unit
) {
    when (mode) {
        SlamMapsMode.List -> SlamListMode(
            state = state,
            filteredLocations = filteredLocations,
            filteredMaps = filteredMaps,
            stacked = stacked,
            modifier = modifier,
            onSelectLocation = onSelectLocation,
            onSelectMap = onSelectMap,
            onPrepareLocalization = onPrepareLocalization,
            onDismissLocalization = onDismissLocalization,
            onOpenMapMatch = onOpenMapMatch
        )

        SlamMapsMode.Scan -> SlamPlaceholderMode(
            modifier = modifier,
            icon = Icons.Outlined.Radar,
            title = stringResource(R.string.slam_maps_scan_title),
            body = stringResource(R.string.slam_maps_scan_body)
        )

        SlamMapsMode.Upload -> SlamPlaceholderMode(
            modifier = modifier,
            icon = Icons.Outlined.CloudUpload,
            title = stringResource(R.string.slam_maps_upload_title),
            body = stringResource(R.string.slam_maps_upload_body)
        )
    }
}

@Composable
private fun SlamListMode(
    state: MapLocalizationUiState,
    filteredLocations: List<String>,
    filteredMaps: List<String>,
    stacked: Boolean,
    modifier: Modifier,
    onSelectLocation: (String) -> Unit,
    onSelectMap: (String) -> Unit,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit,
    onOpenMapMatch: () -> Unit
) {
    val contentModifier = if (stacked) modifier.fillMaxWidth() else modifier.fillMaxHeight()
    if (stacked) {
        Column(
            modifier = contentModifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SlamListPanel(
                title = stringResource(R.string.map_locations_label),
                items = filteredLocations,
                selectedItem = state.selectedLocation,
                emptyTitle = stringResource(R.string.slam_maps_empty_title),
                emptyBody = stringResource(R.string.slam_maps_empty_body),
                icon = Icons.Outlined.Place,
                onSelect = onSelectLocation
            )
            SlamListPanel(
                title = stringResource(R.string.map_assets_label),
                items = filteredMaps,
                selectedItem = state.selectedMapAsset,
                emptyTitle = stringResource(R.string.slam_maps_empty_title),
                emptyBody = stringResource(R.string.slam_maps_empty_body),
                icon = Icons.Outlined.Map,
                onSelect = onSelectMap
            )
            SlamControlPanel(
                state = state,
                onPrepareLocalization = onPrepareLocalization,
                onDismissLocalization = onDismissLocalization,
                onOpenMapMatch = onOpenMapMatch
            )
        }
    } else {
        Row(
            modifier = contentModifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SlamListPanel(
                title = stringResource(R.string.map_locations_label),
                items = filteredLocations,
                selectedItem = state.selectedLocation,
                emptyTitle = stringResource(R.string.slam_maps_empty_title),
                emptyBody = stringResource(R.string.slam_maps_empty_body),
                icon = Icons.Outlined.Place,
                modifier = Modifier.weight(0.95f),
                onSelect = onSelectLocation
            )
            SlamListPanel(
                title = stringResource(R.string.map_assets_label),
                items = filteredMaps,
                selectedItem = state.selectedMapAsset,
                emptyTitle = stringResource(R.string.slam_maps_empty_title),
                emptyBody = stringResource(R.string.slam_maps_empty_body),
                icon = Icons.Outlined.Map,
                modifier = Modifier.weight(1.1f),
                onSelect = onSelectMap
            )
            SlamControlPanel(
                state = state,
                modifier = Modifier.weight(0.95f),
                onPrepareLocalization = onPrepareLocalization,
                onDismissLocalization = onDismissLocalization,
                onOpenMapMatch = onOpenMapMatch
            )
        }
    }
}

@Composable
private fun SlamListPanel(
    title: String,
    items: List<String>,
    selectedItem: String?,
    emptyTitle: String,
    emptyBody: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = SlamPanel,
        border = BorderStroke(1.dp, SlamPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = emptyTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.84f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = emptyBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.24f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        val selected = item == selectedItem
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelect(item) },
                            color = if (selected) SlamBrand.copy(alpha = 0.08f) else SlamSoft,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (selected) SlamBrand.copy(alpha = 0.18f) else SlamPanelBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(SlamBrand.copy(alpha = 0.25f), SlamAccent.copy(alpha = 0.25f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlamControlPanel(
    state: MapLocalizationUiState,
    modifier: Modifier = Modifier,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit,
    onOpenMapMatch: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = SlamPanel,
        border = BorderStroke(1.dp, SlamPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.slam_maps_controls_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            SlamStatusCard(state)

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenMapMatch,
                enabled = !state.isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlamBrand,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.map_match_title))
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPrepareLocalization,
                enabled = !state.isBusy && !state.selectedLocation.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    contentColor = Color.White.copy(alpha = 0.82f),
                    disabledContainerColor = Color.White.copy(alpha = 0.03f),
                    disabledContentColor = Color.White.copy(alpha = 0.24f)
                )
            ) {
                Text(stringResource(R.string.map_prepare))
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismissLocalization,
                enabled = !state.isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    contentColor = Color.White.copy(alpha = 0.82f),
                    disabledContainerColor = Color.White.copy(alpha = 0.03f),
                    disabledContentColor = Color.White.copy(alpha = 0.24f)
                )
            ) {
                Text(stringResource(R.string.map_dismiss))
            }
        }
    }
}

@Composable
private fun SlamStatusCard(state: MapLocalizationUiState) {
    Surface(
        color = SlamSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SlamPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusRow(
                label = stringResource(R.string.slam_maps_active_scene),
                value = state.selectedLocation ?: "—"
            )
            StatusRow(
                label = stringResource(R.string.slam_maps_active_map),
                value = state.selectedMapAsset ?: "—"
            )
            StatusRow(
                label = stringResource(R.string.map_pose_label),
                value = if (state.robotPoseOk) stringResource(R.string.map_localized) else stringResource(R.string.map_searching),
                tone = if (state.robotPoseOk) SlamSuccess else SlamWarning
            )
            StatusRow(
                label = stringResource(R.string.map_busy_label),
                value = if (state.isBusy) stringResource(R.string.map_yes) else stringResource(R.string.map_no),
                tone = if (state.isBusy) SlamBrand else Color.White.copy(alpha = 0.6f)
            )
            state.backendActionMessage?.let { message ->
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, tone: Color = Color.White.copy(alpha = 0.86f)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.24f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SlamPlaceholderMode(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp),
        color = SlamPanel,
        border = BorderStroke(1.dp, SlamPanelBorder)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(SlamBrand.copy(alpha = 0.22f), SlamAccent.copy(alpha = 0.22f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.28f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun filterItems(items: List<String>, query: String): List<String> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return items
    return items.filter { it.lowercase().contains(normalized) }
}
