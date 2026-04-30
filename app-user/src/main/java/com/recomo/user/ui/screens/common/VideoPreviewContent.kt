package com.recomo.user.ui.screens.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.user.ui.screens.run.RunVideoSurfaceView

/**
 * @param videoAspectRatio If > 0, SurfaceView is constrained to this aspect ratio
 *        (no stretching). Pass videoWidth/videoHeight from telemetry. If 0, fills the view.
 */
@Composable
fun VideoPreviewContent(
    showBitmapFrame: Boolean,
    videoBitmap: Bitmap?,
    contentDescription: String,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
    videoAspectRatio: Float = 0f,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (showBitmapFrame && videoBitmap != null) {
            Image(
                bitmap = videoBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // If we know the video aspect ratio, constrain the SurfaceView to it
            // so the video is not stretched. The SurfaceView sits centered in a
            // black background (natural letterbox/pillarbox).
            val surfaceModifier = if (videoAspectRatio > 0f) {
                // Use BoxWithConstraints approach: fit the aspect ratio within the parent
                Modifier.aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false)
            } else {
                Modifier.fillMaxSize()
            }

            AndroidView(
                factory = { context ->
                    RunVideoSurfaceView(context).apply {
                        setZOrderMediaOverlay(false)
                        setOnSurfaceReadyListener(onVideoSurfaceReady)
                        setOnSurfaceDestroyedListener(onVideoSurfaceDestroyed)
                    }
                },
                modifier = surfaceModifier
            )
        }
        overlay()
    }
}
