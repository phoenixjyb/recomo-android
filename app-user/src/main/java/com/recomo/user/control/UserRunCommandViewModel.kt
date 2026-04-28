package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.recomo.common.chat.BasePoseSnapshot
import com.recomo.common.chat.ExecutionRef
import com.recomo.common.chat.GimbalSnapshot
import com.recomo.common.chat.MapSnapshot
import com.recomo.common.chat.RobotStateSnapshot
import com.recomo.common.chat.TrajectoryAttachment
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.FrameRecord
import com.recomo.common.model.SessionDetail
import com.recomo.common.network.OrinGatewayClient
import com.recomo.common.preview.TrajectoryInterpolator
import com.recomo.user.data.UserSettingsRepository
import com.recomo.user.data.trajectory.LocalTrajectoryFrameRecord
import com.recomo.user.data.trajectory.LocalTrajectorySessionDetail
import com.recomo.user.data.trajectory.LocalTrajectorySessionRepository
import com.recomo.user.data.trajectory.LocalTrajectorySessionSourceKind
import com.recomo.user.data.trajectory.LocalTrajectorySessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val RUN_TTL_MS = 1000
private const val SIMPLE_TRACK_MODE = "simple_track"
private const val LIVE_PNC_MODE = "live_pnc"
private const val STATUS_LABEL_TTL_MS = 2500L

@HiltViewModel
class UserRunCommandViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient,
    private val userSettingsRepository: UserSettingsRepository,
    private val localTrajectorySessionRepository: LocalTrajectorySessionRepository
) : ViewModel() {
    private val seqId = AtomicLong(1)
    private val _selectedTrajectory = MutableStateFlow<String?>(null)
    private val _sceneType = MutableStateFlow(SceneType.Unknown)
    private val _statusOverrideLabel = MutableStateFlow<String?>(null)
    private val _trajectoryHandoff = MutableStateFlow<UserTrajectoryHandoffState?>(null)
    private val _localSessions = MutableStateFlow<List<LocalTrajectorySessionSummary>>(emptyList())
    private val _sessionFolderPath = MutableStateFlow("")
    private val _localSessionPreview = MutableStateFlow<UserLocalSessionPreviewState?>(null)
    private val _musicFileName = MutableStateFlow<String?>(null)
    private val _musicOffsetMs = MutableStateFlow(0L)
    private var statusOverrideJob: Job? = null

    val selectedTrajectory: StateFlow<String?> = _selectedTrajectory.asStateFlow()
    val sceneType: StateFlow<SceneType> = _sceneType.asStateFlow()
    val trajectoryHandoff: StateFlow<UserTrajectoryHandoffState?> = _trajectoryHandoff.asStateFlow()
    val localSessions: StateFlow<List<LocalTrajectorySessionSummary>> = _localSessions.asStateFlow()
    val sessionFolderPath: StateFlow<String> = _sessionFolderPath.asStateFlow()
    val localSessionPreview: StateFlow<UserLocalSessionPreviewState?> = _localSessionPreview.asStateFlow()
    val musicFileName: StateFlow<String?> = _musicFileName.asStateFlow()
    val musicOffsetMs: StateFlow<Long> = _musicOffsetMs.asStateFlow()

    val state: StateFlow<UserRunCommandState> = combine(
        gatewayClient.connectionState,
        gatewayClient.robotState,
        selectedTrajectory,
        _statusOverrideLabel,
        _sceneType
    ) { args ->
        val connection = args[0] as ConnectionState
        val robotState = args[1] as? JsonObject
        val selected = args[2] as? String
        val statusOverrideLabel = args[3] as? String
        val currentSceneType = args[4] as SceneType
        val parsed = parseRunSnapshot(robotState, selected, currentSceneType)
        val connectionStatus = when (connection) {
            is ConnectionState.Connected -> UserConnectionStatus.Connected
            is ConnectionState.Connecting -> UserConnectionStatus.Connecting
            is ConnectionState.Disconnected, is ConnectionState.Error -> UserConnectionStatus.Disconnected
        }
        UserRunCommandState(
            selectedTrajectory = selected,
            loadedSessionId = parsed.loadedSessionId,
            loadedSessionName = parsed.loadedSessionName,
            statusLabel = statusOverrideLabel ?: parsed.statusLabel,
            progress = parsed.progress,
            isRunning = parsed.isRunning,
            isPaused = parsed.isPaused,
            isIniting = parsed.isIniting,
            isHoming = parsed.isHoming,
            estopActive = parsed.estopActive,
            sceneType = currentSceneType,
            connectionStatus = connectionStatus,
            executorState = parsed.executorState,
            stage2ExecutionStatus = parsed.stage2ExecutionStatus,
            isStudioDance = currentSceneType == SceneType.StudioDance,
            musicFileName = _musicFileName.value,
            musicOffsetMs = _musicOffsetMs.value
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserRunCommandState()
    )

    val isConnected: Boolean
        get() = gatewayClient.isConnected()

    init {
        viewModelScope.launch {
            userSettingsRepository.sessionFolderPath.collect { path ->
                _sessionFolderPath.value = path
                refreshLocalSessions(path)
            }
        }

        viewModelScope.launch {
            combine(gatewayClient.robotState, selectedTrajectory, _sceneType) { robotState, selected, sceneType ->
                parseRunSnapshot(robotState, selected, sceneType)
            }
                .map { parsed ->
                    parsed.loadedSessionId != null ||
                        parsed.loadedSessionName != null ||
                        parsed.statusCode.isNotBlank() && parsed.statusCode != "idle"
                }
                .distinctUntilChanged()
                .collect { gatewayAuthoritative ->
                    if (gatewayAuthoritative) {
                        clearTransientStatus()
                    }
                }
        }

        // Detect scene type when gateway publishes trajectory lists
        viewModelScope.launch {
            combine(gatewayClient.robotState, selectedTrajectory) { robotState, selected ->
                if (selected.isNullOrBlank()) return@combine SceneType.Unknown
                detectSceneType(robotState, selected)
            }
                .distinctUntilChanged()
                .collect { detectedType ->
                    if (detectedType != SceneType.Unknown) {
                        _sceneType.value = detectedType
                    }
                }
        }
    }

    fun selectTrajectory(trajectory: String) {
        _selectedTrajectory.value = trajectory.trim().ifBlank { null }
        _trajectoryHandoff.value = null
        clearTransientStatus()
        // Scene type will be auto-detected from gateway state via the init coroutine,
        // but we also try immediate detection here for responsiveness
        val detected = detectSceneType(gatewayClient.robotState.value, trajectory.trim())
        if (detected != SceneType.Unknown) _sceneType.value = detected
    }

    fun selectTrajectoryWithSceneType(trajectory: String, sceneType: SceneType) {
        _selectedTrajectory.value = trajectory.trim().ifBlank { null }
        _sceneType.value = sceneType
        _trajectoryHandoff.value = null
        _musicFileName.value = null
        _musicOffsetMs.value = 0L
        clearTransientStatus()
    }

    fun selectStudioDanceTrajectory(
        trajectoryId: String,
        musicFile: String?,
        musicOffsetMs: Long = 0L
    ) {
        _selectedTrajectory.value = trajectoryId.trim().ifBlank { null }
        _sceneType.value = SceneType.StudioDance
        _trajectoryHandoff.value = null
        _musicFileName.value = musicFile
        _musicOffsetMs.value = musicOffsetMs
        clearTransientStatus()
    }

    fun clearTrajectory() {
        _selectedTrajectory.value = null
        _sceneType.value = SceneType.Unknown
        _trajectoryHandoff.value = null
        clearTransientStatus()
    }

    fun loadSelectedTrajectory() {
        val handoff = _trajectoryHandoff.value
        if (handoff != null && handoff.readiness != UserTrajectoryHandoffReadiness.Ready) {
            showTransientStatus(
                when (handoff.readiness) {
                    UserTrajectoryHandoffReadiness.Pending -> "Waiting for disclosure"
                    UserTrajectoryHandoffReadiness.Blocked -> "Needs publish"
                    else -> "Session not ready"
                }
            )
            return
        }
        val trajectory = _selectedTrajectory.value ?: return
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "select", trajectory = trajectory)
            }
            else -> {
                sendRunControl(action = "load", trajectoryId = trajectory)
            }
        }
        showTransientStatus("Loading trajectory")
    }

    fun run(deadman: Boolean = true, loop: Boolean = false, speedScale: Double = 1.0) {
        val trajectory = _selectedTrajectory.value
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "start", trajectory = trajectory)
            }
            SceneType.StudioDance -> {
                // Studio Dance uses the same SimpleTrack executor on the gateway;
                // music playback is handled pad-side by StudioDanceMusicPlayerViewModel.
                sendRunControl(
                    action = "run",
                    trajectoryId = trajectory,
                    deadman = deadman,
                    loop = loop,
                    speedScale = speedScale
                )
            }
            else -> {
                sendRunControl(
                    action = "run",
                    trajectoryId = trajectory,
                    deadman = deadman,
                    loop = loop,
                    speedScale = speedScale
                )
            }
        }
        showTransientStatus("Running")
    }

    fun resume(deadman: Boolean = true) {
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "start", trajectory = _selectedTrajectory.value)
            }
            else -> {
                sendRunControl(
                    action = "resume",
                    trajectoryId = _selectedTrajectory.value,
                    deadman = deadman
                )
            }
        }
        showTransientStatus("Resuming")
    }

    fun pause() {
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "pause", trajectory = _selectedTrajectory.value)
            }
            else -> {
                sendRunControl(
                    action = "pause",
                    trajectoryId = _selectedTrajectory.value
                )
            }
        }
        showTransientStatus("Paused")
    }

    fun stop() {
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "stop", trajectory = _selectedTrajectory.value)
            }
            else -> {
                sendRunControl(
                    action = "stop",
                    trajectoryId = _selectedTrajectory.value
                )
            }
        }
        showTransientStatus("Stopped")
    }

    fun home() {
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "homing", trajectory = _selectedTrajectory.value)
            }
            else -> {
                sendRunControl(
                    action = "home",
                    trajectoryId = _selectedTrajectory.value,
                    mode = SIMPLE_TRACK_MODE
                )
            }
        }
        showTransientStatus("Homing")
    }

    fun initScene() {
        val trajectory = _selectedTrajectory.value
        when (_sceneType.value) {
            SceneType.LivePnC -> {
                sendFixedPositionCmd(action = "initing", trajectory = trajectory)
            }
            else -> {
                sendRunControl(action = "init", trajectoryId = trajectory)
            }
        }
        showTransientStatus("Initializing")
    }

    fun abort() {
        sendRunControl(
            action = "abort",
            trajectoryId = _selectedTrajectory.value
        )
        showTransientStatus("Aborting")
    }

    fun estop() {
        sendSafety("estop")
        showTransientStatus("Emergency stop")
    }

    fun clearEstop() {
        sendSafety("clear_estop")
        showTransientStatus("E-stop cleared")
    }

    fun freezeAll() {
        sendSafety("freeze_all")
        showTransientStatus("Freeze all")
    }

    fun unfreezeAll() {
        sendSafety("unfreeze_all")
        showTransientStatus("Unfreeze all")
    }

    /**
     * Build a lean robot-state snapshot from the latest gateway frame for
     * AI-chat v2.1 attachments. Returns null if no state has arrived yet.
     * Arm joints are converted radians → degrees to match the protocol.
     */
    fun buildRobotStateSnapshot(): RobotStateSnapshot? {
        val state = gatewayClient.robotState.value ?: return null
        val armRad = state["arm"]?.jsonObject?.get("q")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
        val armDeg = armRad?.map { it * 180.0 / Math.PI }
        val gimbalQ = state["gimbal"]?.jsonObject?.get("q")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        val gimbal = if (gimbalQ.isNotEmpty()) {
            GimbalSnapshot(
                yaw = gimbalQ.getOrNull(0),
                pitch = gimbalQ.getOrNull(1),
                roll = gimbalQ.getOrNull(2)
            )
        } else null
        val base = state["base"]?.jsonObject
        val basePose = base?.let {
            val x = it["x"]?.jsonPrimitive?.doubleOrNull
            val y = it["y"]?.jsonPrimitive?.doubleOrNull
            val yaw = it["yaw"]?.jsonPrimitive?.doubleOrNull
            if (x != null && y != null && yaw != null) BasePoseSnapshot(x, y, yaw) else null
        }
        val locationId = state["maps"]?.jsonObject?.get("current_location")
            ?.jsonPrimitive?.contentOrNull
        val map = locationId?.let { MapSnapshot(locationId = it, name = it) }
        val mode = when (_sceneType.value) {
            SceneType.LivePnC -> "live_pnc"
            SceneType.SimpleTrack -> "simple_track"
            else -> null
        }
        if (armDeg == null && gimbal == null && basePose == null && map == null && mode == null) {
            return null
        }
        return RobotStateSnapshot(
            armQDeg = armDeg,
            gimbal = gimbal,
            basePose = basePose,
            map = map,
            mode = mode,
            capturedAtMs = System.currentTimeMillis()
        )
    }

    /** Outcome of the AI chat → Orin "deploy then select" path. */
    sealed class ExecuteCandidateResult {
        /** JSON landed on Orin and `FixedPositionCmd("select")` was issued. */
        data class Ready(val orinPath: String, val sceneType: SceneType) : ExecuteCandidateResult()
        /** Download of [ExecutionRef.executeUrl] failed. */
        data class DownloadFailed(val reason: String) : ExecuteCandidateResult()
        /** Orin deploy service rejected the payload / wasn't reachable. */
        data class DeployFailed(val reason: String) : ExecuteCandidateResult()
    }

    private val cloudDeployHttp = HttpClient(CIO) { engine { proxy = null } }

    /**
     * Drive the AI-chat "Execute" flow in parallel with CopyStyle presets:
     *   1. Fetch the trajectory file at [ExecutionRef.executeUrl] as text.
     *   2. POST the raw body to the Orin trajectory-deploy service via
     *      [OrinGatewayClient.deployTrajectory] — the service drops it
     *      under `fixed_position_traj_dir` and PnC's own converter
     *      handles the format (we don't parse the body).
     *   3. Call [selectTrajectoryWithSceneType] with the filename stem
     *      so MotionRunner + `FixedPositionCmd` pipeline treat it
     *      exactly like a CopyStyle preset.
     *
     * Returns a sealed result so the caller can toast / navigate.
     */
    suspend fun executeCloudAuthoredTrajectory(
        ref: ExecutionRef,
        displayName: String? = null,
    ): ExecuteCandidateResult =
        withContext(Dispatchers.IO) {
            val sceneType = when (ref.sceneType) {
                "LivePnC" -> SceneType.LivePnC
                "SimpleTrack" -> SceneType.SimpleTrack
                else -> SceneType.LivePnC
            }
            val bodyText = try {
                cloudDeployHttp.get(ref.executeUrl).bodyAsText()
            } catch (e: Exception) {
                Log.w("UserRunCmdVM", "executeUrl download failed: ${e.message}", e)
                return@withContext ExecuteCandidateResult.DownloadFailed(e.message ?: "download failed")
            }
            if (bodyText.isBlank()) {
                return@withContext ExecuteCandidateResult.DownloadFailed("empty trajectory body")
            }
            when (val result = gatewayClient.deployTrajectory(
                filename = ref.executeFilename,
                content = bodyText,
                overwrite = ref.overwrite
            )) {
                is OrinGatewayClient.DeployResult.Failure ->
                    return@withContext ExecuteCandidateResult.DeployFailed(result.reason)
                is OrinGatewayClient.DeployResult.Success -> {
                    // Gateway's scan_fixed_position_trajectories()
                    // stores names WITH the .json extension and
                    // FixedPositionCmd("select") does an exact-string
                    // match, so pass the filename verbatim (no
                    // substringBeforeLast('.') trick).
                    val selectName = ref.executeFilename
                    Log.i("UserRunCmdVM",
                        "deployed ${ref.executeFilename} → select=$selectName display=$displayName")
                    selectTrajectoryWithSceneType(selectName, sceneType)
                    // Keep the UI label clean: show the AI candidate's
                    // user-facing name (e.g. "aigen-1_0023-0004") rather
                    // than the opaque JSON filename. MotionRunner
                    // prefers trajectoryHandoff.sourceName over the
                    // wire id when readiness=Ready.
                    val label = displayName?.takeIf { it.isNotBlank() } ?: selectName
                    _trajectoryHandoff.value = UserTrajectoryHandoffState(
                        sourceName = label,
                        sessionId = selectName,
                        trajectoryId = selectName,
                        readiness = UserTrajectoryHandoffReadiness.Ready,
                        note = null,
                    )
                    return@withContext ExecuteCandidateResult.Ready(result.path, sceneType)
                }
            }
        }

    fun persistChatTrajectoryAsLocalSession(rawJson: JsonObject, sessionId: String) {
        val path = _sessionFolderPath.value
        if (path.isBlank() || sessionId.isBlank()) return
        viewModelScope.launch {
            val ok = localTrajectorySessionRepository.saveSessionFile(
                rawJson = rawJson,
                sessionId = sessionId,
                filesystemRootPath = path
            )
            if (ok) refreshLocalSessions(path)
        }
    }

    fun beginChatTrajectoryHandoff(attachment: TrajectoryAttachment) {
        _selectedTrajectory.value = attachment.trajectoryId
        _trajectoryHandoff.value = UserTrajectoryHandoffState(
            sourceName = attachment.name,
            sessionId = "",
            trajectoryId = attachment.trajectoryId,
            readiness = UserTrajectoryHandoffReadiness.Pending,
            note = null
        )
        showTransientStatus("Attaching to run", ttlMs = 4000L)
    }

    fun completeChatTrajectoryHandoff(
        attachment: TrajectoryAttachment,
        resolvedSessionId: String?,
        resolvedSessionName: String?
    ) {
        val normalizedSessionId = resolvedSessionId
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        val localMatch = normalizedSessionId?.let { sessionId ->
            _localSessions.value.firstOrNull { it.sessionId == sessionId }
        }

        _selectedTrajectory.value = localMatch?.sessionId ?: normalizedSessionId ?: attachment.trajectoryId
        _trajectoryHandoff.value = UserTrajectoryHandoffState(
            sourceName = localMatch?.sessionName ?: resolvedSessionName?.ifBlank { attachment.name } ?: attachment.name,
            sessionId = localMatch?.sessionId ?: normalizedSessionId.orEmpty(),
            trajectoryId = attachment.trajectoryId,
            readiness = if (localMatch != null) {
                UserTrajectoryHandoffReadiness.Ready
            } else if (normalizedSessionId != null) {
                UserTrajectoryHandoffReadiness.Pending
            } else {
                UserTrajectoryHandoffReadiness.Blocked
            },
            note = when {
                localMatch != null -> if (localMatch.source.kind == LocalTrajectorySessionSourceKind.Filesystem) {
                    "Disclosed session ready"
                } else {
                    "Local session ready"
                }
                normalizedSessionId != null -> "Session resolved; waiting for local disclosure"
                else -> "Unresolved contract detected. Resolve it before continuing."
            }
        )
        showTransientStatus(
            when {
            localMatch != null -> "Loaded from chat"
            normalizedSessionId != null -> "Attaching to run"
            else -> "Needs publish"
            },
            ttlMs = 4000L
        )
    }

    fun attachLocalSession(session: LocalTrajectorySessionSummary) {
        _selectedTrajectory.value = session.sessionId
        _trajectoryHandoff.value = UserTrajectoryHandoffState(
            sourceName = session.sessionName,
            sessionId = session.sessionId,
            trajectoryId = session.sessionId,
            readiness = UserTrajectoryHandoffReadiness.Ready,
            note = if (session.source.kind == LocalTrajectorySessionSourceKind.Filesystem) {
                "Disclosed session ready"
            } else {
                "Local session ready"
            }
        )
        showTransientStatus("Loaded from chat", ttlMs = 4000L)
    }

    fun previewLocalSession(session: LocalTrajectorySessionSummary) {
        viewModelScope.launch {
            try {
                val detail = localTrajectorySessionRepository.loadSession(
                    sessionId = session.sessionId,
                    filesystemRootPath = _sessionFolderPath.value
                )
                val preview = detail
                    ?.toSessionDetail()
                    ?.let { TrajectoryInterpolator.buildPreview(it) }
                if (detail != null && preview != null) {
                    _localSessionPreview.value = UserLocalSessionPreviewState(
                        sessionId = detail.sessionId,
                        sessionName = detail.sessionName,
                        frameCount = detail.frames.size,
                        preview = preview
                    )
                } else {
                    showTransientStatus("No preview data")
                }
            } catch (e: Exception) {
                showTransientStatus(e.message?.ifBlank { null } ?: "Preview failed")
            }
        }
    }

    fun closeLocalSessionPreview() {
        _localSessionPreview.value = null
    }

    fun refreshLocalSessions() {
        refreshLocalSessions(_sessionFolderPath.value)
    }

    private fun refreshLocalSessions(path: String) {
        viewModelScope.launch {
            try {
                val sessions = localTrajectorySessionRepository.listSessions(path)
                _localSessions.value = sessions
                val handoff = _trajectoryHandoff.value
                if (
                    handoff?.readiness == UserTrajectoryHandoffReadiness.Pending &&
                    handoff.sessionId.isNotBlank()
                ) {
                    val disclosedSession = sessions.firstOrNull { it.sessionId == handoff.sessionId }
                    if (disclosedSession != null) {
                        _selectedTrajectory.value = disclosedSession.sessionId
                        _trajectoryHandoff.value = handoff.copy(
                            sourceName = disclosedSession.sessionName,
                            sessionId = disclosedSession.sessionId,
                            readiness = UserTrajectoryHandoffReadiness.Ready,
                            note = if (disclosedSession.source.kind == LocalTrajectorySessionSourceKind.Filesystem) {
                                "Disclosed session ready"
                            } else {
                                "Local session ready"
                            }
                        )
                        showTransientStatus("Disclosed session ready", ttlMs = 4000L)
                    }
                }
            } catch (e: Exception) {
                _localSessions.value = emptyList()
                showTransientStatus(e.message?.ifBlank { null } ?: "Session refresh failed", ttlMs = 4000L)
            }
        }
    }

    fun failChatTrajectoryHandoff(
        attachment: TrajectoryAttachment,
        reason: String?
    ) {
        _selectedTrajectory.value = attachment.trajectoryId
        _trajectoryHandoff.value = UserTrajectoryHandoffState(
            sourceName = attachment.name,
            sessionId = "",
            trajectoryId = attachment.trajectoryId,
            readiness = UserTrajectoryHandoffReadiness.Blocked,
            note = reason?.ifBlank { null } ?: "Unresolved contract detected. Resolve it before continuing."
        )
        showTransientStatus("Needs publish", ttlMs = 4000L)
    }

    private fun showTransientStatus(label: String, ttlMs: Long = STATUS_LABEL_TTL_MS) {
        _statusOverrideLabel.value = label
        statusOverrideJob?.cancel()
        statusOverrideJob = viewModelScope.launch {
            delay(ttlMs)
            _statusOverrideLabel.value = null
        }
    }

    private fun clearTransientStatus() {
        statusOverrideJob?.cancel()
        statusOverrideJob = null
        _statusOverrideLabel.value = null
    }

    private fun sendRunControl(
        action: String,
        trajectoryId: String? = null,
        deadman: Boolean = false,
        loop: Boolean = false,
        speedScale: Double = 1.0,
        mode: String = when (_sceneType.value) {
            SceneType.LivePnC -> LIVE_PNC_MODE
            else -> SIMPLE_TRACK_MODE
        }
    ) {
        viewModelScope.launch {
            val cmd = buildJsonObject {
                put("type", "RunControl")
                put("seq_id", seqId.getAndIncrement())
                put("timestamp_ms", System.currentTimeMillis())
                put("ttl_ms", RUN_TTL_MS)
                put("action", action)
                put("deadman", deadman)
                put("loop", loop)
                put("speed_scale", speedScale)
                trajectoryId?.takeIf { it.isNotBlank() }?.let {
                    put("trajectory_id", it)
                    put("trajectory", it)
                    put("session_id", it)
                    put("session_name", it)
                }
                put("run_control_mode", mode)
            }
            gatewayClient.sendControl(cmd)
        }
    }

    private fun sendFixedPositionCmd(action: String, trajectory: String? = null) {
        viewModelScope.launch {
            val cmd = buildJsonObject {
                put("type", "FixedPositionCmd")
                put("seq_id", seqId.getAndIncrement())
                put("timestamp_ms", System.currentTimeMillis())
                put("action", action)
                trajectory?.takeIf { it.isNotBlank() }?.let {
                    put("trajectory", it)
                }
            }
            gatewayClient.sendControl(cmd)
        }
    }

    private fun sendSafety(action: String) {
        viewModelScope.launch {
            val cmd = buildJsonObject {
                put("type", "SafetyCmd")
                put("action", action)
            }
            gatewayClient.sendControl(cmd)
        }
    }

    private fun parseRunSnapshot(
        robotState: JsonObject?,
        selectedTrajectory: String?,
        currentSceneType: SceneType = SceneType.Unknown
    ): ParsedRunSnapshot {
        val runBlock = robotState?.get("run")?.jsonObject
        val simpleTrack = robotState?.get("simple_track")?.jsonObject
        val fixedPosition = robotState?.get("fixed_position")?.jsonObject

        // Pick the authoritative status source based on scene type
        val source = when (currentSceneType) {
            SceneType.LivePnC -> fixedPosition ?: runBlock
            SceneType.SimpleTrack, SceneType.StudioDance -> simpleTrack ?: runBlock
            SceneType.Unknown -> runBlock ?: simpleTrack ?: fixedPosition
        }
        val statusRaw = source?.get("status")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val status = statusRaw.lowercase()
        val loadedSessionId = when (currentSceneType) {
            SceneType.LivePnC -> fixedPosition?.get("selected_trajectory")?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { null }
            else -> null
        } ?: runBlock?.get("session_id")?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { null }
        val loadedSessionName = when (currentSceneType) {
            SceneType.LivePnC -> fixedPosition?.get("selected_trajectory")?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { null }
            else -> null
        } ?: runBlock?.get("session_name")?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { null }
        val statusLabel = when {
            status.isNotBlank() -> when (status) {
                "running" -> "Running"
                "paused" -> "Paused"
                "homing" -> "Homing"
                "initing" -> "Initializing"
                "loading" -> "Loading"
                "loaded" -> "Loaded"
                "completed" -> "Completed"
                "idle" -> "Idle"
                "error" -> "Error"
                "stopped" -> "Stopped"
                else -> statusRaw.replaceFirstChar { it.uppercase() }
            }
            !selectedTrajectory.isNullOrBlank() -> "Trajectory selected"
            else -> "Ready"
        }
        val currentFoi = runBlock?.get("current_foi")?.jsonPrimitive?.intOrNull
        val totalFois = runBlock?.get("total_fois")?.jsonPrimitive?.intOrNull
        val progress = if (currentFoi != null && totalFois != null && totalFois > 0) {
            (currentFoi.toFloat() / totalFois.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
        val isRunning = status == "running"
        val isPaused = status == "paused"
        val isIniting = status == "initing"
        val isHoming = status == "homing"
        val estopActive = robotState
            ?.get("safety")
            ?.jsonObject
            ?.get("estop")
            ?.jsonPrimitive
            ?.booleanOrNull ?: false

        // LivePnC executor state tracking
        val executorState = fixedPosition?.get("executor_state")?.jsonPrimitive?.intOrNull ?: 0
        val stage2ExecutionStatus = fixedPosition?.get("stage2_execution_status")
            ?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "idle" }

        return ParsedRunSnapshot(
            statusCode = status,
            loadedSessionId = loadedSessionId,
            loadedSessionName = loadedSessionName,
            statusLabel = statusLabel,
            progress = progress,
            isRunning = isRunning,
            isPaused = isPaused,
            isIniting = isIniting,
            isHoming = isHoming,
            estopActive = estopActive,
            executorState = executorState,
            stage2ExecutionStatus = stage2ExecutionStatus
        )
    }

    private fun detectSceneType(robotState: JsonObject?, trajectoryId: String?): SceneType {
        if (trajectoryId.isNullOrBlank() || robotState == null) return SceneType.Unknown
        val simpleTrackTrajs = robotState.get("simple_track")?.jsonObject
            ?.get("trajectories")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val fixedPositionTrajs = robotState.get("fixed_position")?.jsonObject
            ?.get("trajectories")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        return when {
            fixedPositionTrajs.any { it.equals(trajectoryId, ignoreCase = true) } -> SceneType.LivePnC
            simpleTrackTrajs.any { it.equals(trajectoryId, ignoreCase = true) } -> SceneType.SimpleTrack
            else -> SceneType.Unknown
        }
    }

    private data class ParsedRunSnapshot(
        val statusCode: String,
        val loadedSessionId: String?,
        val loadedSessionName: String?,
        val statusLabel: String,
        val progress: Float?,
        val isRunning: Boolean,
        val isPaused: Boolean,
        val isIniting: Boolean,
        val isHoming: Boolean,
        val estopActive: Boolean,
        val executorState: Int = 0,
        val stage2ExecutionStatus: String = "idle"
    )

    private fun LocalTrajectorySessionDetail.toSessionDetail(): SessionDetail {
        return SessionDetail(
            sessionId = sessionId,
            sessionName = sessionName,
            frames = frames.map { it.toFrameRecord() }
        )
    }

    private fun LocalTrajectoryFrameRecord.toFrameRecord(): FrameRecord {
        return FrameRecord(
            name = name,
            sequenceIndex = sequenceIndex,
            sessionRole = sessionRole,
            baseX = baseX,
            baseY = baseY,
            baseYaw = baseYaw,
            armQ = armQ,
            gimbalQ = gimbalQ,
            timestampMs = timestampMs,
            thumbnail = thumbnail,
            dwellS = dwellS,
            transitionS = transitionS,
            ease = ease
        )
    }
}
