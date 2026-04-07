package com.recomo.user.data.video

import android.util.Log
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.Telemetry
import com.recomo.common.model.VideoFrame
import com.recomo.common.model.VideoFrameFormat
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "UserVideoStreamClient"

class UserVideoStreamClient(
    private val json: Json
) {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20_000
            maxFrameSize = 10 * 1024 * 1024
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }

    private val connectMutex = Mutex()
    private var currentSession: DefaultClientWebSocketSession? = null
    private var activeUrl: String? = null
    private var connectAttemptToken = 0L
    private var connectInProgress = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _videoFrames = MutableStateFlow<VideoFrame?>(null)
    val videoFrames: Flow<VideoFrame?> = _videoFrames

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    fun isActiveFor(url: String): Boolean {
        val normalized = normalizeUrl(url)
        val state = _connectionState.value
        return activeUrl == normalized &&
            (connectInProgress ||
                state is ConnectionState.Connected ||
                state is ConnectionState.Connecting)
    }

    suspend fun connect(url: String) {
        val normalized = normalizeUrl(url)
        if (normalized.isBlank()) {
            _connectionState.value = ConnectionState.Error("Missing video stream URL")
            return
        }
        var attemptToken = 0L
        var staleSession: DefaultClientWebSocketSession? = null

        connectMutex.withLock {
            val state = _connectionState.value
            val sameUrl = activeUrl == normalized
            val alreadyBusy = connectInProgress ||
                state is ConnectionState.Connected ||
                state is ConnectionState.Connecting

            if (alreadyBusy && !sameUrl) {
                connectAttemptToken += 1
                connectInProgress = false
                staleSession = currentSession
                currentSession = null
                activeUrl = null
                _connectionState.value = ConnectionState.Disconnected
            }

            if (connectInProgress ||
                _connectionState.value is ConnectionState.Connected ||
                _connectionState.value is ConnectionState.Connecting) {
                return
            }

            connectInProgress = true
            connectAttemptToken += 1
            attemptToken = connectAttemptToken
            activeUrl = normalized
            _connectionState.value = ConnectionState.Connecting
        }

        try {
            staleSession?.close(CloseReason(CloseReason.Codes.NORMAL, "switch url"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed closing stale video session: ${e.message}")
        }

        try {
            client.webSocket(urlString = normalized) {
                connectMutex.withLock {
                    if (attemptToken != connectAttemptToken) {
                        close(CloseReason(CloseReason.Codes.NORMAL, "stale attempt"))
                        return@withLock
                    }
                    currentSession = this
                    _connectionState.value = ConnectionState.Connected
                }

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> handleBinaryFrame(frame.readBytes())
                        is Frame.Text -> handleTextFrame(frame.readText())
                        is Frame.Close -> {
                            _connectionState.value = ConnectionState.Disconnected
                            break
                        }
                        else -> Unit
                    }
                }
            }
        } catch (e: CancellationException) {
            connectMutex.withLock {
                if (attemptToken == connectAttemptToken) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video websocket error: ${e.message}", e)
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
                    activeUrl = null
                    connectInProgress = false
                }
            }
        }
    }

    suspend fun disconnect() {
        val sessionToClose: DefaultClientWebSocketSession?
        connectMutex.withLock {
            connectAttemptToken += 1
            connectInProgress = false
            activeUrl = null
            sessionToClose = currentSession
            currentSession = null
            _connectionState.value = ConnectionState.Disconnected
        }
        try {
            sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed closing video websocket: ${e.message}")
        }
    }

    fun close() {
        client.close()
    }

    suspend fun sendRawCommand(jsonCommand: String) {
        val session = currentSession ?: return
        try {
            session.send(Frame.Text(jsonCommand))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send raw video command: ${e.message}")
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.endsWith("/control")) trimmed else "$trimmed/control"
    }

    private fun handleBinaryFrame(data: ByteArray) {
        val isJpegFrame = data.size >= 3 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte()
        _videoFrames.value = VideoFrame(
            data = data,
            timestamp = System.currentTimeMillis(),
            isKeyframe = if (isJpegFrame) true else isKeyframeData(data),
            format = if (isJpegFrame) VideoFrameFormat.JPEG else VideoFrameFormat.H26X
        )
    }

    private fun handleTextFrame(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val typeField = jsonElement.jsonObject["type"]?.jsonPrimitive?.content
            if (typeField == null || typeField == "telemetry") {
                _telemetry.value = json.decodeFromString(Telemetry.serializer(), text)
            }
        } catch (e: Exception) {
            Log.v(TAG, "Ignored non-telemetry video frame: ${e.message}")
        }
    }

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
                    val nalType = data[nalIndex].toInt() and 0x1F
                    val nalTypeH265 = (data[nalIndex].toInt() and 0x7E) shr 1
                    if (nalType == 5 || nalType == 7 || nalType == 8 ||
                        nalTypeH265 == 19 || nalTypeH265 == 20 ||
                        nalTypeH265 == 33 || nalTypeH265 == 34
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
