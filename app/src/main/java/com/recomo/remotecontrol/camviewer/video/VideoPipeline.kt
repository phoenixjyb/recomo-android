package com.recomo.remotecontrol.camviewer.video

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
    /**
     * Start the video pipeline and render to the given surface.
     */
    fun start(surface: Surface)
    
    /**
     * Stop the video pipeline and release resources.
     */
    fun stop()
    
    /**
     * Check if the pipeline is currently running.
     */
    fun isRunning(): Boolean
    
    /**
     * Observable state of the pipeline connection.
     */
    val connectionState: StateFlow<PipelineState>
    
    /**
     * Error message if pipeline is in ERROR state, null otherwise.
     */
    val errorMessage: StateFlow<String?>
}
