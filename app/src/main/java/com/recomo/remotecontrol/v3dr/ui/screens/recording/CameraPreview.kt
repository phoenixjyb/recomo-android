package com.recomo.remotecontrol.v3dr.ui.screens.recording

import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "CameraPreview"

/**
 * Camera preview composable that provides a Surface for Camera2Controller.
 * Uses SurfaceView which handles rotation/scaling more reliably on tablets.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    previewWidth: Int = 1920,
    previewHeight: Int = 1080,
    onSurfaceAvailable: (Surface, Int, Int) -> Unit = { _, _, _ -> },
    onSurfaceDestroyed: () -> Unit = {}
) {
    val lastNotifiedSize = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d(TAG, "Surface created")
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        Log.d(TAG, "Surface changed: ${width}x${height}")
                        val newSize = width to height
                        if (lastNotifiedSize.value != newSize) {
                            // Match buffer to actual view size to avoid crop/zoom.
                            holder.setFixedSize(width, height)
                            lastNotifiedSize.value = newSize
                            onSurfaceAvailable(holder.surface, width, height)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d(TAG, "Surface destroyed")
                        lastNotifiedSize.value = null
                        onSurfaceDestroyed()
                    }
                })
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
