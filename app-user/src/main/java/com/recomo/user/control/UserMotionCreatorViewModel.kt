package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.FrameRecord as CommonFrameRecord
import com.recomo.common.network.OrinGatewayClient
import com.recomo.common.preview.TrajectoryPreview
import com.recomo.common.preview.TrajectorySample
import com.recomo.user.data.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class UserMotionCreatorDraftFrame(
    val id: String,
    val name: String,
    val isAnchor: Boolean,
    val capturedAtMs: Long,
    val dwellS: Double? = null,
    val transitionS: Double? = null,
    val ease: String? = null,
    val hasThumbnail: Boolean = false,
    /** Base pose for display in frame list */
    val baseX: Double = 0.0,
    val baseY: Double = 0.0,
    val baseYaw: Double = 0.0,
    /** Arm joint angles (degrees) for display */
    val armQ: List<Double> = emptyList(),
    /** Gimbal joint angles for display */
    val gimbalQ: List<Double> = emptyList()
)

/**
 * Timing parameters captured for a single keyframe.
 * Mirrors :app `FrameTiming` — gateway JSON emits `dwell_s`, `transition_s`, `ease`
 * only when non-null.
 */
data class FrameTiming(
    val dwellS: Double? = null,
    val transitionS: Double? = null,
    val ease: String? = null
)

/**
 * State surfaced to UI when a just-captured frame needs timing to be set.
 * Mirrors :app `PendingFrameTiming`.
 */
data class PendingFrameTiming(
    val frameIndex: Int,
    val frameName: String
)

data class UserMotionCreatorKeypointUiState(
    val connected: Boolean = false,
    val currentSessionName: String? = null,
    val suggestedSessionName: String = "",
    val suggestedFrameName: String = "",
    val frames: List<UserMotionCreatorDraftFrame> = emptyList(),
    val hasAnchor: Boolean = false,
    val selectedScene: String? = null,
    val selectedMap: String? = null,
    val poseLocked: Boolean = false,
    val backendMessage: String? = null,
    val saving: Boolean = false
) {
    val frameCount: Int get() = frames.count { !it.isAnchor }
    val canSave: Boolean get() = frames.isNotEmpty()
}

@HiltViewModel
class UserMotionCreatorViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {
    private val currentSessionName = MutableStateFlow<String?>(null)
    private val draftFrames = MutableStateFlow<List<UserMotionCreatorDraftFrame>>(emptyList())
    private val hasAnchor = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    private val seqId = AtomicLong(1L)
    private var activeFoiSessionId: String? = null
    private var activeFoiSessionName: String? = null
    private var draftHomebase: JsonObject? = null
    private val draftFois = mutableListOf<JsonObject>()

    /**
     * Thumbnail provider — set by the Keypoint workspace to deliver a base64 JPEG
     * from the current live video frame each time a point is captured. Returns null
     * if no frame is available (Phase 5 wires this to UserRunVideoViewModel).
     */
    var thumbnailProvider: (() -> String?)? = null

    private val _pendingFrameTiming = MutableStateFlow<PendingFrameTiming?>(null)
    /** Emitted after `capturePoint` so the UI can pop a timing dialog. */
    val pendingFrameTiming: StateFlow<PendingFrameTiming?> = _pendingFrameTiming.asStateFlow()

    /**
     * Local preview built from the just-saved draft, so the user can inspect
     * the trajectory without waiting for the gateway to ingest and round-trip
     * the session via `library_cmd list`. Non-null while the user is viewing
     * the save-time preview; cleared by [closeDraftPreview].
     */
    private val _draftPreview = MutableStateFlow<TrajectoryPreview?>(null)
    val draftPreview: StateFlow<TrajectoryPreview?> = _draftPreview.asStateFlow()

    /** Fallback duration (s) between frames when the user left transition blank. */
    private val defaultTransitionS = 2.0

    val keypointState: StateFlow<UserMotionCreatorKeypointUiState> = combine(
        gatewayClient.connectionState,
        gatewayClient.robotState,
        currentSessionName,
        draftFrames,
        hasAnchor,
        saving
    ) { values ->
        val connectionState = values[0] as ConnectionState
        val robotState = values[1] as? JsonObject
        val sessionName = values[2] as? String
        @Suppress("UNCHECKED_CAST")
        val frames = values[3] as List<UserMotionCreatorDraftFrame>
        val anchorSet = values[4] as Boolean
        val isSaving = values[5] as Boolean
        val maps = robotState?.get("maps")?.jsonObject
        val selectedScene = maps?.get("current_location")?.jsonPrimitive?.contentOrNull
        val selectedMap = maps?.get("current_map")?.jsonPrimitive?.contentOrNull
        val poseLocked = (
            robotState?.get("topic_health")?.jsonObject?.get("robot_pose_ok")
                ?: robotState?.get("robot_pose_ok")
            )?.jsonPrimitive?.booleanOrNull ?: false
        val libraryDetail = robotState?.get("library_detail")?.jsonObject
        val backendMessage = libraryDetail
            ?.takeIf { it["target"]?.jsonPrimitive?.contentOrNull == "foi_session" || it["target"] == null }
            ?.let { detail ->
                val action = detail["action"]?.jsonPrimitive?.contentOrNull
                when {
                    detail["success"]?.jsonPrimitive?.booleanOrNull == true -> action?.let { "$it: OK" }
                    detail["success"]?.jsonPrimitive?.booleanOrNull == false -> {
                        val error = detail["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        action?.let { "$it: ${error ?: "failed"}" }
                    }
                    else -> null
                }
            }

        UserMotionCreatorKeypointUiState(
            connected = connectionState is ConnectionState.Connected,
            currentSessionName = sessionName,
            suggestedSessionName = sessionName ?: generateSessionName(),
            suggestedFrameName = "Point ${draftFois.size + 1}",
            frames = frames,
            hasAnchor = anchorSet,
            selectedScene = selectedScene,
            selectedMap = selectedMap,
            poseLocked = poseLocked,
            backendMessage = backendMessage,
            saving = isSaving
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserMotionCreatorKeypointUiState()
    )

    fun newDraft(sessionNameInput: String) {
        clearDraftInternal()
        val name = sessionNameInput.trim().ifBlank { generateSessionName() }
        activeFoiSessionName = name
        activeFoiSessionId = generateSessionId("foi")
        currentSessionName.value = name
    }

    fun setAnchor(anchorNameInput: String, sessionNameInput: String) {
        val state = gatewayClient.robotState.value ?: return
        ensureSession(sessionNameInput)
        val frameName = anchorNameInput.trim().ifBlank { "Anchor" }
        val thumbnail = thumbnailProvider?.invoke()
        draftHomebase = buildFoiRecord(
            name = frameName,
            sessionRole = "homebase",
            sequenceIndex = 0,
            state = state,
            thumbnail = thumbnail
        )
        hasAnchor.value = true
        syncDraftFrames()
    }

    fun capturePoint(pointNameInput: String, sessionNameInput: String) {
        val state = gatewayClient.robotState.value ?: return
        ensureSession(sessionNameInput)
        val sequence = draftFois.size
        val frameName = pointNameInput.trim().ifBlank { "Point ${sequence + 1}" }
        val thumbnail = thumbnailProvider?.invoke()
        draftFois.add(
            buildFoiRecord(
                name = frameName,
                sessionRole = "shot",
                sequenceIndex = sequence,
                state = state,
                thumbnail = thumbnail
            )
        )
        syncDraftFrames()
        // Pop timing dialog for the just-captured frame (user may cancel to skip)
        _pendingFrameTiming.value = PendingFrameTiming(frameIndex = sequence, frameName = frameName)
    }

    /**
     * Apply timing (dwell / transition / ease) to a previously captured frame.
     * Mirrors :app `setFrameTiming`: copies the existing JsonObject and
     * overwrites only the three timing keys.
     */
    fun setFrameTiming(frameIndex: Int, timing: FrameTiming) {
        if (frameIndex in draftFois.indices) {
            val oldFrame = draftFois[frameIndex]
            val updated = buildJsonObject {
                oldFrame.forEach { (key, value) ->
                    val drop = (key == "dwell_s" && timing.dwellS == null) ||
                        (key == "transition_s" && timing.transitionS == null) ||
                        (key == "ease" && timing.ease == null)
                    if (!drop) put(key, value)
                }
                timing.dwellS?.let { put("dwell_s", it) }
                timing.transitionS?.let { put("transition_s", it) }
                timing.ease?.let { put("ease", it) }
            }
            draftFois[frameIndex] = updated
            syncDraftFrames()
        }
        _pendingFrameTiming.value = null
    }

    /** Dismiss the timing dialog without changing the frame. */
    fun cancelFrameTiming() {
        _pendingFrameTiming.value = null
    }

    /** Re-open the timing dialog for an already-captured frame. */
    fun editFrameTiming(frameIndex: Int) {
        if (frameIndex in draftFois.indices) {
            val frame = draftFois[frameIndex]
            val frameName = frame["name"]?.jsonPrimitive?.contentOrNull ?: "Point ${frameIndex + 1}"
            _pendingFrameTiming.value = PendingFrameTiming(frameIndex = frameIndex, frameName = frameName)
        }
    }

    /** Delete a previously captured point frame (homebase/anchor is not deletable here). */
    fun removeFrame(frameIndex: Int) {
        if (frameIndex in draftFois.indices) {
            draftFois.removeAt(frameIndex)
            // Re-sequence remaining frames so sequence_index stays contiguous.
            draftFois.forEachIndexed { idx, frame ->
                draftFois[idx] = buildJsonObject {
                    frame.forEach { (key, value) ->
                        if (key != "sequence_index") put(key, value)
                    }
                    put("sequence_index", idx)
                }
            }
            syncDraftFrames()
        }
    }

    fun clearDraft() {
        clearDraftInternal()
    }

    fun saveDraft() {
        if (draftHomebase == null && draftFois.isEmpty()) return
        val sessionId = activeFoiSessionId ?: generateSessionId("foi")
        val sessionName = activeFoiSessionName ?: generateSessionName()
        viewModelScope.launch {
            saving.value = true
            val robotName = userSettingsRepository.appSettings.first().robotProfile.robotName.ifBlank { "recomo" }
            val records = buildList {
                draftHomebase?.let(::add)
                addAll(draftFois)
            }
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "LibraryCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("action", "save")
                    put("target", "foi_session")
                    put(
                        "session",
                        buildFoiSession(
                            sessionId = sessionId,
                            sessionName = sessionName,
                            robotName = robotName,
                            records = records,
                            state = gatewayClient.robotState.value
                        )
                    )
                }
            )
            delay(350)
            requestFoiLibraryList()
            // Snapshot BEFORE clearing so the preview survives the wipe.
            _draftPreview.value = buildDraftPreviewSnapshot(records)
            clearDraftInternal()
            saving.value = false
        }
    }

    /** Close the save-time trajectory preview overlay. */
    fun closeDraftPreview() {
        _draftPreview.value = null
    }

    /**
     * Convert the just-saved FOI records into a `TrajectoryPreview` so the
     * existing `TrajectoryPreviewScreen` can render them. One sample per
     * keyframe, timed by cumulative (dwell + transition) so playback honors
     * the per-frame pacing the user entered. Falls back to 2 s transition
     * when the user left the timing blank.
     */
    private fun buildDraftPreviewSnapshot(records: List<JsonObject>): TrajectoryPreview? {
        if (records.isEmpty()) return null
        val samples = mutableListOf<TrajectorySample>()
        val keyframes = mutableListOf<CommonFrameRecord>()
        var cursor = 0.0
        records.forEachIndexed { idx, frame ->
            val poi = frame["poi"]?.jsonObject
            val bx = poi?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val by = poi?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val byaw = poi?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val armQ = frame["arm_q"]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }
                ?: emptyList()
            val gimbalQRaw = frame["gimbal_q"]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }
                ?: emptyList()
            val gimbalQ = when {
                gimbalQRaw.size >= 3 -> gimbalQRaw.take(3)
                gimbalQRaw.size == 2 -> gimbalQRaw + listOf(0.0)
                gimbalQRaw.size == 1 -> gimbalQRaw + listOf(0.0, 0.0)
                else -> listOf(0.0, 0.0, 0.0)
            }
            val name = frame["name"]?.jsonPrimitive?.contentOrNull ?: "Frame ${idx + 1}"
            val sessionRole = frame["session_role"]?.jsonPrimitive?.contentOrNull ?: "shot"
            val dwellS = frame["dwell_s"]?.jsonPrimitive?.doubleOrNull
            val transitionS = frame["transition_s"]?.jsonPrimitive?.doubleOrNull
            val ease = frame["ease"]?.jsonPrimitive?.contentOrNull
            val thumbnail = frame["thumbnail"]?.jsonPrimitive?.contentOrNull
            val timestampMs = frame["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: 0L

            samples += TrajectorySample(
                tSec = cursor,
                baseX = bx,
                baseY = by,
                baseYaw = byaw,
                armQ = armQ,
                gimbalQ = gimbalQ
            )
            keyframes += CommonFrameRecord(
                name = name,
                sequenceIndex = idx,
                sessionRole = sessionRole,
                baseX = bx,
                baseY = by,
                baseYaw = byaw,
                armQ = armQ,
                gimbalQ = gimbalQ,
                timestampMs = timestampMs,
                thumbnail = thumbnail,
                dwellS = dwellS,
                transitionS = transitionS,
                ease = ease
            )

            // Advance cursor by this frame's dwell (pause here) + its transition
            // (time until the NEXT frame). Last frame: only dwell matters.
            cursor += (dwellS ?: 0.0)
            if (idx < records.lastIndex) {
                cursor += (transitionS ?: defaultTransitionS)
            }
        }
        return TrajectoryPreview(
            samples = samples,
            keyframes = keyframes,
            totalDurationSec = cursor
        )
    }

    private fun ensureSession(sessionNameInput: String) {
        if (activeFoiSessionId != null) return
        val name = sessionNameInput.trim().ifBlank { generateSessionName() }
        activeFoiSessionName = name
        activeFoiSessionId = generateSessionId("foi")
        currentSessionName.value = name
    }

    private fun clearDraftInternal() {
        draftHomebase = null
        draftFois.clear()
        activeFoiSessionId = null
        activeFoiSessionName = null
        currentSessionName.value = null
        hasAnchor.value = false
        draftFrames.value = emptyList()
        saving.value = false
        _pendingFrameTiming.value = null
    }

    private fun syncDraftFrames() {
        val frames = mutableListOf<UserMotionCreatorDraftFrame>()
        draftHomebase?.let { anchor ->
            frames += parseDraftFrame("anchor", anchor, isAnchor = true)
        }
        draftFois.forEachIndexed { index, frame ->
            frames += parseDraftFrame("point_$index", frame, isAnchor = false)
        }
        draftFrames.value = frames
    }

    private fun parseDraftFrame(id: String, json: JsonObject, isAnchor: Boolean): UserMotionCreatorDraftFrame {
        val poi = json["poi"]?.jsonObject
        val armQ = json["arm_q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        val gimbalQ = json["gimbal_q"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        return UserMotionCreatorDraftFrame(
            id = id,
            name = json["name"]?.jsonPrimitive?.contentOrNull ?: if (isAnchor) "Anchor" else "Point",
            isAnchor = isAnchor,
            capturedAtMs = json["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis(),
            dwellS = json["dwell_s"]?.jsonPrimitive?.doubleOrNull,
            transitionS = json["transition_s"]?.jsonPrimitive?.doubleOrNull,
            ease = json["ease"]?.jsonPrimitive?.contentOrNull,
            hasThumbnail = json["thumbnail"] != null,
            baseX = poi?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            baseY = poi?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            baseYaw = poi?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            armQ = armQ,
            gimbalQ = gimbalQ
        )
    }

    private suspend fun requestFoiLibraryList() {
        gatewayClient.sendControl(
            buildJsonObject {
                put("type", "LibraryCmd")
                put("action", "list")
                put("target", "foi_session")
            }
        )
    }

    private fun generateSessionName(): String {
        val sdf = SimpleDateFormat("ddMMyyyy-HHmmss", Locale.US)
        return "shot-plan-${sdf.format(Date())}"
    }

    private fun generateSessionId(prefix: String): String {
        val sdf = SimpleDateFormat("ddMMyyyy-HHmmss", Locale.US)
        return "${prefix}_${sdf.format(Date())}"
    }

    private fun buildFoiSession(
        sessionId: String,
        sessionName: String,
        robotName: String,
        records: List<JsonObject>,
        state: JsonObject?
    ): JsonObject {
        return buildJsonObject {
            put("type", "FoiSession")
            put("session_id", sessionId)
            put("session_name", sessionName)
            put("robot_name", robotName)
            put("frame_id", "odom")
            put("category", "default")
            put("timestamp_ms", System.currentTimeMillis())
            state?.get("maps")?.jsonObject?.get("current_location")?.jsonPrimitive?.contentOrNull?.let { mapName ->
                put("map_name", mapName)
            }
            put("fois", buildJsonArray { records.forEach(::add) })
        }
    }

    /**
     * Build a single FOI record JSON. Schema MUST match `:app`
     * `RecomoControlViewModel.buildFoiRecord` byte-for-byte so the gateway treats
     * records from both apps identically.
     *
     * Optional fields (emitted only when non-null/ok): camera_pose, robot_pose,
     * thumbnail, dwell_s, transition_s, ease.
     */
    private fun buildFoiRecord(
        name: String,
        sessionRole: String,
        sequenceIndex: Int,
        state: JsonObject,
        thumbnail: String? = null,
        timing: FrameTiming? = null
    ): JsonObject {
        val now = System.currentTimeMillis()
        val base = state["base"]?.jsonObject
        val camera = state["camera"]?.jsonObject
        val cameraPosition = camera?.get("position")?.jsonObject
        val cameraQuat = camera?.get("orientation_quat")?.jsonObject
        val cameraOk = camera?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
        val mapPose = state["map_pose"]?.jsonObject
        val mapPoseOk = mapPose?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false

        return buildJsonObject {
            put("name", name)
            put("sequence_index", sequenceIndex)
            put("session_role", sessionRole)
            put(
                "poi",
                buildJsonObject {
                    put("x", base?.get("x")?.jsonPrimitive?.doubleOrNull ?: 0.0)
                    put("y", base?.get("y")?.jsonPrimitive?.doubleOrNull ?: 0.0)
                    put("yaw", base?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0)
                }
            )
            put("arm_q", buildArmQ(state))
            put("gimbal_q", buildGimbalQ(state))
            if (cameraOk && cameraPosition != null && cameraQuat != null) {
                put(
                    "camera_pose",
                    buildJsonObject {
                        put("x", cameraPosition["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        put("y", cameraPosition["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        put("z", cameraPosition["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        put("qw", cameraQuat["w"]?.jsonPrimitive?.doubleOrNull ?: 1.0)
                        put("qx", cameraQuat["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        put("qy", cameraQuat["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                        put("qz", cameraQuat["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                    }
                )
            }
            if (mapPoseOk) {
                val position = mapPose?.get("position")?.jsonObject
                val quat = mapPose?.get("orientation_quat")?.jsonObject
                if (position != null && quat != null) {
                    put(
                        "robot_pose",
                        buildJsonObject {
                            mapPose["frame_id"]?.jsonPrimitive?.contentOrNull?.let { put("frame_id", it) }
                            put(
                                "position",
                                buildJsonObject {
                                    put("x", position["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                    put("y", position["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                    put("z", position["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                }
                            )
                            put(
                                "orientation_quat",
                                buildJsonObject {
                                    put("w", quat["w"]?.jsonPrimitive?.doubleOrNull ?: 1.0)
                                    put("x", quat["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                    put("y", quat["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                    put("z", quat["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                }
                            )
                            mapPose["yaw"]?.jsonPrimitive?.doubleOrNull?.let { put("yaw", it) }
                        }
                    )
                }
            }
            // Optional trailing fields — MUST match :app ordering: thumbnail → timing → timestamp_ms
            if (thumbnail != null) put("thumbnail", thumbnail)
            if (timing?.dwellS != null) put("dwell_s", timing.dwellS)
            if (timing?.transitionS != null) put("transition_s", timing.transitionS)
            if (timing?.ease != null) put("ease", timing.ease)
            put("timestamp_ms", now)
        }
    }

    private fun buildArmQ(state: JsonObject): JsonArray {
        val arm = state["arm"]?.jsonObject
        val names = arm?.get("joint_names")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val positions = arm?.get("q")?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
        val output = if (names.isEmpty() && positions.size >= 3) {
            doubleArrayOf(positions[0], positions[1], positions[2])
        } else {
            armPositionsFromState(arm, names, positions) ?: doubleArrayOf(0.0, 0.0, 0.0)
        }
        return buildJsonArray {
            add(JsonPrimitive(output[0]))
            add(JsonPrimitive(output[1]))
            add(JsonPrimitive(output[2]))
        }
    }

    private fun armPositionsFromState(
        arm: JsonObject?,
        names: List<String>,
        positions: List<Double>
    ): DoubleArray? {
        if (arm == null || positions.size < 3) return null
        if (names.isEmpty()) return doubleArrayOf(positions[0], positions[1], positions[2])
        val mapped = DoubleArray(3) { Double.NaN }
        names.forEachIndexed { idx, jointName ->
            val jointIndex = when {
                jointName.lowercase().contains("elbow") || jointName.lowercase().contains("joint4") -> 0
                jointName.lowercase().contains("base_pitch") || jointName.lowercase().contains("joint5") -> 1
                jointName.lowercase().contains("base_yaw") || jointName.lowercase().contains("joint6") -> 2
                else -> null
            } ?: return@forEachIndexed
            val value = positions.getOrNull(idx) ?: return@forEachIndexed
            mapped[jointIndex] = value
        }
        return if (mapped.any { it.isNaN() }) null else mapped
    }

    private fun buildGimbalQ(state: JsonObject): JsonArray {
        val values = state["gimbal"]?.jsonObject?.get("q")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
            ?: emptyList()
        return buildJsonArray {
            add(JsonPrimitive(values.getOrNull(0) ?: 0.0))
            add(JsonPrimitive(values.getOrNull(1) ?: 0.0))
            add(JsonPrimitive(values.getOrNull(2) ?: 0.0))
        }
    }
}
