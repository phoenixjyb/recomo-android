package com.recomo.remotecontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import com.recomo.remotecontrol.camviewer.data.model.RobotProfile
import com.recomo.remotecontrol.PanelBox
import com.recomo.remotecontrol.SecondaryButton
import com.recomo.remotecontrol.preview.FilamentPreviewRenderer
import com.recomo.remotecontrol.preview.RobotKinematics
import com.recomo.remotecontrol.preview.RobotKinematicsConfig
import com.recomo.remotecontrol.preview.TrajectoryPreview
import com.recomo.remotecontrol.preview.urdf.UrdfCache
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TrajectoryPreviewOverlay(
    viewModel: RecomoControlViewModel,
    onClose: () -> Unit
) {
    val preview by viewModel.trajectoryPreview.collectAsState()
    val profile by viewModel.robotProfile.collectAsState()
    val library by viewModel.librarySummary.collectAsState()
    val availableMaps by viewModel.availableMaps.collectAsState()
    val selectedMap by viewModel.selectedMap.collectAsState()
    var mode by remember { mutableStateOf(PreviewMode.THREE_D) }
    var currentIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var fitTrigger by remember { mutableStateOf(0) }

    // Selector state
    var mapExpanded by remember { mutableStateOf(false) }
    var sessionExpanded by remember { mutableStateOf(false) }
    var sessionSearchQuery by remember { mutableStateOf("") }

    // Fetch maps on first open
    LaunchedEffect(Unit) {
        viewModel.fetchMapList()
    }

    LaunchedEffect(preview) {
        currentIndex = 0
    }

    LaunchedEffect(isPlaying, preview) {
        val previewState = preview ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        while (isPlaying) {
            val next = currentIndex + 1
            currentIndex = if (previewState.samples.isNotEmpty()) {
                if (next >= previewState.samples.size) 0 else next
            } else {
                0
            }
            kotlinx.coroutines.delay(33L)
        }
    }

    // Trajectory sessions (sorted newest first)
    val allSessions = remember(library) {
        library.foiSessions.sortedByDescending { it.sessionId }
    }
    val filteredSessions = remember(allSessions, sessionSearchQuery) {
        val q = sessionSearchQuery.trim()
        if (q.isBlank()) allSessions
        else allSessions.filter {
            it.sessionId.contains(q, ignoreCase = true) ||
                it.sessionName.contains(q, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1B1B1F))
                .border(1.dp, Color(0xFF3A3A3F), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            // ── Title bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trajectory Preview", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controlsVisible = !controlsVisible }) {
                        Text(if (controlsVisible) "HIDE UI" else "SHOW UI")
                    }
                    Button(onClick = onClose) { Text("CLOSE") }
                }
            }

            if (controlsVisible) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Selector row: Location + Trajectory ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location (map) dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        SecondaryButton(
                            text = selectedMap ?: "Select Location...",
                            modifier = Modifier.height(32.dp).fillMaxWidth()
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

                    // Trajectory session dropdown
                    Box(modifier = Modifier.weight(2f)) {
                        val loadedName = preview?.let { "Loaded" } ?: "Select Trajectory..."
                        SecondaryButton(
                            text = sessionSearchQuery.ifBlank { loadedName },
                            modifier = Modifier.height(32.dp).fillMaxWidth()
                        ) { sessionExpanded = true }
                        DropdownMenu(
                            expanded = sessionExpanded,
                            onDismissRequest = { sessionExpanded = false },
                            modifier = Modifier.widthIn(min = 300.dp, max = 500.dp).heightIn(max = 300.dp)
                        ) {
                            // Search field inside dropdown
                            androidx.compose.material3.OutlinedTextField(
                                value = sessionSearchQuery,
                                onValueChange = { sessionSearchQuery = it },
                                placeholder = { Text("Search sessions...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            if (filteredSessions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No sessions found") },
                                    onClick = { sessionExpanded = false },
                                    enabled = false
                                )
                            } else {
                                val scrollState = rememberScrollState()
                                Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(scrollState)) {
                                    filteredSessions.forEach { session ->
                                        val label = session.sessionName.ifBlank { session.sessionId }
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        label,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        "${session.count} frames • ${session.sessionId.take(8)}",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF888888)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                sessionExpanded = false
                                                sessionSearchQuery = label
                                                viewModel.loadSession(session.sessionId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Refresh button
                    Button(
                        onClick = { viewModel.fetchMapList() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) { Text("↻", fontSize = 14.sp) }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── Mode chips + playback controls ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewModeChip(label = "3D", active = mode == PreviewMode.THREE_D) {
                        mode = PreviewMode.THREE_D
                    }
                    PreviewModeChip(label = "2D", active = mode == PreviewMode.TWO_D) {
                        mode = PreviewMode.TWO_D
                    }
                    Button(
                        onClick = { fitTrigger += 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F))
                    ) { Text("FIT") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { isPlaying = !isPlaying },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F))
                    ) {
                        Text(if (isPlaying) "PAUSE" else "PLAY")
                    }
                }
            }

            // ── Timeline slider ──
            val previewState = preview
            if (controlsVisible && previewState != null && previewState.samples.isNotEmpty()) {
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { value ->
                        isPlaying = false
                        currentIndex = value.roundToInt()
                    },
                    valueRange = 0f..previewState.samples.lastIndex.toFloat(),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (controlsVisible) 6.dp else 6.dp))

            // ── 3D/2D view ──
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TrajectoryPreviewSurface(
                    preview = previewState,
                    profile = profile,
                    mode = mode,
                    currentIndex = currentIndex,
                    fitTrigger = fitTrigger
                )
            }
        }
    }
}

@Composable
fun TrajectoryPreviewPanel(
    viewModel: RecomoControlViewModel,
    panelsVisible: Boolean,
    onTogglePanels: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview by viewModel.trajectoryPreview.collectAsState()
    val profile by viewModel.robotProfile.collectAsState()
    var mode by remember { mutableStateOf(PreviewMode.THREE_D) }
    var currentIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var fitTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(preview) {
        currentIndex = 0
    }

    LaunchedEffect(isPlaying, preview) {
        val previewState = preview ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        while (isPlaying) {
            val next = currentIndex + 1
            currentIndex = if (previewState.samples.isNotEmpty()) {
                if (next >= previewState.samples.size) 0 else next
            } else {
                0
            }
            kotlinx.coroutines.delay(33L)
        }
    }

    PanelBox(
        title = "PREVIEW",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PreviewModeChip(label = "3D", active = mode == PreviewMode.THREE_D) {
                    mode = PreviewMode.THREE_D
                }
                PreviewModeChip(label = "2D", active = mode == PreviewMode.TWO_D) {
                    mode = PreviewMode.TWO_D
                }
                Button(
                    onClick = { fitTrigger += 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F))
                ) { Text("FIT") }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onExpand,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F))
                ) { Text("FULL") }
                Button(
                    onClick = onTogglePanels,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F))
                ) { Text(if (panelsVisible) "HIDE PANELS" else "SHOW PANELS") }
            }
            val previewState = preview
            if (previewState != null && previewState.samples.isNotEmpty()) {
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { value ->
                        isPlaying = false
                        currentIndex = value.roundToInt()
                    },
                    valueRange = 0f..previewState.samples.lastIndex.toFloat(),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { isPlaying = !isPlaying },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2F))
                ) { Text(if (isPlaying) "PAUSE" else "PLAY") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AspectPreviewBox {
                    TrajectoryPreviewSurface(
                        preview = previewState,
                        profile = profile,
                        mode = mode,
                        currentIndex = currentIndex,
                        fitTrigger = fitTrigger
                    )
                }
            }
        }
    }
}

@Composable
private fun TrajectoryPreviewSurface(
    preview: TrajectoryPreview?,
    profile: RobotProfile,
    mode: PreviewMode,
    currentIndex: Int,
    fitTrigger: Int
) {
    if (preview == null || preview.samples.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No trajectory loaded", color = Color(0xFFAAAAAA))
        }
        return
    }
    val clampedIndex = currentIndex.coerceIn(0, preview.samples.lastIndex)
    when (mode) {
        PreviewMode.TWO_D -> TrajectoryPreview2D(
            preview = preview,
            profile = profile,
            currentIndex = clampedIndex
        )
        PreviewMode.THREE_D -> TrajectoryPreview3D(
            preview = preview,
            profile = profile,
            currentIndex = clampedIndex,
            fitTrigger = fitTrigger
        )
    }
}

@Composable
private fun AspectPreviewBox(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val targetRatio = 16f / 9f
        val widthRatio = maxWidth / maxHeight
        val boxModifier = if (widthRatio > targetRatio) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(targetRatio)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(targetRatio)
        }
        Box(modifier = boxModifier) { content() }
    }
}

@Composable
private fun PreviewModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Color(0xFFF5C451) else Color(0xFF3A3A3F)
    val textColor = if (active) Color.Black else Color.White
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) { Text(label, color = textColor) }
}

@Composable
private fun TrajectoryPreview2D(
    preview: TrajectoryPreview,
    profile: RobotProfile,
    currentIndex: Int
) {
    val context = LocalContext.current
    val config = RobotKinematics.configForProfile(profile)
    val model = remember(profile) {
        runCatching { UrdfCache.get(context, config.urdfAsset) }.getOrNull()
    }

    val basePath = remember(preview) {
        preview.samples.map { Offset(it.baseX.toFloat(), it.baseY.toFloat()) }
    }
    val eePath = remember(preview) {
        if (model == null) {
            emptyList()
        } else {
            preview.samples.map { sample ->
                RobotKinematics.computeCameraPose(model, config, sample)?.position?.let {
                    Offset(it.x.toFloat(), it.y.toFloat())
                }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF101014))) {
        val padding = 24.dp.toPx()
        val basePathPoints = basePath.filter { it.x.isFinite() && it.y.isFinite() }
        val eePathPoints = eePath.filterNotNull().filter { it.x.isFinite() && it.y.isFinite() }
        val allPoints = basePathPoints + eePathPoints
        if (allPoints.isEmpty()) return@Canvas
        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }
        val spanX = max(0.1f, maxX - minX)
        val spanY = max(0.1f, maxY - minY)

        val scale = min(
            (size.width - 2 * padding) / spanX,
            (size.height - 2 * padding) / spanY
        )

        fun mapPoint(p: Offset): Offset {
            val x = (p.x - minX) * scale + padding
            val y = (p.y - minY) * scale + padding
            return Offset(x, size.height - y)
        }

        drawPath(
            path = buildPath(basePathPoints, ::mapPoint),
            color = Color(0xFFF5C451),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (eePathPoints.isNotEmpty()) {
            drawPath(
                path = buildPath(eePathPoints, ::mapPoint),
                color = Color(0xFF66D2FF),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        preview.keyframes.forEach { frame ->
            val x = frame.baseX.toFloat()
            val y = frame.baseY.toFloat()
            if (x.isFinite() && y.isFinite()) {
                val mapped = mapPoint(Offset(x, y))
                drawCircle(Color(0xFFFFD27A), radius = 5.dp.toPx(), center = mapped)
            }
        }

        val currentBase = basePath.getOrNull(currentIndex)?.takeIf { it.x.isFinite() && it.y.isFinite() }?.let(::mapPoint)
        if (currentBase != null) {
            drawCircle(Color(0xFFFF4D4D), radius = 7.dp.toPx(), center = currentBase)
        }
        val currentEe = eePath.getOrNull(currentIndex)
            ?.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?.let(::mapPoint)
        if (currentEe != null) {
            drawCircle(Color(0xFF6FE3FF), radius = 6.dp.toPx(), center = currentEe)
        }
    }
}

private fun buildPath(points: List<Offset>, mapper: (Offset) -> Offset): Path {
    val path = Path()
    points.firstOrNull()?.let { start ->
        val mapped = mapper(start)
        path.moveTo(mapped.x, mapped.y)
    }
    points.drop(1).forEach { point ->
        val mapped = mapper(point)
        path.lineTo(mapped.x, mapped.y)
    }
    return path
}

@Composable
private fun TrajectoryPreview3D(
    preview: TrajectoryPreview,
    profile: RobotProfile,
    currentIndex: Int,
    fitTrigger: Int
) {
    val context = LocalContext.current
    val renderer = remember { FilamentPreviewRenderer(context) }
    val config = RobotKinematics.configForProfile(profile)

    var orbitYaw by remember { mutableStateOf(0.6) }
    var orbitPitch by remember { mutableStateOf(0.4) }
    var orbitDistance by remember { mutableStateOf(3.5) }
    var panX by remember { mutableStateOf(0.0) }
    var panY by remember { mutableStateOf(0.0) }
    var avatarVisible by remember { mutableStateOf(true) }
    var interactMode by remember { mutableStateOf(false) }
    var selectedObject by remember { mutableStateOf<String?>(null) }

    // Default orbit for RESET
    val defaultYaw = 0.6
    val defaultPitch = 0.4
    val defaultDistance = 3.5

    // Pan step size scales with zoom level
    val panStep = (orbitDistance * 0.15).coerceIn(0.1, 1.5)
    val rotateStep = Math.toRadians(15.0) // 15° per tap

    DisposableEffect(Unit) {
        onDispose { renderer.destroy() }
    }

    LaunchedEffect(preview, profile) {
        renderer.setPreview(preview, config)
    }

    LaunchedEffect(currentIndex) {
        renderer.setCurrentIndex(currentIndex)
    }

    LaunchedEffect(fitTrigger) {
        renderer.fitCameraToPreview()
    }

    LaunchedEffect(orbitYaw, orbitPitch, orbitDistance, panX, panY) {
        renderer.setCameraOrbit(orbitYaw, orbitPitch, orbitDistance, panX, panY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── 3D surface with unified gesture handler ──
        // Single pointerInput block handles all gestures via awaitEachGesture:
        //   1 finger: orbit camera (or drag selected object in interact mode)
        //   2 fingers: pinch zoom
        //   3+ fingers: pan scene
        //   Short tap (interact mode): select/deselect object

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(interactMode, selectedObject) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downPos = firstDown.position
                        var prevCentroid = firstDown.position
                        var prevSpread = 0f
                        var maxPointers = 1
                        var totalSingleDrag = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val count = pressed.size
                            if (count > maxPointers) maxPointers = count

                            if (count >= 1) {
                                // Compute centroid of all pressed pointers
                                val centroid = Offset(
                                    pressed.map { it.position.x }.average().toFloat(),
                                    pressed.map { it.position.y }.average().toFloat()
                                )
                                val delta = centroid - prevCentroid

                                when {
                                    count >= 3 -> {
                                        // 3+ fingers: pan scene
                                        val panScale = orbitDistance * 0.002
                                        val cosY = kotlin.math.cos(orbitYaw).toFloat()
                                        val sinY = kotlin.math.sin(orbitYaw).toFloat()
                                        panX -= (delta.x * panScale * cosY - delta.y * panScale * sinY)
                                        panY -= (delta.x * panScale * sinY + delta.y * panScale * cosY)
                                    }
                                    count == 2 -> {
                                        // 2 fingers: pinch zoom only
                                        val spread = (pressed[0].position - pressed[1].position).getDistance()
                                        if (prevSpread > 10f) {
                                            val zoomFactor = spread / prevSpread
                                            orbitDistance = (orbitDistance / zoomFactor).coerceIn(0.8, 20.0)
                                        }
                                        prevSpread = spread
                                    }
                                    count == 1 && maxPointers == 1 -> {
                                        // Single finger only (never went multi-touch)
                                        totalSingleDrag += delta
                                        if (interactMode && selectedObject != null) {
                                            // Drag selected object on ground plane
                                            renderer.moveObjectByScreenDelta(
                                                selectedObject!!, delta.x, delta.y,
                                                size.width, size.height
                                            )
                                        } else {
                                            // Orbit camera
                                            orbitYaw += delta.x * 0.005
                                            orbitPitch = (orbitPitch - delta.y * 0.005).coerceIn(-1.4, 1.4)
                                        }
                                    }
                                }

                                prevCentroid = centroid
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        // On gesture end: check for tap (short press, minimal movement)
                        val dragDist = totalSingleDrag.getDistance()
                        android.util.Log.d("Preview3D", "gesture end: maxPtrs=$maxPointers dragDist=${"%.1f".format(dragDist)} interact=$interactMode size=${size.width}x${size.height}")
                        if (maxPointers == 1 && dragDist < 30f && interactMode) {
                            val hit = renderer.hitTestAt(
                                downPos.x, downPos.y, size.width, size.height
                            )
                            android.util.Log.d("Preview3D", "tap hitTest result: $hit")
                            selectedObject = hit // null = deselect
                        }
                    }
                },
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    renderer.initialize(this)
                }
            }
        )

        // ── Selection indicator (top-center) ──
        if (interactMode && selectedObject != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(Color(0xAA2A2A2F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${selectedObject!!.replaceFirstChar { it.uppercase() }}",
                    color = Color(0xFFF5C451),
                    fontSize = 12.sp
                )
                // Rotate left
                PreviewIconButton(label = "↺", size = 32) {
                    renderer.rotateObject(selectedObject!!, -rotateStep)
                }
                // Rotate right
                PreviewIconButton(label = "↻", size = 32) {
                    renderer.rotateObject(selectedObject!!, rotateStep)
                }
                // Deselect
                PreviewIconButton(label = "✕", size = 32) {
                    selectedObject = null
                }
            }
        }

        // ── D-pad pan buttons (bottom-left) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .size(130.dp)
        ) {
            val cosY = kotlin.math.cos(orbitYaw)
            val sinY = kotlin.math.sin(orbitYaw)

            // Up (screen up → forward in scene)
            PreviewIconButton(
                label = "▲",
                size = 36,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                panX += sinY * panStep
                panY -= cosY * panStep
            }
            // Down
            PreviewIconButton(
                label = "▼",
                size = 36,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                panX -= sinY * panStep
                panY += cosY * panStep
            }
            // Left
            PreviewIconButton(
                label = "◀",
                size = 36,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                panX += cosY * panStep
                panY += sinY * panStep
            }
            // Right
            PreviewIconButton(
                label = "▶",
                size = 36,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                panX -= cosY * panStep
                panY -= sinY * panStep
            }

            // Gesture hint (center of d-pad, very subtle)
            Text(
                text = "PAN",
                color = Color(0x44FFFFFF),
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ── Right-side control buttons ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Zoom in
            PreviewIconButton(label = "+") {
                orbitDistance = (orbitDistance * 0.8).coerceIn(0.8, 20.0)
            }
            // Zoom out
            PreviewIconButton(label = "−") {
                orbitDistance = (orbitDistance * 1.25).coerceIn(0.8, 20.0)
            }
            // Reset camera + object positions
            PreviewIconButton(label = "⌂") {
                orbitYaw = defaultYaw
                orbitPitch = defaultPitch
                orbitDistance = defaultDistance
                panX = 0.0
                panY = 0.0
                selectedObject = null
                renderer.resetObjectOffsets()
                renderer.fitCameraToPreview()
            }
            // Toggle avatar
            PreviewIconButton(
                label = "👤",
                active = avatarVisible
            ) {
                avatarVisible = !avatarVisible
                renderer.setAvatarVisible(avatarVisible)
            }
            // Toggle interact mode (pick & move objects)
            PreviewIconButton(
                label = "✋",
                active = interactMode
            ) {
                interactMode = !interactMode
                if (!interactMode) selectedObject = null
            }
        }

        // Gesture hint (bottom-center, subtle)
        Text(
            text = "1-finger: orbit • 2-finger: zoom • 3-finger: pan",
            color = Color(0x44FFFFFF),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun PreviewIconButton(
    label: String,
    active: Boolean = true,
    size: Int = 40,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (active) Color(0xAA2A2A2F) else Color(0x442A2A2F)
    val textColor = if (active) Color.White else Color(0xFF888888)
    val fontSize = if (size <= 32) 14.sp else 18.sp
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(label, color = textColor, fontSize = fontSize, textAlign = TextAlign.Center)
        }
    }
}

private enum class PreviewMode {
    TWO_D,
    THREE_D
}
