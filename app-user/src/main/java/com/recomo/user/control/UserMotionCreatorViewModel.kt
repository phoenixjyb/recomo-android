package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
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
    val capturedAtMs: Long
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
        draftHomebase = buildFoiRecord(
            name = frameName,
            sessionRole = "homebase",
            sequenceIndex = 0,
            state = state
        )
        hasAnchor.value = true
        syncDraftFrames()
    }

    fun capturePoint(pointNameInput: String, sessionNameInput: String) {
        val state = gatewayClient.robotState.value ?: return
        ensureSession(sessionNameInput)
        val sequence = draftFois.size
        val frameName = pointNameInput.trim().ifBlank { "Point ${sequence + 1}" }
        draftFois.add(
            buildFoiRecord(
                name = frameName,
                sessionRole = "shot",
                sequenceIndex = sequence,
                state = state
            )
        )
        syncDraftFrames()
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
            clearDraftInternal()
            saving.value = false
        }
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
    }

    private fun syncDraftFrames() {
        val frames = mutableListOf<UserMotionCreatorDraftFrame>()
        draftHomebase?.let { anchor ->
            frames += UserMotionCreatorDraftFrame(
                id = "anchor",
                name = anchor["name"]?.jsonPrimitive?.contentOrNull ?: "Anchor",
                isAnchor = true,
                capturedAtMs = anchor["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }
        draftFois.forEachIndexed { index, frame ->
            frames += UserMotionCreatorDraftFrame(
                id = "point_$index",
                name = frame["name"]?.jsonPrimitive?.contentOrNull ?: "Point ${index + 1}",
                isAnchor = false,
                capturedAtMs = frame["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis()
            )
        }
        draftFrames.value = frames
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

    private fun buildFoiRecord(
        name: String,
        sessionRole: String,
        sequenceIndex: Int,
        state: JsonObject
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
