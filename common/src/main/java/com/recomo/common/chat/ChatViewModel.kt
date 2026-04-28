package com.recomo.common.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.preview.TrajectoryPreview
import com.recomo.common.preview.TrajectorySample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ChatViewModel"

data class PreviewTrajectoryState(
    val attachment: TrajectoryAttachment,
    val preview: TrajectoryPreview,
    val resolvedSessionId: String? = null,
    val resolvedSessionName: String? = null
)

/**
 * ViewModel for the AI chat conversation.
 *
 * Manages message history, WebSocket connection to the cloud chat server,
 * streaming assistant responses, and trajectory attachment handling.
 *
 * The transport (`WS_BRIDGE` vs `DIRECT_REST`) is resolved at
 * construction time via [ChatTransportConfigProvider], which the app
 * layer binds against user settings. Existing behaviour preserved:
 * default config is `WS_BRIDGE`, identical to pre-Hilt `ChatRepository()`.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    transportConfigProvider: ChatTransportConfigProvider
) : ViewModel() {

    val repository: ChatTransport = ChatTransportFactory.create(
        transportConfigProvider.currentConfig()
    )
    private val trajectoryResolver = TrajectoryResolver()

    // ── Observable state ──────────────────────────────────────────

    private val _messages = MutableStateFlow<List<ChatMessageItem>>(emptyList())
    val messages: StateFlow<List<ChatMessageItem>> = _messages.asStateFlow()

    val connectionState: StateFlow<ChatConnectionState> = repository.connectionState
    val conversationId: StateFlow<String?> = repository.conversationId

    /** Currently streaming message ID (if any). */
    private val _streamingMessageId = MutableStateFlow<String?>(null)
    val isStreaming: StateFlow<Boolean> = _streamingMessageId.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Active task progress (planning, generating, etc.). */
    private val _activeTask = MutableStateFlow<TaskStatus?>(null)
    val activeTask: StateFlow<TaskStatus?> = _activeTask.asStateFlow()

    /** Trajectory ready for 3D preview (set after download). */
    private val _previewTrajectory = MutableStateFlow<PreviewTrajectoryState?>(null)
    val previewTrajectory: StateFlow<PreviewTrajectoryState?> = _previewTrajectory.asStateFlow()

    // ── v2 state ──────────────────────────────────────────────────

    /** Latest prompt hint from the server (welcome or clarification). */
    private val _promptHint = MutableStateFlow<PromptHint?>(null)
    val promptHint: StateFlow<PromptHint?> = _promptHint.asStateFlow()

    /** Latest candidate set from the server (typically 5 trajectories). */
    private val _candidates = MutableStateFlow<CandidateSet?>(null)
    val candidates: StateFlow<CandidateSet?> = _candidates.asStateFlow()

    /** Currently selected candidate within the active set. */
    private val _selectedCandidateId = MutableStateFlow<String?>(null)
    val selectedCandidateId: StateFlow<String?> = _selectedCandidateId.asStateFlow()

    // Buffer for accumulating streaming chunks
    private var streamBuffer = StringBuilder()

    init {
        // Collect incoming server events
        viewModelScope.launch {
            repository.incomingEvents.collect { event ->
                handleEvent(event)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────

    fun connect(url: String, deviceId: String, robotProfile: String? = null, locationId: String? = null) {
        repository.connect(url, deviceId, robotProfile = robotProfile, locationId = locationId)
    }

    fun disconnect() {
        repository.disconnect()
    }

    /**
     * Send a user message. Adds it to local history immediately,
     * then sends to server.
     */
    fun sendMessage(content: String, attachments: UserAttachments? = null) {
        if (content.isBlank()) return

        val userMsg = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            content = content.trim(),
            userSnapshot = attachments?.snapshot
        )
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            repository.sendMessage(content.trim(), attachments = attachments)
        }
    }

    fun cancelGeneration() {
        viewModelScope.launch { repository.cancelGeneration() }
    }

    // ── v2 public API ─────────────────────────────────────────────

    /** v2: mark a candidate as the user's selection. Echoes to backend. */
    fun selectCandidate(candidateId: String) {
        val set = _candidates.value ?: return
        if (set.candidates.none { it.id == candidateId }) return
        _selectedCandidateId.value = candidateId
        // Reflect on the message that holds the candidate set so the UI can
        // render the chosen card with a highlight.
        val current = _messages.value.toMutableList()
        val idx = current.indexOfFirst { it.candidateSet?.messageId == set.messageId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(selectedCandidateId = candidateId)
            _messages.value = current
        }
        viewModelScope.launch {
            repository.selectCandidate(messageId = set.messageId, candidateId = candidateId)
        }
    }

    /** v2: ask the backend for a refined batch ("shorter", "more dramatic"). */
    fun refinePrompt(refinement: String) {
        val parent = _candidates.value ?: return
        viewModelScope.launch {
            repository.refinePrompt(parentMessageId = parent.messageId, refinement = refinement)
        }
    }

    /** v2: clear the local "selected" state (user tapped "Pick another"). */
    fun clearCandidateSelection() {
        _selectedCandidateId.value = null
        val set = _candidates.value ?: return
        val current = _messages.value.toMutableList()
        val idx = current.indexOfFirst { it.candidateSet?.messageId == set.messageId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(selectedCandidateId = null)
            _messages.value = current
        }
    }

    /** v2: convenience — return the candidate the user picked, or null. */
    fun selectedCandidate(): TrajectoryCandidate? {
        val set = _candidates.value ?: return null
        val id = _selectedCandidateId.value ?: return null
        return set.candidates.firstOrNull { it.id == id }
    }

    /** Download a trajectory attachment and return the result. */
    fun downloadTrajectory(attachment: TrajectoryAttachment, onResult: (TrajectoryDownloadResult) -> Unit) {
        viewModelScope.launch {
            val result = trajectoryResolver.downloadTrajectory(attachment.downloadUrl)
            onResult(result)
        }
    }

    /**
     * Fetch a raw text body (used for TUM trajectories served by the
     * bridge). The viewer reads these directly via `InlineTum` —
     * avoids the planar-flattening that happens when we route through
     * the session-JSON schema.
     */
    fun downloadText(url: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(trajectoryResolver.downloadText(url))
        }
    }

    /** Download trajectory and open 3D preview. */
    fun previewTrajectory(attachment: TrajectoryAttachment) {
        viewModelScope.launch {
            val result = trajectoryResolver.downloadTrajectory(attachment.downloadUrl)
            if (result is TrajectoryDownloadResult.Success) {
                val preview = convertToPreview(result)
                if (preview != null) {
                    _previewTrajectory.value = PreviewTrajectoryState(
                        attachment = attachment,
                        preview = preview,
                        resolvedSessionId = result.sessionId,
                        resolvedSessionName = result.sessionName
                    )
                    Log.i(TAG, "Preview ready: ${preview.samples.size} samples")
                } else {
                    addSystemMessage("Failed to parse trajectory for preview")
                }
            } else if (result is TrajectoryDownloadResult.Error) {
                addSystemMessage("Download failed: ${result.message}")
            }
        }
    }

    /** Close the 3D preview. */
    fun closePreview() {
        _previewTrajectory.value = null
    }

    /** Convert downloaded JSON frames to TrajectoryPreview. */
    private fun convertToPreview(result: TrajectoryDownloadResult.Success): TrajectoryPreview? {
        return try {
            val samples = when (result.payloadFormat) {
                TrajectoryPayloadFormat.Frames -> result.framesJson.mapNotNull { elem ->
                    val frame = elem.jsonObject
                    val t = frame["timestamp"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val base = frame["base"]?.jsonObject
                    val bx = base?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val by = base?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val byaw = base?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val armQ = frame["arm_q"]?.jsonArray?.map {
                        it.jsonPrimitive.doubleOrNull ?: 0.0
                    } ?: emptyList()
                    val gimbal = frame["gimbal"]?.jsonObject
                    val gy = gimbal?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val gp = gimbal?.get("pitch")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    TrajectorySample(
                        tSec = t, baseX = bx, baseY = by, baseYaw = byaw,
                        armQ = armQ, gimbalQ = listOf(gy, gp, 0.0)
                    )
                }

                TrajectoryPayloadFormat.Foi -> result.framesJson.mapIndexedNotNull { index, elem ->
                    val frame = elem.jsonObject
                    val t = (frame["timestamp_ms"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 1000.0
                    val poi = frame["poi"]?.jsonObject
                    val base = poi?.get("base_pose")?.jsonObject ?: poi
                    val bx = base?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val by = base?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val byaw = base?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val armQ = frame["arm_q"]?.jsonArray?.map {
                        it.jsonPrimitive.doubleOrNull ?: 0.0
                    } ?: emptyList()
                    val gimbalQ = frame["gimbal_q"]?.jsonArray?.map {
                        it.jsonPrimitive.doubleOrNull ?: 0.0
                    } ?: emptyList()
                    TrajectorySample(
                        tSec = if (t > 0.0) t else index.toDouble(),
                        baseX = bx,
                        baseY = by,
                        baseYaw = byaw,
                        armQ = armQ,
                        gimbalQ = when {
                            gimbalQ.size >= 3 -> gimbalQ.take(3)
                            gimbalQ.size == 2 -> gimbalQ + listOf(0.0)
                            gimbalQ.size == 1 -> gimbalQ + listOf(0.0, 0.0)
                            else -> listOf(0.0, 0.0, 0.0)
                        }
                    )
                }
            }
            if (samples.isEmpty()) return null
            val duration = samples.last().tSec - samples.first().tSec
            TrajectoryPreview(samples = samples, keyframes = emptyList(), totalDurationSec = duration)
        } catch (e: Exception) {
            Log.e(TAG, "convertToPreview failed: ${e.message}", e)
            null
        }
    }

    /** Clear conversation history (local only). */
    fun clearMessages() {
        _messages.value = emptyList()
        _streamingMessageId.value = null
        _activeTask.value = null
        streamBuffer.clear()
    }

    // ── Event handling ────────────────────────────────────────────

    private fun handleEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.Connected -> {
                Log.i(TAG, "Connected to conversation: ${event.msg.conversationId}")
                // Optionally add a system message
                addSystemMessage("Connected to AI assistant")
            }

            is ServerEvent.Chunk -> {
                val msgId = event.msg.messageId
                if (_streamingMessageId.value != msgId) {
                    // New streaming message — create placeholder
                    _streamingMessageId.value = msgId
                    streamBuffer.clear()
                }
                streamBuffer.append(event.msg.delta)
                updateOrAddAssistantMessage(
                    id = msgId,
                    content = streamBuffer.toString(),
                    status = MessageStatus.STREAMING
                )
            }

            is ServerEvent.Done -> {
                _streamingMessageId.value = null
                streamBuffer.clear()
                updateOrAddAssistantMessage(
                    id = event.msg.messageId,
                    content = event.msg.content,
                    status = MessageStatus.COMPLETE,
                    attachments = event.msg.attachments,
                    actions = event.msg.actions
                )
            }

            is ServerEvent.TaskProgress -> {
                _activeTask.value = event.msg
                if (event.msg.status == "done" || event.msg.status == "failed") {
                    // Clear after a short display period
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        if (_activeTask.value?.taskId == event.msg.taskId) {
                            _activeTask.value = null
                        }
                    }
                }
            }

            is ServerEvent.TrajectoryAvailable -> {
                // Update the relevant message with trajectory attachment
                val traj = event.msg.trajectory
                val attachment = ChatAttachment(
                    attachmentType = "trajectory",
                    trajectory = traj
                )
                val current = _messages.value.toMutableList()
                val idx = current.indexOfFirst { it.id == event.msg.messageId }
                if (idx >= 0) {
                    current[idx] = current[idx].copy(
                        attachments = current[idx].attachments + attachment
                    )
                    _messages.value = current
                }
            }

            is ServerEvent.ServerError -> {
                addSystemMessage("Error: ${event.msg.message}")
            }

            is ServerEvent.ConnectionError -> {
                addSystemMessage("Connection lost: ${event.reason}")
            }

            // ── v2 events ─────────────────────────────────────────
            is ServerEvent.PromptHintReceived -> {
                _promptHint.value = event.msg
                // Surface as a dedicated assistant message so it's anchored
                // in the scroll position; the UI renders the hint card from
                // the message's `promptHint` field rather than the text body.
                val hintMsg = ChatMessageItem(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    content = event.msg.title,
                    promptHint = event.msg
                )
                _messages.value = _messages.value + hintMsg
                Log.i(TAG, "Prompt hint: ${event.msg.title}")
            }

            is ServerEvent.CandidatesReady -> {
                _candidates.value = event.msg
                _selectedCandidateId.value = null
                // If there's an in-flight assistant text message with the same
                // message_id, attach the candidates to it. Otherwise create a
                // fresh assistant message holding the carousel.
                val current = _messages.value.toMutableList()
                val idx = current.indexOfFirst { it.id == event.msg.messageId }
                if (idx >= 0) {
                    current[idx] = current[idx].copy(candidateSet = event.msg)
                } else {
                    current += ChatMessageItem(
                        id = event.msg.messageId,
                        role = ChatRole.ASSISTANT,
                        content = "",
                        candidateSet = event.msg
                    )
                }
                _messages.value = current
                Log.i(TAG, "Candidates ready: ${event.msg.candidates.size} entries")
            }
        }
    }

    private fun updateOrAddAssistantMessage(
        id: String,
        content: String,
        status: MessageStatus,
        attachments: List<ChatAttachment> = emptyList(),
        actions: List<ChatAction> = emptyList()
    ) {
        val current = _messages.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            // Preserve v2 fields (candidateSet, promptHint, selectedCandidateId)
            // that may have been attached between chunks/done arriving —
            // otherwise a later assistant_done would wipe an already-displayed
            // candidate carousel.
            current[idx] = current[idx].copy(
                role = ChatRole.ASSISTANT,
                content = content,
                status = status,
                attachments = attachments,
                actions = actions
            )
        } else {
            current.add(
                ChatMessageItem(
                    id = id,
                    role = ChatRole.ASSISTANT,
                    content = content,
                    status = status,
                    attachments = attachments,
                    actions = actions
                )
            )
        }
        _messages.value = current
    }

    private fun addSystemMessage(text: String) {
        val msg = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = ChatRole.SYSTEM,
            content = text
        )
        _messages.value = _messages.value + msg
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
