package com.recomo.remotecontrol

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Filter2
import androidx.compose.material.icons.filled.Filter3
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.recomo.remotecontrol.camviewer.ui.screens.settings.SettingsScreen
import com.recomo.remotecontrol.camviewer.ui.screens.video.VideoScreen
import com.recomo.remotecontrol.camviewer.ui.screens.video.VideoViewModel
import com.recomo.remotecontrol.controller.ControllerManager
import com.recomo.remotecontrol.controller.ControllerRouter
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.ui.ArmControlMode
import com.recomo.remotecontrol.ui.ControlFocus
import com.recomo.remotecontrol.ui.ControlMode
import com.recomo.remotecontrol.ui.ControllerSettingsPanel
import com.recomo.remotecontrol.ui.FrameTiming
import com.recomo.remotecontrol.ui.LibraryTarget
import com.recomo.remotecontrol.ui.RecomoControlViewModel
import com.recomo.remotecontrol.ui.RunControlMode
import com.recomo.remotecontrol.ui.RunStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.recomo.remotecontrol.ui.StepSettingsPanel
import com.recomo.remotecontrol.ui.StepMode
import com.recomo.remotecontrol.ui.TrajectoryPreviewOverlay
import com.recomo.remotecontrol.ui.TrajectoryPreviewPanel
import com.recomo.remotecontrol.ui.tracking.PanelToggleButton
import com.recomo.remotecontrol.ui.tracking.TrackingOverlay
import com.recomo.remotecontrol.ui.ControlViewMode
import com.recomo.remotecontrol.ui.EEPositionControlPanel
import com.recomo.remotecontrol.ui.SafetyStatus
import com.recomo.remotecontrol.ui.ControllerStatus
import com.recomo.remotecontrol.ui.OperationMode
import com.recomo.remotecontrol.settings.ControllerSettingsViewModel
import com.recomo.remotecontrol.settings.StepSettingsViewModel
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import com.recomo.remotecontrol.network.OrinGatewayClient
import kotlin.math.PI
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Slider

private val TabRailWidth = 96.dp
private val ChipColumnWidth = 64.dp
private val ChipSize = 38.dp
private const val TAB_TEACH = 0
private const val TAB_RUN = 1
private const val TAB_PHONE_MOCO = 2
private const val TAB_LIBRARY = 3
private const val TAB_VIDEO_MANAGEMENT = 4
private const val TAB_SETTINGS = 5

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val recomoViewModel: RecomoControlViewModel by viewModels()
    private val controllerSettingsViewModel: ControllerSettingsViewModel by viewModels()
    private val controllerManager = ControllerManager()
    private val controllerRouter by lazy { ControllerRouter(recomoViewModel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controllerRouter.start(this, controllerManager)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controllerSettingsViewModel.settings.collect { settings ->
                    controllerManager.updateSettings(settings)
                    controllerRouter.updateSettings(settings)
                }
            }
        }

        setContent {
            RecomoApp(recomoViewModel, controllerManager)
        }
        
        // Enable immersive mode after content is set
        window.decorView.post {
            enableImmersiveMode()
            
            // Exclude entire screen from system gestures to prevent Samsung Edge Panel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val rect = android.graphics.Rect(0, 0, window.decorView.width, window.decorView.height)
                window.decorView.systemGestureExclusionRects = listOf(rect)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        recomoViewModel.initImuTeleop(this)
    }

    override fun onPause() {
        super.onPause()
        recomoViewModel.stopImuTeleop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controllerManager.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerManager.onMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    
    private fun enableImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior = 
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}

@Composable
fun RecomoApp(viewModel: RecomoControlViewModel, controllerManager: ControllerManager) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val opMode by viewModel.operationMode.collectAsState()
    val controllerStatus by viewModel.controllerStatus.collectAsState()
    val controllerState by controllerManager.state.collectAsState()
    val tabs = listOf(
        TabItem("TEACH", Icons.Default.Edit),
        TabItem("RUN", Icons.Default.PlayArrow),
        TabItem("PHONE-MOCO", Icons.Default.Videocam),
        TabItem("LIBRARY", Icons.Default.CollectionsBookmark),
        TabItem("VIDEO", Icons.Default.CloudUpload),
        TabItem("SETTINGS", Icons.Default.Settings)
    )
    val videoViewModel: VideoViewModel = hiltViewModel()
    val context = LocalContext.current
    
    // Wire up thumbnail provider
    LaunchedEffect(Unit) {
        viewModel.thumbnailProvider = { videoViewModel.captureThumbnailBase64() }
    }
    LaunchedEffect(Unit) {
        viewModel.loadSampleSessions(context)
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                // VideoScreen and TrackingOverlay only for non Phone-Moco tabs.
                if (selectedTab != TAB_PHONE_MOCO) {
                    VideoScreen(
                        showTelemetryOverlay = false,
                        connectionControlsEndPadding = TabRailWidth + 8.dp
                    )
                    
                    // Tracking overlay for bounding box drawing and feedback
                    // Get VideoViewModel to access video resolution
                    val videoViewModel: VideoViewModel = hiltViewModel()
                    val telemetry by videoViewModel.latestTelemetry.collectAsState()
                    val trackingTargetWidth by viewModel.trackingTargetWidth.collectAsState()
                    val trackingTargetHeight by viewModel.trackingTargetHeight.collectAsState()
                    
                    // Calculate video content area based on actual resolution
                    var videoContentArea by remember { mutableStateOf<Rect?>(null) }
                    
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val viewWidth = constraints.maxWidth.toFloat()
                        val viewHeight = constraints.maxHeight.toFloat()
                        
                        // Get actual video resolution from telemetry or decoder config
                        val videoRes = telemetry?.resolution
                        val videoWidth = videoRes?.width?.toFloat() ?: 1920f
                        val videoHeight = videoRes?.height?.toFloat() ?: 1080f
                        
                        // Calculate content area accounting for aspect ratio fit
                        val videoAspect = videoWidth / videoHeight
                        val viewAspect = viewWidth / viewHeight
                        
                        videoContentArea = if (videoAspect > viewAspect) {
                            // Video is wider - letterbox top/bottom
                            val contentHeight = viewWidth / videoAspect
                            val offsetY = (viewHeight - contentHeight) / 2f
                            Rect(0f, offsetY, viewWidth, offsetY + contentHeight)
                        } else {
                            // Video is taller - pillarbox left/right
                            val contentWidth = viewHeight * videoAspect
                            val offsetX = (viewWidth - contentWidth) / 2f
                            Rect(offsetX, 0f, offsetX + contentWidth, viewHeight)
                        }
                        
                        if (selectedTab == TAB_RUN && opMode == OperationMode.SUBJECT_FOLLOWING) {
                            TrackingOverlay(
                                gatewayClient = viewModel.getGatewayClient(),
                                videoContentArea = videoContentArea,
                                targetWidth = trackingTargetWidth,
                                targetHeight = trackingTargetHeight,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                when (selectedTab) {
                    TAB_TEACH -> TeachOverlay(viewModel, controllerState)
                    TAB_RUN -> RunOverlay(viewModel)
                    TAB_PHONE_MOCO -> PhoneMocoOverlay(
                        modifier = Modifier.fillMaxSize().padding(end = TabRailWidth + 8.dp)
                    )
                    TAB_LIBRARY -> LibraryOverlay(viewModel)
                    TAB_VIDEO_MANAGEMENT -> VideoManagementOverlay()
                    TAB_SETTINGS -> SettingsOverlay(controllerManager)
                }
                // Hide robot status badges on Phone-Moco (V3DR has its own UI) and Settings (obstructs content)
                if (selectedTab != TAB_PHONE_MOCO && selectedTab != TAB_SETTINGS) {
                    ModeStatusBadge(
                        status = controllerStatus,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                    // System health bar — center-top, between ModeStatusBadge and ConnectionControls
                    SystemHealthBar(
                        viewModel = viewModel,
                        onTap = { viewModel.navigateToSettingsGroup("ORIN") },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .padding(end = TabRailWidth + 8.dp)
                    )
                    if (controllerStatus.modeSelectActive) {
                        ModeSelectOverlay(
                            status = controllerStatus,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 56.dp)
                        )
                    }
                }
                LaunchedEffect(selectedTab) {
                    if (selectedTab == TAB_LIBRARY) {
                        viewModel.requestLibraryList(LibraryTarget.FOI_SESSION)
                    } else if (selectedTab == TAB_VIDEO_MANAGEMENT) {
                        viewModel.refreshGatewayService()
                        viewModel.refreshVideoManagementStatus()
                    }
                }
                RightTabRail(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onSelect = { viewModel.setSelectedTab(it) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachOverlay(viewModel: RecomoControlViewModel, controllerState: com.recomo.remotecontrol.controller.ControllerState) {
    val context = LocalContext.current
    val safety by viewModel.safetyStatus.collectAsState()
    val stepMode by viewModel.stepMode.collectAsState()
    val focus by viewModel.controlFocus.collectAsState()
    val armMode by viewModel.armControlMode.collectAsState()
    val controlViewMode by viewModel.controlViewMode.collectAsState()
    val robotProfile by viewModel.robotProfile.collectAsState()
    val armAngles by viewModel.armAngles.collectAsState()
    val gimbalAngles by viewModel.gimbalAngles.collectAsState()
    val baseYawRad by viewModel.baseYawRad.collectAsState()
    val controllerStatus by viewModel.controllerStatus.collectAsState()
    val controllerSettingsViewModel: ControllerSettingsViewModel = hiltViewModel()
    val controllerSettings by controllerSettingsViewModel.settings.collectAsState()
    val currentSessionName by viewModel.currentSessionName.collectAsState()
    val currentFrameCount by viewModel.currentFrameCount.collectAsState()
    val hasHomebase by viewModel.hasHomebase.collectAsState()
    val suggestedSessionName by viewModel.suggestedSessionName.collectAsState()
    val suggestedFrameName by viewModel.suggestedFrameName.collectAsState()
    var sessionName by rememberSaveable { mutableStateOf("") }
    var frameName by rememberSaveable { mutableStateOf("") }
    var panelsVisible by remember { mutableStateOf(true) }
    
    // Fetch map list when connected
    val connectionState by viewModel.connectionState.collectAsState()
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            viewModel.fetchMapList()
        }
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLargeScreen = maxWidth > 900.dp || maxHeight > 700.dp
            // For large screens: top spacer gets 3 parts, panels get 4 parts (4/7 ≈ 57%)
            val topWeight = if (isLargeScreen) 3f else 1f
            val panelWeight = if (isLargeScreen) 4f else 1f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = TabRailWidth + 8.dp)
            ) {
                Spacer(modifier = Modifier.weight(topWeight))
                
                // Control View Mode Toggle
                if (panelsVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlViewModeToggle(
                            currentMode = controlViewMode,
                            onModeChange = { viewModel.setControlViewMode(it) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SecondaryButton(
                                text = "ARM-H",
                                modifier = Modifier.height(40.dp).width(72.dp),
                                enabled = focus == ControlFocus.ARM
                            ) { viewModel.sendArmHomingPose() }
                            SecondaryButton(
                                text = "GMBL-H",
                                modifier = Modifier.height(40.dp).width(72.dp),
                                enabled = focus == ControlFocus.GIMBAL
                            ) { viewModel.sendGimbalHomingPose() }
                        }
                    }
                }
                
                // Wrap panels in a weighted box that maintains proper height
                Box(
                    modifier = Modifier
                        .weight(panelWeight)
                        .fillMaxWidth()
                ) {
                    if (panelsVisible) {
                        // Choose panel layout based on control view mode
                        when (controlViewMode) {
                            ControlViewMode.WORLD_VIEW -> {
                                // Traditional layout: Chassis + Arm + Gimbal + FOI
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                    val controlMode = controllerStatus.controlMode
                    val focusMode = controlMode == ControlMode.FOCUS
                    val positionMode = controlMode == ControlMode.POSITION
                    val upperMode = controlMode == ControlMode.UPPER
                    val cineMode = controlMode == ControlMode.CINE
                    val allDofMode = controlMode == ControlMode.ALL_DOF
                    val chassisEnabled = (focusMode && focus == ControlFocus.CHASSIS) || positionMode || cineMode || allDofMode
                    val armEnabled = (focusMode && focus == ControlFocus.ARM) || positionMode || upperMode || allDofMode
                    val gimbalEnabled = (focusMode && focus == ControlFocus.GIMBAL) || upperMode || cineMode || allDofMode
                    val armJointEnabled = remember(robotProfile) {
                        if (robotProfile.isProto1Family()) setOf(0, 1, 2) else setOf(1, 2)
                    }
                    val axisThreshold = 0.35f
                    val triggerThreshold = 0.35f
                    val chassisControlActive = chassisEnabled
                    val gimbalControlActive = gimbalEnabled
                    val useChassisDpad = focusMode && focus == ControlFocus.CHASSIS
                    val useGimbalDpad = focusMode && focus == ControlFocus.GIMBAL
                    val jointOptions = remember(robotProfile) {
                        if (robotProfile.isProto1Family()) intArrayOf(0, 1, 2) else intArrayOf(1, 2)
                    }
                    var selectedJointUiIdx by remember(robotProfile) { mutableStateOf(0) }
                    var prevControllerState by remember { mutableStateOf(controllerState) }

                    LaunchedEffect(controllerState, armMode, focus, controlMode, robotProfile) {
                        val jointControlActive = armMode == ArmControlMode.JOINT_ANGLE &&
                            ((focusMode && focus == ControlFocus.ARM) || positionMode || upperMode || allDofMode)
                        if (jointControlActive) {
                            if (controllerState.dpadLeft && !prevControllerState.dpadLeft) {
                                selectedJointUiIdx = (selectedJointUiIdx - 1 + jointOptions.size) % jointOptions.size
                            }
                            if (controllerState.dpadRight && !prevControllerState.dpadRight) {
                                selectedJointUiIdx = (selectedJointUiIdx + 1) % jointOptions.size
                            }
                        }
                        prevControllerState = controllerState
                    }

                    val selectedJoint = jointOptions.getOrNull(selectedJointUiIdx) ?: jointOptions.first()
                    val chassisForwardAxis = if (controllerSettings.invertChassisY) -controllerState.leftY else controllerState.leftY
                    val armUseRightStick = positionMode
                    val armUseLeftStick = upperMode || (focusMode && focus == ControlFocus.ARM)
                    val armForwardAxisRaw = if (armUseRightStick) controllerState.rightY else controllerState.leftY
                    val armLateralAxisRaw = if (armUseRightStick) controllerState.rightX else controllerState.leftX
                    val armForwardAxis = if (controllerSettings.invertArmY) -armForwardAxisRaw else armForwardAxisRaw
                    val gimbalPitchAxis = if (controllerSettings.invertGimbalY) -controllerState.rightY else controllerState.rightY
                    val armVerticalAxis = controllerState.r2 - controllerState.l2

                    val forwardActive = (chassisControlActive && chassisForwardAxis > axisThreshold) ||
                        (useChassisDpad && controllerState.dpadUp)
                    val backActive = (chassisControlActive && chassisForwardAxis < -axisThreshold) ||
                        (useChassisDpad && controllerState.dpadDown)
                    val leftActive = (chassisControlActive && controllerState.leftX < -axisThreshold) ||
                        (useChassisDpad && controllerState.dpadLeft)
                    val rightActive = (chassisControlActive && controllerState.leftX > axisThreshold) ||
                        (useChassisDpad && controllerState.dpadRight)
                    val rotateLeftActive = (chassisControlActive && controllerState.l2 > triggerThreshold) ||
                        (focusMode && focus == ControlFocus.CHASSIS && controllerState.rightX < -axisThreshold)
                    val rotateRightActive = (chassisControlActive && controllerState.r2 > triggerThreshold) ||
                        (focusMode && focus == ControlFocus.CHASSIS && controllerState.rightX > axisThreshold)

                    val gimbalYawNegActive = (gimbalControlActive && controllerState.rightX < -axisThreshold) ||
                        (useGimbalDpad && controllerState.dpadLeft)
                    val gimbalYawPosActive = (gimbalControlActive && controllerState.rightX > axisThreshold) ||
                        (useGimbalDpad && controllerState.dpadRight)
                    val gimbalPitchNegActive = (gimbalControlActive && gimbalPitchAxis < -axisThreshold) ||
                        (useGimbalDpad && controllerState.dpadDown)
                    val gimbalPitchPosActive = (gimbalControlActive && gimbalPitchAxis > axisThreshold) ||
                        (useGimbalDpad && controllerState.dpadUp)
                    val gimbalRollNegActive = focusMode && focus == ControlFocus.GIMBAL &&
                        controllerState.l2 > triggerThreshold
                    val gimbalRollPosActive = focusMode && focus == ControlFocus.GIMBAL &&
                        controllerState.r2 > triggerThreshold

                    val armStickActive = armMode == ArmControlMode.EE_POSITION && (armUseLeftStick || armUseRightStick)
                    val armDpadActive = allDofMode && armMode == ArmControlMode.EE_POSITION
                    val armVerticalFromTriggers = armMode == ArmControlMode.EE_POSITION && armUseLeftStick
                    val armVerticalFromDpad = armMode == ArmControlMode.EE_POSITION && armUseRightStick
                    val armForwardActive = (armStickActive && armForwardAxis > axisThreshold) ||
                        (armDpadActive && !controllerState.r1 && controllerState.dpadUp)
                    val armBackActive = (armStickActive && armForwardAxis < -axisThreshold) ||
                        (armDpadActive && !controllerState.r1 && controllerState.dpadDown)
                    val armLeftActive = (armStickActive && armLateralAxisRaw < -axisThreshold) ||
                        (armDpadActive && controllerState.dpadLeft)
                    val armRightActive = (armStickActive && armLateralAxisRaw > axisThreshold) ||
                        (armDpadActive && controllerState.dpadRight)
                    val armUpActive = (armVerticalFromTriggers && armVerticalAxis > triggerThreshold) ||
                        (armVerticalFromDpad && controllerState.dpadUp) ||
                        (armDpadActive && controllerState.r1 && controllerState.dpadUp)
                    val armDownActive = (armVerticalFromTriggers && armVerticalAxis < -triggerThreshold) ||
                        (armVerticalFromDpad && controllerState.dpadDown) ||
                        (armDpadActive && controllerState.r1 && controllerState.dpadDown)

                    val jointControlActive = armMode == ArmControlMode.JOINT_ANGLE &&
                        ((focusMode && focus == ControlFocus.ARM) || positionMode || upperMode || allDofMode)
                    val jointStepPos = jointControlActive && controllerState.dpadUp
                    val jointStepNeg = jointControlActive && controllerState.dpadDown
                    val jointAxisActive = armMode == ArmControlMode.JOINT_ANGLE && (armUseLeftStick || armUseRightStick)
                    val jointAxisPos = jointAxisActive && armForwardAxis > axisThreshold
                    val jointAxisNeg = jointAxisActive && armForwardAxis < -axisThreshold
                    val jointPosActive = jointStepPos || jointAxisPos
                    val jointNegActive = jointStepNeg || jointAxisNeg
                    val joint4PosActive = selectedJoint == 0 && jointPosActive
                    val joint4NegActive = selectedJoint == 0 && jointNegActive
                    val joint5PosActive = selectedJoint == 1 && jointPosActive
                    val joint5NegActive = selectedJoint == 1 && jointNegActive
                    val joint6PosActive = selectedJoint == 2 && jointPosActive
                    val joint6NegActive = selectedJoint == 2 && jointNegActive

                    PanelBox(
                        title = "CHASSIS",
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        titleSize = 12.sp
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val compact = maxHeight < 220.dp
                            val gap = if (compact) 4.dp else 6.dp
                            val gridTopGap = if (compact) 8.dp else 12.dp
                            val buttonHeight = (maxHeight / 12).coerceIn(26.dp, 38.dp)
                            val arrowSize = if (compact) 18.sp else 22.sp
                            val labelSize = if (compact) 12.sp else 14.sp
                            val textOnlySize = labelSize

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DangerButton(
                                        text = "E-STOP",
                                        onClick = { viewModel.sendEstop() },
                                        modifier = Modifier
                                            .height(buttonHeight)
                                            .weight(1f)
                                    )
                                    SecondaryButton(
                                        text = if (safety.freezeAll) "UNFREEZE" else "FREEZE",
                                        onClick = {
                                            if (safety.freezeAll) viewModel.unfreezeAll() else viewModel.freezeAll()
                                        },
                                        modifier = Modifier
                                            .height(buttonHeight)
                                            .weight(1f)
                                    )
                                }
                                if (safety.estop) {
                                    Spacer(modifier = Modifier.height(gap / 2))
                                    SecondaryButton(
                                        text = if (safety.estopCooldownMs > 0)
                                            "CLEAR (${String.format("%.1f", safety.estopCooldownMs / 1000.0)}s)"
                                        else "CLEAR",
                                        onClick = { viewModel.clearEstop() },
                                        enabled = safety.canClearEstop,
                                        modifier = Modifier
                                            .height(buttonHeight)
                                            .fillMaxWidth(0.6f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(gridTopGap))
                                ChassisControlGrid(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .alpha(if (chassisEnabled) 1f else 0.4f),
                                    arrowSize = arrowSize,
                                    textSize = textOnlySize,
                                    enabled = chassisEnabled,
                                    forwardActive = forwardActive,
                                    backActive = backActive,
                                    leftActive = leftActive,
                                    rightActive = rightActive,
                                    rotateLeftActive = rotateLeftActive,
                                    rotateRightActive = rotateRightActive,
                                    onForwardTap = { viewModel.sendChassisStep(1, 0) },
                                    onForwardHoldStart = { viewModel.startChassisMove(1, 0, 0) },
                                    onForwardHoldEnd = { viewModel.stopChassisMove() },
                                    onBackTap = { viewModel.sendChassisStep(-1, 0) },
                                    onBackHoldStart = { viewModel.startChassisMove(-1, 0, 0) },
                                    onBackHoldEnd = { viewModel.stopChassisMove() },
                                    onLeftTap = { viewModel.sendChassisStep(0, 1) },
                                    onLeftHoldStart = { viewModel.startChassisMove(0, 1, 0) },
                                    onLeftHoldEnd = { viewModel.stopChassisMove() },
                                    onRightTap = { viewModel.sendChassisStep(0, -1) },
                                    onRightHoldStart = { viewModel.startChassisMove(0, -1, 0) },
                                    onRightHoldEnd = { viewModel.stopChassisMove() },
                                    onRotateLeftTap = { viewModel.sendChassisRotate(1) },
                                    onRotateLeftHoldStart = { viewModel.startChassisMove(0, 0, 1) },
                                    onRotateLeftHoldEnd = { viewModel.stopChassisMove() },
                                    onRotateRightTap = { viewModel.sendChassisRotate(-1) },
                                    onRotateRightHoldStart = { viewModel.startChassisMove(0, 0, -1) },
                                    onRotateRightHoldEnd = { viewModel.stopChassisMove() }
                                )
                                Spacer(modifier = Modifier.height(gap / 2))
                                Text(
                                    text = "Yaw ${formatAngle(baseYawRad)}",
                                    color = Color(0xFFDDDDDD),
                                    fontSize = if (compact) 10.sp else 11.sp
                                )
                            }
                        }
                    }

                    FocusColumn(
                        focus = focus,
                        onFocusChange = { viewModel.setControlFocus(it) },
                        modifier = Modifier
                            .width(ChipColumnWidth)
                            .fillMaxHeight()
                    )

                    PanelBox(
                        title = "FRAME OF INTEREST",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        titleSize = 12.sp
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val compact = maxHeight < 220.dp
                            val gap = if (compact) 6.dp else 10.dp
                            val inputHeight = if (compact) 52.dp else 58.dp
                            val rowHeight = (maxHeight * 0.15f).coerceIn(30.dp, 42.dp)
                            val labelSize = if (compact) 10.sp else 11.sp
                            val focusManager = LocalFocusManager.current

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                // Session status line
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentSessionName ?: "(no session)",
                                        color = if (currentSessionName != null) Color.White else Color.Gray,
                                        fontSize = labelSize,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${if (hasHomebase) "H+" else ""}${currentFrameCount}F",
                                        color = Color(0xFF88FF88),
                                        fontSize = labelSize
                                    )
                                }
                                
                                // Frame list (if any frames recorded)
                                val draftFrames by viewModel.draftFramesList.collectAsState()
                                if (draftFrames.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 120.dp)
                                            .background(Color(0xFF1A1A1F), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(6.dp))
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(draftFrames.size) { index ->
                                                FrameListItem(
                                                    frame = draftFrames[index],
                                                    index = index,
                                                    onClick = { viewModel.editFrameTiming(index) }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Session name input
                                OutlinedTextField(
                                    value = sessionName,
                                    onValueChange = { sessionName = it },
                                    label = { Text("Session name") },
                                    placeholder = { Text(suggestedSessionName, color = Color.Gray) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(inputHeight),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = if (compact) 11.sp else 13.sp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (sessionName.isBlank()) {
                                            sessionName = suggestedSessionName
                                        }
                                        focusManager.clearFocus()
                                    })
                                )
                                
                                // Frame name input
                                OutlinedTextField(
                                    value = frameName,
                                    onValueChange = { frameName = it },
                                    label = { Text("Frame name") },
                                    placeholder = { Text(suggestedFrameName, color = Color.Gray) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(inputHeight),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = if (compact) 11.sp else 13.sp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (frameName.isBlank()) {
                                            frameName = suggestedFrameName
                                        }
                                        focusManager.clearFocus()
                                    })
                                )
                                
                                // Map selection dropdown
                                val availableMaps by viewModel.availableMaps.collectAsState()
                                val selectedMap by viewModel.selectedMap.collectAsState()
                                var mapExpanded by remember { mutableStateOf(false) }
                                
                                @OptIn(ExperimentalMaterial3Api::class)
                                ExposedDropdownMenuBox(
                                    expanded = mapExpanded,
                                    onExpandedChange = { mapExpanded = !mapExpanded && availableMaps.isNotEmpty() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = when {
                                            selectedMap != null -> selectedMap!!
                                            availableMaps.isEmpty() -> "No locations (check connection)"
                                            else -> "Select location"
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = availableMaps.isNotEmpty(),
                                        label = { Text("Location", fontSize = if (compact) 10.sp else 12.sp) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = if (availableMaps.isEmpty()) Color.Gray else Color(0xFFCCCCCC)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                            .height(inputHeight),
                                        textStyle = TextStyle(fontSize = if (compact) 11.sp else 13.sp),
                                        colors = TextFieldDefaults.colors(
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent
                                        )
                                    )
                                    
                                    ExposedDropdownMenu(
                                        expanded = mapExpanded,
                                        onDismissRequest = { mapExpanded = false },
                                        modifier = Modifier.heightIn(max = 200.dp)
                                    ) {
                                        availableMaps.forEach { mapName ->
                                            DropdownMenuItem(
                                                text = { Text(mapName, color = Color.White) },
                                                onClick = {
                                                    viewModel.selectMap(mapName)
                                                    mapExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                
                                // Display selected map name
                                if (selectedMap != null) {
                                    Text(
                                        text = "Selected location: $selectedMap",
                                        fontSize = if (compact) 10.sp else 11.sp,
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight),
                                        horizontalArrangement = Arrangement.spacedBy(gap),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SecondaryButton(
                                            text = "NEW",
                                            onClick = { 
                                                viewModel.checkAndStartLocalization()
                                                viewModel.newSession(sessionName)
                                                sessionName = ""  // Clear after creating session
                                            },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
                                        SecondaryButton(
                                            text = "HOMEBASE",
                                            onClick = { viewModel.setHomebase(frameName, sessionName) },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight),
                                        horizontalArrangement = Arrangement.spacedBy(gap),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SecondaryButton(
                                            text = "RECORD",
                                            onClick = { 
                                                viewModel.recordFrame(frameName, sessionName)
                                                // Clear if it was an auto-name so placeholder updates
                                                if (frameName.isBlank() || frameName.matches(Regex("frame_\\d+"))) {
                                                    frameName = ""
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
                                        SecondaryButton(
                                            text = "SAVE",
                                            onClick = { 
                                                viewModel.saveSessions()
                                                sessionName = ""
                                                frameName = ""
                                            },
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    StepColumn(
                        stepMode = stepMode,
                        onStepModeChange = { viewModel.setStepMode(it) },
                        modifier = Modifier
                            .width(ChipColumnWidth)
                            .fillMaxHeight()
                    )

                    PanelBox(
                        title = "CAMERA MOTION",
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val compact = maxHeight < 220.dp
                            val largePanel = maxWidth > 280.dp
                            val leftPadSize = (maxHeight / 4.2f).coerceIn(40.dp, 72.dp)
                            val posePadSize = (maxHeight / 6.5f).coerceIn(26.dp, 44.dp)
                            val gap = if (compact) 4.dp else 6.dp
                            val labelSize = if (compact) 12.sp else 14.sp
                            val poseLabelSize = labelSize
                            val poseArrowSize = if (compact) 20.sp else 24.sp
                            val poseGap = when {
                                largePanel -> if (compact) 6.dp else 10.dp
                                else -> if (compact) 4.dp else 7.dp
                            }
                            val poseLabelWidthFactor = if (largePanel) 0.6f else 0.4f
                            val arrowSize = if (compact) 18.sp else 22.sp

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                        .alpha(if (armEnabled) 1f else 0.4f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(
                                        if (armMode == ArmControlMode.JOINT_ANGLE) poseGap else gap
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModeChip(
                                            text = "EE-POSITION",
                                            selected = armMode == ArmControlMode.EE_POSITION,
                                            onClick = {
                                                android.widget.Toast.makeText(context, "In future release", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ModeChip(
                                            text = "JOINT ANGLE",
                                            selected = armMode == ArmControlMode.JOINT_ANGLE,
                                            onClick = { viewModel.setArmControlMode(ArmControlMode.JOINT_ANGLE) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (armMode == ArmControlMode.EE_POSITION) {
                                        Text("POSITION", color = Color.White, fontSize = labelSize)
                                        CameraPositionGrid(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            labelSize = labelSize,
                                            arrowSize = arrowSize,
                                            buttonSize = leftPadSize,
                                            enabled = armEnabled,
                                            upActive = armUpActive,
                                            downActive = armDownActive,
                                            leftActive = armLeftActive,
                                            forwardActive = armForwardActive,
                                            rightActive = armRightActive,
                                            backActive = armBackActive,
                                            onUp = { viewModel.nudgeCameraGoal(0, 0, 1) },
                                            onDown = { viewModel.nudgeCameraGoal(0, 0, -1) },
                                            onLeft = { viewModel.nudgeCameraGoal(0, 1, 0) },
                                            onForward = { viewModel.nudgeCameraGoal(1, 0, 0) },
                                            onRight = { viewModel.nudgeCameraGoal(0, -1, 0) },
                                            onBack = { viewModel.nudgeCameraGoal(-1, 0, 0) }
                                        )
                                    } else {
                                        Text("JOINT ANGLE", color = Color.White, fontSize = labelSize)
                                        PosePair(
                                            label = "4",
                                            size = posePadSize,
                                            fontSize = poseLabelSize,
                                            arrowSize = poseArrowSize,
                                            gap = poseGap,
                                            labelWidthFactor = poseLabelWidthFactor,
                                            enabled = armEnabled && armJointEnabled.contains(0),
                                            selected = jointControlActive && selectedJoint == 0,
                                            negativeActive = armJointEnabled.contains(0) && joint4NegActive,
                                            positiveActive = armJointEnabled.contains(0) && joint4PosActive,
                                            onNegative = { viewModel.nudgeArmJointNegative(0) },
                                            onPositive = { viewModel.nudgeArmJointPositive(0) },
                                            onNegativeHoldStart = { viewModel.startArmJointMove(0, -1) },
                                            onNegativeHoldEnd = { viewModel.stopArmJointMove() },
                                            onPositiveHoldStart = { viewModel.startArmJointMove(0, 1) },
                                            onPositiveHoldEnd = { viewModel.stopArmJointMove() }
                                        )
                                        PosePair(
                                            label = "5",
                                            size = posePadSize,
                                            fontSize = poseLabelSize,
                                            arrowSize = poseArrowSize,
                                            gap = poseGap,
                                            labelWidthFactor = poseLabelWidthFactor,
                                            enabled = armEnabled && armJointEnabled.contains(1),
                                            selected = jointControlActive && selectedJoint == 1,
                                            negativeActive = armJointEnabled.contains(1) && joint5NegActive,
                                            positiveActive = armJointEnabled.contains(1) && joint5PosActive,
                                            onNegative = { viewModel.nudgeArmJointNegative(1) },
                                            onPositive = { viewModel.nudgeArmJointPositive(1) },
                                            onNegativeHoldStart = { viewModel.startArmJointMove(1, -1) },
                                            onNegativeHoldEnd = { viewModel.stopArmJointMove() },
                                            onPositiveHoldStart = { viewModel.startArmJointMove(1, 1) },
                                            onPositiveHoldEnd = { viewModel.stopArmJointMove() }
                                        )
                                        PosePair(
                                            label = "6",
                                            size = posePadSize,
                                            fontSize = poseLabelSize,
                                            arrowSize = poseArrowSize,
                                            gap = poseGap,
                                            labelWidthFactor = poseLabelWidthFactor,
                                            enabled = armEnabled && armJointEnabled.contains(2),
                                            selected = jointControlActive && selectedJoint == 2,
                                            negativeActive = armJointEnabled.contains(2) && joint6NegActive,
                                            positiveActive = armJointEnabled.contains(2) && joint6PosActive,
                                            onNegative = { viewModel.nudgeArmJointNegative(2) },
                                            onPositive = { viewModel.nudgeArmJointPositive(2) },
                                            onNegativeHoldStart = { viewModel.startArmJointMove(2, -1) },
                                            onNegativeHoldEnd = { viewModel.stopArmJointMove() },
                                            onPositiveHoldStart = { viewModel.startArmJointMove(2, 1) },
                                            onPositiveHoldEnd = { viewModel.stopArmJointMove() }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                                    AngleInfoPanel(
                                        title = "ARM",
                                        labels = listOf("J4", "J5", "J6"),
                                        values = List(3) { armAngles.getOrNull(it) },
                                        selectedIndex = if (jointControlActive) selectedJoint else null,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .fillMaxHeight()
                                        .alpha(if (gimbalEnabled) 1f else 0.4f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(poseGap)
                                ) {
                                    Text("END-POSE", color = Color.White, fontSize = labelSize)
                                    PosePair(
                                        label = "P",
                                        size = posePadSize,
                                        fontSize = poseLabelSize,
                                        arrowSize = poseArrowSize,
                                        gap = poseGap,
                                        labelWidthFactor = poseLabelWidthFactor,
                                        enabled = gimbalEnabled,
                                        negativeActive = gimbalYawNegActive,
                                        positiveActive = gimbalYawPosActive,
                                        onNegative = { viewModel.nudgeGimbalNegative(2) },
                                        onPositive = { viewModel.nudgeGimbalPositive(2) },
                                        onNegativeHoldStart = { viewModel.startGimbalVelocityControl(2, -1.0) },
                                        onNegativeHoldEnd = { viewModel.stopGimbalVelocityControl(2) },
                                        onPositiveHoldStart = { viewModel.startGimbalVelocityControl(2, 1.0) },
                                        onPositiveHoldEnd = { viewModel.stopGimbalVelocityControl(2) },
                                        negativeArrow = "←",
                                        positiveArrow = "→"
                                    )
                                    PosePair(
                                        label = "T",
                                        size = posePadSize,
                                        fontSize = poseLabelSize,
                                        arrowSize = poseArrowSize,
                                        gap = poseGap,
                                        labelWidthFactor = poseLabelWidthFactor,
                                        enabled = gimbalEnabled,
                                        negativeActive = gimbalPitchNegActive,
                                        positiveActive = gimbalPitchPosActive,
                                        onNegative = { viewModel.nudgeGimbalNegative(1) },
                                        onPositive = { viewModel.nudgeGimbalPositive(1) },
                                        onNegativeHoldStart = { viewModel.startGimbalVelocityControl(1, -1.0) },
                                        onNegativeHoldEnd = { viewModel.stopGimbalVelocityControl(1) },
                                        onPositiveHoldStart = { viewModel.startGimbalVelocityControl(1, 1.0) },
                                        onPositiveHoldEnd = { viewModel.stopGimbalVelocityControl(1) },
                                        negativeArrow = "↓",
                                        positiveArrow = "↑"
                                    )
                                    PosePair(
                                        label = "R",
                                        size = posePadSize,
                                        fontSize = poseLabelSize,
                                        arrowSize = poseArrowSize,
                                        gap = poseGap,
                                        labelWidthFactor = poseLabelWidthFactor,
                                        enabled = gimbalEnabled,
                                        negativeActive = gimbalRollNegActive,
                                        positiveActive = gimbalRollPosActive,
                                        onNegative = { viewModel.nudgeGimbalNegative(0) },
                                        onPositive = { viewModel.nudgeGimbalPositive(0) },
                                        onNegativeHoldStart = { viewModel.startGimbalVelocityControl(0, -1.0) },
                                        onNegativeHoldEnd = { viewModel.stopGimbalVelocityControl(0) },
                                        onPositiveHoldStart = { viewModel.startGimbalVelocityControl(0, 1.0) },
                                        onPositiveHoldEnd = { viewModel.stopGimbalVelocityControl(0) }
                                    )
                                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                                    AngleInfoPanel(
                                        title = "END-POSE",
                                        labels = listOf("P", "T", "R"),
                                        values = listOf(
                                            gimbalAngles.getOrNull(2),
                                            gimbalAngles.getOrNull(1),
                                            gimbalAngles.getOrNull(0)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                                }  // End of Row (WORLD_VIEW panels)
                            }  // End of WORLD_VIEW branch
                            ControlViewMode.CAMERA_VIEW -> {
                                // Camera-centric layout: EE Position + Gimbal + FOI
                                CameraViewPanels(
                                    viewModel = viewModel,
                                    safety = safety,
                                    gimbalAngles = gimbalAngles,
                                    sessionName = sessionName,
                                    frameName = frameName,
                                    onSessionNameChange = { sessionName = it },
                                    onFrameNameChange = { frameName = it },
                                    currentSessionName = currentSessionName,
                                    currentFrameCount = currentFrameCount,
                                    hasHomebase = hasHomebase,
                                    suggestedSessionName = suggestedSessionName,
                                    suggestedFrameName = suggestedFrameName
                                )
                            }  // End of CAMERA_VIEW branch
                        }  // End of when (controlViewMode)
                    }  // End of if (panelsVisible)
                }  // End of Box (weighted panel container)
            }  // End of Column
        }  // End of BoxWithConstraints
        
        // Panel Toggle Button (positioned at bottom-center when panels hidden)
        PanelToggleButton(
            panelsVisible = panelsVisible,
            onClick = { panelsVisible = !panelsVisible },
            modifier = Modifier
                .align(if (panelsVisible) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(
                    end = if (panelsVisible) TabRailWidth + 16.dp else 0.dp,
                    bottom = 16.dp
                )
        )
        
        // Frame Timing Dialog
        val pendingTiming by viewModel.pendingFrameTiming.collectAsState()
        val draftFrames by viewModel.draftFramesList.collectAsState()
        pendingTiming?.let { pending ->
            // Extract current timing from frame (if editing existing)
            val currentFrame = draftFrames.getOrNull(pending.frameIndex)
            val currentTiming = currentFrame?.let { frame ->
                FrameTiming(
                    dwellS = frame["dwell_s"]?.jsonPrimitive?.doubleOrNull,
                    transitionS = frame["transition_s"]?.jsonPrimitive?.doubleOrNull,
                    ease = frame["ease"]?.jsonPrimitive?.contentOrNull
                )
            }
            FrameTimingDialog(
                frameName = pending.frameName,
                currentTiming = currentTiming,
                onConfirm = { timing ->
                    viewModel.setFrameTiming(pending.frameIndex, timing)
                },
                onDismiss = {
                    viewModel.cancelFrameTiming()
                }
            )
        }
    }
}

@Composable
fun FrameListItem(
    frame: JsonObject,
    index: Int,
    onClick: () -> Unit
) {
    val name = frame["name"]?.jsonPrimitive?.contentOrNull ?: "frame_$index"
    val dwellS = frame["dwell_s"]?.jsonPrimitive?.doubleOrNull
    val transitionS = frame["transition_s"]?.jsonPrimitive?.doubleOrNull
    val ease = frame["ease"]?.jsonPrimitive?.contentOrNull
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2F), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frame name
        Text(
            text = name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        
        // Timing info
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dwell
            if (dwellS != null && dwellS > 0.0) {
                Text(
                    text = "⏸ ${dwellS}s",
                    color = Color(0xFFFFAA44),
                    fontSize = 9.sp
                )
            }
            
            // Transition
            if (transitionS != null) {
                Text(
                    text = "→ ${transitionS}s",
                    color = Color(0xFF44AAFF),
                    fontSize = 9.sp
                )
            } else {
                Text(
                    text = "→ auto",
                    color = Color(0xFF888888),
                    fontSize = 9.sp
                )
            }
            
            // Ease
            val easeIcon = when (ease) {
                "ease_in" -> "↗"
                "ease_out" -> "↘"
                "ease_in_out" -> "⟳"
                else -> "→"
            }
            Text(
                text = easeIcon,
                color = Color(0xFF88FF88),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun RunOverlay(viewModel: RecomoControlViewModel) {
    val safety by viewModel.safetyStatus.collectAsState()
    val focus by viewModel.controlFocus.collectAsState()
    val runState by viewModel.runState.collectAsState()
    val library by viewModel.librarySummary.collectAsState()
    val preview by viewModel.trajectoryPreview.collectAsState()
    val opMode by viewModel.operationMode.collectAsState()
    val availableMaps by viewModel.availableMaps.collectAsState()
    val selectedMap by viewModel.selectedMap.collectAsState()
    val mapAssets by viewModel.mapAssets.collectAsState()
    val selectedMapAsset by viewModel.selectedMapAsset.collectAsState()
    val poiNamesBySession by viewModel.poiNamesBySession.collectAsState()
    val poiNavActive by viewModel.poiNavActive.collectAsState()
    val fixedPositionState by viewModel.fixedPositionState.collectAsState()
    val simpleTrackState by viewModel.simpleTrackState.collectAsState()
    var sessionName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var pickerExpanded by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    var panelsVisible by remember { mutableStateOf(true) }

    // Fetch map list when entering Motion Replica mode
    LaunchedEffect(opMode) {
        if (opMode == OperationMode.MOTION_REPLICA) {
            viewModel.fetchMapList()
            viewModel.requestFixedPositionTrajectories()
        } else if (opMode == OperationMode.FIXED_POSITION) {
            viewModel.requestFixedPositionTrajectories()
        }
    }

    LaunchedEffect(runState.loadedSessionId, runState.loadedSessionName) {
        val label = runState.loadedSessionName ?: runState.loadedSessionId
        if (!label.isNullOrBlank()) {
            sessionName = label
            searchQuery = ""
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        val panelHeight = if (panelsVisible) {
            if (maxHeight > 600.dp) maxHeight * 0.40f else maxHeight * 0.52f
        } else {
            0.dp
        }
        val buttonHeight = 32.dp
        val sessions = library.foiSessions.sortedByDescending { it.sessionId }
        val query = searchQuery.trim()
        val filteredSessions = if (query.isBlank()) {
            sessions
        } else {
            sessions.filter {
                it.sessionId.contains(query, ignoreCase = true) ||
                    it.sessionName.contains(query, ignoreCase = true)
            }
        }

        // Mode selector bar — always visible at top center
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, end = TabRailWidth)
                .zIndex(2f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunModeSelector(
                currentMode = opMode,
                onModeSelected = { viewModel.setOperationMode(it) }
            )
            // Preview button — opens full-screen preview overlay (Motion Replica mode)
            if (opMode == OperationMode.MOTION_REPLICA) {
                Button(
                    onClick = { previewOpen = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF333333)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        "PREVIEW",
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }

        if (panelsVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(panelHeight)
                    .padding(8.dp)
                    .padding(end = TabRailWidth + 8.dp)
                    .zIndex(2f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Safety panel — always visible
                RunSafetyPanel(
                    safety = safety,
                    focus = focus,
                    buttonHeight = buttonHeight,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                // Mode-specific panels
                when (opMode) {
                    OperationMode.MANUAL -> {
                        ManualModePanel(
                            viewModel = viewModel,
                            buttonHeight = buttonHeight,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )
                    }
                    OperationMode.MOTION_REPLICA -> {
                        MotionReplicaPanel(
                            viewModel = viewModel,
                            runState = runState,
                            runControlMode = runState.runControlMode,
                            fixedState = fixedPositionState,
                            simpleTrackState = simpleTrackState,
                            livePncTrajectories = fixedPositionState.trajectories,
                            preview = preview,
                            availableMaps = availableMaps,
                            selectedMap = selectedMap,
                            mapAssets = mapAssets,
                            selectedMapAsset = selectedMapAsset,
                            poiSessions = library.poiSessions,
                            poiNamesBySession = poiNamesBySession,
                            poiNavActive = poiNavActive,
                            sessionName = sessionName,
                            searchQuery = searchQuery,
                            pickerExpanded = pickerExpanded,
                            filteredSessions = filteredSessions,
                            buttonHeight = buttonHeight,
                            onSessionNameChange = { sessionName = it; searchQuery = it; if (!pickerExpanded) pickerExpanded = true },
                            onPickerToggle = { pickerExpanded = !pickerExpanded },
                            onPickerDismiss = { pickerExpanded = false },
                            onSessionSelect = { session ->
                                pickerExpanded = false
                                sessionName = session.sessionName.ifBlank { session.sessionId }
                                searchQuery = ""
                                viewModel.loadSession(session.sessionId)
                            },
                            onRunControlModeChange = { mode -> viewModel.setRunControlMode(mode) },
                            onPreviewOpen = { previewOpen = true },
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )
                    }
                    OperationMode.SUBJECT_FOLLOWING -> {
                        SubjectFollowingPanel(
                            viewModel = viewModel,
                            buttonHeight = buttonHeight,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )
                    }
                    OperationMode.FIXED_POSITION -> {
                        FixedPositionPanel(
                            viewModel = viewModel,
                            fixedState = fixedPositionState,
                            buttonHeight = buttonHeight,
                            modifier = Modifier.weight(2f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        if (previewOpen) {
            TrajectoryPreviewOverlay(
                viewModel = viewModel,
                onClose = { previewOpen = false }
            )
        }

        PanelToggleButton(
            panelsVisible = panelsVisible,
            onClick = { panelsVisible = !panelsVisible },
            modifier = Modifier
                .zIndex(3f)
                .align(if (panelsVisible) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(
                    end = if (panelsVisible) TabRailWidth + 16.dp else 0.dp,
                    bottom = 16.dp
                )
        )
    }
}

@Composable
fun RunModeSelector(
    currentMode: OperationMode,
    onModeSelected: (OperationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        OperationMode.MANUAL to "MANUAL",
        OperationMode.MOTION_REPLICA to "MOTION REPLICA",
        OperationMode.SUBJECT_FOLLOWING to "SUBJECT FOLLOW",
        OperationMode.FIXED_POSITION to "FIXED POSITION"
    )
    Row(
        modifier = modifier
            .background(Color(0xCC1A1A1A), RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { (mode, label) ->
            val selected = mode == currentMode
            Button(
                onClick = { onModeSelected(mode) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Color(0xFF2196F3) else Color(0xFF333333),
                    contentColor = if (selected) Color.White else Color(0xFFAAAAAA)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun RunSafetyPanel(
    safety: SafetyStatus,
    focus: ControlFocus,
    buttonHeight: Dp,
    viewModel: RecomoControlViewModel,
    modifier: Modifier = Modifier
) {
    val safetyPrimaryButtonHeight = buttonHeight + 10.dp
    PanelBox(title = "SAFETY", modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DangerButton(
                    text = "E-STOP",
                    modifier = Modifier.height(safetyPrimaryButtonHeight).weight(1f)
                ) { viewModel.sendEstop() }
                if (safety.estop) {
                    SecondaryButton(
                        text = if (safety.estopCooldownMs > 0)
                            "CLEAR (${String.format("%.1f", safety.estopCooldownMs / 1000.0)}s)"
                        else "CLEAR",
                        enabled = safety.canClearEstop,
                        modifier = Modifier.height(buttonHeight).weight(1f)
                    ) { viewModel.clearEstop() }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            SecondaryButton(
                text = if (safety.freezeAll) "UNFREEZE" else "FREEZE",
                modifier = Modifier.height(safetyPrimaryButtonHeight).fillMaxWidth()
            ) {
                if (safety.freezeAll) viewModel.unfreezeAll() else viewModel.freezeAll()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CAMERA HOMING",
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SecondaryButton(
                    text = "ARM HOME",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = focus == ControlFocus.ARM
                ) { viewModel.sendArmHomingPose() }
                SecondaryButton(
                    text = "GIMBAL HOME",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = focus == ControlFocus.GIMBAL
                ) { viewModel.sendGimbalHomingPose() }
            }
        }
    }
}

@Composable
fun ManualModePanel(
    viewModel: RecomoControlViewModel,
    buttonHeight: Dp,
    modifier: Modifier = Modifier
) {
    PanelBox(title = "MANUAL CONTROL", modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Use TEACH tab controls for manual robot operation.\nSwitch to TEACH tab for full control interface.",
                color = Color(0xFFBBBBBB),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Manual mode — all teleop commands active",
                color = Color(0xFF66BB6A),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun MotionReplicaPanel(
    viewModel: RecomoControlViewModel,
    runState: com.recomo.remotecontrol.ui.RunState,
    runControlMode: RunControlMode,
    fixedState: com.recomo.remotecontrol.ui.FixedPositionState,
    simpleTrackState: com.recomo.remotecontrol.ui.SimpleTrackState,
    livePncTrajectories: List<String>,
    preview: com.recomo.remotecontrol.preview.TrajectoryPreview?,
    availableMaps: List<String>,
    selectedMap: String?,
    mapAssets: List<String>,
    selectedMapAsset: String?,
    poiSessions: List<com.recomo.remotecontrol.ui.SessionSummary>,
    poiNamesBySession: Map<String, List<String>>,
    poiNavActive: Boolean,
    sessionName: String,
    searchQuery: String,
    pickerExpanded: Boolean,
    filteredSessions: List<com.recomo.remotecontrol.ui.SessionSummary>,
    buttonHeight: Dp,
    onSessionNameChange: (String) -> Unit,
    onPickerToggle: () -> Unit,
    onPickerDismiss: () -> Unit,
    onSessionSelect: (com.recomo.remotecontrol.ui.SessionSummary) -> Unit,
    onRunControlModeChange: (RunControlMode) -> Unit,
    onPreviewOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mapExpanded by remember { mutableStateOf(false) }
    var mapAssetExpanded by remember { mutableStateOf(false) }
    var poiExpanded by remember { mutableStateOf(false) }
    var poiIndexExpanded by remember { mutableStateOf(false) }
    var selectedPoi by remember { mutableStateOf<com.recomo.remotecontrol.ui.SessionSummary?>(null) }
    var selectedPoiIndex by remember { mutableStateOf(0) }
    var navSpeed by remember { mutableStateOf(0.8f) }
    var liveTrajectoryExpanded by remember { mutableStateOf(false) }
    var simpleTrackTrajExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedMap, poiSessions, poiNavActive) {
        if (poiNavActive) return@LaunchedEffect
        val mapSessionId = selectedMap?.let { "map_poi::$it" }
        if (!mapSessionId.isNullOrBlank()) {
            val mapSession = poiSessions.firstOrNull { it.sessionId == mapSessionId }
            if (mapSession != null && selectedPoi?.sessionId != mapSession.sessionId) {
                selectedPoi = mapSession
            }
        }
        val current = selectedPoi
        if (current != null && poiSessions.none { it.sessionId == current.sessionId }) {
            selectedPoi = null
        }
    }
    LaunchedEffect(selectedPoi?.sessionId) {
        selectedPoiIndex = 0
        selectedPoi?.sessionId?.let { viewModel.requestPoiSessionDetail(it) }
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Step 1 & 2: Map + POI selection
        PanelBox(title = "MAP & POI", modifier = Modifier.weight(1f).fillMaxHeight()) {
            val mapPoiScrollState = rememberScrollState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(mapPoiScrollState)
            ) {
                // Map dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = selectedMap ?: "Select Location...",
                        modifier = Modifier.height(buttonHeight).fillMaxWidth()
                    ) { mapExpanded = true }
                    DropdownMenu(
                        expanded = mapExpanded,
                        onDismissRequest = { mapExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp, max = 360.dp).heightIn(max = 220.dp)
                    ) {
                        if (availableMaps.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No maps found") },
                                onClick = { mapExpanded = false },
                                enabled = false
                            )
                        } else {
                            val scrollState = rememberScrollState()
                            Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(scrollState)) {
                                availableMaps.forEach { map ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                map,
                                                fontWeight = if (map == selectedMap) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            mapExpanded = false
                                            viewModel.selectMap(map)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SecondaryButton(
                    text = "REFRESH LOCATIONS",
                    modifier = Modifier.height(buttonHeight).fillMaxWidth()
                ) { viewModel.fetchMapList() }
                Spacer(modifier = Modifier.height(6.dp))

                // Map asset dropdown (within selected location)
                Box(modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = if (selectedMap == null) {
                            "Select location first"
                        } else {
                            selectedMapAsset ?: if (mapAssets.isEmpty()) "No map assets" else "Select map asset..."
                        },
                        modifier = Modifier.height(buttonHeight).fillMaxWidth(),
                        enabled = selectedMap != null && mapAssets.isNotEmpty()
                    ) { mapAssetExpanded = true }
                    DropdownMenu(
                        expanded = mapAssetExpanded,
                        onDismissRequest = { mapAssetExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp, max = 360.dp).heightIn(max = 220.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(scrollState)) {
                            mapAssets.forEach { asset ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            asset,
                                            fontWeight = if (asset == selectedMapAsset) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        mapAssetExpanded = false
                                        viewModel.selectMapAsset(asset)
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // POI session picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = selectedPoi?.let {
                            val name = it.sessionName.ifBlank { it.sessionId }
                            "$name (${it.count} POIs)"
                        } ?: if (poiSessions.isEmpty()) "No POI sessions" else "Select POI session...",
                        modifier = Modifier.height(buttonHeight).fillMaxWidth(),
                        enabled = poiSessions.isNotEmpty()
                    ) { poiExpanded = true }
                    DropdownMenu(
                        expanded = poiExpanded,
                        onDismissRequest = { poiExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp, max = 360.dp).heightIn(max = 220.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(scrollState)) {
                            poiSessions.forEach { poi ->
                                val label = poi.sessionName.ifBlank { poi.sessionId }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "$label (${poi.count} POIs)",
                                            fontWeight = if (poi == selectedPoi) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        poiExpanded = false
                                        selectedPoi = poi
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                // POI index picker (within selected POI session)
                selectedPoi?.let { poi ->
                    val poiCount = poi.count.coerceAtLeast(0)
                    if (poiCount > 0) {
                        val poiNames = poiNamesBySession[poi.sessionId].orEmpty()
                        val currentPoiLabel = poiNames.getOrNull(selectedPoiIndex)?.takeIf { it.isNotBlank() }
                            ?: "POI ${selectedPoiIndex + 1}"
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SecondaryButton(
                                text = "$currentPoiLabel (${selectedPoiIndex + 1}/$poiCount)",
                                modifier = Modifier.height(buttonHeight).fillMaxWidth(),
                                enabled = true
                            ) { poiIndexExpanded = true }
                            DropdownMenu(
                                expanded = poiIndexExpanded,
                                onDismissRequest = { poiIndexExpanded = false },
                                modifier = Modifier.widthIn(min = 200.dp, max = 360.dp).heightIn(max = 220.dp)
                            ) {
                                val scrollState = rememberScrollState()
                                Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(scrollState)) {
                                    for (idx in 0 until poiCount) {
                                        val poiLabel = poiNames.getOrNull(idx)?.takeIf { it.isNotBlank() }
                                            ?: "POI ${idx + 1}"
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    poiLabel,
                                                    fontWeight = if (idx == selectedPoiIndex) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                poiIndexExpanded = false
                                                selectedPoiIndex = idx
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Speed slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Speed", color = Color(0xFFAAAAAA), fontSize = 11.sp, modifier = Modifier.width(44.dp))
                    Slider(
                        value = navSpeed,
                        onValueChange = {
                            navSpeed = it
                            if (poiNavActive || runState.pncFsmState == 1) {
                                viewModel.updatePoiNavSpeed(it.toDouble())
                            }
                        },
                        valueRange = 0.0f..1.5f,
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF2196F3)
                        )
                    )
                    Text("%.1f".format(navSpeed), color = Color(0xFFDDDDDD), fontSize = 11.sp, modifier = Modifier.width(36.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))

                // PnC navigation status
                // Reflect live FSM while still distinguishing local GO state.
                val fsmLabel = when {
                    runState.pncFsmState == 4 -> "✅ Arrived"
                    poiNavActive && runState.pncFsmState == 1 -> "🟡 Navigating"
                    runState.pncFsmState == 1 -> "🟡 Running"
                    runState.pncFsmState == 2 -> "⏸ FSM Exit"
                    runState.pncFsmState == 3 -> "🎬 FSM Demo"
                    poiNavActive -> "🟡 Command sent"
                    else -> "⚪ Idle"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(fsmLabel, color = Color(0xFFDDDDDD), fontSize = 11.sp)
                    Text("PnC FSM=${runState.pncFsmState}", color = Color(0xFF888888), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))

                // GO / PAUSE / STOP buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton(
                        text = "GO",
                        modifier = Modifier.height(buttonHeight).weight(1f),
                        enabled = (selectedPoi?.count ?: 0) > 0
                    ) {
                        selectedPoi?.let {
                            viewModel.navigateToPoi(it.sessionId, navSpeed.toDouble(), selectedPoiIndex)
                        }
                    }
                    SecondaryButton(
                        text = "PAUSE",
                        modifier = Modifier.height(buttonHeight).weight(1f),
                        enabled = poiNavActive || runState.pncFsmState == 1
                    ) { viewModel.pausePoiNav() }
                    DangerButton(
                        text = "STOP",
                        modifier = Modifier.height(buttonHeight).weight(1f)
                    ) { viewModel.stopPoiNav() }
                }
            }
        }

        // Step 3 & 4: Trajectory execution
        PanelBox(
            title = "RUN CONTROL",
            modifier = Modifier.weight(1f).fillMaxHeight(),
            titleAction = {
                IconButton(
                    onClick = { viewModel.triggerCameraPhoto() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Photo",
                        tint = Color(0xFFDDDDDD),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isLivePncMode = runControlMode == RunControlMode.LIVE_PNC
                val canSwitchRunControlMode = if (isLivePncMode) {
                    !fixedState.motionActive
                } else {
                    runState.status != RunStatus.RUNNING
                }
                Text("TRACK MODE", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onRunControlModeChange(RunControlMode.SIMPLE_TRACK) },
                        enabled = canSwitchRunControlMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runControlMode == RunControlMode.SIMPLE_TRACK) Color(0xFF2A4A2A) else Color(0xFF3A3A3A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(buttonHeight).weight(1f)
                    ) {
                        Text("simpleTrack", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onRunControlModeChange(RunControlMode.LIVE_PNC) },
                        enabled = canSwitchRunControlMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runControlMode == RunControlMode.LIVE_PNC) Color(0xFF2A4A2A) else Color(0xFF3A3A3A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(buttonHeight).weight(1f)
                    ) {
                        Text("LivePnC", fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (isLivePncMode) {
                    val selectedTrajectory = fixedState.selectedTrajectory
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = selectedTrajectory
                                ?: if (livePncTrajectories.isEmpty()) "No trajectories found" else "Select trajectory...",
                            modifier = Modifier.height(buttonHeight).fillMaxWidth(),
                            enabled = livePncTrajectories.isNotEmpty()
                        ) { liveTrajectoryExpanded = true }
                        DropdownMenu(
                            expanded = liveTrajectoryExpanded,
                            onDismissRequest = { liveTrajectoryExpanded = false },
                            modifier = Modifier.widthIn(min = 260.dp, max = 420.dp).heightIn(max = 260.dp)
                        ) {
                            if (livePncTrajectories.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No trajectory found") },
                                    onClick = { liveTrajectoryExpanded = false },
                                    enabled = false
                                )
                            } else {
                                val menuScrollState = rememberScrollState()
                                Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(menuScrollState)) {
                                    livePncTrajectories.forEach { trajectory ->
                                        DropdownMenuItem(
                                            text = { Text(trajectory) },
                                            onClick = {
                                                liveTrajectoryExpanded = false
                                                viewModel.selectFixedPositionTrajectory(trajectory)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    SecondaryButton(
                        text = "REFRESH TRAJ",
                        modifier = Modifier.height(buttonHeight).fillMaxWidth()
                    ) { viewModel.requestFixedPositionTrajectories() }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondaryButton(
                            text = "HOMING",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = selectedTrajectory != null && (!fixedState.motionActive || fixedState.paused)
                        ) { viewModel.homeRun(selectedTrajectory.orEmpty()) }
                        SecondaryButton(
                            text = "INITING",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = selectedTrajectory != null && (!fixedState.motionActive || fixedState.paused)
                        ) { viewModel.fixedPositionIniting() }
                        SecondaryButton(
                            text = "START",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = selectedTrajectory != null && (!fixedState.motionActive || fixedState.paused)
                        ) { viewModel.fixedPositionStart() }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondaryButton(
                            text = "PAUSE",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = runState.status == RunStatus.HOMING || (fixedState.motionActive && !fixedState.paused)
                        ) {
                            if (runState.status == RunStatus.HOMING) {
                                viewModel.pauseRun()
                            } else {
                                viewModel.fixedPositionPause()
                            }
                        }
                        DangerButton(
                            text = "STOP",
                            modifier = Modifier.height(buttonHeight).weight(1f)
                        ) {
                            if (runState.status == RunStatus.HOMING) {
                                viewModel.stopRun()
                            } else if (fixedState.motionActive || fixedState.paused) {
                                viewModel.fixedPositionStop()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val liveLabel = when {
                        runState.status == RunStatus.HOMING -> "🟡 Homing"
                        else -> when (fixedState.status.lowercase()) {
                        "homing" -> "🟡 Homing"
                        "initing" -> "🟡 Initing"
                        "ready" -> "🟢 Ready (Stage1)"
                        "running" -> "🟢 Running"
                        "paused" -> "⏸ Paused"
                        "end" -> "✅ End"
                        "completed" -> "✅ Completed"
                        "stopped" -> "🛑 Stopped"
                        "error" -> "❌ Error"
                        else -> "⚪ Idle"
                        }
                    }
                    Text(liveLabel, color = Color(0xFFDDDDDD), fontSize = 11.sp)
                    Text(
                        "Executor=${fixedState.executorState} · Stage2=${fixedState.stage2ExecutionStatus}",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                    Text(
                        "Artifacts: video ${if (fixedState.videoReady) "✅" else "…"} · thumbnail ${if (fixedState.thumbnailReady) "✅" else "…"}",
                        color = Color(0xFFBBBBBB),
                        fontSize = 10.sp
                    )
                    Text(
                        if (fixedState.uploadEligible) "Upload: READY" else "Upload: NOT READY",
                        color = if (fixedState.uploadEligible) Color(0xFF66BB6A) else Color(0xFFEF5350),
                        fontSize = 10.sp
                    )
                    fixedState.message?.takeIf { it.isNotBlank() }?.let { msg ->
                        Text(msg, color = Color(0xFFAAAAAA), fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                    Text("Source: fixed-position pipeline", color = Color(0xFF888888), fontSize = 10.sp)
                } else {
                    // SimpleTrack mode — trajectory-based, same staged flow as LivePnC
                    val stSelectedTrajectory = simpleTrackState.selectedTrajectory
                    val stTrajectories = simpleTrackState.trajectories
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = stSelectedTrajectory
                                ?: if (stTrajectories.isEmpty()) "No trajectories found" else "Select trajectory...",
                            modifier = Modifier.height(buttonHeight).fillMaxWidth(),
                            enabled = stTrajectories.isNotEmpty()
                        ) { simpleTrackTrajExpanded = true }
                        DropdownMenu(
                            expanded = simpleTrackTrajExpanded,
                            onDismissRequest = { simpleTrackTrajExpanded = false },
                            modifier = Modifier.widthIn(min = 260.dp, max = 420.dp).heightIn(max = 260.dp)
                        ) {
                            if (stTrajectories.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No trajectory found") },
                                    onClick = { simpleTrackTrajExpanded = false },
                                    enabled = false
                                )
                            } else {
                                val menuScrollState = rememberScrollState()
                                Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(menuScrollState)) {
                                    stTrajectories.forEach { trajectory ->
                                        DropdownMenuItem(
                                            text = { Text(trajectory) },
                                            onClick = {
                                                simpleTrackTrajExpanded = false
                                                viewModel.selectSimpleTrackTrajectory(trajectory)
                                                viewModel.loadSession(trajectory)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    SecondaryButton(
                        text = "REFRESH TRAJ",
                        modifier = Modifier.height(buttonHeight).fillMaxWidth()
                    ) { viewModel.refreshSimpleTrackTrajectories() }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondaryButton(
                            text = "HOMING",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = stSelectedTrajectory != null && runState.status != RunStatus.RUNNING
                        ) { viewModel.homeRun() }
                        SecondaryButton(
                            text = "INITING",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = stSelectedTrajectory != null && runState.status != RunStatus.RUNNING
                        ) { viewModel.simpleTrackIniting() }
                        SecondaryButton(
                            text = "START",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = stSelectedTrajectory != null && runState.status != RunStatus.RUNNING
                        ) { viewModel.startRun(deadman = true) }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondaryButton(
                            text = if (runState.status == RunStatus.PAUSED) "RESUME" else "PAUSE",
                            modifier = Modifier.height(buttonHeight).weight(1f),
                            enabled = runState.status == RunStatus.RUNNING || runState.status == RunStatus.PAUSED
                        ) { if (runState.status == RunStatus.PAUSED) viewModel.resumeRun(deadman = true) else viewModel.pauseRun() }
                        DangerButton(
                            text = "STOP",
                            modifier = Modifier.height(buttonHeight).weight(1f)
                        ) { viewModel.stopRun() }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val stStatusLabel = when (simpleTrackState.status.lowercase()) {
                        "homing" -> "Homing (arm ramp)"
                        "loaded" -> "Loaded"
                        "running" -> "Running"
                        "paused" -> "Paused"
                        "completed" -> "Completed"
                        "error" -> "Error"
                        else -> "Idle"
                    }
                    Text("Mode: simpleTrack | $stStatusLabel", color = Color(0xFFDDDDDD), fontSize = 11.sp)
                    Text("Source: keypoint trajectories (.txt)", color = Color(0xFF888888), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun SubjectFollowingPanel(
    viewModel: RecomoControlViewModel,
    buttonHeight: Dp,
    modifier: Modifier = Modifier
) {
    var followActive by remember { mutableStateOf(false) }

    PanelBox(
        title = "SUBJECT FOLLOWING",
        modifier = modifier,
        titleAction = {
            IconButton(
                onClick = { viewModel.triggerCameraPhoto() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Photo",
                    tint = Color(0xFFDDDDDD),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "1. Draw a bounding box on screen to select target\n2. Tap START FOLLOW to begin tracking",
                color = Color(0xFFBBBBBB),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SecondaryButton(
                    text = if (followActive) "FOLLOWING..." else "START FOLLOW",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = !followActive
                ) {
                    followActive = true
                    viewModel.sendFollowCmd("start")
                }
                DangerButton(
                    text = "STOP FOLLOW",
                    modifier = Modifier.height(buttonHeight).weight(1f)
                ) {
                    followActive = false
                    viewModel.sendFollowCmd("stop")
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (followActive) "🟢 Following active" else "⚪ Idle — draw target to begin",
                color = if (followActive) Color(0xFF66BB6A) else Color(0xFF888888),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun FixedPositionPanel(
    viewModel: RecomoControlViewModel,
    fixedState: com.recomo.remotecontrol.ui.FixedPositionState,
    buttonHeight: Dp,
    modifier: Modifier = Modifier
) {
    var trajExpanded by remember { mutableStateOf(false) }
    val selectedTrajectory = fixedState.selectedTrajectory
    val statusLabel = when (fixedState.status.lowercase()) {
        "homing" -> "🟡 Homing"
        "initing" -> "🟡 Initing"
        "ready" -> "🟢 Ready (Stage1 Done)"
        "running" -> "🟢 Running"
        "paused" -> "⏸ Paused"
        "end" -> "✅ End"
        "completed" -> "✅ Completed"
        "stopped" -> "🛑 Stopped"
        "error" -> "❌ Error"
        else -> "⚪ Idle"
    }

    PanelBox(
        title = "FIXED POSITION",
        modifier = modifier,
        titleAction = {
            IconButton(
                onClick = { viewModel.triggerCameraPhoto() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Photo",
                    tint = Color(0xFFDDDDDD),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Trajectory", color = Color(0xFFAAAAAA), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = selectedTrajectory ?: "Select trajectory...",
                    modifier = Modifier.height(buttonHeight).fillMaxWidth()
                ) { trajExpanded = true }
                DropdownMenu(
                    expanded = trajExpanded,
                    onDismissRequest = { trajExpanded = false },
                    modifier = Modifier.widthIn(min = 200.dp, max = 360.dp).heightIn(max = 220.dp)
                ) {
                    if (fixedState.trajectories.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No trajectory found") },
                            onClick = { trajExpanded = false },
                            enabled = false
                        )
                    } else {
                        fixedState.trajectories.forEach { traj ->
                            DropdownMenuItem(
                                text = { Text(traj) },
                                onClick = {
                                    trajExpanded = false
                                    viewModel.selectFixedPositionTrajectory(traj)
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SecondaryButton(
                    text = "HOMING",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = !fixedState.motionActive || fixedState.paused
                ) { viewModel.fixedPositionHoming() }
                SecondaryButton(
                    text = "INITING",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = selectedTrajectory != null && (!fixedState.motionActive || fixedState.paused)
                ) { viewModel.fixedPositionIniting() }
                SecondaryButton(
                    text = "START",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = selectedTrajectory != null && (!fixedState.motionActive || fixedState.paused)
                ) { viewModel.fixedPositionStart() }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SecondaryButton(
                    text = "PAUSE",
                    modifier = Modifier.height(buttonHeight).weight(1f),
                    enabled = fixedState.motionActive && !fixedState.paused
                ) { viewModel.fixedPositionPause() }
                DangerButton(
                    text = "STOP",
                    modifier = Modifier.height(buttonHeight).weight(1f)
                ) {
                    if (fixedState.motionActive || fixedState.paused) {
                        viewModel.fixedPositionStop()
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                statusLabel,
                color = when {
                    fixedState.status.equals("end", ignoreCase = true) -> Color(0xFF66BB6A)
                    fixedState.status.equals("completed", ignoreCase = true) -> Color(0xFF66BB6A)
                    fixedState.status.equals("error", ignoreCase = true) -> Color(0xFFEF5350)
                    fixedState.motionActive -> Color(0xFFFFD54F)
                    else -> Color(0xFF888888)
                },
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Executor: ${fixedState.executorState} · Stage2: ${fixedState.stage2ExecutionStatus}",
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Artifacts: video ${if (fixedState.videoReady) "✅" else "…" } · thumbnail ${if (fixedState.thumbnailReady) "✅" else "…" }",
                color = Color(0xFFBBBBBB),
                fontSize = 10.sp
            )
            Text(
                if (fixedState.uploadEligible) "Upload: READY" else "Upload: NOT READY",
                color = if (fixedState.uploadEligible) Color(0xFF66BB6A) else Color(0xFFEF5350),
                fontSize = 10.sp
            )
            fixedState.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    msg,
                    color = Color(0xFFAAAAAA),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Composable
fun LibraryOverlay(viewModel: RecomoControlViewModel) {
    val library by viewModel.librarySummary.collectAsState()
    val sessionDetail by viewModel.sessionDetail.collectAsState()
    var expandedSessionId by remember { mutableStateOf<String?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = TabRailWidth + 8.dp, start = 16.dp, top = 16.dp, bottom = 16.dp)
    ) {
        PanelBox(
            title = "FRAME SESSIONS",
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.85f)
                .align(Alignment.Center)
        ) {
            if (library.foiSessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No sessions recorded yet",
                        color = Color(0xFF777777),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(library.foiSessions, key = { it.sessionId }) { session ->
                        val isExpanded = expandedSessionId == session.sessionId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isExpanded) Color(0x33FFFFFF) else Color(0x22FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isExpanded) {
                                            expandedSessionId = null
                                        } else {
                                            expandedSessionId = session.sessionId
                                            viewModel.requestLibraryGet(LibraryTarget.FOI_SESSION, session.sessionId)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.sessionName.ifBlank { session.sessionId },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                     Text(
                                        text = "${session.count} frames" + if (isExpanded) " ▲" else " ▼",
                                        color = Color(0xFF88FF88),
                                        fontSize = 12.sp
                                    )
                                    // Show duration if session is loaded and preview exists
                                    if (isExpanded && sessionDetail?.sessionId == session.sessionId) {
                                        val previewState by viewModel.trajectoryPreview.collectAsState()
                                        if (previewState != null) {
                                            Text(
                                                text = "⏱ %.1fs".format(previewState!!.totalDurationSec),
                                                color = Color(0xFFFFDD88),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    viewModel.deleteSession(LibraryTarget.FOI_SESSION, session.sessionId)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete session",
                                        tint = Color(0xFFFF6666)
                                    )
                                }
                            }
                            
                            // Expanded frame details
                            if (isExpanded && sessionDetail?.sessionId == session.sessionId) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sessionDetail?.frames?.forEach { frame ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0x11FFFFFF), RoundedCornerShape(6.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Thumbnail
                                            val thumbnailBitmap = remember(frame.thumbnail) {
                                                frame.thumbnail?.let { base64 ->
                                                    try {
                                                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                    } catch (e: Exception) {
                                                        null
                                                    }
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp, 45.dp)
                                                    .background(Color(0xFF333333), RoundedCornerShape(4.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (thumbnailBitmap != null) {
                                                    Image(
                                                        bitmap = thumbnailBitmap.asImageBitmap(),
                                                        contentDescription = "Frame thumbnail",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Text("📷", fontSize = 20.sp)
                                                }
                                            }
                                            
                                            // Frame details column
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Header row: name + role indicator
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = frame.name,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = if (frame.sessionRole == "homebase") "🏠 HOME" else "#${frame.sequenceIndex + 1}",
                                                        color = if (frame.sessionRole == "homebase") Color(0xFFFFCC00) else Color(0xFF88FF88),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            
                                                // Data rows in a grid-like layout
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    // Base pose column
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "BASE",
                                                            color = Color(0xFF88AAFF),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    Text(
                                                        text = "x: %.2f m".format(frame.baseX),
                                                        color = Color(0xFFAAAAAA),
                                                        fontSize = 10.sp
                                                    )
                                                    Text(
                                                        text = "y: %.2f m".format(frame.baseY),
                                                        color = Color(0xFFAAAAAA),
                                                        fontSize = 10.sp
                                                    )
                                                    Text(
                                                        text = "yaw: %.1f°".format(Math.toDegrees(frame.baseYaw)),
                                                        color = Color(0xFFAAAAAA),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                
                                                // Arm joints column
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "ARM",
                                                        color = Color(0xFFFFAA88),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (frame.armQ.isNotEmpty()) {
                                                        frame.armQ.forEachIndexed { idx, q ->
                                                            Text(
                                                                text = "J${idx + 4}: %.1f°".format(Math.toDegrees(q)),
                                                                color = Color(0xFFAAAAAA),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "(no data)",
                                                            color = Color(0xFF666666),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                                
                                                // Gimbal column
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "GIMBAL",
                                                        color = Color(0xFFAAFF88),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val gimbalLabels = listOf("P", "T", "R")
                                                    if (frame.gimbalQ.isNotEmpty()) {
                                                        frame.gimbalQ.forEachIndexed { idx, q ->
                                                            val label = gimbalLabels.getOrElse(idx) { "$idx" }
                                                            Text(
                                                                text = "$label: %.1f°".format(Math.toDegrees(q)),
                                                                color = Color(0xFFAAAAAA),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "(no data)",
                                                            color = Color(0xFF666666),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            // Timing row (if any timing data)
                                            if (frame.dwellS != null || frame.transitionS != null || frame.ease != null) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        text = "TIMING",
                                                        color = Color(0xFFFFDD88),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (frame.dwellS != null && frame.dwellS > 0.0) {
                                                            Text(
                                                                text = "⏸ ${frame.dwellS}s",
                                                                color = Color(0xFFFFAA44),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        if (frame.transitionS != null) {
                                                            Text(
                                                                text = "→ ${frame.transitionS}s",
                                                                color = Color(0xFF44AAFF),
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        val easeIcon = when (frame.ease) {
                                                            "ease_in" -> "↗"
                                                            "ease_out" -> "↘"
                                                            "ease_in_out" -> "⟳"
                                                            else -> null
                                                        }
                                                        if (easeIcon != null) {
                                                            Text(
                                                                text = easeIcon,
                                                                color = Color(0xFF88FF88),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }  // End of Column (frame details)
                                    }  // End of Row (frame card)
                                }  // End of forEach frame
                            }  // End of Column (expanded content)
                        }  // End of if (isExpanded)
                        }  // End of Column (session)
                    }  // End of items (sessions)
                }  // End of LazyColumn
            }  // End of else (sessions exist)
        }  // End of PanelBox
    }  // End of Box
}

@Composable
fun SettingsOverlay(controllerManager: ControllerManager) {
    val recomoViewModel: RecomoControlViewModel = hiltViewModel()
    val controllerState by controllerManager.state.collectAsState()
    val controllerDeviceInfo by controllerManager.deviceInfo.collectAsState()
    val settingsScheme = darkColorScheme(
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFD0D0D0),
        onBackground = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White
    )

    val settingsInitialGroup by recomoViewModel.settingsInitialGroup.collectAsState()
    val groups = listOf("ROBOT", "CONNECT", "VIDEO", "CTRL", "ORIN")
    var selectedGroup by rememberSaveable { mutableStateOf("ROBOT") }
    // When the ViewModel requests a specific group (e.g. via SystemHealthBar tap), honour it
    LaunchedEffect(settingsInitialGroup) {
        if (settingsInitialGroup.isNotBlank()) {
            selectedGroup = settingsInitialGroup
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(end = TabRailWidth)
    ) {
        MaterialTheme(colorScheme = settingsScheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(Modifier.fillMaxSize()) {
                    // Left narrow nav panel
                    Column(
                        modifier = Modifier
                            .width(76.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF1A1A1A))
                            .padding(top = 40.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        groups.forEach { group ->
                            val selected = group == selectedGroup
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .clickable { selectedGroup = group },
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) Color(0xFF2C5282) else Color.Transparent
                            ) {
                                Text(
                                    text = group,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else Color(0xFFB0B0B0),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    // Right content panel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        key(selectedGroup) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                when (selectedGroup) {
                                    "ROBOT" -> RobotGroupContent(recomoViewModel)
                                    "CONNECT" -> GatewayGroupContent(recomoViewModel)
                                    "VIDEO" -> VideoSettingsGroupContent(recomoViewModel)
                                    "CTRL" -> {
                                        CtrlExtrasGroupContent(recomoViewModel)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ControllerSettingsPanel(
                                            recomoViewModel = recomoViewModel,
                                            controllerState = controllerState,
                                            controllerDeviceInfo = controllerDeviceInfo
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        StepSettingsPanel()
                                    }
                                    "ORIN" -> OrinGroupContent(recomoViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusColumn(
    focus: ControlFocus,
    onFocusChange: (ControlFocus) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xAA1A1A1A),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SquareIconChip(
                icon = Icons.Default.DirectionsCar,
                contentDescription = "Chassis",
                selected = focus == ControlFocus.CHASSIS,
                accentColor = Color(0xFF4DA3FF),
                onClick = { onFocusChange(ControlFocus.CHASSIS) }
            )
            SquareIconChip(
                icon = Icons.Default.PrecisionManufacturing,
                contentDescription = "Arm",
                selected = focus == ControlFocus.ARM,
                accentColor = Color(0xFFFFB347),
                onClick = { onFocusChange(ControlFocus.ARM) }
            )
            SquareIconChip(
                icon = Icons.Default.ControlCamera,
                contentDescription = "Gimbal",
                selected = focus == ControlFocus.GIMBAL,
                accentColor = Color(0xFF5AD1B2),
                onClick = { onFocusChange(ControlFocus.GIMBAL) }
            )
        }
    }
}

@Composable
fun ModeStatusBadge(status: com.recomo.remotecontrol.ui.ControllerStatus, modifier: Modifier = Modifier) {
    val modeLabel = when (status.controlMode) {
        ControlMode.FOCUS -> "FOCUS"
        ControlMode.POSITION -> "POSITION"
        ControlMode.UPPER -> "UPPER"
        ControlMode.CINE -> "CINE"
        ControlMode.ALL_DOF -> "ALL-DOF"
    }
    val focusLabel = when (status.focus) {
        ControlFocus.CHASSIS -> "CHASSIS"
        ControlFocus.ARM -> "ARM"
        ControlFocus.GIMBAL -> "GIMBAL"
    }
    val text = when {
        status.modeSelectActive -> "MODE SELECT · ${when (status.modeCandidate) {
            ControlMode.FOCUS -> "FOCUS"
            ControlMode.POSITION -> "POSITION"
            ControlMode.UPPER -> "UPPER"
            ControlMode.CINE -> "CINE"
            ControlMode.ALL_DOF -> "ALL-DOF"
        }}"
        status.tabSelectActive -> "TAB SELECT"
        modeLabel == "FOCUS" -> "MODE: FOCUS · $focusLabel"
        else -> "MODE: $modeLabel"
    }
    val bg = when {
        status.modeSelectActive -> Color(0xFF6A4B2C)
        status.tabSelectActive -> Color(0xFF5A3E2C)
        status.controlMode == ControlMode.CINE -> Color(0xFF2D5346)
        status.controlMode == ControlMode.UPPER -> Color(0xFF2F4E3A)
        status.controlMode == ControlMode.POSITION -> Color(0xFF2B4A6E)
        status.controlMode == ControlMode.ALL_DOF -> Color(0xFF3B3B3B)
        else -> Color(0xCC1E1E1E)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ModeSelectOverlay(status: com.recomo.remotecontrol.ui.ControllerStatus, modifier: Modifier = Modifier) {
    val candidate = when (status.modeCandidate) {
        ControlMode.FOCUS -> "FOCUS"
        ControlMode.POSITION -> "POSITION"
        ControlMode.UPPER -> "UPPER"
        ControlMode.CINE -> "CINE"
        ControlMode.ALL_DOF -> "ALL-DOF"
    }
    Surface(
        color = Color(0xCC121212),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MODE SELECT",
                color = Color(0xFFEEB266),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Current: $candidate",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "D-pad < >  A confirm  B cancel",
                color = Color(0xFFB8B8B8),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun StepColumn(
    stepMode: StepMode,
    onStepModeChange: (StepMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xAA1A1A1A),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SquareIconChip(
                icon = Icons.Default.Filter1,
                contentDescription = "Fine step",
                selected = stepMode == StepMode.FINE,
                accentColor = Color(0xFF6EDB8F),
                onClick = { onStepModeChange(StepMode.FINE) }
            )
            SquareIconChip(
                icon = Icons.Default.Filter2,
                contentDescription = "Normal step",
                selected = stepMode == StepMode.NORMAL,
                accentColor = Color(0xFFFFC857),
                onClick = { onStepModeChange(StepMode.NORMAL) }
            )
            SquareIconChip(
                icon = Icons.Default.Filter3,
                contentDescription = "Leap step",
                selected = stepMode == StepMode.LEAP,
                accentColor = Color(0xFFFF7A7A),
                onClick = { onStepModeChange(StepMode.LEAP) }
            )
        }
    }
}

@Composable
private fun SquareIconChip(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) accentColor.copy(alpha = 0.24f) else Color(0x55252525)
    val border = if (selected) accentColor.copy(alpha = 0.95f) else Color(0x55FFFFFF)
    val iconTint = if (selected) accentColor else Color.White

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        shadowElevation = if (selected) 10.dp else 0.dp,
        modifier = Modifier
            .size(ChipSize)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accentColor, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color(0xFF3F3F3F) else Color(0x55252525)
    val border = if (selected) Color(0x99FFFFFF) else Color(0x55FFFFFF)

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .height(28.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PanelBox(
    title: String,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 14.sp,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1C1C1C),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color(0x55FFFFFF))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = titleSize)
                if (titleAction != null) {
                    titleAction()
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun AngleInfoPanel(
    title: String,
    labels: List<String>,
    values: List<Double?>,
    selectedIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF252525),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(title, color = Color(0xFFDDDDDD), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    val selected = selectedIndex == index
                    val labelColor = if (selected) Color(0xFFBBD0FF) else Color(0xFFAAAAAA)
                    val labelBg = if (selected) Color(0x332E7DFF) else Color.Transparent
                    val valueColor = if (selected) Color(0xFFEFF3FF) else Color.White
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            color = labelColor,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .background(labelBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Text(
                            formatAngle(values.getOrNull(index)),
                            color = valueColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFB00020),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color(0x66FFFFFF)),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3A3A3A),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A2A2A),
            disabledContentColor = Color(0xFF888888)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, Color(0x55FFFFFF)),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RightTabRail(
    tabs: List<TabItem>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(TabRailWidth)
            .fillMaxSize()
            .background(Color(0x99000000)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        tabs.forEachIndexed { index, tab ->
            RailTabButton(
                tab = tab,
                selected = selectedTab == index,
                onClick = { onSelect(index) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun RailTabButton(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFF2E2E2E) else Color(0x44000000)
    val fg = if (selected) Color.White else Color(0xFFBBBBBB)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = Modifier
            .width(80.dp)
            .height(44.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = fg
        )
    }
}

@Composable
fun HoldButton(
    label: String,
    size: Dp = 52.dp,
    fontSize: TextUnit = 16.sp,
    active: Boolean = false,
    onTick: () -> Unit
) {
    HoldButtonBase(size = size, active = active, onTick = onTick) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = fontSize)
    }
}

@Composable
fun ArrowButton(
    label: String,
    arrow: String,
    size: Dp,
    fontSize: TextUnit,
    active: Boolean = false,
    onTick: () -> Unit
) {
    HoldButtonBase(size = size, active = active, onTick = onTick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(arrow, fontWeight = FontWeight.SemiBold, fontSize = fontSize)
            Text(label, fontWeight = FontWeight.Bold, fontSize = fontSize)
        }
    }
}

@Composable
fun PosePair(
    label: String,
    size: Dp,
    fontSize: TextUnit,
    arrowSize: TextUnit,
    gap: Dp,
    labelWidthFactor: Float = 0.4f,
    enabled: Boolean = true,
    selected: Boolean = false,
    negativeActive: Boolean = false,
    positiveActive: Boolean = false,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
    onNegativeHoldStart: () -> Unit = {},
    onNegativeHoldEnd: () -> Unit = {},
    onPositiveHoldStart: () -> Unit = {},
    onPositiveHoldEnd: () -> Unit = {},
    negativeArrow: String = "⟲",
    positiveArrow: String = "⟳"
) {
    val labelColor = if (selected) Color(0xFFBBD0FF) else Color(0xFFDDDDDD)
    val labelBg = if (selected) Color(0x332E7DFF) else Color.Transparent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HoldableButton(
                arrow = negativeArrow,
                size = size,
                arrowSize = arrowSize,
                enabled = enabled,
                active = negativeActive,
                onTap = onNegative,
                onHoldStart = onNegativeHoldStart,
                onHoldEnd = onNegativeHoldEnd
            )
            Text(
                label,
                color = labelColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .widthIn(min = size * labelWidthFactor)
                    .background(labelBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                textAlign = TextAlign.Center
            )
            HoldableButton(
                arrow = positiveArrow,
                size = size,
                arrowSize = arrowSize,
                enabled = enabled,
                active = positiveActive,
                onTap = onPositive,
                onHoldStart = onPositiveHoldStart,
                onHoldEnd = onPositiveHoldEnd
            )
        }
    }
}

@Composable
fun ChassisControlGrid(
    modifier: Modifier = Modifier,
    arrowSize: TextUnit,
    textSize: TextUnit,
    enabled: Boolean = true,
    forwardActive: Boolean = false,
    backActive: Boolean = false,
    leftActive: Boolean = false,
    rightActive: Boolean = false,
    rotateLeftActive: Boolean = false,
    rotateRightActive: Boolean = false,
    onForwardTap: () -> Unit,
    onForwardHoldStart: () -> Unit,
    onForwardHoldEnd: () -> Unit,
    onBackTap: () -> Unit,
    onBackHoldStart: () -> Unit,
    onBackHoldEnd: () -> Unit,
    onLeftTap: () -> Unit,
    onLeftHoldStart: () -> Unit,
    onLeftHoldEnd: () -> Unit,
    onRightTap: () -> Unit,
    onRightHoldStart: () -> Unit,
    onRightHoldEnd: () -> Unit,
    onRotateLeftTap: () -> Unit,
    onRotateLeftHoldStart: () -> Unit,
    onRotateLeftHoldEnd: () -> Unit,
    onRotateRightTap: () -> Unit,
    onRotateRightHoldStart: () -> Unit,
    onRotateRightHoldEnd: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth / 3
        val cellH = maxHeight / 6
        val baseCircle = (if (cellW < cellH) cellW else cellH) * 0.85f
        val maxCircle = (if (cellW < cellH * 2) cellW else cellH * 2) * 0.98f
        val circleCap = (maxHeight * 0.26f).coerceIn(48.dp, 80.dp)
        val circle = minOf(baseCircle * 3.6f, maxCircle, circleCap)
        val midGap = cellH * 0.5f
        val ltRtShift = cellW * 0.2f

        @Composable
        fun place(
            col: Int,
            rowStart: Int,
            rowEnd: Int,
            label: String,
            arrow: String,
            active: Boolean,
            onTap: () -> Unit,
            onHoldStart: () -> Unit,
            onHoldEnd: () -> Unit,
            xOffset: Dp = 0.dp,
            gapTop: Dp = 0.dp,
            gapBottom: Dp = 0.dp
        ) {
            val height = cellH * (rowEnd - rowStart + 1) - gapTop - gapBottom
            val localCircle = minOf(circle, height * 0.98f, cellW * 0.98f)
            Box(
                modifier = Modifier
                    .offset(
                        x = cellW * (col - 1) + xOffset,
                        y = cellH * (rowStart - 1) + gapTop
                    )
                    .size(cellW, height),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HoldableButton(
                        arrow = arrow,
                        size = localCircle,
                        arrowSize = arrowSize,
                        enabled = enabled,
                        active = active,
                        onTap = onTap,
                        onHoldStart = onHoldStart,
                        onHoldEnd = onHoldEnd
                    )
                    Text(
                        label,
                        color = Color.White,
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }

        place(2, 1, 2, "F", "↑", forwardActive, onForwardTap, onForwardHoldStart, onForwardHoldEnd, gapBottom = midGap / 2)
        place(2, 3, 4, "B", "↓", backActive, onBackTap, onBackHoldStart, onBackHoldEnd, gapTop = midGap / 2)
        place(1, 2, 3, "L", "←", leftActive, onLeftTap, onLeftHoldStart, onLeftHoldEnd)
        place(3, 2, 3, "R", "→", rightActive, onRightTap, onRightHoldStart, onRightHoldEnd)
        place(1, 5, 6, "LT", "⟲", rotateLeftActive, onRotateLeftTap, onRotateLeftHoldStart, onRotateLeftHoldEnd, xOffset = ltRtShift)
        place(3, 5, 6, "RT", "⟳", rotateRightActive, onRotateRightTap, onRotateRightHoldStart, onRotateRightHoldEnd, xOffset = -ltRtShift)
    }
}

@Composable
fun CircleArrowButton(
    label: String,
    arrow: String,
    size: Dp,
    labelSize: TextUnit,
    arrowSize: TextUnit,
    arrowOffsetY: Dp = 0.dp,
    enabled: Boolean = true,
    active: Boolean = false,
    onTick: () -> Unit
) {
    val density = LocalDensity.current
    val sizeSp = with(density) { size.toSp() }
    val actualArrow = maxOf(arrowSize.value, (sizeSp.value * 0.2f)).sp
    val actualLabel = maxOf(labelSize.value, (sizeSp.value * 0.12f)).sp
    val shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 6f)

    HoldButtonBase(size = size, enabled = enabled, active = active, onTick = onTick) {
        if (label.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    arrow,
                    modifier = Modifier.offset(y = arrowOffsetY),
                    fontWeight = FontWeight.Bold,
                    fontSize = actualArrow,
                    color = Color.White,
                    style = TextStyle(shadow = shadow),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    arrow,
                    fontWeight = FontWeight.Bold,
                    fontSize = actualArrow,
                    color = Color.White,
                    style = TextStyle(shadow = shadow),
                    textAlign = TextAlign.Center
                )
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = actualLabel,
                    color = Color.White,
                    style = TextStyle(shadow = shadow),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CameraPositionGrid(
    modifier: Modifier = Modifier,
    labelSize: TextUnit,
    arrowSize: TextUnit,
    buttonSize: Dp,
    enabled: Boolean = true,
    upActive: Boolean = false,
    downActive: Boolean = false,
    leftActive: Boolean = false,
    forwardActive: Boolean = false,
    rightActive: Boolean = false,
    backActive: Boolean = false,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onForward: () -> Unit,
    onRight: () -> Unit,
    onBack: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val cellW = maxWidth / 3
        val cellH = maxHeight / 4
        val baseCircle = minOf(cellW, cellH) * 0.95f
        val circle = minOf(buttonSize, baseCircle, cellW * 0.95f, cellH * 0.95f)
        val labelGap = 6.dp
        val udInset = cellW * 0.22f

        @Composable
        fun place(
            col: Int,
            rowStart: Int,
            rowEnd: Int,
            label: String,
            arrow: String,
            active: Boolean,
            onTick: () -> Unit,
            xOffset: Dp = 0.dp
        ) {
            val height = cellH * (rowEnd - rowStart + 1)
            val localCircle = minOf(circle, height * 0.95f, cellW * 0.95f)
            Box(
                modifier = Modifier
                    .offset(x = cellW * (col - 1) + xOffset, y = cellH * (rowStart - 1))
                    .size(cellW, height),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(labelGap)
                ) {
                    CircleArrowButton(
                        label = "",
                        arrow = arrow,
                        size = localCircle,
                        labelSize = labelSize,
                        arrowSize = arrowSize,
                        arrowOffsetY = (-3).dp,
                        enabled = enabled,
                        active = active,
                        onTick = onTick
                    )
                    Text(
                        label,
                        color = Color.White,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        place(1, 1, 1, "U", "↑", upActive, onUp, xOffset = udInset)
        place(2, 2, 2, "F", "↗", forwardActive, onForward)
        place(3, 1, 1, "D", "↓", downActive, onDown, xOffset = -udInset)
        place(1, 2, 3, "L", "←", leftActive, onLeft)
        place(3, 2, 3, "R", "→", rightActive, onRight)
        place(2, 3, 3, "B", "↙", backActive, onBack)
    }
}

@Composable
fun HoldableButton(
    arrow: String,
    size: Dp,
    arrowSize: TextUnit,
    enabled: Boolean = true,
    active: Boolean = false,
    holdThresholdMs: Long = 200L,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    val density = LocalDensity.current
    val sizeSp = with(density) { size.toSp() }
    val actualArrow = maxOf(arrowSize.value, (sizeSp.value * 0.2f)).sp
    val shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 6f)
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var flashUntil by remember { mutableStateOf(0L) }
    val now = System.currentTimeMillis()
    val flash = now < flashUntil
    LaunchedEffect(flashUntil) {
        if (flashUntil > 0L) {
            val remaining = flashUntil - System.currentTimeMillis()
            if (remaining > 0) kotlinx.coroutines.delay(remaining)
            flashUntil = 0L
        }
    }
    val isActive = active || isHolding || flash
    val bgColor = when {
        enabled && isActive -> Color(0xFF5A8BFF)
        enabled -> Color(0xFF4A4A4A)
        isActive -> Color(0xFF3A4A66)
        else -> Color(0xFF2A2A2A)
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = bgColor,
                shape = CircleShape
            )
            .border(BorderStroke(1.dp, if (isActive) Color(0xFFBBD0FF) else Color(0xAAFFFFFF)), CircleShape)
            .systemGestureExclusion()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    down.consume()
                    isHolding = false
                    
                    // Start hold detection job
                    holdJob = coroutineScope.launch {
                        delay(holdThresholdMs)
                        isHolding = true
                        onHoldStart()
                    }
                    
                    // Wait for the original pointer to lift
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        val tracked = event.changes.firstOrNull { it.id == pointerId }
                        if (tracked == null || !tracked.pressed) {
                            break
                        }
                    }
                    
                    holdJob?.cancel()
                    if (isHolding) {
                        onHoldEnd()
                    } else {
                        onTap()
                        flashUntil = System.currentTimeMillis() + 150
                    }
                    isHolding = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            arrow,
            fontWeight = FontWeight.Bold,
            fontSize = actualArrow,
            color = if (enabled) Color.White else Color.Gray,
            style = TextStyle(shadow = shadow),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HoldButtonBase(
    size: Dp,
    enabled: Boolean = true,
    active: Boolean = false,
    onTick: () -> Unit,
    content: @Composable () -> Unit
) {
    var flashUntil by remember { mutableStateOf(0L) }
    val now = System.currentTimeMillis()
    val flash = now < flashUntil
    LaunchedEffect(flashUntil) {
        if (flashUntil > 0L) {
            val remaining = flashUntil - System.currentTimeMillis()
            if (remaining > 0) kotlinx.coroutines.delay(remaining)
            flashUntil = 0L
        }
    }
    val isActive = active || flash
    val containerColor = when {
        enabled && isActive -> Color(0xFF5A8BFF)
        enabled -> Color(0xFF4A4A4A)
        isActive -> Color(0xFF3A4A66)
        else -> Color(0xFF2A2A2A)
    }
    Button(
        onClick = {
            flashUntil = System.currentTimeMillis() + 150
            onTick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor,
            disabledContentColor = Color.Gray
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, if (isActive) Color(0xFFBBD0FF) else Color(0xAAFFFFFF)),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(size)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun FrameTimingDialog(
    frameName: String,
    currentTiming: FrameTiming? = null,  // NEW: Load existing timing
    onConfirm: (FrameTiming) -> Unit,
    onDismiss: () -> Unit
) {
    var dwellS by remember { mutableStateOf(currentTiming?.dwellS?.toString() ?: "") }
    var transitionS by remember { mutableStateOf(currentTiming?.transitionS?.toString() ?: "") }
    var ease by remember { mutableStateOf(currentTiming?.ease ?: "linear") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    
    // Validation
    val dwellValue = dwellS.toDoubleOrNull()
    val transitionValue = transitionS.toDoubleOrNull()
    val isValid = (dwellS.isBlank() || (dwellValue != null && dwellValue >= 0.0 && dwellValue <= 60.0)) &&
                  (transitionS.isBlank() || (transitionValue != null && transitionValue >= 0.1 && transitionValue <= 60.0))
    
    // Warnings for unusual values (not errors, just suggestions)
    warningMessage = when {
        dwellValue != null && dwellValue > 20.0 -> "⚠️ Long pause (${dwellValue}s). Sure you want to wait that long?"
        transitionValue != null && transitionValue > 30.0 -> "⚠️ Very slow motion (${transitionValue}s). Consider shorter time."
        transitionValue != null && transitionValue < 1.0 -> "⚠️ Very fast motion (${transitionValue}s). May be jerky."
        else -> null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Frame Timing: $frameName", color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Set timing parameters for this frame (optional)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                
                // Dwell time input
                OutlinedTextField(
                    value = dwellS,
                    onValueChange = { 
                        dwellS = it.filter { c -> c.isDigit() || c == '.' }
                        val value = dwellS.toDoubleOrNull()
                        errorMessage = when {
                            value != null && value > 60.0 -> "Dwell time max 60s"
                            value != null && value < 0.0 -> "Dwell time must be >= 0"
                            else -> null
                        }
                    },
                    label = { Text("Pause at frame (0-60s)", color = Color.White.copy(alpha = 0.6f)) },
                    placeholder = { Text("0-5s typical, 0 for no pause", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2A2F),
                        unfocusedContainerColor = Color(0xFF2A2A2F)
                    ),
                    singleLine = true,
                    isError = !dwellS.isBlank() && (dwellValue == null || dwellValue < 0.0 || dwellValue > 60.0)
                )
                
                // Transition time input
                OutlinedTextField(
                    value = transitionS,
                    onValueChange = { 
                        transitionS = it.filter { c -> c.isDigit() || c == '.' }
                        val value = transitionS.toDoubleOrNull()
                        errorMessage = when {
                            value != null && value > 60.0 -> "Transition time max 60s"
                            value != null && value < 0.1 -> "Transition time must be >= 0.1s"
                            else -> null
                        }
                    },
                    label = { Text("Move time to next (0.1-60s)", color = Color.White.copy(alpha = 0.6f)) },
                    placeholder = { Text("2-10s typical, blank=auto", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2A2A2F),
                        unfocusedContainerColor = Color(0xFF2A2A2F)
                    ),
                    singleLine = true,
                    isError = !transitionS.isBlank() && (transitionValue == null || transitionValue < 0.1 || transitionValue > 60.0)
                )
                
                // Error message
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF6666),
                        fontSize = 11.sp
                    )
                }
                
                // Warning message (yellow, informational)
                if (warningMessage != null) {
                    Text(
                        text = warningMessage!!,
                        color = Color(0xFFFFDD88),
                        fontSize = 11.sp
                    )
                }
                
                // Easing selector
                Text("Motion Style", color = Color.White, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("linear", "ease_in", "ease_out", "ease_in_out").forEach { option ->
                        Button(
                            onClick = { ease = option },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ease == option) Color(0xFF4A9EFF) else Color(0xFF3A3A3F)
                            ),
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                text = option.replace("_", "\n"),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timing = FrameTiming(
                        dwellS = dwellS.toDoubleOrNull(),
                        transitionS = transitionS.toDoubleOrNull(),
                        ease = if (ease == "linear") null else ease
                    )
                    onConfirm(timing)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1A1A1F),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

private fun formatAngle(rad: Double?): String {
    if (rad == null || rad.isNaN()) return "--"
    val deg = rad * 180.0 / PI
    return String.format(Locale.US, "%.1f°", deg)
}

@Composable
fun WorldViewPanels(
    viewModel: RecomoControlViewModel,
    controllerState: com.recomo.remotecontrol.controller.ControllerState,
    controllerStatus: ControllerStatus,
    controllerSettings: com.recomo.remotecontrol.settings.ControllerSettings,
    safety: SafetyStatus,
    focus: ControlFocus,
    armMode: ArmControlMode,
    stepMode: StepMode,
    robotProfile: RobotProfile,
    armAngles: List<Double>,
    gimbalAngles: List<Double>,
    baseYawRad: Double?,
    sessionName: String,
    frameName: String,
    onSessionNameChange: (String) -> Unit,
    onFrameNameChange: (String) -> Unit,
    currentSessionName: String?,
    currentFrameCount: Int,
    hasHomebase: Boolean,
    suggestedSessionName: String,
    suggestedFrameName: String
) {
    // TODO: Move existing panel code here (Chassis + Arm + Gimbal + FOI)
    Text("World View Panels - TODO: Implement existing panel layout",
        modifier = Modifier.fillMaxSize().padding(16.dp),
        color = Color.White
    )
}

@Composable
fun CameraViewPanels(
    viewModel: RecomoControlViewModel,
    safety: SafetyStatus,
    gimbalAngles: List<Double>,
    sessionName: String,
    frameName: String,
    onSessionNameChange: (String) -> Unit,
    onFrameNameChange: (String) -> Unit,
    currentSessionName: String?,
    currentFrameCount: Int,
    hasHomebase: Boolean,
    suggestedSessionName: String,
    suggestedFrameName: String
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val stepSettingsViewModel: StepSettingsViewModel = hiltViewModel()
    val stepSettings by stepSettingsViewModel.stepSettings.collectAsState()
    var sessionNameText by remember { mutableStateOf(sessionName) }
    var frameNameText by remember { mutableStateOf(frameName) }
    
    // Sync with parent state
    LaunchedEffect(sessionName) { sessionNameText = sessionName }
    LaunchedEffect(frameName) { frameNameText = frameName }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Safety controls at top (E-STOP, FREEZE) - always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DangerButton(
                text = "E-STOP",
                onClick = { viewModel.sendEstop() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            SecondaryButton(
                text = if (safety.freezeAll) "UNFREEZE" else "FREEZE",
                onClick = {
                    if (safety.freezeAll) viewModel.unfreezeAll() else viewModel.freezeAll()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            if (safety.estop) {
                SecondaryButton(
                    text = if (safety.estopCooldownMs > 0)
                        "CLEAR (${String.format("%.1f", safety.estopCooldownMs / 1000.0)}s)"
                    else "CLEAR",
                    onClick = { viewModel.clearEstop() },
                    enabled = safety.canClearEstop,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                )
            }
        }
        
        // Main control panels
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: EE Position Control Panel
            EEPositionControlPanel(
                gatewayClient = viewModel.getGatewayClient(),
                isConnected = isConnected,
                frame = "camera",
                stepSettings = stepSettings,
                ikRunning = viewModel.eeIkControllerRunning.collectAsState().value,
                ikDryRun = viewModel.eeIkDryRun.collectAsState().value,
                ikBusy = viewModel.eeIkControllerBusy.collectAsState().value,
                onIkStart = { dryRun -> viewModel.startEEIKController(dryRun) },
                onIkStop = { viewModel.stopEEIKController() },
                // IMU teleop
                imuTeleopState = viewModel.imuTeleopManager.state.collectAsState().value,
                imuSensitivity = viewModel.imuTeleopManager.sensitivity.collectAsState().value,
                imuGimbalMix = viewModel.imuTeleopManager.gimbalMix.collectAsState().value,
                onImuDeadmanDown = { viewModel.imuTeleopManager.setActive(true) },
                onImuDeadmanUp = { viewModel.imuTeleopManager.setActive(false) },
                onImuCalibrate = { viewModel.imuTeleopManager.calibrate() },
                onImuSensitivityChange = { viewModel.imuTeleopManager.sensitivity.value = it },
                onImuGimbalMixChange = { viewModel.imuTeleopManager.gimbalMix.value = it },
                modifier = Modifier.weight(0.9f)
            )
            
            // Center: FOI Session Panel (same proportions as World View)
            PanelBox(
                title = "FRAME OF INTEREST",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                titleSize = 12.sp
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val compact = maxHeight < 220.dp
                    val gap = if (compact) 4.dp else 6.dp
                    val inputHeight = if (compact) 40.dp else 46.dp
                    val rowHeight = (maxHeight * 0.15f).coerceIn(30.dp, 42.dp)
                    val labelSize = if (compact) 10.sp else 11.sp
                    val focusManager = LocalFocusManager.current

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        // Session status line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSessionName ?: "(no session)",
                                color = if (currentSessionName != null) Color.White else Color.Gray,
                                fontSize = labelSize,
                                maxLines = 1
                            )
                            Text(
                                text = "${if (hasHomebase) "H+" else ""}${currentFrameCount}F",
                                color = Color(0xFF88FF88),
                                fontSize = labelSize
                            )
                        }
                        
                        // Frame list (if any frames recorded)
                        val draftFrames by viewModel.draftFramesList.collectAsState()
                        if (draftFrames.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 80.dp)
                                    .background(Color(0xFF1A1A1F), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(6.dp))
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    items(draftFrames.size) { index ->
                                        FrameListItem(
                                            frame = draftFrames[index],
                                            index = index,
                                            onClick = { viewModel.editFrameTiming(index) }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Session name input
                        OutlinedTextField(
                            value = sessionNameText,
                            onValueChange = { 
                                sessionNameText = it
                                onSessionNameChange(it)
                            },
                            label = { Text("Session name", fontSize = 10.sp) },
                            placeholder = { Text(suggestedSessionName, color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(inputHeight),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = if (compact) 10.sp else 11.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (sessionNameText.isBlank()) {
                                    sessionNameText = suggestedSessionName
                                    onSessionNameChange(suggestedSessionName)
                                }
                                focusManager.clearFocus()
                            })
                        )
                        
                        // Frame name input
                        OutlinedTextField(
                            value = frameNameText,
                            onValueChange = { 
                                frameNameText = it
                                onFrameNameChange(it)
                            },
                            label = { Text("Frame name", fontSize = 10.sp) },
                            placeholder = { Text(suggestedFrameName, color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(inputHeight),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = if (compact) 10.sp else 11.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (frameNameText.isBlank()) {
                                    frameNameText = suggestedFrameName
                                    onFrameNameChange(suggestedFrameName)
                                }
                                focusManager.clearFocus()
                            })
                        )
                        
                        // Map selection dropdown
                        val availableMaps by viewModel.availableMaps.collectAsState()
                        val selectedMap by viewModel.selectedMap.collectAsState()
                        var mapExpanded by remember { mutableStateOf(false) }
                        
                        @OptIn(ExperimentalMaterial3Api::class)
                        ExposedDropdownMenuBox(
                            expanded = mapExpanded,
                            onExpandedChange = { mapExpanded = !mapExpanded && availableMaps.isNotEmpty() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = when {
                                    selectedMap != null -> selectedMap!!
                                    availableMaps.isEmpty() -> "No locations (check connection)"
                                    else -> "Select location"
                                },
                                onValueChange = {},
                                readOnly = true,
                                enabled = availableMaps.isNotEmpty(),
                                label = { Text("Location", fontSize = if (compact) 10.sp else 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = if (availableMaps.isEmpty()) Color.Gray else Color(0xFFCCCCCC)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .height(inputHeight),
                                textStyle = TextStyle(fontSize = if (compact) 11.sp else 13.sp),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent
                                )
                            )
                            
                            ExposedDropdownMenu(
                                expanded = mapExpanded,
                                onDismissRequest = { mapExpanded = false },
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                availableMaps.forEach { mapName ->
                                    DropdownMenuItem(
                                        text = { Text(mapName, color = Color.White) },
                                        onClick = {
                                            viewModel.selectMap(mapName)
                                            mapExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Display selected map name
                        if (selectedMap != null) {
                            Text(
                                text = "Selected location: $selectedMap",
                                fontSize = if (compact) 10.sp else 11.sp,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SecondaryButton(
                                    text = "NEW",
                                    onClick = { 
                                        viewModel.checkAndStartLocalization()
                                        viewModel.newSession(sessionNameText)
                                        sessionNameText = ""
                                        onSessionNameChange("")
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                )
                                SecondaryButton(
                                    text = "HOMEBASE",
                                    onClick = { viewModel.setHomebase(frameNameText, sessionNameText) },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SecondaryButton(
                                    text = "RECORD",
                                    onClick = { 
                                        viewModel.recordFrame(frameNameText, sessionNameText)
                                        // Clear if it was an auto-name so placeholder updates
                                        if (frameNameText.isBlank() || frameNameText.matches(Regex("frame_\\d+"))) {
                                            frameNameText = ""
                                            onFrameNameChange("")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                )
                                SecondaryButton(
                                    text = "SAVE",
                                    onClick = { 
                                        viewModel.saveSessions()
                                        sessionNameText = ""
                                        frameNameText = ""
                                        onSessionNameChange("")
                                        onFrameNameChange("")
                                    },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Right: Gimbal Control Panel
            PanelBox(
                title = "END-POSE",
                modifier = Modifier.weight(0.9f).fillMaxHeight(),
                titleSize = 12.sp
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val compact = maxHeight < 200.dp
                    val posePadSize = if (compact) 32.dp else 42.dp
                    val poseLabelSize = if (compact) 10.sp else 12.sp
                    val poseArrowSize = if (compact) 18.sp else 22.sp
                    val poseGap = if (compact) 4.dp else 6.dp
                    val poseLabelWidthFactor = 0.4f
                    val gimbalEnabled = true // Always enabled in camera view
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(poseGap)
                    ) {
                        PosePair(
                            label = "P",
                            size = posePadSize,
                            fontSize = poseLabelSize,
                            arrowSize = poseArrowSize,
                            gap = poseGap,
                            labelWidthFactor = poseLabelWidthFactor,
                            enabled = gimbalEnabled,
                            negativeActive = false,
                            positiveActive = false,
                            onNegative = { viewModel.nudgeGimbalNegative(2) },
                            onPositive = { viewModel.nudgeGimbalPositive(2) },
                            onNegativeHoldStart = { viewModel.startGimbalVelocityControl(2, -1.0) },
                            onNegativeHoldEnd = { viewModel.stopGimbalVelocityControl(2) },
                            onPositiveHoldStart = { viewModel.startGimbalVelocityControl(2, 1.0) },
                            onPositiveHoldEnd = { viewModel.stopGimbalVelocityControl(2) },
                            negativeArrow = "←",
                            positiveArrow = "→"
                        )
                        PosePair(
                            label = "T",
                            size = posePadSize,
                            fontSize = poseLabelSize,
                            arrowSize = poseArrowSize,
                            gap = poseGap,
                            labelWidthFactor = poseLabelWidthFactor,
                            enabled = gimbalEnabled,
                            negativeActive = false,
                            positiveActive = false,
                            onNegative = { viewModel.nudgeGimbalNegative(1) },
                            onPositive = { viewModel.nudgeGimbalPositive(1) },
                            onNegativeHoldStart = { viewModel.startGimbalVelocityControl(1, -1.0) },
                            onNegativeHoldEnd = { viewModel.stopGimbalVelocityControl(1) },
                            onPositiveHoldStart = { viewModel.startGimbalVelocityControl(1, 1.0) },
                            onPositiveHoldEnd = { viewModel.stopGimbalVelocityControl(1) },
                            negativeArrow = "↓",
                            positiveArrow = "↑"
                        )
                        PosePair(
                            label = "R",
                            size = posePadSize,
                            fontSize = poseLabelSize,
                            arrowSize = poseArrowSize,
                            gap = poseGap,
                            labelWidthFactor = poseLabelWidthFactor,
                            enabled = gimbalEnabled,
                            negativeActive = false,
                            positiveActive = false,
                            onNegative = { viewModel.nudgeGimbalNegative(0) },
                            onPositive = { viewModel.nudgeGimbalPositive(0) },
                            onNegativeHoldStart = { viewModel.startGimbalVelocityControl(0, -1.0) },
                            onNegativeHoldEnd = { viewModel.stopGimbalVelocityControl(0) },
                            onPositiveHoldStart = { viewModel.startGimbalVelocityControl(0, 1.0) },
                            onPositiveHoldEnd = { viewModel.stopGimbalVelocityControl(0) }
                        )
                        Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
                        AngleInfoPanel(
                            title = "END-POSE",
                            labels = listOf("P", "T", "R"),
                            values = listOf(
                                gimbalAngles.getOrNull(2),
                                gimbalAngles.getOrNull(1),
                                gimbalAngles.getOrNull(0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }  // End of Row (main panels)
    }  // End of Column (with safety controls)
}

@Composable
fun ControlViewModeToggle(
    currentMode: ControlViewMode,
    onModeChange: (ControlViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // World View button
        Button(
            onClick = { onModeChange(ControlViewMode.WORLD_VIEW) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == ControlViewMode.WORLD_VIEW) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                contentColor = if (currentMode == ControlViewMode.WORLD_VIEW) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ),
            modifier = Modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "World View",
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "World View",
                    fontSize = 12.sp,
                    fontWeight = if (currentMode == ControlViewMode.WORLD_VIEW) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        
        // Camera View button
        Button(
            onClick = { onModeChange(ControlViewMode.CAMERA_VIEW) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == ControlViewMode.CAMERA_VIEW) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                contentColor = if (currentMode == ControlViewMode.CAMERA_VIEW) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ),
            modifier = Modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew, // Using Autorenew as placeholder for camera/lens icon
                    contentDescription = "Camera View",
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Camera View",
                    fontSize = 12.sp,
                    fontWeight = if (currentMode == ControlViewMode.CAMERA_VIEW) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)
