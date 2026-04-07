package com.recomo.user.ui.screens.run

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class RunVideoSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {
    private var surfaceReadyCallback: ((SurfaceHolder) -> Unit)? = null
    private var surfaceDestroyedCallback: (() -> Unit)? = null

    init {
        holder.addCallback(this)
    }

    fun setOnSurfaceReadyListener(callback: (SurfaceHolder) -> Unit) {
        surfaceReadyCallback = callback
    }

    fun setOnSurfaceDestroyedListener(callback: () -> Unit) {
        surfaceDestroyedCallback = callback
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReadyCallback?.invoke(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceDestroyedCallback?.invoke()
    }
}
