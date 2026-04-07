package com.recomo.remotecontrol.ui.tracking

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Button to toggle visibility of control panels.
 */
@Composable
fun PanelToggleButton(
    panelsVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size by animateDpAsState(targetValue = if (panelsVisible) 48.dp else 44.dp, label = "button_size")
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xAA1A1A1A))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (panelsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (panelsVisible) "Hide panels" else "Show panels",
            tint = if (panelsVisible) Color.White else Color(0xFF4CAF50),
            modifier = Modifier.size(28.dp)
        )
    }
}
