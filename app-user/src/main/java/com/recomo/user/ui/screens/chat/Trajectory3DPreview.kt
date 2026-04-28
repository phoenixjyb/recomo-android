package com.recomo.user.ui.screens.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.common.chat.SubjectStand
import com.recomo.user.ui.theme.StudioChrome
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ════════════════════════════════════════════════════════════════════
// Interactive 3D Trajectory Preview
//
// Lightweight Compose Canvas renderer with orbit camera. Renders the
// camera trajectory path, subject stand position, and home/start
// position in a simple 3D view with pan/zoom/rotate touch gestures.
// No Filament, no OpenGL — pure software projection on Canvas.
// ════════════════════════════════════════════════════════════════════

// ── View angle presets ──────────────────────────────────────────

private data class ViewPreset(
    val label: String,
    val azimuth: Float,
    val elevation: Float
)

private val VIEW_PRESETS = listOf(
    ViewPreset("3D",     -45f,  35f),
    ViewPreset("Top",      0f,  89f),
    ViewPreset("Front",    0f,   5f),
    ViewPreset("Back",   180f,   5f),
    ViewPreset("Left",   -90f,   5f),
    ViewPreset("Right",   90f,   5f),
)

/**
 * Full-size interactive 3D trajectory preview. Supports:
 * - One-finger drag: orbit camera (rotate around center)
 * - Pinch: zoom in/out
 * - Two-finger drag: pan the view target
 *
 * @param tumText Raw TUM trajectory text (timestamp x y z qx qy qz qw per line)
 * @param subjectStand Subject position relative to robot (from enriched response).
 *   When null, a synthetic subject is placed ~2.5m in front of trajectory start.
 * @param candidateName Display name for the header
 * @param onDismiss Callback to close the preview
 */
@Composable
fun Trajectory3DPreview(
    tumText: String,
    subjectStand: SubjectStand?,
    candidateName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember(tumText) { parseTumPoints3D(tumText) }
    if (points.size < 2) return

    val sceneBounds = remember(points) { computeSceneBounds(points) }

    // Camera state: orbit angles + zoom + pan offset
    var azimuth by remember { mutableFloatStateOf(-45f) }
    var elevation by remember { mutableFloatStateOf(35f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Effective subject: real data or synthetic fallback
    val effectiveSubject = remember(subjectStand, points) {
        subjectStand ?: synthesizeSubject(points)
    }
    val isSubjectSynthetic = subjectStand == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
    ) {
        // Header bar
        PreviewHeader(
            candidateName = candidateName,
            subjectStand = subjectStand,
            onDismiss = onDismiss
        )

        // Quick view angle buttons
        ViewAngleBar(
            onPreset = { preset ->
                azimuth = preset.azimuth
                elevation = preset.elevation
                panX = 0f
                panY = 0f
                zoom = 1f
            }
        )

        // 3D Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.3f, 5f)
                            panX += pan.x * 0.5f
                            panY += pan.y * 0.5f
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            azimuth -= dragAmount.x * 0.3f
                            elevation = (elevation - dragAmount.y * 0.3f).coerceIn(-85f, 85f)
                        }
                    }
            ) {
                drawScene(
                    points = points,
                    bounds = sceneBounds,
                    effectiveSubject = effectiveSubject,
                    isSubjectSynthetic = isSubjectSynthetic,
                    azimuth = azimuth,
                    elevation = elevation,
                    zoom = zoom,
                    panX = panX,
                    panY = panY
                )
            }

            // Legend overlay (bottom-left)
            PreviewLegend(
                isSubjectSynthetic = isSubjectSynthetic,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

// ── Synthesize subject when cloud doesn't send one ──────────────

@Suppress("UNUSED_PARAMETER")
private fun synthesizeSubject(points: List<Vec3>): SubjectStand {
    // Place subject ~2.5m "in front" of trajectory start, using the
    // initial direction of travel to define "forward".
    return SubjectStand(
        forwardM = -2.5,  // negative = in front of camera
        leftM = 0.0,
        distanceM = 2.5,
        bearingDeg = 0.0,
        confident = false,
        instruction = "Estimated position (no cloud data)"
    )
}

// ── Header ──────────────────────────────────────────────────────

@Composable
private fun PreviewHeader(
    candidateName: String,
    subjectStand: SubjectStand?,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF12121A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "3D Preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioChrome.textStrong,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = candidateName,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioChrome.textTertiary,
                    maxLines = 1
                )
            }
            if (subjectStand != null && subjectStand.instruction.isNotBlank()) {
                Surface(
                    color = Color(0x1A4CAF50),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = subjectStand.instruction,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF81C784),
                        maxLines = 2
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = StudioChrome.textSecondary
                )
            }
        }
    }
}

// ── View Angle Bar ──────────────────────────────────────────────

@Composable
private fun ViewAngleBar(
    onPreset: (ViewPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0E0E16),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "View:",
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textMuted,
                fontSize = 10.sp
            )
            VIEW_PRESETS.forEach { preset ->
                Surface(
                    onClick = { onPreset(preset) },
                    color = Color(0xFF1A1A28),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFF2A2A3A)
                    )
                ) {
                    Text(
                        text = preset.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioChrome.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ── Legend ───────────────────────────────────────────────────────

@Composable
private fun PreviewLegend(
    isSubjectSynthetic: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xAA12121A),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LegendItem(StartColor, "Home / Start")
            LegendItem(EndColor, "End")
            LegendItem(PathStartColor, "Trajectory")
            LegendItem(
                SubjectColor,
                if (isSubjectSynthetic) "Subject (est.)" else "Subject"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem(AxisXColor, "X")
                LegendItem(AxisYColor, "Y")
                LegendItem(AxisZColor, "Z")
            }
            Text(
                text = "Drag: rotate  Pinch: zoom",
                style = MaterialTheme.typography.labelSmall,
                color = StudioChrome.textMuted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = StudioChrome.textSecondary,
            fontSize = 10.sp
        )
    }
}

// ── 3D Math ─────────────────────────────────────────────────────

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun length() = sqrt(x * x + y * y + z * z)
}

private data class SceneBounds(
    val center: Vec3,
    val extent: Float  // half-diagonal of bounding box
)

private fun computeSceneBounds(pts: List<Vec3>): SceneBounds {
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    for (p in pts) {
        if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
    }
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    val cz = (minZ + maxZ) / 2f
    val dx = maxX - minX; val dy = maxY - minY; val dz = maxZ - minZ
    val extent = max(0.5f, sqrt(dx * dx + dy * dy + dz * dz) / 2f)
    return SceneBounds(Vec3(cx, cy, cz), extent)
}

/**
 * Project a 3D point to 2D screen coordinates using an orbiting camera.
 */
private fun projectPoint(
    point: Vec3,
    target: Vec3,
    azimuth: Float,
    elevation: Float,
    radius: Float,
    screenW: Float,
    screenH: Float,
    panX: Float,
    panY: Float
): Offset? {
    val azRad = azimuth * (PI.toFloat() / 180f)
    val elRad = elevation * (PI.toFloat() / 180f)

    // Camera position in world space (orbiting around target)
    val camX = target.x + radius * cos(elRad) * sin(azRad)
    val camY = target.y + radius * sin(elRad)
    val camZ = target.z + radius * cos(elRad) * cos(azRad)

    // Forward (camera looks at target)
    val fwdX = target.x - camX
    val fwdY = target.y - camY
    val fwdZ = target.z - camZ
    val fwdLen = sqrt(fwdX * fwdX + fwdY * fwdY + fwdZ * fwdZ)
    if (fwdLen < 1e-6f) return null
    val fx = fwdX / fwdLen; val fy = fwdY / fwdLen; val fz = fwdZ / fwdLen

    // Right = cross(forward, worldUp(0,1,0))
    var rx = fz; var ry = 0f; var rz = -fx
    val rLen = sqrt(rx * rx + ry * ry + rz * rz)
    if (rLen < 1e-6f) { rx = 1f; ry = 0f; rz = 0f } else { rx /= rLen; ry /= rLen; rz /= rLen }

    // Up = cross(right, forward)
    val ux = ry * fz - rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy - ry * fx

    // Transform to camera space
    val dx = point.x - camX; val dy = point.y - camY; val dz = point.z - camZ
    val cx = rx * dx + ry * dy + rz * dz
    val cy = ux * dx + uy * dy + uz * dz
    val cz = fx * dx + fy * dy + fz * dz  // depth

    if (cz < 0.01f) return null

    val fov = 60f * (PI.toFloat() / 180f)
    val scale = 1f / kotlin.math.tan(fov / 2f)
    val aspect = screenW / screenH.coerceAtLeast(1f)

    val ndcX = (scale / aspect) * (cx / cz)
    val ndcY = scale * (cy / cz)

    return Offset(
        screenW / 2f + ndcX * (screenW / 2f) + panX,
        screenH / 2f - ndcY * (screenH / 2f) + panY
    )
}

// ── Scene Drawing ───────────────────────────────────────────────

private fun DrawScope.drawScene(
    points: List<Vec3>,
    bounds: SceneBounds,
    effectiveSubject: SubjectStand,
    isSubjectSynthetic: Boolean,
    azimuth: Float,
    elevation: Float,
    zoom: Float,
    panX: Float,
    panY: Float
) {
    val w = size.width
    val h = size.height
    val radius = bounds.extent * 3f / zoom
    val target = bounds.center

    fun project(p: Vec3): Offset? =
        projectPoint(p, target, azimuth, elevation, radius, w, h, panX, panY)

    // ── 1. Ground grid ──────────────────────────────────────────
    drawGroundGrid(bounds, target, azimuth, elevation, radius, w, h, panX, panY)

    // ── 2. XYZ Axis gizmo (fixed in bottom-right corner) ──────
    drawAxisGizmo(azimuth, elevation, w, h)

    // ── 3. Subject marker (draw before trajectory so path draws on top) ──
    drawSubjectMarker(
        points = points,
        subject = effectiveSubject,
        isSynthetic = isSubjectSynthetic,
        project = ::project
    )

    // ── 4. Trajectory path with time gradient ───────────────────
    val n = points.size
    for (i in 0 until n - 1) {
        val a = project(points[i]) ?: continue
        val b = project(points[i + 1]) ?: continue
        val t = i.toFloat() / (n - 1).coerceAtLeast(1)
        val color = lerp(PathStartColor, PathEndColor, t)
        drawLine(color, a, b, strokeWidth = 3f, cap = StrokeCap.Round)
    }

    // Waypoint dots (every ~5% of path)
    val step = max(1, n / 20)
    for (i in step until n - 1 step step) {
        val pt = project(points[i]) ?: continue
        drawCircle(Color(0x55FFFFFF), radius = 2.5f, center = pt)
    }

    // ── 5. Start marker (green bullseye) ────────────────────────
    val startPt = points.first()
    project(startPt)?.let { start ->
        drawCircle(StartColor, radius = 12f, center = start)
        drawCircle(Color(0xFF0A0A12), radius = 7f, center = start)
        drawCircle(StartColor, radius = 4f, center = start)

        // Forward direction arrow
        if (points.size > 5) {
            project(points[5])?.let { fwd ->
                val dx = fwd.x - start.x
                val dy = fwd.y - start.y
                val dLen = sqrt(dx * dx + dy * dy)
                if (dLen > 5f) {
                    val nx = dx / dLen * 24f
                    val ny = dy / dLen * 24f
                    drawLine(
                        StartColor.copy(alpha = 0.7f),
                        start,
                        Offset(start.x + nx, start.y + ny),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // "HOME" label + coordinates
        drawCoordBadge(
            label = "HOME",
            pos = startPt,
            screenPos = start,
            accentArgb = 0xFF4CAF50,
            offsetY = -18f
        )
    }

    // ── 6. End marker (red) ─────────────────────────────────────
    val endPt = points.last()
    project(endPt)?.let { end ->
        drawCircle(EndColor, radius = 9f, center = end)
        drawCircle(Color(0xFF0A0A12), radius = 5f, center = end)
        drawCircle(EndColor, radius = 3f, center = end)

        drawCoordBadge(
            label = "END",
            pos = endPt,
            screenPos = end,
            accentArgb = 0xFFEF5350,
            offsetY = -14f
        )
    }
}

// ── XYZ Axis Gizmo (screen-space corner indicator) ──────────────

/**
 * Draws a fixed-size orientation gizmo in the bottom-right corner.
 * The gizmo rotates with the camera so the user always knows which
 * direction X/Y/Z point in the current view.
 */
private fun DrawScope.drawAxisGizmo(
    azimuth: Float,
    elevation: Float,
    screenW: Float,
    screenH: Float
) {
    val azRad = azimuth * (PI.toFloat() / 180f)
    val elRad = elevation * (PI.toFloat() / 180f)

    // Camera basis vectors (same math as projectPoint but we only need the rotation)
    val cosEl = cos(elRad); val sinEl = sin(elRad)
    val cosAz = cos(azRad); val sinAz = sin(azRad)

    // Forward = normalize(target - cam), but for the gizmo we just need the rotation
    // Camera looks at -forward direction in orbit mode:
    //   forward = (-cosEl*sinAz, -sinEl, -cosEl*cosAz)
    val fx = -cosEl * sinAz; val fy = -sinEl; val fz = -cosEl * cosAz

    // Right = cross(forward, worldUp(0,1,0))
    var rx = fz; var ry = 0f; var rz = -fx
    val rLen = sqrt(rx * rx + ry * ry + rz * rz)
    if (rLen > 1e-6f) { rx /= rLen; ry /= rLen; rz /= rLen }

    // Up = cross(right, forward)
    val ux = ry * fz - rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy - ry * fx

    // Gizmo center: bottom-right corner with padding
    val gizmoRadius = 40f
    val cx = screenW - 60f
    val cy = screenH - 60f

    // Project each unit axis direction to screen-space offset
    // For a unit vector (ax, ay, az), its screen projection is:
    //   screenX = dot(axis, right) * gizmoRadius
    //   screenY = -dot(axis, up) * gizmoRadius  (flip Y for screen coords)

    data class AxisDef(val dir: Vec3, val color: Color, val label: String, val labelArgb: Long)
    val axes = listOf(
        AxisDef(Vec3(1f, 0f, 0f), AxisXColor, "X", 0xFFFF5252),
        AxisDef(Vec3(0f, 1f, 0f), AxisYColor, "Y", 0xFF69F0AE),
        AxisDef(Vec3(0f, 0f, 1f), AxisZColor, "Z", 0xFF448AFF),
    )

    // Background circle
    drawCircle(Color(0x33000000), radius = gizmoRadius + 8f, center = Offset(cx, cy))
    drawCircle(Color(0x22FFFFFF), radius = gizmoRadius + 8f, center = Offset(cx, cy), style = Stroke(1f))

    // Sort axes by depth (draw back-to-front)
    val sorted = axes.sortedBy { a ->
        // Depth = dot(axis, forward) — more positive = further from camera
        a.dir.x * fx + a.dir.y * fy + a.dir.z * fz
    }

    for (axis in sorted) {
        val dotR = axis.dir.x * rx + axis.dir.y * ry + axis.dir.z * rz
        val dotU = axis.dir.x * ux + axis.dir.y * uy + axis.dir.z * uz
        val tipX = cx + dotR * gizmoRadius
        val tipY = cy - dotU * gizmoRadius

        // Axis line
        drawLine(
            axis.color,
            Offset(cx, cy),
            Offset(tipX, tipY),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        // Tip dot
        drawCircle(axis.color, radius = 4f, center = Offset(tipX, tipY))
        // Label
        drawContext.canvas.nativeCanvas.drawText(
            axis.label,
            tipX + 6f,
            tipY - 6f,
            axisLabelPaint(axis.labelArgb)
        )
    }
}

// ── Subject Marker ──────────────────────────────────────────────

private fun DrawScope.drawSubjectMarker(
    points: List<Vec3>,
    subject: SubjectStand,
    isSynthetic: Boolean,
    project: (Vec3) -> Offset?
) {
    val startPt = points.first()

    // Estimate forward direction from initial trajectory motion
    val fwdDir = if (points.size > 3) {
        val dx = points[3].x - startPt.x
        val dz = points[3].z - startPt.z
        val len = sqrt(dx * dx + dz * dz)
        if (len > 0.01f) Vec3(dx / len, 0f, dz / len) else Vec3(0f, 0f, -1f)
    } else Vec3(0f, 0f, -1f)

    // Left = perpendicular to forward in XZ plane
    val leftDir = Vec3(-fwdDir.z, 0f, fwdDir.x)

    // forward_m is negative (in front of camera), so negate to get world offset
    val subjectPos = Vec3(
        startPt.x + fwdDir.x * (-subject.forwardM.toFloat()) + leftDir.x * subject.leftM.toFloat(),
        startPt.y,
        startPt.z + fwdDir.z * (-subject.forwardM.toFloat()) + leftDir.z * subject.leftM.toFloat()
    )

    val subjScreen = project(subjectPos) ?: return
    val startScreen = project(startPt)

    // Dashed line from home to subject
    if (startScreen != null) {
        drawLine(
            SubjectColor.copy(alpha = 0.35f),
            startScreen,
            subjScreen,
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    }

    // Subject ground circle (larger ring for visibility)
    val subjectAlpha = if (isSynthetic) 0.5f else 1f
    val ringColor = SubjectColor.copy(alpha = subjectAlpha)
    drawCircle(ringColor, radius = 18f, center = subjScreen, style = Stroke(width = 3f))
    drawCircle(SubjectColor.copy(alpha = 0.15f * subjectAlpha), radius = 18f, center = subjScreen)
    // Inner person marker
    drawCircle(ringColor, radius = 7f, center = subjScreen)
    drawCircle(Color(0xFF0A0A12), radius = 4f, center = subjScreen)
    drawCircle(ringColor, radius = 2.5f, center = subjScreen)

    // Label + coordinates
    val labelText = if (isSynthetic) "SUBJECT (est.)" else "SUBJECT"
    val accentArgb = if (isSynthetic) 0x99FFAB40L else 0xFFFFAB40L
    drawCoordBadge(
        label = labelText,
        pos = subjectPos,
        screenPos = subjScreen,
        accentArgb = accentArgb,
        offsetY = -22f
    )

    // Distance annotation on the dashed line
    val distText = "%.1fm".format(subject.distanceM)
    if (startScreen != null) {
        val midX = (startScreen.x + subjScreen.x) / 2f
        val midY = (startScreen.y + subjScreen.y) / 2f
        drawCoordPill(distText, Offset(midX, midY), 0xAAFFAB40)
    }
}

// ── Ground Grid ─────────────────────────────────────────────────

private fun DrawScope.drawGroundGrid(
    bounds: SceneBounds,
    target: Vec3,
    azimuth: Float,
    elevation: Float,
    radius: Float,
    screenW: Float,
    screenH: Float,
    panX: Float,
    panY: Float
) {
    val gridExtent = bounds.extent * 1.5f
    val gridStep = computeGridStep(gridExtent)
    val gridY = bounds.center.y - bounds.extent * 0.5f
    val gridColor = Color(0x18FFFFFF)

    val snapX = (target.x / gridStep).toInt() * gridStep
    val snapZ = (target.z / gridStep).toInt() * gridStep
    val halfSteps = ((gridExtent / gridStep).toInt() + 1).coerceAtMost(20)

    for (i in -halfSteps..halfSteps) {
        val offset = i * gridStep
        // Lines parallel to Z
        val a = projectPoint(
            Vec3(snapX + offset, gridY, snapZ - gridExtent),
            target, azimuth, elevation, radius, screenW, screenH, panX, panY
        )
        val b = projectPoint(
            Vec3(snapX + offset, gridY, snapZ + gridExtent),
            target, azimuth, elevation, radius, screenW, screenH, panX, panY
        )
        if (a != null && b != null) drawLine(gridColor, a, b, strokeWidth = 0.8f)

        // Lines parallel to X
        val c = projectPoint(
            Vec3(snapX - gridExtent, gridY, snapZ + offset),
            target, azimuth, elevation, radius, screenW, screenH, panX, panY
        )
        val d = projectPoint(
            Vec3(snapX + gridExtent, gridY, snapZ + offset),
            target, azimuth, elevation, radius, screenW, screenH, panX, panY
        )
        if (c != null && d != null) drawLine(gridColor, c, d, strokeWidth = 0.8f)
    }
}

private fun computeGridStep(extent: Float): Float {
    val raw = extent / 5f
    return when {
        raw < 0.3f -> 0.25f
        raw < 0.7f -> 0.5f
        raw < 1.5f -> 1f
        raw < 3.5f -> 2f
        raw < 7.5f -> 5f
        else -> 10f
    }
}

// ── TUM Parsing ─────────────────────────────────────────────────

private fun parseTumPoints3D(tum: String): List<Vec3> {
    return tum.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 4) {
                val x = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val y = parts[2].toFloatOrNull() ?: return@mapNotNull null
                val z = parts[3].toFloatOrNull() ?: return@mapNotNull null
                Vec3(x, y, z)
            } else null
        }
        .toList()
}

// ── Coordinate Badges ───────────────────────────────────────────

/**
 * Draws a label + coordinate badge next to a marker. The badge shows
 * the name on the first line and (x, y, z) in metres on the second,
 * both inside a rounded-rect pill with a translucent dark background.
 *
 *   ┌─────────────────┐
 *   │  HOME            │
 *   │  1.2, 0.0, -3.4 │
 *   └─────────────────┘
 */
private fun DrawScope.drawCoordBadge(
    label: String,
    pos: Vec3,
    screenPos: Offset,
    accentArgb: Long,
    offsetY: Float     // vertical offset from screenPos (negative = above)
) {
    val canvas = drawContext.canvas.nativeCanvas
    val coordText = "(%.2f, %.2f, %.2f)".format(pos.x, pos.y, pos.z)

    val namePaint = badgeNamePaint(accentArgb)
    val coordPaint = badgeCoordPaint()

    // Measure text widths
    val nameW = namePaint.measureText(label)
    val coordW = coordPaint.measureText(coordText)
    val textW = maxOf(nameW, coordW)

    val padH = 10f   // horizontal padding
    val padV = 6f    // vertical padding
    val lineH = 30f  // line height
    val badgeW = textW + padH * 2
    val badgeH = lineH * 2 + padV * 2

    // Position badge: centered horizontally above the marker
    val bx = screenPos.x - badgeW / 2f
    val by = screenPos.y + offsetY - badgeH

    // Background pill
    val bgPaint = android.graphics.Paint().apply {
        color = 0xCC0A0A12.toInt()
        isAntiAlias = true
    }
    canvas.drawRoundRect(bx, by, bx + badgeW, by + badgeH, 8f, 8f, bgPaint)

    // Accent left stripe
    val stripePaint = android.graphics.Paint().apply {
        color = accentArgb.toInt()
        isAntiAlias = true
    }
    canvas.drawRoundRect(bx, by, bx + 3f, by + badgeH, 2f, 2f, stripePaint)

    // Name
    canvas.drawText(label, bx + padH + 4f, by + padV + 22f, namePaint)
    // Coordinates
    canvas.drawText(coordText, bx + padH + 4f, by + padV + lineH + 20f, coordPaint)
}

/**
 * Small translucent pill showing a single text (e.g. distance "2.5m").
 */
private fun DrawScope.drawCoordPill(
    text: String,
    center: Offset,
    accentArgb: Long
) {
    val canvas = drawContext.canvas.nativeCanvas
    val paint = smallLabelPaint(accentArgb)
    val tw = paint.measureText(text)
    val padH = 8f; val padV = 4f
    val w = tw + padH * 2; val h = 26f + padV * 2

    val bx = center.x - w / 2f
    val by = center.y - h / 2f

    val bgPaint = android.graphics.Paint().apply {
        color = 0xAA0A0A12.toInt()
        isAntiAlias = true
    }
    canvas.drawRoundRect(bx, by, bx + w, by + h, 12f, 12f, bgPaint)
    canvas.drawText(text, bx + padH, center.y + 8f, paint)
}

// ── Paint helpers (native Canvas text) ──────────────────────────

private fun badgeNamePaint(argb: Long) = android.graphics.Paint().apply {
    color = argb.toInt()
    textSize = 22f
    isAntiAlias = true
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}

private fun badgeCoordPaint() = android.graphics.Paint().apply {
    color = 0xBBCCCCDD.toInt()
    textSize = 19f
    isAntiAlias = true
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
}

private fun axisLabelPaint(argb: Long) = android.graphics.Paint().apply {
    color = argb.toInt()
    textSize = 24f
    isAntiAlias = true
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}

private fun smallLabelPaint(argb: Long) = android.graphics.Paint().apply {
    color = argb.toInt()
    textSize = 22f
    isAntiAlias = true
    typeface = android.graphics.Typeface.DEFAULT
}

// ── Colors ──────────────────────────────────────────────────────

private val PathStartColor = Color(0xFF42A5F5)   // blue
private val PathEndColor = Color(0xFFEF5350)      // red
private val StartColor = Color(0xFF4CAF50)        // green
private val EndColor = Color(0xFFEF5350)          // red
private val SubjectColor = Color(0xFFFFAB40)      // amber/orange
private val AxisXColor = Color(0xFFFF5252)        // red
private val AxisYColor = Color(0xFF69F0AE)        // green
private val AxisZColor = Color(0xFF448AFF)        // blue
