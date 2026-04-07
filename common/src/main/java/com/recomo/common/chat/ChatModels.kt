package com.recomo.common.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ════════════════════════════════════════════════════════════════════
// Chat Protocol — shared contract between App and Cloud Chat Server
// Transport: WebSocket (JSON text frames)
// ════════════════════════════════════════════════════════════════════

// ── Envelope ──────────────────────────────────────────────────────

/**
 * Every WebSocket frame is a JSON object with a "type" discriminator.
 * Direction is determined by context (client→server vs server→client).
 */
object ChatMessageType {
    // Client → Server
    const val CONNECT = "connect"          // Initial handshake
    const val SEND_MESSAGE = "send_message"
    const val CANCEL = "cancel"            // Cancel in-progress generation

    // Server → Client
    const val CONNECTED = "connected"      // Handshake ack
    const val ASSISTANT_CHUNK = "assistant_chunk"   // Streaming text delta
    const val ASSISTANT_DONE = "assistant_done"     // Full message complete
    const val TASK_STATUS = "task_status"           // Async task progress
    const val TRAJECTORY_READY = "trajectory_ready" // Trajectory available
    const val ERROR = "error"
}

// ── Client → Server messages ──────────────────────────────────────

@Serializable
data class ChatConnect(
    val type: String = ChatMessageType.CONNECT,
    @SerialName("device_id") val deviceId: String,
    @SerialName("conversation_id") val conversationId: String? = null, // Resume existing
    @SerialName("robot_profile") val robotProfile: String? = null,
    @SerialName("location_id") val locationId: String? = null
)

@Serializable
data class ChatSendMessage(
    val type: String = ChatMessageType.SEND_MESSAGE,
    @SerialName("conversation_id") val conversationId: String,
    val content: String,
    val context: ChatContext? = null
)

@Serializable
data class ChatCancel(
    val type: String = ChatMessageType.CANCEL,
    @SerialName("conversation_id") val conversationId: String
)

@Serializable
data class ChatContext(
    @SerialName("location_id") val locationId: String? = null,
    @SerialName("loaded_session_id") val loadedSessionId: String? = null,
    @SerialName("robot_profile") val robotProfile: String? = null
)

// ── Server → Client messages ──────────────────────────────────────

@Serializable
data class ChatConnected(
    val type: String = ChatMessageType.CONNECTED,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("server_version") val serverVersion: String? = null
)

@Serializable
data class AssistantChunk(
    val type: String = ChatMessageType.ASSISTANT_CHUNK,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("message_id") val messageId: String,
    val delta: String                      // Incremental text
)

@Serializable
data class AssistantDone(
    val type: String = ChatMessageType.ASSISTANT_DONE,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("message_id") val messageId: String,
    val content: String,                   // Full final text
    val attachments: List<ChatAttachment> = emptyList(),
    val actions: List<ChatAction> = emptyList()
)

@Serializable
data class TaskStatus(
    val type: String = ChatMessageType.TASK_STATUS,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("task_id") val taskId: String,
    val status: String,                    // "planning", "generating", "uploading", "done", "failed"
    val progress: Float? = null,           // 0.0–1.0
    val message: String? = null
)

@Serializable
data class TrajectoryReady(
    val type: String = ChatMessageType.TRAJECTORY_READY,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("message_id") val messageId: String,
    val trajectory: TrajectoryAttachment
)

@Serializable
data class ChatError(
    val type: String = ChatMessageType.ERROR,
    val code: String,
    val message: String,
    @SerialName("conversation_id") val conversationId: String? = null
)

// ── Shared types ──────────────────────────────────────────────────

@Serializable
data class ChatAttachment(
    @SerialName("attachment_type") val attachmentType: String, // "trajectory", "image", "video"
    val trajectory: TrajectoryAttachment? = null
)

@Serializable
data class TrajectoryAttachment(
    @SerialName("trajectory_id") val trajectoryId: String,
    val name: String,
    @SerialName("duration_sec") val durationSec: Double? = null,
    @SerialName("frame_count") val frameCount: Int? = null,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class ChatAction(
    val action: String,    // "preview", "execute", "modify", "retry"
    val label: String,
    @SerialName("trajectory_id") val trajectoryId: String? = null
)

// ── Local UI state ────────────────────────────────────────────────

/** Role in conversation. */
enum class ChatRole { USER, ASSISTANT, SYSTEM }

/** Status of assistant message generation. */
enum class MessageStatus { COMPLETE, STREAMING, ERROR }

/**
 * A single message in the conversation (local representation).
 * Includes both user and assistant messages.
 */
data class ChatMessageItem(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val attachments: List<ChatAttachment> = emptyList(),
    val actions: List<ChatAction> = emptyList()
)
