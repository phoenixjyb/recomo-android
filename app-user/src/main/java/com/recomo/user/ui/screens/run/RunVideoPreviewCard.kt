package com.recomo.user.ui.screens.run

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.common.model.ConnectionState as VideoConnectionState
import com.recomo.user.R
import com.recomo.user.control.UserRunVideoDetailState
import com.recomo.user.control.UserRunVideoUiState

@Composable
fun RunVideoPreviewCard(
    state: UserRunVideoUiState,
    bitmapFrame: Bitmap?,
    modifier: Modifier = Modifier,
    onSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF)),
        tonalElevation = 0.dp
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.run_live_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (val connection = state.connectionState) {
                            is VideoConnectionState.Connected -> stringResource(R.string.connection_status_connected)
                            is VideoConnectionState.Connecting -> stringResource(R.string.connection_status_connecting)
                            is VideoConnectionState.Error -> state.errorMessage ?: connection.message
                            else -> stringResource(R.string.connection_status_disconnected)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (state.connectionState) {
                            is VideoConnectionState.Connected -> Color(0xFF1BC47D)
                            is VideoConnectionState.Connecting -> Color(0xFFFFB74D)
                            is VideoConnectionState.Error -> Color(0xFFEF5350)
                            else -> Color(0xB3FFFFFF)
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text(stringResource(R.string.connection_disconnect))
                    }
                    Button(onClick = onReconnect, enabled = state.canReconnect) {
                        Text(stringResource(R.string.connection_reconnect))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080808))
            ) {
                if (state.showBitmapFrame && bitmapFrame != null) {
                    Image(
                        bitmap = bitmapFrame.asImageBitmap(),
                        contentDescription = stringResource(R.string.run_live_preview_title),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AndroidView(
                        factory = { context ->
                            RunVideoSurfaceView(context).apply {
                                setZOrderMediaOverlay(false)
                                setOnSurfaceReadyListener(onSurfaceReady)
                                setOnSurfaceDestroyedListener(onSurfaceDestroyed)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!state.isStreaming) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        state.detailState == UserRunVideoDetailState.ReceivingFrames -> Color(0xFF1BC47D)
                                        state.connectionState is VideoConnectionState.Connected -> Color(0xFF1BC47D)
                                        state.connectionState is VideoConnectionState.Connecting -> Color(0xFFFFB74D)
                                        state.connectionState is VideoConnectionState.Error -> Color(0xFFEF5350)
                                        else -> Color(0x66FFFFFF)
                                    }
                                )
                        )
                        Text(
                            text = when (state.detailState) {
                                UserRunVideoDetailState.NoUrl -> stringResource(R.string.run_live_preview_no_url)
                                UserRunVideoDetailState.WaitingFrames -> stringResource(R.string.run_live_preview_waiting_frames)
                                UserRunVideoDetailState.ReceivingFrames -> stringResource(R.string.run_live_preview_receiving)
                                UserRunVideoDetailState.Error -> stringResource(R.string.run_live_preview_error)
                                UserRunVideoDetailState.Idle -> when (state.connectionState) {
                                    is VideoConnectionState.Connecting -> stringResource(R.string.run_live_preview_connecting)
                                    else -> stringResource(R.string.run_live_preview_offline)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xE6FFFFFF)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RunVideoMetric(
                    label = stringResource(R.string.run_live_preview_stream),
                    value = state.cameraUrl.ifBlank { "—" },
                    modifier = Modifier.weight(1f)
                )
                RunVideoMetric(
                    label = stringResource(R.string.run_live_preview_codec),
                    value = state.codecLabel ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RunVideoMetric(
                    label = stringResource(R.string.run_live_preview_resolution),
                    value = state.resolutionLabel,
                    modifier = Modifier.weight(1f)
                )
                RunVideoMetric(
                    label = stringResource(R.string.run_live_preview_fps),
                    value = state.fpsLabel ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RunVideoMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x10FFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0x99FFFFFF)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
