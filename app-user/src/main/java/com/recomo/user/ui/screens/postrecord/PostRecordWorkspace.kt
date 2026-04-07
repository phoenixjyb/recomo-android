package com.recomo.user.ui.screens.postrecord

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.user.R
import com.recomo.user.control.UserPostRecordUiState
import com.recomo.user.data.postrecord.UserPostRecordItem
import com.recomo.user.ui.theme.StudioChrome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val PostBackground = StudioChrome.background
private val PostPanel = StudioChrome.panel
private val PostPanelBorder = StudioChrome.panelBorder
private val PostSoft = StudioChrome.panelSoft
private val PostBrand = StudioChrome.accentBlue
private val PostAccent = StudioChrome.accentPurple
private val PostSuccess = StudioChrome.success

@Composable
fun PostRecordWorkspace(
    state: UserPostRecordUiState,
    selectedRecording: UserPostRecordItem?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSelectRecording: (UserPostRecordItem) -> Unit,
    onRefresh: () -> Unit,
    onDelete: (UserPostRecordItem) -> Unit,
    onOpenGallery: () -> Unit,
    onRunAgain: () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioChrome.screenBackgroundBrush)
    ) {
        val stacked = maxWidth < 1080.dp

        if (stacked) {
            Column(modifier = Modifier.fillMaxSize()) {
                PostRecordInfoPanel(
                    state = state,
                    selectedRecording = selectedRecording,
                    stacked = true,
                    onBack = onBack,
                    onSelectRecording = onSelectRecording,
                    onRefresh = onRefresh,
                    onDelete = onDelete,
                    onOpenGallery = onOpenGallery,
                    onRunAgain = onRunAgain
                )
                PostRecordPlayerPanel(
                    recording = selectedRecording,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                PostRecordExportPanel(
                    recording = selectedRecording,
                    stacked = true
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                PostRecordInfoPanel(
                    state = state,
                    selectedRecording = selectedRecording,
                    stacked = false,
                    onBack = onBack,
                    onSelectRecording = onSelectRecording,
                    onRefresh = onRefresh,
                    onDelete = onDelete,
                    onOpenGallery = onOpenGallery,
                    onRunAgain = onRunAgain
                )
                PostRecordPlayerPanel(
                    recording = selectedRecording,
                    modifier = Modifier.weight(1f)
                )
                PostRecordExportPanel(
                    recording = selectedRecording,
                    stacked = false
                )
            }
        }
    }
}

@Composable
private fun PostRecordInfoPanel(
    state: UserPostRecordUiState,
    selectedRecording: UserPostRecordItem?,
    stacked: Boolean,
    onBack: () -> Unit,
    onSelectRecording: (UserPostRecordItem) -> Unit,
    onRefresh: () -> Unit,
    onDelete: (UserPostRecordItem) -> Unit,
    onOpenGallery: () -> Unit,
    onRunAgain: () -> Unit
) {
    val modifier = if (stacked) {
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    } else {
        Modifier
            .width(288.dp)
            .fillMaxHeight()
    }
    Surface(
        modifier = modifier,
        color = PostPanel,
        border = BorderStroke(1.dp, PostPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White.copy(alpha = 0.56f)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.route_post_record),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.post_record_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.22f)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.post_record_refresh),
                        tint = Color.White.copy(alpha = 0.46f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedRecording != null) {
                Surface(
                    color = PostSuccess.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PostSuccess.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(PostSuccess.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = PostSuccess
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.post_record_completed),
                                style = MaterialTheme.typography.labelLarge,
                                color = PostSuccess
                            )
                            Text(
                                text = stringResource(R.string.post_record_saved_local),
                                style = MaterialTheme.typography.labelSmall,
                                color = PostSuccess.copy(alpha = 0.68f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            Text(
                text = stringResource(R.string.post_record_file_info),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.18f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            PostRecordMetadataCard(recording = selectedRecording)

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.post_record_recent_takes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.18f)
                )
                Text(
                    text = stringResource(R.string.post_record_take_count, state.recordings.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when {
                state.isLoading && state.recordings.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = PostBrand
                        )
                    }
                }

                state.recordings.isEmpty() -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        color = PostSoft,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PostPanelBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.post_record_empty_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.84f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.post_record_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.26f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.recordings, key = { it.id }) { recording ->
                            PostRecordTakeRow(
                                recording = recording,
                                selected = selectedRecording?.id == recording.id,
                                onClick = { onSelectRecording(recording) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenGallery,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PostBrand,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.post_record_open_gallery))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onRunAgain,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = Color.White.copy(alpha = 0.82f)
                        )
                    ) {
                        Text(stringResource(R.string.post_record_run_again))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { selectedRecording?.let(onDelete) },
                        enabled = selectedRecording != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = Color.White.copy(alpha = 0.82f),
                            disabledContainerColor = Color.White.copy(alpha = 0.03f),
                            disabledContentColor = Color.White.copy(alpha = 0.24f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.post_record_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRecordMetadataCard(recording: UserPostRecordItem?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PostSoft,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PostPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataRow(
                label = stringResource(R.string.gallery_detail_duration),
                value = recording?.durationMs?.let(::formatDuration) ?: "00:00"
            )
            MetadataRow(
                label = stringResource(R.string.gallery_detail_resolution),
                value = recording?.let(::formatResolution) ?: "—"
            )
            MetadataRow(
                label = stringResource(R.string.gallery_detail_fps),
                value = recording?.let(::formatFrameRate) ?: "—"
            )
            MetadataRow(
                label = stringResource(R.string.gallery_detail_codec),
                value = recording?.codecLabel ?: "—"
            )
            MetadataRow(
                label = stringResource(R.string.gallery_detail_size),
                value = recording?.fileSizeBytes?.let(::formatBytes) ?: "—"
            )
            MetadataRow(
                label = stringResource(R.string.gallery_detail_date),
                value = recording?.recordedAtMs?.let(::formatFullDate) ?: "—"
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.22f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.66f)
        )
    }
}

@Composable
private fun PostRecordTakeRow(
    recording: UserPostRecordItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (selected) PostBrand.copy(alpha = 0.08f) else PostSoft,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (selected) PostBrand.copy(alpha = 0.2f) else PostPanelBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(PostBrand.copy(alpha = 0.24f), PostAccent.copy(alpha = 0.24f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.post_record_take_label, formatTakeTime(recording.recordedAtMs)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFullDate(recording.recordedAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.26f)
                )
            }
            Text(
                text = recording.durationMs?.let(::formatDuration) ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) PostBrand else Color.White.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun PostRecordPlayerPanel(
    recording: UserPostRecordItem?,
    modifier: Modifier = Modifier
) {
    var videoView by remember(recording?.id) { mutableStateOf<VideoView?>(null) }
    var isPrepared by remember(recording?.id) { mutableStateOf(false) }
    var isPlaying by remember(recording?.id) { mutableStateOf(false) }
    var durationMs by remember(recording?.id) { mutableLongStateOf(recording?.durationMs ?: 0L) }
    var positionMs by remember(recording?.id) { mutableLongStateOf(0L) }
    val context = LocalContext.current

    DisposableEffect(recording?.id) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    LaunchedEffect(recording?.id, isPrepared, isPlaying) {
        while (recording != null && isPrepared) {
            videoView?.let { view ->
                positionMs = view.currentPosition.toLong()
                isPlaying = view.isPlaying
            }
            delay(if (isPlaying) 120L else 300L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = PostPanel,
            border = BorderStroke(1.dp, PostPanelBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.post_record_replay),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = recording?.let {
                                listOf(formatResolution(it), formatFrameRate(it), it.codecLabel ?: "—")
                                    .joinToString(" · ")
                            } ?: stringResource(R.string.post_record_no_selection),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.28f)
                        )
                    }
                    if (recording != null) {
                        Text(
                            text = stringResource(R.string.post_record_saved_local),
                            style = MaterialTheme.typography.labelSmall,
                            color = PostBrand
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    if (recording == null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.post_record_no_selection),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.84f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.post_record_no_selection_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.24f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        key(recording.id) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = {
                                    VideoView(context).apply {
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        setVideoURI(Uri.fromFile(recording.file))
                                        setOnPreparedListener { mediaPlayer ->
                                            isPrepared = true
                                            durationMs = mediaPlayer.duration.toLong().takeIf { it > 0L }
                                                ?: recording.durationMs
                                                ?: 0L
                                            seekTo(1)
                                        }
                                        setOnCompletionListener {
                                            isPlaying = false
                                            positionMs = durationMs
                                        }
                                        videoView = this
                                    }
                                }
                            )
                        }

                        if (!isPlaying) {
                            Button(
                                modifier = Modifier.align(Alignment.Center),
                                onClick = {
                                    videoView?.start()
                                    isPlaying = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (positionMs >= durationMs && durationMs > 0L) {
                                        stringResource(R.string.post_record_replay)
                                    } else {
                                        stringResource(R.string.run_run)
                                    }
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    val progress = if (durationMs > 0L) {
                        positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
                    } else {
                        0f
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PostSoft)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        repeat(48) { index ->
                            val fraction = (index + 1) / 48f
                            val heightFraction = 0.28f + (((index % 7) + 1) / 10f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (fraction <= progress) PostBrand.copy(alpha = 0.46f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.28f)
                        )
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.28f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                videoView?.seekTo(0)
                                positionMs = 0L
                                isPlaying = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                contentColor = Color.White.copy(alpha = 0.82f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.post_record_restart_action))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val view = videoView ?: return@Button
                                if (view.isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    if (positionMs >= durationMs && durationMs > 0L) {
                                        view.seekTo(0)
                                        positionMs = 0L
                                    }
                                    view.start()
                                    isPlaying = true
                                }
                            },
                            enabled = recording != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PostBrand,
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.05f),
                                disabledContentColor = Color.White.copy(alpha = 0.24f)
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) stringResource(R.string.run_pause)
                                else stringResource(R.string.run_run)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRecordExportPanel(
    recording: UserPostRecordItem?,
    stacked: Boolean
) {
    val modifier = if (stacked) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    } else {
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
    }
    Surface(
        modifier = modifier,
        color = PostPanel,
        border = BorderStroke(1.dp, PostPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.post_record_export_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.post_record_export_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.24f)
                )
            }

            Surface(
                color = PostSoft,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PostPanelBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExportRow(
                        label = stringResource(R.string.post_record_export_format),
                        value = recording?.file?.extension?.uppercase()?.ifBlank { "MP4" } ?: "MP4"
                    )
                    ExportRow(
                        label = stringResource(R.string.post_record_export_resolution),
                        value = recording?.let(::formatResolution) ?: "—"
                    )
                    ExportRow(
                        label = stringResource(R.string.post_record_export_frame_rate),
                        value = recording?.let(::formatFrameRate) ?: "—"
                    )
                    ExportRow(
                        label = stringResource(R.string.post_record_export_codec),
                        value = recording?.codecLabel ?: "—"
                    )
                }
            }

            Surface(
                color = PostBrand.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PostBrand.copy(alpha = 0.16f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.post_record_export_coming_soon),
                        style = MaterialTheme.typography.labelLarge,
                        color = PostBrand
                    )
                    Text(
                        text = stringResource(R.string.post_record_trim_coming),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.28f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.24f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.66f)
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatFullDate(timestamp: Long): String =
    SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatTakeTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatResolution(recording: UserPostRecordItem): String =
    if ((recording.width ?: 0) > 0 && (recording.height ?: 0) > 0) {
        "${recording.width}×${recording.height}"
    } else {
        "—"
    }

private fun formatFrameRate(recording: UserPostRecordItem): String =
    recording.frameRate
        ?.takeIf { it > 0f }
        ?.let {
            if (it % 1f == 0f) "${it.toInt()} fps"
            else "${String.format(Locale.US, "%.1f", it)} fps"
        }
        ?: "—"

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
