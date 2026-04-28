package com.recomo.user.ui.screens.viewer

import com.recomo.common.sceneviewer.AnchorPose

data class SceneViewerLaunchRequest(
    val title: String,
    val entrySource: SceneViewerEntrySource,
    val trajectorySource: SceneTrajectorySource? = null,
    val sceneSource: SceneAssetSource? = null,
    val sessionId: String? = null,
    val subtitle: String? = null,
    /**
     * Optional bundled asset path of a single-line anchor TUM to use verbatim
     * instead of synthesizing one from the scene's in-memory default anchor.
     * Used by CopyStyle presets whose trajectory JSON carries a POI that
     * matches the SLAM map used for the SPZ scene.
     */
    val anchorTumAssetPath: String? = null,
    /**
     * Optional inline anchor pose. When the caller already knows the correct
     * SE(3) alignment for this scene/trajectory pair (e.g. AI chat candidates
     * whose backend supplies an anchor_pose in the protocol), pass it here
     * and the SceneViewer UI will use it as the initial anchor instead of
     * the registry lookup.
     */
    val anchorOverride: AnchorPose? = null
)

enum class SceneViewerEntrySource {
    MotionLibrary,
    CopyStyle,
    AiChat,
    LocalSession,
    Standalone
}

sealed interface SceneTrajectorySource {
    data class SessionReference(val sessionId: String) : SceneTrajectorySource
    data class TrajectoryReference(val trajectoryId: String) : SceneTrajectorySource
    data class InlineTum(val tumText: String) : SceneTrajectorySource
    data class InlineJson(val json: String) : SceneTrajectorySource
    data class LocalFile(val absolutePath: String) : SceneTrajectorySource
    /**
     * TUM trajectory bundled as an app asset under `assets/`. The launcher
     * reads the bytes via AssetManager and copies them into the cache dir so
     * the local HTTP server can serve the file.
     */
    data class AppAssetTum(val relativePath: String) : SceneTrajectorySource
}

sealed interface SceneAssetSource {
    data class RemoteUrl(val url: String) : SceneAssetSource
    data class LocalFile(val absolutePath: String) : SceneAssetSource
    data class AppAsset(val relativePath: String) : SceneAssetSource
}
