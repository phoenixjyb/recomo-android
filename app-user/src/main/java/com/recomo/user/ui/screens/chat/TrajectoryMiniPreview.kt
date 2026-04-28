package com.recomo.user.ui.screens.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight 3D trajectory preview rendered on a Compose [Canvas].
 *
 * Parses inline TUM text (timestamp x y z qx qy qz qw per line),
 * projects the camera path into an isometric-ish 2D view, and draws
 * a time-gradient polyline with start/end markers. No Filament, no GL
 * — renders inside a candidate card with zero overhead.
 */
@Composable
fun TrajectoryMiniPreview(
    tumText: String,
    modifier: Modifier = Modifier
) {
    val points = remember(tumText) { parseTumPoints(tumText) }
    if (points.size < 2) return

    val projected = remember(points) { projectIsometric(points) }
    val bounds = remember(projected) { computeBounds(projected) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12121A))
    ) {
        val pad = 12f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val rangeX = (bounds.maxX - bounds.minX).coerceAtLeast(0.001f)
        val rangeY = (bounds.maxY - bounds.minY).coerceAtLeast(0.001f)
        val scale = min(w / rangeX, h / rangeY)
        val offX = pad + (w - rangeX * scale) / 2f
        val offY = pad + (h - rangeY * scale) / 2f

        fun toScreen(p: Pt2): Offset = Offset(
            offX + (p.x - bounds.minX) * scale,
            offY + (bounds.maxY - p.y) * scale // flip Y for screen coords
        )

        // Draw path with time gradient
        val n = projected.size
        for (i in 0 until n - 1) {
            val t = i.toFloat() / (n - 1).coerceAtLeast(1)
            val color = lerp(PathStartColor, PathEndColor, t)
            val a = toScreen(projected[i])
            val b = toScreen(projected[i + 1])
            drawLine(color, a, b, strokeWidth = 2.5f, cap = StrokeCap.Round)
        }

        // Start marker (green)
        val start = toScreen(projected.first())
        drawCircle(Color(0xFF4CAF50), radius = 5f, center = start)

        // End marker (red)
        val end = toScreen(projected.last())
        drawCircle(Color(0xFFEF5350), radius = 5f, center = end)

        // Waypoint dots (every ~10% of the path)
        val step = max(1, n / 10)
        for (i in step until n - 1 step step) {
            val pt = toScreen(projected[i])
            drawCircle(Color(0x66FFFFFF), radius = 2.5f, center = pt)
        }
    }
}

// ── Data types ───────────────────────────────────────────────────

private data class Pt3(val x: Float, val y: Float, val z: Float)
private data class Pt2(val x: Float, val y: Float)
private data class Bounds2(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float)

private val PathStartColor = Color(0xFF42A5F5) // blue
private val PathEndColor = Color(0xFFEF5350)   // red

// ── TUM parsing ──────────────────────────────────────────────────

private fun parseTumPoints(tum: String): List<Pt3> {
    return tum.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 4) {
                // TUM: timestamp x y z [qx qy qz qw]
                val x = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val y = parts[2].toFloatOrNull() ?: return@mapNotNull null
                val z = parts[3].toFloatOrNull() ?: return@mapNotNull null
                Pt3(x, y, z)
            } else null
        }
        .toList()
}

// ── Projection ───────────────────────────────────────────────────

/**
 * Simple isometric projection: rotate the XZ plane ~30° so the view
 * has depth. Y (up in TUM) maps to screen-Y offset for height.
 */
private fun projectIsometric(pts: List<Pt3>): List<Pt2> {
    val angle = Math.toRadians(30.0)
    val cosA = cos(angle).toFloat()
    val sinA = sin(angle).toFloat()
    return pts.map { p ->
        Pt2(
            x = p.x * cosA - p.z * sinA,
            y = p.x * sinA + p.z * cosA + p.y * 0.5f
        )
    }
}

private fun computeBounds(pts: List<Pt2>): Bounds2 {
    var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
    for (p in pts) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    // Add 5% margin
    val dx = (maxX - minX) * 0.05f
    val dy = (maxY - minY) * 0.05f
    return Bounds2(minX - dx, maxX + dx, minY - dy, maxY + dy)
}
