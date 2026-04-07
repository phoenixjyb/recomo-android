package com.recomo.common.model

/**
 * A single keyframe in a recorded trajectory session.
 * Shared between engineering and user apps for trajectory preview.
 */
data class FrameRecord(
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

/**
 * Parsed trajectory session with keyframes.
 */
data class SessionDetail(
    val sessionId: String,
    val sessionName: String,
    val frames: List<FrameRecord>
)

/**
 * Summary of a session in the library list.
 */
data class SessionSummary(
    val sessionId: String,
    val sessionName: String,
    val count: Int,
    val target: String = "foi_session"
)

/**
 * RUN mode state machine status.
 */
enum class RunStatus {
    IDLE,
    HOMING,
    LOADING,
    LOADED,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR
}
