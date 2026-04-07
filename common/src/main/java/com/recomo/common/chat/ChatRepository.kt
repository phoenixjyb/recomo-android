package com.recomo.common.chat

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

private const val TAG = "ChatRepository"

/**
 * Connection state for the chat WebSocket.
 */
enum class ChatConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}

/**
 * Manages WebSocket connection to the cloud chat server.
 *
 * Usage:
 *   val repo = ChatRepository()
 *   repo.connect("wss://chat.example.com/ws", deviceId = "tablet-001")
 *   repo.sendMessage("Plan a shot from A to B")
 *   repo.incomingEvents.collect { event -> ... }
 */
class ChatRepository {

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20_000
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    /** All incoming server events (chunks, done, task status, errors). */
    private val _incomingEvents = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val incomingEvents: SharedFlow<ServerEvent> = _incomingEvents.asSharedFlow()

    @Volatile private var session: DefaultClientWebSocketSession? = null
    private val sendMutex = Mutex()
    private var connectionJob: Job? = null
    private var serverUrl: String? = null
    private var deviceId: String? = null

    // ── Public API ────────────────────────────────────────────────

    /**
     * Connect to the chat server. If already connected, disconnects first.
     * @param url WebSocket URL, e.g. "wss://chat.recomo.com/ws" or "ws://192.168.1.100:8800/ws"
     * @param deviceId Unique device identifier
     * @param existingConversationId Resume a previous conversation (optional)
     */
    fun connect(
        url: String,
        deviceId: String,
        existingConversationId: String? = null,
        robotProfile: String? = null,
        locationId: String? = null
    ) {
        this.serverUrl = url
        this.deviceId = deviceId
        connectionJob?.cancel()
        connectionJob = scope.launch {
            _connectionState.value = ChatConnectionState.CONNECTING
            try {
                client.webSocket(url) {
                    session = this
                    Log.i(TAG, "WebSocket connected to $url")

                    // Send handshake
                    val connectMsg = json.encodeToString(
                        ChatConnect.serializer(),
                        ChatConnect(
                            deviceId = deviceId,
                            conversationId = existingConversationId,
                            robotProfile = robotProfile,
                            locationId = locationId
                        )
                    )
                    send(Frame.Text(connectMsg))

                    _connectionState.value = ChatConnectionState.CONNECTED

                    // Receive loop
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handleFrame(frame.readText())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "WebSocket error: ${e.message}", e)
                _incomingEvents.emit(ServerEvent.ConnectionError(e.message ?: "Unknown error"))
            } finally {
                session = null
                val wasConnected = _connectionState.value == ChatConnectionState.CONNECTED
                _connectionState.value = ChatConnectionState.DISCONNECTED
                if (wasConnected) {
                    Log.w(TAG, "Connection lost, scheduling reconnect")
                    scheduleReconnect()
                }
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            session?.close()
            session = null
            _connectionState.value = ChatConnectionState.DISCONNECTED
        }
    }

    /**
     * Send a user message to the conversation.
     */
    suspend fun sendMessage(content: String, context: ChatContext? = null) {
        val convId = _conversationId.value
        if (convId == null) {
            Log.w(TAG, "Cannot send: no conversation ID yet")
            return
        }
        val msg = json.encodeToString(
            ChatSendMessage.serializer(),
            ChatSendMessage(
                conversationId = convId,
                content = content,
                context = context
            )
        )
        sendRaw(msg)
    }

    /** Cancel in-progress assistant generation. */
    suspend fun cancelGeneration() {
        val convId = _conversationId.value ?: return
        val msg = json.encodeToString(
            ChatCancel.serializer(),
            ChatCancel(conversationId = convId)
        )
        sendRaw(msg)
    }

    // ── Internal ──────────────────────────────────────────────────

    private suspend fun sendRaw(text: String) {
        sendMutex.withLock {
            try {
                session?.send(Frame.Text(text))
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}")
            }
        }
    }

    private suspend fun handleFrame(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return

            when (type) {
                ChatMessageType.CONNECTED -> {
                    val msg = json.decodeFromJsonElement(ChatConnected.serializer(), obj)
                    _conversationId.value = msg.conversationId
                    _incomingEvents.emit(ServerEvent.Connected(msg))
                    Log.i(TAG, "Chat connected, conversation=${msg.conversationId}")
                }
                ChatMessageType.ASSISTANT_CHUNK -> {
                    val msg = json.decodeFromJsonElement(AssistantChunk.serializer(), obj)
                    _incomingEvents.emit(ServerEvent.Chunk(msg))
                }
                ChatMessageType.ASSISTANT_DONE -> {
                    val msg = json.decodeFromJsonElement(AssistantDone.serializer(), obj)
                    _incomingEvents.emit(ServerEvent.Done(msg))
                }
                ChatMessageType.TASK_STATUS -> {
                    val msg = json.decodeFromJsonElement(TaskStatus.serializer(), obj)
                    _incomingEvents.emit(ServerEvent.TaskProgress(msg))
                }
                ChatMessageType.TRAJECTORY_READY -> {
                    val msg = json.decodeFromJsonElement(TrajectoryReady.serializer(), obj)
                    _incomingEvents.emit(ServerEvent.TrajectoryAvailable(msg))
                }
                ChatMessageType.ERROR -> {
                    val msg = json.decodeFromJsonElement(ChatError.serializer(), obj)
                    _incomingEvents.emit(ServerEvent.ServerError(msg))
                    Log.e(TAG, "Server error: [${msg.code}] ${msg.message}")
                }
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse frame: ${e.message}", e)
        }
    }

    private fun scheduleReconnect() {
        val url = serverUrl ?: return
        val devId = deviceId ?: return
        scope.launch {
            delay(3000)
            if (_connectionState.value == ChatConnectionState.DISCONNECTED) {
                Log.i(TAG, "Attempting reconnect...")
                _connectionState.value = ChatConnectionState.RECONNECTING
                connect(url, devId, _conversationId.value)
            }
        }
    }
}

/**
 * Sealed class for all server→client events consumed by the ViewModel.
 */
sealed class ServerEvent {
    data class Connected(val msg: ChatConnected) : ServerEvent()
    data class Chunk(val msg: AssistantChunk) : ServerEvent()
    data class Done(val msg: AssistantDone) : ServerEvent()
    data class TaskProgress(val msg: TaskStatus) : ServerEvent()
    data class TrajectoryAvailable(val msg: TrajectoryReady) : ServerEvent()
    data class ServerError(val msg: ChatError) : ServerEvent()
    data class ConnectionError(val reason: String) : ServerEvent()
}
