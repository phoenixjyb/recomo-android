package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.common.model.ConnectionState
import com.recomo.common.network.OrinGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

@HiltViewModel
class UserMapLocalizationViewModel @Inject constructor(
    private val gatewayClient: OrinGatewayClient
) : ViewModel() {
    private val selectedLocationInternal = MutableStateFlow<String?>(null)
    private val selectedMapAssetInternal = MutableStateFlow<String?>(null)
    private val busyInternal = MutableStateFlow(false)
    private val prepareStartedAt = MutableStateFlow<Long?>(null)
    private val clockTick = MutableStateFlow(0L)
    private var busyToken = 0

    // Pose-stability tracking. Updated from robotState collector.
    private data class PoseSample(val tMs: Long, val x: Double, val y: Double, val yawDeg: Double)
    private var lastPose: PoseSample? = null
    private var stableSinceMs: Long? = null
    private val stableAgeMs = MutableStateFlow(0L)

    val state: StateFlow<UserMapLocalizationState> = combine(
        gatewayClient.robotState,
        selectedLocationInternal,
        selectedMapAssetInternal,
        busyInternal,
        combine(prepareStartedAt, clockTick, stableAgeMs) { started, _, age -> started to age }
    ) { robotState, selectedLocation, selectedMapAsset, isBusy, (started, age) ->
        parseState(robotState, selectedLocation, selectedMapAsset, isBusy, started, age)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UserMapLocalizationState()
    )

    init {
        viewModelScope.launch {
            gatewayClient.connectionState.collect { connectionState ->
                if (connectionState is ConnectionState.Connected) {
                    refreshMapList()
                } else if (connectionState !is ConnectionState.Connecting) {
                    busyInternal.value = false
                }
            }
        }
        viewModelScope.launch {
            gatewayClient.robotState.collect { robotState ->
                val hasMapAck = robotState?.get("map_detail") != null
                val hasMapsPayload = robotState?.get("maps") != null
                if (hasMapAck || hasMapsPayload) {
                    busyInternal.value = false
                }
                updatePoseStability(robotState)
            }
        }
        // 1Hz tick so prepareElapsedMs / poseStableAgeMs advance even when
        // robotState and pose are silent.
        viewModelScope.launch {
            while (true) {
                delay(500L)
                clockTick.value = System.currentTimeMillis()
                stableSinceMs?.let { stableAgeMs.value = System.currentTimeMillis() - it }
            }
        }
    }

    val availableLocations: StateFlow<List<String>> = state
        .map { it.availableLocations }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedLocation: StateFlow<String?> = state
        .map { it.selectedLocation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val availableMapAssets: StateFlow<List<String>> = state
        .map { it.availableMapAssets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedMapAsset: StateFlow<String?> = state
        .map { it.selectedMapAsset }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val robotPoseOk: StateFlow<Boolean> = state
        .map { it.robotPoseOk }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val localized: StateFlow<Boolean> = state
        .map { it.localized }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val latestAction: StateFlow<String?> = state
        .map { it.latestAction }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val latestMessage: StateFlow<String?> = state
        .map { it.latestMessage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectLocation(locationId: String) {
        val trimmed = locationId.trim()
        if (trimmed.isBlank()) return
        selectedLocationInternal.value = trimmed
        selectedMapAssetInternal.value = null
        markBusy()
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "MapCmd")
                    put("action", "select")
                    put("location_id", trimmed)
                }
            )
        }
    }

    fun selectMapAsset(mapName: String) {
        val trimmed = mapName.trim()
        if (trimmed.isBlank()) return
        selectedMapAssetInternal.value = trimmed
        val locationId = selectedLocationInternal.value ?: return
        markBusy()
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "MapCmd")
                    put("action", "select")
                    put("location_id", locationId)
                    put("map_name", trimmed)
                }
            )
        }
    }

    fun prepareLocalization(threshold: Double = 0.30) {
        val locationId = selectedLocationInternal.value ?: return
        markBusy()
        // Reset stability tracking for the new prepare attempt.
        prepareStartedAt.value = System.currentTimeMillis()
        lastPose = null
        stableSinceMs = null
        stableAgeMs.value = 0L
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", "prepare_localization")
                    put("location_id", locationId)
                    put("threshold", threshold.coerceIn(0.0, 1.0))
                    selectedMapAssetInternal.value?.takeIf { it.isNotBlank() }?.let {
                        put("map_name", it)
                    }
                }
            )
        }
    }

    fun dismissLocalization() {
        markBusy()
        prepareStartedAt.value = null
        lastPose = null
        stableSinceMs = null
        stableAgeMs.value = 0L
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "LocalizationCmd")
                    put("action", "dismiss_localization")
                }
            )
        }
    }

    fun refreshMapList() {
        if (!gatewayClient.isConnected()) return
        markBusy()
        viewModelScope.launch {
            gatewayClient.sendControl(
                buildJsonObject {
                    put("type", "MapCmd")
                    put("action", "list")
                }
            )
        }
    }

    fun isConnected(): Boolean = gatewayClient.isConnected()

    private fun updatePoseStability(robotState: JsonObject?) {
        val mapPose = robotState?.get("map_pose")?.jsonObject
        val ok = mapPose?.get("ok")?.jsonPrimitive?.booleanOrNull == true
        if (!ok) {
            lastPose = null
            stableSinceMs = null
            stableAgeMs.value = 0L
            return
        }
        val pos = mapPose?.get("position")?.jsonObject
        val x = pos?.get("x")?.jsonPrimitive?.doubleOrNull ?: return
        val y = pos?.get("y")?.jsonPrimitive?.doubleOrNull ?: return
        val yawRad = mapPose?.get("yaw")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val yawDeg = Math.toDegrees(yawRad)
        val now = System.currentTimeMillis()
        val prev = lastPose
        val dx = if (prev != null) abs(x - prev.x) else 0.0
        val dy = if (prev != null) abs(y - prev.y) else 0.0
        // Wrap yaw delta to [-180,180].
        val rawDyaw = if (prev != null) (yawDeg - prev.yawDeg) else 0.0
        val dyaw = abs(((rawDyaw + 180.0) % 360.0 + 360.0) % 360.0 - 180.0)
        val dt = if (prev != null) (now - prev.tMs) else 0L
        lastPose = PoseSample(now, x, y, yawDeg)

        if (prev == null) return

        // Thresholds: 2cm position, 0.5 deg yaw between consecutive samples.
        // (Robot-state cadence is ~5-10 Hz, so this catches drift but ignores quantization noise.)
        val sampleStable = dx <= POS_EPS && dy <= POS_EPS && dyaw <= YAW_EPS_DEG
        if (sampleStable) {
            if (stableSinceMs == null) {
                // Back-date by dt so a quiet pair of samples counts immediately.
                stableSinceMs = now - dt.coerceAtLeast(0L)
            }
            stableAgeMs.value = now - (stableSinceMs ?: now)
        } else {
            stableSinceMs = null
            stableAgeMs.value = 0L
        }
    }

    private fun parseState(
        robotState: JsonObject?,
        selectedLocation: String?,
        selectedMapAsset: String?,
        isBusy: Boolean,
        prepareStartedAtMs: Long?,
        stableAgeValue: Long
    ): UserMapLocalizationState {
        val maps = robotState?.get("maps")?.jsonObject
        val availableLocations = maps?.get("locations")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: maps?.get("available")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val selectedLocationValue = maps?.get("current_location")?.jsonPrimitive?.contentOrNull
            ?: maps?.get("current")?.jsonPrimitive?.contentOrNull
            ?: selectedLocation
        val selectedMapAssetValue = maps?.get("current_map")?.jsonPrimitive?.contentOrNull
            ?: selectedMapAsset
        val directAssets = maps?.get("map_assets")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        val fallbackAssets = maps?.get("map_assets_by_location")?.jsonObject
            ?.get(selectedLocationValue ?: "")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val availableAssets = directAssets ?: fallbackAssets

        val mapDetail = robotState?.get("map_detail")?.jsonObject
            ?: maps?.get("detail")?.jsonObject
        val latestAction = mapDetail?.get("action")?.jsonPrimitive?.contentOrNull
        val actionSuccess = mapDetail?.get("success")?.jsonPrimitive?.booleanOrNull
        val actionError = mapDetail?.get("error")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val latestMessage = when (actionSuccess) {
            true -> latestAction?.let { "$it: OK" }
            false -> latestAction?.let { "$it: ${actionError ?: "failed"}" }
            else -> latestAction
        }
        val robotPoseOk = (robotState?.get("topic_health")?.jsonObject?.get("robot_pose_ok")
            ?: robotState?.get("robot_pose_ok"))?.jsonPrimitive?.booleanOrNull ?: false

        val poseStable = robotPoseOk && stableAgeValue >= POSE_STABLE_HOLD_MS
        val prepareElapsed = prepareStartedAtMs?.let { System.currentTimeMillis() - it }

        return UserMapLocalizationState(
            availableLocations = availableLocations,
            selectedLocation = selectedLocationValue,
            availableMapAssets = availableAssets,
            selectedMapAsset = selectedMapAssetValue,
            robotPoseOk = robotPoseOk,
            localized = poseStable,
            latestAction = latestAction,
            latestMessage = latestMessage,
            isBusy = isBusy,
            prepareElapsedMs = prepareElapsed,
            poseStable = poseStable,
            poseStableAgeMs = stableAgeValue,
            lastActionFailed = actionSuccess == false,
            lastActionError = actionError
        )
    }

    private fun markBusy() {
        val token = ++busyToken
        busyInternal.value = true
        viewModelScope.launch {
            delay(2_000)
            if (busyToken == token) {
                busyInternal.value = false
            }
        }
    }

    companion object {
        private const val POS_EPS = 0.02 // 2 cm between consecutive samples
        private const val YAW_EPS_DEG = 0.5
        private const val POSE_STABLE_HOLD_MS = 3_000L
    }
}
