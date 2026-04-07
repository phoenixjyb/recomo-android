package com.recomo.remotecontrol.camviewer.network

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "WebRTCReceiver"

// Must match publisher's chunking constants
private const val CHUNK_HEADER_SIZE = 12 // 4 bytes frame ID + 4 bytes chunk index + 4 bytes total chunks

@Serializable
data class SignalingMessage(
    val type: String,
    val peer_id: String? = null,
    val room_id: String? = null,
    val target_peer_id: String? = null,
    val from_peer_id: String? = null,
    val sdp: String? = null,
    val candidate: IceCandidateData? = null
)

@Serializable
data class IceCandidateData(
    val sdp: String,
    val sdpMLineIndex: Int,
    val sdpMid: String
)

/**
 * WebRTC Receiver for CamViewer
 * 
 * Receives H.265 video frames from WebRTC peers via data channels.
 * Uses signaling server for peer connection setup.
 * 
 * Architecture:
 * - Data Channel → WebRTCReceiver.onVideoFrame() → VideoDecoder
 * - Signaling: WebSocket connection to signaling server
 * - Peer Management: Maintains map of peer connections
 */
class WebRTCReceiver(
    private val context: Context,
    private val signalingUrl: String,
    private val roomId: String = "camcontrol-room",
    private val peerId: String = "camviewer-tablet",
    private val scope: CoroutineScope,
    private val onVideoFrame: (ByteArray) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // WebRTC components
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peerConnections = mutableMapOf<String, PeerConnection>()
    
    // Signaling
    private var signalingClient: HttpClient? = null
    private var signalingSession: DefaultClientWebSocketSession? = null
    private var signalingJob: Job? = null
    
    // State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _connectedPeers = MutableStateFlow<Int>(0)
    val connectedPeers: StateFlow<Int> = _connectedPeers.asStateFlow()
    
    // Statistics
    private var framesReceived = 0L
    private var bytesReceived = 0L
    
    // Chunk reassembly - stores partial frames while waiting for all chunks
    // Key: frameId, Value: Pair(totalChunks, Map<chunkIndex, data>)
    // Guarded by frameLock — accessed from WebRTC data channel native thread
    private val frameLock = Any()
    private val pendingFrames = mutableMapOf<Int, Pair<Int, MutableMap<Int, ByteArray>>>()
    private var lastCompletedFrameId = -1

    // Track dropped frames to request keyframes when quality degrades
    private var droppedFrameCount = 0
    private var lastKeyframeRequestTime = 0L
    private val KEYFRAME_REQUEST_COOLDOWN_MS = 2000L  // Don't request keyframes too often
    private val DROPPED_FRAMES_THRESHOLD = 3  // Request keyframe after this many dropped frames
    
    // Callback to request keyframe from publisher
    var onKeyframeNeeded: (() -> Unit)? = null
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    /**
     * Initialize WebRTC components
     */
    fun initialize() {
        Log.i(TAG, "Initializing WebRTC receiver...")
        
        try {
            // Initialize PeerConnectionFactory
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            
            PeerConnectionFactory.initialize(initOptions)
            
            val options = PeerConnectionFactory.Options()
            
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            
            Log.i(TAG, "✅ WebRTC initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize WebRTC", e)
            _connectionState.value = ConnectionState.Error("Initialization failed: ${e.message}")
        }
    }
    
    /**
     * Connect to signaling server and start receiving video
     */
    suspend fun connect() {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "❌ WebRTC not initialized")
            return
        }
        
        _connectionState.value = ConnectionState.Connecting
        
        try {
            // Create Ktor WebSocket client for signaling with proxy bypass
            signalingClient = HttpClient(CIO) {
                install(io.ktor.client.plugins.HttpTimeout) {
                    connectTimeoutMillis = 5_000
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
                install(WebSockets) {
                    pingInterval = 20_000
                }
                engine {
                    proxy = null  // Bypass proxy for direct ZeroTier connection
                }
            }
            
            Log.i(TAG, "📡 Connecting to signaling server: $signalingUrl")
            
            signalingJob = scope.launch {
                try {
                    signalingClient!!.webSocket(signalingUrl) {
                        signalingSession = this
                        
                        // Register with signaling server
                        send(json.encodeToString(SignalingMessage(
                            type = "register",
                            peer_id = peerId,
                            room_id = roomId
                        )))
                        
                        _connectionState.value = ConnectionState.Connected
                        Log.i(TAG, "✅ Connected to signaling server as $peerId in room $roomId")
                        
                        // Handle incoming signaling messages
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleSignalingMessage(frame.readText())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ WebRTC signaling loop error", e)
                    _connectionState.value = ConnectionState.Error("Signaling failed: ${e.message}")
                } finally {
                    try { signalingSession?.close() } catch (_: Throwable) {}
                    try { signalingClient?.close() } catch (_: Throwable) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to connect to signaling server", e)
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
        }
    }
    
    /**
     * Handle incoming signaling messages
     */
    private suspend fun handleSignalingMessage(message: String) {
        try {
            val msg = json.decodeFromString<SignalingMessage>(message)
            
            when (msg.type) {
                "registered" -> {
                    Log.i(TAG, "📝 Registered as ${msg.peer_id}")
                }
                
                "peer_joined" -> {
                    val remotePeerId = msg.peer_id ?: return
                    Log.i(TAG, "👋 Peer joined: $remotePeerId")
                    // Wait for offer from publisher (we don't initiate)
                }
                
                "peer_left" -> {
                    val remotePeerId = msg.peer_id ?: return
                    Log.i(TAG, "👋 Peer left: $remotePeerId")
                    removePeerConnection(remotePeerId)
                }
                
                "offer" -> {
                    val remotePeerId = msg.from_peer_id ?: return
                    val sdpString = msg.sdp ?: return
                    handleOffer(remotePeerId, sdpString)
                }
                
                "answer" -> {
                    val remotePeerId = msg.from_peer_id ?: return
                    val sdpString = msg.sdp ?: return
                    handleAnswer(remotePeerId, sdpString)
                }
                
                "ice_candidate" -> {
                    val remotePeerId = msg.from_peer_id ?: return
                    val candidateData = msg.candidate ?: return
                    handleIceCandidate(remotePeerId, candidateData)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling signaling message", e)
        }
    }
    
    /**
     * Create peer connection for a remote peer
     */
    private fun createPeerConnection(remotePeerId: String): PeerConnection? {
        Log.i(TAG, "🔗 Creating peer connection for $remotePeerId")
        
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )
        
        val peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    scope.launch {
                        sendIceCandidate(remotePeerId, candidate)
                    }
                }
                
                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.i(TAG, "📦 Data channel received from $remotePeerId: ${dataChannel.label()}")
                    
                    // Reset frame reassembly state for fresh connection
                    // This is crucial for fast reconnection - clears any stale partial frames
                    synchronized(frameLock) {
                        pendingFrames.clear()
                        lastCompletedFrameId = -1
                        droppedFrameCount = 0
                    }
                    Log.i(TAG, "🔄 Reset frame reassembly state for new connection")
                    
                    setupDataChannel(remotePeerId, dataChannel)
                    
                    // Request keyframe immediately when new data channel is established
                    // This speeds up video display after reconnection
                    scope.launch {
                        delay(500) // Wait for connection to fully stabilize
                        Log.i(TAG, "📢 Requesting keyframe after new data channel from $remotePeerId")
                        requestKeyframe()
                        // Request again after 1 second in case first was missed
                        delay(1000)
                        Log.i(TAG, "📢 Requesting keyframe again (backup)")
                        requestKeyframe()
                    }
                }
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "🧊 ICE connection state for $remotePeerId: $state")
                    if (state == PeerConnection.IceConnectionState.CONNECTED) {
                        _connectedPeers.value = peerConnections.size
                    } else if (state == PeerConnection.IceConnectionState.FAILED ||
                               state == PeerConnection.IceConnectionState.CLOSED) {
                        removePeerConnection(remotePeerId)
                    }
                }
                
                override fun onSignalingChange(state: PeerConnection.SignalingState) {
                    Log.d(TAG, "📡 Signaling state for $remotePeerId: $state")
                }
                
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
            }
        ) ?: run {
            Log.e(TAG, "❌ Failed to create peer connection")
            return null
        }
        
        peerConnections[remotePeerId] = peerConnection
        return peerConnection
    }
    
    /**
     * Setup data channel to receive video frames
     */
    private fun setupDataChannel(remotePeerId: String, dataChannel: DataChannel) {
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onStateChange() {
                Log.i(TAG, "📦 Data channel state for $remotePeerId: ${dataChannel.state()}")
            }
            
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) {
                    val data = buffer.data
                    
                    // Check if message has chunk header
                    if (data.remaining() < CHUNK_HEADER_SIZE) {
                        Log.w(TAG, "⚠️ Received message too small for chunk header")
                        return
                    }
                    
                    // Read chunk header
                    data.order(ByteOrder.BIG_ENDIAN)
                    val frameId = data.getInt()
                    val chunkIndex = data.getInt()
                    val totalChunks = data.getInt()
                    
                    // Read chunk data
                    val chunkData = ByteArray(data.remaining())
                    data.get(chunkData)
                    
                    bytesReceived += chunkData.size + CHUNK_HEADER_SIZE
                    
                    // Process chunk (synchronized — data channel callback runs on native thread)
                    val frameData = synchronized(frameLock) {
                        processChunk(frameId, chunkIndex, totalChunks, chunkData)
                    }

                    if (frameData != null) {
                        // Complete frame received
                        framesReceived++
                        
                        // Log first frames and periodically
                        if (framesReceived <= 10 || framesReceived % 30 == 0L) {
                            Log.i(TAG, "📥 Received complete frame #$framesReceived: ${frameData.size} bytes from $remotePeerId")
                        }
                        
                        // Forward to video decoder
                        onVideoFrame(frameData)
                    }
                } else {
                    Log.w(TAG, "⚠️ Received non-binary data on data channel")
                }
            }
            
            override fun onBufferedAmountChange(amount: Long) {}
        })
    }
    
    /**
     * Process a chunk and return the complete frame if all chunks are received
     */
    private fun processChunk(frameId: Int, chunkIndex: Int, totalChunks: Int, chunkData: ByteArray): ByteArray? {
        // Skip old frames
        if (frameId <= lastCompletedFrameId) {
            return null
        }
        
        // Single-chunk frame - no reassembly needed
        if (totalChunks == 1) {
            lastCompletedFrameId = frameId
            // Clean up any older pending frames
            cleanupOldFrames(frameId)
            return chunkData
        }
        
        // Multi-chunk frame - store and reassemble
        val (storedTotalChunks, chunks) = pendingFrames.getOrPut(frameId) {
            Pair(totalChunks, mutableMapOf())
        }
        
        chunks[chunkIndex] = chunkData
        
        if (framesReceived <= 10 || chunkIndex == 0) {
            Log.d(TAG, "🧩 Chunk $chunkIndex/$totalChunks for frame $frameId (${chunkData.size} bytes)")
        }
        
        // Check if all chunks received
        if (chunks.size == storedTotalChunks) {
            // Reassemble frame
            val totalSize = chunks.values.sumOf { it.size }
            val completeFrame = ByteArray(totalSize)
            var offset = 0
            
            for (i in 0 until storedTotalChunks) {
                val chunk = chunks[i] ?: run {
                    Log.e(TAG, "❌ Missing chunk $i for frame $frameId")
                    pendingFrames.remove(frameId)
                    return null
                }
                System.arraycopy(chunk, 0, completeFrame, offset, chunk.size)
                offset += chunk.size
            }
            
            // Clean up
            pendingFrames.remove(frameId)
            lastCompletedFrameId = frameId
            cleanupOldFrames(frameId)
            
            Log.i(TAG, "✅ Reassembled frame $frameId: $totalSize bytes from $storedTotalChunks chunks")
            return completeFrame
        }
        
        return null
    }
    
    /**
     * Remove pending frames older than the given frameId
     * Also tracks dropped frames and requests keyframes when quality degrades
     * Caller must hold frameLock.
     */
    private fun cleanupOldFrames(currentFrameId: Int) {
        // Cap total pending frames to prevent memory leak from large frameId gaps
        if (pendingFrames.size > 20) {
            val sortedKeys = pendingFrames.keys.sorted()
            val toRemove = sortedKeys.take(pendingFrames.size - 10)
            toRemove.forEach { pendingFrames.remove(it) }
            droppedFrameCount += toRemove.size
        }
        val droppedIds = pendingFrames.keys.filter { it < currentFrameId - 5 }
        droppedIds.forEach { oldFrameId ->
            val (totalChunks, receivedChunks) = pendingFrames[oldFrameId] ?: return@forEach
            val missing = totalChunks - receivedChunks.size
            Log.w(TAG, "🗑️ Dropped incomplete frame $oldFrameId (missing $missing/$totalChunks chunks)")
            pendingFrames.remove(oldFrameId)
            droppedFrameCount++
        }
        
        // Request keyframe if too many frames dropped
        if (droppedFrameCount >= DROPPED_FRAMES_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastKeyframeRequestTime > KEYFRAME_REQUEST_COOLDOWN_MS) {
                Log.i(TAG, "📢 Requesting keyframe after $droppedFrameCount dropped frames")
                lastKeyframeRequestTime = now
                droppedFrameCount = 0
                onKeyframeNeeded?.invoke()
            }
        }
    }
    
    /**
     * Handle incoming SDP offer
     */
    private fun handleOffer(remotePeerId: String, sdpString: String) {
        Log.i(TAG, "📩 Received offer from $remotePeerId")
        
        // Always remove old peer connection when receiving a new offer
        // This handles reconnection scenarios where the remote peer restarted
        if (peerConnections.containsKey(remotePeerId)) {
            Log.i(TAG, "🔄 Removing stale peer connection for $remotePeerId (new offer received)")
            removePeerConnection(remotePeerId)
        }
        
        val peerConnection = createPeerConnection(remotePeerId) ?: return
        
        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
        
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "✅ Remote description set for $remotePeerId, creating answer...")
                
                // Create answer
                peerConnection.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerSdp: SessionDescription) {
                        peerConnection.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                scope.launch {
                                    sendAnswer(remotePeerId, answerSdp.description)
                                }
                            }
                            override fun onSetFailure(error: String) {
                                Log.e(TAG, "❌ Failed to set local description: $error")
                            }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, answerSdp)
                    }
                    
                    override fun onCreateFailure(error: String) {
                        Log.e(TAG, "❌ Failed to create answer: $error")
                    }
                    
                    override fun onSetSuccess() {}
                    override fun onSetFailure(p0: String?) {}
                }, MediaConstraints())
            }
            
            override fun onSetFailure(error: String) {
                Log.e(TAG, "❌ Failed to set remote description: $error")
            }
            
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }
    
    /**
     * Handle incoming SDP answer
     */
    private fun handleAnswer(remotePeerId: String, sdpString: String) {
        Log.i(TAG, "📩 Received answer from $remotePeerId")
        
        val peerConnection = peerConnections[remotePeerId] ?: return
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "✅ Remote description set for $remotePeerId")
            }
            
            override fun onSetFailure(error: String) {
                Log.e(TAG, "❌ Failed to set remote description: $error")
            }
            
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }
    
    /**
     * Handle incoming ICE candidate
     */
    private fun handleIceCandidate(remotePeerId: String, candidateData: IceCandidateData) {
        val peerConnection = peerConnections[remotePeerId] ?: return
        
        val iceCandidate = IceCandidate(
            candidateData.sdpMid,
            candidateData.sdpMLineIndex,
            candidateData.sdp
        )
        
        peerConnection.addIceCandidate(iceCandidate)
        Log.d(TAG, "🧊 Added ICE candidate from $remotePeerId")
    }
    
    /**
     * Send signaling messages
     */
    private suspend fun sendAnswer(remotePeerId: String, sdp: String) {
        sendSignalingMessage(SignalingMessage(
            type = "answer",
            target_peer_id = remotePeerId,
            sdp = sdp
        ))
    }
    
    private suspend fun sendIceCandidate(remotePeerId: String, candidate: IceCandidate) {
        sendSignalingMessage(SignalingMessage(
            type = "ice_candidate",
            target_peer_id = remotePeerId,
            candidate = IceCandidateData(
                sdp = candidate.sdp,
                sdpMLineIndex = candidate.sdpMLineIndex,
                sdpMid = candidate.sdpMid
            )
        ))
    }
    
    private suspend fun sendSignalingMessage(message: SignalingMessage) {
        try {
            signalingSession?.send(json.encodeToString(message))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send signaling message", e)
        }
    }
    
    /**
     * Request a keyframe from all connected publishers
     */
    fun requestKeyframe() {
        scope.launch {
            peerConnections.keys.forEach { peerId ->
                try {
                    sendSignalingMessage(SignalingMessage(
                        type = "request_keyframe",
                        target_peer_id = peerId
                    ))
                    Log.i(TAG, "📢 Sent keyframe request to $peerId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send keyframe request", e)
                }
            }
        }
    }
    
    /**
     * Remove peer connection
     * NOTE: We don't call .close() on the peer connection to avoid native crashes.
     * Just remove from map and let it be garbage collected.
     */
    private fun removePeerConnection(remotePeerId: String) {
        peerConnections.remove(remotePeerId)
        // Don't call .close() - causes native thread crashes
        _connectedPeers.value = peerConnections.size
        Log.i(TAG, "🗑️ Removed peer connection: $remotePeerId")
    }
    
    /**
     * Close all connections and cleanup
     * 
     * Note: WebRTC native objects (PeerConnection, PeerConnectionFactory) are NOT disposed
     * synchronously because the native signaling thread may still be accessing them.
     * They will be cleaned up when the receiver is garbage collected or on app exit.
     */
    fun close() {
        Log.i(TAG, "Closing WebRTC receiver...")
        
        // Update state first - this signals to all callbacks that we're shutting down
        _connectionState.value = ConnectionState.Disconnected
        _connectedPeers.value = 0
        
        // Cancel the signaling coroutine job
        signalingJob?.cancel()
        signalingJob = null
        
        // Close signaling WebSocket - this will cause the for loop in connect() to exit
        // Do NOT close peer connections or factory here - causes native crashes
        scope.launch {
            try {
                signalingSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            } catch (e: Exception) {
                Log.w(TAG, "Error closing signaling session: ${e.message}")
            }
            signalingSession = null
            
            try {
                signalingClient?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing signaling client: ${e.message}")
            }
            signalingClient = null
        }
        
        // Clear pending frames
        synchronized(frameLock) { pendingFrames.clear() }
        
        Log.i(TAG, "✅ WebRTC receiver closed (signaling disconnected)")
        Log.i(TAG, "📊 Stats: Frames received: $framesReceived, Bytes received: $bytesReceived")
    }
    
    fun getStats(): Map<String, Any> {
        return mapOf(
            "connected_peers" to peerConnections.size,
            "frames_received" to framesReceived,
            "bytes_received" to bytesReceived,
            "state" to connectionState.value.toString()
        )
    }
}
