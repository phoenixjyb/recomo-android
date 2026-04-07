package com.recomo.user.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibrarySummaryPanel(
    state: LibrarySummaryUiState,
    modifier: Modifier = Modifier,
    onSessionClick: (LibrarySessionSummaryUiItem) -> Unit = {}
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.lib_summary_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "${state.totalCount} sessions",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Collections, contentDescription = null)
                    }
                )
            }

            if (state.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp))
                    Text(
                        text = stringResource(R.string.lib_summary_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }
            }

            if (!state.hasContent && !state.isLoading) {
                EmptyState(message = state.emptyMessage)
            } else {
                SessionSection(
                    title = LibrarySessionType.FOI.displayGroupLabel(),
                    items = state.foiSessions,
                    onSessionClick = onSessionClick
                )
                SessionSection(
                    title = LibrarySessionType.POI.displayGroupLabel(),
                    items = state.poiSessions,
                    onSessionClick = onSessionClick
                )
            }
        }
    }
}

@Composable
private fun SessionSection(
    title: String,
    items: List<LibrarySessionSummaryUiItem>,
    onSessionClick: (LibrarySessionSummaryUiItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF66D2FF))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "(${items.size})",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x80FFFFFF)
            )
        }

        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.lib_summary_no_items),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x80FFFFFF)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.sessionId }) { item ->
                    SessionCard(item = item, onClick = { onSessionClick(item) })
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionCard(
    item: LibrarySessionSummaryUiItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (item.isSelected) 1.dp else 0.5.dp,
            color = if (item.isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0x10FFFFFF)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayMotionTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.displayMotionSubtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0x80FFFFFF)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0x80FFFFFF)
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionChip(label = stringResource(R.string.lib_summary_type), value = item.type.displayLabel())
                if (item.robotName.isNotBlank()) {
                    SessionChip(label = stringResource(R.string.library_robot), value = item.robotName)
                }
                if (item.category.isNotBlank()) {
                    SessionChip(label = stringResource(R.string.library_category), value = item.category)
                }
                if (item.frameId.isNotBlank()) {
                    SessionChip(label = stringResource(R.string.library_frame), value = item.frameId)
                }
                SessionChip(label = stringResource(R.string.library_count), value = item.count.toString())
            }
        }
    }
}

@Composable
private fun SessionChip(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x80FFFFFF)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Collections,
                contentDescription = null,
                tint = Color(0x80FFFFFF)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.lib_summary_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x80FFFFFF)
            )
        }
    }
}
