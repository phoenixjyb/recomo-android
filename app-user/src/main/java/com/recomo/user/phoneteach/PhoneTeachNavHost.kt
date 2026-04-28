package com.recomo.user.phoneteach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recomo.user.phoneteach.ui.auth.AuthScreen
import com.recomo.user.phoneteach.ui.capture.CaptureScreen
import com.recomo.user.phoneteach.ui.library.LibraryScreen
import com.recomo.user.phoneteach.ui.playback.PlaybackScreen
import com.recomo.user.phoneteach.ui.settings.CaptureSettingsScreen
import com.recomo.user.phoneteach.ui.upload.UploadScreen
import java.io.File

/**
 * Phone Teach (手机示教) feature nav host stub for :app-user MotionCreator.
 *
 * Replaces the legacy [com.recomo.user.ui.screens.creator.PhoneTeachModePlaceholder] private
 * composable when wired via the `phoneTeachContent` slot on `MotionCreatorScreen`. Owns an inner
 * state-based nav between the six sub-screens of the phone moco flow:
 *
 *   Capture → Library → Playback → Upload → Settings → Auth
 *
 * Each screen is currently a [PhoneTeachPlaceholder] stub. Actual implementations land in later
 * phases of the migration:
 *
 *   P5 → Capture        (merged Camera2 + ARCore + IMU session recording)
 *   P6 → Library + Playback  (session browser + Media3 ExoPlayer review)
 *   P7 → Upload + Settings + Auth  (WorkManager multipart to V3DR Lake)
 *
 * See `memory/project_app_user_phone_moco_migration.md` for the full plan.
 */
enum class PhoneTeachRoute(val label: String) {
    CAPTURE("Capture"),
    LIBRARY("Library"),
    PLAYBACK("Playback"),
    UPLOAD("Upload"),
    SETTINGS("Settings"),
    AUTH("Auth")
}

@Composable
fun PhoneTeachNavHost(
    modifier: Modifier = Modifier,
    onPreviewTrajectory: (File) -> Unit = {}
) {
    var activeRoute by rememberSaveable { mutableStateOf(PhoneTeachRoute.CAPTURE) }
    // Session selected from Library → hoisted here so Playback can consume it.
    // Using rememberSaveable with the absolute path as a String keeps survival across
    // config changes without needing a custom Saver for File.
    var selectedSessionPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSessionDir = remember(selectedSessionPath) {
        selectedSessionPath?.let { File(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0B0B0B))
    ) {
        // Content area — fullbleed for CAPTURE so the camera preview gets the entire
        // viewport. Other routes get top padding so their own internal headers clear
        // the floating tab bar above.
        val contentPadding = when (activeRoute) {
            PhoneTeachRoute.CAPTURE -> PaddingValues(0.dp)
            else -> PaddingValues(
                top = 68.dp,
                start = 8.dp,
                end = 8.dp,
                bottom = 8.dp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (activeRoute) {
                PhoneTeachRoute.CAPTURE -> CaptureScreen(modifier = Modifier.fillMaxSize())
                PhoneTeachRoute.LIBRARY -> LibraryScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPlaySession = { dir ->
                        selectedSessionPath = dir.absolutePath
                        activeRoute = PhoneTeachRoute.PLAYBACK
                    }
                )
                PhoneTeachRoute.PLAYBACK -> {
                    val dir = selectedSessionDir
                    if (dir != null && dir.exists()) {
                        PlaybackScreen(
                            sessionDir = dir,
                            modifier = Modifier.fillMaxSize(),
                            onBackToLibrary = {
                                selectedSessionPath = null
                                activeRoute = PhoneTeachRoute.LIBRARY
                            }
                        )
                    } else {
                        PhoneTeachPlaceholder(
                            title = "Playback",
                            body = "Pick a session from Library to review it.",
                            status = "No session selected"
                        )
                    }
                }
                PhoneTeachRoute.UPLOAD -> UploadScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPreviewTrajectory = onPreviewTrajectory
                )
                PhoneTeachRoute.SETTINGS -> CaptureSettingsScreen(modifier = Modifier.fillMaxSize())
                PhoneTeachRoute.AUTH -> AuthScreen(modifier = Modifier.fillMaxSize())
            }
        }

        // Floating controls — a single compact pill at top-center containing the
        // "Phone Teach" label + 6 route buttons, semi-transparent so the camera preview
        // shows through on the CAPTURE route. Placed in the top band which stays clear
        // of CaptureScreen's existing top-left telemetry card and top-right ARCore /
        // camera-switch overlays.
        PhoneTeachFloatingControls(
            active = activeRoute,
            onSelect = { activeRoute = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun PhoneTeachFloatingControls(
    active: PhoneTeachRoute,
    onSelect: (PhoneTeachRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xAA0A0A0A),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x1EFFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Phone Teach",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFDFDFDF),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp)
            )
            PhoneTeachRoute.entries.forEach { route ->
                val isActive = route == active
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isActive) Color(0xFF2D6CDF) else Color(0x1FFFFFFF)
                        )
                        .clickable { onSelect(route) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = route.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) Color.White else Color(0xFFBFBFBF),
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneTeachPlaceholder(
    title: String,
    body: String,
    status: String
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xCC111111),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFEFEFEF),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB3B3B3)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0x332D6CDF)
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF89B6FF)
                )
            }
        }
    }
}
