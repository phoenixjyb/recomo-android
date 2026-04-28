package com.recomo.user.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.recomo.common.sceneviewer.AnchorPose
import com.recomo.common.sceneviewer.SceneAssetRepository
import com.recomo.common.sceneviewer.SpzRegistryEntry
import java.io.File

/**
 * Pre-launch panel for the SceneViewer. Lists available SPZ files, auto-selects
 * the best match for the given session (if any), shows the effective anchor pose,
 * and provides an "Open in Browser" button that launches an external browser via
 * [SceneViewerLauncher].
 *
 * Rendering happens in the external browser (Edge / Chrome) because the embedded
 * Android WebView on this hardware does not composite the Gaussian splat canvas —
 * see `docs/SCENEVIEWER_PLAN.md` for background.
 */
@Composable
fun SceneViewerScreen(
    request: SceneViewerLaunchRequest,
    repository: SceneAssetRepository,
    httpServer: SceneViewerHttpServer,
    tumCacheDir: File,
    onEnsureReady: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entries by repository.entries.collectAsState()

    // Preferred entry = best-match by session, fallback to first available.
    val autoSelected = remember(entries, request.sessionId) {
        request.sessionId?.let { repository.findBestMatchForSession(it) }
            ?: entries.firstOrNull()
    }
    var selectedEntry by remember(autoSelected) { mutableStateOf(autoSelected) }

    // Ensure repository is scanned and server is started on first compose.
    LaunchedEffect(Unit) { onEnsureReady() }

    // Priority: inline override from the launch request (AI chat candidates
    // carry their own anchor_pose) > per-trajectory anchor in the registry
    // entry > registry's default anchor > identity.
    val persistedAnchor = request.anchorOverride
        ?: selectedEntry?.anchorFor(request.sessionId)
        ?: AnchorPose.IDENTITY
    // Runtime override — not persisted unless user taps a Save button.
    var workingAnchor by remember(selectedEntry?.id, request.sessionId, request.anchorOverride) {
        mutableStateOf(persistedAnchor)
    }
    var anchorExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101014))
                .border(1.dp, Color(0xFF2A2A31), RoundedCornerShape(20.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header row — title + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = request.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request.subtitle ?: "Scene viewer launcher",
                        color = Color.White.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            // SPZ selector
            SectionHeader("Scene file")
            if (entries.isEmpty()) {
                EmptyFolderHint(
                    folder = repository.currentFolder(),
                    onRefresh = { repository.refresh() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161C))
                        .padding(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        SpzRow(
                            entry = entry,
                            selected = entry.id == selectedEntry?.id,
                            onClick = { selectedEntry = entry }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Anchor — summary + expandable editor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Anchor (scene alignment)")
                TextButton(onClick = { anchorExpanded = !anchorExpanded }) {
                    Text(
                        text = if (anchorExpanded) "Hide" else "Edit",
                        color = Color(0xFF66D2FF)
                    )
                }
            }
            AnchorSummary(
                anchor = workingAnchor,
                isTrajectoryOverride = selectedEntry?.trajectoryAnchors?.containsKey(request.sessionId) == true,
                dirty = workingAnchor != persistedAnchor
            )

            if (anchorExpanded) {
                Spacer(Modifier.height(8.dp))
                AnchorEditor(
                    value = workingAnchor,
                    onValueChange = { workingAnchor = it },
                    onResetToPersisted = { workingAnchor = persistedAnchor },
                    onResetToIdentity = { workingAnchor = AnchorPose.IDENTITY },
                    onSaveAsDefault = {
                        selectedEntry?.let { entry ->
                            val updated = repository.updateAnchor(entry.id, null, workingAnchor)
                            if (updated != null) selectedEntry = updated
                        }
                    },
                    onSaveForTrajectory = {
                        val trajectoryId = request.sessionId ?: return@AnchorEditor
                        selectedEntry?.let { entry ->
                            val updated = repository.updateAnchor(entry.id, trajectoryId, workingAnchor)
                            if (updated != null) selectedEntry = updated
                        }
                    },
                    canSaveForTrajectory = request.sessionId != null && selectedEntry != null,
                    canSaveAsDefault = selectedEntry != null
                )
            }

            Spacer(Modifier.height(24.dp))

            // Launch button
            Button(
                onClick = {
                    val launched = SceneViewerLauncher.launch(
                        context = context,
                        request = request,
                        selectedEntry = selectedEntry,
                        anchorOverride = workingAnchor.takeIf { it != persistedAnchor },
                        server = httpServer,
                        tumCacheDir = tumCacheDir
                    )
                    if (launched) onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2962FF),
                    contentColor = Color.White
                ),
                enabled = selectedEntry != null || request.sceneSource != null || request.trajectorySource != null
            ) {
                Text("Open in Browser")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SpzRow(
    entry: SpzRegistryEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x332962FF) else Color(0xFF1C1C24))
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) Color(0xFF2962FF) else Color(0xFF2A2A31),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = entry.displayName,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${(entry.fileSize / 1024 / 1024)} MB",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (selected) {
            Text("✓", color = Color(0xFF2962FF), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AnchorSummary(
    anchor: AnchorPose,
    isTrajectoryOverride: Boolean,
    dirty: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF16161C))
            .padding(12.dp)
    ) {
        val label = when {
            dirty -> "Unsaved edits"
            isTrajectoryOverride -> "Trajectory override"
            else -> "Scene default"
        }
        val labelColor = if (dirty) Color(0xFFF5C451) else Color(0xFF66D2FF)
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "pos: (%.3f, %.3f, %.3f)".format(anchor.x, anchor.y, anchor.z),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "quat: (%.3f, %.3f, %.3f, %.3f)".format(anchor.qx, anchor.qy, anchor.qz, anchor.qw),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AnchorEditor(
    value: AnchorPose,
    onValueChange: (AnchorPose) -> Unit,
    onResetToPersisted: () -> Unit,
    onResetToIdentity: () -> Unit,
    onSaveAsDefault: () -> Unit,
    onSaveForTrajectory: () -> Unit,
    canSaveAsDefault: Boolean,
    canSaveForTrajectory: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF16161C))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("x", value.x, Modifier.weight(1f)) { onValueChange(value.copy(x = it)) }
            NumberField("y", value.y, Modifier.weight(1f)) { onValueChange(value.copy(y = it)) }
            NumberField("z", value.z, Modifier.weight(1f)) { onValueChange(value.copy(z = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("qx", value.qx, Modifier.weight(1f)) { onValueChange(value.copy(qx = it)) }
            NumberField("qy", value.qy, Modifier.weight(1f)) { onValueChange(value.copy(qy = it)) }
            NumberField("qz", value.qz, Modifier.weight(1f)) { onValueChange(value.copy(qz = it)) }
            NumberField("qw", value.qw, Modifier.weight(1f)) { onValueChange(value.copy(qw = it)) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onResetToIdentity,
                modifier = Modifier.weight(1f)
            ) { Text("Identity", color = Color.White, style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = onResetToPersisted,
                modifier = Modifier.weight(1f)
            ) { Text("Revert", color = Color.White, style = MaterialTheme.typography.labelSmall) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onSaveAsDefault,
                enabled = canSaveAsDefault,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A6F3A))
            ) {
                Text("Save default", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = onSaveForTrajectory,
                enabled = canSaveForTrajectory,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A6F3A))
            ) {
                Text("Save for this trajectory", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf("%.4f".format(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF66D2FF),
            unfocusedBorderColor = Color(0x33FFFFFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF66D2FF),
            focusedLabelColor = Color(0xFF66D2FF),
            unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
        )
    )
}

@Composable
private fun EmptyFolderHint(folder: File?, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1C1C24))
            .padding(12.dp)
    ) {
        Text(
            text = "No .spz files found",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = folder?.let { "Folder: ${it.absolutePath}" }
                ?: "Set a scenes folder in Settings → SceneViewer",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("Rescan", color = Color.White)
        }
    }
}
