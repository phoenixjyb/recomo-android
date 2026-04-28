package com.recomo.user.ui.screens.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.common.chat.*
import com.recomo.user.R
import com.recomo.user.ui.screens.common.VideoPreviewContent
import com.recomo.user.ui.theme.StudioChrome
import java.util.UUID

// ── Configuration ────────────────────────────────────────────────
// v2 dev mock lives at ~/yanbo/recomo-chat-v2-mock on the converge dev box (port 6776).
// BuildConfig.DEFAULT_CHAT_SERVER_URL is the source of truth — this is just a fallback
// for ad-hoc previews / unit tests. The /chat path is mandatory per the protocol doc
// (the server only upgrades requests on that path, plus a root fallback).
private const val DEFAULT_CHAT_SERVER_URL = "ws://192.168.100.97:6776/chat"

// ── Colors (delegated to StudioChrome for v2 consistency) ─────────
private val UserBubbleColor = StudioChrome.accentBlue
private val AssistantBubbleColor = StudioChrome.panelMuted
private val SystemColor = StudioChrome.textTertiary
private val TrajectoryCardColor = Color(0xFF1B3A1B)
private val StreamingDotColor = StudioChrome.success

/**
 * Full-screen chat UI.
 *
 * @param viewModel ChatViewModel from :common
 * @param onPreviewTrajectory v1: called when user taps "Preview" on a legacy single trajectory
 * @param onExecuteTrajectory v1: called when user taps "Run" on a legacy single trajectory
 * @param onPreviewCandidate v2: called when user taps "Preview" on a TrajectoryCandidate card
 * @param onExecuteCandidate v2: called when user taps "Execute on Robot" in the selected banner
 */
/**
 * Video preview configuration for the chat screen. When provided, the chat
 * renders in split-pane mode (video left, dialogue right) and a snapshot of
 * the current frame is attached to every user message (v2.1 protocol).
 */
data class ChatVideoConfig(
    val showBitmapFrame: Boolean,
    val videoBitmap: Bitmap?,
    val onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    val onVideoSurfaceDestroyed: () -> Unit,
    /** Returns a base64 JPEG of the current frame, or null if no frame is available. */
    val captureSnapshot: () -> String?,
    /** Optional hook to attach a robot-state snapshot alongside the image. */
    val captureRobotState: (() -> RobotStateSnapshot?)? = null
)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatServerUrl: String = DEFAULT_CHAT_SERVER_URL,
    onPreviewTrajectory: (TrajectoryAttachment) -> Unit = {},
    onExecuteTrajectory: (TrajectoryAttachment) -> Unit = {},
    onPreviewCandidate: (TrajectoryCandidate) -> Unit = {},
    onExecuteCandidate: (TrajectoryCandidate) -> Unit = {},
    onClose: (() -> Unit)? = null,
    videoConfig: ChatVideoConfig? = null,
    voiceEngine: com.recomo.common.chat.voice.VoiceEngine = com.recomo.common.chat.voice.VoiceEngine.SYSTEM,
    whisperRepository: com.recomo.common.chat.voice.WhisperModelRepository? = null,
    voiceModelId: String = com.recomo.common.chat.voice.WhisperModelRepository.DEFAULT_MODEL_ID
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val activeTask by viewModel.activeTask.collectAsState()
    val candidatesState by viewModel.candidates.collectAsState()
    val selectedCandidateId by viewModel.selectedCandidateId.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    val voiceRecognizer = rememberVoiceRecognizer(
        engine = voiceEngine,
        whisperRepository = whisperRepository,
        modelId = voiceModelId
    )
    val voiceState by voiceRecognizer.state.collectAsState()
    LaunchedEffect(voiceState) {
        val s = voiceState
        when (s) {
            is com.recomo.common.chat.voice.VoiceState.Listening -> {
                if (s.partial.isNotEmpty()) inputText = s.partial
            }
            is com.recomo.common.chat.voice.VoiceState.Final -> {
                inputText = s.text
                voiceRecognizer.reset()
            }
            is com.recomo.common.chat.voice.VoiceState.Error -> {
                // Auto-reset after a short display so the button returns
                // to idle (tappable) instead of staying stuck on MicOff.
                kotlinx.coroutines.delay(2000)
                voiceRecognizer.reset()
            }
            com.recomo.common.chat.voice.VoiceState.Idle -> Unit
        }
    }

    // Surface error as a brief snackbar-style hint above the input bar
    val voiceErrorText = remember(voiceState) {
        (voiceState as? com.recomo.common.chat.voice.VoiceState.Error)?.let { e ->
            when (e.code) {
                com.recomo.common.chat.voice.VoiceErrorCode.UNAVAILABLE ->
                    "Voice service unavailable — try Whisper in Settings"
                com.recomo.common.chat.voice.VoiceErrorCode.PERMISSION_DENIED ->
                    "Microphone permission denied"
                com.recomo.common.chat.voice.VoiceErrorCode.NO_MATCH ->
                    "No speech detected — try again"
                else -> e.message
            }
        }
    }

    val currentSelected = remember(candidatesState, selectedCandidateId) {
        val id = selectedCandidateId ?: return@remember null
        candidatesState?.candidates?.firstOrNull { it.id == id }
    }

    // 3D trajectory preview dialog state
    var preview3DCandidate by remember { mutableStateOf<TrajectoryCandidate?>(null) }

    // Auto-connect to chat server when screen first appears
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (connectionState == ChatConnectionState.DISCONNECTED) {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            viewModel.connect(
                url = chatServerUrl.ifBlank { DEFAULT_CHAT_SERVER_URL },
                deviceId = "recomo-tablet-$androidId"
            )
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Captured on every send: builds the v2.1 UserAttachments from the active
    // video frame + robot state. Called right before sendMessage so the
    // snapshot matches what the user was looking at when they tapped send.
    val buildAttachments: () -> UserAttachments? = {
        val cfg = videoConfig
        if (cfg == null) {
            null
        } else {
            val base64 = runCatching { cfg.captureSnapshot() }.getOrNull()
            val snapshot = if (!base64.isNullOrBlank()) {
                SnapshotAttachment(
                    snapshotId = UUID.randomUUID().toString(),
                    dataBase64 = base64,
                    capturedAtMs = System.currentTimeMillis()
                )
            } else null
            val state = cfg.captureRobotState?.let { runCatching { it() }.getOrNull() }
            if (snapshot == null && state == null) null
            else UserAttachments(snapshot = snapshot, robotState = state)
        }
    }

    // ── Chat column (right side in split mode, full-width otherwise) ──
    val chatColumn: @Composable ColumnScope.() -> Unit = {
        ChatHeader(
            connectionState = connectionState,
            onClose = onClose,
            onReconnect = {
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                viewModel.connect(
                    url = chatServerUrl.ifBlank { DEFAULT_CHAT_SERVER_URL },
                    deviceId = "recomo-tablet-$androidId"
                )
            }
        )

        activeTask?.let { task ->
            TaskProgressBar(task)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                when (message.role) {
                    ChatRole.USER -> UserBubble(message)
                    ChatRole.ASSISTANT -> AssistantBubble(
                        message = message,
                        onPreview = onPreviewTrajectory,
                        onExecute = onExecuteTrajectory,
                        onPreviewCandidate = { candidate ->
                            // If inline TUM text is available, show lightweight 3D preview.
                            // Otherwise fall back to the heavy scene viewer.
                            if (!candidate.tumText.isNullOrBlank()) {
                                preview3DCandidate = candidate
                            } else {
                                onPreviewCandidate(candidate)
                            }
                        },
                        onSelectCandidate = { viewModel.selectCandidate(it.id) },
                        onExampleTap = { example -> inputText = example },
                        onRefine = { refinement -> inputText = refinement },
                        selectedCandidateId = selectedCandidateId
                    )
                    ChatRole.SYSTEM -> SystemMessage(message)
                }
            }

            if (isStreaming) {
                item { StreamingIndicator() }
            }
        }

        if (messages.isEmpty() ||
            (messages.size == 1 && messages.first().role == ChatRole.ASSISTANT && messages.first().promptHint != null)) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                PromptTemplateChipRow(onTemplateTap = { prompt -> inputText = prompt })
            }
        }

        currentSelected?.let { candidate ->
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SelectedCandidateBanner(
                    candidate = candidate,
                    onExecute = { onExecuteCandidate(candidate) },
                    onClear = { viewModel.clearCandidateSelection() }
                )
            }
        }

        // Voice error hint (auto-clears after 2 s via LaunchedEffect above)
        voiceErrorText?.let { msg ->
            Text(
                text = msg,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = Color(0xFFEF5350),
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText, buildAttachments())
                    inputText = ""
                }
            },
            canSend = connectionState == ChatConnectionState.CONNECTED && !isStreaming,
            isStreaming = isStreaming,
            onCancel = { viewModel.cancelGeneration() },
            snapshotAvailable = videoConfig != null,
            voiceState = voiceState,
            onVoiceStart = { voiceRecognizer.start("zh-CN") },
            onVoiceStop = { voiceRecognizer.stop() }
        )
    }

    if (videoConfig == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF101014)),
            content = { chatColumn() }
        )
    } else {
        // Split-pane: video left ~55%, chat right ~45%. Landscape tablet.
        // The video pane is black (letterbox); the inner surface keeps a
        // fixed 16:9 aspect so SurfaceView doesn't stretch the frame when
        // the pane's aspect doesn't match the stream.
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFF101014))) {
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    VideoPreviewContent(
                        showBitmapFrame = videoConfig.showBitmapFrame,
                        videoBitmap = videoConfig.videoBitmap,
                        contentDescription = stringResource(R.string.chat_ai_director),
                        onVideoSurfaceReady = videoConfig.onVideoSurfaceReady,
                        onVideoSurfaceDestroyed = videoConfig.onVideoSurfaceDestroyed
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.45f).fillMaxHeight(),
                content = { chatColumn() }
            )
        }
    }

    // 3D trajectory preview fullscreen overlay
    preview3DCandidate?.let { candidate ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { preview3DCandidate = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true
            )
        ) {
            Trajectory3DPreview(
                tumText = candidate.tumText ?: "",
                subjectStand = candidate.subjectStand,
                candidateName = candidate.name,
                onDismiss = { preview3DCandidate = null }
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    connectionState: ChatConnectionState,
    onClose: (() -> Unit)?,
    onReconnect: () -> Unit
) {
    val connectedText = stringResource(R.string.connection_status_connected)
    val connectingText = stringResource(R.string.connection_status_connecting) + "..."
    val reconnectingText = stringResource(R.string.connection_reconnect) + "ing..."
    val disconnectedText = stringResource(R.string.connection_status_disconnected)
    val (statusColor, statusText) = when (connectionState) {
        ChatConnectionState.CONNECTED -> Color(0xFF66BB6A) to connectedText
        ChatConnectionState.CONNECTING -> Color(0xFFFFA726) to connectingText
        ChatConnectionState.RECONNECTING -> Color(0xFFFFA726) to reconnectingText
        ChatConnectionState.DISCONNECTED -> Color(0xFFEF5350) to disconnectedText
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1B1B1F)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_ai_director), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(statusText, color = statusColor, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (connectionState == ChatConnectionState.DISCONNECTED) {
                    IconButton(onClick = onReconnect) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.chat_reconnect), tint = Color.White)
                    }
                }
                onClose?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_close), tint = Color.White)
                    }
                }
            }
        }
    }
}

// ── Message bubbles ───────────────────────────────────────────────

@Composable
private fun UserBubble(message: ChatMessageItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = 500.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            message.userSnapshot?.let { snap ->
                SnapshotThumbnailChip(snap)
            }
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = UserBubbleColor
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SnapshotThumbnailChip(snapshot: SnapshotAttachment) {
    val bitmap = remember(snapshot.snapshotId, snapshot.dataBase64) {
        decodeBase64Jpeg(snapshot.dataBase64)
    }
    if (bitmap != null) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 120.dp, height = 72.dp)
            )
        }
    }
}

private fun decodeBase64Jpeg(data: String?): Bitmap? {
    if (data.isNullOrBlank()) return null
    return runCatching {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
private fun AssistantBubble(
    message: ChatMessageItem,
    onPreview: (TrajectoryAttachment) -> Unit,
    onExecute: (TrajectoryAttachment) -> Unit,
    onPreviewCandidate: (TrajectoryCandidate) -> Unit,
    onSelectCandidate: (TrajectoryCandidate) -> Unit,
    onExampleTap: (String) -> Unit,
    onRefine: (String) -> Unit,
    selectedCandidateId: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Text content — only if this message has a visible body and isn't a
        // pure prompt-hint card (hint messages stash the title in `content`
        // but render via the card, not as a plain bubble).
        if (message.content.isNotBlank() && message.promptHint == null) {
            Surface(
                modifier = Modifier.widthIn(max = 500.dp),
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = AssistantBubbleColor
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = StudioChrome.textPrimary,
                        fontSize = 14.sp
                    )
                    if (message.status == MessageStatus.STREAMING) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("●", color = StreamingDotColor, fontSize = 12.sp)
                    }
                }
            }
        }

        // v2: prompt hint card
        message.promptHint?.let { hint ->
            PromptHintCard(
                hint = hint,
                onExampleTap = onExampleTap
            )
        }

        // v2: candidate carousel + quick-refine chips
        message.candidateSet?.let { set ->
            CandidateCarousel(
                candidateSet = set,
                selectedCandidateId = selectedCandidateId,
                onPreview = onPreviewCandidate,
                onSelect = onSelectCandidate
            )
            QuickRefineChips(onRefine = onRefine)
        }

        // v1: legacy single trajectory attachments
        message.attachments
            .filter { it.attachmentType == "trajectory" && it.trajectory != null }
            .forEach { attachment ->
                val traj = attachment.trajectory ?: return@forEach
                TrajectoryCard(
                    trajectory = traj,
                    onPreview = { onPreview(traj) },
                    onExecute = { onExecute(traj) }
                )
            }

        // Generic action buttons (if no trajectory-specific actions above)
        if (message.actions.isNotEmpty() && message.attachments.isEmpty() && message.candidateSet == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.actions.forEach { action ->
                    OutlinedButton(
                        onClick = { /* TODO: handle generic actions */ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioChrome.accentBlue)
                    ) {
                        Text(action.label, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrajectoryCard(
    trajectory: TrajectoryAttachment,
    onPreview: () -> Unit,
    onExecute: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 400.dp),
        shape = RoundedCornerShape(12.dp),
        color = TrajectoryCardColor
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = trajectory.name,
                color = Color(0xFF66BB6A),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                trajectory.durationSec?.let {
                    Text("%.0fs".format(it), color = Color(0xFF888888), fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                trajectory.frameCount?.let {
                    Text("$it frames", color = Color(0xFF888888), fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPreview,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A4A2A)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_preview_title), fontSize = 12.sp)
                }
                Button(
                    onClick = onExecute,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_open_in_run), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SystemMessage(message: ChatMessageItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = message.content,
            color = SystemColor,
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic
        )
    }
}

// ── Streaming indicator ───────────────────────────────────────────

@Composable
private fun StreamingIndicator() {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StreamingDotColor.copy(alpha = 0.6f))
            )
        }
    }
}

// ── Task progress ─────────────────────────────────────────────────

@Composable
private fun TaskProgressBar(task: TaskStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1A1A2E)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    task.message ?: task.status.replaceFirstChar { it.uppercase() },
                    color = Color(0xFFFFA726),
                    fontSize = 12.sp
                )
                task.progress?.let {
                    Text("${(it * 100).toInt()}%", color = Color(0xFFFFA726), fontSize = 12.sp)
                }
            }
            task.progress?.let {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color(0xFFFFA726),
                    trackColor = Color(0xFF333333)
                )
            }
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    isStreaming: Boolean,
    onCancel: () -> Unit,
    snapshotAvailable: Boolean = false,
    voiceState: com.recomo.common.chat.voice.VoiceState = com.recomo.common.chat.voice.VoiceState.Idle,
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1B1B1F),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (snapshotAvailable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StudioChrome.success)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_snapshot_attached_hint),
                        color = StudioChrome.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatVoiceButton(
                state = voiceState,
                onStart = onVoiceStart,
                onStop = onVoiceStop,
                enabled = !isStreaming
            )

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_describe_shot), color = Color(0xFF666666)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF555555),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = Color(0xFF2962FF)
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = false,
                maxLines = 3
            )

            if (isStreaming) {
                // Cancel button while streaming
                FilledIconButton(
                    onClick = onCancel,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFEF5350)
                    )
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_cancel), tint = Color.White)
                }
            } else {
                // Send button
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend && text.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = UserBubbleColor,
                        disabledContainerColor = Color(0xFF333333)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                }
            }
        }
        }
    }
}
