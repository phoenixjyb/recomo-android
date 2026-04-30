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
 * Composition presets — names match Orin-side config keys.
 * The actual (u*, v*, s*, roll*) parameters live on Orin;
 * the App only sends the preset name.
 */
enum class CompositionPreset(
    val wireValue: String,
    val labelEn: String,
    val labelZh: String,
    val descEn: String,
    val descZh: String
) {
    CenteredFullBody("centered_full_body", "Centered", "居中全身",
        "Subject centered, 70% of frame", "目标居中，占画面70%"),
    RuleOfThirds("rule_of_thirds", "1/3 Rule", "三分法则",
        "Subject at 1/3 line, lead room auto", "目标在三分线，自动留空"),
    Headroom("headroom", "Headroom", "头顶留空",
        "Extra space above head", "头顶多留空间"),
    Dutch15("dutch_15", "Dutch", "Dutch Angle",
        "15° creative tilt", "15° 创意倾斜");
}

/**
 * Composite UI state for the Smart Follow screen.
 */
/**
 * PnC follow states from /planning/follow_state (Int32).
 * Published by follow_pnc node.
 */
enum class FollowPncState(val value: Int, val labelEn: String, val labelZh: String) {
    Idle(0, "Idle", "空闲"),
    Running(1, "Following", "跟随中"),
    Paused(2, "Paused", "已暂停"),
    LostTarget(3, "Target Lost", "目标丢失"),
    Arrived(4, "Arrived", "已到达"),
    Error(5, "PnC Error", "规划错误");

    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: Idle
    }
}

/**
 * Parsed composition quality from /smart_follow/composition_status JSON.
 */
data class CompositionQualityState(
    val qualityPct: Int = 0,
    val errorU: Int = 0,
    val errorV: Int = 0,
    val errorS: Int = 0,
    val presetName: String = "",
    val trackingState: String = ""
)

/**
 * Composite UI state for the Smart Follow screen.
 */
data class SmartFollowUiState(
    val state: SmartFollowState = SmartFollowState.Idle,
    val subjectTracking: SubjectTracking? = null,
    val isConnected: Boolean = false,
    val followActive: Boolean = false,
    val pncFsmState: Int = 0,
    val followPncState: FollowPncState = FollowPncState.Idle,
    val robotPoseOk: Boolean = false,
    val currentMap: String? = null,
    val selectedPreset: CompositionPreset = CompositionPreset.CenteredFullBody,
    val compositionQuality: CompositionQualityState? = null,
    val maxSpeed: Double = 0.8,
    val followDistance: Double = 2.0
)
