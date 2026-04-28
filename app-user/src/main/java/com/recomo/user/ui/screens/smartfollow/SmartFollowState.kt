package com.recomo.user.ui.screens.smartfollow

import com.recomo.common.model.SubjectTracking

/**
 * State machine for Smart Follow feature.
 */
sealed class SmartFollowState {
    /** No tracking active. Draw a bbox to begin. */
    data object Idle : SmartFollowState()

    /** User is actively drawing a bounding box. */
    data object Selecting : SmartFollowState()

    /** ROI sent to Orin, waiting for tracker initialization. */
    data object Pending : SmartFollowState()

    /** Tracker locked onto target, ready to start following. */
    data class Tracking(val confidence: Float = 0f) : SmartFollowState()

    /** PnC engaged, robot is following the tracked subject. */
    data class Following(val confidence: Float = 0f) : SmartFollowState()

    /** User paused following (PnC stopped, tracker still active). */
    data object Paused : SmartFollowState()

    /** PnC reports FSM finished (arrived at destination / target reached). */
    data object Arrived : SmartFollowState()

    /** Tracker lost the target. */
    data object Lost : SmartFollowState()

    /** Tracker lost target while PnC was active. PnC auto-paused. */
    data object LostWhileFollowing : SmartFollowState()

    /** Gateway disconnected or unrecoverable error. */
    data class Error(val message: String) : SmartFollowState()
}

/**
 * Composite UI state for the Smart Follow screen.
 */
data class SmartFollowUiState(
    val state: SmartFollowState = SmartFollowState.Idle,
    val subjectTracking: SubjectTracking? = null,
    val isConnected: Boolean = false,
    val followActive: Boolean = false,
    val pncFsmState: Int = 0,
    val robotPoseOk: Boolean = false,
    val currentMap: String? = null
)
