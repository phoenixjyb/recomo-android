package com.recomo.user.ui.screens.preview

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.common.preview.FilamentPreviewRenderer
import com.recomo.common.preview.RobotKinematics
import com.recomo.common.preview.RobotKinematicsConfig
import com.recomo.common.preview.TrajectoryPreview
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Standalone trajectory 3D preview overlay for the user app.
 *
 * Takes a [TrajectoryPreview] directly (no ViewModel dependency).
 * Shows robot + avatar, playback controls, orbit/zoom/pan gestures,
 * interact mode for pick-and-move, and D-pad pan.
 */
@Composable
fun TrajectoryPreviewScreen(
    preview: TrajectoryPreview,
    title: String = "Trajectory Preview",
    frameSummary: String? = null,
    executeLabel: String = "RUN",
    config: RobotKinematicsConfig = RobotKinematics.defaultConfig(),
    onClose: () -> Unit,
    onExecute: (() -> Unit)? = null
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, preview) {
        if (!isPlaying) return@LaunchedEffect
        while (isPlaying) {
            val next = currentIndex + 1
            currentIndex = if (preview.samples.isNotEmpty()) {
                if (next >= preview.samples.size) 0 else next
            } else 0
            delay(33L)
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
            // Title bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        frameSummary ?: "${preview.samples.size} frames",
                        color = Color(0xFF888888), fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onExecute != null) {
                        Button(
                            onClick = onExecute,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF))
                        ) { Text(executeLabel) }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3D View
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Preview3DView(preview = preview, config = config, currentIndex = currentIndex)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
                if (preview.samples.isNotEmpty()) {
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { isPlaying = false; currentIndex = it.roundToInt() },
                        valueRange = 0f..(preview.samples.size - 1).toFloat().coerceAtLeast(0f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${currentIndex + 1}/${preview.samples.size}",
                        color = Color(0xFF888888), fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Preview3DView(
    preview: TrajectoryPreview,
    config: RobotKinematicsConfig,
    currentIndex: Int
) {
    val context = LocalContext.current
    val renderer = remember { FilamentPreviewRenderer(context) }

    var orbitYaw by remember { mutableStateOf(0.6) }
    var orbitPitch by remember { mutableStateOf(0.4) }
    var orbitDistance by remember { mutableStateOf(3.5) }
    var panX by remember { mutableStateOf(0.0) }
    var panY by remember { mutableStateOf(0.0) }
    var avatarVisible by remember { mutableStateOf(true) }
    var interactMode by remember { mutableStateOf(false) }
    var selectedObject by remember { mutableStateOf<String?>(null) }

    val panStep = (orbitDistance * 0.15).coerceIn(0.1, 1.5)
    val rotateStep = Math.toRadians(15.0)

    DisposableEffect(Unit) { onDispose { renderer.destroy() } }

    LaunchedEffect(preview, config) { renderer.setPreview(preview, config) }
    LaunchedEffect(currentIndex) { renderer.setCurrentIndex(currentIndex) }
    LaunchedEffect(orbitYaw, orbitPitch, orbitDistance, panX, panY) {
        renderer.setCameraOrbit(orbitYaw, orbitPitch, orbitDistance, panX, panY)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                                val centroid = Offset(
                                    pressed.map { it.position.x }.average().toFloat(),
                                    pressed.map { it.position.y }.average().toFloat()
                                )
                                val delta = centroid - prevCentroid

                                when {
                                    count >= 3 -> {
                                        val panScale = orbitDistance * 0.002
                                        val cosY = cos(orbitYaw).toFloat()
                                        val sinY = sin(orbitYaw).toFloat()
                                        panX -= (delta.x * panScale * cosY - delta.y * panScale * sinY)
                                        panY -= (delta.x * panScale * sinY + delta.y * panScale * cosY)
                                    }
                                    count == 2 -> {
                                        val spread = (pressed[0].position - pressed[1].position).getDistance()
                                        if (prevSpread > 10f) {
                                            val zoomFactor = spread / prevSpread
                                            orbitDistance = (orbitDistance / zoomFactor).coerceIn(0.8, 20.0)
                                        }
                                        prevSpread = spread
                                    }
                                    count == 1 && maxPointers == 1 -> {
                                        totalSingleDrag += delta
                                        if (interactMode && selectedObject != null) {
                                            renderer.moveObjectByScreenDelta(
                                                selectedObject!!, delta.x, delta.y, size.width, size.height
                                            )
                                        } else {
                                            orbitYaw += delta.x * 0.005
                                            orbitPitch = (orbitPitch - delta.y * 0.005).coerceIn(-1.4, 1.4)
                                        }
                                    }
                                }
                                prevCentroid = centroid
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        if (maxPointers == 1 && totalSingleDrag.getDistance() < 30f && interactMode) {
                            selectedObject = renderer.hitTestAt(downPos.x, downPos.y, size.width, size.height)
                        }
                    }
                },
            factory = { ctx -> SurfaceView(ctx).apply { renderer.initialize(this) } }
        )

        // Selection indicator
        if (interactMode && selectedObject != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter).padding(top = 8.dp)
                    .background(Color(0xAA2A2A2F), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selected: ${selectedObject!!.replaceFirstChar { it.uppercase() }}", color = Color(0xFFF5C451), fontSize = 12.sp)
                SmallBtn("↺") { renderer.rotateObject(selectedObject!!, -rotateStep) }
                SmallBtn("↻") { renderer.rotateObject(selectedObject!!, rotateStep) }
                SmallBtn("✕") { selectedObject = null }
            }
        }

        // D-pad (bottom-left)
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(130.dp)) {
            val cosY = cos(orbitYaw); val sinY = sin(orbitYaw)
            SmallBtn("▲", 36, Modifier.align(Alignment.TopCenter)) { panX += sinY * panStep; panY -= cosY * panStep }
            SmallBtn("▼", 36, Modifier.align(Alignment.BottomCenter)) { panX -= sinY * panStep; panY += cosY * panStep }
            SmallBtn("◀", 36, Modifier.align(Alignment.CenterStart)) { panX += cosY * panStep; panY += sinY * panStep }
            SmallBtn("▶", 36, Modifier.align(Alignment.CenterEnd)) { panX -= cosY * panStep; panY -= sinY * panStep }
            Text("PAN", color = Color(0x44FFFFFF), fontSize = 8.sp, modifier = Modifier.align(Alignment.Center))
        }

        // Right controls
        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("+") { orbitDistance = (orbitDistance * 0.8).coerceIn(0.8, 20.0) }
            SmallBtn("−") { orbitDistance = (orbitDistance * 1.25).coerceIn(0.8, 20.0) }
            SmallBtn("⌂") {
                orbitYaw = 0.6; orbitPitch = 0.4; orbitDistance = 3.5; panX = 0.0; panY = 0.0
                selectedObject = null; renderer.resetObjectOffsets(); renderer.fitCameraToPreview()
            }
            SmallBtn("👤", active = avatarVisible) { avatarVisible = !avatarVisible; renderer.setAvatarVisible(avatarVisible) }
            SmallBtn("✋", active = interactMode) { interactMode = !interactMode; if (!interactMode) selectedObject = null }
        }

        Text(
            "1-finger: orbit • 2-finger: zoom • 3-finger: pan",
            color = Color(0x44FFFFFF), fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun SmallBtn(
    label: String, size: Int = 40, modifier: Modifier = Modifier, active: Boolean = true, onClick: () -> Unit
) {
    val bg = if (active) Color(0xAA2A2A2F) else Color(0x442A2A2F)
    val textColor = if (active) Color.White else Color(0xFF888888)
    val fontSize = if (size <= 32) 14.sp else 18.sp
    Box(modifier = modifier.size(size.dp).clip(CircleShape).background(bg).padding(2.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick, modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = CircleShape, contentPadding = PaddingValues(0.dp)
        ) { Text(label, color = textColor, fontSize = fontSize, textAlign = TextAlign.Center) }
    }
}
