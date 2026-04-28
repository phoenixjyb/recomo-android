package com.recomo.user.ui.screens.runner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Full-screen countdown overlay: 3 → 2 → 1 → GO!
 *
 * Displays a large animated counter that ticks once per second. When the
 * counter reaches zero, [onComplete] fires and the overlay auto-dismisses.
 * The user can tap "Cancel" at any time to abort ([onCancel]).
 *
 * @param durationSeconds Countdown length (configurable in Settings, default 3).
 * @param onComplete Called exactly once when the countdown finishes.
 * @param onCancel Called if the user taps "Cancel" before the countdown ends.
 */
@Composable
fun CountdownOverlay(
    durationSeconds: Int = 3,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remaining by remember { mutableIntStateOf(durationSeconds.coerceAtLeast(1)) }
    var showGo by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in durationSeconds.coerceAtLeast(1) downTo 1) {
            remaining = i
            delay(1000L)
        }
        showGo = true
        delay(600L)
        dismissed = true
        onComplete()
    }

    AnimatedVisibility(
        visible = !dismissed,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                if (showGo) {
                    CountdownText(text = "GO!", color = Color(0xFF00E676))
                } else {
                    CountdownText(text = remaining.toString(), color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        dismissed = true
                        onCancel()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownText(text: String, color: Color) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "countdown_scale"
    )
    Text(
        text = text,
        fontSize = 120.sp,
        fontWeight = FontWeight.Black,
        color = color,
        modifier = Modifier.scale(scale)
    )
}
