package com.recomo.common.video

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * State of a video pipeline.
 */
enum class PipelineState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Interface for video input pipelines.
 * Implementations handle different video sources (WebSocket, HDMI-USB, etc.)
 */
interface VideoPipeline {
    fun start(surface: Surface)
    fun stop()
    fun isRunning(): Boolean
    val connectionState: StateFlow<PipelineState>
    val errorMessage: StateFlow<String?>
}
