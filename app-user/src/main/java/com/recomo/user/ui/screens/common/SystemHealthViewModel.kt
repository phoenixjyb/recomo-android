package com.recomo.user.ui.screens.common

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.network.OrinGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "SystemHealth"
private const val SERVICE_CONTROL_PORT = 8083
private const val RESOURCE_POLL_INTERVAL_MS = 5_000L
private const val SERVICE_POLL_INTERVAL_MS = 10_000L

@HiltViewModel
class SystemHealthViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }) }
        engine { requestTimeout = 3_000 }
    }

    private val _clockSyncStatus = MutableStateFlow<ClockSyncStatus?>(null)
    val clockSyncStatus: StateFlow<ClockSyncStatus?> = _clockSyncStatus.asStateFlow()

    private val _orinResources = MutableStateFlow<OrinResources?>(null)
    val orinResources: StateFlow<OrinResources?> = _orinResources.asStateFlow()

    private val _serviceHealthSummary = MutableStateFlow<ServiceHealthSummary?>(null)
    val serviceHealthSummary: StateFlow<ServiceHealthSummary?> = _serviceHealthSummary.asStateFlow()

    val stateHz: StateFlow<Double> = gatewayClient.stateHz

    val robotState = gatewayClient.robotState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val connectionState = gatewayClient.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.recomo.common.model.ConnectionState.Disconnected)

    init {
        // Clock sync from robot state
        viewModelScope.launch {
            gatewayClient.robotState.collect { state ->
                val orinTs = state?.get("timestamp_ms")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                if (orinTs != null) {
                    val tabletTs = System.currentTimeMillis()
                    val rawOffset = orinTs - tabletTs

                    val clockSync = state?.get("clock_sync")
                    val baselineSet = clockSync?.jsonObject?.get("baseline_set")
                        ?.jsonPrimitive?.booleanOrNull ?: false
                    val baselineOffset = clockSync?.jsonObject?.get("baseline_offset_ms")
                        ?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

                    val drift = if (baselineSet) rawOffset + baselineOffset else 0L
                    val absDrift = kotlin.math.abs(drift)
                    val severity = when {
                        !baselineSet -> SyncSeverity.OK
                        absDrift < 500 -> SyncSeverity.OK
                        absDrift < 2000 -> SyncSeverity.WARNING
                        absDrift < 5000 -> SyncSeverity.ERROR
                        else -> SyncSeverity.CRITICAL
                    }
                    _clockSyncStatus.value = ClockSyncStatus(
                        offsetMs = rawOffset,
                        baselineMs = baselineOffset,
                        baselineSet = baselineSet,
                        driftMs = drift,
                        severity = severity
                    )
                }
            }
        }

        // Resource polling
        viewModelScope.launch {
            while (isActive) {
                val host = gatewayClient.connectedHostFlow.value
                if (host != null) {
                    try {
                        val url = "http://$host:$SERVICE_CONTROL_PORT/api/system/resources"
                        val response = httpClient.get(url)
                        if (response.status.isSuccess()) {
                            _orinResources.value = parseOrinResources(response.body())
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Resource poll failed: ${e.message}")
                    }
                }
                delay(RESOURCE_POLL_INTERVAL_MS)
            }
        }

        // Service health polling
        viewModelScope.launch {
            while (isActive) {
                val host = gatewayClient.connectedHostFlow.value
                if (host != null) {
                    try {
                        val url = "http://$host:$SERVICE_CONTROL_PORT/api/services/status"
                        val response = httpClient.get(url)
                        if (response.status.isSuccess()) {
                            _serviceHealthSummary.value = parseServiceHealth(response.body())
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Service poll failed: ${e.message}")
                    }
                }
                delay(SERVICE_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }

    private fun parseOrinResources(json: JsonObject): OrinResources? {
        return try {
            val cpuObj = json["cpu"]?.jsonObject ?: return null
            val cpuOverall = cpuObj["overall_pct"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                ?: return null
            val cpuCores = cpuObj["cores"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.doubleOrNull?.toFloat() }
                ?: emptyList()
            val gpuObj = json["gpu"]?.jsonObject
            val gpuLoad = gpuObj?.get("load_pct")?.jsonPrimitive?.doubleOrNull?.toFloat()
            val memObj = json["memory"]?.jsonObject ?: return null
            val memUsedPct = memObj["used_pct"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                ?: return null
            val memUsedMb = memObj["used_mb"]?.jsonPrimitive?.intOrNull ?: 0
            val memTotalMb = memObj["total_mb"]?.jsonPrimitive?.intOrNull ?: 0
            val thermalObj = json["thermal"]?.jsonObject
            val cpuTempC = thermalObj?.get("cpu_temp_c")?.jsonPrimitive?.doubleOrNull?.toFloat()
            val gpuTempC = thermalObj?.get("gpu_temp_c")?.jsonPrimitive?.doubleOrNull?.toFloat()
            OrinResources(
                cpuOverallPct = cpuOverall,
                cpuCores = cpuCores,
                gpuLoadPct = gpuLoad,
                memoryUsedPct = memUsedPct,
                memoryUsedMb = memUsedMb,
                memoryTotalMb = memTotalMb,
                cpuTempC = cpuTempC,
                gpuTempC = gpuTempC
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse OrinResources: ${e.message}")
            null
        }
    }

    private fun parseServiceHealth(json: JsonObject): ServiceHealthSummary {
        val entries = json.entries.map { (key, value) ->
            val obj = value.jsonObject
            val running = obj["status"]?.jsonPrimitive?.contentOrNull == "running" ||
                obj["running"]?.jsonPrimitive?.booleanOrNull == true
            val displayName = obj["display_name"]?.jsonPrimitive?.contentOrNull
                ?: key.replace("_", " ").replaceFirstChar { it.uppercase() }
            ServiceEntry(id = key, displayName = displayName, running = running)
        }

        // Group by tier/prefix for readability
        val coreIds = setOf("recomo_gateway_ws", "gateway", "recomo_gateway")
        val videoIds = setOf("video_management", "recomo_video_manager")
        val core = entries.filter { it.id in coreIds || it.displayName.contains("gateway", true) }
        val video = entries.filter { it.id in videoIds || it.displayName.contains("video", true) }
        val other = entries - core.toSet() - video.toSet()

        val groups = mutableMapOf<String, GroupHealth>()
        if (core.isNotEmpty()) groups["Core"] = GroupHealth("Core", core, core.all { it.running })
        if (video.isNotEmpty()) groups["Video"] = GroupHealth("Video", video, video.all { it.running })
        if (other.isNotEmpty()) groups["Other"] = GroupHealth("Other", other, other.all { it.running })

        val all = entries
        return ServiceHealthSummary(
            totalServices = all.size,
            runningServices = all.count { it.running },
            groups = groups
        )
    }
}
