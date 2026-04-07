package com.recomo.user.ui.screens.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.R

@Composable
fun TrajectoryHandoffCard(
    state: TrajectoryHandoffCardUiState,
    modifier: Modifier = Modifier,
    onPrimaryAction: () -> Unit
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.traj_handoff_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.sourceName.ifBlank { "Unknown source" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF)
                    )
                }

                ReadinessBadge(readiness = state.readiness)
            }
            state.secondaryNote?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0x10FFFFFF))
                ) {
                    Text(
                        text = note,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xCCFFFFFF)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrimaryAction,
                    enabled = state.primaryButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF223341),
                        disabledContentColor = Color(0x66FFFFFF)
                    )
                ) {
                    Text(state.primaryButtonLabel)
                }
            }
        }
    }
}

@Composable
private fun ReadinessBadge(
    readiness: TrajectoryHandoffReadiness
) {
    val accent = when (readiness) {
        TrajectoryHandoffReadiness.Ready -> Color(0xFF66BB6A)
        TrajectoryHandoffReadiness.Pending -> Color(0xFFF5C451)
        TrajectoryHandoffReadiness.Blocked -> Color(0xFFEF5350)
        TrajectoryHandoffReadiness.Unknown -> Color(0xFF90A4AE)
    }

    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = readiness.name,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
    }
}
