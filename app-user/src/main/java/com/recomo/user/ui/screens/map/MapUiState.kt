package com.recomo.user.ui.screens.map

data class MapLocalizationUiState(
    val availableLocations: List<String> = emptyList(),
    val availableMapAssets: List<String> = emptyList(),
    val selectedLocation: String? = null,
    val selectedMapAsset: String? = null,
    val robotPoseOk: Boolean = false,
    val poseStable: Boolean = false,
    val poseStableAgeMs: Long = 0L,
    val prepareElapsedMs: Long? = null,
    val lastActionFailed: Boolean = false,
    val lastActionError: String? = null,
    val localizationStatus: String = "Ready",
    val backendActionMessage: String? = null,
    val isBusy: Boolean = false
)

/**
 * Honest MapMatchDialog state machine.
 *
 * Detecting → gateway hasn't returned a map list yet
 * Select    → maps available, waiting for user choice
 * Preparing → prepare_localization sent, /robot/pose not publishing yet
 * PosePublishing → /robot/pose has a publisher but pose is still moving
 * Stable    → pose has held steady for ≥3s (honest "ready to run")
 * TimedOut  → preparing > 30s without Stable
 * Failed    → gateway returned success=false on prepare/select
 */
enum class MapMatchPhase {
    Detecting,
    Select,
    Preparing,
    PosePublishing,
    Stable,
    TimedOut,
    Failed
}

data class MapMatchOption(
    val id: String,
    val name: String,
    val isRecommended: Boolean = false
)

data class MapMatchDialogUiState(
    val phase: MapMatchPhase = MapMatchPhase.Detecting,
    val selectedLocation: String? = null,
    val selectedMapAsset: String? = null,
    val availableLocations: List<String> = emptyList(),
    val availableMapAssets: List<String> = emptyList(),
    val message: String? = null,
    val maps: List<MapMatchOption> = emptyList(),
    val selectedMapId: String? = null,
    val motionName: String = "",
    /** Elapsed ms since prepareLocalization() was invoked. Drives timeout. */
    val prepareElapsedMs: Long = 0L,
    /** How long pose has been stable (ms). Shown on Stable phase. */
    val poseStableAgeMs: Long = 0L,
    /** True if Skip should be disabled (e.g. LivePnC scenes that require localization). */
    val skipBlocked: Boolean = false,
    /** Reason shown when skip is disabled. */
    val skipBlockedReason: String? = null,
    /** Error string if phase == Failed. */
    val errorMessage: String? = null
)

fun MapLocalizationUiState.toMapMatchOptions(): List<MapMatchOption> {
    // NO fake confidence scores. The gateway doesn't publish a real SLAM match
    // score yet; pretending otherwise lets the dialog show "94% ready" while
    // voxel_slam is still searching. Until there's a real signal, we only
    // surface the map name + an optional "recommended" badge for the
    // motion-linked / currently-selected map.
    return availableMapAssets.map { asset ->
        MapMatchOption(
            id = asset,
            name = asset,
            isRecommended = asset == selectedMapAsset
        )
    }
}

/**
 * Derive the honest dialog phase from map state + pose stability.
 * Kept here so both the dialog and any tests share the same logic.
 */
fun deriveMapMatchPhase(
    state: MapLocalizationUiState,
    prepareElapsedMs: Long?
): MapMatchPhase {
    if (state.lastActionFailed) return MapMatchPhase.Failed
    if (state.poseStable) return MapMatchPhase.Stable
    if (prepareElapsedMs != null) {
        if (prepareElapsedMs > PREPARE_TIMEOUT_MS) return MapMatchPhase.TimedOut
        return if (state.robotPoseOk) MapMatchPhase.PosePublishing else MapMatchPhase.Preparing
    }
    if (state.availableLocations.isEmpty() && state.availableMapAssets.isEmpty()) {
        return MapMatchPhase.Detecting
    }
    return MapMatchPhase.Select
}

const val PREPARE_TIMEOUT_MS = 30_000L
