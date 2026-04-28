package com.recomo.user.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.recomo.common.chat.PreviewTrajectoryState
import com.recomo.common.chat.ChatViewModel
import com.recomo.common.chat.TrajectoryAttachment
import com.recomo.common.chat.TrajectoryCandidate
import com.recomo.common.chat.TrajectoryDownloadResult
import com.recomo.common.sceneviewer.AnchorPose as CommonAnchorPose
import com.recomo.common.model.RobotProfile
import com.recomo.common.model.VideoSource
import com.recomo.common.preview.TrajectoryPreview
import com.recomo.user.R
import com.recomo.user.data.media.UserMediaItem
import com.recomo.user.data.trajectory.LocalTrajectorySessionSummary
import com.recomo.user.control.UserConnectionStatus
import com.recomo.user.control.UserGatewayViewModel
import com.recomo.user.control.UserLibrarySessionDetail
import com.recomo.user.control.UserLibraryViewModel
import com.recomo.user.control.UserMapLocalizationViewModel
import com.recomo.user.control.UserNavigationViewModel
import com.recomo.user.control.UserMediaGalleryFilter
import com.recomo.user.control.UserMediaGallerySort
import com.recomo.user.control.UserMediaGalleryUiState
import com.recomo.user.control.UserMediaGalleryViewModel
import com.recomo.user.control.UserMediaGalleryViewMode
import com.recomo.user.control.UserPostRecordUiState
import com.recomo.user.control.UserPostRecordViewModel
import com.recomo.user.control.UserTrajectoryHandoffReadiness
import com.recomo.user.control.UserRunCommandViewModel
import com.recomo.user.control.UserLocalSessionPreviewState
import com.recomo.user.control.UserRunVideoDetailState
import com.recomo.user.control.UserRunVideoUiState
import com.recomo.user.control.UserRunVideoViewModel
import com.recomo.user.control.UserSystemViewModel
import com.recomo.user.control.UserTouchControlViewModel
import com.recomo.user.ui.screens.chat.ChatScreen
import com.recomo.user.phoneteach.PhoneTeachNavHost
import com.recomo.user.ui.screens.creator.MotionCreatorMode
import com.recomo.user.ui.screens.creator.MotionCreatorModeItemUiState
import com.recomo.user.ui.screens.creator.MotionCreatorScreen
import com.recomo.user.ui.screens.smartfollow.SmartFollowRoute
import com.recomo.user.ui.screens.creator.MotionCreatorShellUiState
import com.recomo.user.ui.screens.control.ControlBasePoseUiState
import com.recomo.user.ui.screens.control.ControlOverviewUiState
import com.recomo.user.ui.screens.control.ControlSafetyUiState
import com.recomo.user.ui.screens.control.ControlTrackingSummaryUiState
import com.recomo.user.ui.screens.control.HomeReadinessPanel
import com.recomo.user.ui.screens.control.HomeOperationsUiState
import com.recomo.user.ui.screens.library.LibrarySessionSummaryUiItem
import com.recomo.user.ui.screens.library.LibrarySessionType
import com.recomo.user.ui.screens.library.LibrarySummaryUiState
import com.recomo.user.ui.screens.library.MotionDetailItem
import com.recomo.user.ui.screens.library.MotionDetailOverlay
import com.recomo.user.ui.screens.library.MotionLibraryViewMode
import com.recomo.user.ui.screens.library.MotionLibraryWorkspace
import com.recomo.user.ui.screens.library.displayGroupLabel
import com.recomo.user.ui.screens.library.displayMotionTitle
import com.recomo.user.ui.screens.library.toMotionDetailItem
import com.recomo.user.ui.screens.library.toMotionLibraryWorkspaceState
import com.recomo.user.ui.screens.main.MainControlBottomDockUiState
import com.recomo.user.ui.screens.main.MainControlHeaderUiState
import com.recomo.user.ui.screens.main.MainControlJointUiState
import com.recomo.user.ui.screens.main.MainControlMetricUiState
import com.recomo.user.ui.screens.main.MainControlPreviewUiState
import com.recomo.user.ui.screens.main.MainControlPrimaryActionUiState
import com.recomo.user.ui.screens.main.MainControlRecentMotionUiState
import com.recomo.user.ui.screens.main.MainControlScreen
import com.recomo.user.ui.screens.main.MainControlShortcutKey
import com.recomo.user.ui.screens.main.MainControlShortcutUiState
import com.recomo.user.ui.screens.main.MainControlSidebarUiState
import com.recomo.user.ui.screens.main.MainControlToolButtonUiState
import com.recomo.user.ui.screens.main.MainControlToolKey
import com.recomo.user.ui.screens.main.MainControlTone
import com.recomo.user.ui.screens.main.MainControlTransportControlKey
import com.recomo.user.ui.screens.main.MainControlTransportControlUiState
import com.recomo.user.ui.screens.main.MainControlUiState
import com.recomo.user.ui.screens.common.EmergencyStopOverlay
import com.recomo.user.ui.screens.common.SystemHealthBar
import com.recomo.user.ui.screens.common.SystemHealthDetail
import com.recomo.user.ui.screens.common.SystemHealthViewModel
import com.recomo.user.ui.screens.common.VideoPreviewContent
import com.recomo.user.ui.screens.connection.ConnectionScreen
import com.recomo.user.ui.screens.main.NavigationPanel
import com.recomo.user.control.UserNavState
import com.recomo.user.control.UserOperationMode
import com.recomo.user.control.UserPoiSession
import com.recomo.user.ui.screens.map.MapLocalizationPanel
import com.recomo.user.ui.screens.map.MapLocalizationUiState
import com.recomo.user.ui.screens.map.MapMatchDialog
import com.recomo.user.ui.screens.map.MapMatchDialogUiState
import com.recomo.user.ui.screens.map.MapMatchPhase
import com.recomo.user.ui.screens.map.deriveMapMatchPhase
import com.recomo.user.ui.screens.map.toMapMatchOptions
import com.recomo.user.ui.screens.map.SlamMapsWorkspace
import com.recomo.user.ui.screens.media.MediaGalleryWorkspace
import com.recomo.user.ui.screens.postrecord.PostRecordWorkspace
import com.recomo.user.ui.screens.preview.TrajectoryPreviewScreen
import com.recomo.user.ui.screens.runner.MotionRunnerActionIcon
import com.recomo.user.ui.screens.runner.MotionRunnerActionStyle
import com.recomo.user.ui.screens.runner.MotionRunnerActionUiState
import com.recomo.user.ui.screens.runner.MotionRunnerFeedOverlayUiState
import com.recomo.user.ui.screens.runner.MotionRunnerMetricIcon
import com.recomo.user.ui.screens.runner.MotionRunnerMetricUiState
import com.recomo.user.ui.screens.runner.MotionRunnerOverlayVisual
import com.recomo.user.ui.screens.runner.MotionRunnerPlaybackState
import com.recomo.user.ui.screens.runner.MotionRunnerSafetyUiState
import com.recomo.user.ui.screens.runner.MotionRunnerScreen
import com.recomo.user.ui.screens.runner.MotionRunnerSupportBadgeUiState
import com.recomo.user.ui.screens.runner.MotionRunnerSupportCardUiState
import com.recomo.user.ui.screens.runner.MotionRunnerTimelineItemUiState
import com.recomo.user.ui.screens.runner.MotionRunnerTone
import com.recomo.user.ui.screens.runner.MotionRunnerUiState
import com.recomo.user.ui.screens.runner.MotionRunnerVideoUiState
import com.recomo.user.ui.screens.run.RunChecklistUiState
import com.recomo.user.ui.screens.run.RunWorkspaceUiState
import com.recomo.user.ui.screens.run.RunVideoSurfaceView
import com.recomo.user.ui.screens.run.TrajectoryHandoffCardUiState
import com.recomo.user.ui.screens.run.TrajectoryHandoffReadiness
import com.recomo.user.ui.screens.settings.SettingsPanel
import com.recomo.user.ui.screens.settings.SettingsUiState
import com.recomo.user.ui.screens.touch.TouchControlScreen
import com.recomo.user.ui.screens.touch.TouchControlSpeedMode
import com.recomo.user.ui.screens.touch.TouchControlWorkspaceState
import com.recomo.user.ui.screens.viewer.SceneTrajectorySource
import com.recomo.user.ui.screens.viewer.SceneAssetSource
import com.recomo.user.ui.screens.viewer.SceneViewerEntrySource
import com.recomo.user.ui.screens.viewer.SceneViewerLaunchRequest
import com.recomo.user.ui.screens.viewer.SceneViewerScreen
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun UserMainScreen() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val shellViewModel: UserMainViewModel = hiltViewModel()
    val controlViewModel: UserGatewayViewModel = hiltViewModel()
    val libraryViewModel: UserLibraryViewModel = hiltViewModel()
    val mapViewModel: UserMapLocalizationViewModel = hiltViewModel()
    val runViewModel: UserRunCommandViewModel = hiltViewModel()
    val runVideoViewModel: UserRunVideoViewModel = hiltViewModel()
    val systemViewModel: UserSystemViewModel = hiltViewModel()
    val touchControlViewModel: UserTouchControlViewModel = hiltViewModel()
    val mediaGalleryViewModel: UserMediaGalleryViewModel = hiltViewModel()
    val postRecordViewModel: UserPostRecordViewModel = hiltViewModel()
    val navigationViewModel: UserNavigationViewModel = hiltViewModel()
    val chatViewModel: ChatViewModel = hiltViewModel()
    val systemHealthViewModel: SystemHealthViewModel = hiltViewModel()
    val musicPlayerViewModel: com.recomo.user.control.StudioDanceMusicPlayerViewModel = hiltViewModel()
    var hasCameraPermission by remember(context) {
        mutableStateOf(context.hasCameraPermission())
    }
    val stage by shellViewModel.stage.collectAsState()
    val connectionStatus by shellViewModel.connectionStatus.collectAsState()
    val selectedRobot by shellViewModel.selectedRobot.collectAsState()
    val availableRobots by shellViewModel.availableRobots.collectAsState()
    val chatServerUrl by shellViewModel.chatServerUrl.collectAsState()
    val sessionFolderPath by shellViewModel.sessionFolderPath.collectAsState()
    val sceneViewerFolderPath by shellViewModel.sceneViewerFolderPath.collectAsState()
    val useWebRTC by shellViewModel.useWebRTC.collectAsState()
    val videoSource by shellViewModel.videoSource.collectAsState()
    val hdmiDeviceAvailable by runVideoViewModel.videoSourceManager.hdmiDeviceAvailable.collectAsState()
    val speedOverrides by shellViewModel.speedTierOverrides.collectAsState()
    val route by shellViewModel.route.collectAsState()
    val settingsOpen by shellViewModel.settingsOpen.collectAsState()
    val statusMessage by shellViewModel.statusMessage.collectAsState()
    val sceneViewerRequest by shellViewModel.sceneViewerRequest.collectAsState()
    val previewTrajectory by chatViewModel.previewTrajectory.collectAsState()
    val controlConnectionStatus by controlViewModel.connectionStatus.collectAsState()
    val stateHz by controlViewModel.stateHz.collectAsState()
    val lastStateAgeMs by controlViewModel.lastStateAgeMs.collectAsState()
    val basePose by controlViewModel.basePose.collectAsState()
    val safetyFlags by controlViewModel.safetyFlags.collectAsState()
    val hasMapPose by controlViewModel.hasMapPose.collectAsState()
    val trackingSummary by controlViewModel.trackingSummary.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val librarySessionDetail by libraryViewModel.sessionDetail.collectAsState()
    val mapState by mapViewModel.state.collectAsState()
    val runCommandState by runViewModel.state.collectAsState()
    val currentSceneType by runViewModel.sceneType.collectAsState()
    val selectedTrajectory by runViewModel.selectedTrajectory.collectAsState()
    val trajectoryHandoff by runViewModel.trajectoryHandoff.collectAsState()
    val localSessions by runViewModel.localSessions.collectAsState()
    val localSessionPreview by runViewModel.localSessionPreview.collectAsState()
    val runVideoState by runVideoViewModel.uiState.collectAsState()
    val runVideoBitmap by runVideoViewModel.latestBitmap.collectAsState()
    val musicState by musicPlayerViewModel.state.collectAsState()
    val systemInfoState by systemViewModel.infoCardState.collectAsState()
    val systemLoadState by systemViewModel.systemLoadState.collectAsState()
    val gatewayControlState by systemViewModel.gatewayControlState.collectAsState()
    val gatewayServiceState by systemViewModel.gatewayServiceState.collectAsState()
    val robotPreparationState by systemViewModel.robotPreparationState.collectAsState()
    val actuatorCardState by systemViewModel.actuatorCardState.collectAsState()
    val sceneCardState by systemViewModel.sceneCardState.collectAsState()
    val autonomyCardState by systemViewModel.autonomyCardState.collectAsState()
    val cloudCardState by systemViewModel.cloudCardState.collectAsState()
    val topStatusHighlights by systemViewModel.topStatusHighlights.collectAsState()
    val touchControlState by touchControlViewModel.workspaceState.collectAsState()
    val mediaGalleryState by mediaGalleryViewModel.uiState.collectAsState()
    val postRecordState by postRecordViewModel.uiState.collectAsState()
    val navState by navigationViewModel.navState.collectAsState()
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        runVideoViewModel.onCameraPermissionResult(granted)
    }
    val connectionLabelConnected = stringResource(R.string.connection_status_connected)
    val connectionLabelConnecting = stringResource(R.string.connection_status_connecting)
    val connectionLabelDisconnected = stringResource(R.string.connection_status_disconnected)
    val runReadyLabel = stringResource(R.string.run_ready)
    val runRunningLabel = stringResource(R.string.run_running)
    val runPausedLabel = stringResource(R.string.run_paused)
    val runLoadLabel = stringResource(R.string.run_load)
    val runRunActionLabel = stringResource(R.string.run_run)
    val runResumeActionLabel = stringResource(R.string.run_resume)
    val mapLocalizedLabel = stringResource(R.string.map_localized)
    val mapNotReadyLabel = stringResource(R.string.map_not_ready)
    val libraryTitle = stringResource(R.string.library_title)
    val libraryEmptyLabel = stringResource(R.string.library_no_data)
    val runChecklistSessionMissingLabel = stringResource(R.string.run_checklist_session_missing)
    val runChecklistSafetyClearLabel = stringResource(R.string.run_checklist_safety_clear)
    val runChecklistGatewayLabel = stringResource(R.string.run_checklist_gateway)
    val runChecklistRobotLabel = stringResource(R.string.run_checklist_robot)
    val runChecklistLocalizationLabel = stringResource(R.string.run_checklist_localization)
    val runChecklistSessionLabel = stringResource(R.string.run_checklist_session)
    val runEstopLabel = stringResource(R.string.run_estop)
    val runGuideConnectLabel = stringResource(R.string.run_guidance_connect_gateway)
    val runGuidePrepareLabel = stringResource(R.string.run_guidance_prepare_robot)
    val runGuideLocalizeLabel = stringResource(R.string.run_guidance_localize_robot)
    val runGuideSelectLabel = stringResource(R.string.run_guidance_select_session)
    val runGuideClearEstopLabel = stringResource(R.string.run_guidance_clear_estop)
    val runGuideLoadLabel = stringResource(R.string.run_guidance_load_session)
    val runGuideReloadLabel = stringResource(R.string.run_guidance_reload_session)
    val runGuideResumeLabel = stringResource(R.string.run_guidance_resume)
    val runGuideRunningLabel = stringResource(R.string.run_guidance_running)
    val runGuideReadyLabel = stringResource(R.string.run_guidance_ready)
    val runLoadedSelectedLabel = stringResource(R.string.run_loaded_selected)
    val runLoadedOtherLabel = stringResource(R.string.run_loaded_other)
    val runLoadedNoneLabel = stringResource(R.string.run_loaded_none)
    val statusVideoLiveLabel = stringResource(R.string.status_video_live)
    val statusVideoWaitingLabel = stringResource(R.string.status_video_waiting)
    val statusVideoOfflineLabel = stringResource(R.string.status_video_offline)
    val statusVideoErrorLabel = stringResource(R.string.status_video_error)
    val localPreviewTitleLabel = stringResource(R.string.run_preview_local_session)
    val scenePreviewLabel = stringResource(R.string.scene_viewer_open)
    val touchTitle = stringResource(R.string.touch_title)
    val touchSlowLabel = stringResource(R.string.touch_slow)
    val touchNormalLabel = stringResource(R.string.touch_normal)
    val touchFastLabel = stringResource(R.string.touch_fast)
    val touchActiveLabel = stringResource(R.string.touch_active)
    val touchChassisLabel = stringResource(R.string.touch_chassis)
    val touchChassisSubLabel = stringResource(R.string.touch_chassis_sub)
    val touchArmLabel = stringResource(R.string.touch_arm)
    val touchArmSubLabel = stringResource(R.string.touch_arm_sub)
    val touchGimbalLabel = stringResource(R.string.touch_gimbal)
    val touchGimbalSubLabel = stringResource(R.string.touch_gimbal_sub)
    val touchConnectionLabel = stringResource(R.string.touch_connection)
    val touchSafetyLabel = stringResource(R.string.touch_safety)
    val touchYawLabel = stringResource(R.string.touch_yaw)
    val touchArmHomeLabel = stringResource(R.string.touch_arm_home)
    val touchGimbalHomeLabel = stringResource(R.string.touch_gimbal_home)
    val touchStopLabel = stringResource(R.string.touch_stop)
    val touchEstopLabel = stringResource(R.string.run_estop)
    val touchClearEstopLabel = stringResource(R.string.run_clear_estop)
    val touchFreezeLabel = stringResource(R.string.run_freeze_all)
    val touchUnfreezeLabel = stringResource(R.string.run_unfreeze_all)
    val mainPoseLockedLabel = stringResource(R.string.main_pose_locked)
    val mainPoseSearchLabel = stringResource(R.string.main_pose_search)
    val mainLinkReadyLabel = stringResource(R.string.main_link_ready)
    val mainLinkIdleLabel = stringResource(R.string.main_link_idle)
    val mainLinkMetricLabel = stringResource(R.string.main_link_label)
    val mainPoseMetricLabel = stringResource(R.string.main_pose_label)
    val mainDelayMetricLabel = stringResource(R.string.main_delay_label)
    val mainFpsMetricLabel = stringResource(R.string.main_fps_label)
    val mainResMetricLabel = stringResource(R.string.main_res_label)
    val mainCodecMetricLabel = stringResource(R.string.main_codec_label)
    val mainStateMetricLabel = stringResource(R.string.main_state_label)
    val mainVideoMetricLabel = stringResource(R.string.main_video_label)
    val mainPreviewIdleLabel = stringResource(R.string.main_preview_idle)
    val mainCreateDetailLabel = stringResource(R.string.main_create_detail)
    val mainTouchDetailLabel = stringResource(R.string.main_touch_detail)
    val mainRecordLabel = stringResource(R.string.main_record_label)
    val mainStopRecordLabel = stringResource(R.string.main_stop_record_label)
    val mainRecordingBadge = runVideoState.recordingElapsedLabel?.let {
        stringResource(R.string.main_recording_badge, it)
    }
    var mapMatchOpen by rememberSaveable { mutableStateOf(false) }
    var motionDetailItem by remember { mutableStateOf<com.recomo.user.ui.screens.library.MotionDetailItem?>(null) }
    var localizationSkipped by rememberSaveable { mutableStateOf(false) }
    var showCountdown by remember { mutableStateOf(false) }
    val countdownDuration by shellViewModel.countdownDurationSeconds.collectAsState(initial = 3)

    val copyStyleDir = remember { mediaGalleryViewModel.copyStyleDirectory }

    val overviewState = remember(
        selectedRobot.label,
        controlConnectionStatus,
        stateHz,
        lastStateAgeMs,
        basePose,
        safetyFlags,
        hasMapPose,
        trackingSummary
    ) {
        ControlOverviewUiState(
            robotName = selectedRobot.label,
            connectionLabel = when (controlConnectionStatus) {
                UserConnectionStatus.Connected -> connectionLabelConnected
                UserConnectionStatus.Connecting -> connectionLabelConnecting
                UserConnectionStatus.Disconnected -> connectionLabelDisconnected
            },
            isConnected = controlConnectionStatus == UserConnectionStatus.Connected,
            stateHz = stateHz,
            lastStateAgeMs = lastStateAgeMs,
            basePose = basePose?.let { ControlBasePoseUiState(it.x, it.y, it.yawDeg) },
            safety = safetyFlags?.let { ControlSafetyUiState(it.estop, it.deadmanOk, it.commOk) },
            mapPoseAvailable = hasMapPose,
            trackingSummary = trackingSummary?.let {
                ControlTrackingSummaryUiState(it.label, it.state)
            }
        )
    }

    val mapUiState = remember(mapState) {
        MapLocalizationUiState(
            availableLocations = mapState.availableLocations,
            availableMapAssets = mapState.availableMapAssets,
            selectedLocation = mapState.selectedLocation,
            selectedMapAsset = mapState.selectedMapAsset,
            robotPoseOk = mapState.robotPoseOk,
            poseStable = mapState.poseStable,
            poseStableAgeMs = mapState.poseStableAgeMs,
            prepareElapsedMs = mapState.prepareElapsedMs,
            lastActionFailed = mapState.lastActionFailed,
            lastActionError = mapState.lastActionError,
            localizationStatus = if (mapState.localized) mapLocalizedLabel else mapNotReadyLabel,
            backendActionMessage = mapState.latestMessage,
            isBusy = mapState.isBusy
        )
    }

    val isLivePnCScene = currentSceneType == com.recomo.user.control.SceneType.LivePnC
    val skipBlockedReasonLivePnC = stringResource(R.string.map_match_skip_blocked_livepnc)

    val mapMatchDialogState = remember(mapUiState, isLivePnCScene, skipBlockedReasonLivePnC) {
        MapMatchDialogUiState(
            phase = deriveMapMatchPhase(mapUiState, mapUiState.prepareElapsedMs),
            selectedLocation = mapUiState.selectedLocation,
            selectedMapAsset = mapUiState.selectedMapAsset,
            availableLocations = mapUiState.availableLocations,
            availableMapAssets = mapUiState.availableMapAssets,
            message = mapUiState.backendActionMessage,
            maps = mapUiState.toMapMatchOptions(),
            selectedMapId = mapUiState.selectedMapAsset,
            prepareElapsedMs = mapUiState.prepareElapsedMs ?: 0L,
            poseStableAgeMs = mapUiState.poseStableAgeMs,
            skipBlocked = isLivePnCScene,
            skipBlockedReason = if (isLivePnCScene) skipBlockedReasonLivePnC else null,
            errorMessage = mapUiState.lastActionError
        )
    }

    val runChecklistState = remember(
        connectionStatus,
        robotPreparationState,
        mapUiState,
        selectedTrajectory,
        trajectoryHandoff,
        runCommandState,
        runChecklistSessionMissingLabel,
        runChecklistSafetyClearLabel,
        runEstopLabel,
        localizationSkipped
    ) {
        RunChecklistUiState(
            gatewayReady = connectionStatus == UserConnectionStatus.Connected,
            gatewayDetail = when (connectionStatus) {
                UserConnectionStatus.Connected -> connectionLabelConnected
                UserConnectionStatus.Connecting -> connectionLabelConnecting
                UserConnectionStatus.Disconnected -> connectionLabelDisconnected
            },
            robotReady = robotPreparationState.statusLabel.equals("Ready", ignoreCase = true) ||
                robotPreparationState.canDismiss,
            robotDetail = robotPreparationState.statusLabel,
            localizationReady = mapUiState.robotPoseOk || localizationSkipped,
            localizationDetail = if (localizationSkipped && !mapUiState.robotPoseOk) "Skipped" else mapUiState.localizationStatus,
            sessionReady = when {
                trajectoryHandoff?.readiness == UserTrajectoryHandoffReadiness.Ready -> true
                trajectoryHandoff != null -> false
                !selectedTrajectory.isNullOrBlank() -> true
                else -> false
            },
            sessionDetail = when {
                trajectoryHandoff?.readiness == UserTrajectoryHandoffReadiness.Ready -> trajectoryHandoff?.sourceName.orEmpty().ifBlank { selectedTrajectory.orEmpty() }
                trajectoryHandoff != null -> trajectoryHandoff?.note.orEmpty().ifBlank {
                    trajectoryHandoff?.sourceName.orEmpty().ifBlank { selectedTrajectory.orEmpty() }
                }
                !selectedTrajectory.isNullOrBlank() -> selectedTrajectory.orEmpty()
                else -> runChecklistSessionMissingLabel
            },
            safetyReady = !runCommandState.estopActive,
            safetyDetail = if (runCommandState.estopActive) {
                runEstopLabel
            } else {
                runChecklistSafetyClearLabel
            }
        )
    }

    val runWorkspaceState = remember(
        runCommandState,
        selectedTrajectory,
        trajectoryHandoff,
        currentSceneType,
        runChecklistState,
        runGuideConnectLabel,
        runGuidePrepareLabel,
        runGuideLocalizeLabel,
        runGuideSelectLabel,
        runGuideClearEstopLabel,
        runGuideLoadLabel,
        runGuideReloadLabel,
        runGuideResumeLabel,
        runGuideRunningLabel,
        runGuideReadyLabel,
        runLoadedSelectedLabel,
        runLoadedOtherLabel,
        runLoadedNoneLabel,
        runLoadLabel,
        runRunActionLabel,
        runResumeActionLabel,
        runChecklistGatewayLabel,
        runChecklistRobotLabel,
        runChecklistLocalizationLabel,
        runChecklistSessionLabel
    ) {
        val handoff = trajectoryHandoff
        val selectedSessionId = when {
            handoff?.readiness == UserTrajectoryHandoffReadiness.Ready && handoff.sessionId.isNotBlank() ->
                handoff.sessionId
            !selectedTrajectory.isNullOrBlank() -> selectedTrajectory
            else -> null
        }
        // Display override: when the handoff supplied a user-facing
        // sourceName (AI chat candidates show their "aigen-..." name),
        // prefer that in UI labels even though FixedPositionCmd on the
        // wire still uses selectedSessionId. Match-checks remain on the
        // underlying id so loadedMatchesSelection stays correct.
        val selectedSessionDisplay = when {
            handoff?.readiness == UserTrajectoryHandoffReadiness.Ready &&
                handoff.sourceName.isNotBlank() -> handoff.sourceName
            else -> selectedSessionId
        }
        val loadedSessionId = runCommandState.loadedSessionId
        val loadedSessionName = runCommandState.loadedSessionName
        val isLoaded = !runCommandState.loadedSessionId.isNullOrBlank() ||
            !runCommandState.loadedSessionName.isNullOrBlank() ||
            runCommandState.statusLabel.equals("Loaded", ignoreCase = true)
        val loadedMatchesSelection = when {
            selectedSessionId.isNullOrBlank() -> false
            loadedSessionId.isNullOrBlank() && loadedSessionName.isNullOrBlank() -> false
            loadedSessionId == selectedSessionId -> true
            loadedSessionName == selectedSessionId -> true
            else -> false
        }
        // SimpleTrack only needs gateway + session; no driver preparation or localization
        val isSimpleTrack = currentSceneType == com.recomo.user.control.SceneType.SimpleTrack
        val isLivePnC = currentSceneType == com.recomo.user.control.SceneType.LivePnC
        // LivePnC can run open-loop without localization; SimpleTrack needs no robot prep
        val coreReady = when {
            isSimpleTrack -> runChecklistState.gatewayReady && runChecklistState.sessionReady && runChecklistState.safetyReady
            isLivePnC -> runChecklistState.gatewayReady && runChecklistState.robotReady &&
                runChecklistState.sessionReady && runChecklistState.safetyReady
            else -> runChecklistState.allReady
        }
        val canLoad = coreReady &&
            !runCommandState.isRunning &&
            !runCommandState.isPaused &&
            (!isLoaded || !loadedMatchesSelection)
        val canHome = runChecklistState.gatewayReady &&
            (isSimpleTrack || runChecklistState.robotReady) &&
            runChecklistState.safetyReady &&
            !runCommandState.isRunning && !runCommandState.isHoming
        // LivePnC: init requires homing done (not actively homing)
        val canInit = runChecklistState.gatewayReady &&
            (isSimpleTrack || runChecklistState.robotReady) &&
            !runCommandState.isRunning && !runCommandState.isIniting &&
            (!isLivePnC || (!runCommandState.isHoming && isLoaded))
        // LivePnC: run requires executor reached wait-trigger (state >= 2) after homing+init
        val canRun = if (isLivePnC) {
            coreReady && !runCommandState.isRunning &&
                (runCommandState.isPaused || runCommandState.executorState >= 2)
        } else {
            coreReady &&
                (runCommandState.isPaused || (isLoaded && loadedMatchesSelection)) &&
                !runCommandState.isRunning
        }
        val canPause = runCommandState.isRunning
        val canStop = runCommandState.isRunning || runCommandState.isPaused || isLoaded ||
            runCommandState.isIniting || runCommandState.isHoming
        RunWorkspaceUiState(
            trajectoryText = selectedSessionDisplay.orEmpty(),
            statusLabel = runCommandState.statusLabel.ifBlank {
                when {
                    runCommandState.isPaused -> runPausedLabel
                    runCommandState.isRunning -> runRunningLabel
                    else -> runReadyLabel
                }
            },
            guidanceLabel = when {
                !runChecklistState.gatewayReady -> runGuideConnectLabel
                !runChecklistState.robotReady -> runGuidePrepareLabel
                !runChecklistState.localizationReady -> runGuideLocalizeLabel
                !runChecklistState.sessionReady -> runGuideSelectLabel
                !runChecklistState.safetyReady -> runGuideClearEstopLabel
                runCommandState.isHoming -> "Homing arm & gimbal…"
                runCommandState.isIniting -> "Moving to start position…"
                isLoaded && !loadedMatchesSelection && !runCommandState.isPaused && !runCommandState.isRunning -> runGuideReloadLabel
                !isLoaded && !runCommandState.isPaused && !runCommandState.isRunning -> runGuideLoadLabel
                isLivePnC && isLoaded && !runCommandState.isHoming && runCommandState.executorState < 1 && !runCommandState.isRunning -> "Press Home to begin"
                isLivePnC && isLoaded && runCommandState.executorState >= 1 && runCommandState.executorState < 2 && !runCommandState.isIniting && !runCommandState.isRunning -> "Press Init to move to start"
                isLivePnC && runCommandState.executorState >= 2 && !runCommandState.isRunning && !runCommandState.isPaused -> "Ready — press Run"
                runCommandState.isPaused -> runGuideResumeLabel
                runCommandState.isRunning -> runGuideRunningLabel
                else -> runGuideReadyLabel
            },
            progress = runCommandState.progress,
            elapsedLabel = null,
            remainingLabel = null,
            selectedTrajectoryLabel = selectedSessionDisplay,
            // When a display override is active, show it for the loaded
            // label too so "running …" in the runner page reads the
            // AI candidate name, not the masked wire id.
            loadedTrajectory = if (selectedSessionDisplay != selectedSessionId && loadedMatchesSelection)
                selectedSessionDisplay
            else loadedSessionName ?: loadedSessionId ?: runCommandState.selectedTrajectory,
            loadedTrajectoryMatchesSelection = loadedMatchesSelection,
            loadedTrajectoryDetail = when {
                isLoaded && loadedMatchesSelection -> runLoadedSelectedLabel
                isLoaded -> runLoadedOtherLabel
                else -> runLoadedNoneLabel
            },
            phaseChips = if (isLivePnC) {
                // LivePnC: Gateway → Robot → Localization → Load → Homing → Init → Run
                val homingDone = !runCommandState.isHoming && runCommandState.executorState >= 1
                val initDone = !runCommandState.isIniting && runCommandState.executorState >= 2
                listOf(
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistGatewayLabel,
                        active = !runChecklistState.gatewayReady,
                        complete = runChecklistState.gatewayReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistRobotLabel,
                        active = runChecklistState.gatewayReady && !runChecklistState.robotReady,
                        complete = runChecklistState.robotReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistLocalizationLabel,
                        active = runChecklistState.gatewayReady && runChecklistState.robotReady && !runChecklistState.localizationReady,
                        complete = runChecklistState.localizationReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runLoadLabel,
                        active = coreReady && !isLoaded && !runCommandState.isRunning,
                        complete = isLoaded
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = "Homing",
                        active = runCommandState.isHoming,
                        complete = homingDone && isLoaded
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = "Init",
                        active = runCommandState.isIniting,
                        complete = initDone && isLoaded
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = if (runCommandState.isPaused) runResumeActionLabel else runRunActionLabel,
                        active = initDone && isLoaded && !runCommandState.isRunning && !runCommandState.isPaused,
                        complete = runCommandState.isRunning
                    )
                )
            } else {
                listOf(
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistGatewayLabel,
                        active = !runChecklistState.gatewayReady,
                        complete = runChecklistState.gatewayReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistRobotLabel,
                        active = runChecklistState.gatewayReady && !runChecklistState.robotReady,
                        complete = runChecklistState.robotReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistLocalizationLabel,
                        active = runChecklistState.gatewayReady && runChecklistState.robotReady && !runChecklistState.localizationReady,
                        complete = runChecklistState.localizationReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runChecklistSessionLabel,
                        active = runChecklistState.gatewayReady && runChecklistState.robotReady && runChecklistState.localizationReady && !runChecklistState.sessionReady,
                        complete = runChecklistState.sessionReady
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = runLoadLabel,
                        active = coreReady && (!isLoaded || !loadedMatchesSelection) && !runCommandState.isRunning && !runCommandState.isPaused,
                        complete = isLoaded && loadedMatchesSelection
                    ),
                    com.recomo.user.ui.screens.run.RunPhaseChipUiState(
                        label = if (runCommandState.isPaused) runResumeActionLabel else runRunActionLabel,
                        active = coreReady && isLoaded && loadedMatchesSelection && !runCommandState.isRunning,
                        complete = runCommandState.isRunning
                    )
                )
            },
            isRunning = runCommandState.isRunning,
            isPaused = runCommandState.isPaused,
            hasError = runCommandState.statusLabel.equals("Error", ignoreCase = true),
            estopActive = runCommandState.estopActive,
            canLoad = canLoad,
            canRun = canRun,
            canInit = canInit,
            canPause = canPause,
            canStop = canStop,
            canHome = canHome
        )
    }

    val libraryUiState = remember(libraryState, selectedTrajectory, localSessions) {
        // Real SimpleTrack trajectories from gateway (scanned from Orin disk)
        val simpleTrackEntries = libraryState.simpleTrackTrajectories.map { trajName ->
            LibrarySessionSummaryUiItem(
                type = LibrarySessionType.FOI,
                sessionId = trajName,
                sessionName = trajName.removeSuffix(".txt"),
                robotName = "",
                category = "cinematic",
                count = 0,
                isSelected = selectedTrajectory == trajName,
                sceneType = "SimpleTrack",
                linkedMap = ""
            )
        }
        // Studio Dance sessions from bundled assets + filesystem
        val studioDanceEntries = localSessions
            .filter { it.sessionType?.equals("studio_dance", ignoreCase = true) == true }
            .map { session ->
                LibrarySessionSummaryUiItem(
                    type = LibrarySessionType.FOI,
                    sessionId = session.sessionId,
                    sessionName = session.sessionName,
                    robotName = session.robotName,
                    category = session.category.ifBlank { "Studio Dance" },
                    frameId = session.frameId,
                    count = session.count,
                    isSelected = selectedTrajectory == session.sessionId,
                    sceneType = "StudioDance",
                    sessionType = session.sessionType,
                    bpm = session.bpm,
                    musicFile = session.musicFile
                )
            }
        LibrarySummaryUiState(
            foiSessions = simpleTrackEntries + studioDanceEntries,
            poiSessions = libraryState.poiSessions.map { session ->
                LibrarySessionSummaryUiItem(
                    type = LibrarySessionType.POI,
                    sessionId = session.sessionId,
                    sessionName = session.sessionName,
                    robotName = session.robotName,
                    category = session.category,
                    frameId = session.frameId,
                    count = session.count,
                    isSelected = selectedTrajectory == session.sessionId,
                    sceneType = session.sceneType.name,
                    linkedMap = session.linkedMap,
                    libraryTarget = session.target
                )
            },
            statusLabel = libraryState.latestMessage ?: libraryTitle,
            isLoading = false,
            emptyMessage = libraryEmptyLabel
        )
    }

    val homeOperationsState = remember(connectionStatus, gatewayServiceState, robotPreparationState) {
        HomeOperationsUiState(
            gatewayLabel = when (connectionStatus) {
                UserConnectionStatus.Connected -> connectionLabelConnected
                UserConnectionStatus.Connecting -> connectionLabelConnecting
                UserConnectionStatus.Disconnected -> connectionLabelDisconnected
            },
            serviceLabel = gatewayServiceState.serviceLabel,
            robotLabel = robotPreparationState.statusLabel,
            note = robotPreparationState.errorMessage
                ?: robotPreparationState.message
                ?: gatewayServiceState.errorMessage,
            canReconnect = connectionStatus != UserConnectionStatus.Connecting,
            canRefreshGateway = gatewayServiceState.canRefresh,
            canPrepareRobot = robotPreparationState.canPrepare,
            canDismissRobot = robotPreparationState.canDismiss
        )
    }

    val mainControlUiState = run {
        val recentSessions = (libraryUiState.foiSessions + libraryUiState.poiSessions).take(3)
        val poseLabel = if (mapUiState.robotPoseOk) mainPoseLockedLabel else mainPoseSearchLabel
        val linkLabel = if (connectionStatus == UserConnectionStatus.Connected) mainLinkReadyLabel else mainLinkIdleLabel
        val videoLabel = if (runVideoState.isStreaming) statusVideoLiveLabel else mainPreviewIdleLabel
        val stateRateValue = overviewState.stateHz?.let { "%2.0f Hz".format(it) } ?: "—"
        val stateDelayValue = overviewState.lastStateAgeMs?.let { "%3dms".format(it.coerceAtMost(999)) } ?: "—"
        val fpsValue = runVideoState.fpsLabel ?: "—"
        val resolutionValue = runVideoState.resolutionLabel.ifBlank { "—" }
        val codecValue = runVideoState.codecLabel ?: "—"
        val delayTone = when {
            overviewState.lastStateAgeMs == null -> MainControlTone.Neutral
            overviewState.lastStateAgeMs <= 150 -> MainControlTone.Success
            overviewState.lastStateAgeMs <= 400 -> MainControlTone.Warning
            else -> MainControlTone.Danger
        }
        MainControlUiState(
            header = MainControlHeaderUiState(
                deviceName = selectedRobot.label,
                connectionLabel = homeOperationsState.gatewayLabel,
                recordingLabel = mainRecordingBadge,
                telemetry = listOf(
                    MainControlMetricUiState(mainLinkMetricLabel, linkLabel, toneForStatus(homeOperationsState.gatewayLabel)),
                    MainControlMetricUiState(mainPoseMetricLabel, poseLabel, if (mapUiState.robotPoseOk) MainControlTone.Success else MainControlTone.Warning),
                    MainControlMetricUiState(mainDelayMetricLabel, stateDelayValue, delayTone),
                    MainControlMetricUiState(mainFpsMetricLabel, fpsValue, if (runVideoState.isStreaming) MainControlTone.Success else MainControlTone.Neutral),
                    MainControlMetricUiState("Nav", navState.statusLabel, when {
                        navState.navActive -> MainControlTone.Brand
                        navState.followActive -> MainControlTone.Secondary
                        navState.arrivedPreserved -> MainControlTone.Success
                        else -> MainControlTone.Neutral
                    })
                )
            ),
            preview = MainControlPreviewUiState(
                title = "",
                detail = null,
                statusLabel = null,
                showGrid = false,
                horizonLabel = overviewState.basePose?.yawDeg?.let { "%.1f°".format(it) } ?: "0.0°",
                cameraParams = listOf(
                    MainControlMetricUiState(mainStateMetricLabel, stateRateValue),
                    MainControlMetricUiState(mainDelayMetricLabel, stateDelayValue),
                    MainControlMetricUiState(mainFpsMetricLabel, fpsValue),
                    MainControlMetricUiState(mainResMetricLabel, resolutionValue),
                    MainControlMetricUiState(mainCodecMetricLabel, codecValue)
                )
            ),
            primaryTools = listOf(
                MainControlToolButtonUiState(MainControlToolKey.Grid, stringResource(R.string.main_grid)),
                MainControlToolButtonUiState(MainControlToolKey.Focus, stringResource(R.string.main_focus)),
                MainControlToolButtonUiState(MainControlToolKey.Frame, stringResource(R.string.main_frame)),
                MainControlToolButtonUiState(MainControlToolKey.Lens, stringResource(R.string.main_lens))
            ),
            secondaryTools = listOf(
                MainControlToolButtonUiState(MainControlToolKey.Navigation, "Nav"),
                MainControlToolButtonUiState(MainControlToolKey.Maps, stringResource(R.string.route_slam_maps)),
                MainControlToolButtonUiState(MainControlToolKey.Gallery, stringResource(R.string.route_media_gallery)),
                MainControlToolButtonUiState(MainControlToolKey.Settings, stringResource(R.string.nav_settings))
            ),
            bottomDock = MainControlBottomDockUiState(
                shortcuts = listOf(
                    MainControlShortcutUiState(
                        key = MainControlShortcutKey.MotionLibrary,
                        title = stringResource(R.string.route_motion_library),
                        detail = stringResource(R.string.main_saved_motions_count, libraryUiState.totalCount)
                    ),
                    MainControlShortcutUiState(
                        key = MainControlShortcutKey.CreateMotion,
                        title = stringResource(R.string.route_motion_creator),
                        detail = mainCreateDetailLabel,
                        tone = MainControlTone.Brand
                    ),
                    MainControlShortcutUiState(
                        key = MainControlShortcutKey.SmartFollow,
                        title = stringResource(R.string.route_smart_follow),
                        detail = stringResource(R.string.smart_follow_detail),
                        tone = MainControlTone.Secondary
                    )
                ),
                transportControls = listOf(
                    MainControlTransportControlUiState(MainControlTransportControlKey.Reset, "Reset"),
                    MainControlTransportControlUiState(MainControlTransportControlKey.PlayPause, stringResource(R.string.route_motion_runner)),
                    MainControlTransportControlUiState(
                        MainControlTransportControlKey.Record,
                        if (runVideoState.isRecording) mainStopRecordLabel else mainRecordLabel,
                        selected = runVideoState.isRecording,
                        enabled = runVideoState.canRecord
                    ),
                    MainControlTransportControlUiState(MainControlTransportControlKey.Save, stringResource(R.string.route_post_record)),
                    MainControlTransportControlUiState(MainControlTransportControlKey.Stop, stringResource(R.string.control_stop))
                ),
                primaryAction = MainControlPrimaryActionUiState(
                    label = stringResource(R.string.route_touch_control),
                    detail = mainTouchDetailLabel
                )
            ),
            sidebar = MainControlSidebarUiState(
                connectionLabel = homeOperationsState.gatewayLabel,
                statusMetrics = listOf(
                    MainControlMetricUiState(mainLinkMetricLabel, linkLabel, toneForStatus(homeOperationsState.gatewayLabel)),
                    MainControlMetricUiState(mainStateMetricLabel, stateRateValue, if (overviewState.stateHz == null) MainControlTone.Neutral else MainControlTone.Brand),
                    MainControlMetricUiState(mainDelayMetricLabel, stateDelayValue, delayTone),
                    MainControlMetricUiState(mainVideoMetricLabel, videoLabel, if (runVideoState.isStreaming) MainControlTone.Success else MainControlTone.Neutral)
                ),
                joints = listOfNotNull(
                    overviewState.basePose?.let {
                        MainControlJointUiState("X", "%.2f".format(it.x), (((it.x).toFloat() + 5f) / 10f).coerceIn(0f, 1f), MainControlTone.Brand)
                    },
                    overviewState.basePose?.let {
                        MainControlJointUiState("Y", "%.2f".format(it.y), (((it.y).toFloat() + 5f) / 10f).coerceIn(0f, 1f), MainControlTone.Secondary)
                    },
                    overviewState.basePose?.let {
                        MainControlJointUiState("Yaw", "%.1f°".format(it.yawDeg), (((it.yawDeg).toFloat() + 180f) / 360f).coerceIn(0f, 1f), MainControlTone.Success)
                    }
                ),
                recentMotions = recentSessions.mapIndexed { index, session ->
                    MainControlRecentMotionUiState(
                        id = session.sessionId,
                        title = session.displayMotionTitle(),
                        detail = session.type.displayGroupLabel(),
                        tone = when (index) {
                            0 -> MainControlTone.Brand
                            1 -> MainControlTone.Secondary
                            else -> MainControlTone.Success
                        }
                    )
                }
            )
        )
    }

    val chatDirectEnabled by shellViewModel.chatDirectEnabled.collectAsState()
    val chatDirectBaseUrl by shellViewModel.chatDirectBaseUrl.collectAsState()
    val chatDirectAuthToken by shellViewModel.chatDirectAuthToken.collectAsState()
    val voiceEngine by shellViewModel.voiceEngine.collectAsState()
    val voiceModel by shellViewModel.voiceModel.collectAsState()

    val whisperDownloadState by shellViewModel.whisperModelRepository.downloadState.collectAsState()

    val settingsUiState = remember(
        selectedRobot,
        chatServerUrl,
        chatDirectEnabled,
        chatDirectBaseUrl,
        chatDirectAuthToken,
        voiceEngine,
        voiceModel,
        whisperDownloadState,
        sessionFolderPath,
        sceneViewerFolderPath,
        connectionStatus,
        useWebRTC,
        videoSource,
        hdmiDeviceAvailable,
        speedOverrides
    ) {
        SettingsUiState(
            robotLabel = selectedRobot.label,
            robotProfileLabel = selectedRobot.profile.name,
            networkPresetLabel = selectedRobot.preset.name,
            chatServerUrl = chatServerUrl,
            chatDirectEnabled = chatDirectEnabled,
            chatDirectBaseUrl = chatDirectBaseUrl,
            chatDirectAuthToken = chatDirectAuthToken,
            voiceEngine = voiceEngine,
            voiceModel = voiceModel,
            whisperDownloadState = whisperDownloadState,
            sessionFolderPath = sessionFolderPath,
            sceneViewerFolderPath = sceneViewerFolderPath,
            gatewayStatusLabel = when (connectionStatus) {
                UserConnectionStatus.Connected -> connectionLabelConnected
                UserConnectionStatus.Connecting -> connectionLabelConnecting
                UserConnectionStatus.Disconnected -> connectionLabelDisconnected
            },
            isConnected = connectionStatus == UserConnectionStatus.Connected,
            isConnecting = connectionStatus == UserConnectionStatus.Connecting,
            useWebRTC = useWebRTC,
            videoSource = videoSource,
            hdmiDeviceAvailable = hdmiDeviceAvailable,
            speedSlowMps = speedOverrides.slowMps,
            speedNormalMps = speedOverrides.normalMps,
            speedFastMps = speedOverrides.fastMps,
            countdownDurationSeconds = countdownDuration
        )
    }

    val shellStatusHighlights = remember(
        topStatusHighlights,
        runVideoState,
        statusVideoLiveLabel,
        statusVideoWaitingLabel,
        statusVideoOfflineLabel,
        statusVideoErrorLabel
    ) {
        val videoHighlight = when {
            runVideoState.detailState == UserRunVideoDetailState.ReceivingFrames -> statusVideoLiveLabel
            runVideoState.connectionState == com.recomo.common.model.ConnectionState.Connected -> statusVideoWaitingLabel
            runVideoState.detailState == UserRunVideoDetailState.Error -> statusVideoErrorLabel
            else -> statusVideoOfflineLabel
        }
        (topStatusHighlights + videoHighlight).distinct()
    }

    val motionRunnerUiState = run {
        val playbackState = when {
            runWorkspaceState.estopActive -> MotionRunnerPlaybackState.Stopped
            runWorkspaceState.hasError -> MotionRunnerPlaybackState.Error
            runCommandState.isIniting -> MotionRunnerPlaybackState.Initing
            runCommandState.isHoming -> MotionRunnerPlaybackState.Homing
            runWorkspaceState.isPaused -> MotionRunnerPlaybackState.Paused
            runWorkspaceState.isRunning -> MotionRunnerPlaybackState.Running
            runWorkspaceState.progress != null && runWorkspaceState.progress >= 1f -> MotionRunnerPlaybackState.Completed
            else -> MotionRunnerPlaybackState.Idle
        }
        MotionRunnerUiState(
            motionName = runWorkspaceState.selectedTrajectoryLabel
                ?: runWorkspaceState.loadedTrajectory
                ?: stringResource(R.string.route_motion_runner),
            motionSubtitle = runWorkspaceState.loadedTrajectoryDetail ?: "",
            playbackState = playbackState,
            playbackLabel = runWorkspaceState.statusLabel,
            playbackTone = when (playbackState) {
                MotionRunnerPlaybackState.Running -> MotionRunnerTone.Success
                MotionRunnerPlaybackState.Paused -> MotionRunnerTone.Warning
                MotionRunnerPlaybackState.Completed -> MotionRunnerTone.Primary
                MotionRunnerPlaybackState.Homing,
                MotionRunnerPlaybackState.Initing -> MotionRunnerTone.Primary
                MotionRunnerPlaybackState.Stopped,
                MotionRunnerPlaybackState.Error -> MotionRunnerTone.Danger
                else -> MotionRunnerTone.Neutral
            },
            playbackDetail = runWorkspaceState.guidanceLabel,
            progress = runWorkspaceState.progress ?: 0f,
            elapsedLabel = runWorkspaceState.elapsedLabel ?: "--",
            remainingLabel = runWorkspaceState.remainingLabel ?: "--",
            keyframeLabel = runWorkspaceState.selectedTrajectoryLabel ?: "--",
            speedLabel = if (runWorkspaceState.isRunning) "1.0x" else "--",
            leftMetrics = listOf(
                MotionRunnerMetricUiState("Gateway", runChecklistState.gatewayDetail, toneForRunnerStatus(runChecklistState.gatewayReady)),
                MotionRunnerMetricUiState("Robot", runChecklistState.robotDetail, toneForRunnerStatus(runChecklistState.robotReady)),
                MotionRunnerMetricUiState("Pose", runChecklistState.localizationDetail, toneForRunnerStatus(runChecklistState.localizationReady)),
                MotionRunnerMetricUiState("Safety", runChecklistState.safetyDetail, toneForRunnerStatus(runChecklistState.safetyReady))
            ),
            timelineTitle = stringResource(R.string.run_checklist_title),
            timelineItems = runWorkspaceState.phaseChips.mapIndexed { index, chip ->
                MotionRunnerTimelineItemUiState(
                    indexLabel = (index + 1).toString(),
                    timeLabel = chip.label,
                    isCurrent = chip.active,
                    isComplete = chip.complete
                )
            },
            leftFooterActions = listOf(
                MotionRunnerActionUiState("back", stringResource(R.string.action_back), icon = MotionRunnerActionIcon.Back),
                MotionRunnerActionUiState("home_route", stringResource(R.string.route_main_control), enabled = true, style = MotionRunnerActionStyle.Secondary, icon = MotionRunnerActionIcon.Home)
            ),
            video = MotionRunnerVideoUiState(
                bitmapFrame = runVideoBitmap,
                showSurfaceFeed = runVideoState.showSurfaceFrame,
                headerPillLabel = stringResource(R.string.run_live_preview_title),
                headerCaption = runWorkspaceState.selectedTrajectoryLabel ?: runWorkspaceState.loadedTrajectory,
                emptyTitle = when (runVideoState.detailState) {
                    UserRunVideoDetailState.NoUrl -> stringResource(R.string.run_live_preview_no_url)
                    UserRunVideoDetailState.WaitingFrames -> stringResource(R.string.run_live_preview_waiting_frames)
                    UserRunVideoDetailState.ReceivingFrames -> stringResource(R.string.run_live_preview_receiving)
                    UserRunVideoDetailState.Error -> stringResource(R.string.run_live_preview_error)
                    UserRunVideoDetailState.Idle -> stringResource(R.string.run_live_preview_offline)
                },
                emptyDetail = runVideoState.errorMessage,
                progress = runWorkspaceState.progress ?: 0f,
                topMetrics = listOf(
                    MotionRunnerMetricUiState("Res", runVideoState.resolutionLabel, MotionRunnerTone.Neutral, MotionRunnerMetricIcon.Signal),
                    MotionRunnerMetricUiState("FPS", runVideoState.fpsLabel ?: "--", MotionRunnerTone.Success, MotionRunnerMetricIcon.Clock),
                    MotionRunnerMetricUiState("Codec", runVideoState.codecLabel ?: "--", MotionRunnerTone.Neutral, MotionRunnerMetricIcon.Power)
                ),
                supportBadges = listOf(
                    MotionRunnerSupportBadgeUiState(homeOperationsState.gatewayLabel, toneForRunnerStatus(runChecklistState.gatewayReady)),
                    MotionRunnerSupportBadgeUiState(
                        if (mapUiState.robotPoseOk) mapLocalizedLabel else mapNotReadyLabel,
                        toneForRunnerStatus(mapUiState.robotPoseOk)
                    )
                ),
                supportCards = listOf(
                    MotionRunnerSupportCardUiState(
                        title = stringResource(R.string.run_checklist_session),
                        value = runWorkspaceState.selectedTrajectoryLabel ?: "--",
                        detail = runWorkspaceState.loadedTrajectoryDetail
                    ),
                    MotionRunnerSupportCardUiState(
                        title = stringResource(R.string.run_checklist_localization),
                        value = mapUiState.selectedLocation ?: "Unset",
                        detail = mapUiState.selectedMapAsset,
                        tone = if (mapUiState.robotPoseOk) MotionRunnerTone.Success else MotionRunnerTone.Warning
                    )
                ),
                overlay = when {
                    runWorkspaceState.estopActive -> MotionRunnerFeedOverlayUiState(
                        visual = MotionRunnerOverlayVisual.Stopped,
                        title = stringResource(R.string.run_estop),
                        detail = runWorkspaceState.guidanceLabel,
                        actionId = "estop_clear",
                        actionLabel = stringResource(R.string.action_clear)
                    )
                    runWorkspaceState.hasError -> MotionRunnerFeedOverlayUiState(
                        visual = MotionRunnerOverlayVisual.Error,
                        title = runWorkspaceState.statusLabel,
                        detail = runWorkspaceState.guidanceLabel
                    )
                    runWorkspaceState.isPaused -> MotionRunnerFeedOverlayUiState(
                        visual = MotionRunnerOverlayVisual.Paused,
                        title = stringResource(R.string.run_paused),
                        detail = runWorkspaceState.guidanceLabel,
                        actionId = "resume",
                        actionLabel = stringResource(R.string.run_resume)
                    )
                    else -> null
                }
            ),
            rightRailTitle = stringResource(R.string.run_checklist_safety),
            safety = MotionRunnerSafetyUiState(
                title = stringResource(R.string.run_estop),
                label = stringResource(R.string.run_estop),
                hint = runWorkspaceState.guidanceLabel,
                isLatched = runWorkspaceState.estopActive,
                enabled = true
            ),
            rightRailActions = listOf(
                MotionRunnerActionUiState("load", stringResource(R.string.run_load), enabled = runWorkspaceState.canLoad, style = MotionRunnerActionStyle.Secondary, icon = MotionRunnerActionIcon.Check),
                MotionRunnerActionUiState("home", stringResource(R.string.run_home), enabled = runWorkspaceState.canHome, style = MotionRunnerActionStyle.Subtle, icon = MotionRunnerActionIcon.Home),
                MotionRunnerActionUiState("init", stringResource(R.string.run_init), enabled = runWorkspaceState.canInit, style = MotionRunnerActionStyle.Secondary, icon = MotionRunnerActionIcon.Check),
                MotionRunnerActionUiState("run", if (runWorkspaceState.isPaused) stringResource(R.string.run_resume) else stringResource(R.string.run_run), enabled = runWorkspaceState.canRun, style = MotionRunnerActionStyle.Primary, icon = MotionRunnerActionIcon.Play),
                MotionRunnerActionUiState("stop", stringResource(R.string.run_stop), enabled = runWorkspaceState.canStop, style = MotionRunnerActionStyle.Secondary, icon = MotionRunnerActionIcon.Stop),
                MotionRunnerActionUiState("pause", stringResource(R.string.run_pause), enabled = runWorkspaceState.canPause, style = MotionRunnerActionStyle.Secondary, icon = MotionRunnerActionIcon.Pause)
            ),
            rightRailStats = listOf(
                MotionRunnerMetricUiState("Session", runWorkspaceState.loadedTrajectory ?: "--", MotionRunnerTone.Primary, MotionRunnerMetricIcon.Speed),
                MotionRunnerMetricUiState("Pose", if (mapUiState.robotPoseOk) mapLocalizedLabel else mapNotReadyLabel, toneForRunnerStatus(mapUiState.robotPoseOk), MotionRunnerMetricIcon.Shield),
                MotionRunnerMetricUiState("Video", if (runVideoState.isStreaming) stringResource(R.string.video_live) else stringResource(R.string.video_idle), if (runVideoState.isStreaming) MotionRunnerTone.Success else MotionRunnerTone.Warning, MotionRunnerMetricIcon.Signal)
            ),
            isStudioDance = runCommandState.isStudioDance,
            musicProgress = musicState.progress,
            musicTimeLabel = musicState.timeLabel,
            musicFileName = musicState.musicFileName ?: runCommandState.musicFileName
        )
    }

    val resolveChatTrajectoryToRun = remember(runViewModel, shellViewModel, chatViewModel) {
        { attachment: TrajectoryAttachment ->
            runViewModel.beginChatTrajectoryHandoff(attachment)
            shellViewModel.navigateTo(UserMainRoute.MotionRunner)
            chatViewModel.downloadTrajectory(attachment) { result ->
                when (result) {
                    is TrajectoryDownloadResult.Success -> {
                        runViewModel.completeChatTrajectoryHandoff(
                            attachment = attachment,
                            resolvedSessionId = result.sessionId,
                            resolvedSessionName = result.sessionName
                        )
                    }

                    is TrajectoryDownloadResult.Error -> {
                        runViewModel.failChatTrajectoryHandoff(
                            attachment = attachment,
                            reason = result.message
                        )
                    }
                }
            }
        }
    }

    val handoffPreviewTrajectoryToRun = remember(runViewModel, shellViewModel, chatViewModel) {
        { attachment: TrajectoryAttachment, resolvedSessionId: String?, resolvedSessionName: String? ->
            runViewModel.completeChatTrajectoryHandoff(
                attachment = attachment,
                resolvedSessionId = resolvedSessionId,
                resolvedSessionName = resolvedSessionName
            )
            chatViewModel.closePreview()
            shellViewModel.navigateTo(UserMainRoute.MotionRunner)
        }
    }

    // v2: AI chat candidates. Preview downloads the trajectory JSON, builds a
    // SceneViewerLaunchRequest with RemoteUrl(scene.spz_url) + InlineJson(traj)
    // + anchorOverride(scene.anchor_pose), and pushes it to the shell's scene
    // viewer state. Execute turns the candidate into the same handoff shape
    // the v1 single-trajectory path already uses, then navigates to Runner.
    val previewChatCandidate = remember(chatViewModel, shellViewModel) {
        { candidate: TrajectoryCandidate ->
            val previewUrl = candidate.previewUrl ?: candidate.downloadUrl
            if (previewUrl.endsWith(".tum", ignoreCase = true)) {
                // Cloud bridge serves the raw TUM: hand it to the viewer's own
                // parseTUMContent via InlineTum. Skips the session-JSON flatten
                // which collapses 3D height + full orientation.
                chatViewModel.downloadText(previewUrl) { tum ->
                    if (!tum.isNullOrBlank()) {
                        shellViewModel.openSceneViewer(
                            chatCandidateToSceneViewerRequestTum(candidate, tum)
                        )
                    }
                }
            } else {
                // Legacy JSON payload (keypoint-teach sessions, older cloud
                // shims). Keep the existing planar-JSON path for compat.
                val adapter = candidate.toLegacyAttachment()
                chatViewModel.downloadTrajectory(adapter) { result ->
                    when (result) {
                        is TrajectoryDownloadResult.Success -> {
                            val request = chatCandidateToSceneViewerRequest(
                                candidate = candidate,
                                trajectoryJson = result.rawJson.toString()
                            )
                            shellViewModel.openSceneViewer(request)
                        }
                        is TrajectoryDownloadResult.Error -> {
                            // chat already appended a SYSTEM message.
                        }
                    }
                }
            }
        }
    }

    val executeChatCandidate: (TrajectoryCandidate) -> Unit = remember(runViewModel, shellViewModel, chatViewModel, context, scope) {
        { candidate: TrajectoryCandidate ->
            val exec = candidate.executionRef
            if (exec != null && candidate.simResult?.feasible != false) {
                // Cloud-authored path: drop JSON on Orin via deploy service, then
                // run it through the same fixed_position pipeline CopyStyle uses.
                scope.launch {
                    when (val r = runViewModel.executeCloudAuthoredTrajectory(exec, displayName = candidate.name)) {
                        is com.recomo.user.control.UserRunCommandViewModel.ExecuteCandidateResult.Ready -> {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.chat_execute_deployed),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            shellViewModel.navigateTo(UserMainRoute.MotionRunner)
                        }
                        is com.recomo.user.control.UserRunCommandViewModel.ExecuteCandidateResult.DownloadFailed -> {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.chat_execute_download_failed, r.reason),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        is com.recomo.user.control.UserRunCommandViewModel.ExecuteCandidateResult.DeployFailed -> {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.chat_execute_deploy_failed, r.reason),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                // Legacy preview-only path: stash trajectory locally so MotionRunner
                // handoff becomes Ready. Base-only execute if sim_result allows.
                val adapter = candidate.toLegacyAttachment()
                runViewModel.beginChatTrajectoryHandoff(adapter)
                shellViewModel.navigateTo(UserMainRoute.MotionRunner)
                chatViewModel.downloadTrajectory(adapter) { result ->
                    when (result) {
                        is TrajectoryDownloadResult.Success -> {
                            runViewModel.persistChatTrajectoryAsLocalSession(
                                rawJson = result.rawJson,
                                sessionId = result.sessionId
                            )
                            runViewModel.completeChatTrajectoryHandoff(
                                attachment = adapter,
                                resolvedSessionId = result.sessionId,
                                resolvedSessionName = result.sessionName
                            )
                        }
                        is TrajectoryDownloadResult.Error -> {
                            runViewModel.failChatTrajectoryHandoff(
                                attachment = adapter,
                                reason = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(route) {
        if (route == UserMainRoute.MotionLibrary) {
            libraryViewModel.refresh()
        } else if (route == UserMainRoute.SlamMaps) {
            mapViewModel.refreshMapList()
        }
    }

    val videoRoutes = setOf(
        UserMainRoute.MainControl,
        UserMainRoute.MotionRunner,
        UserMainRoute.TouchControl,
        UserMainRoute.MotionLibrary,
        UserMainRoute.MotionCreator,
        UserMainRoute.SmartFollow
    )

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = context.hasCameraPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(videoSource, hdmiDeviceAvailable, hasCameraPermission) {
        if (videoSource != VideoSource.HDMI_USB || !hdmiDeviceAvailable) {
            return@LaunchedEffect
        }

        if (hasCameraPermission) {
            runVideoViewModel.onCameraPermissionResult(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(stage, settingsOpen, route) {
        runVideoViewModel.setActive(
            active = stage == UserShellStage.Main &&
                !settingsOpen &&
                route in videoRoutes
        )
    }

    LaunchedEffect(route, connectionStatus) {
        if (
            route in videoRoutes &&
            connectionStatus == UserConnectionStatus.Connected
        ) {
            runVideoViewModel.ensureConnected()
        } else if (
            route !in videoRoutes ||
            connectionStatus == UserConnectionStatus.Disconnected
        ) {
            runVideoViewModel.disconnect()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (stage) {
            UserShellStage.Splash -> SplashScreen()
            UserShellStage.Connection -> ConnectionScreen(
                shellViewModel = shellViewModel,
                connectionStatus = connectionStatus
            )
            UserShellStage.Main -> {
                val showShellStatusBar =
                    route != UserMainRoute.MainControl && route != UserMainRoute.MotionRunner
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (showShellStatusBar) {
                        UserStatusBar(
                            robotName = selectedRobot.label,
                            connectionStatus = connectionStatus,
                            statusMessage = statusMessage,
                            route = route,
                            settingsOpen = settingsOpen,
                            detailHighlights = emptyList(),
                            onOpenMainControl = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                            onOpenRunner = { shellViewModel.navigateTo(UserMainRoute.MotionRunner) },
                            onOpenSettings = shellViewModel::openSettings,
                            onOpenSceneViewer = {
                                // Standalone launch — opens the pre-launch panel with
                                // auto-selected best-match from the SceneAssetRepository.
                                shellViewModel.openSceneViewer(
                                    SceneViewerLaunchRequest(
                                        title = "Scene Viewer",
                                        subtitle = "Pick a scene from the list",
                                        entrySource = SceneViewerEntrySource.Standalone
                                    )
                                )
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (route) {
                            UserMainRoute.MainControl -> MainControlRoute(
                                uiState = mainControlUiState,
                                videoState = runVideoState,
                                videoBitmap = runVideoBitmap,
                                navState = navState,
                                onVideoSurfaceReady = runVideoViewModel::initializeSurface,
                                onVideoSurfaceDestroyed = runVideoViewModel::releaseSurface,
                                onToggleRecording = runVideoViewModel::toggleRecording,
                                onOpenRunner = { shellViewModel.navigateTo(UserMainRoute.MotionRunner) },
                                onOpenLibrary = { shellViewModel.navigateTo(UserMainRoute.MotionLibrary) },
                                onOpenCreator = { shellViewModel.navigateTo(UserMainRoute.MotionCreator) },
                                onOpenSmartFollow = { shellViewModel.navigateTo(UserMainRoute.SmartFollow) },
                                onOpenTouchControl = { shellViewModel.navigateTo(UserMainRoute.TouchControl) },
                                onOpenSlamMaps = { shellViewModel.navigateTo(UserMainRoute.SlamMaps) },
                                onOpenMediaGallery = { shellViewModel.navigateTo(UserMainRoute.MediaGallery) },
                                onOpenPostRecord = { shellViewModel.navigateTo(UserMainRoute.PostRecord) },
                                onOpenSettings = shellViewModel::openSettings,
                                onSelectPoiSession = navigationViewModel::selectPoiSession,
                                onSelectPoiIndex = navigationViewModel::selectPoiIndex,
                                onNavGo = navigationViewModel::sendGoToPoi,
                                onNavStop = navigationViewModel::sendNavStop,
                                onNavPause = navigationViewModel::sendNavPause,
                                onNavResume = navigationViewModel::sendNavResume,
                                onNavSpeedChange = navigationViewModel::sendNavSpeed,
                                onFollowToggle = { active ->
                                    if (active) navigationViewModel.startFollowing()
                                    else navigationViewModel.stopFollowing()
                                },
                                onModeChange = navigationViewModel::setOperationMode,
                                onRefreshSessions = navigationViewModel::refreshPoiSessions,
                                onTargetRoi = navigationViewModel::sendTargetRoi
                            )
                            UserMainRoute.MotionCreator -> MotionCreatorRoute(
                                chatViewModel = chatViewModel,
                                chatServerUrl = chatServerUrl,
                                showBitmapFrame = runVideoState.showBitmapFrame,
                                videoBitmap = runVideoBitmap,
                                onVideoSurfaceReady = runVideoViewModel::initializeSurface,
                                onVideoSurfaceDestroyed = runVideoViewModel::releaseSurface,
                                captureThumbnail = { runVideoViewModel.captureThumbnailBase64() },
                                captureRobotState = { runViewModel.buildRobotStateSnapshot() },
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onPreviewTrajectory = { chatViewModel.previewTrajectory(it) },
                                onExecuteTrajectory = resolveChatTrajectoryToRun,
                                onPreviewCandidate = previewChatCandidate,
                                onExecuteCandidate = executeChatCandidate,
                                onCopyStylePresetSelected = { detail ->
                                    val matchedSession = libraryState.poiSessions.firstOrNull { session ->
                                        session.sessionId.equals(detail.id, ignoreCase = true) ||
                                            session.sessionName.equals(detail.id, ignoreCase = true)
                                    }
                                    // Presets hard-code linkedMap = "". Resolve it from the
                                    // real library session so the runner's left-rail "定位"
                                    // label and the MapMatch dialog pick up the correct map
                                    // (e.g. t8space-3f) instead of whatever the gateway last
                                    // reported as current_location (e.g. panyan1).
                                    val resolvedMap = if (detail.linkedMap.isBlank()) {
                                        matchedSession?.linkedMap.orEmpty()
                                    } else {
                                        detail.linkedMap
                                    }
                                    matchedSession?.target?.let { target ->
                                        libraryViewModel.requestSessionDetail(target, matchedSession.sessionId)
                                    }
                                    motionDetailItem = if (resolvedMap != detail.linkedMap) {
                                        detail.copy(
                                            linkedMap = resolvedMap,
                                            libraryTarget = matchedSession?.target
                                        )
                                    } else {
                                        detail.copy(libraryTarget = matchedSession?.target)
                                    }
                                },
                                onOpenSceneViewer = { shellViewModel.openSceneViewer(it) },
                                voiceEngine = voiceEngine,
                                whisperRepository = shellViewModel.whisperModelRepository,
                                voiceModelId = shellViewModel.voiceModel.value.modelId
                            )
                            UserMainRoute.MotionRunner -> MotionRunnerRoute(
                                uiState = motionRunnerUiState,
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onVideoSurfaceReady = runVideoViewModel::initializeSurface,
                                onVideoSurfaceDestroyed = runVideoViewModel::releaseSurface,
                                onLoad = runViewModel::loadSelectedTrajectory,
                                onInit = runViewModel::initScene,
                                onRun = {
                                    if (runCommandState.isPaused) {
                                        runViewModel.resume()
                                        if (runCommandState.isStudioDance) musicPlayerViewModel.resume()
                                    } else if (runCommandState.isStudioDance) {
                                        // Studio Dance: show countdown, then sync-start
                                        showCountdown = true
                                    } else {
                                        runViewModel.run()
                                        if (!runVideoViewModel.isRecording.value) {
                                            runVideoViewModel.startRecording()
                                        }
                                    }
                                },
                                onPause = {
                                    runViewModel.pause()
                                    if (runCommandState.isStudioDance) musicPlayerViewModel.pause()
                                },
                                onStop = {
                                    runViewModel.stop()
                                    if (runCommandState.isStudioDance) musicPlayerViewModel.stop()
                                    if (runVideoViewModel.isRecording.value) {
                                        runVideoViewModel.stopRecording()
                                    }
                                },
                                onHome = runViewModel::home,
                                onOpenMainControl = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onEmergencyStop = {
                                    if (runCommandState.estopActive) runViewModel.clearEstop()
                                    else runViewModel.estop()
                                }
                            )
                            UserMainRoute.MotionLibrary -> LibraryRoute(
                                state = libraryUiState,
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onCreateMotion = { shellViewModel.navigateTo(UserMainRoute.MotionCreator) },
                                onRefresh = libraryViewModel::refresh,
                                onSelectSession = { session ->
                                    session.libraryTarget?.let { target ->
                                        libraryViewModel.requestSessionDetail(target, session.sessionId)
                                    }
                                    motionDetailItem = session.toMotionDetailItem()
                                }
                            )
                            UserMainRoute.TouchControl -> TouchControlRoute(
                                state = touchControlState,
                                title = touchTitle,
                                slowLabel = touchSlowLabel,
                                normalLabel = touchNormalLabel,
                                fastLabel = touchFastLabel,
                                activeLabel = touchActiveLabel,
                                chassisTitle = touchChassisLabel,
                                chassisSubtitle = touchChassisSubLabel,
                                armTitle = touchArmLabel,
                                armSubtitle = touchArmSubLabel,
                                gimbalTitle = touchGimbalLabel,
                                gimbalSubtitle = touchGimbalSubLabel,
                                connectionLabel = touchConnectionLabel,
                                safetyLabel = touchSafetyLabel,
                                estopLabel = touchEstopLabel,
                                clearEstopLabel = touchClearEstopLabel,
                                freezeLabel = touchFreezeLabel,
                                unfreezeLabel = touchUnfreezeLabel,
                                yawLabel = touchYawLabel,
                                armHomeLabel = touchArmHomeLabel,
                                gimbalHomeLabel = touchGimbalHomeLabel,
                                stopLabel = touchStopLabel,
                                videoState = runVideoState,
                                videoBitmap = runVideoBitmap,
                                onVideoSurfaceReady = runVideoViewModel::initializeSurface,
                                onVideoSurfaceDestroyed = runVideoViewModel::releaseSurface,
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onToggleGrid = touchControlViewModel::toggleGrid,
                                onSpeedModeChange = touchControlViewModel::setSpeedMode,
                                onChassisForwardTap = { touchControlViewModel.sendChassisStep(1, 0) },
                                onChassisForwardHoldStart = { touchControlViewModel.startChassisMove(1, 0, 0) },
                                onChassisForwardHoldEnd = touchControlViewModel::stopChassisMove,
                                onChassisBackTap = { touchControlViewModel.sendChassisStep(-1, 0) },
                                onChassisBackHoldStart = { touchControlViewModel.startChassisMove(-1, 0, 0) },
                                onChassisBackHoldEnd = touchControlViewModel::stopChassisMove,
                                onChassisLeftTap = { touchControlViewModel.sendChassisStep(0, 1) },
                                onChassisLeftHoldStart = { touchControlViewModel.startChassisMove(0, 1, 0) },
                                onChassisLeftHoldEnd = touchControlViewModel::stopChassisMove,
                                onChassisRightTap = { touchControlViewModel.sendChassisStep(0, -1) },
                                onChassisRightHoldStart = { touchControlViewModel.startChassisMove(0, -1, 0) },
                                onChassisRightHoldEnd = touchControlViewModel::stopChassisMove,
                                onChassisRotateLeftTap = { touchControlViewModel.sendChassisRotate(1) },
                                onChassisRotateLeftHoldStart = { touchControlViewModel.startChassisMove(0, 0, 1) },
                                onChassisRotateLeftHoldEnd = touchControlViewModel::stopChassisMove,
                                onChassisRotateRightTap = { touchControlViewModel.sendChassisRotate(-1) },
                                onChassisRotateRightHoldStart = { touchControlViewModel.startChassisMove(0, 0, -1) },
                                onChassisRotateRightHoldEnd = touchControlViewModel::stopChassisMove,
                                onArmJointTap = { jointIndex, positive ->
                                    if (positive) touchControlViewModel.nudgeArmJointPositive(jointIndex)
                                    else touchControlViewModel.nudgeArmJointNegative(jointIndex)
                                },
                                onArmJointHoldStart = { jointIndex, positive ->
                                    touchControlViewModel.startArmJointMove(jointIndex, if (positive) 1 else -1)
                                },
                                onArmJointHoldEnd = touchControlViewModel::stopArmJointMove,
                                onGimbalTap = { axis, positive ->
                                    if (positive) touchControlViewModel.nudgeGimbalPositive(axis)
                                    else touchControlViewModel.nudgeGimbalNegative(axis)
                                },
                                onGimbalHoldStart = { axis, positive ->
                                    touchControlViewModel.startGimbalVelocityControl(axis, if (positive) 1.0 else -1.0)
                                },
                                onGimbalHoldEnd = touchControlViewModel::stopGimbalVelocityControl,
                                onEmergencyStopTap = {
                                    if (touchControlState.estopActive) touchControlViewModel.clearEstop()
                                    else touchControlViewModel.estop()
                                },
                                onFreezeToggleTap = {
                                    if (touchControlState.freezeAllActive) touchControlViewModel.unfreezeAll()
                                    else touchControlViewModel.freezeAll()
                                },
                                onArmHome = touchControlViewModel::sendArmHomingPose,
                                onGimbalHome = touchControlViewModel::sendGimbalHomingPose,
                                onStopAll = {
                                    touchControlViewModel.stopChassisMove()
                                    touchControlViewModel.stopArmJointMove()
                                    touchControlViewModel.stopGimbalVelocityControl(0)
                                    touchControlViewModel.stopGimbalVelocityControl(1)
                                    touchControlViewModel.stopGimbalVelocityControl(2)
                                }
                            )
                            UserMainRoute.PostRecord -> PostRecordRoute(
                                state = postRecordState,
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onOpenGallery = { shellViewModel.navigateTo(UserMainRoute.MediaGallery) },
                                onRunAgain = { shellViewModel.navigateTo(UserMainRoute.MotionRunner) },
                                onRefresh = postRecordViewModel::refresh,
                                onDelete = postRecordViewModel::deleteRecording
                            )
                            UserMainRoute.MediaGallery -> MediaGalleryRoute(
                                state = mediaGalleryState,
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onSearchQueryChange = mediaGalleryViewModel::onSearchQueryChange,
                                onFilterChange = mediaGalleryViewModel::onFilterChange,
                                onSortChange = mediaGalleryViewModel::onSortChange,
                                onViewModeChange = mediaGalleryViewModel::onViewModeChange,
                                onRefresh = mediaGalleryViewModel::refresh,
                                onDownload = mediaGalleryViewModel::downloadMedia,
                                onDeleteLocal = mediaGalleryViewModel::deleteLocalMedia,
                                getLocalFile = mediaGalleryViewModel::getLocalFile
                            )
                            UserMainRoute.SlamMaps -> SlamMapsWorkspace(
                                state = mapUiState,
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                onRefresh = mapViewModel::refreshMapList,
                                onSelectLocation = mapViewModel::selectLocation,
                                onSelectMap = mapViewModel::selectMapAsset,
                                onPrepareLocalization = mapViewModel::prepareLocalization,
                                onDismissLocalization = mapViewModel::dismissLocalization,
                                onOpenMapMatch = { mapMatchOpen = true }
                            )
                            UserMainRoute.SmartFollow -> SmartFollowRoute(
                                onBack = { shellViewModel.navigateTo(UserMainRoute.MainControl) },
                                showBitmapFrame = runVideoState.showBitmapFrame,
                                videoBitmap = runVideoBitmap,
                                onVideoSurfaceReady = runVideoViewModel::initializeSurface,
                                onVideoSurfaceDestroyed = runVideoViewModel::releaseSurface
                            )
                        }

                        if (settingsOpen) {
                            SettingsOverlay(
                                state = settingsUiState,
                                systemInfoState = systemInfoState,
                                systemLoadState = systemLoadState,
                                gatewayControlState = gatewayControlState,
                                gatewayServiceState = gatewayServiceState,
                                actuatorCardState = actuatorCardState,
                                sceneCardState = sceneCardState,
                                autonomyCardState = autonomyCardState,
                                cloudCardState = cloudCardState,
                                availableRobots = availableRobots,
                                selectedRobot = selectedRobot,
                                onSelectRobot = shellViewModel::selectRobot,
                                onClose = shellViewModel::closeSettings,
                                onConnectOrReconnect = systemViewModel::connectOrReconnect,
                                onDisconnect = shellViewModel::disconnect,
                                onRefreshGatewayService = systemViewModel::refreshGatewayService,
                                onStartGatewayService = systemViewModel::startGatewayService,
                                onStopGatewayService = systemViewModel::stopGatewayService,
                                onSetPncAuthorityAuto = { systemViewModel.setPncAuthority("auto") },
                                onSetPncAuthorityLocal = { systemViewModel.setPncAuthority("local") },
                                onSetPncAuthorityExternal = { systemViewModel.setPncAuthority("external") },
                                onPreparePerception = systemViewModel::preparePerceptionNodes,
                                onDismissPerception = systemViewModel::dismissPerceptionNodes,
                                onPrepareLocalizationStack = systemViewModel::prepareLocalizationNodes,
                                onDismissLocalizationStack = systemViewModel::dismissLocalizationNodes,
                                onPreparePnc = systemViewModel::preparePncNodes,
                                onDismissPnc = systemViewModel::dismissPncNodes,
                                onPrepareActuators = systemViewModel::prepareActuators,
                                onDismissActuators = systemViewModel::dismissActuators,
                                onPrepareSensors = systemViewModel::prepareSensors,
                                onDismissSensors = systemViewModel::dismissSensors,
                                onPrepareScene = { systemViewModel.prepareScene() },
                                onDismissScene = systemViewModel::dismissScene,
                                onUseWebRTCChange = shellViewModel::updateUseWebRTC,
                                onVideoSourceChange = shellViewModel::updateVideoSource,
                                onChatServerUrlChange = shellViewModel::updateChatServerUrl,
                                onChatDirectEnabledChange = shellViewModel::updateChatDirectEnabled,
                                onChatDirectBaseUrlChange = shellViewModel::updateChatDirectBaseUrl,
                                onChatDirectAuthTokenChange = shellViewModel::updateChatDirectAuthToken,
                                onVoiceEngineChange = shellViewModel::updateVoiceEngine,
                                onVoiceModelChange = shellViewModel::updateVoiceModel,
                                onDownloadWhisperModel = shellViewModel::downloadWhisperModel,
                                onSessionFolderPathChange = shellViewModel::updateSessionFolderPath,
                                onSceneViewerFolderPathChange = shellViewModel::updateSceneViewerFolderPath,
                                onSpeedTierChange = shellViewModel::updateSpeedTiers,
                                onCountdownDurationChange = shellViewModel::updateCountdownDuration
                            )
                        }
                    }
                }
            }
        }

        when {
            sceneViewerRequest != null -> {
                val sceneViewerContext = LocalContext.current
                val sceneViewerRootDir = remember(sceneViewerContext) {
                    java.io.File(sceneViewerContext.getExternalFilesDir(null), "sceneviewer")
                }
                SceneViewerScreen(
                    request = sceneViewerRequest!!,
                    repository = shellViewModel.sceneAssetRepository,
                    httpServer = shellViewModel.sceneViewerHttpServer,
                    tumCacheDir = java.io.File(sceneViewerRootDir, "cache"),
                    onEnsureReady = {
                        val sceneDir = java.io.File(sceneViewerRootDir, "scenes")
                        sceneDir.mkdirs()
                        java.io.File(sceneViewerRootDir, "cache").mkdirs()
                        shellViewModel.ensureSceneAssetsReady(sceneDir)
                    },
                    onClose = shellViewModel::closeSceneViewer
                )
            }
            localSessionPreview != null -> {
                val preview = localSessionPreview!!
                val previewSession = localSessions.firstOrNull { it.sessionId == preview.sessionId }
                val isStudioDancePreview = previewSession?.sessionType?.equals("studio_dance", ignoreCase = true) == true
                TrajectoryPreviewScreen(
                    preview = preview.preview,
                    title = preview.sessionName.ifBlank { localPreviewTitleLabel },
                    frameSummary = "${stringResource(R.string.chat_preview_frames_label)}: ${preview.frameCount}",
                    executeLabel = stringResource(R.string.run_use_local_session),
                    scenePreviewLabel = scenePreviewLabel,
                    onClose = {
                        runViewModel.closeLocalSessionPreview()
                        if (isStudioDancePreview) musicPlayerViewModel.stop()
                    },
                    onOpenSceneViewer = {
                        shellViewModel.openSceneViewer(
                            localSessionPreviewToSceneViewerRequest(preview)
                        )
                    },
                    onExecute = {
                        previewSession?.let(runViewModel::attachLocalSession)
                        runViewModel.closeLocalSessionPreview()
                        if (isStudioDancePreview) musicPlayerViewModel.stop()
                    },
                    onPlaybackChanged = if (isStudioDancePreview) {
                        { playing ->
                            if (playing) musicPlayerViewModel.play() else musicPlayerViewModel.pause()
                        }
                    } else null
                )
            }
            motionDetailItem != null -> {
                val detail = motionDetailItem!!
                if (mapMatchOpen) {
                    // MapMatchDialog 4-phase flow over the detail overlay
                    MapMatchDialog(
                        state = mapMatchDialogState.copy(motionName = detail.title),
                        onDismiss = { mapMatchOpen = false },
                        onSelectMap = { mapId ->
                            mapViewModel.selectMapAsset(mapId)
                        },
                        onPrepareLocalization = {
                            mapViewModel.prepareLocalization()
                        },
                        onRetry = {
                            mapViewModel.prepareLocalization()
                        },
                        autoSelect = detail.linkedMap.isNotBlank(),
                        onSkipMap = {
                            // Skip map — go directly to MotionRunner.
                            // Still push the motion's linked map to the gateway so the
                            // runner's "定位" label reflects the motion's associated map
                            // (e.g. t8space-3f) instead of whatever current_location the
                            // gateway last reported. Skipping only skips the localization
                            // step, not the map association.
                            val sceneType = when (detail.sceneType) {
                                "SimpleTrack" -> com.recomo.user.control.SceneType.SimpleTrack
                                "LivePnC" -> com.recomo.user.control.SceneType.LivePnC
                                "Studio Dance", "StudioDance" -> com.recomo.user.control.SceneType.StudioDance
                                else -> com.recomo.user.control.SceneType.Unknown
                            }
                            runViewModel.selectTrajectoryWithSceneType(detail.id, sceneType)
                            if (detail.linkedMap.isNotBlank()) {
                                mapViewModel.selectLocation(detail.linkedMap)
                            }
                            localizationSkipped = true
                            mapMatchOpen = false
                            motionDetailItem = null
                            shellViewModel.navigateTo(UserMainRoute.MotionRunner)
                        },
                        onRunWithMap = {
                            val sceneType = when (detail.sceneType) {
                                "SimpleTrack" -> com.recomo.user.control.SceneType.SimpleTrack
                                "LivePnC" -> com.recomo.user.control.SceneType.LivePnC
                                "Studio Dance", "StudioDance" -> com.recomo.user.control.SceneType.StudioDance
                                else -> com.recomo.user.control.SceneType.Unknown
                            }
                            runViewModel.selectTrajectoryWithSceneType(detail.id, sceneType)
                            if (detail.linkedMap.isNotBlank()) {
                                mapViewModel.selectLocation(detail.linkedMap)
                            }
                            localizationSkipped = false
                            mapMatchOpen = false
                            motionDetailItem = null
                            shellViewModel.navigateTo(UserMainRoute.MotionRunner)
                        }
                    )
                } else {
                    MotionDetailOverlay(
                        item = detail,
                        videoFile = detail.videoFileName?.let { java.io.File(copyStyleDir, it) },
                        onBack = { motionDetailItem = null },
                        onOpenSceneViewer = {
                            shellViewModel.openSceneViewer(
                                motionDetailToSceneViewerRequest(
                                    detail = detail,
                                    sessionDetail = librarySessionDetail
                                )
                            )
                        },
                        onPreview = if (detail.isStudioDance) {
                            {
                                // Build 3D preview, prepare music (don't auto-play)
                                val session = localSessions.firstOrNull { it.sessionId == detail.id }
                                if (session != null) {
                                    runViewModel.previewLocalSession(session)
                                }
                                detail.musicFile?.let { musicPlayerViewModel.prepareByName(it) }
                            }
                        } else null,
                        onRunThis = {
                            if (detail.isStudioDance) {
                                // Studio Dance — prepare music + go to runner
                                runViewModel.selectStudioDanceTrajectory(
                                    trajectoryId = detail.id,
                                    musicFile = detail.musicFile,
                                    musicOffsetMs = detail.musicOffsetMs ?: 0L
                                )
                                detail.musicFile?.let { musicPlayerViewModel.prepareByName(it) }
                                localizationSkipped = true
                                motionDetailItem = null
                                shellViewModel.navigateTo(UserMainRoute.MotionRunner)
                            } else if (detail.sceneType == "SimpleTrack") {
                                // SimpleTrack doesn't need a map — go straight to runner
                                runViewModel.selectTrajectoryWithSceneType(
                                    detail.id,
                                    com.recomo.user.control.SceneType.SimpleTrack
                                )
                                localizationSkipped = true
                                motionDetailItem = null
                                shellViewModel.navigateTo(UserMainRoute.MotionRunner)
                            } else {
                                // LivePnC / Unknown — show map dialog (user can skip or localize)
                                if (detail.linkedMap.isNotBlank()) {
                                    mapViewModel.selectLocation(detail.linkedMap)
                                } else {
                                    mapViewModel.refreshMapList()
                                }
                                mapMatchOpen = true
                            }
                        }
                    )
                }
            }
            mapMatchOpen -> {
                MapMatchDialog(
                    state = mapMatchDialogState,
                    onDismiss = { mapMatchOpen = false },
                    onPrepareLocalization = {
                        mapViewModel.prepareLocalization()
                    },
                    onRetry = {
                        mapViewModel.prepareLocalization()
                    }
                )
            }
            previewTrajectory != null -> {
                val preview = previewTrajectory!!
                TrajectoryPreviewScreen(
                    preview = preview.preview,
                    title = preview.attachment.name.ifBlank { stringResource(R.string.chat_preview_title) },
                    frameSummary = "${stringResource(R.string.chat_preview_frames_label)}: ${preview.preview.samples.size}",
                    executeLabel = stringResource(R.string.chat_open_in_run),
                    scenePreviewLabel = scenePreviewLabel,
                    onClose = { chatViewModel.closePreview() },
                    onOpenSceneViewer = {
                        shellViewModel.openSceneViewer(
                            chatPreviewToSceneViewerRequest(preview)
                        )
                    },
                    onExecute = {
                        handoffPreviewTrajectoryToRun(
                            preview.attachment,
                            preview.resolvedSessionId,
                            preview.resolvedSessionName
                        )
                    }
                )
            }
        }

        // Studio Dance countdown overlay — rendered above all content
        if (showCountdown) {
            com.recomo.user.ui.screens.runner.CountdownOverlay(
                durationSeconds = countdownDuration,
                onComplete = {
                    showCountdown = false
                    // Synchronized start: motion + music together
                    runViewModel.run()
                    musicPlayerViewModel.play(offsetMs = runCommandState.musicOffsetMs)
                    if (!runVideoViewModel.isRecording.value) {
                        runVideoViewModel.startRecording()
                    }
                },
                onCancel = {
                    showCountdown = false
                }
            )
        }

        // System health bar — compact pill at top center, tap to expand detail
        if (stage == UserShellStage.Main && !settingsOpen) {
            var healthDetailOpen by rememberSaveable { mutableStateOf(false) }

            SystemHealthBar(
                viewModel = systemHealthViewModel,
                onTap = { healthDetailOpen = !healthDetailOpen },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )

            if (healthDetailOpen) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .fillMaxWidth(0.45f)
                        .heightIn(max = 500.dp),
                    color = Color(0xEE111111),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                    SystemHealthDetail(viewModel = systemHealthViewModel)
                }
            }
        }

        // Global emergency-stop floating button — visible on all pages in the
        // main stage except when Settings overlay is open. Pages that already
        // have their own inline e-stop (RUN, TouchControl, MotionRunner) will
        // show both — the global one is small and unobtrusive in the corner,
        // so the redundancy is acceptable for safety.
        if (stage == UserShellStage.Main && !settingsOpen) {
            EmergencyStopOverlay(
                estopActive = runCommandState.estopActive,
                onToggle = {
                    if (runCommandState.estopActive) runViewModel.clearEstop()
                    else runViewModel.estop()
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

private fun motionDetailToSceneViewerRequest(
    detail: MotionDetailItem,
    sessionDetail: UserLibrarySessionDetail?
): SceneViewerLaunchRequest = SceneViewerLaunchRequest(
    title = detail.title,
    subtitle = detail.subtitle.ifBlank { "Trajectory-linked scene preview" },
    entrySource = if (detail.isPreset) {
        SceneViewerEntrySource.CopyStyle
    } else {
        SceneViewerEntrySource.MotionLibrary
    },
    trajectorySource = resolveTrajectorySource(detail, sessionDetail),
    sceneSource = motionDetailSceneSource(detail, sessionDetail),
    sessionId = detail.id,
    anchorTumAssetPath = detail.previewAnchorTumAssetPath
)

/**
 * Priority for the SceneViewer trajectory source:
 *   1. Pre-computed bundled TUM (CopyStyle presets — see `assets/spzviewer/trajectories/`)
 *   2. Matching library session's raw JSON — converted to TUM at launch time
 *   3. Opaque TrajectoryReference — the launcher can't render this, falls through to no-trajectory mode
 */
private fun resolveTrajectorySource(
    detail: MotionDetailItem,
    sessionDetail: UserLibrarySessionDetail?
): SceneTrajectorySource {
    detail.previewTumAssetPath?.takeIf { it.isNotBlank() }?.let {
        return SceneTrajectorySource.AppAssetTum(it)
    }
    matchingLibrarySessionDetail(detail, sessionDetail)?.let { matchedDetail ->
        return SceneTrajectorySource.InlineJson(matchedDetail.rawSession.toString())
    }
    return SceneTrajectorySource.TrajectoryReference(detail.id)
}

private fun matchingLibrarySessionDetail(
    detail: MotionDetailItem,
    sessionDetail: UserLibrarySessionDetail?
): UserLibrarySessionDetail? {
    if (detail.libraryTarget == null || sessionDetail == null) return null
    if (sessionDetail.target != detail.libraryTarget) return null
    return sessionDetail.takeIf { it.sessionId.equals(detail.id, ignoreCase = true) }
}

private fun motionDetailSceneSource(
    detail: MotionDetailItem,
    sessionDetail: UserLibrarySessionDetail?
): SceneAssetSource? {
    val matchedDetail = matchingLibrarySessionDetail(detail, sessionDetail)
    return listOf(
        detail.linkedMap,
        matchedDetail?.linkedMap.orEmpty(),
        matchedDetail?.mapName.orEmpty()
    ).firstNotNullOfOrNull { candidate ->
        candidate.toSceneAssetSourceOrNull()
    }
}

private fun chatPreviewToSceneViewerRequest(
    preview: PreviewTrajectoryState
): SceneViewerLaunchRequest = SceneViewerLaunchRequest(
    title = preview.attachment.name.ifBlank { "AI trajectory scene preview" },
    subtitle = preview.attachment.frameCount?.let { "AI trajectory · $it frames" } ?: "AI trajectory preview",
    entrySource = SceneViewerEntrySource.AiChat,
    trajectorySource = preview.preview.toSceneTrajectorySource(
        fallbackId = preview.resolvedSessionId ?: preview.attachment.trajectoryId
    ),
    sessionId = preview.resolvedSessionId ?: preview.attachment.trajectoryId
)

// ── v2 chat candidate → scene viewer bridge ──────────────────────
//
// A TrajectoryCandidate carries everything needed for a 3D preview:
//   - download_url → trajectory JSON (we just downloaded it, pass InlineJson)
//   - scene.spz_url → SPZ file (RemoteUrl, served by the launcher's HTTP proxy)
//   - scene.anchor_pose → SE(3) alignment (anchorOverride, bypasses registry)
// The sceneviewer module already handles the rest.

private fun TrajectoryCandidate.toLegacyAttachment(): TrajectoryAttachment =
    TrajectoryAttachment(
        trajectoryId = id,
        name = name,
        durationSec = durationSec,
        frameCount = frameCount,
        downloadUrl = downloadUrl,
        thumbnailUrl = thumbnailUrl
    )

private fun chatCandidateToSceneViewerRequest(
    candidate: TrajectoryCandidate,
    trajectoryJson: String
): SceneViewerLaunchRequest {
    val scene = candidate.scene
    val sceneSource = scene?.spzUrl?.let { SceneAssetSource.RemoteUrl(it) }
    val anchorOverride = scene?.anchorPose?.let { dto ->
        CommonAnchorPose(
            x = dto.x,
            y = dto.y,
            z = dto.z,
            qx = dto.qx,
            qy = dto.qy,
            qz = dto.qz,
            qw = dto.qw
        )
    }
    val subtitle = buildString {
        append("AI candidate · ")
        append("${candidate.frameCount} frames")
        candidate.simResult?.let { sim ->
            append(if (sim.feasible) " · sim ok" else " · sim failed")
        }
    }
    return SceneViewerLaunchRequest(
        title = candidate.name.ifBlank { "AI trajectory candidate" },
        subtitle = subtitle,
        entrySource = SceneViewerEntrySource.AiChat,
        trajectorySource = SceneTrajectorySource.InlineJson(trajectoryJson),
        sceneSource = sceneSource,
        sessionId = candidate.id,
        anchorOverride = anchorOverride
    )
}

/**
 * Build a viewer request from a raw TUM body. Identical to
 * [chatCandidateToSceneViewerRequest] except the trajectory is given to
 * the viewer as `InlineTum` — its `parseTUMContent()` honours full 6-DOF
 * camera poses (no planar-flatten / yaw-only loss). Use this when the
 * cloud bridge serves `/trajectories/<id>.tum`.
 */
private fun chatCandidateToSceneViewerRequestTum(
    candidate: TrajectoryCandidate,
    tumText: String
): SceneViewerLaunchRequest {
    val scene = candidate.scene
    val sceneSource = scene?.spzUrl?.let { raw ->
        val trimmed = raw.trim()
        when {
            trimmed.isBlank() -> null
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ->
                SceneAssetSource.RemoteUrl(trimmed)
            else -> SceneAssetSource.AppAsset(trimmed.removePrefix("./"))
        }
    }
    val anchorOverride = scene?.anchorPose?.let { dto ->
        CommonAnchorPose(
            x = dto.x, y = dto.y, z = dto.z,
            qx = dto.qx, qy = dto.qy, qz = dto.qz, qw = dto.qw
        )
    }
    val subtitle = buildString {
        append("AI candidate · ${candidate.frameCount} frames")
        candidate.simResult?.let { sim ->
            append(if (sim.feasible) " · sim ok" else " · sim failed")
        }
    }
    return SceneViewerLaunchRequest(
        title = candidate.name.ifBlank { "AI trajectory candidate" },
        subtitle = subtitle,
        entrySource = SceneViewerEntrySource.AiChat,
        trajectorySource = SceneTrajectorySource.InlineTum(tumText),
        sceneSource = sceneSource,
        sessionId = candidate.id,
        anchorOverride = anchorOverride
    )
}

private fun localSessionPreviewToSceneViewerRequest(
    preview: UserLocalSessionPreviewState
): SceneViewerLaunchRequest = SceneViewerLaunchRequest(
    title = preview.sessionName.ifBlank { "Local session scene preview" },
    subtitle = "Local session · ${preview.frameCount} frames",
    entrySource = SceneViewerEntrySource.LocalSession,
    trajectorySource = preview.preview.toSceneTrajectorySource(preview.sessionId),
    sessionId = preview.sessionId
)

private fun TrajectoryPreview.toSceneTrajectorySource(
    fallbackId: String
): SceneTrajectorySource {
    val tumText = toTumText()
    return if (tumText.isBlank()) {
        SceneTrajectorySource.SessionReference(fallbackId)
    } else {
        SceneTrajectorySource.InlineTum(tumText)
    }
}

private fun TrajectoryPreview.toTumText(): String {
    if (samples.isEmpty()) return ""
    return samples.mapIndexed { index, sample ->
        val timestamp = sample.tSec.takeIf { it.isFinite() } ?: index.toDouble()
        val halfYaw = sample.baseYaw / 2.0
        String.format(
            Locale.US,
            "%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f",
            timestamp,
            sample.baseX,
            0.0,
            sample.baseY,
            0.0,
            sin(halfYaw),
            0.0,
            cos(halfYaw)
        )
    }.joinToString(separator = "\n")
}

private fun String.toSceneAssetSourceOrNull(): SceneAssetSource? {
    val normalized = trim()
    return when {
        normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true) ->
            SceneAssetSource.RemoteUrl(normalized)
        normalized.startsWith("/") ->
            SceneAssetSource.LocalFile(normalized)
        else -> null
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.splash_title),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun UserStatusBar(
    robotName: String,
    connectionStatus: UserConnectionStatus,
    statusMessage: String,
    route: UserMainRoute,
    settingsOpen: Boolean,
    detailHighlights: List<String>,
    onOpenMainControl: () -> Unit,
    onOpenRunner: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSceneViewer: () -> Unit = {}
) {
    val statusLabel = statusMessage.ifBlank {
        when (connectionStatus) {
            UserConnectionStatus.Disconnected -> stringResource(R.string.connection_status_disconnected)
            UserConnectionStatus.Connecting -> stringResource(R.string.connection_status_connecting)
            UserConnectionStatus.Connected -> stringResource(R.string.connection_status_connected)
        }
    }
    val statusColor = when (connectionStatus) {
        UserConnectionStatus.Connected -> Color(0xFF66D2FF)
        UserConnectionStatus.Connecting -> Color(0xFFF5C451)
        UserConnectionStatus.Disconnected -> Color(0xFFEF5350)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(robotName, style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = Color(0x10FFFFFF),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color(0x14FFFFFF))
                    ) {
                        Text(
                            text = when {
                                settingsOpen -> stringResource(R.string.nav_settings)
                                route == UserMainRoute.MainControl -> stringResource(R.string.route_main_control)
                                route == UserMainRoute.MotionRunner -> stringResource(R.string.route_motion_runner)
                                route == UserMainRoute.MotionLibrary -> stringResource(R.string.route_motion_library)
                                route == UserMainRoute.MotionCreator -> stringResource(R.string.route_motion_creator)
                                route == UserMainRoute.TouchControl -> stringResource(R.string.route_touch_control)
                                route == UserMainRoute.PostRecord -> stringResource(R.string.route_post_record)
                                route == UserMainRoute.MediaGallery -> stringResource(R.string.route_media_gallery)
                                route == UserMainRoute.SmartFollow -> stringResource(R.string.route_smart_follow)
                                else -> stringResource(R.string.route_slam_maps)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xCCFFFFFF)
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.24f))
                    ) {
                        Text(
                            text = statusLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
                if (detailHighlights.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        detailHighlights.take(4).forEach { item ->
                            Surface(
                                color = Color(0x10FFFFFF),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, Color(0x14FFFFFF))
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xCCFFFFFF)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!settingsOpen && route != UserMainRoute.MainControl) {
                    OutlinedButton(onClick = onOpenMainControl) {
                        Text(stringResource(R.string.route_main_control))
                    }
                }
                if (!settingsOpen && route != UserMainRoute.MotionRunner) {
                    Button(
                        onClick = onOpenRunner,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A3FF),
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.route_motion_runner))
                    }
                }
                TextButton(onClick = onOpenSceneViewer) {
                    Text("SceneViewer")
                }
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.nav_settings))
                }
            }
        }
    }
}

@Composable
private fun MainControlRoute(
    uiState: MainControlUiState,
    videoState: UserRunVideoUiState,
    videoBitmap: Bitmap?,
    navState: UserNavState,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    onToggleRecording: () -> Unit,
    onOpenRunner: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCreator: () -> Unit,
    onOpenSmartFollow: () -> Unit,
    onOpenTouchControl: () -> Unit,
    onOpenSlamMaps: () -> Unit,
    onOpenMediaGallery: () -> Unit,
    onOpenPostRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectPoiSession: (UserPoiSession) -> Unit,
    onSelectPoiIndex: (Int) -> Unit,
    onNavGo: () -> Unit,
    onNavStop: () -> Unit,
    onNavPause: () -> Unit,
    onNavResume: () -> Unit,
    onNavSpeedChange: (Double) -> Unit,
    onFollowToggle: (Boolean) -> Unit,
    onModeChange: (UserOperationMode) -> Unit,
    onRefreshSessions: () -> Unit,
    onTargetRoi: (xOffset: Int, yOffset: Int, width: Int, height: Int) -> Unit
) {
    var showGrid by rememberSaveable { mutableStateOf(uiState.preview.showGrid) }
    var showNavPanel by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        MainControlScreen(
            state = uiState.copy(
                preview = uiState.preview.copy(showGrid = showGrid)
            ),
            previewContent = {
                val roiSize = 80
                VideoPreviewContent(
                    showBitmapFrame = videoState.showBitmapFrame,
                    videoBitmap = videoBitmap,
                    contentDescription = uiState.preview.title,
                    onVideoSurfaceReady = onVideoSurfaceReady,
                    onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
                    modifier = Modifier.pointerInput(navState.operationMode, navState.followActive) {
                        if (navState.operationMode == UserOperationMode.SubjectFollowing) {
                            detectTapGestures { offset ->
                                val x = (offset.x - roiSize / 2).toInt().coerceAtLeast(0)
                                val y = (offset.y - roiSize / 2).toInt().coerceAtLeast(0)
                                onTargetRoi(x, y, roiSize, roiSize)
                            }
                        }
                    }
                )
            },
            onToolClick = { key ->
                when (key) {
                    MainControlToolKey.Grid -> showGrid = !showGrid
                    MainControlToolKey.Navigation -> showNavPanel = !showNavPanel
                    MainControlToolKey.Maps -> onOpenSlamMaps()
                    MainControlToolKey.Gallery -> onOpenMediaGallery()
                    MainControlToolKey.Settings -> onOpenSettings()
                    else -> Unit
                }
            },
            onShortcutClick = { key ->
                when (key) {
                    MainControlShortcutKey.MotionLibrary -> onOpenLibrary()
                    MainControlShortcutKey.CreateMotion -> onOpenCreator()
                    MainControlShortcutKey.SmartFollow -> onOpenSmartFollow()
                }
            },
            onTransportControlClick = { key ->
                when (key) {
                    MainControlTransportControlKey.PlayPause -> onOpenRunner()
                    MainControlTransportControlKey.Record -> onToggleRecording()
                    MainControlTransportControlKey.Save -> onOpenPostRecord()
                    else -> Unit
                }
            },
            onPrimaryActionClick = onOpenTouchControl,
            onRecentMotionClick = { onOpenRunner() }
        )

        // Navigation panel overlay — slides in from the right
        NavigationPanel(
            navState = navState,
            visible = showNavPanel,
            onDismiss = { showNavPanel = false },
            onSelectPoiSession = onSelectPoiSession,
            onSelectPoiIndex = onSelectPoiIndex,
            onGo = onNavGo,
            onStop = onNavStop,
            onPause = onNavPause,
            onResume = onNavResume,
            onSpeedChange = onNavSpeedChange,
            onFollowToggle = onFollowToggle,
            onModeChange = onModeChange,
            onRefreshSessions = onRefreshSessions,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun MotionCreatorRoute(
    chatViewModel: ChatViewModel,
    chatServerUrl: String,
    showBitmapFrame: Boolean,
    videoBitmap: android.graphics.Bitmap?,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    captureThumbnail: () -> String?,
    captureRobotState: (() -> com.recomo.common.chat.RobotStateSnapshot?)? = null,
    onBack: () -> Unit,
    onPreviewTrajectory: (TrajectoryAttachment) -> Unit,
    onExecuteTrajectory: (TrajectoryAttachment) -> Unit,
    onPreviewCandidate: (TrajectoryCandidate) -> Unit,
    onExecuteCandidate: (TrajectoryCandidate) -> Unit,
    onCopyStylePresetSelected: (MotionDetailItem) -> Unit = {},
    onOpenSceneViewer: (SceneViewerLaunchRequest) -> Unit = {},
    voiceEngine: com.recomo.common.chat.voice.VoiceEngine = com.recomo.common.chat.voice.VoiceEngine.SYSTEM,
    whisperRepository: com.recomo.common.chat.voice.WhisperModelRepository? = null,
    voiceModelId: String = com.recomo.common.chat.voice.WhisperModelRepository.DEFAULT_MODEL_ID
) {
    var activeMode by rememberSaveable { mutableStateOf(MotionCreatorMode.Chat) }
    val motionCreatorViewModel: com.recomo.user.control.UserMotionCreatorViewModel = hiltViewModel()
    val eePositionViewModel: com.recomo.user.control.UserEEPositionViewModel = hiltViewModel()
    MotionCreatorScreen(
        state = MotionCreatorShellUiState(
            title = stringResource(R.string.route_motion_creator),
            subtitle = stringResource(R.string.creator_subtitle),
            statusLabel = stringResource(R.string.creator_status),
            guidanceLabel = stringResource(R.string.creator_guidance),
            activeMode = activeMode,
            modes = listOf(
                MotionCreatorModeItemUiState(
                    mode = MotionCreatorMode.Chat,
                    shortLabel = "AI",
                    title = stringResource(R.string.creator_ai_chat),
                    subtitle = stringResource(R.string.creator_ai_chat_sub),
                    detail = ""
                ),
                MotionCreatorModeItemUiState(
                    mode = MotionCreatorMode.CopyStyle,
                    shortLabel = "CS",
                    title = stringResource(R.string.creator_copy_style),
                    subtitle = stringResource(R.string.creator_copy_style_sub),
                    detail = ""
                ),
                MotionCreatorModeItemUiState(
                    mode = MotionCreatorMode.Keypoint,
                    shortLabel = "KP",
                    title = stringResource(R.string.creator_keypoint),
                    subtitle = stringResource(R.string.creator_keypoint_sub),
                    detail = ""
                ),
                MotionCreatorModeItemUiState(
                    mode = MotionCreatorMode.Phone,
                    shortLabel = "PH",
                    title = stringResource(R.string.creator_phone_teach),
                    subtitle = stringResource(R.string.creator_phone_teach_sub),
                    detail = "",
                    badge = null
                )
            )
        ),
        onModeSelected = { activeMode = it },
        onCopyStylePresetSelected = onCopyStylePresetSelected,
        chatContent = {
            ChatScreen(
                viewModel = chatViewModel,
                chatServerUrl = chatServerUrl,
                onPreviewTrajectory = onPreviewTrajectory,
                onExecuteTrajectory = onExecuteTrajectory,
                onPreviewCandidate = onPreviewCandidate,
                onExecuteCandidate = onExecuteCandidate,
                videoConfig = com.recomo.user.ui.screens.chat.ChatVideoConfig(
                    showBitmapFrame = showBitmapFrame,
                    videoBitmap = videoBitmap,
                    onVideoSurfaceReady = onVideoSurfaceReady,
                    onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
                    captureSnapshot = captureThumbnail,
                    captureRobotState = captureRobotState
                ),
                voiceEngine = voiceEngine,
                whisperRepository = whisperRepository,
                voiceModelId = voiceModelId
            )
        },
        keypointContent = {
            com.recomo.user.ui.screens.creator.KeypointCaptureWorkspace(
                motionCreatorViewModel = motionCreatorViewModel,
                eePositionViewModel = eePositionViewModel,
                showBitmapFrame = showBitmapFrame,
                videoBitmap = videoBitmap,
                onVideoSurfaceReady = onVideoSurfaceReady,
                onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
                captureThumbnail = captureThumbnail,
                modifier = Modifier.fillMaxSize()
            )
        },
        phoneTeachContent = {
            PhoneTeachNavHost(
                onPreviewTrajectory = { tumFile ->
                    onOpenSceneViewer(
                        SceneViewerLaunchRequest(
                            title = tumFile.parentFile?.name ?: "Phone Moco Trajectory",
                            entrySource = SceneViewerEntrySource.LocalSession,
                            trajectorySource = SceneTrajectorySource.LocalFile(tumFile.absolutePath)
                        )
                    )
                }
            )
        }
    )
}

@Composable
private fun MotionRunnerRoute(
    uiState: MotionRunnerUiState,
    onBack: () -> Unit,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    onLoad: () -> Unit,
    onRun: () -> Unit,
    onInit: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onOpenMainControl: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    MotionRunnerScreen(
        state = uiState,
        onAction = { actionId ->
            when (actionId) {
                "back" -> onBack()
                "home_route" -> onOpenMainControl()
                "load" -> onLoad()
                "init" -> onInit()
                "run" -> onRun()
                "pause" -> onPause()
                "stop" -> onStop()
                "home" -> onHome()
            }
        },
        onOverlayAction = { actionId ->
            when (actionId) {
                "resume" -> onRun()
                "estop_clear" -> onEmergencyStop()
            }
        },
        onEmergencyStopHoldStart = onEmergencyStop,
        onEmergencyStopHoldEnd = {},
        onSurfaceReady = onVideoSurfaceReady,
        onSurfaceDestroyed = onVideoSurfaceDestroyed
    )
}

@Composable
private fun LibraryRoute(
    state: LibrarySummaryUiState,
    onBack: () -> Unit,
    onCreateMotion: () -> Unit,
    onRefresh: () -> Unit,
    onSelectSession: (LibrarySessionSummaryUiItem) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf("all") }
    var viewMode by rememberSaveable { mutableStateOf(MotionLibraryViewMode.Grid) }
    val workspaceState = state.toMotionLibraryWorkspaceState(
        searchQuery = searchQuery,
        selectedCategoryId = selectedCategoryId,
        viewMode = viewMode
    )

    MotionLibraryWorkspace(
        state = workspaceState,
        onBack = onBack,
        onCreateMotion = onCreateMotion,
        onSearchQueryChange = { searchQuery = it },
        onCategorySelected = { selectedCategoryId = it.id },
        onViewModeChange = { viewMode = it },
        onEntryClick = { entry ->
            val session = (state.foiSessions + state.poiSessions).firstOrNull { it.sessionId == entry.id }
            if (session != null) onSelectSession(session)
        },
        onRunEntry = { entry ->
            val session = (state.foiSessions + state.poiSessions).firstOrNull { it.sessionId == entry.id }
            if (session != null) onSelectSession(session)
        },
        onEditEntry = {},
        onToggleFavorite = {},
        onDeleteEntry = {},
        onSidebarActionClick = {
            if (it.id == "storage") onRefresh()
        }
    )
}

@Composable
private fun TouchControlRoute(
    state: TouchControlWorkspaceState,
    title: String,
    slowLabel: String,
    normalLabel: String,
    fastLabel: String,
    activeLabel: String,
    chassisTitle: String,
    chassisSubtitle: String,
    armTitle: String,
    armSubtitle: String,
    gimbalTitle: String,
    gimbalSubtitle: String,
    connectionLabel: String,
    safetyLabel: String,
    estopLabel: String,
    clearEstopLabel: String,
    freezeLabel: String,
    unfreezeLabel: String,
    yawLabel: String,
    armHomeLabel: String,
    gimbalHomeLabel: String,
    stopLabel: String,
    videoState: UserRunVideoUiState,
    videoBitmap: Bitmap?,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onSpeedModeChange: (TouchControlSpeedMode) -> Unit,
    onChassisForwardTap: () -> Unit,
    onChassisForwardHoldStart: () -> Unit,
    onChassisForwardHoldEnd: () -> Unit,
    onChassisBackTap: () -> Unit,
    onChassisBackHoldStart: () -> Unit,
    onChassisBackHoldEnd: () -> Unit,
    onChassisLeftTap: () -> Unit,
    onChassisLeftHoldStart: () -> Unit,
    onChassisLeftHoldEnd: () -> Unit,
    onChassisRightTap: () -> Unit,
    onChassisRightHoldStart: () -> Unit,
    onChassisRightHoldEnd: () -> Unit,
    onChassisRotateLeftTap: () -> Unit,
    onChassisRotateLeftHoldStart: () -> Unit,
    onChassisRotateLeftHoldEnd: () -> Unit,
    onChassisRotateRightTap: () -> Unit,
    onChassisRotateRightHoldStart: () -> Unit,
    onChassisRotateRightHoldEnd: () -> Unit,
    onArmJointTap: (jointIndex: Int, positive: Boolean) -> Unit,
    onArmJointHoldStart: (jointIndex: Int, positive: Boolean) -> Unit,
    onArmJointHoldEnd: () -> Unit,
    onGimbalTap: (axis: Int, positive: Boolean) -> Unit,
    onGimbalHoldStart: (axis: Int, positive: Boolean) -> Unit,
    onGimbalHoldEnd: (axis: Int) -> Unit,
    onEmergencyStopTap: () -> Unit,
    onFreezeToggleTap: () -> Unit,
    onArmHome: () -> Unit,
    onGimbalHome: () -> Unit,
    onStopAll: () -> Unit
) {
    TouchControlScreen(
        state = state,
        title = title,
        slowLabel = slowLabel,
        normalLabel = normalLabel,
        fastLabel = fastLabel,
        chassisTitle = chassisTitle,
        chassisSubtitle = chassisSubtitle,
        armTitle = armTitle,
        armSubtitle = armSubtitle,
        gimbalTitle = gimbalTitle,
        gimbalSubtitle = gimbalSubtitle,
        activeLabel = activeLabel,
        connectionLabel = connectionLabel,
        safetyLabel = safetyLabel,
        estopLabel = estopLabel,
        clearEstopLabel = clearEstopLabel,
        freezeLabel = freezeLabel,
        unfreezeLabel = unfreezeLabel,
        yawLabel = yawLabel,
        armHomeLabel = armHomeLabel,
        gimbalHomeLabel = gimbalHomeLabel,
        stopLabel = stopLabel,
        onBack = onBack,
        onToggleGrid = onToggleGrid,
        onSpeedModeChange = onSpeedModeChange,
        onChassisForwardTap = onChassisForwardTap,
        onChassisForwardHoldStart = onChassisForwardHoldStart,
        onChassisForwardHoldEnd = onChassisForwardHoldEnd,
        onChassisBackTap = onChassisBackTap,
        onChassisBackHoldStart = onChassisBackHoldStart,
        onChassisBackHoldEnd = onChassisBackHoldEnd,
        onChassisLeftTap = onChassisLeftTap,
        onChassisLeftHoldStart = onChassisLeftHoldStart,
        onChassisLeftHoldEnd = onChassisLeftHoldEnd,
        onChassisRightTap = onChassisRightTap,
        onChassisRightHoldStart = onChassisRightHoldStart,
        onChassisRightHoldEnd = onChassisRightHoldEnd,
        onChassisRotateLeftTap = onChassisRotateLeftTap,
        onChassisRotateLeftHoldStart = onChassisRotateLeftHoldStart,
        onChassisRotateLeftHoldEnd = onChassisRotateLeftHoldEnd,
        onChassisRotateRightTap = onChassisRotateRightTap,
        onChassisRotateRightHoldStart = onChassisRotateRightHoldStart,
        onChassisRotateRightHoldEnd = onChassisRotateRightHoldEnd,
        onArmJointTap = onArmJointTap,
        onArmJointHoldStart = onArmJointHoldStart,
        onArmJointHoldEnd = onArmJointHoldEnd,
        onGimbalTap = onGimbalTap,
        onGimbalHoldStart = onGimbalHoldStart,
        onGimbalHoldEnd = onGimbalHoldEnd,
        onEmergencyStopTap = onEmergencyStopTap,
        onFreezeToggleTap = onFreezeToggleTap,
        onArmHome = onArmHome,
        onGimbalHome = onGimbalHome,
        onStopAll = onStopAll,
        previewContent = {
            VideoPreviewContent(
                showBitmapFrame = videoState.showBitmapFrame,
                videoBitmap = videoBitmap,
                contentDescription = title,
                onVideoSurfaceReady = onVideoSurfaceReady,
                onVideoSurfaceDestroyed = onVideoSurfaceDestroyed
            )
        }
    )
}

@Composable
private fun MediaGalleryRoute(
    state: UserMediaGalleryUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (UserMediaGalleryFilter) -> Unit,
    onSortChange: (UserMediaGallerySort) -> Unit,
    onViewModeChange: (UserMediaGalleryViewMode) -> Unit,
    onRefresh: () -> Unit,
    onDownload: (UserMediaItem) -> Unit,
    onDeleteLocal: (UserMediaItem) -> Unit,
    getLocalFile: (UserMediaItem) -> java.io.File?
) {
    var selectedMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedItem = remember(state.items, selectedMediaId) {
        state.items.firstOrNull { it.id == selectedMediaId }
    }

    LaunchedEffect(state.items, selectedMediaId) {
        if (selectedMediaId != null && selectedItem == null) {
            selectedMediaId = null
        }
    }

    MediaGalleryWorkspace(
        state = state,
        selectedItem = selectedItem,
        onBack = onBack,
        onSelectItem = { selectedMediaId = it.id },
        onClearSelection = { selectedMediaId = null },
        onSearchQueryChange = onSearchQueryChange,
        onFilterChange = onFilterChange,
        onSortChange = onSortChange,
        onViewModeChange = onViewModeChange,
        onRefresh = onRefresh,
        onDownload = onDownload,
        onDeleteLocal = onDeleteLocal,
        getLocalFile = getLocalFile
    )
}

@Composable
private fun PostRecordRoute(
    state: UserPostRecordUiState,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onRunAgain: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: (com.recomo.user.data.postrecord.UserPostRecordItem) -> Unit
) {
    var selectedRecordingId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRecording = remember(state.recordings, selectedRecordingId) {
        state.recordings.firstOrNull { it.id == selectedRecordingId } ?: state.recordings.firstOrNull()
    }

    LaunchedEffect(state.recordings, selectedRecordingId) {
        when {
            state.recordings.isEmpty() -> selectedRecordingId = null
            selectedRecordingId == null -> selectedRecordingId = state.recordings.first().id
            state.recordings.none { it.id == selectedRecordingId } -> {
                selectedRecordingId = state.recordings.first().id
            }
        }
    }

    PostRecordWorkspace(
        state = state,
        selectedRecording = selectedRecording,
        onBack = onBack,
        onSelectRecording = { selectedRecordingId = it.id },
        onRefresh = onRefresh,
        onDelete = onDelete,
        onOpenGallery = onOpenGallery,
        onRunAgain = onRunAgain
    )
}

@Composable
private fun PlaceholderWorkspace(
    title: String,
    body: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x14FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xB3FFFFFF)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOverlay(
    state: SettingsUiState,
    systemInfoState: com.recomo.user.ui.screens.system.SystemInfoCardUiState,
    systemLoadState: com.recomo.user.ui.screens.system.SystemLoadCardUiState,
    gatewayControlState: com.recomo.user.ui.screens.system.GatewayControlCardUiState,
    gatewayServiceState: com.recomo.user.ui.screens.system.GatewayServiceCardUiState,
    actuatorCardState: com.recomo.user.ui.screens.system.ActuatorCardUiState = com.recomo.user.ui.screens.system.ActuatorCardUiState(),
    sceneCardState: com.recomo.user.ui.screens.system.SceneCardUiState = com.recomo.user.ui.screens.system.SceneCardUiState(),
    autonomyCardState: com.recomo.user.ui.screens.system.AutonomyCardUiState = com.recomo.user.ui.screens.system.AutonomyCardUiState(),
    cloudCardState: com.recomo.user.ui.screens.system.CloudCardUiState = com.recomo.user.ui.screens.system.CloudCardUiState(),
    availableRobots: List<UserRobotOption>,
    selectedRobot: UserRobotOption,
    onSelectRobot: (UserRobotOption) -> Unit,
    onClose: () -> Unit,
    onConnectOrReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshGatewayService: () -> Unit,
    onStartGatewayService: () -> Unit,
    onStopGatewayService: () -> Unit,
    onSetPncAuthorityAuto: () -> Unit,
    onSetPncAuthorityLocal: () -> Unit,
    onSetPncAuthorityExternal: () -> Unit,
    onPreparePerception: () -> Unit,
    onDismissPerception: () -> Unit,
    onPrepareLocalizationStack: () -> Unit,
    onDismissLocalizationStack: () -> Unit,
    onPreparePnc: () -> Unit,
    onDismissPnc: () -> Unit,
    onPrepareActuators: () -> Unit = {},
    onDismissActuators: () -> Unit = {},
    onPrepareSensors: () -> Unit = {},
    onDismissSensors: () -> Unit = {},
    onPrepareScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    onUseWebRTCChange: (Boolean) -> Unit,
    onVideoSourceChange: (VideoSource) -> Unit = {},
    onChatServerUrlChange: (String) -> Unit,
    onChatDirectEnabledChange: (Boolean) -> Unit = {},
    onChatDirectBaseUrlChange: (String) -> Unit = {},
    onChatDirectAuthTokenChange: (String) -> Unit = {},
    onVoiceEngineChange: (com.recomo.common.chat.voice.VoiceEngine) -> Unit = {},
    onVoiceModelChange: (com.recomo.common.chat.voice.WhisperModel) -> Unit = {},
    onDownloadWhisperModel: () -> Unit = {},
    onSessionFolderPathChange: (String) -> Unit,
    onSceneViewerFolderPathChange: (String) -> Unit = {},
    onSpeedTierChange: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onCountdownDurationChange: (Int) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onClose) {
                Text(stringResource(R.string.action_back))
            }

            SettingsPanel(
                state = state,
                availableRobots = availableRobots,
                selectedRobot = selectedRobot,
                systemInfoState = systemInfoState,
                systemLoadState = systemLoadState,
                gatewayControlState = gatewayControlState,
                gatewayServiceState = gatewayServiceState,
                actuatorCardState = actuatorCardState,
                sceneCardState = sceneCardState,
                autonomyCardState = autonomyCardState,
                cloudCardState = cloudCardState,
                modifier = Modifier.fillMaxWidth(),
                onConnectOrReconnectClick = onConnectOrReconnect,
                onDisconnectClick = onDisconnect,
                onRefreshGatewayServiceClick = onRefreshGatewayService,
                onStartGatewayServiceClick = onStartGatewayService,
                onStopGatewayServiceClick = onStopGatewayService,
                onSetPncAuthorityAutoClick = onSetPncAuthorityAuto,
                onSetPncAuthorityLocalClick = onSetPncAuthorityLocal,
                onSetPncAuthorityExternalClick = onSetPncAuthorityExternal,
                onPreparePerceptionClick = onPreparePerception,
                onDismissPerceptionClick = onDismissPerception,
                onPrepareLocalizationClick = onPrepareLocalizationStack,
                onDismissLocalizationClick = onDismissLocalizationStack,
                onPreparePncClick = onPreparePnc,
                onDismissPncClick = onDismissPnc,
                onPrepareActuatorsClick = onPrepareActuators,
                onDismissActuatorsClick = onDismissActuators,
                onPrepareSensorsClick = onPrepareSensors,
                onDismissSensorsClick = onDismissSensors,
                onPrepareSceneClick = onPrepareScene,
                onDismissSceneClick = onDismissScene,
                onSelectRobot = onSelectRobot,
                onUseWebRTCChange = onUseWebRTCChange,
                onVideoSourceChange = onVideoSourceChange,
                onChatServerUrlChange = onChatServerUrlChange,
                onChatDirectEnabledChange = onChatDirectEnabledChange,
                onChatDirectBaseUrlChange = onChatDirectBaseUrlChange,
                onChatDirectAuthTokenChange = onChatDirectAuthTokenChange,
                onVoiceEngineChange = onVoiceEngineChange,
                onVoiceModelChange = onVoiceModelChange,
                onDownloadWhisperModel = onDownloadWhisperModel,
                onSessionFolderPathChange = onSessionFolderPathChange,
                onSceneViewerFolderPathChange = onSceneViewerFolderPathChange,
                onSpeedTierChange = onSpeedTierChange,
                onCountdownDurationChange = onCountdownDurationChange
            )
        }
    }
}

private fun toneForStatus(status: String): MainControlTone {
    val normalized = status.lowercase()
    return when {
        "connect" in normalized || "ready" in normalized || "localized" in normalized -> MainControlTone.Success
        "disconnected" in normalized || "error" in normalized || "stop" in normalized -> MainControlTone.Danger
        "connecting" in normalized || "waiting" in normalized || "unset" in normalized -> MainControlTone.Warning
        else -> MainControlTone.Neutral
    }
}

private fun toneForRunnerStatus(ready: Boolean): MotionRunnerTone =
    if (ready) MotionRunnerTone.Success else MotionRunnerTone.Warning

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
