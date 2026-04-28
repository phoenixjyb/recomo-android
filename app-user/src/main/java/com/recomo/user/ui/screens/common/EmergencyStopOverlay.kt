package com.recomo.user.ui.screens.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating emergency-stop button intended to be placed as a Box overlay on
 * every control-capable page. Toggles between "ESTOP" (send emergency stop)
 * and "CLEAR" (clear emergency stop) based on [estopActive].
 *
 * Place inside a Box with `Alignment.BottomEnd` (or any preferred corner).
 */
@Composable
fun EmergencyStopOverlay(
    estopActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onToggle,
        modifier = modifier
            .padding(16.dp)
            .size(width = 72.dp, height = 40.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = if (estopActive) Color(0xFFB91C1C) else Color(0xFFEF4444),
        contentColor = Color.White
    ) {
        Text(
            text = if (estopActive) "CLEAR" else "ESTOP",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
