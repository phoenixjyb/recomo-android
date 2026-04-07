package com.recomo.common.preview

import com.recomo.common.model.FrameRecord
import com.recomo.common.model.SessionDetail
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object TrajectoryInterpolator {
    private const val MIN_SEGMENT_SEC = 0.2
    private const val MAX_SEGMENT_SEC = 6.0

    fun buildPreview(
        session: SessionDetail,
        limits: PreviewLimits = PreviewLimits()
    ): TrajectoryPreview? {
        val frames = session.frames.sortedBy { it.sequenceIndex }
        if (frames.isEmpty()) return null
        if (frames.size == 1) {
            val single = frames.first()
            val armSingle = maybeConvertJointUnitsToRad(listOf(normalizeJointList(single.armQ))).firstOrNull()
                ?: emptyList()
            val gimbalSingle = maybeConvertJointUnitsToRad(listOf(normalizeJointList(single.gimbalQ))).firstOrNull()
                ?: emptyList()
            return TrajectoryPreview(
                samples = listOf(
                    TrajectorySample(
                        tSec = 0.0,
                        baseX = single.baseX,
                        baseY = single.baseY,
                        baseYaw = wrapAngle(single.baseYaw),
                        armQ = armSingle,
                        gimbalQ = gimbalSingle
                    )
                ),
                keyframes = frames,
                totalDurationSec = 0.0
            )
        }

        val baseX = frames.map { it.baseX }
        val baseY = frames.map { it.baseY }
        val baseYaw = unwrapAngles(frames.map { it.baseYaw })
        val armQ = maybeConvertJointUnitsToRad(normalizeJointMatrix(frames.map { it.armQ }))
        val gimbalQ = maybeConvertJointUnitsToRad(normalizeJointMatrix(frames.map { it.gimbalQ }))

        val samples = mutableListOf<TrajectorySample>()
        var timeCursor = 0.0

        for (i in 0 until frames.size - 1) {
            val currentFrame = frames[i]
            val ease = currentFrame.ease
            
            // Use explicit transition_s if provided, otherwise calculate from velocity limits
            val segTime = currentFrame.transitionS ?: segmentDuration(
                baseX[i], baseY[i], baseYaw[i],
                baseX[i + 1], baseY[i + 1], baseYaw[i + 1],
                armQ[i], armQ[i + 1],
                gimbalQ[i], gimbalQ[i + 1],
                limits
            )
            val steps = max(2, ceil(segTime * limits.sampleHz).toInt())
            val lastIndex = if (i == frames.size - 2) steps - 1 else steps - 2

            for (step in 0..lastIndex) {
                val tLinear = step.toDouble() / (steps - 1).coerceAtLeast(1)
                val tEased = applyEasing(tLinear, ease)  // Apply easing curve
                val sample = TrajectorySample(
                    tSec = timeCursor + tLinear * segTime,  // Timeline uses linear time
                    baseX = catmull(baseX, i, tEased),
                    baseY = catmull(baseY, i, tEased),
                    baseYaw = wrapAngle(catmull(baseYaw, i, tEased)),
                    armQ = buildJointSample(armQ, i, tEased, wrap = true),
                    gimbalQ = buildJointSample(gimbalQ, i, tEased, wrap = true)
                )
                samples.add(sample)
            }
            timeCursor += segTime
            
            // Add dwell pause at the end of this segment (at frame i+1)
            val nextFrame = frames[i + 1]
            val dwellTime = nextFrame.dwellS ?: 0.0
            if (dwellTime > 0.0 && samples.isNotEmpty()) {
                val dwellSteps = max(1, ceil(dwellTime * limits.sampleHz).toInt())
                val lastSample = samples.last()
                // Hold position during dwell
                for (dwellStep in 1..dwellSteps) {
                    samples.add(
                        lastSample.copy(tSec = timeCursor + dwellStep * (dwellTime / dwellSteps))
                    )
                }
                timeCursor += dwellTime
            }
        }

        return TrajectoryPreview(
            samples = samples,
            keyframes = frames,
            totalDurationSec = timeCursor
        )
    }

    private fun segmentDuration(
        x0: Double,
        y0: Double,
        yaw0: Double,
        x1: Double,
        y1: Double,
        yaw1: Double,
        arm0: List<Double>,
        arm1: List<Double>,
        gimbal0: List<Double>,
        gimbal1: List<Double>,
        limits: PreviewLimits
    ): Double {
        val baseTime = max(
            abs(x1 - x0) / limits.baseLinearMps,
            abs(y1 - y0) / limits.baseLinearMps
        )
        val yawTime = abs(yaw1 - yaw0) / limits.baseYawRadps
        val armTime = arm0.zip(arm1) { a, b -> abs(b - a) / limits.armRadps }.maxOrNull() ?: 0.0
        val gimbalTime = gimbal0.zip(gimbal1) { a, b -> abs(b - a) / limits.gimbalRadps }.maxOrNull() ?: 0.0
        return clamp(max(max(baseTime, yawTime), max(armTime, gimbalTime)), MIN_SEGMENT_SEC, MAX_SEGMENT_SEC)
    }

    private fun buildJointSample(
        joints: List<List<Double>>,
        index: Int,
        t: Double,
        wrap: Boolean
    ): List<Double> {
        if (joints.isEmpty()) return emptyList()
        val out = MutableList(joints[0].size) { 0.0 }
        for (j in joints[0].indices) {
            val value = catmull(joints.map { it[j] }, index, t)
            out[j] = if (wrap) wrapAngle(value) else value
        }
        return out
    }

    private fun catmull(values: List<Double>, index: Int, t: Double): Double {
        val p0 = values.getOrNull(index - 1) ?: values[index]
        val p1 = values[index]
        val p2 = values[index + 1]
        val p3 = values.getOrNull(index + 2) ?: values[index + 1]
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * ((2 * p1) +
            (-p0 + p2) * t +
            (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
            (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    private fun normalizeJointMatrix(values: List<List<Double>>): List<List<Double>> {
        val width = values.maxOfOrNull { it.size } ?: 0
        if (width == 0) return emptyList()
        return values.map { normalizeJointList(it, width) }
    }

    private fun normalizeJointList(values: List<Double>, width: Int? = null): List<Double> {
        val target = width ?: values.size
        if (target == 0) return emptyList()
        if (values.size == target) return values
        val padded = values.toMutableList()
        while (padded.size < target) {
            padded.add(padded.lastOrNull() ?: 0.0)
        }
        if (padded.size > target) {
            return padded.take(target)
        }
        return padded
    }

    private fun maybeConvertJointUnitsToRad(values: List<List<Double>>): List<List<Double>> {
        if (values.isEmpty()) return values
        val flattened = values.asSequence().flatten().filter { it.isFinite() }.map { abs(it) }.toList()
        if (flattened.isEmpty()) return values
        val likelyDegrees = flattened.maxOrNull()?.let { it > (2.0 * PI + 0.2) } ?: false
        if (!likelyDegrees) return values
        return values.map { row -> row.map { it * PI / 180.0 } }
    }

    private fun unwrapAngles(values: List<Double>): List<Double> {
        if (values.isEmpty()) return emptyList()
        val out = mutableListOf(values.first())
        var prev = values.first()
        for (i in 1 until values.size) {
            var v = values[i]
            var delta = v - prev
            while (delta > PI) {
                v -= 2 * PI
                delta = v - prev
            }
            while (delta < -PI) {
                v += 2 * PI
                delta = v - prev
            }
            out.add(v)
            prev = v
        }
        return out
    }

    private fun wrapAngle(value: Double): Double {
        var v = value
        while (v > PI) v -= 2 * PI
        while (v < -PI) v += 2 * PI
        return v
    }

    private fun clamp(value: Double, minValue: Double, maxValue: Double): Double {
        return min(maxValue, max(minValue, value))
    }
    
    /**
     * Easing functions for smooth motion curves.
     * Input t is normalized [0.0, 1.0], output is also [0.0, 1.0]
     */
    private fun applyEasing(t: Double, ease: String?): Double {
        return when (ease) {
            "ease_in" -> t * t  // Quadratic ease-in (slow start)
            "ease_out" -> t * (2.0 - t)  // Quadratic ease-out (slow end)
            "ease_in_out" -> {
                // Cubic S-curve
                if (t < 0.5) 2.0 * t * t
                else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
            }
            else -> t  // "linear" or null defaults to linear
        }
    }
}
