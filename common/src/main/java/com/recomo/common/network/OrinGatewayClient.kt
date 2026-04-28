package com.recomo.common.network

import android.util.Log
import com.recomo.common.model.ConnectionState
import com.recomo.common.model.SubjectTracking
import com.recomo.common.model.TrackingBoundingBox
import com.recomo.common.model.TargetRoi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val TAG = "OrinGatewayClient"
private const val HEARTBEAT_INTERVAL_MS = 200L

class OrinGatewayClient constructor(
    private val json: Json
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
        engine {
            proxy = null // Bypass system proxy for direct ZeroTier connections
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _robotState = MutableStateFlow<JsonObject?>(null)
    val robotState: StateFlow<JsonObject?> = _robotState.asStateFlow()

    private val _lastStateAtMs = MutableStateFlow(0L)
    val lastStateAtMs: StateFlow<Long> = _lastStateAtMs.asStateFlow()

    private val _stateHz = MutableStateFlow(0.0)
    val stateHz: StateFlow<Double> = _stateHz.asStateFlow()

    private val _subjectTracking = MutableStateFlow<SubjectTracking?>(null)
    val subjectTracking: StateFlow<SubjectTracking?> = _subjectTracking.asStateFlow()

    private val _controlConnected = MutableStateFlow(false)
    private val _stateConnected = MutableStateFlow(false)

    @Volatile private var controlSession: DefaultClientWebSocketSession? = null
    @Volatile private var stateSession: DefaultClientWebSocketSession? = null
    private val sendMutex = Mutex()
    private var lastStateWallMs = 0L
    private var stateHzEma = 0.0

    // Track connection Jobs so we can cancel duplicates (A-C1/C2 fix)
    private var controlJob: kotlinx.coroutines.Job? = null
    private var stateJob: kotlinx.coroutines.Job? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    /** Host reachable via ZeroTier/LAN, extracted from the last `connect(baseUrl)` call.
     * Used to build sibling-port HTTP URLs (trajectory-deploy on :9078, etc). */
    @Volatile private var connectedHost: String? = null

    private val _connectedHostFlow = MutableStateFlow<String?>(null)
    /** Public host for building sibling-port HTTP URLs (e.g. service control on :8083). */
    val connectedHostFlow: StateFlow<String?> = _connectedHostFlow.asStateFlow()

    suspend fun connect(baseUrl: String) {
        connectMutex.withLock {
            if (_connectionState.value is ConnectionState.Connecting ||
                _connectionState.value is ConnectionState.Connected) return
            // Cancel any leftover coroutines from a previous connection
            controlJob?.cancel()
            stateJob?.cancel()
            _connectionState.value = ConnectionState.Connecting
        }

        val raw = baseUrl.trim().trimEnd('/')
        val trimmed = if (raw.contains("://")) raw else "ws://$raw"
        val controlUrl = when {
            trimmed.endsWith("/control") -> trimmed
            trimmed.endsWith("/state") -> trimmed.removeSuffix("/state") + "/control"
            else -> "$trimmed/control"
        }
        val stateUrl = when {
            trimmed.endsWith("/state") -> trimmed
            trimmed.endsWith("/control") -> trimmed.removeSuffix("/control") + "/state"
            else -> "$trimmed/state"
        }

        // Stash the bare host so sibling-port helpers (deployTrajectory on :9078
        // etc) can hit the same Orin over plain HTTP without reparsing.
        connectedHost = runCatching {
            val stripped = trimmed
                .substringAfter("://")
                .substringBefore('/')
            stripped.substringBefore(':').ifBlank { null }
        }.getOrNull()
        _connectedHostFlow.value = connectedHost

        controlJob = scope.launch { connectControl(controlUrl) }
        stateJob = scope.launch { connectState(stateUrl) }
    }

    suspend fun disconnect() {
        // Cancel connection coroutines first to prevent races
        stopHeartbeat()
        controlJob?.cancel()
        stateJob?.cancel()

        try {
            controlSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            Log.w(TAG, "control close error: ${e.message}")
        } finally {
            controlSession = null
            _controlConnected.value = false
        }

        try {
            stateSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        } catch (e: Exception) {
            Log.w(TAG, "state close error: ${e.message}")
        } finally {
            stateSession = null
            _stateConnected.value = false
        }

        connectedHost = null
        _connectedHostFlow.value = null
        updateConnectionState()
    }

    /**
     * Sealed result from [deployTrajectory]. Success carries the Orin-side
     * absolute path where the file landed; Failure carries a human-readable
     * reason suitable for a toast.
     */
    sealed class DeployResult {
        data class Success(val path: String, val bytes: Long) : DeployResult()
        data class Failure(val reason: String) : DeployResult()
    }

    /**
     * Upload a cloud-authored trajectory file to the Orin
     * trajectory-deploy service (port 9078). The service drops the file
     * under `fixed_position_traj_dir` and PnC picks it up with whatever
     * converter matches the extension (TUM/txt/json). After success the
     * caller can issue a normal `FixedPositionCmd("select", stem)`
     * through [sendControl].
     *
     * @param filename   must match `^[A-Za-z0-9_.-]+\.(tum|txt|json)$`.
     * @param content    raw file body (TUM/txt lines, or a JSON string).
     *                   Passed verbatim; the service does no parsing.
     * @param overwrite  overwrite an existing same-named file (default true).
     */
    suspend fun deployTrajectory(
        filename: String,
        content: String,
        overwrite: Boolean = true
    ): DeployResult = withContext(Dispatchers.IO) {
        val host = connectedHost
            ?: return@withContext DeployResult.Failure("gateway host unknown — connect first")
        val url = "http://$host:9078/api/deploy_trajectory"
        val envelope = buildJsonObject {
            put("filename", filename)
            put("overwrite", overwrite)
            put("content", content)
        }
        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), envelope))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Log.w(TAG, "deployTrajectory HTTP ${response.status.value}: $body")
                return@withContext DeployResult.Failure("HTTP ${response.status.value}: $body")
            }
            val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return@withContext DeployResult.Failure("bad response: $body")
            val ok = parsed["ok"]?.jsonPrimitive?.booleanOrNull == true
            if (!ok) {
                val reason = parsed["reason"]?.jsonPrimitive?.contentOrNull ?: body
                return@withContext DeployResult.Failure(reason)
            }
            val path = parsed["path"]?.jsonPrimitive?.contentOrNull ?: ""
            val bytes = parsed["bytes"]?.jsonPrimitive?.longOrNull ?: 0L
            DeployResult.Success(path, bytes)
        } catch (e: Exception) {
            Log.e(TAG, "deployTrajectory failed: ${e.message}", e)
            DeployResult.Failure(e.message ?: "deploy failed")
        }
    }

    suspend fun sendControl(payload: JsonObject) {
        val session = controlSession
        if (session == null || !session.isActive) {
            Log.w(TAG, "sendControl ignored: no active control session")
            return
        }
        try {
            val msgType = payload["type"]?.jsonPrimitive?.content ?: "unknown"
            Log.d(TAG, "sendControl: type=$msgType")
            sendMutex.withLock {
                session.send(Frame.Text(payload.toString()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendControl failed: ${e.message}", e)
        }
    }

    suspend fun sendTargetRoi(roi: TargetRoi) {
        val payload = buildJsonObject {
            put("type", "TargetRoi")
            put("x_offset", roi.xOffset)
            put("y_offset", roi.yOffset)
            put("width", roi.width)
            put("height", roi.height)
        }
        Log.i(TAG, "Sending target_roi: $payload")
        sendControl(payload)
    }

    fun isConnected(): Boolean {
        return _connectionState.value is ConnectionState.Connected
    }

    private suspend fun connectControl(url: String) {
        try {
            client.webSocket(urlString = url) {
                controlSession = this
                _controlConnected.value = true
                updateConnectionState()
                startHeartbeat()
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "control socket error: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "control connect failed")
        } finally {
            stopHeartbeat()
            controlSession = null
            _controlConnected.value = false
            updateConnectionState()
        }
    }

    private suspend fun connectState(url: String) {
        try {
            client.webSocket(urlString = url) {
                stateSession = this
                _stateConnected.value = true
                updateConnectionState()
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        handleStateFrame(frame.readText())
                    } else if (frame is Frame.Close) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "state socket error: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "state connect failed")
        } finally {
            stateSession = null
            _stateConnected.value = false
            updateConnectionState()
        }
    }

    private fun handleStateFrame(text: String) {
        try {
            val now = System.currentTimeMillis()
            val parsed = json.parseToJsonElement(text).jsonObject
            
            // Check if this is a subject_tracking message
            val msgType = parsed["type"]?.jsonPrimitive?.content
            if (msgType == "subject_tracking") {
                val tracking = parseSubjectTracking(parsed)
                if (tracking != null) {
                    _subjectTracking.value = tracking
                    Log.d(TAG, "Tracking update: state=${tracking.state}, bbox=(${tracking.bbox.xOffset},${tracking.bbox.yOffset})")
                }
            } else {
                // Regular robot state message (may include embedded tracking)
                val trackingObj = parsed["subject_tracking"]?.jsonObject
                if (trackingObj != null) {
                    val tracking = parseSubjectTracking(trackingObj)
                    if (tracking != null) {
                        _subjectTracking.value = tracking
                        Log.d(TAG, "Tracking update: state=${tracking.state}, bbox=(${tracking.bbox.xOffset},${tracking.bbox.yOffset})")
                    }
                } else {
                    // No tracking in this state update - clear stale data
                    if (_subjectTracking.value != null) {
                        Log.d(TAG, "Clearing stale tracking data")
                        _subjectTracking.value = null
                    }
                }
                _robotState.value = parsed
                // Only update lastStateAtMs for actual robot state, not tracking-only messages
                _lastStateAtMs.value = now
            }
            if (lastStateWallMs > 0L) {
                val dt = (now - lastStateWallMs).coerceAtLeast(1L)
                val instHz = 1000.0 / dt.toDouble()
                stateHzEma = if (stateHzEma <= 0.0) instHz else (stateHzEma * 0.8 + instHz * 0.2)
                _stateHz.value = stateHzEma
            }
            lastStateWallMs = now
        } catch (e: Exception) {
            Log.w(TAG, "State frame parse error: ${e.message}")
        }
    }

    private fun parseSubjectTracking(payload: JsonObject): SubjectTracking? {
        return try {
            val trackingId = payload["tracking_id"]?.jsonPrimitive?.content ?: return null
            val state = payload["state"]?.jsonPrimitive?.content ?: return null
            val bbox = payload["bbox"]?.jsonObject ?: return null
            val xOffset = bbox["x_offset"]?.jsonPrimitive?.intOrNull ?: return null
            val yOffset = bbox["y_offset"]?.jsonPrimitive?.intOrNull ?: return null
            val width = bbox["width"]?.jsonPrimitive?.intOrNull ?: return null
            val height = bbox["height"]?.jsonPrimitive?.intOrNull ?: return null
            val confidence = payload["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f
            SubjectTracking(
                trackingId = trackingId,
                state = state,
                bbox = TrackingBoundingBox(
                    xOffset = xOffset,
                    yOffset = yOffset,
                    width = width,
                    height = height
                ),
                confidence = confidence
            )
        } catch (e: Exception) {
            Log.w(TAG, "subject_tracking parse error: ${e.message}")
            null
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob != null) return
        heartbeatJob = scope.launch {
            while (true) {
                try {
                    sendControl(buildJsonObject { put("type", "Heartbeat") })
                } catch (e: Exception) {
                    Log.w(TAG, "heartbeat send error: ${e.message}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun updateConnectionState() {
        val controlOk = _controlConnected.value
        val stateOk = _stateConnected.value
        val next = when {
            controlOk && stateOk -> ConnectionState.Connected
            controlOk || stateOk -> ConnectionState.Connecting
            else -> ConnectionState.Disconnected
        }
        // Allow Error → Disconnected transition so reconnect can be triggered.
        // Previously, Error state was sticky and blocked all transitions.
        _connectionState.value = next
        if (!stateOk) {
            lastStateWallMs = 0L
            _lastStateAtMs.value = 0L
            _stateHz.value = 0.0
            stateHzEma = 0.0
        }
    }
}
