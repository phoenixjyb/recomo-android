package com.recomo.user.control

data class UserMapLocalizationState(
    val availableLocations: List<String> = emptyList(),
    val selectedLocation: String? = null,
    val availableMapAssets: List<String> = emptyList(),
    val selectedMapAsset: String? = null,
    val robotPoseOk: Boolean = false,
    val localized: Boolean = false,
    val latestAction: String? = null,
    val latestMessage: String? = null,
    val isBusy: Boolean = false,
    /** Elapsed ms since the last prepareLocalization() was invoked; null if never triggered. */
    val prepareElapsedMs: Long? = null,
    /** True when robot_pose_ok has held steady with pose deltas below threshold for [poseStableHoldMs]. */
    val poseStable: Boolean = false,
    /** How long the current stable state has held (ms). 0 if not stable. */
    val poseStableAgeMs: Long = 0L,
    /** True when the last map/localization action returned success=false. */
    val lastActionFailed: Boolean = false,
    val lastActionError: String? = null
)
