package com.recomo.user.data.trajectory

data class LocalTrajectoryFrameRecord(
    val name: String,
    val sequenceIndex: Int,
    val sessionRole: String,
    val baseX: Double,
    val baseY: Double,
    val baseYaw: Double,
    val armQ: List<Double>,
    val gimbalQ: List<Double>,
    val timestampMs: Long,
    val thumbnail: String? = null,
    val dwellS: Double? = null,
    val transitionS: Double? = null,
    val ease: String? = null
)

enum class LocalTrajectorySessionSourceKind {
    Assets,
    Filesystem
}

data class LocalTrajectorySessionSource(
    val kind: LocalTrajectorySessionSourceKind,
    val rootPath: String
)

data class LocalTrajectorySessionSummary(
    val sessionId: String,
    val sessionName: String,
    val robotName: String,
    val frameId: String,
    val category: String,
    val count: Int,
    val source: LocalTrajectorySessionSource,
    val sessionType: String? = null,
    val bpm: Int? = null,
    val musicFile: String? = null
)

data class LocalTrajectorySessionDetail(
    val sessionId: String,
    val sessionName: String,
    val robotName: String,
    val frameId: String,
    val category: String,
    val sessionType: String? = null,
    val timestampMs: Long = 0L,
    val frames: List<LocalTrajectoryFrameRecord>,
    val source: LocalTrajectorySessionSource,
    val musicFile: String? = null,
    val musicOffsetMs: Long? = null,
    val bpm: Int? = null
)

data class LocalTrajectorySessionLibrary(
    val sessions: List<LocalTrajectorySessionSummary>,
    val details: Map<String, LocalTrajectorySessionDetail>
)
