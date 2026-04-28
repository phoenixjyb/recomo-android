package com.recomo.user.ui.screens.touch

data class TouchControlWorkspaceState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val speedMode: TouchControlSpeedMode = TouchControlSpeedMode.Normal,
    /** Effective speed profile: user-configured overrides merged over the enum
     *  defaults. Always use this instead of [speedMode]`.profile` when reading
     *  actual velocity values. */
    val effectiveProfile: TouchControlSpeedProfile = TouchControlSpeedMode.Normal.profile,
    val showGrid: Boolean = false,
    val estopActive: Boolean = false,
    val freezeAllActive: Boolean = false,
    val estopCooldownRemainingMs: Long = 0L,
    val deadmanOk: Boolean = false,
    val commOk: Boolean = false,
    val baseYawDeg: Double? = null,
    val armJointAnglesDeg: List<Double> = emptyList(),
    val gimbalAnglesDeg: List<Double> = emptyList()
) {
    val canClearEstop: Boolean get() = estopActive && estopCooldownRemainingMs <= 0L
}

enum class TouchControlSpeedMode(
    val labelKey: String,
    val profile: TouchControlSpeedProfile
) {
    Slow(
        labelKey = "slow",
        profile = TouchControlSpeedProfile(
            baseVelocityMps = 0.18,
            armVelocityRadS = 0.45,
            gimbalVelocityRadS = 0.45
        )
    ),
    Normal(
        labelKey = "normal",
        profile = TouchControlSpeedProfile(
            baseVelocityMps = 0.35,
            armVelocityRadS = 0.85,
            gimbalVelocityRadS = 0.85
        )
    ),
    Fast(
        labelKey = "fast",
        profile = TouchControlSpeedProfile(
            baseVelocityMps = 0.55,
            armVelocityRadS = 1.20,
            gimbalVelocityRadS = 1.15
        )
    )
}

data class TouchControlSpeedProfile(
    val baseVelocityMps: Double,
    val armVelocityRadS: Double,
    val gimbalVelocityRadS: Double
)
