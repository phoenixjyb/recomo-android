package com.recomo.user.ui.screens.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.recomo.user.R

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MapLocalizationPanel(
    state: MapLocalizationUiState,
    modifier: Modifier = Modifier,
    onOpenMapMatch: () -> Unit = {},
    onLocationChange: (String) -> Unit,
    onMapAssetChange: (String) -> Unit,
    onSelectLocation: () -> Unit,
    onSelectMapAsset: () -> Unit,
    onPrepareLocalization: () -> Unit,
    onDismissLocalization: () -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF66D2FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.map_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = state.localizationStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.robotPoseOk) Color(0xFF66BB6A) else Color(0xFFF5C451)
            )

            state.backendActionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF)
                )
            }

            OutlinedTextField(
                value = state.selectedLocation.orEmpty(),
                onValueChange = onLocationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.map_location)) },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true
            )

            OutlinedTextField(
                value = state.selectedMapAsset.orEmpty(),
                onValueChange = onMapAssetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.map_asset)) },
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                singleLine = true
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(
                    stringResource(R.string.map_pose_label),
                    if (state.robotPoseOk) stringResource(R.string.map_localized) else stringResource(R.string.map_searching)
                )
                Chip(
                    stringResource(R.string.map_busy_label),
                    if (state.isBusy) stringResource(R.string.map_yes) else stringResource(R.string.map_no)
                )
                Chip(stringResource(R.string.map_locations_label), state.availableLocations.size.toString())
                Chip(stringResource(R.string.map_assets_label), state.availableMapAssets.size.toString())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSelectLocation,
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B5EFF))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_select_location))
                }
                OutlinedButton(
                    onClick = onSelectMapAsset,
                    enabled = !state.isBusy
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_select_map))
                }
                Button(
                    onClick = onPrepareLocalization,
                    enabled = !state.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A3FF))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_prepare))
                }
                OutlinedButton(
                    onClick = onOpenMapMatch,
                    enabled = !state.isBusy
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_match_title))
                }
                OutlinedButton(
                    onClick = onDismissLocalization,
                    enabled = !state.isBusy
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_dismiss))
                }
                OutlinedButton(
                    onClick = { onLocationChange(state.selectedLocation.orEmpty()) },
                    enabled = !state.isBusy
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_refresh))
                }
            }

            state.availableLocations.takeIf { it.isNotEmpty() }?.let { locations ->
                Text(
                    text = stringResource(R.string.map_match_locations_available, locations.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x80FFFFFF)
                )
            }

            state.availableMapAssets.takeIf { it.isNotEmpty() }?.let { assets ->
                Text(
                    text = stringResource(R.string.map_match_assets_available, assets.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x80FFFFFF)
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
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
