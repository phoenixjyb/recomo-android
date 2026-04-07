package com.recomo.remotecontrol.camviewer.tracking

import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TrackingSender(
    private val host: String = "127.0.0.1",
    private val port: Int = 9999
) {
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private val isConnected = AtomicBoolean(false)
    private val connectInFlight = AtomicBoolean(false)
    @Volatile private var lastConnectAttemptMs = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        if (isConnected.get() || connectInFlight.get()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastConnectAttemptMs < RECONNECT_INTERVAL_MS) {
            return
        }
        lastConnectAttemptMs = now
        connectInFlight.set(true)

        scope.launch {
            try {
                Log.d(TAG, "Connecting to $host:$port")
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket = newSocket
                outputStream = DataOutputStream(newSocket.getOutputStream())
                isConnected.set(true)
                Log.i(TAG, "Connected to tracking receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                isConnected.set(false)
                close()
            } finally {
                connectInFlight.set(false)
            }
        }
    }

    fun send(status: TrackingStatus) {
        if (!isConnected.get()) {
            connect()
            return
        }

        scope.launch {
            try {
                outputStream?.apply {
                    // Protocol:
                    // Magic: 0xBE, 0xEF (2 bytes)
                    // TrackID: Int (4 bytes)
                    // State: Byte (1 byte)
                    // X, Y, W, H: Float (4 * 4 bytes)
                    // Confidence: Float (4 bytes)
                    
                    writeByte(0xBE)
                    writeByte(0xEF)
                    
                    val trackIdInt = status.trackingId.toIntOrNull() ?: 0
                    writeInt(trackIdInt)
                    
                    val stateByte = when(status.state) {
                        TrackingState.PENDING -> 0
                        TrackingState.TRACKING -> 1
                        TrackingState.LOST -> 2
                    }
                    writeByte(stateByte)
                    
                    writeFloat(status.bbox.xOffset)
                    writeFloat(status.bbox.yOffset)
                    writeFloat(status.bbox.width)
                    writeFloat(status.bbox.height)
                    
                    writeFloat(status.confidence)
                    
                    flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}")
                isConnected.set(false)
                close()
            }
        }
    }
    
    fun close() {
        scope.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            socket = null
            outputStream = null
            isConnected.set(false)
        }
    }

    companion object {
        private const val TAG = "TrackingSender"
        private const val CONNECT_TIMEOUT_MS = 1000
        private const val RECONNECT_INTERVAL_MS = 5000L
    }
}
