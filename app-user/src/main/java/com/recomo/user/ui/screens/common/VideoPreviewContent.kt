package com.recomo.user.ui.screens.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.recomo.user.ui.screens.run.RunVideoSurfaceView

@Composable
fun VideoPreviewContent(
    showBitmapFrame: Boolean,
    videoBitmap: Bitmap?,
    contentDescription: String,
    onVideoSurfaceReady: (android.view.SurfaceHolder) -> Unit,
    onVideoSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
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
            AndroidView(
                factory = { context ->
                    RunVideoSurfaceView(context).apply {
                        setZOrderMediaOverlay(false)
                        setOnSurfaceReadyListener(onVideoSurfaceReady)
                        setOnSurfaceDestroyedListener(onVideoSurfaceDestroyed)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        overlay()
    }
}
