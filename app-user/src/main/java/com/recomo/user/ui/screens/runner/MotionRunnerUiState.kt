package com.recomo.user.ui.screens.runner

import android.graphics.Bitmap

enum class MotionRunnerPlaybackState {
    Idle,
    Loading,
    Homing,
    Initing,
    Running,
    Paused,
    Completed,
    Stopped,
    Error
}

enum class MotionRunnerTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Danger
}

enum class MotionRunnerMetricIcon {
    Signal,
    Battery,
    Latency,
    Speed,
    Clock,
    Shield,
    Temperature,
    Power
}

enum class MotionRunnerActionStyle {
    Primary,
    Secondary,
    Danger,
    Subtle
}

enum class MotionRunnerActionIcon {
    Back,
    Play,
    Pause,
    Stop,
    Home,
    Restart,
    Check,
    Shield
}

enum class MotionRunnerOverlayVisual {
    Paused,
    Completed,
    Stopped,
    Error
}

data class MotionRunnerMetricUiState(
    val label: String,
    val value: String,
    val tone: MotionRunnerTone = MotionRunnerTone.Neutral,
    val icon: MotionRunnerMetricIcon? = null
)

data class MotionRunnerTimelineItemUiState(
    val indexLabel: String,
    val timeLabel: String,
    val isCurrent: Boolean = false,
    val isComplete: Boolean = false
)

data class MotionRunnerActionUiState(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val style: MotionRunnerActionStyle = MotionRunnerActionStyle.Secondary,
    val icon: MotionRunnerActionIcon? = null
)

data class MotionRunnerSupportBadgeUiState(
    val label: String,
    val tone: MotionRunnerTone = MotionRunnerTone.Neutral
)

data class MotionRunnerSupportCardUiState(
    val title: String,
    val value: String,
    val detail: String? = null,
    val tone: MotionRunnerTone = MotionRunnerTone.Neutral
)

data class MotionRunnerFeedOverlayUiState(
    val visual: MotionRunnerOverlayVisual,
    val title: String,
    val detail: String? = null,
    val actionId: String? = null,
    val actionLabel: String? = null,
    val actionEnabled: Boolean = true
)

data class MotionRunnerVideoUiState(
    val bitmapFrame: Bitmap? = null,
    val showSurfaceFeed: Boolean = true,
    val headerPillLabel: String = "Live Feed",
    val headerCaption: String? = null,
    val emptyTitle: String = "Preview idle",
    val emptyDetail: String? = null,
    val progress: Float = 0f,
    val topMetrics: List<MotionRunnerMetricUiState> = emptyList(),
    val supportBadges: List<MotionRunnerSupportBadgeUiState> = emptyList(),
    val supportCards: List<MotionRunnerSupportCardUiState> = emptyList(),
    val overlay: MotionRunnerFeedOverlayUiState? = null
)

data class MotionRunnerSafetyUiState(
    val title: String = "Emergency Stop",
    val label: String = "E-STOP",
    val hint: String? = null,
    val holdProgress: Float = 0f,
    val isHolding: Boolean = false,
    val isLatched: Boolean = false,
    val enabled: Boolean = true
)

data class MotionRunnerUiState(
    val motionName: String,
    val motionSubtitle: String,
    val playbackState: MotionRunnerPlaybackState = MotionRunnerPlaybackState.Idle,
    val playbackLabel: String = "Idle",
    val playbackTone: MotionRunnerTone = MotionRunnerTone.Neutral,
    val playbackDetail: String? = null,
    val progress: Float = 0f,
    val elapsedLabel: String = "--",
    val remainingLabel: String = "--",
    val keyframeLabel: String = "--",
    val speedLabel: String = "1.0x",
    val leftMetrics: List<MotionRunnerMetricUiState> = emptyList(),
    val timelineTitle: String = "Keyframes",
    val timelineItems: List<MotionRunnerTimelineItemUiState> = emptyList(),
    val leftFooterActions: List<MotionRunnerActionUiState> = emptyList(),
    val video: MotionRunnerVideoUiState = MotionRunnerVideoUiState(),
    val rightRailTitle: String = "Safety & Actions",
    val safety: MotionRunnerSafetyUiState = MotionRunnerSafetyUiState(),
    val rightRailActions: List<MotionRunnerActionUiState> = emptyList(),
    val rightRailStats: List<MotionRunnerMetricUiState> = emptyList(),
    val isStudioDance: Boolean = false,
    val musicProgress: Float = 0f,
    val musicTimeLabel: String = "--",
    val musicFileName: String? = null
)
