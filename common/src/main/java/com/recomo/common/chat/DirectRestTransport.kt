package com.recomo.common.chat

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val TAG = "DirectRestTransport"

/**
 * Direct-to-cloud REST transport. Replaces the Termux `bridge.py` +
 * SSH-tunnel pathway once the cloud has a public IP.
 *
 * This impl maps wanqiang's one-shot REST `/chat` response onto the
 * v2 WebSocket event stream the rest of the app expects. The event
 * synthesis mirrors `server/cloud_bridge/bridge.py:_handle_send_message`
 * line-for-line so behaviour stays identical through the cutover.
 *
 * Still a skeleton: the REST endpoints are live but `baseUrl` defaults
 * to a placeholder until wanqiang's public endpoint lands. Attach
 * against a local mock or the real IP via [DirectRestConfig.baseUrl].
 *
 * Non-streaming responses and one-shot REST mean a few v2 client→server
 * calls become no-ops (cancelGeneration, refinePrompt). They log at
 * warn so regressions are visible during soak.
 */
class DirectRestTransport(
    private val config: DirectRestConfig
) : ChatTransport {

    private val client = HttpClient(CIO) {
        engine { proxy = null }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    override val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    override val incomingEvents: SharedFlow<ServerEvent> = _incomingEvents.asSharedFlow()

    @Volatile override var serverCapabilities: ChatCapabilities = ChatCapabilities(
        candidatesV2 = true,
        promptHint = true,
        selectCandidate = true,
        scenePreview = true,
        refinePrompt = false,
        vlmAttachments = false
    )
        private set

    @Volatile private var cloudSessionId: String? = null
    @Volatile private var deviceId: String? = null

    // ── ChatTransport ────────────────────────────────────────────

    override fun connect(
        url: String,
        deviceId: String,
        existingConversationId: String?,
        robotProfile: String?,
        locationId: String?
    ) {
        // The "url" parameter is ignored here — we use [config.baseUrl].
        // Keeping it in the signature so callers can swap transports
        // without code changes. `existingConversationId` doubles as the
        // cloud session id for continuity across reconnects.
        this.deviceId = deviceId
        cloudSessionId = existingConversationId
        val conv = existingConversationId ?: "conv_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        _conversationId.value = conv
        _connectionState.value = ChatConnectionState.CONNECTED
        scope.launch {
            _incomingEvents.emit(
                ServerEvent.Connected(
                    ChatConnected(
                        conversationId = conv,
                        serverVersion = "direct-rest/1.0"
                    )
                )
            )
            // No separate /guide call on first connect yet — wanqiang's
            // note (§3) has them returning welcome hint inline. When that
            // lands, fetch + emit PromptHintReceived here.
        }
        Log.i(TAG, "connected (direct) conv=$conv baseUrl=${config.baseUrl}")
    }

    override fun disconnect() {
        scope.coroutineContext[Job]?.cancelChildren()
        _connectionState.value = ChatConnectionState.DISCONNECTED
    }

    override suspend fun sendMessage(
        content: String,
        context: ChatContext?,
        attachments: UserAttachments?
    ) {
        val convId = _conversationId.value
        if (convId == null) {
            Log.w(TAG, "sendMessage before connect — ignored")
            return
        }
        val taskId = "task_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        emitTask(convId, taskId, "planning", 0.1f)
        emitTask(convId, taskId, "generating", 0.4f)

        val resp = runCatching { postChat(convId, content, attachments) }
            .onFailure { Log.w(TAG, "cloud /chat failed: ${it.message}") }
            .getOrNull()

        if (resp == null) {
            _incomingEvents.emit(
                ServerEvent.ServerError(
                    ChatError(
                        code = "upstream_failed",
                        message = "Cloud /chat failed (check baseUrl=${config.baseUrl})",
                        conversationId = convId
                    )
                )
            )
            emitTask(convId, taskId, "failed", 1.0f)
            return
        }

        cloudSessionId = resp.sessionId ?: cloudSessionId

        val candidates = resp.trajectories.orEmpty().mapIndexed { idx, item ->
            toCandidate(item, rank = idx + 1, fallbackScene = config.defaultScene)
        }

        val assistantText = composeAssistantIntro(candidates.size, resp.assistantMessage)

        // 1) single chunk + done (mirror bridge.py behaviour)
        _incomingEvents.emit(
            ServerEvent.Chunk(
                AssistantChunk(
                    conversationId = convId,
                    messageId = messageId,
                    delta = assistantText
                )
            )
        )
        _incomingEvents.emit(
            ServerEvent.Done(
                AssistantDone(
                    conversationId = convId,
                    messageId = messageId,
                    content = assistantText
                )
            )
        )

        // 2) candidate set
        if (candidates.isNotEmpty()) {
            _incomingEvents.emit(
                ServerEvent.CandidatesReady(
                    CandidateSet(
                        conversationId = convId,
                        messageId = messageId,
                        promptEcho = content,
                        candidates = candidates
                    )
                )
            )
        }

        emitTask(convId, taskId, "complete", 1.0f)
    }

    override suspend fun cancelGeneration() {
        // No streaming → nothing to cancel. Kept as no-op for interface parity.
        Log.d(TAG, "cancelGeneration() — no-op in direct REST mode")
    }

    override suspend fun selectCandidate(messageId: String, candidateId: String) {
        // Fire-and-forget analytics. Swallow if cloud doesn't expose the route.
        val convId = _conversationId.value ?: return
        runCatching {
            client.post("${config.baseUrl.trimEnd('/')}/select_candidate") {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    SelectCandidate.serializer(),
                    SelectCandidate(
                        conversationId = convId,
                        messageId = messageId,
                        candidateId = candidateId
                    )
                ))
            }
        }.onFailure { Log.d(TAG, "select_candidate swallowed: ${it.message}") }
    }

    override suspend fun refinePrompt(parentMessageId: String, refinement: String) {
        Log.w(TAG, "refinePrompt() not yet supported on direct REST transport")
    }

    // ── Internal ─────────────────────────────────────────────────

    private suspend fun postChat(
        @Suppress("UNUSED_PARAMETER") convId: String,
        userText: String,
        attachments: UserAttachments?
    ): CloudChatResponse {
        val body = json.encodeToString(
            CloudChatRequest.serializer(),
            CloudChatRequest(
                sessionId = cloudSessionId,
                message = userText,
                topK = config.topK,
                attachments = attachments
            )
        )
        val text = client.post("${config.baseUrl.trimEnd('/')}/chat") {
            authHeader()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()
        return json.decodeFromString(CloudChatResponse.serializer(), text)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        config.authToken?.takeIf { it.isNotBlank() }?.let {
            header("Authorization", "Bearer $it")
        }
    }

    private suspend fun emitTask(convId: String, taskId: String, status: String, progress: Float) {
        _incomingEvents.emit(
            ServerEvent.TaskProgress(
                TaskStatus(
                    conversationId = convId,
                    taskId = taskId,
                    status = status,
                    progress = progress
                )
            )
        )
    }

    // Local candidate composition — mirrors bridge.py:_cloud_item_to_candidate
    private fun toCandidate(
        item: CloudTrajectoryItem,
        rank: Int,
        fallbackScene: SceneRef?
    ): TrajectoryCandidate {
        val pointCount = item.trajTxt?.pointCount ?: 0
        val duration = if (pointCount > 0) max(1.0, pointCount / 10.0) else 10.0
        val videoId = item.videoId ?: "?"
        val shotId = item.shotId ?: "?"
        val score = item.score ?: 0.0
        val tags = item.matchedKeywords.orEmpty().take(6)
        val captionBits = buildList {
            if (videoId != "?") add("$videoId/$shotId")
            item.trajTxt?.scaleFactor?.let { add("scale=$it") }
        }
        val shotTag = shotId.removePrefix("shot_")
        // Prefer cloud-provided display_name (e.g. "AIgen-20260416114722-0034")
        // which already carries timestamp+seq; fall back to composed name.
        val displayName = item.displayName?.takeIf { it.isNotBlank() }
            ?: if (videoId != "?") "aigen-$videoId-$shotTag" else "aigen-${"%02d".format(rank)}"
        val executeUrl = item.executeUrl ?: item.trajJsonUrl
        val previewUrl = item.previewUrl ?: item.trajTxtUrl
        val executionRef = executeUrl?.let {
            ExecutionRef(
                executeUrl = it,
                executeFilename = deriveExecuteFilename(it, videoId, shotId),
                sceneType = "LivePnC",
                overwrite = true
            )
        }
        val scene = item.scene?.toSceneRef() ?: fallbackScene
        val confidence = if (score > 1.0) (score / 100.0) else score
        return TrajectoryCandidate(
            id = "cloud_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            rank = rank,
            name = displayName,
            caption = captionBits.joinToString(" · "),
            tags = tags,
            durationSec = duration,
            frameCount = pointCount,
            confidence = max(0.0, min(1.0, confidence)).toFloat(),
            downloadUrl = previewUrl ?: executeUrl ?: "",
            previewUrl = previewUrl,
            thumbnailUrl = item.thumbnailUrl,
            scene = scene,
            sceneType = if (scene != null) "LivePnC" else "SimpleTrack",
            simResult = SimResult(feasible = item.feasible ?: (executionRef != null)),
            executionRef = executionRef,
            tumText = item.trajTxt?.text,
            subjectStand = item.subjectStand
        )
    }

    private fun deriveExecuteFilename(url: String, videoId: String, shotId: String): String {
        val last = url.substringAfterLast('/').substringBefore('?')
        if (last.matches(Regex("^[A-Za-z0-9_.-]+\\.json$"))) return last
        val tag = shotId.removePrefix("shot_")
        return "aigen_${videoId}_${tag}.json"
    }

    private fun composeAssistantIntro(candidateCount: Int, cloudText: String?): String {
        return if (candidateCount > 0) {
            "根据你的描述，我为你生成了 $candidateCount 条运镜候选。" +
                "点击预览查看镜头，点击在机器人上运行交给机器人执行。"
        } else {
            cloudText ?: "抱歉，这次没能找到合适的运镜。"
        }
    }
}

/** Runtime config for [DirectRestTransport]. */
data class DirectRestConfig(
    /** Public HTTPS base for wanqiang's REST API. Placeholder until IP lands. */
    val baseUrl: String = "http://PLACEHOLDER:9999",
    /** Optional bearer token — empty string or null disables. */
    val authToken: String? = null,
    /** Retrieval top_k on /chat. */
    val topK: Int = 5,
    /** Default scene the bridge used to inject; null to disable the fallback. */
    val defaultScene: SceneRef? = null
)

// ── Cloud wire types (wanqiang's REST contract) ─────────────────────

@Serializable
private data class CloudChatRequest(
    @SerialName("session_id") val sessionId: String? = null,
    val message: String,
    @SerialName("top_k") val topK: Int = 5,
    val attachments: UserAttachments? = null
)

@Serializable
private data class CloudChatResponse(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("assistant_message") val assistantMessage: String? = null,
    val keywords: List<String> = emptyList(),
    val trajectories: List<CloudTrajectoryItem>? = null
)

@Serializable
private data class CloudTrajectoryItem(
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("shot_id") val shotId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val score: Double? = null,
    @SerialName("matched_keywords") val matchedKeywords: List<String>? = null,
    @SerialName("traj_txt") val trajTxt: CloudTrajTxt? = null,
    @SerialName("execute_url") val executeUrl: String? = null,
    @SerialName("traj_json_url") val trajJsonUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("traj_txt_url") val trajTxtUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val scene: CloudSceneRef? = null,
    @SerialName("subject_stand") val subjectStand: SubjectStand? = null,
    val enriched: Boolean = false,
    val feasible: Boolean? = null
)

@Serializable
private data class CloudTrajTxt(
    val text: String? = null,
    @SerialName("point_count") val pointCount: Int? = null,
    @SerialName("scale_factor") val scaleFactor: Double? = null
)

@Serializable
private data class CloudSceneRef(
    @SerialName("scene_id") val sceneId: String,
    @SerialName("spz_url") val spzUrl: String,
    @SerialName("anchor_pose") val anchorPose: AnchorPoseDto? = null
) {
    fun toSceneRef() = SceneRef(
        sceneId = sceneId,
        spzUrl = spzUrl,
        anchorPose = anchorPose ?: AnchorPoseDto()
    )
}
