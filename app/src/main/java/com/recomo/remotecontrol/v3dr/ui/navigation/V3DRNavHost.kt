package com.recomo.remotecontrol.v3dr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recomo.remotecontrol.v3dr.ui.screens.home.HomeScreen
import com.recomo.remotecontrol.v3dr.ui.screens.recording.RecordingScreen
import com.recomo.remotecontrol.v3dr.ui.screens.recording.ArCoreRecordingScreen
import com.recomo.remotecontrol.v3dr.ui.screens.library.LibraryScreen
import com.recomo.remotecontrol.v3dr.ui.screens.playback.PlaybackScreen
import com.recomo.remotecontrol.v3dr.ui.scenes.SceneListScreen
import com.recomo.remotecontrol.v3dr.ui.screens.sceneviewer.SceneViewerScreen
import com.recomo.remotecontrol.v3dr.ui.screens.upload.UploadScreen
import com.recomo.remotecontrol.v3dr.ui.screens.settings.SettingsScreen
import com.recomo.remotecontrol.v3dr.ui.screens.vio.VioDiagnosticsScreen
import com.recomo.remotecontrol.v3dr.ui.screens.trajectory.TrajectoryViewerScreen
import com.recomo.remotecontrol.v3dr.ui.screens.auth.AuthScreen
import android.os.Environment
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Main navigation host for V3DR
 */
@Composable
fun V3DRNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToRecording = { navController.navigate(Screen.Recording.route) },
                onNavigateToArCoreRecording = { navController.navigate(Screen.ArCoreRecording.route) },
                onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                onNavigateToSceneList = { navController.navigate(Screen.SceneList.route) },
                onNavigateToTrajectoryViewer = { navController.navigate(Screen.TrajectoryViewer.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Recording.route) {
            RecordingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ArCoreRecording.route) {
            ArCoreRecordingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onRecordingClick = { recordingId ->
                    navController.navigate(Screen.Playback.createRoute(recordingId))
                },
                onNavigateToSceneViewer = { sessionId ->
                    navController.navigate(Screen.SceneViewer.createRoute(sessionId))
                },
                onNavigateToUpload = { navController.navigate(Screen.Upload.route) }
            )
        }

        composable(
            route = Screen.Playback.route,
            arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getString("recordingId") ?: return@composable
            PlaybackScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SceneList.route) {
            SceneListScreen(
                onSceneClick = { sceneId ->
                    navController.navigate(Screen.SceneViewer.createRoute(sceneId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SceneViewer.route,
            arguments = listOf(navArgument("sceneId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sceneId = backStackEntry.arguments?.getString("sceneId") ?: return@composable
            SceneViewerScreen(
                sceneId = sceneId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Upload.route) {
            UploadScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVioDiagnostics = { navController.navigate(Screen.VioDiagnostics.route) },
                onNavigateToAuth = { navController.navigate(Screen.Auth.route) }
            )
        }

        composable(Screen.VioDiagnostics.route) {
            VioDiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TrajectoryViewer.route) {
            val context = LocalContext.current
            val sessionsDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "V3DR"
            )
            TrajectoryViewerScreen(
                sessionsDir = sessionsDir,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Auth.route) {
            AuthScreen(
                onNavigateBack = { navController.popBackStack() },
                onLoginSuccess = { navController.popBackStack() }
            )
        }
    }
}
