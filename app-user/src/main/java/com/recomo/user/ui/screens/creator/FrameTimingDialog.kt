package com.recomo.user.ui.screens.creator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.user.control.FrameTiming

/**
 * Dialog for setting dwell / transition / ease on a single keyframe.
 *
 * Ports `:app` `FrameTimingDialog` layout and validation rules:
 *  - dwell: blank, or 0..60 s (warn if > 20 s)
 *  - transition: blank, or 0.1..60 s (warn if > 30 s or < 1 s)
 *  - ease: linear / ease_in / ease_out / ease_in_out
 *
 * Semantics (matches `:app` `setFrameTiming`): blank fields pass `null`, which
 * **removes** the corresponding key from the persisted frame JSON. Use the
 * "Skip" dismiss button to leave the frame untouched.
 */
@Composable
fun FrameTimingDialog(
    frameName: String,
    currentTiming: FrameTiming? = null,
    onConfirm: (FrameTiming) -> Unit,
    onDismiss: () -> Unit
) {
    var dwellText by remember(frameName) {
        mutableStateOf(currentTiming?.dwellS?.toString() ?: "")
    }
    var transitionText by remember(frameName) {
        mutableStateOf(currentTiming?.transitionS?.toString() ?: "")
    }
    var ease by remember(frameName) {
        mutableStateOf(currentTiming?.ease ?: "linear")
    }

    val dwellValue = dwellText.toDoubleOrNull()
    val transitionValue = transitionText.toDoubleOrNull()
    val dwellValid = dwellText.isBlank() ||
        (dwellValue != null && dwellValue >= 0.0 && dwellValue <= 60.0)
    val transitionValid = transitionText.isBlank() ||
        (transitionValue != null && transitionValue >= 0.1 && transitionValue <= 60.0)
    val isValid = dwellValid && transitionValid

    val warning = when {
        dwellValue != null && dwellValue > 20.0 ->
            "Long pause (${"%.1f".format(dwellValue)}s). Sure?"
        transitionValue != null && transitionValue > 30.0 ->
            "Very slow motion (${"%.1f".format(transitionValue)}s)."
        transitionValue != null && transitionValue < 1.0 ->
            "Very fast motion (${"%.1f".format(transitionValue)}s). May be jerky."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frame Timing: $frameName", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Set timing for this frame (optional — blank = clear)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = dwellText,
                    onValueChange = { raw ->
                        dwellText = raw.filter { it.isDigit() || it == '.' }
                    },
                    label = {
                        Text("Pause at frame (0-60s)", color = Color.White.copy(alpha = 0.6f))
                    },
                    placeholder = {
                        Text("0-5s typical, blank = no pause", color = Color.White.copy(alpha = 0.4f))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !dwellValid,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2A2F),
                        unfocusedContainerColor = Color(0xFF2A2A2F)
                    )
                )

                OutlinedTextField(
                    value = transitionText,
                    onValueChange = { raw ->
                        transitionText = raw.filter { it.isDigit() || it == '.' }
                    },
                    label = {
                        Text("Move time to next (0.1-60s)", color = Color.White.copy(alpha = 0.6f))
                    },
                    placeholder = {
                        Text("2-10s typical, blank = auto", color = Color.White.copy(alpha = 0.4f))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !transitionValid,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2A2F),
                        unfocusedContainerColor = Color(0xFF2A2A2F)
                    )
                )

                if (warning != null) {
                    Text(text = warning, color = Color(0xFFFFDD88), fontSize = 11.sp)
                }

                Text("Motion Style", color = Color.White, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("linear", "ease_in", "ease_out", "ease_in_out").forEach { option ->
                        Button(
                            onClick = { ease = option },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ease == option) {
                                    Color(0xFF4A9EFF)
                                } else {
                                    Color(0xFF3A3A3F)
                                }
                            ),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                text = option.replace("_", "\n"),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timing = FrameTiming(
                        dwellS = dwellText.toDoubleOrNull(),
                        transitionS = transitionText.toDoubleOrNull(),
                        // "linear" is the default — emit null so the JSON omits the key
                        ease = if (ease == "linear") null else ease
                    )
                    onConfirm(timing)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Skip", color = Color.White) }
        },
        containerColor = Color(0xFF1F1F24)
    )
}
