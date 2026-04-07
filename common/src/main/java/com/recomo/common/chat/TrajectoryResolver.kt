package com.recomo.common.chat

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private const val TAG = "TrajectoryResolver"

enum class TrajectoryPayloadFormat {
    Frames,
    Foi
}

/**
 * Downloads trajectory JSON from a URL and parses it into a format
 * compatible with the existing preview/execution pipeline.
 *
 * Expected JSON format matches the existing SessionDetail/FrameRecord schema:
 * ```json
 * {
 *   "session_id": "traj_xxx",
 *   "session_name": "...",
 *   "frames": [
 *     {"name":"kf_1", "sequenceIndex":0, "baseX":1.2, "baseY":0.5, "baseYaw":0.3,
 *      "armQ":[30,45,60], "gimbalQ":[0,-10,5], "timestampMs":0, ...}
 *   ]
 * }
 * ```
 */
class TrajectoryResolver {

    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Download and parse a trajectory from a URL.
     * @return Parsed JSON object, or null on failure.
     *
     * The caller (ViewModel) is responsible for converting this into
     * a SessionDetail and feeding it to the preview pipeline.
     */
    suspend fun downloadTrajectory(url: String): TrajectoryDownloadResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Downloading trajectory from $url")
            val response = client.get(url)
            val body = response.bodyAsText()
            val obj = json.parseToJsonElement(body).jsonObject

            val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val sessionName = obj["session_name"]?.jsonPrimitive?.contentOrNull ?: sessionId
            val frames = obj["frames"]?.jsonArray
            val fois = obj["fois"]?.jsonArray
            val payload = when {
                frames != null && frames.isNotEmpty() -> frames to TrajectoryPayloadFormat.Frames
                fois != null && fois.isNotEmpty() -> fois to TrajectoryPayloadFormat.Foi
                else -> null
            }

            if (payload == null) {
                Log.w(TAG, "Trajectory has no frames: $sessionId")
                return@withContext TrajectoryDownloadResult.Error("Trajectory has no frames")
            }

            Log.i(TAG, "Downloaded trajectory $sessionId: ${payload.first.size} records")
            TrajectoryDownloadResult.Success(
                sessionId = sessionId,
                sessionName = sessionName,
                framesJson = payload.first,
                rawJson = obj,
                payloadFormat = payload.second
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download trajectory: ${e.message}", e)
            TrajectoryDownloadResult.Error(e.message ?: "Download failed")
        }
    }
}

sealed class TrajectoryDownloadResult {
    data class Success(
        val sessionId: String,
        val sessionName: String,
        val framesJson: JsonArray,
        val rawJson: JsonObject,
        val payloadFormat: TrajectoryPayloadFormat = TrajectoryPayloadFormat.Frames
    ) : TrajectoryDownloadResult()

    data class Error(val message: String) : TrajectoryDownloadResult()
}
