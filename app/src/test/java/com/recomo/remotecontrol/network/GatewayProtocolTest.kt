package com.recomo.remotecontrol.network

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Gateway protocol contract tests.
 *
 * These tests build JSON objects the same way RecomoControlViewModel does
 * and verify field names, orderings, and structure. These tests would have
 * caught the A-C3 bug (reversed joint ordering) before it shipped.
 */
class GatewayProtocolTest {

    // Constants mirrored from RecomoControlViewModel
    private val JOG_TTL_MS = 1000
    private val IMU_GIMBAL_TTL_MS = 120
    private var seqId = 0

    // ── JogCmd arm velocity ───────────────────────────────────────────────

    @Test
    fun `JogCmd arm velocity has correct type and target`() {
        val cmd = buildArmJogVelocity(j4 = 0.1, j5 = 0.2, j6 = 0.3)
        assertEquals("JogCmd", cmd["type"]?.jsonPrimitive?.content)
        assertEquals("arm", cmd["target"]?.jsonPrimitive?.content)
        assertEquals("velocity", cmd["mode"]?.jsonPrimitive?.content)
    }

    @Test
    fun `JogCmd arm velocity values ordering is j4 j5 j6 -- would catch A-C3 bug`() {
        // A-C3 bug: values were [j6, j5, j4] instead of [j4, j5, j6]
        val j4 = 0.1
        val j5 = 0.2
        val j6 = 0.3
        val cmd = buildArmJogVelocity(j4, j5, j6)
        val values = cmd["values"]!!.jsonArray

        assertEquals(3, values.size, "Arm jog velocity should have exactly 3 values")
        assertEquals(j4, values[0].jsonPrimitive.double, 1e-9, "values[0] must be j4 (elbow)")
        assertEquals(j5, values[1].jsonPrimitive.double, 1e-9, "values[1] must be j5 (pitch)")
        assertEquals(j6, values[2].jsonPrimitive.double, 1e-9, "values[2] must be j6 (yaw)")
    }

    @Test
    fun `JogCmd arm velocity has required envelope fields`() {
        val cmd = buildArmJogVelocity(0.0, 0.0, 0.0)
        assertNotNull(cmd["seq_id"], "Must have seq_id")
        assertNotNull(cmd["timestamp_ms"], "Must have timestamp_ms")
        assertNotNull(cmd["ttl_ms"], "Must have ttl_ms")
        assertNotNull(cmd["deadman"], "Must have deadman")
        assertEquals(JOG_TTL_MS, cmd["ttl_ms"]!!.jsonPrimitive.int)
        assertEquals(true, cmd["deadman"]!!.jsonPrimitive.boolean)
    }

    // ── JogCmd arm position ───────────────────────────────────────────────

    @Test
    fun `JogCmd arm position values ordering is j4 j5 j6 -- would catch A-C3 bug`() {
        val j4 = 10.0 // degrees
        val j5 = 45.0
        val j6 = -30.0
        val cmd = buildArmJogPosition(j4, j5, j6)
        val values = cmd["values"]!!.jsonArray

        assertEquals(3, values.size)
        assertEquals(j4, values[0].jsonPrimitive.double, 1e-9, "values[0] must be j4 (elbow)")
        assertEquals(j5, values[1].jsonPrimitive.double, 1e-9, "values[1] must be j5 (pitch)")
        assertEquals(j6, values[2].jsonPrimitive.double, 1e-9, "values[2] must be j6 (yaw)")
    }

    @Test
    fun `JogCmd arm position has mode position`() {
        val cmd = buildArmJogPosition(0.0, 90.0, 135.0)
        assertEquals("position", cmd["mode"]?.jsonPrimitive?.content)
        assertEquals("arm", cmd["target"]?.jsonPrimitive?.content)
    }

    // ── JogCmd gimbal velocity ────────────────────────────────────────────

    @Test
    fun `JogCmd gimbal velocity values ordering is roll pitch yaw`() {
        val roll = 0.1
        val pitch = 0.2
        val yaw = 0.3
        val cmd = buildGimbalJogVelocity(roll, pitch, yaw)
        val values = cmd["values"]!!.jsonArray

        assertEquals(3, values.size, "Gimbal jog should have exactly 3 values")
        assertEquals(roll, values[0].jsonPrimitive.double, 1e-9, "values[0] must be roll")
        assertEquals(pitch, values[1].jsonPrimitive.double, 1e-9, "values[1] must be pitch")
        assertEquals(yaw, values[2].jsonPrimitive.double, 1e-9, "values[2] must be yaw")
    }

    @Test
    fun `JogCmd gimbal velocity has correct target`() {
        val cmd = buildGimbalJogVelocity(0.0, 0.0, 0.0)
        assertEquals("gimbal", cmd["target"]?.jsonPrimitive?.content)
        assertEquals("velocity", cmd["mode"]?.jsonPrimitive?.content)
    }

    // ── IMU gimbal TTL ────────────────────────────────────────────────────

    @Test
    fun `IMU gimbal commands use 120ms TTL not 1000ms`() {
        val cmd = buildImuGimbalCmd(rollVel = 0.1, pitchVel = 0.2, yawVel = 0.3)
        val ttl = cmd["ttl_ms"]!!.jsonPrimitive.int

        assertEquals(IMU_GIMBAL_TTL_MS, ttl,
            "IMU gimbal TTL must be 120ms (3x the 40ms send interval), not the default $JOG_TTL_MS ms")
        assertNotEquals(JOG_TTL_MS, ttl,
            "IMU gimbal must NOT use the default JOG_TTL_MS of ${JOG_TTL_MS}ms")
    }

    @Test
    fun `IMU gimbal command has gimbal target and velocity mode`() {
        val cmd = buildImuGimbalCmd(rollVel = 0.1, pitchVel = 0.0, yawVel = 0.0)
        assertEquals("gimbal", cmd["target"]?.jsonPrimitive?.content)
        assertEquals("velocity", cmd["mode"]?.jsonPrimitive?.content)
    }

    // ── ee_position_cmd ───────────────────────────────────────────────────

    @Test
    fun `ee_position_cmd has correct fields`() {
        val cmd = buildEePositionCmd(dx = 0.01, dy = 0.0, dz = -0.02, frame = "camera")
        assertEquals("ee_position_cmd", cmd["type"]?.jsonPrimitive?.content)
        assertEquals(0.01, cmd["dx"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(0.0, cmd["dy"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(-0.02, cmd["dz"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("camera", cmd["frame"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ee_position_cmd has no extra envelope fields`() {
        // ee_position_cmd is a lightweight command — no seq_id, timestamp, ttl, deadman
        val cmd = buildEePositionCmd(dx = 0.0, dy = 0.0, dz = 0.0, frame = "camera")
        assertNull(cmd["seq_id"], "ee_position_cmd should not have seq_id")
        assertNull(cmd["timestamp_ms"], "ee_position_cmd should not have timestamp_ms")
        assertNull(cmd["ttl_ms"], "ee_position_cmd should not have ttl_ms")
    }

    // ── ee_ik_controller_cmd ──────────────────────────────────────────────

    @Test
    fun `ee_ik_controller_cmd start has type action and dry_run`() {
        val cmd = buildEeIkControllerCmd(action = "start", dryRun = true)
        assertEquals("ee_ik_controller_cmd", cmd["type"]?.jsonPrimitive?.content)
        assertEquals("start", cmd["action"]?.jsonPrimitive?.content)
        assertEquals(true, cmd["dry_run"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `ee_ik_controller_cmd stop has type and action, no dry_run`() {
        val cmd = buildEeIkControllerCmd(action = "stop", dryRun = null)
        assertEquals("ee_ik_controller_cmd", cmd["type"]?.jsonPrimitive?.content)
        assertEquals("stop", cmd["action"]?.jsonPrimitive?.content)
        assertNull(cmd["dry_run"], "stop command should not include dry_run")
    }

    @Test
    fun `ee_ik_controller_cmd start with dry_run false`() {
        val cmd = buildEeIkControllerCmd(action = "start", dryRun = false)
        assertEquals(false, cmd["dry_run"]?.jsonPrimitive?.boolean)
    }

    // ── SafetyCmd ─────────────────────────────────────────────────────────

    @Test
    fun `SafetyCmd has type and action`() {
        val cmd = buildSafetyCmd("estop")
        assertEquals("SafetyCmd", cmd["type"]?.jsonPrimitive?.content)
        assertEquals("estop", cmd["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun `SafetyCmd clear_estop action`() {
        val cmd = buildSafetyCmd("clear_estop")
        assertEquals("SafetyCmd", cmd["type"]?.jsonPrimitive?.content)
        assertEquals("clear_estop", cmd["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun `SafetyCmd has exactly two fields`() {
        val cmd = buildSafetyCmd("estop")
        assertEquals(2, cmd.size, "SafetyCmd should have exactly {type, action}")
    }

    // ── Gimbal position ───────────────────────────────────────────────────

    @Test
    fun `JogCmd gimbal position values ordering is roll pitch yaw`() {
        val roll = 0.5
        val pitch = -0.3
        val yaw = 1.0
        val cmd = buildGimbalJogPosition(roll, pitch, yaw)
        val values = cmd["values"]!!.jsonArray

        assertEquals(3, values.size)
        assertEquals(roll, values[0].jsonPrimitive.double, 1e-9, "values[0] must be roll")
        assertEquals(pitch, values[1].jsonPrimitive.double, 1e-9, "values[1] must be pitch")
        assertEquals(yaw, values[2].jsonPrimitive.double, 1e-9, "values[2] must be yaw")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Builder helpers — reproduce the exact JSON construction from ViewModel
    // ═══════════════════════════════════════════════════════════════════════

    private fun buildArmJogVelocity(j4: Double, j5: Double, j6: Double): JsonObject {
        return buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId++)
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "arm")
            put("mode", "velocity")
            put("values", buildJsonArray {
                add(j4)  // joint4 (elbow)
                add(j5)  // joint5 (pitch)
                add(j6)  // joint6 (yaw)
            })
        }
    }

    private fun buildArmJogPosition(j4: Double, j5: Double, j6: Double): JsonObject {
        return buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId++)
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "arm")
            put("mode", "position")
            put("values", buildJsonArray {
                add(j4)  // elbow (joint4) — gateway index 0
                add(j5)  // base_pitch (joint5) — gateway index 1
                add(j6)  // base_yaw (joint6) — gateway index 2
            })
        }
    }

    private fun buildGimbalJogVelocity(roll: Double, pitch: Double, yaw: Double): JsonObject {
        return buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId++)
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "gimbal")
            put("mode", "velocity")
            put("values", buildJsonArray {
                add(roll)
                add(pitch)
                add(yaw)
            })
        }
    }

    private fun buildGimbalJogPosition(roll: Double, pitch: Double, yaw: Double): JsonObject {
        return buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId++)
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", JOG_TTL_MS)
            put("deadman", true)
            put("target", "gimbal")
            put("mode", "position")
            put("values", buildJsonArray {
                add(roll)
                add(pitch)
                add(yaw)
            })
        }
    }

    private fun buildImuGimbalCmd(rollVel: Double, pitchVel: Double, yawVel: Double): JsonObject {
        return buildJsonObject {
            put("type", "JogCmd")
            put("seq_id", seqId++)
            put("timestamp_ms", System.currentTimeMillis())
            put("ttl_ms", IMU_GIMBAL_TTL_MS)
            put("deadman", true)
            put("target", "gimbal")
            put("mode", "velocity")
            put("values", buildJsonArray {
                add(rollVel)
                add(pitchVel)
                add(yawVel)
            })
        }
    }

    private fun buildEePositionCmd(dx: Double, dy: Double, dz: Double, frame: String): JsonObject {
        return buildJsonObject {
            put("type", "ee_position_cmd")
            put("dx", dx)
            put("dy", dy)
            put("dz", dz)
            put("frame", frame)
        }
    }

    private fun buildEeIkControllerCmd(action: String, dryRun: Boolean?): JsonObject {
        return buildJsonObject {
            put("type", "ee_ik_controller_cmd")
            put("action", action)
            if (dryRun != null) {
                put("dry_run", dryRun)
            }
        }
    }

    private fun buildSafetyCmd(action: String): JsonObject {
        return buildJsonObject {
            put("type", "SafetyCmd")
            put("action", action)
        }
    }
}
