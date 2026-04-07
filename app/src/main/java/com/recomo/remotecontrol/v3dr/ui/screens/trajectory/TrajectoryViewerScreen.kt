package com.recomo.remotecontrol.v3dr.ui.screens.trajectory

import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Session with trajectory for selection
 */
data class TrajectorySession(
    val sessionDir: File,
    val trajectoryFile: File,
    val sessionName: String,
    val algorithm: String?
)

/**
 * Trajectory Viewer Screen - visualize camera trajectories in 3D and 2D
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrajectoryViewerScreen(
    sessionsDir: File,
    onNavigateBack: () -> Unit
) {
    // Find all sessions with trajectories
    val sessions = remember(sessionsDir) {
        sessionsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            TrajectoryData.findTrajectoryFile(dir)?.let { trajFile ->
                val data = TrajectoryData.fromTumFile(trajFile)
                TrajectorySession(
                    sessionDir = dir,
                    trajectoryFile = trajFile,
                    sessionName = dir.name,
                    algorithm = data?.algorithm
                )
            }
        }?.sortedByDescending { it.sessionDir.lastModified() } ?: emptyList()
    }
    
    var selectedSession by remember { mutableStateOf<TrajectorySession?>(sessions.firstOrNull()) }
    var trajectoryData by remember { mutableStateOf<TrajectoryData?>(null) }
    
    // 3D view state
    var renderer3D by remember { mutableStateOf<TrajectoryRenderer?>(null) }
    var glSurface3D by remember { mutableStateOf<GLSurfaceView?>(null) }
    var zoom3D by remember { mutableFloatStateOf(3.0f) }
    
    // Load trajectory when session changes
    LaunchedEffect(selectedSession) {
        selectedSession?.let {
            trajectoryData = TrajectoryData.fromTumFile(it.trajectoryFile)
        }
    }
    
    // Update renderer when trajectory changes
    LaunchedEffect(trajectoryData) {
        trajectoryData?.let { data ->
            renderer3D?.setTrajectory(data)
            glSurface3D?.requestRender()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trajectory Viewer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No trajectories found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Record videos with ARCore or run Cloud VIO first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp)
            ) {
                // Left side - Session list + 3D view
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                ) {
                    // Session selector
                    SessionSelector(
                        sessions = sessions,
                        selectedSession = selectedSession,
                        onSessionSelected = { selectedSession = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 3D View with controls
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        // OpenGL View
                        AndroidView(
                            factory = { ctx ->
                                GLSurfaceView(ctx).apply {
                                    setEGLContextClientVersion(2)
                                    val r = TrajectoryRenderer()
                                    renderer3D = r
                                    setRenderer(r)
                                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                    
                                    trajectoryData?.let { r.setTrajectory(it) }
                                    
                                    // Touch handling for rotation/zoom/pan
                                    var lastX = 0f
                                    var lastY = 0f
                                    var mode = 0 // 0=none, 1=rotate, 2=zoom, 3=pan
                                    
                                    val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                                            // Pinch out (spread) = zoom in = decrease distance
                                            // Pinch in (pinch) = zoom out = increase distance
                                            r.distance /= detector.scaleFactor
                                            r.distance = r.distance.coerceIn(0.5f, 20f)
                                            zoom3D = r.distance
                                            requestRender()
                                            return true
                                        }
                                    })
                                    
                                    setOnTouchListener { _, event ->
                                        scaleDetector.onTouchEvent(event)
                                        
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                lastX = event.x
                                                lastY = event.y
                                                mode = 1
                                            }
                                            MotionEvent.ACTION_POINTER_DOWN -> {
                                                if (event.pointerCount == 2) {
                                                    mode = 2 // Two fingers = zoom (handled by scaleDetector)
                                                    lastX = (event.getX(0) + event.getX(1)) / 2
                                                    lastY = (event.getY(0) + event.getY(1)) / 2
                                                } else if (event.pointerCount >= 3) {
                                                    mode = 3 // Three fingers = pan
                                                    lastX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3
                                                    lastY = (event.getY(0) + event.getY(1) + event.getY(2)) / 3
                                                }
                                            }
                                            MotionEvent.ACTION_MOVE -> {
                                                if (event.pointerCount == 1 && mode == 1) {
                                                    // Single finger - rotate around trajectory center (origin)
                                                    val dx = event.x - lastX
                                                    val dy = event.y - lastY
                                                    r.rotationY += dx * 0.5f
                                                    r.rotationX += dy * 0.5f
                                                    r.rotationX = r.rotationX.coerceIn(-89f, 89f)
                                                    lastX = event.x
                                                    lastY = event.y
                                                } else if (event.pointerCount >= 3 && mode == 3) {
                                                    // Three fingers - pan
                                                    val cx = (event.getX(0) + event.getX(1) + event.getX(2)) / 3
                                                    val cy = (event.getY(0) + event.getY(1) + event.getY(2)) / 3
                                                    r.panX -= (cx - lastX) * 0.002f * r.distance
                                                    r.panY += (cy - lastY) * 0.002f * r.distance
                                                    lastX = cx
                                                    lastY = cy
                                                }
                                                requestRender()
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                                if (event.pointerCount <= 1) mode = 0
                                            }
                                        }
                                        true
                                    }
                                    
                                    glSurface3D = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // View label and legend
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    "3D View",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Legend - use square markers to match OpenGL points
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).background(Color.Green))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Start", color = Color.White, fontSize = 10.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).background(Color.Red))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("End", color = Color.White, fontSize = 10.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).background(Color.Yellow))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Waypoint", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        
                        // Control buttons (top right)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Fit button
                            SmallFloatingActionButton(
                                onClick = {
                                    renderer3D?.fitToView()
                                    zoom3D = 3.0f
                                    glSurface3D?.requestRender()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.FitScreen, contentDescription = "Fit", modifier = Modifier.size(18.dp))
                            }
                            // Reset button
                            SmallFloatingActionButton(
                                onClick = {
                                    renderer3D?.resetView()
                                    zoom3D = 3.0f
                                    glSurface3D?.requestRender()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        // Zoom slider (right edge, vertical)
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .width(40.dp)
                                .height(150.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Slider(
                                value = zoom3D,
                                onValueChange = { 
                                    zoom3D = it
                                    renderer3D?.distance = it
                                    glSurface3D?.requestRender()
                                },
                                valueRange = 0.5f..15f,
                                modifier = Modifier
                                    .weight(1f)
                                    .width(120.dp)
                                    .graphicsLayer { rotationZ = -90f }
                            )
                            Icon(Icons.Default.ZoomOut, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                    
                    // Stats bar
                    trajectoryData?.let { data ->
                        TrajectoryStatsBar(data = data)
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Right side - 2D views
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top view (XZ)
                    TrajectoryView2D(
                        trajectoryData = trajectoryData,
                        viewProjection = ViewProjection.TOP,
                        label = "Top (XZ)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    
                    // Front view (XY)
                    TrajectoryView2D(
                        trajectoryData = trajectoryData,
                        viewProjection = ViewProjection.FRONT,
                        label = "Front (XY)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    
                    // Side view (YZ)
                    TrajectoryView2D(
                        trajectoryData = trajectoryData,
                        viewProjection = ViewProjection.SIDE,
                        label = "Side (YZ)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSelector(
    sessions: List<TrajectorySession>,
    selectedSession: TrajectorySession?,
    onSessionSelected: (TrajectorySession) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSession?.let { "${it.sessionName} (${it.algorithm ?: "unknown"})" } ?: "Select session",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sessions.forEach { session ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(session.sessionName, fontSize = 12.sp)
                            Text(
                                session.algorithm ?: "unknown",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSessionSelected(session)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TrajectoryView2D(
    trajectoryData: TrajectoryData?,
    viewProjection: ViewProjection,
    label: String,
    modifier: Modifier = Modifier
) {
    var renderer by remember { mutableStateOf<TrajectoryRenderer2D?>(null) }
    var glSurfaceView by remember { mutableStateOf<GLSurfaceView?>(null) }
    
    LaunchedEffect(trajectoryData) {
        trajectoryData?.let { data ->
            renderer?.setTrajectory(data)
            glSurfaceView?.requestRender()
        }
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    val r = TrajectoryRenderer2D(viewProjection)
                    renderer = r
                    setRenderer(r)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    
                    trajectoryData?.let { r.setTrajectory(it) }
                    
                    // Touch handling for pan/zoom
                    var lastX = 0f
                    var lastY = 0f
                    
                    val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            // Pinch out (spread) = zoom in = smaller view area
                            // Pinch in (pinch) = zoom out = larger view area
                            r.zoom /= detector.scaleFactor
                            r.zoom = r.zoom.coerceIn(0.1f, 5f)
                            requestRender()
                            return true
                        }
                    })
                    
                    setOnTouchListener { _, event ->
                        scaleDetector.onTouchEvent(event)
                        
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                lastX = event.x
                                lastY = event.y
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount == 1) {
                                    val dx = event.x - lastX
                                    val dy = event.y - lastY
                                    r.panX -= dx * 0.002f * r.zoom
                                    r.panY += dy * 0.002f * r.zoom
                                    lastX = event.x
                                    lastY = event.y
                                    requestRender()
                                }
                            }
                        }
                        true
                    }
                    
                    glSurfaceView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // View label
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 10.sp
            )
        }
        
        // Reset button for 2D views
        SmallFloatingActionButton(
            onClick = {
                renderer?.resetView()
                glSurfaceView?.requestRender()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun TrajectoryStatsBar(
    data: TrajectoryData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem("Poses", data.poseCount.toString())
            StatItem("Duration", String.format("%.1fs", data.duration))
            StatItem("Algorithm", data.algorithm ?: "unknown")
            StatItem("Scale", String.format("%.2fm", data.scale))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
