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
    const val SELECT_CANDIDATE = "select_candidate" // v2: user picked a candidate (analytics)
    const val REFINE_PROMPT = "refine_prompt"       // v2: "give me 5 more, shorter"

    // Server → Client
    const val CONNECTED = "connected"      // Handshake ack
    const val ASSISTANT_CHUNK = "assistant_chunk"   // Streaming text delta
    const val ASSISTANT_DONE = "assistant_done"     // Full message complete
    const val TASK_STATUS = "task_status"           // Async task progress
    const val TRAJECTORY_READY = "trajectory_ready" // v1: single trajectory available
    const val PROMPT_HINT = "prompt_hint"           // v2: server-side prompting guidance
    const val CANDIDATES = "candidates"             // v2: N-candidate result set
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
    val context: ChatContext? = null,
    // v2.1: VLM-style attachments (video snapshot + robot state).
    // Both optional; servers that don't support VLM ignore them.
    val attachments: UserAttachments? = null
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

// ════════════════════════════════════════════════════════════════════
// Protocol v2 — see docs/AI_CHAT_PROTOCOL.md
// New message types preserve back-compat with v1 servers. Clients negotiate
// via the capabilities field on the `connected` handshake.
// ════════════════════════════════════════════════════════════════════

// ── v2: Capability flags on the connected handshake ───────────────

@Serializable
data class ChatCapabilities(
    @SerialName("candidates_v2") val candidatesV2: Boolean = false,
    @SerialName("prompt_hint") val promptHint: Boolean = false,
    @SerialName("refine_prompt") val refinePrompt: Boolean = false,
    @SerialName("select_candidate") val selectCandidate: Boolean = false,
    @SerialName("scene_preview") val scenePreview: Boolean = false,
    // v2.1: server accepts VLM attachments (snapshot + robot_state) on send_message.
    @SerialName("vlm_attachments") val vlmAttachments: Boolean = false
)

/**
 * v2 server → client. The `connected` ack is shared with v1 (`ChatConnected`)
 * but real v2 servers also include a `capabilities` field — see
 * [ChatConnectedV2]. v1 servers omit it; the client falls back to v1 mode
 * when `capabilities.candidatesV2 == false` or absent.
 */
@Serializable
data class ChatConnectedV2(
    val type: String = ChatMessageType.CONNECTED,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("server_version") val serverVersion: String? = null,
    val capabilities: ChatCapabilities = ChatCapabilities()
)

// ── v2: Server → client messages ──────────────────────────────────

/**
 * The dialogue agent wants to coach the user on how to prompt. Sent on:
 *   - first connect to a fresh conversation (welcome / template hints)
 *   - any prompt that's too vague to retrieve from the database
 *
 * Non-blocking — the client renders this as a hint card and the user
 * may ignore it.
 */
@Serializable
data class PromptHint(
    val type: String = ChatMessageType.PROMPT_HINT,
    @SerialName("conversation_id") val conversationId: String,
    val title: String,
    val guidance: String,
    @SerialName("required_fields") val requiredFields: List<String> = emptyList(),
    @SerialName("example_prompts") val examplePrompts: List<String> = emptyList(),
    @SerialName("template_ids") val templateIds: List<String> = emptyList()
)

/**
 * The headline result of a successful generation. Replaces v1's
 * `assistant_done.attachments[0].trajectory` path. Each candidate carries
 * everything the client needs to preview (scene SPZ + anchor) AND execute
 * (download_url + scene_type) the trajectory.
 */
@Serializable
data class CandidateSet(
    val type: String = ChatMessageType.CANDIDATES,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("prompt_echo") val promptEcho: String,
    val candidates: List<TrajectoryCandidate>
)

@Serializable
data class TrajectoryCandidate(
    val id: String,
    val rank: Int,
    val name: String,
    val caption: String,
    val tags: List<String> = emptyList(),
    @SerialName("duration_sec") val durationSec: Double,
    @SerialName("frame_count") val frameCount: Int,
    val confidence: Float = 0f,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val scene: SceneRef? = null,
    @SerialName("home_point") val homePoint: HomePointRef? = null,
    @SerialName("sim_result") val simResult: SimResult? = null,
    /**
     * `"LivePnC"` or `"SimpleTrack"`. If absent, the client infers from
     * `scene` presence (scene present → LivePnC, scene absent → SimpleTrack).
     */
    @SerialName("scene_type") val sceneType: String? = null,
    /**
     * URL to a `.tum` camera trajectory for 3D preview. When null, the
     * client falls back to [downloadUrl] (which may return JSON or TUM).
     */
    @SerialName("preview_url") val previewUrl: String? = null,
    /**
     * Optional Orin-native execution block. When present (+ `simResult.feasible`
     * is not false), the Execute path POSTs the JSON at [executionRef.executeUrl]
     * to Orin's trajectory-deploy service, then drives the downstream pipeline
     * via `FixedPositionCmd("select", <filename-stem>)` — the same route
     * CopyStyle uses. When absent, the candidate is preview-only.
     */
    @SerialName("execution_ref") val executionRef: ExecutionRef? = null,
    /**
     * Inline TUM camera trajectory text for the mini-preview Canvas.
     * Populated by [DirectRestTransport] from `traj_txt.text`; null when
     * the WS-bridge path is used (bridge doesn't forward inline text).
     * Format: one line per frame, `timestamp x y z qx qy qz qw`.
     */
    @kotlinx.serialization.Transient val tumText: String? = null,
    /**
     * Subject standing position relative to the robot. Populated from
     * the enriched `:9070` endpoint's `subject_stand` field.
     */
    @kotlinx.serialization.Transient val subjectStand: SubjectStand? = null
)

/**
 * Everything the tablet needs to drop a cloud-authored trajectory onto
 * Orin and run it through the fixed_position pipeline.
 */
@Serializable
data class ExecutionRef(
    /** Where to download the full Orin-native JSON from the cloud. */
    @SerialName("execute_url") val executeUrl: String,
    /** Filename the JSON lands under on Orin. Must match `^[A-Za-z0-9_.-]+\.json$`. */
    @SerialName("execute_filename") val executeFilename: String,
    /** "LivePnC" | "SimpleTrack" — mirrors [TrajectoryCandidate.sceneType]. */
    @SerialName("scene_type") val sceneType: String = "LivePnC",
    /** SLAM map the embedded POI targets (for MapMatchDialog hinting). */
    @SerialName("linked_map") val linkedMap: String? = null,
    /** Overwrite an existing same-named file on Orin (default true). */
    val overwrite: Boolean = true
)

@Serializable
data class SceneRef(
    @SerialName("scene_id") val sceneId: String,
    @SerialName("spz_url") val spzUrl: String,
    @SerialName("anchor_pose") val anchorPose: AnchorPoseDto
)

@Serializable
data class AnchorPoseDto(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val qx: Double = 0.0,
    val qy: Double = 0.0,
    val qz: Double = 0.0,
    val qw: Double = 1.0
)

@Serializable
data class HomePointRef(
    val x: Double,
    val y: Double,
    val yaw: Double
)

@Serializable
data class SubjectStand(
    @SerialName("forward_m") val forwardM: Double = 0.0,
    @SerialName("left_m") val leftM: Double = 0.0,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("bearing_deg") val bearingDeg: Double = 0.0,
    val confident: Boolean = false,
    val instruction: String = ""
)

@Serializable
data class SimResult(
    val feasible: Boolean,
    @SerialName("max_base_speed") val maxBaseSpeed: Double? = null,
    @SerialName("max_arm_jerk") val maxArmJerk: Double? = null,
    val warnings: List<String> = emptyList()
)

/**
 * v2 error. Adds a `code` enum (see protocol §5.7) and a `retryable` flag
 * so the client can show a Retry button only when it makes sense.
 */
@Serializable
data class ChatErrorV2(
    val type: String = ChatMessageType.ERROR,
    val code: String,
    val message: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    val retryable: Boolean = false
)

// ── v2: Client → server messages ──────────────────────────────────

/** User picked one candidate from a set. Fire-and-forget; for backend analytics. */
@Serializable
data class SelectCandidate(
    val type: String = ChatMessageType.SELECT_CANDIDATE,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("candidate_id") val candidateId: String,
    @SerialName("selected_at_ms") val selectedAtMs: Long = System.currentTimeMillis()
)

/** "Give me another 5, but…" — lighter than re-typing the whole prompt. */
@Serializable
data class RefinePrompt(
    val type: String = ChatMessageType.REFINE_PROMPT,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("parent_message_id") val parentMessageId: String,
    val refinement: String
)

// ════════════════════════════════════════════════════════════════════
// Protocol v2.1 — VLM user-message attachments
// See docs/AI_CHAT_PROTOCOL.md §6 (v2.1). Additive over v2.
// ════════════════════════════════════════════════════════════════════

/**
 * Attachments the client sends with a user message so the backend can answer
 * in a vision-language-model style (image + text + structured context).
 * Both fields optional; omit either to skip.
 */
@Serializable
data class UserAttachments(
    val snapshot: SnapshotAttachment? = null,
    @SerialName("robot_state") val robotState: RobotStateSnapshot? = null
)

/**
 * A still frame from the live video feed at send-time.
 *
 * Transport: MVP uses inline base64 JPEG (`data_base64`). Production should
 * prefer HTTP upload and reference by `url` — servers accept either.
 */
@Serializable
data class SnapshotAttachment(
    @SerialName("snapshot_id") val snapshotId: String,
    val mime: String = "image/jpeg",
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("data_base64") val dataBase64: String? = null,
    val url: String? = null,
    @SerialName("captured_at_ms") val capturedAtMs: Long
)

/** Lean subset of RobotState the server needs to ground VLM answers. */
@Serializable
data class RobotStateSnapshot(
    @SerialName("arm_q_deg") val armQDeg: List<Double>? = null,
    val gimbal: GimbalSnapshot? = null,
    @SerialName("base_pose") val basePose: BasePoseSnapshot? = null,
    val map: MapSnapshot? = null,
    val mode: String? = null,
    @SerialName("captured_at_ms") val capturedAtMs: Long
)

@Serializable
data class GimbalSnapshot(
    val yaw: Double? = null,
    val pitch: Double? = null,
    val roll: Double? = null
)

@Serializable
data class BasePoseSnapshot(
    val x: Double,
    val y: Double,
    val yaw: Double
)

@Serializable
data class MapSnapshot(
    @SerialName("location_id") val locationId: String? = null,
    val name: String? = null,
    @SerialName("poi_name") val poiName: String? = null
)

// ── Local UI state ────────────────────────────────────────────────

/** Role in conversation. */
enum class ChatRole { USER, ASSISTANT, SYSTEM }

/** Status of assistant message generation. */
enum class MessageStatus { COMPLETE, STREAMING, ERROR }

/**
 * A single message in the conversation (local representation).
 * Includes both user and assistant messages.
 *
 * v2: messages may carry a [candidateSet] (5 candidates from CINVLA) or a
 * [promptHint] (server-side prompting guidance). These are rendered as
 * cards/carousels in addition to the text bubble.
 */
data class ChatMessageItem(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val attachments: List<ChatAttachment> = emptyList(),
    val actions: List<ChatAction> = emptyList(),
    // v2 additions
    val candidateSet: CandidateSet? = null,
    val promptHint: PromptHint? = null,
    val selectedCandidateId: String? = null,
    // v2.1: snapshot the user sent with this message (local echo for UI chip)
    val userSnapshot: SnapshotAttachment? = null
)
