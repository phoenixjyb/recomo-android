package com.recomo.user.control

enum class UserLibraryTarget {
    FoiSession,
    PoiSession
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
