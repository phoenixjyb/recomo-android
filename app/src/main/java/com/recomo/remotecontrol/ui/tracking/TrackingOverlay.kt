package com.recomo.remotecontrol.ui.tracking

import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.hilt.navigation.compose.hiltViewModel
import com.recomo.remotecontrol.camviewer.ui.screens.video.VideoViewModel
import com.recomo.remotecontrol.config.TrackingConfig
import com.recomo.remotecontrol.network.OrinGatewayClient
import com.recomo.remotecontrol.tracking.BoundingBox
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * Interactive overlay for drawing bounding boxes and displaying tracker feedback.
 * Place this on top of VideoScreen.
 */
@Composable
fun TrackingOverlay(
    gatewayClient: OrinGatewayClient,
    videoContentArea: Rect? = null,
    targetWidth: Int = TrackingConfig.TARGET_WIDTH,
    targetHeight: Int = TrackingConfig.TARGET_HEIGHT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Drag state for user drawing
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    
    // View size and video content area
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val contentArea = videoContentArea ?: remember(viewSize) {
        // Default to full view if content area not provided
        Rect(
            left = 0f,
            top = 0f,
            right = viewSize.width.toFloat(),
            bottom = viewSize.height.toFloat()
        )
    }
    
    // Tracker feedback from Orin
    val subjectTracking by gatewayClient.subjectTracking.collectAsState()
    val latestContentArea by rememberUpdatedState(contentArea)
    val latestTargetWidth by rememberUpdatedState(targetWidth.coerceAtLeast(2))
    val latestTargetHeight by rememberUpdatedState(targetHeight.coerceAtLeast(2))
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewSize = coordinates.size
            }
            .pointerInput(contentArea, targetWidth, targetHeight) {
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
                    
                    // Distinguish between tap and drag
                    if (totalDrag >= TrackingConfig.MIN_DRAG_THRESHOLD_PX) {
                        // Drag ended - send target ROI
                        val start = dragStart
                        val end = dragCurrent
                        if (start != null && end != null) {
                            // Check if connected
                            if (!gatewayClient.isConnected()) {
                                Toast.makeText(
                                    context,
                                    "Not connected to robot yet",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val activeContentArea = latestContentArea
                                if (activeContentArea.width <= 1f || activeContentArea.height <= 1f) {
                                    android.util.Log.w(
                                        "TrackingOverlay",
                                        "Skip target_roi send: invalid content area=$activeContentArea"
                                    )
                                    Toast.makeText(
                                        context,
                                        "Video area not ready, please try again",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // Normalize and send
                                    android.util.Log.d("TrackingOverlay", 
                                        "Screen drag: start=$start, end=$end")
                                    android.util.Log.d("TrackingOverlay", 
                                        "Content area: $activeContentArea")
                                    
                                    val normalizedRect = CoordinateConverter.normalizeToContent(
                                        screenStart = start,
                                        screenEnd = end,
                                        contentArea = activeContentArea
                                    )
                                    android.util.Log.d("TrackingOverlay", 
                                        "Normalized rect: $normalizedRect")
                                    
                                    val targetRoi = CoordinateConverter.normalizedToTargetRoi(
                                        normalizedRect = normalizedRect,
                                        targetWidth = latestTargetWidth,
                                        targetHeight = latestTargetHeight
                                    )
                                    
                                    scope.launch {
                                        gatewayClient.sendTargetRoi(targetRoi)
                                        android.util.Log.d("TrackingOverlay", 
                                            "Sent target_roi: (${targetRoi.xOffset},${targetRoi.yOffset}) ${targetRoi.width}x${targetRoi.height}")
                                    }
                                    
                                    // Show confirmation toast
                                    Toast.makeText(
                                        context,
                                        "Target sent: ${targetRoi.width}x${targetRoi.height}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    
                    // Clear drag state
                    dragStart = null
                    dragCurrent = null
                }
            }
    ) {
        // User-drawn box overlay (while dragging)
        val start = dragStart
        val current = dragCurrent
        if (start != null && current != null) {
            val dragDist = abs(current.x - start.x) + abs(current.y - start.y)
            if (dragDist >= TrackingConfig.MIN_DRAG_THRESHOLD_PX) {
                val screenRect = CoordinateConverter.createScreenRect(start, current)
                BoundingBoxOverlay(
                    box = screenRect,
                    state = TrackingState.DRAWING
                )
            }
        }
        
        // Tracked box overlay (from Orin feedback)
        subjectTracking?.let { tracking ->
            val trackingState = when (tracking.state) {
                "pending" -> TrackingState.PENDING
                "tracking" -> TrackingState.TRACKING
                "lost" -> TrackingState.LOST
                else -> TrackingState.IDLE
            }
            
            val screenRect = CoordinateConverter.targetRoiToScreen(
                roi = com.recomo.remotecontrol.tracking.TargetRoi(
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
