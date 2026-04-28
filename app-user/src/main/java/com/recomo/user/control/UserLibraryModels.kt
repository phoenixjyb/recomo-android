package com.recomo.user.control

import kotlinx.serialization.json.JsonObject

enum class UserLibraryTarget {
    FoiSession,
    PoiSession
}

fun UserLibraryTarget.wireName(): String {
    return when (this) {
        UserLibraryTarget.FoiSession -> "foi_session"
        UserLibraryTarget.PoiSession -> "poi_session"
    }
}

fun userLibraryTargetFromWireName(value: String?): UserLibraryTarget? {
    return when (value?.trim()?.lowercase()) {
        "foi_session" -> UserLibraryTarget.FoiSession
        "poi_session" -> UserLibraryTarget.PoiSession
        else -> null
    }
}

data class UserLibrarySessionSummary(
    val sessionId: String,
    val sessionName: String,
    val robotName: String,
    val frameId: String,
    val category: String,
    val count: Int,
    val target: UserLibraryTarget,
    val sceneType: SceneType = SceneType.Unknown,
    val linkedMap: String = ""
)

data class UserLibrarySessionDetail(
    val sessionId: String,
    val sessionName: String,
    val target: UserLibraryTarget,
    val linkedMap: String = "",
    val mapName: String = "",
    val rawSession: JsonObject
)

data class UserLibraryState(
    val foiSessions: List<UserLibrarySessionSummary> = emptyList(),
    val poiSessions: List<UserLibrarySessionSummary> = emptyList(),
    val foiSessionCount: Int = 0,
    val poiSessionCount: Int = 0,
    val totalFoiCount: Int = 0,
    val totalPoiCount: Int = 0,
    val latestAction: String? = null,
    val latestMessage: String? = null,
    val simpleTrackTrajectories: List<String> = emptyList()
)
