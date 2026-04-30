package com.recomo.user.ui.screens.smartfollow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.ui.theme.StudioChrome

/**
 * Expandable panel with follow parameter sliders (maxSpeed, followDistance).
 */
@Composable
fun SmartFollowParameterPanel(
    maxSpeed: Double,
    followDistance: Double,
    onMaxSpeedChange: (Double) -> Unit,
    onFollowDistanceChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StudioChrome.background
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — tap to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parameters",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioChrome.textMuted,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "%.1f m/s · %.1f m".format(maxSpeed, followDistance),
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textMuted
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = StudioChrome.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Sliders — animated expand
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    // Max Speed slider
                    ParameterSlider(
                        label = "Max Speed",
                        value = maxSpeed.toFloat(),
                        valueLabel = "%.1f m/s".format(maxSpeed),
                        range = 0.1f..1.5f,
                        onValueChange = { onMaxSpeedChange(it.toDouble()) }
                    )

                    Spacer(Modifier.height(4.dp))

                    // Follow Distance slider
                    ParameterSlider(
                        label = "Distance",
                        value = followDistance.toFloat(),
                        valueLabel = "%.1f m".format(followDistance),
                        range = 0.5f..5.0f,
                        onValueChange = { onFollowDistanceChange(it.toDouble()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textMuted,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = StudioChrome.accentBlue,
                activeTrackColor = StudioChrome.accentBlue,
                inactiveTrackColor = StudioChrome.panelBorder
            )
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textPrimary,
            modifier = Modifier.width(52.dp)
        )
    }
}
