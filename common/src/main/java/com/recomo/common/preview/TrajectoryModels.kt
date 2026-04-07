package com.recomo.common.preview

import com.recomo.common.model.FrameRecord

data class TrajectorySample(
    val tSec: Double,
    val baseX: Double,
    val baseY: Double,
    val baseYaw: Double,
    val armQ: List<Double>,
    val gimbalQ: List<Double>
)

data class TrajectoryPreview(
    val samples: List<TrajectorySample>,
    val keyframes: List<FrameRecord>,
    val totalDurationSec: Double
)

data class PreviewLimits(
    val baseLinearMps: Double = 0.25,
    val baseYawRadps: Double = 0.6,
    val armRadps: Double = 0.8,
    val gimbalRadps: Double = 1.0,
    val sampleHz: Double = 30.0
)
