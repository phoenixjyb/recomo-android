package com.recomo.user.ui.screens.creator

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.recomo.user.R

enum class MotionCreatorMode {
    Chat,
    CopyStyle,
    Keypoint,
    Phone
}

@Immutable
data class MotionCreatorShellUiState(
    val title: String = "Motion Creator",
    val subtitle: String = "Create executable motion from chat, style presets, live keypoints, or phone teaching.",
    val statusLabel: String = "Workspace online",
    val guidanceLabel: String = "Chat is the fastest path for AI-directed motion. Copy Style and capture flows stay operator-visible in the same shell.",
    val activeMode: MotionCreatorMode = MotionCreatorMode.Chat,
    val modes: List<MotionCreatorModeItemUiState> = defaultMotionCreatorModes()
)

@Immutable
data class MotionCreatorModeItemUiState(
    val mode: MotionCreatorMode,
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    val enabled: Boolean = true,
    val badge: String? = null
)

@Immutable
data class MotionCreatorCopyPresetUiState(
    val id: String,
    val title: String,
    val description: String,
    val accentStart: Color,
    val accentEnd: Color,
    val duration: Int = 0,
    val keyframes: Int = 0,
    @DrawableRes val thumbnailRes: Int? = null,
    @DrawableRes val trajectoryRes: Int? = null,
    val videoFileName: String? = null,
    val sceneType: String = "SimpleTrack",
    val trajectoryId: String = "",
    val linkedMap: String = ""
)

fun MotionCreatorMode.defaultLabel(): String = when (this) {
    MotionCreatorMode.Chat -> "AI Chat"
    MotionCreatorMode.CopyStyle -> "Copy Style"
    MotionCreatorMode.Keypoint -> "Keypoint"
    MotionCreatorMode.Phone -> "Phone Teach"
}

fun defaultMotionCreatorModes(): List<MotionCreatorModeItemUiState> = listOf(
    MotionCreatorModeItemUiState(
        mode = MotionCreatorMode.Chat,
        shortLabel = "AI",
        title = "AI Chat",
        subtitle = "Prompt the director",
        detail = "Use the existing conversation workflow to generate and refine motion."
    ),
    MotionCreatorModeItemUiState(
        mode = MotionCreatorMode.CopyStyle,
        shortLabel = "CS",
        title = "Copy Style",
        subtitle = "Preset-led composition",
        detail = "Start from cinematic, tracking, and product-shot motion templates."
    ),
    MotionCreatorModeItemUiState(
        mode = MotionCreatorMode.Keypoint,
        shortLabel = "KP",
        title = "Keypoint",
        subtitle = "Manual pose capture",
        detail = "Build a trajectory from hand-placed robot poses and timing anchors."
    ),
    MotionCreatorModeItemUiState(
        mode = MotionCreatorMode.Phone,
        shortLabel = "PH",
        title = "Phone Teach",
        subtitle = "Device-led teaching",
        detail = "Reserve a lane for phone IMU capture when the bridge is ready.",
        badge = "Soon"
    )
)

fun defaultCopyStylePresets(): List<MotionCreatorCopyPresetUiState> = listOf(
    MotionCreatorCopyPresetUiState(
        id = "afternoon_tea",
        title = "下午茶",
        description = "Outdoor afternoon tea scene — smooth arc around subject with dessert table reveal.",
        accentStart = Color(0xFFFF8C42),
        accentEnd = Color(0xFFFFD166),
        duration = 20,
        keyframes = 8,
        thumbnailRes = R.drawable.copystyle_thumb_1,
        trajectoryRes = R.drawable.copystyle_traj_1,
        videoFileName = "copystyle_video_1.mp4",
        sceneType = "LivePnC",
        trajectoryId = "trajectory_7_from_traj_interpolated_open_loop.json",
        linkedMap = "t8space-3f"
    ),
    MotionCreatorCopyPresetUiState(
        id = "cozy_home",
        title = "Cozy Home",
        description = "Indoor living room — gentle follow shot with warm cinematic framing.",
        accentStart = Color(0xFF13C7A3),
        accentEnd = Color(0xFF00A3FF),
        duration = 25,
        keyframes = 12,
        thumbnailRes = R.drawable.copystyle_thumb_2,
        trajectoryRes = R.drawable.copystyle_traj_2,
        videoFileName = "copystyle_video_2.mp4",
        sceneType = "LivePnC",
        trajectoryId = "trajectory_8_from_traj_interpolated_open_loop.json",
        linkedMap = "t8space-3f"
    ),
    MotionCreatorCopyPresetUiState(
        id = "master_shots",
        title = "大师运镜集锦",
        description = "Multi-subject portrait showcase — tracking pass with depth separation.",
        accentStart = Color(0xFF7B5EFF),
        accentEnd = Color(0xFFE457FF),
        duration = 30,
        keyframes = 16,
        thumbnailRes = R.drawable.copystyle_thumb_3,
        trajectoryRes = R.drawable.copystyle_traj_3,
        videoFileName = "copystyle_video_3.mp4",
        sceneType = "LivePnC",
        trajectoryId = "trajectory_9_from_traj_interpolated_open_loop.json",
        linkedMap = "t8space-3f"
    ),
    MotionCreatorCopyPresetUiState(
        id = "my_new_tablet",
        title = "我的新平板",
        description = "Close-up product unboxing — orbiting reveal centered on the subject.",
        accentStart = Color(0xFF00A3FF),
        accentEnd = Color(0xFF7B5EFF),
        duration = 15,
        keyframes = 6,
        thumbnailRes = R.drawable.copystyle_thumb_4,
        trajectoryRes = R.drawable.copystyle_traj_4,
        videoFileName = "copystyle_video_4.mp4",
        sceneType = "SimpleTrack",
        trajectoryId = "simpleTrack1"
    )
)
