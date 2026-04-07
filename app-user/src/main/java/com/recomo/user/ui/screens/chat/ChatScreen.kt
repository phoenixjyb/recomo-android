package com.recomo.user.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.common.chat.*
import com.recomo.user.R

// ── Configuration ────────────────────────────────────────────────
private const val DEFAULT_CHAT_SERVER_URL = "ws://192.168.100.97:9110"

// ── Colors ────────────────────────────────────────────────────────
private val UserBubbleColor = Color(0xFF2962FF)
private val AssistantBubbleColor = Color(0xFF2A2A2F)
private val SystemColor = Color(0xFF666666)
private val TrajectoryCardColor = Color(0xFF1B3A1B)
private val StreamingDotColor = Color(0xFF66BB6A)

/**
 * Full-screen chat UI.
 *
 * @param viewModel ChatViewModel from :common
 * @param onPreviewTrajectory Called when user taps "Preview" on a trajectory attachment
 * @param onExecuteTrajectory Called when user taps "Run" on a trajectory attachment
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatServerUrl: String = DEFAULT_CHAT_SERVER_URL,
    onPreviewTrajectory: (TrajectoryAttachment) -> Unit = {},
    onExecuteTrajectory: (TrajectoryAttachment) -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val activeTask by viewModel.activeTask.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
    ) {
        // ── Header ──
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

        // ── Task progress bar ──
        activeTask?.let { task ->
            TaskProgressBar(task)
        }

        // ── Message list ──
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
                        onExecute = onExecuteTrajectory
                    )
                    ChatRole.SYSTEM -> SystemMessage(message)
                }
            }

            // Streaming indicator
            if (isStreaming) {
                item {
                    StreamingIndicator()
                }
            }
        }

        // ── Input bar ──
        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                }
            },
            canSend = connectionState == ChatConnectionState.CONNECTED && !isStreaming,
            isStreaming = isStreaming,
            onCancel = { viewModel.cancelGeneration() }
        )
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
        Surface(
            modifier = Modifier.widthIn(max = 500.dp),
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

@Composable
private fun AssistantBubble(
    message: ChatMessageItem,
    onPreview: (TrajectoryAttachment) -> Unit,
    onExecute: (TrajectoryAttachment) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Text content
        if (message.content.isNotBlank()) {
            Surface(
                modifier = Modifier.widthIn(max = 500.dp),
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = AssistantBubbleColor
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = Color(0xFFE0E0E0),
                        fontSize = 14.sp
                    )
                    if (message.status == MessageStatus.STREAMING) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("●", color = StreamingDotColor, fontSize = 12.sp)
                    }
                }
            }
        }

        // Trajectory attachments
        message.attachments
            .filter { it.attachmentType == "trajectory" && it.trajectory != null }
            .forEach { attachment ->
                val traj = attachment.trajectory ?: return@forEach
                Spacer(modifier = Modifier.height(6.dp))
                TrajectoryCard(
                    trajectory = traj,
                    onPreview = { onPreview(traj) },
                    onExecute = { onExecute(traj) }
                )
            }

        // Action buttons (if no trajectory-specific actions above)
        if (message.actions.isNotEmpty() && message.attachments.isEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.actions.forEach { action ->
                    OutlinedButton(
                        onClick = { /* TODO: handle generic actions */ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF90CAF9))
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
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1B1B1F),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
