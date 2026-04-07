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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.data.trajectory.LocalTrajectorySessionSourceKind
import com.recomo.user.data.trajectory.LocalTrajectorySessionSummary

data class LocalSessionListCardRowUiState(
    val title: String = "",
    val subtitle: String = "",
    val sourceLabel: String = "",
    val sourceKind: LocalTrajectorySessionSourceKind = LocalTrajectorySessionSourceKind.Assets,
    val previewButtonLabel: String = "Preview",
    val useButtonLabel: String = "Use",
    val previewEnabled: Boolean = true,
    val useEnabled: Boolean = true
)

fun LocalTrajectorySessionSourceKind.displaySourceLabel(): String =
    when (this) {
        LocalTrajectorySessionSourceKind.Assets -> "Bundled"
        LocalTrajectorySessionSourceKind.Filesystem -> "Synced folder"
    }

fun LocalTrajectorySessionSummary.displayMotionTitle(): String =
    sessionName.trim().ifBlank {
        category.trim().takeIf { it.isNotBlank() } ?: "Saved motion"
    }

fun LocalTrajectorySessionSummary.displayMotionMeta(): String =
    listOfNotNull(
        count.takeIf { it > 0 }?.let { "$it keyframes" },
        category.trim().takeIf { it.isNotBlank() },
        robotName.trim().takeIf { it.isNotBlank() }
    ).joinToString(" · ").ifBlank { "Ready to stage" }

fun LocalTrajectorySessionSummary.toLocalSessionListCardRowUiState(): LocalSessionListCardRowUiState {
    return LocalSessionListCardRowUiState(
        title = displayMotionTitle(),
        subtitle = displayMotionMeta(),
        sourceLabel = source.kind.displaySourceLabel(),
        sourceKind = source.kind
    )
}

@Composable
fun LocalSessionListCardRow(
    state: LocalSessionListCardRowUiState,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit,
    onUse: () -> Unit
) {
    val sourceAccent = when (state.sourceKind) {
        LocalTrajectorySessionSourceKind.Assets -> Color(0xFF66D2FF)
        LocalTrajectorySessionSourceKind.Filesystem -> Color(0xFF66BB6A)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { "Untitled session" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xB3FFFFFF)
                        )
                    }
                }

                SourceBadge(
                    label = state.sourceLabel.ifBlank { "Unknown" },
                    accent = sourceAccent
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = state.previewEnabled
                ) {
                    Text(state.previewButtonLabel)
                }
                Button(
                    onClick = onUse,
                    enabled = state.useEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00A3FF),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF223341),
                        disabledContentColor = Color(0x66FFFFFF)
                    )
                ) {
                    Text(state.useButtonLabel)
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
    }
}
