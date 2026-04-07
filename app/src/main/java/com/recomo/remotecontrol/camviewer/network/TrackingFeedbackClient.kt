package com.recomo.remotecontrol.camviewer.network

import android.util.Log
import com.recomo.remotecontrol.camviewer.data.model.BoundingBox
import com.recomo.remotecontrol.camviewer.data.model.TrackingUpdate
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import com.recomo.remotecontrol.camviewer.di.KtorWebSocketClient

/**
 * WebSocket client for receiving real-time tracking updates from Orin.
 * Connects to the tracking feedback server on Orin and receives updates about
 * the state of object tracking initiated by the user.
 */
@Singleton
class TrackingFeedbackClient @Inject constructor(
    @KtorWebSocketClient private val wsClient: HttpClient
) {
    companion object {
        private const val TAG = "TrackingFeedbackClient"
        private const val DEFAULT_PORT = 8084
    }

    private val _trackingUpdates = MutableStateFlow<TrackingUpdate?>(null)
    val trackingUpdates: StateFlow<TrackingUpdate?> = _trackingUpdates.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var shouldReconnect = false

    /**
     * Connect to tracking feedback WebSocket server on Orin
     * 
     * @param host Orin IP address (e.g., "172.16.30.234")
     * @param port WebSocket port (default: 8084)
     */
    suspend fun connect(host: String, port: Int = DEFAULT_PORT) {
        // Cancel any existing connection
        disconnect()
        
        shouldReconnect = true
        connectionJob = scope.launch {
            while (shouldReconnect) {
                try {
                    val url = "ws://$host:$port"
                    Log.i(TAG, "Connecting to tracking feedback server: $url")

                    wsClient.webSocket(urlString = url) {
                        _isConnected.value = true
                        Log.i(TAG, "Connected to tracking feedback server")

                        try {
                            // Receive messages from server
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        handleMessage(text)
                                    }
                                    else -> {
                                        Log.d(TAG, "Received non-text frame: ${frame.frameType}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error receiving tracking updates", e)
                        } finally {
                            _isConnected.value = false
                            Log.i(TAG, "Disconnected from tracking feedback server")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to tracking feedback server", e)
                    _isConnected.value = false
                }
                
                // Wait before reconnecting if connection dropped
                if (shouldReconnect) {
                    Log.i(TAG, "Reconnecting in 3 seconds...")
                    delay(3000)
                }
            }
        }
    }

    /**
     * Handle incoming WebSocket message
     */
    private fun handleMessage(text: String) {
        try {
            val message = json.decodeFromString<WebSocketMessage>(text)
            
            when (message.type) {
                "tracking_update" -> {
                    message.data?.let { data ->
                        val update = TrackingUpdate(
                            trackingId = data.tracking_id,
                            state = data.state,
                            bbox = data.bbox?.let { bbox ->
                                BoundingBox(
                                    x = bbox.x,
                                    y = bbox.y,
                                    width = bbox.width,
                                    height = bbox.height
                                )
                            },
                            confidence = data.confidence,
                            timestamp = data.timestamp
                        )
                        
                        _trackingUpdates.value = update
                        
                        Log.d(TAG, "Received tracking update: id=${update.trackingId}, " +
                                "state=${update.state}, confidence=${update.confidence}")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown message type: ${message.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tracking message: $text", e)
        }
    }

    /**
     * Disconnect from tracking feedback server
     */
    fun disconnect() {
        shouldReconnect = false
        connectionJob?.cancel()
        connectionJob = null
        _isConnected.value = false
        _trackingUpdates.value = null
        Log.i(TAG, "Disconnecting from tracking feedback server")
    }

    /**
     * Clear the last tracking update without closing the connection.
     * Useful when starting a new tracking cycle to avoid showing stale boxes.
     */
    fun clearTrackingUpdate() {
        _trackingUpdates.value = null
    }

    /**
     * WebSocket message wrapper
     */
    @Serializable
    private data class WebSocketMessage(
        val type: String,
        val data: TrackingUpdateData? = null
    )

    /**
     * Tracking update data from server
     */
    @Serializable
    private data class TrackingUpdateData(
        val tracking_id: String,
        val state: String,
        val bbox: BBoxData? = null,
        val confidence: Float,
        val timestamp: Long
    )

    /**
     * Bounding box data from server
     */
    @Serializable
    private data class BBoxData(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
}
