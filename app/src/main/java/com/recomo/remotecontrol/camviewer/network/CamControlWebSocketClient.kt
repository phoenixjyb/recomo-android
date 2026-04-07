package com.recomo.remotecontrol.camviewer.network

import android.util.Log
import com.recomo.remotecontrol.camviewer.data.model.BoundingBox
import com.recomo.remotecontrol.camviewer.data.model.ConnectionState
import com.recomo.remotecontrol.camviewer.data.model.Telemetry
import com.recomo.remotecontrol.camviewer.data.model.TrackingUpdate
import com.recomo.remotecontrol.camviewer.data.model.VideoFrame
import com.recomo.remotecontrol.camviewer.data.model.VideoFrameFormat
import com.recomo.remotecontrol.camviewer.di.KtorWebSocketClient
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerialName
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CamControlWS"

/**
 * WebSocket client for receiving video frames and telemetry from CamControl.
 * 
 * Protocol:
 * - Binary frames: H.264/H.265 NAL units (Annex-B format with start codes)
 * - Text frames: JSON telemetry data or tracking status
 * 
 * Connection URL: ws://<camcontrol-ip>:9090/control
 */
@Singleton
class CamControlWebSocketClient @Inject constructor(
    @KtorWebSocketClient private val client: HttpClient,
    private val json: Json
) {
    // Scope for async frame processing (doesn't block WebSocket receive)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _videoFrames = MutableStateFlow<VideoFrame?>(null)
    val videoFrames: Flow<VideoFrame?> = _videoFrames
    
    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()
    
    private val _trackingStatus = MutableStateFlow<TrackingUpdate?>(null)
    val trackingStatus: StateFlow<TrackingUpdate?> = _trackingStatus.asStateFlow()
    private val _trackingControl = MutableStateFlow<Boolean?>(null)
    val trackingControl: StateFlow<Boolean?> = _trackingControl.asStateFlow()

    @Volatile private var trackingFrameWidth = 1920f
    @Volatile private var trackingFrameHeight = 1080f
    @Volatile private var rotationDeg = 0

    data class TimeSyncResult(
        val rttMs: Long,
        // Offset to convert phone(server) wall time -> tablet(client) wall time.
        // clientTime ~= serverTime + serverToClientOffsetMs
        val serverToClientOffsetMs: Long,
        val lastUpdatedMs: Long
    )

    private val _timeSync = MutableStateFlow<TimeSyncResult?>(null)
    val timeSync: StateFlow<TimeSyncResult?> = _timeSync.asStateFlow()

    private var timeSyncSeq: Long = 0
    private val pendingTimeSync = mutableMapOf<Long, Long>() // seq -> clientSendMs
    private val MAX_PENDING_TIME_SYNCS = 10 // Prevent unbounded growth if responses are lost
    
    private var currentSession: DefaultClientWebSocketSession? = null
    private val connectMutex = Mutex()
    @Volatile private var connectInProgress = false
    @Volatile private var connectAttemptToken = 0L
    @Volatile private var activeControlUrl: String? = null

    fun isActiveFor(url: String): Boolean {
        val baseUrl = url.trim().trimEnd('/')
        val controlUrl = if (baseUrl.endsWith("/control")) baseUrl else "$baseUrl/control"
        val state = _connectionState.value
        return activeControlUrl == controlUrl &&
            (connectInProgress ||
                state is ConnectionState.Connected ||
                state is ConnectionState.Connecting)
    }
    
    /**
     * Connect to CamControl WebSocket server
     * @param url WebSocket base URL (e.g., "ws://172.16.30.28:9090") or full control URL
     *            (e.g., "ws://172.16.30.28:9090/control").
     */
    suspend fun connect(url: String) {
        val baseUrl = url.trim().trimEnd('/')
        val controlUrl = if (baseUrl.endsWith("/control")) baseUrl else "$baseUrl/control"
        var attemptToken = 0L
        var sessionToClose: DefaultClientWebSocketSession? = null

        // Serialize connection attempts without blocking disconnect.
        connectMutex.withLock {
            val state = _connectionState.value
            val alreadyBusy = connectInProgress ||
                state is ConnectionState.Connected ||
                state is ConnectionState.Connecting
            val sameUrl = activeControlUrl == controlUrl

            if (alreadyBusy && !sameUrl) {
                Log.i(
                    TAG,
                    "connect() switching URL from $activeControlUrl to $controlUrl while state=$state"
                )
                // Invalidate any in-flight attempt and close current session if present.
                connectAttemptToken += 1
                connectInProgress = false
                sessionToClose = currentSession
                currentSession = null
                activeControlUrl = null
                _connectionState.value = ConnectionState.Disconnected
            }

            if (connectInProgress ||
                _connectionState.value is ConnectionState.Connected ||
                _connectionState.value is ConnectionState.Connecting) {
                Log.d(
                    TAG,
                    "connect() no-op - already connecting/connected: state=${_connectionState.value}, activeUrl=$activeControlUrl, requestedUrl=$controlUrl"
                )
                return
            }
            connectInProgress = true
            connectAttemptToken += 1
            attemptToken = connectAttemptToken
            activeControlUrl = controlUrl
            _connectionState.value = ConnectionState.Connecting
        }

        if (sessionToClose != null) {
            try {
                sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, "switch url"))
            } catch (e: Exception) {
                Log.w(TAG, "Failed closing previous session during URL switch: ${e.message}")
            }
        }

        Log.i(TAG, "Connecting to: $controlUrl")

        try {
            withContext(Dispatchers.IO) {
                client.webSocket(urlString = controlUrl) {
                    connectMutex.withLock {
                        if (attemptToken != connectAttemptToken) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "stale attempt"))
                            return@withLock
                        }
                        currentSession = this
                        _connectionState.value = ConnectionState.Connected
                    }
                    Log.i(TAG, "WebSocket connected successfully")

                    // Process incoming frames asynchronously (don't block receive loop)
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Binary -> {
                                val data = frame.readBytes()
                                // Process frame in background without blocking receive
                                scope.launch {
                                    handleBinaryFrame(data)
                                }
                            }
                            is Frame.Text -> {
                                val text = frame.readText()
                                // Process telemetry in background
                                scope.launch {
                                    handleTextFrame(text)
                                }
                            }
                            is Frame.Close -> {
                                Log.i(TAG, "WebSocket closed by server")
                                _connectionState.value = ConnectionState.Disconnected
                                break
                            }
                            else -> { /* Ignore Ping, Pong */ }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "WebSocket coroutine cancelled: ${e.message}")
            connectMutex.withLock {
                if (attemptToken == connectAttemptToken) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket error: ${e.message}", e)
            connectMutex.withLock {
                if (attemptToken == connectAttemptToken) {
                    _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
                }
            }
        } finally {
            connectMutex.withLock {
                if (attemptToken == connectAttemptToken) {
                    currentSession = null
                    if (_connectionState.value !is ConnectionState.Error) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    connectInProgress = false
                    activeControlUrl = null
                }
            }
            Log.i(TAG, "WebSocket session ended, state=${_connectionState.value}")
        }
    }
    
    /**
     * Disconnect from WebSocket server
     */
    suspend fun disconnect() {
        Log.d(TAG, "disconnect() called, current state: ${_connectionState.value}")
        connectMutex.withLock {
            connectAttemptToken += 1
            connectInProgress = false
            activeControlUrl = null
        }
        try {
            currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            Log.w(TAG, "Error while closing session: ${e.message}")
        } finally {
            currentSession = null
            _connectionState.value = ConnectionState.Disconnected
            Log.d(TAG, "disconnect() completed, new state: ${_connectionState.value}")
        }
    }
    
    /**
     * Send camera control command
     * @param command Command name (e.g., "zoom", "focus", "codec")
     * @param params Command parameters
     */
    suspend fun sendCommand(command: String, params: Map<String, String> = emptyMap()) {
        currentSession?.let { session ->
            if (session.isActive) {
                try {
                    val commandJson = json.encodeToString(
                        kotlinx.serialization.serializer(),
                        mapOf("command" to command, "params" to params)
                    )
                    session.send(Frame.Text(commandJson))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send command: $command", e)
                }
            }
        }
    }
    
    /**
     * Send raw JSON command (for structured commands like setTargetRoi)
     * @param jsonCommand JSON string to send
     */
    suspend fun sendRawCommand(jsonCommand: String) {
        currentSession?.let { session ->
            if (session.isActive) {
                try {
                    Log.d(TAG, "Sending raw command: $jsonCommand")
                    session.send(Frame.Text(jsonCommand))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send raw command", e)
                }
            } else {
                Log.w(TAG, "Cannot send command - session not active")
            }
        } ?: Log.w(TAG, "Cannot send command - no session")
    }

    /**
     * Request a time sync / RTT sample.
     * The phone replies with a "type":"timeSync" message.
     */
    suspend fun requestTimeSync() {
        val session = currentSession ?: return
        if (!session.isActive) return

        val seq = ++timeSyncSeq
        val clientSendMs = System.currentTimeMillis()
        // Prune stale entries to prevent unbounded growth
        if (pendingTimeSync.size >= MAX_PENDING_TIME_SYNCS) {
            val oldest = pendingTimeSync.keys.minOrNull()
            if (oldest != null) pendingTimeSync.remove(oldest)
        }
        pendingTimeSync[seq] = clientSendMs

        val cmd = TimeSyncCommand(seq = seq, clientSendMs = clientSendMs)
        session.send(Frame.Text(json.encodeToString(TimeSyncCommand.serializer(), cmd)))
    }
    
    /**
     * Handle binary frame (video data)
     * Binary frames contain H.264/H.265 NAL units in Annex-B format
     */
    private fun handleBinaryFrame(data: ByteArray) {
        val isJpegFrame = data.size >= 3 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte()
        // Check if this is a keyframe by looking for SPS/PPS NAL units
        val isKeyframe = if (isJpegFrame) true else isKeyframeData(data)
        
        if (isKeyframe) {
            Log.i(TAG, "Received KEYFRAME: ${data.size} bytes")
        }
        
        val videoFrame = VideoFrame(
            data = data,
            timestamp = System.currentTimeMillis(),
            isKeyframe = isKeyframe,
            format = if (isJpegFrame) VideoFrameFormat.JPEG else VideoFrameFormat.H26X
        )
        
        _videoFrames.value = videoFrame
    }
    
    /**
     * Handle text frame (telemetry data or tracking status)
     */
    private fun handleTextFrame(text: String) {
        try {
            // Try to determine message type by checking for "type" field
            val jsonElement = json.parseToJsonElement(text)
            val typeField = jsonElement.jsonObject["type"]?.jsonPrimitive?.content
            
            when (typeField) {
                "timeSync" -> {
                    val resp = json.decodeFromString<TimeSyncResponse>(text)
                    val clientRecvMs = System.currentTimeMillis()
                    val clientSendMs = pendingTimeSync.remove(resp.seq) ?: resp.clientSendMs

                    val rttMs = (clientRecvMs - clientSendMs).coerceAtLeast(0)
                    // NTP-style offset estimate (client - server)
                    val serverMidMs = (resp.serverRecvMs + resp.serverSendMs) / 2.0
                    val clientMidMs = (clientSendMs + clientRecvMs) / 2.0
                    val clientMinusServer = (clientMidMs - serverMidMs).toLong()

                    _timeSync.value = TimeSyncResult(
                        rttMs = rttMs,
                        serverToClientOffsetMs = clientMinusServer,
                        lastUpdatedMs = clientRecvMs
                    )
                }
                "tracking" -> {
                    // Parse tracking status from camcontroller
                    val trackingData = json.decodeFromString<CamControllerTrackingStatus>(text)
                    Log.d(TAG, "Received tracking status: state=${trackingData.state}, confidence=${trackingData.confidence}")
                    
                    val frameW = trackingFrameWidth
                    val frameH = trackingFrameHeight
                    val normalized = BoundingBox(
                        x = trackingData.bbox.xOffset / frameW,
                        y = trackingData.bbox.yOffset / frameH,
                        width = trackingData.bbox.width / frameW,
                        height = trackingData.bbox.height / frameH
                    )

                    // Convert to TrackingUpdate format
                    val update = TrackingUpdate(
                        trackingId = trackingData.trackingId,
                        state = trackingData.state,
                        bbox = normalized,
                        confidence = trackingData.confidence,
                        timestamp = trackingData.timestampMs
                    )
                    _trackingStatus.value = update
                }
                "tracking_control" -> {
                    val control = json.decodeFromString<TrackingControlMessage>(text)
                    _trackingControl.value = control.enabled
                }
                else -> {
                    // Assume it's telemetry data
                    val telemetry = json.decodeFromString<Telemetry>(text)
                    telemetry.resolution?.let { res ->
                        if (res.width > 0 && res.height > 0) {
                            trackingFrameWidth = res.width.toFloat()
                            trackingFrameHeight = res.height.toFloat()
                        }
                    }
                    telemetry.rotationDeg?.let { rotation ->
                        rotationDeg = ((rotation % 360) + 360) % 360
                    }
                    _telemetry.value = telemetry
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse text frame: ${e.message}")
        }
    }

    @Serializable
    private data class TimeSyncCommand(
        val cmd: String = "timeSync",
        val seq: Long,
        @SerialName("client_send_ms") val clientSendMs: Long
    )

    @Serializable
    private data class TimeSyncResponse(
        val type: String = "timeSync",
        val seq: Long,
        @SerialName("client_send_ms") val clientSendMs: Long,
        @SerialName("server_recv_ms") val serverRecvMs: Long,
        @SerialName("server_send_ms") val serverSendMs: Long
    )
    
    /**
     * Tracking status format from camcontroller
     */
    @Serializable
    private data class CamControllerTrackingStatus(
        val type: String = "tracking",
        @kotlinx.serialization.SerialName("tracking_id")
        val trackingId: String,
        val state: String,
        val bbox: CamControllerBBox,
        val confidence: Float,
        @kotlinx.serialization.SerialName("timestamp_ms")
        val timestampMs: Long = 0
    )
    
    @Serializable
    private data class CamControllerBBox(
        @kotlinx.serialization.SerialName("x_offset")
        val xOffset: Float,
        @kotlinx.serialization.SerialName("y_offset")
        val yOffset: Float,
        val width: Float,
        val height: Float
    )

    @Serializable
    private data class TrackingControlMessage(
        val type: String = "tracking_control",
        val enabled: Boolean,
        val source: String = ""
    )

    /**
     * Check if data contains keyframe indicators (SPS/PPS NAL units)
     * H.264 SPS = NAL type 7, PPS = NAL type 8
     * H.265 SPS = NAL type 33, PPS = NAL type 34
     */
    private fun isKeyframeData(data: ByteArray): Boolean {
        var i = 0
        while (i < data.size - 3) {
            var startCodeLength = 0
            if (i + 3 < data.size &&
                data[i] == 0.toByte() &&
                data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() &&
                data[i + 3] == 1.toByte()
            ) {
                startCodeLength = 4
            } else if (data[i] == 0.toByte() &&
                data[i + 1] == 0.toByte() &&
                data[i + 2] == 1.toByte()
            ) {
                startCodeLength = 3
            }

            if (startCodeLength > 0) {
                val nalIndex = i + startCodeLength
                if (nalIndex < data.size) {
                    val nalType = (data[nalIndex].toInt() and 0x1F) // H.264 NAL type (lower 5 bits)
                    val nalTypeH265 = (data[nalIndex].toInt() and 0x7E) shr 1 // H.265 NAL type (bits 1-6)

                    // H.264: SPS=7, PPS=8, IDR=5
                    // H.265: SPS=33, PPS=34, IDR=19-20
                    if (nalType == 7 || nalType == 8 || nalType == 5 ||
                        nalTypeH265 == 33 || nalTypeH265 == 34 ||
                        nalTypeH265 == 19 || nalTypeH265 == 20
                    ) {
                        return true
                    }
                }
                i += startCodeLength
            } else {
                i++
            }
        }
        return false
    }
}
