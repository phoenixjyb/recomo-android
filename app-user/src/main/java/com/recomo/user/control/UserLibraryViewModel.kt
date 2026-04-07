package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.network.OrinGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@HiltViewModel
class UserLibraryViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {
    val state: StateFlow<UserLibraryState> = combine(
        gatewayClient.robotState,
        gatewayClient.connectionState
    ) { robotState, _ ->
        parseState(robotState)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserLibraryState()
    )

    val foiSessions: StateFlow<List<UserLibrarySessionSummary>> = state
        .map { it.foiSessions }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val poiSessions: StateFlow<List<UserLibrarySessionSummary>> = state
        .map { it.poiSessions }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val foiSessionCount: StateFlow<Int> = state
        .map { it.foiSessionCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val poiSessionCount: StateFlow<Int> = state
        .map { it.poiSessionCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val totalFoiCount: StateFlow<Int> = state
        .map { it.totalFoiCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val totalPoiCount: StateFlow<Int> = state
        .map { it.totalPoiCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val latestAction: StateFlow<String?> = state
        .map { it.latestAction }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val latestMessage: StateFlow<String?> = state
        .map { it.latestMessage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun refresh() {
        requestLibraryList(UserLibraryTarget.FoiSession)
        requestLibraryList(UserLibraryTarget.PoiSession)
    }

    fun requestLibraryList(target: UserLibraryTarget) {
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "LibraryCmd")
                    put("action", "list")
                    put("target", target.wireName())
                }
            )
        }
    }

    fun isConnected(): Boolean = gatewayClient.isConnected()

    private fun parseState(robotState: JsonObject?): UserLibraryState {
        val library = robotState?.get("library")?.jsonObject

        // Collect trajectory lists for scene type detection
        val simpleTrackTrajs = robotState?.get("simple_track")?.jsonObject
            ?.get("trajectories")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val fixedPositionTrajs = robotState?.get("fixed_position")?.jsonObject
            ?.get("trajectories")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val foiSessions = parseSessions(
            sessions = library?.get("foi_sessions")?.jsonArray,
            target = UserLibraryTarget.FoiSession,
            countField = "foi_count",
            simpleTrackTrajs = simpleTrackTrajs,
            fixedPositionTrajs = fixedPositionTrajs
        )
        val poiSessions = parseSessions(
            sessions = library?.get("poi_sessions")?.jsonArray,
            target = UserLibraryTarget.PoiSession,
            countField = "poi_count",
            simpleTrackTrajs = simpleTrackTrajs,
            fixedPositionTrajs = fixedPositionTrajs
        )

        val detail = robotState?.get("library_detail")?.jsonObject
        val latestAction = detail?.get("action")?.jsonPrimitive?.contentOrNull
        val latestMessage = when {
            detail == null -> null
            detail["success"]?.jsonPrimitive?.booleanOrNull == true -> {
                latestAction?.let { "$it: OK" }
            }
            detail["success"]?.jsonPrimitive?.booleanOrNull == false -> {
                latestAction?.let {
                    val error = detail["error"]?.jsonPrimitive?.contentOrNull?.takeIf { value -> value.isNotBlank() }
                    "$it: ${error ?: "failed"}"
                }
            }
            else -> latestAction
        }

        return UserLibraryState(
            foiSessions = foiSessions,
            poiSessions = poiSessions,
            foiSessionCount = foiSessions.size,
            poiSessionCount = poiSessions.size,
            totalFoiCount = foiSessions.sumOf { it.count.coerceAtLeast(0) },
            totalPoiCount = poiSessions.sumOf { it.count.coerceAtLeast(0) },
            latestAction = latestAction,
            latestMessage = latestMessage,
            simpleTrackTrajectories = simpleTrackTrajs
        )
    }

    private fun parseSessions(
        sessions: kotlinx.serialization.json.JsonArray?,
        target: UserLibraryTarget,
        countField: String,
        simpleTrackTrajs: List<String> = emptyList(),
        fixedPositionTrajs: List<String> = emptyList()
    ): List<UserLibrarySessionSummary> {
        return sessions
            ?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val sessionName = obj["session_name"]?.jsonPrimitive?.contentOrNull?.trim()
                    .orEmpty()
                val linkedMap = obj["linked_map"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val sceneType = when {
                    fixedPositionTrajs.any { it.equals(sessionId, ignoreCase = true) || it.equals(sessionName, ignoreCase = true) } ->
                        SceneType.LivePnC
                    simpleTrackTrajs.any { it.equals(sessionId, ignoreCase = true) || it.equals(sessionName, ignoreCase = true) } ->
                        SceneType.SimpleTrack
                    else -> SceneType.Unknown
                }
                UserLibrarySessionSummary(
                    sessionId = sessionId,
                    sessionName = sessionName,
                    robotName = obj["robot_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    frameId = obj["frame_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    category = obj["category"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                    count = obj[countField]?.jsonPrimitive?.intOrNull ?: 0,
                    target = target,
                    sceneType = sceneType,
                    linkedMap = linkedMap
                )
            }
            ?.filter { it.sessionId.isNotBlank() || it.sessionName.isNotBlank() }
            ?: emptyList()
    }

    private fun UserLibraryTarget.wireName(): String {
        return when (this) {
            UserLibraryTarget.FoiSession -> "foi_session"
            UserLibraryTarget.PoiSession -> "poi_session"
        }
    }
}
