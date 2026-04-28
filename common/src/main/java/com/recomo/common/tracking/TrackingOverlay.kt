package com.recomo.common.tracking

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.recomo.common.model.SubjectTracking
import com.recomo.common.model.TargetRoi
import com.recomo.common.model.TrackingState
import kotlin.math.abs

private const val TAG = "TrackingOverlay"

/**
 * Interactive overlay for drawing bounding boxes and displaying tracker feedback.
 * Place this on top of a video surface.
 *
 * Decoupled from OrinGatewayClient — the caller provides tracking state and
 * handles ROI submission via callbacks.
 *
 * @param subjectTracking Current tracking feedback (null = no active tracking)
 * @param onRoiSelected Called when the user finishes drawing a bounding box
 * @param videoContentArea Video content area within the overlay (accounts for letterboxing)
 * @param targetWidth Target resolution width for coordinate conversion
 * @param targetHeight Target resolution height for coordinate conversion
 * @param enabled Whether drawing is enabled (set false to disable touch input)
 * @param isConnected Whether the robot connection is active (shows toast if not)
 */
@Composable
fun TrackingOverlay(
    subjectTracking: SubjectTracking?,
    onRoiSelected: (TargetRoi) -> Unit,
    videoContentArea: Rect? = null,
    targetWidth: Int = TrackingConfig.TARGET_WIDTH,
    targetHeight: Int = TrackingConfig.TARGET_HEIGHT,
    enabled: Boolean = true,
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val contentArea = videoContentArea ?: remember(viewSize) {
        Rect(0f, 0f, viewSize.width.toFloat(), viewSize.height.toFloat())
    }

    val latestContentArea by rememberUpdatedState(contentArea)
    val latestTargetWidth by rememberUpdatedState(targetWidth.coerceAtLeast(2))
    val latestTargetHeight by rememberUpdatedState(targetHeight.coerceAtLeast(2))

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { viewSize = it.size }
            .then(
                if (enabled) {
                    Modifier.pointerInput(contentArea, targetWidth, targetHeight) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            dragStart = down.position
                            dragCurrent = down.position

                            var totalDrag = 0f

                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        dragCurrent = change.position
                                        val dx = abs(change.position.x - dragStart!!.x)
                                        val dy = abs(change.position.y - dragStart!!.y)
                                        totalDrag = dx + dy
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (totalDrag >= TrackingConfig.MIN_DRAG_THRESHOLD_PX) {
                                val start = dragStart
                                val end = dragCurrent
                                if (start != null && end != null) {
                                    if (!isConnected) {
                                        Toast.makeText(context, "Not connected to robot", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val activeContentArea = latestContentArea
                                        if (activeContentArea.width <= 1f || activeContentArea.height <= 1f) {
                                            Log.w(TAG, "Skip ROI: invalid content area=$activeContentArea")
                                            Toast.makeText(context, "Video area not ready", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val normalizedRect = CoordinateConverter.normalizeToContent(
                                                screenStart = start,
                                                screenEnd = end,
                                                contentArea = activeContentArea
                                            )
                                            val targetRoi = CoordinateConverter.normalizedToTargetRoi(
                                                normalizedRect = normalizedRect,
                                                targetWidth = latestTargetWidth,
                                                targetHeight = latestTargetHeight
                                            )
                                            Log.d(TAG, "ROI selected: (${targetRoi.xOffset},${targetRoi.yOffset}) ${targetRoi.width}x${targetRoi.height}")
                                            onRoiSelected(targetRoi)
                                        }
                                    }
                                }
                            }

                            dragStart = null
                            dragCurrent = null
                        }
                    }
                } else Modifier
            )
    ) {
        // User-drawn box while dragging
        val start = dragStart
        val current = dragCurrent
        if (start != null && current != null) {
            val dragDist = abs(current.x - start.x) + abs(current.y - start.y)
            if (dragDist >= TrackingConfig.MIN_DRAG_THRESHOLD_PX) {
                BoundingBoxOverlay(
                    box = CoordinateConverter.createScreenRect(start, current),
                    state = TrackingState.DRAWING
                )
            }
        }

        // Tracked box from Orin feedback
        subjectTracking?.let { tracking ->
            val trackingState = when (tracking.state) {
                "pending" -> TrackingState.PENDING
                "tracking" -> TrackingState.TRACKING
                "lost" -> TrackingState.LOST
                else -> TrackingState.IDLE
            }

            val screenRect = CoordinateConverter.targetRoiToScreen(
                roi = TargetRoi(
                    xOffset = tracking.bbox.xOffset,
                    yOffset = tracking.bbox.yOffset,
                    width = tracking.bbox.width,
                    height = tracking.bbox.height
                ),
                contentArea = contentArea,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )

            TrackedBoundingBox(
                box = screenRect,
                state = trackingState
            )
        }
    }
}
