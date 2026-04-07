package com.recomo.remotecontrol.v3dr.ui.navigation

/**
 * Navigation routes for V3DR app
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Recording : Screen("recording")
    data object ArCoreRecording : Screen("arcore_recording")
    data object CameraSettings : Screen("camera_settings")
    data object Library : Screen("library")
    data object Playback : Screen("playback/{recordingId}") {
        fun createRoute(recordingId: String) = "playback/$recordingId"
    }
    data object SceneList : Screen("scene_list")
    data object SceneViewer : Screen("scene_viewer/{sceneId}") {
        fun createRoute(sceneId: String) = "scene_viewer/$sceneId"
    }
    data object TrajectoryViewer : Screen("trajectory_viewer")
    data object Upload : Screen("upload")
    data object Settings : Screen("settings")
    data object VioDiagnostics : Screen("vio_diagnostics")
    data object Auth : Screen("auth")
}
