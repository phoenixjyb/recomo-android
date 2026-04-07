package com.recomo.user.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recomo.user.R
import com.recomo.user.ui.components.VideoPlayer
import java.io.File

data class MotionDetailItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val keyframes: Int = 0,
    val duration: Int = 0,
    val sceneType: String = "",
    val linkedMap: String = "",
    val gradientStart: Color = Color(0xFF0084E8),
    val gradientEnd: Color = Color(0xFF7B5EFF),
    val isPreset: Boolean = false,
    @androidx.annotation.DrawableRes val thumbnailRes: Int? = null,
    @androidx.annotation.DrawableRes val trajectoryRes: Int? = null,
    val videoFileName: String? = null
)

@Composable
fun MotionDetailOverlay(
    item: MotionDetailItem,
    onBack: () -> Unit,
    onRunThis: () -> Unit,
    modifier: Modifier = Modifier,
    videoFile: File? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f)
                    )
                }
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // Badge: Preset or Library
                Surface(
                    color = if (item.isPreset) Color(0xFF7B5EFF).copy(alpha = 0.12f)
                            else Color(0xFF0084E8).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (item.isPreset) Color(0xFF7B5EFF).copy(alpha = 0.2f)
                        else Color(0xFF0084E8).copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = stringResource(
                            if (item.isPreset) R.string.motion_detail_preset
                            else R.string.motion_detail_library
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.isPreset) Color(0xFFB89AFF) else Color(0xFF66D2FF),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Main content — video preview + trajectory + info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left: Video preview — ExoPlayer when video file exists, else thumbnail
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(item.gradientStart, item.gradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoFile != null && videoFile.exists()) {
                        VideoPlayer(
                            file = videoFile,
                            modifier = Modifier.fillMaxSize(),
                            autoPlay = true,
                            loop = true,
                            showControls = false
                        )
                    } else if (item.thumbnailRes != null) {
                        Image(
                            painter = painterResource(item.thumbnailRes),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Play icon overlay (no video file available)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Right column: trajectory preview + metadata + Run button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Trajectory preview image
                    if (item.trajectoryRes != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF101418)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(item.trajectoryRes),
                                contentDescription = "Trajectory preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Metadata pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.duration > 0) {
                            MetadataPill(
                                icon = Icons.Default.AccessTime,
                                text = stringResource(R.string.motion_detail_duration, item.duration)
                            )
                        }
                        if (item.keyframes > 0) {
                            MetadataPill(
                                icon = Icons.Default.Timeline,
                                text = stringResource(R.string.motion_detail_keyframes, item.keyframes)
                            )
                        }
                        if (item.sceneType.isNotBlank()) {
                            MetadataPill(
                                icon = Icons.Default.PlayArrow,
                                text = item.sceneType
                            )
                        }
                    }

                    // Run This button
                    Button(
                        onClick = onRunThis,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.dp, Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0084E8), Color(0xFF7B5EFF))
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (item.isPreset) R.string.motion_detail_use_this
                                        else R.string.motion_detail_run_this
                                    ),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF66D2FF),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// Conversion helpers

fun LibrarySessionSummaryUiItem.toMotionDetailItem(): MotionDetailItem {
    val gradients = gradientForCategory(category)
    return MotionDetailItem(
        id = sessionId,
        title = displayMotionTitle(),
        subtitle = displayMotionSubtitle(),
        keyframes = count,
        duration = 0,
        sceneType = sceneType,
        linkedMap = linkedMap,
        gradientStart = gradients.first,
        gradientEnd = gradients.second,
        isPreset = false
    )
}

private fun gradientForCategory(category: String): Pair<Color, Color> = when (category.lowercase()) {
    "product" -> Color(0xFF0084E8) to Color(0xFF6C5CE7)
    "lifestyle" -> Color(0xFFEF6C35) to Color(0xFFE91E63)
    "interview" -> Color(0xFF00BFA5) to Color(0xFF0084E8)
    "cinematic" -> Color(0xFF7B5EFF) to Color(0xFF2C3E94)
    "tour" -> Color(0xFF26A69A) to Color(0xFF1E88E5)
    else -> Color(0xFF0084E8) to Color(0xFF7B5EFF)
}
