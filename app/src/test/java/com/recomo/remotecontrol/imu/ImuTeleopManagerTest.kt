package com.recomo.remotecontrol.imu

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the pure-Kotlin math and logic in ImuTeleopManager.
 *
 * ImuTeleopManager itself depends on Android SensorManager, so we test the
 * underlying math (dead zone, velocity clamping, gimbal mix) by reproducing
 * the same computations that startSendLoop() performs.
 */
class ImuTeleopManagerTest {

    // Constants mirrored from ImuTeleopManager.Companion (private, so we duplicate)
    private val DEAD_ZONE_RAD = 0.017f
    private val MAX_GIMBAL_VEL_RAD_S = 1.0
    private val MAX_EE_VEL_M_S = 0.15
    private val GIMBAL_GAIN = 2.0
    private val EE_POS_GAIN = 0.3
    private val MAX_DELTA_RAD = 1.05f

    // ── Dead zone ─────────────────────────────────────────────────────────

    @Test
    fun `dead zone suppresses small deltas`() {
        val smallDelta = 0.010f // below 0.017 rad threshold
        val result = applyDeadZone(smallDelta)
        assertEquals(0f, result)
    }

    @Test
    fun `dead zone passes large deltas`() {
        val largeDelta = 0.05f // above 0.017 rad
        val result = applyDeadZone(largeDelta)
        assertEquals(largeDelta, result)
    }

    @Test
    fun `dead zone handles negative values`() {
        val negativeDelta = -0.05f
        val result = applyDeadZone(negativeDelta)
        assertEquals(negativeDelta, result)
    }

    @Test
    fun `dead zone handles exact boundary`() {
        // At exactly DEAD_ZONE_RAD, filtered value should be zero (not >)
        val result = applyDeadZone(DEAD_ZONE_RAD)
        assertEquals(0f, result)
    }

    @Test
    fun `dead zone handles just above boundary`() {
        val justAbove = DEAD_ZONE_RAD + 0.001f
        val result = applyDeadZone(justAbove)
        assertEquals(justAbove, result)
    }

    // ── Velocity clamping ─────────────────────────────────────────────────

    @Test
    fun `ee velocity clamped at MAX_EE_VEL_M_S`() {
        val hugeVelocity = 10.0
        val clamped = hugeVelocity.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S)
        assertEquals(MAX_EE_VEL_M_S, clamped)
    }

    @Test
    fun `ee velocity negative clamped at negative MAX_EE_VEL_M_S`() {
        val hugeNegative = -10.0
        val clamped = hugeNegative.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S)
        assertEquals(-MAX_EE_VEL_M_S, clamped)
    }

    @Test
    fun `gimbal velocity clamped at MAX_GIMBAL_VEL_RAD_S`() {
        val hugeVelocity = 5.0
        val clamped = hugeVelocity.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S)
        assertEquals(MAX_GIMBAL_VEL_RAD_S, clamped)
    }

    @Test
    fun `within-range velocity passes through unchanged`() {
        val vel = 0.05
        val clamped = vel.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S)
        assertEquals(vel, clamped)
    }

    // ── Gimbal mix (alpha blending) ───────────────────────────────────────

    @Test
    fun `alpha 0 means all EE, no gimbal`() {
        val alpha = 0.0
        val sensitivity = 0.5
        val delta = OrientationDelta(pitch = 0.1f, roll = 0.1f, yaw = 0.1f)

        val result = computeImuCommand(delta, sensitivity, alpha)

        // Gimbal should be zero
        assertEquals(0.0, result.gimbalPitchVel, 1e-9)
        assertEquals(0.0, result.gimbalRollVel, 1e-9)
        assertEquals(0.0, result.gimbalYawVel, 1e-9)

        // EE should be non-zero (posAlpha = 1.0)
        assertTrue(result.eeDz != 0.0, "eeDz should be non-zero when alpha=0")
        assertTrue(result.eeDx != 0.0, "eeDx should be non-zero when alpha=0")
    }

    @Test
    fun `alpha 1 means all gimbal, no EE`() {
        val alpha = 1.0
        val sensitivity = 0.5
        val delta = OrientationDelta(pitch = 0.1f, roll = 0.1f, yaw = 0.1f)

        val result = computeImuCommand(delta, sensitivity, alpha)

        // EE should be zero (posAlpha = 0.0)
        assertEquals(0.0, result.eeDx, 1e-9)
        assertEquals(0.0, result.eeDy, 1e-9)
        assertEquals(0.0, result.eeDz, 1e-9)

        // Gimbal should be non-zero
        assertTrue(result.gimbalPitchVel != 0.0, "gimbalPitchVel should be non-zero when alpha=1")
        assertTrue(result.gimbalRollVel != 0.0, "gimbalRollVel should be non-zero when alpha=1")
        assertTrue(result.gimbalYawVel != 0.0, "gimbalYawVel should be non-zero when alpha=1")
    }

    @Test
    fun `alpha 0_5 splits between gimbal and EE`() {
        val alpha = 0.5
        val sensitivity = 1.0
        val delta = OrientationDelta(pitch = 0.1f, roll = 0.1f, yaw = 0.0f)

        val result = computeImuCommand(delta, sensitivity, alpha)

        // Both should be non-zero
        assertTrue(result.gimbalPitchVel != 0.0)
        assertTrue(result.eeDz != 0.0)

        // Compute expected: gimbal pitch = 0.5 * 2.0 * 1.0 * 0.1 = 0.1
        val expectedGimbalPitch = (0.5 * GIMBAL_GAIN * 1.0 * 0.1)
        assertEquals(expectedGimbalPitch, result.gimbalPitchVel, 1e-6)

        // EE dz = 0.5 * 0.3 * 1.0 * 0.1 = 0.015
        val expectedEeDz = (0.5 * EE_POS_GAIN * 1.0 * 0.1)
        assertEquals(expectedEeDz, result.eeDz, 1e-6)
    }

    @Test
    fun `zero delta produces zero command`() {
        val delta = OrientationDelta(pitch = 0f, roll = 0f, yaw = 0f)
        val result = computeImuCommand(delta, 1.0, 0.5)

        assertEquals(0.0, result.eeDx, 1e-9)
        assertEquals(0.0, result.eeDy, 1e-9)
        assertEquals(0.0, result.eeDz, 1e-9)
        assertEquals(0.0, result.gimbalRollVel, 1e-9)
        assertEquals(0.0, result.gimbalPitchVel, 1e-9)
        assertEquals(0.0, result.gimbalYawVel, 1e-9)
    }

    @Test
    fun `eeDy is always zero (no up-down from rotation)`() {
        val delta = OrientationDelta(pitch = 0.5f, roll = 0.5f, yaw = 0.5f)
        val result = computeImuCommand(delta, 1.0, 0.3)
        assertEquals(0.0, result.eeDy, 1e-9, "eeDy should always be 0 (no accelerometer mapping)")
    }

    @Test
    fun `pitch maps to eeDz (forward-back), roll maps to eeDx (left-right)`() {
        val delta = OrientationDelta(pitch = 0.1f, roll = 0.0f, yaw = 0.0f)
        val result = computeImuCommand(delta, 1.0, 0.0) // all EE
        assertTrue(result.eeDz != 0.0, "pitch should map to eeDz")
        assertEquals(0.0, result.eeDx, 1e-9, "with roll=0, eeDx should be 0")

        val delta2 = OrientationDelta(pitch = 0.0f, roll = 0.1f, yaw = 0.0f)
        val result2 = computeImuCommand(delta2, 1.0, 0.0) // all EE
        assertTrue(result2.eeDx != 0.0, "roll should map to eeDx")
        assertEquals(0.0, result2.eeDz, 1e-9, "with pitch=0, eeDz should be 0")
    }

    @Test
    fun `yaw does not contribute to EE position`() {
        // yaw is consumed entirely by gimbal per design
        val delta = OrientationDelta(pitch = 0.0f, roll = 0.0f, yaw = 0.5f)
        val result = computeImuCommand(delta, 1.0, 0.0) // all EE
        assertEquals(0.0, result.eeDx, 1e-9)
        assertEquals(0.0, result.eeDy, 1e-9)
        assertEquals(0.0, result.eeDz, 1e-9)
    }

    @Test
    fun `sensitivity 0 produces zero command`() {
        val delta = OrientationDelta(pitch = 1.0f, roll = 1.0f, yaw = 1.0f)
        val result = computeImuCommand(delta, 0.0, 0.5)

        assertEquals(0.0, result.eeDx, 1e-9)
        assertEquals(0.0, result.eeDz, 1e-9)
        assertEquals(0.0, result.gimbalPitchVel, 1e-9)
        assertEquals(0.0, result.gimbalRollVel, 1e-9)
        assertEquals(0.0, result.gimbalYawVel, 1e-9)
    }

    @Test
    fun `large delta triggers velocity clamping`() {
        // With alpha=1.0 (all gimbal), sensitivity=1.0, delta.pitch=1.0 rad
        // gimbalPitch = 1.0 * 2.0 * 1.0 * 1.0 = 2.0 → should be clamped to 1.0
        val delta = OrientationDelta(pitch = 1.0f, roll = 0.0f, yaw = 0.0f)
        val result = computeImuCommand(delta, 1.0, 1.0)
        assertEquals(MAX_GIMBAL_VEL_RAD_S, result.gimbalPitchVel, 1e-9)
    }

    // ── Safety threshold ──────────────────────────────────────────────────

    @Test
    fun `MAX_DELTA_RAD is approximately 60 degrees`() {
        val degrees = Math.toDegrees(MAX_DELTA_RAD.toDouble())
        assertTrue(degrees in 55.0..65.0, "MAX_DELTA_RAD should be ~60 degrees, was $degrees")
    }

    // ── Helper: reproduces ImuTeleopManager.startSendLoop() math ─────────

    private fun applyDeadZone(value: Float): Float {
        return if (kotlin.math.abs(value) > DEAD_ZONE_RAD) value else 0f
    }

    private fun computeImuCommand(
        delta: OrientationDelta,
        sensitivity: Double,
        alpha: Double
    ): ImuTeleopCommand {
        val s = sensitivity
        val gimbalPitch = alpha * GIMBAL_GAIN * s * delta.pitch
        val gimbalRoll = alpha * GIMBAL_GAIN * s * delta.roll
        val gimbalYaw = alpha * GIMBAL_GAIN * s * delta.yaw

        val posAlpha = 1.0 - alpha
        val eeDz = posAlpha * EE_POS_GAIN * s * delta.pitch
        val eeDx = posAlpha * EE_POS_GAIN * s * delta.roll
        val eeDy = 0.0

        return ImuTeleopCommand(
            eeDx = eeDx.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S),
            eeDy = eeDy,
            eeDz = eeDz.coerceIn(-MAX_EE_VEL_M_S, MAX_EE_VEL_M_S),
            eeFrame = "camera",
            gimbalRollVel = gimbalRoll.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S),
            gimbalPitchVel = gimbalPitch.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S),
            gimbalYawVel = gimbalYaw.coerceIn(-MAX_GIMBAL_VEL_RAD_S, MAX_GIMBAL_VEL_RAD_S)
        )
    }
}
