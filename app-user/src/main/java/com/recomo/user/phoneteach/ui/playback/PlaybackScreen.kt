package com.recomo.user.phoneteach.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import java.io.File
import kotlin.math.sqrt

/**
 * Playback screen for a single Phone Teach session.
 *
 * Embedded in [com.recomo.user.phoneteach.PhoneTeachNavHost]; receives the target
 * [sessionDir] from the nav host state (set when the user taps a session card in Library).
 */
@Composable
fun PlaybackScreen(
    sessionDir: File,
    modifier: Modifier = Modifier,
    onBackToLibrary: () -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exoPlayer = viewModel.exoPlayer
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val imuData by viewModel.imuData.collectAsStateWithLifecycle()
    val currentImuSample by viewModel.currentImuSample.collectAsStateWithLifecycle()

    LaunchedEffect(sessionDir) {
        viewModel.loadSession(sessionDir)
    }

    // Note: player lifecycle is owned by the ViewModel (released on onCleared) so we don't
    // call release here. DisposableEffect is a safety net for pause-on-exit only.
    DisposableEffect(sessionDir) {
        onDispose {
            viewModel.exoPlayer?.pause()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBackToLibrary) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to library",
                    tint = Color(0xFFBFBFBF)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sessionDir.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF3F3F3),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                val imuInfo = imuData?.let {
                    "IMU: ${it.samples.size} samples @ ${it.sampleRate.toInt()} Hz"
                } ?: "No IMU data"
                Text(
                    text = imuInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A9A9A)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Video surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view -> view.player = exoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val errorMessage = (playbackState as? PlaybackViewModel.PlaybackState.Error)?.message
                Text(
                    text = errorMessage ?: "Loading…",
                    color = if (errorMessage != null) Color(0xFFCF6E6E) else Color(0xFFBFBFBF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Live IMU overlay (top-right)
            currentImuSample?.let { sample ->
                val aMag = sqrt(
                    (sample.accel.x * sample.accel.x +
                            sample.accel.y * sample.accel.y +
                            sample.accel.z * sample.accel.z).toDouble()
                )
                val gMag = sqrt(
                    (sample.gyro.x * sample.gyro.x +
                            sample.gyro.y * sample.gyro.y +
                            sample.gyro.z * sample.gyro.z).toDouble()
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "accel %.2f m/s²".format(aMag),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "gyro  %.2f rad/s".format(gMag),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress + time
        Column {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { fraction ->
                    if (duration > 0) viewModel.seekTo((fraction * duration).toLong())
                },
                valueRange = 0f..1f,
                enabled = duration > 0
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color(0xFFBFBFBF),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatTime(duration),
                    color = Color(0xFFBFBFBF),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.seekBackward() }) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            FloatingActionButton(
                onClick = { viewModel.playPause() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = { viewModel.seekForward() }) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Forward 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
