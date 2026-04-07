package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val NAV_TTL_MS = 2000
private const val STATUS_LABEL_TTL_MS = 3000L

@HiltViewModel
class UserNavigationViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {

    private val seqId = AtomicLong(1L)

    // Local selection state — independent of gateway
    private val _selectedPoiSession = MutableStateFlow<UserPoiSession?>(null)
    private val _selectedPoiIndex = MutableStateFlow(0)

    // FSM bounce guard: once we see Finished, hold it until the user explicitly triggers GO
    private val _arrivedLatch = MutableStateFlow(false)

    // Transient status override (shown briefly after user actions)
    private val _statusOverride = MutableStateFlow<String?>(null)
    private var statusOverrideJob: Job? = null

    // Combine gateway state with local UI selections using nested combines to stay within
    // the typed 5-flow limit of kotlinx.coroutines combine.
    private val _gatewaySnapshot = combine(
        gatewayClient.connectionState,
        gatewayClient.robotState,
        _arrivedLatch
    ) { connection, robotState, arrivedLatch ->
        Triple(connection, robotState, arrivedLatch)
    }

    private val _selectionSnapshot = combine(
        _selectedPoiSession,
        _selectedPoiIndex,
        _statusOverride
    ) { session, poiIndex, statusOverride ->
        Triple(session, poiIndex, statusOverride)
    }

    val navState: StateFlow<UserNavState> = combine(
        _gatewaySnapshot,
        _selectionSnapshot
    ) { (connection, robotState, arrivedLatch), (selectedSession, selectedPoiIndex, statusOverride) ->
        buildNavState(
            connectionState = connection,
            robotState = robotState,
            selectedSession = selectedSession,
            selectedPoiIndex = selectedPoiIndex,
            arrivedLatch = arrivedLatch,
            statusOverride = statusOverride
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserNavState()
    )

    init {
        // Watch FSM state to latch Finished and clear latch on Running→other
        viewModelScope.launch {
            combine(
                gatewayClient.robotState,
                _arrivedLatch
            ) { robotState, latch ->
                val runBlock = robotState?.get("run")?.jsonObject
                val fsmCode = runBlock?.get("pnc_fsm_state")?.jsonPrimitive?.intOrNull ?: 0
                val fsm = UserFsmState.fromCode(fsmCode)
                val navActive = runBlock?.get("nav_active")?.jsonPrimitive?.booleanOrNull ?: false
                Triple(fsm, navActive, latch)
            }.collect { (fsm, navActive, latch) ->
                when {
                    fsm == UserFsmState.Finished && !latch -> {
                        _arrivedLatch.value = true
                    }
                    fsm == UserFsmState.Running && navActive && latch -> {
                        // New navigation started — clear the latch
                        _arrivedLatch.value = false
                    }
                    fsm == UserFsmState.Init && !navActive && latch -> {
                        // Navigation was stopped/reset
                        _arrivedLatch.value = false
                    }
                }
            }
        }

        // Refresh POI session list whenever connected
        viewModelScope.launch {
            gatewayClient.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    requestLibraryList()
                }
            }
        }
    }

    // ----- Selection -----

    fun selectPoiSession(session: UserPoiSession) {
        if (_selectedPoiSession.value?.sessionId == session.sessionId) return
        _selectedPoiSession.value = session
        _selectedPoiIndex.value = 0
        fetchPoiSessionDetail(session.sessionId)
    }

    fun selectPoiIndex(index: Int) {
        _selectedPoiIndex.value = index.coerceAtLeast(0)
    }

    // ----- Navigation commands -----

    fun sendGoToPoi(
        sessionId: String = _selectedPoiSession.value?.sessionId ?: "",
        poiIndex: Int = _selectedPoiIndex.value,
        speed: Double = navState.value.navSpeed,
        mode: UserOperationMode = navState.value.operationMode
    ) {
        if (sessionId.isBlank()) {
            showTransientStatus("No session selected")
            return
        }
        if (navState.value.duplicateFsmPublishers) {
            showTransientStatus("Multiple nav publishers — GO rejected")
            return
        }
        // Clear arrived latch so FSM Running can re-latch properly
        _arrivedLatch.value = false
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "NavCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("ttl_ms", NAV_TTL_MS)
                    put("action", "go")
                    put("session_id", sessionId)
                    put("poi_index", poiIndex)
                    put("speed", speed.coerceIn(0.1, 2.0))
                    put("mode", mode.wireValue)
                }
            )
        }
        showTransientStatus("Navigating")
    }

    fun sendNavPause() {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "NavCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("ttl_ms", NAV_TTL_MS)
                    put("action", "pause")
                }
            )
        }
        showTransientStatus("Paused")
    }

    fun sendNavResume() {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "NavCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("ttl_ms", NAV_TTL_MS)
                    put("action", "resume")
                }
            )
        }
        showTransientStatus("Resuming")
    }

    fun sendNavStop() {
        _arrivedLatch.value = false
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "NavCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("ttl_ms", NAV_TTL_MS)
                    put("action", "stop")
                }
            )
        }
        showTransientStatus("Stopped")
    }

    fun sendNavSpeed(speed: Double) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "NavCmd")
                    put("seq_id", seqId.getAndIncrement())
                    put("timestamp_ms", System.currentTimeMillis())
                    put("ttl_ms", NAV_TTL_MS)
                    put("action", "speed")
                    put("speed", speed.coerceIn(0.1, 2.0))
                }
            )
        }
    }

    // ----- Follow commands -----

    fun startFollowing(
        maxSpeed: Double = 1.0,
        followDistance: Double = 2.0
    ) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "FollowCmd")
                    put("action", "start")
                    put("max_speed", maxSpeed.coerceIn(0.1, 2.0))
                    put("follow_distance", followDistance.coerceIn(0.5, 10.0))
                }
            )
        }
        showTransientStatus("Following")
    }

    fun stopFollowing() {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "FollowCmd")
                    put("action", "stop")
                }
            )
        }
        showTransientStatus("Follow stopped")
    }

    // ----- Target ROI (subject following) -----

    fun sendTargetRoi(xOffset: Int, yOffset: Int, width: Int, height: Int) {
        viewModelScope.launch {
            gatewayClient.sendTargetRoi(
                com.recomo.common.model.TargetRoi(
                    xOffset = xOffset,
                    yOffset = yOffset,
                    width = width,
                    height = height
                )
            )
        }
    }

    // ----- Mode commands -----

    fun setOperationMode(mode: UserOperationMode) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "ModeCmd")
                    put("mode", mode.wireValue)
                }
            )
        }
    }

    // ----- Library / POI refresh -----

    fun refreshPoiSessions() {
        requestLibraryList()
    }

    fun fetchPoiSessionDetail(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "LibraryCmd")
                    put("action", "get")
                    put("target", "poi_session")
                    put("session_id", sessionId)
                }
            )
        }
    }

    // ----- Private helpers -----

    private fun requestLibraryList() {
        if (!gatewayClient.isConnected()) return
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "LibraryCmd")
                    put("action", "list")
                    put("target", "poi_session")
                }
            )
        }
    }

    private fun showTransientStatus(label: String, ttlMs: Long = STATUS_LABEL_TTL_MS) {
        _statusOverride.value = label
        statusOverrideJob?.cancel()
        statusOverrideJob = viewModelScope.launch {
            delay(ttlMs)
            _statusOverride.value = null
        }
    }

    private fun buildNavState(
        connectionState: ConnectionState,
        robotState: JsonObject?,
        selectedSession: UserPoiSession?,
        selectedPoiIndex: Int,
        arrivedLatch: Boolean,
        statusOverride: String?
    ): UserNavState {
        val isConnected = connectionState is ConnectionState.Connected

        val runBlock = robotState?.get("run")?.jsonObject
        val operationMode = UserOperationMode.fromWire(
            runBlock?.get("operation_mode")?.jsonPrimitive?.contentOrNull
        )
        val navActive = runBlock?.get("nav_active")?.jsonPrimitive?.booleanOrNull ?: false
        val navSessionId = runBlock?.get("nav_session_id")?.jsonPrimitive?.contentOrNull.orEmpty()
        val navSpeed = runBlock?.get("nav_speed")?.jsonPrimitive?.doubleOrNull ?: 0.8
        val fsmCode = runBlock?.get("pnc_fsm_state")?.jsonPrimitive?.intOrNull ?: 0
        val rawFsm = UserFsmState.fromCode(fsmCode)
        // Apply FSM bounce guard: if latch is set, report Finished regardless of raw state
        val fsmState = if (arrivedLatch) UserFsmState.Finished else rawFsm

        val followActive = runBlock?.get("follow_active")?.jsonPrimitive?.booleanOrNull ?: false
        val followMaxSpeed = runBlock?.get("follow_max_speed")?.jsonPrimitive?.doubleOrNull ?: 1.0
        val followDistance = runBlock?.get("follow_distance")?.jsonPrimitive?.doubleOrNull ?: 2.0

        // Check duplicate FSM publishers (reject GO if >1)
        val robotPosePubs = robotState
            ?.get("topic_health")?.jsonObject
            ?.get("robot_pose_pubs")?.jsonPrimitive?.intOrNull ?: 0
        val duplicateFsmPublishers = robotPosePubs > 1

        // Parse POI sessions from library block
        val libraryBlock = robotState?.get("library")?.jsonObject
        val poiSessions = libraryBlock
            ?.get("poi_sessions")?.jsonArray
            ?.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val sid = obj["session_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val sname = obj["session_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val count = obj["poi_count"]?.jsonPrimitive?.intOrNull ?: 0
                if (sid.isBlank() && sname.isBlank()) return@mapNotNull null
                UserPoiSession(sessionId = sid, sessionName = sname, poiCount = count)
            }
            ?: emptyList()

        // Parse current POI list from library_detail (populated after LibraryCmd get)
        val currentPoiList = parsePoiDetailList(robotState, selectedSession?.sessionId)

        // Determine readable status
        val statusLabel = statusOverride ?: when {
            !isConnected -> "Disconnected"
            followActive -> "Following"
            arrivedLatch -> "Arrived"
            navActive && fsmState == UserFsmState.Running -> "Navigating"
            navActive -> "Nav active"
            fsmState == UserFsmState.Running -> "Running"
            fsmState == UserFsmState.Exit -> "Exiting"
            fsmState == UserFsmState.Demo -> "Demo"
            else -> "Idle"
        }

        val canGo = isConnected &&
            !duplicateFsmPublishers &&
            selectedSession != null &&
            selectedPoiIndex >= 0

        return UserNavState(
            operationMode = operationMode,
            navActive = navActive,
            navSessionId = navSessionId,
            navSpeed = navSpeed,
            fsmState = fsmState,
            followActive = followActive,
            followMaxSpeed = followMaxSpeed,
            followDistance = followDistance,
            poiSessions = poiSessions,
            selectedPoiSession = selectedSession,
            selectedPoiIndex = selectedPoiIndex,
            currentPoiList = currentPoiList,
            statusLabel = statusLabel,
            isConnected = isConnected,
            canGo = canGo,
            duplicateFsmPublishers = duplicateFsmPublishers
        )
    }

    /**
     * Parses the POI item list from `library_detail` when it matches the selected session.
     * The gateway populates `library_detail` in response to LibraryCmd `get` for a poi_session.
     * Each POI entry has: {name, x, y, yaw, frame_id}.
     */
    private fun parsePoiDetailList(robotState: JsonObject?, selectedSessionId: String?): List<UserPoiItem> {
        if (selectedSessionId.isNullOrBlank()) return emptyList()
        val detail = robotState?.get("library_detail")?.jsonObject ?: return emptyList()
        // Detail block: {session_id, session_name, pois: [{name, x, y, yaw, frame_id}, ...]}
        val detailSessionId = detail["session_id"]?.jsonPrimitive?.contentOrNull?.trim()
        if (detailSessionId != selectedSessionId) return emptyList()
        val poisArray = detail["pois"]?.jsonArray ?: return emptyList()
        return poisArray.mapIndexedNotNull { index, elem ->
            val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: "POI ${index + 1}"
            val x = obj["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val y = obj["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val yaw = obj["yaw"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            UserPoiItem(index = index, name = name, x = x, y = y, yaw = yaw)
        }
    }
}
