package com.recomo.user.ui.screens.creator

import android.graphics.Bitmap
import android.view.SurfaceHolder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomo.user.control.FrameTiming
import com.recomo.user.control.UserEEPositionViewModel
import com.recomo.user.control.UserMotionCreatorDraftFrame
import com.recomo.user.control.UserMotionCreatorKeypointUiState
import com.recomo.user.control.UserMotionCreatorViewModel
import com.recomo.user.ui.components.ee.EEPositionControlPanel
import com.recomo.user.ui.screens.common.VideoPreviewContent
import com.recomo.user.ui.screens.preview.TrajectoryPreviewScreen

/**
 * Keypoint capture workspace — replaces `KeypointModePlaceholder` inside the
 * MotionCreator "运镜创建 / 关键帧" tab.
 *
 * Layout (tablet landscape):
 *  - Left pane (weight 1.2): live video preview + HUD overlay (session /
 *    frame count / homebase badge) + bottom-row capture controls
 *    (Set Anchor / Capture Point).
 *  - Right pane (weight 0.8): EEPositionControlPanel + scrollable frame list
 *    + Save / Clear action bar.
 *
 * Video is rendered through the shared `UserRunVideoViewModel` instance that
 * powers the Run / MainControl / TouchControl tabs — the route is already in
 * `videoRoutes`, so `setActive(true)` is handled by `UserMainScreen`. Callers
 * just forward the surface callbacks.
 *
 * Frame timing is captured via the modal [FrameTimingDialog], which the VM
 * pops via `pendingFrameTiming` after each `capturePoint`.
 */
@Composable
fun KeypointCaptureWorkspace(
    motionCreatorViewModel: UserMotionCreatorViewModel,
    eePositionViewModel: UserEEPositionViewModel,
    showBitmapFrame: Boolean,
    videoBitmap: Bitmap?,
    onVideoSurfaceReady: (SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    captureThumbnail: () -> String?,
    modifier: Modifier = Modifier
) {
    val state by motionCreatorViewModel.keypointState.collectAsState()
    val pendingTiming by motionCreatorViewModel.pendingFrameTiming.collectAsState()
    val draftPreview by motionCreatorViewModel.draftPreview.collectAsState()

    // Bind the thumbnail provider for the lifetime of this workspace. Clearing
    // on dispose prevents other MotionCreator modes from accidentally using it.
    DisposableEffect(motionCreatorViewModel, captureThumbnail) {
        motionCreatorViewModel.thumbnailProvider = captureThumbnail
        onDispose {
            motionCreatorViewModel.thumbnailProvider = null
        }
    }

    // Save → preview handoff: the moment a draft preview appears, render
    // `TrajectoryPreviewScreen` instead of the capture workspace. Close
    // returns here so the user can capture more frames for another take.
    val localPreview = draftPreview
    if (localPreview != null) {
        TrajectoryPreviewScreen(
            preview = localPreview,
            title = "Saved Keypoint Session",
            frameSummary = "Frames: ${localPreview.keyframes.size} · Duration: ${"%.1f".format(localPreview.totalDurationSec)}s",
            executeLabel = "Close",
            onClose = { motionCreatorViewModel.closeDraftPreview() },
            onExecute = null
        )
        return
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── LEFT: video preview + HUD + capture controls ────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = Color.Black,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0x55FFFFFF))
            ) {
                // Preserve the native 16:9 stream aspect; black letterbox fills the rest.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        VideoPreviewContent(
                            showBitmapFrame = showBitmapFrame,
                            videoBitmap = videoBitmap,
                            contentDescription = "Keypoint capture video",
                            onVideoSurfaceReady = onVideoSurfaceReady,
                            onVideoSurfaceDestroyed = onVideoSurfaceDestroyed,
                            modifier = Modifier.fillMaxSize(),
                            overlay = { KeypointHud(state = state) }
                        )
                    }
                }
            }

            KeypointCaptureControls(
                state = state,
                onSetAnchor = {
                    motionCreatorViewModel.setAnchor(
                        anchorNameInput = "Anchor",
                        sessionNameInput = state.suggestedSessionName
                    )
                },
                onCapturePoint = {
                    motionCreatorViewModel.capturePoint(
                        pointNameInput = state.suggestedFrameName,
                        sessionNameInput = state.suggestedSessionName
                    )
                }
            )
        }

        // ── RIGHT: EE teleop panel + frame list + save/clear ────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // EE panel wraps its own content — no weight, no scroll needed
            EEPositionControlPanel(
                viewModel = eePositionViewModel,
                modifier = Modifier.fillMaxWidth()
            )
            // Frame list takes ALL remaining vertical space
            KeypointFrameList(
                frames = state.frames,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onEditTiming = { idx -> motionCreatorViewModel.editFrameTiming(idx) },
                onRemove = { idx -> motionCreatorViewModel.removeFrame(idx) }
            )
            // Action bar always pinned to bottom
            KeypointActionBar(
                state = state,
                onClear = { motionCreatorViewModel.clearDraft() },
                onSave = { motionCreatorViewModel.saveDraft() }
            )
        }
    }

    // Timing dialog — shown after capture or from the frame list edit button
    pendingTiming?.let { pending ->
        FrameTimingDialog(
            frameName = pending.frameName,
            // Pre-populate with the frame's existing timing when re-editing.
            currentTiming = state.frames
                .filter { !it.isAnchor }
                .getOrNull(pending.frameIndex)
                ?.let { f ->
                    FrameTiming(
                        dwellS = f.dwellS,
                        transitionS = f.transitionS,
                        ease = f.ease
                    )
                },
            onConfirm = { timing ->
                motionCreatorViewModel.setFrameTiming(pending.frameIndex, timing)
            },
            onDismiss = { motionCreatorViewModel.cancelFrameTiming() }
        )
    }
}

// ── HUD overlay on video ────────────────────────────────────────────────────

@Composable
private fun androidx.compose.foundation.layout.BoxScope.KeypointHud(
    state: UserMotionCreatorKeypointUiState
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xAA000000),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = state.currentSessionName ?: state.suggestedSessionName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (state.hasAnchor) {
            Surface(color = Color(0xCC4CAF50), shape = RoundedCornerShape(4.dp)) {
                Text(
                    text = "ANCHOR",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Surface(color = Color(0xAA000000), shape = RoundedCornerShape(4.dp)) {
            Text(
                text = "Frames: ${state.frameCount}",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }

    // Status pill bottom-right
    Surface(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp),
        color = if (state.connected) Color(0xCC1B3A1B) else Color(0xCC5D2020),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = when {
                !state.connected -> "Disconnected"
                !state.poseLocked -> "No pose lock"
                else -> "Ready"
            },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ── Capture controls (bottom of left pane) ──────────────────────────────────

@Composable
private fun KeypointCaptureControls(
    state: UserMotionCreatorKeypointUiState,
    onSetAnchor: () -> Unit,
    onCapturePoint: () -> Unit
) {
    Surface(
        color = Color(0xFF1C1C1C),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onSetAnchor,
                enabled = state.connected && state.poseLocked,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (state.hasAnchor) {
                        Color(0xFF2E5F2E)
                    } else {
                        Color(0xFF1B3A1B)
                    }
                )
            ) {
                Icon(Icons.Filled.Anchor, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = if (state.hasAnchor) "Reset Anchor" else "Set Anchor",
                    modifier = Modifier.padding(start = 6.dp),
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onCapturePoint,
                enabled = state.connected && state.poseLocked && state.hasAnchor,
                modifier = Modifier.weight(1.3f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A9EFF)
                )
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "Capture Point",
                    modifier = Modifier.padding(start = 6.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Compact frame list (no card chrome) ─────────────────────────────────────

@Composable
private fun KeypointFrameList(
    frames: List<UserMotionCreatorDraftFrame>,
    onEditTiming: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Tiny header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Keyframes", fontSize = 10.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)
            if (frames.isNotEmpty()) {
                Text(" (${frames.size})", fontSize = 10.sp, color = Color(0xFF666666))
            }
        }
        if (frames.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Set anchor → capture points", color = Color(0xFF555555), fontSize = 10.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                var pointIdx = 0
                frames.forEach { frame ->
                    val idx = if (frame.isAnchor) -1 else pointIdx
                    CompactFrameRow(
                        frame = frame,
                        onEditTiming = { onEditTiming(idx) },
                        onRemove = { onRemove(idx) }
                    )
                    if (!frame.isAnchor) pointIdx++
                }
            }
        }
    }
}

@Composable
private fun CompactFrameRow(
    frame: UserMotionCreatorDraftFrame,
    onEditTiming: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (frame.isAnchor) Color(0xFF1A2E1A) else Color(0xFF1E1E22),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Name + pose inline
        Column(modifier = Modifier.weight(1f)) {
            val poseSuffix = "  (%.2f, %.2f) %.0f°".format(frame.baseX, frame.baseY, Math.toDegrees(frame.baseYaw))
            Text(
                text = frame.name + poseSuffix,
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1
            )
            // Timing — single line
            val timing = buildString {
                frame.dwellS?.let { append("d:${"%.1f".format(it)}s ") }
                frame.transitionS?.let { append("t:${"%.1f".format(it)}s ") }
                frame.ease?.let { append(it) }
            }.trim()
            if (timing.isNotEmpty()) {
                Text(timing, fontSize = 8.sp, color = Color(0xFF777777), maxLines = 1)
            }
        }
        // Edit + delete (only for points, not anchor)
        if (!frame.isAnchor) {
            IconButton(onClick = onEditTiming, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Save, "Edit", tint = Color(0xFF88AACC), modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Delete, "Del", tint = Color(0xFFCC6666), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Action bar ──────────────────────────────────────────────────────────────

@Composable
private fun KeypointActionBar(
    state: UserMotionCreatorKeypointUiState,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onClear,
            enabled = state.frames.isNotEmpty() && !state.saving,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text("Clear", fontSize = 11.sp)
        }
        Button(
            onClick = onSave,
            enabled = state.canSave && !state.saving,
            modifier = Modifier.weight(1f).height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Text(
                text = if (state.saving) "Saving…" else "Save & Preview",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (state.backendMessage != null) {
            Text(state.backendMessage, fontSize = 8.sp, color = Color(0xFFAACCFF))
        }
    }
}
