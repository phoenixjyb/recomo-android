package com.recomo.user.ui.screens.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recomo.user.R

@Composable
fun RunChecklistCard(
    state: RunChecklistUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.run_checklist_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (state.allReady) {
                    stringResource(R.string.run_checklist_ready, state.readyCount, state.totalCount)
                } else {
                    stringResource(R.string.run_checklist_pending, state.readyCount, state.totalCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.allReady) Color(0xFF1BC47D) else Color(0xB3FFFFFF)
            )

            ChecklistRow(
                label = stringResource(R.string.run_checklist_gateway),
                detail = state.gatewayDetail,
                ready = state.gatewayReady
            )
            ChecklistRow(
                label = stringResource(R.string.run_checklist_robot),
                detail = state.robotDetail,
                ready = state.robotReady
            )
            ChecklistRow(
                label = stringResource(R.string.run_checklist_localization),
                detail = state.localizationDetail,
                ready = state.localizationReady
            )
            ChecklistRow(
                label = stringResource(R.string.run_checklist_session),
                detail = state.sessionDetail,
                ready = state.sessionReady
            )
            ChecklistRow(
                label = stringResource(R.string.run_checklist_safety),
                detail = state.safetyDetail,
                ready = state.safetyReady
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    label: String,
    detail: String,
    ready: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (ready) Color(0xFF1BC47D) else Color(0xFFFFB74D),
                        shape = RoundedCornerShape(999.dp)
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) Color(0xFF1BC47D) else Color(0xB3FFFFFF)
        )
    }
}
