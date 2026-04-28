package com.recomo.common.sceneviewer

import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts the app's JSON trajectory format into a TUM-format `.txt` file
 * that the browser-side viewer can play back, applying an [AnchorPose] transform
 * so the samples align with the SPZ scene's world frame.
 *
 * TUM line format: `timestamp x y z qx qy qz qw` — one sample per line, fields
 * separated by single spaces, all floats in standard decimal notation.
 *
 * Input JSON formats (matches the logic in `viewer.html#convertTrajectoryJsonToTUM`):
 * - A top-level JSON array of records, OR
 * - An object with `frames` / `samples` / `fois` / `pois` holding the records array.
 *
 * Each record supplies a planar pose (the robot moves on the XZ ground plane with
 * rotation around the Y axis) via any of:
 * - `baseX`, `baseY` (treated as XZ), `baseYaw`
 * - `base.x`, `base.y`, `base.yaw`
 * - `poi.base_pose.x`, `poi.base_pose.y`, `poi.base_pose.yaw`
 *
 * Timestamps are sourced from the first available of `timestamp`, `tSec`,
 * `timestampMs / 1000`, `timestamp_ms / 1000`, `sequence_index`, or the record index.
 */
object TrajectoryExporter {

    /**
     * Convert [json] to TUM text, applying [anchor] as a scene-frame alignment.
     *
     * The anchor is applied as `world = R(anchor) * p(sample) + t(anchor)`, where
     * `p(sample) = (baseX, 0, baseY)` and the sample rotation is around Y by `baseYaw`.
     *
     * @throws IllegalArgumentException if the JSON is missing a records array or empty.
     */
    fun convertJsonToTumText(json: String, anchor: AnchorPose = AnchorPose.IDENTITY): String {
        val root = parseRoot(json)
        val records = extractRecords(root)
        require(records.length() > 0) { "trajectory JSON has no samples" }

        val lines = StringBuilder()
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i) ?: continue
            val sample = extractPlanarSample(record, i)
            val tumLine = transformAndFormat(sample, anchor)
            lines.append(tumLine).append('\n')
        }
        return lines.toString()
    }

    /**
     * Convert [json] and write the result to [outputFile]. Creates parent directories
     * if needed. Returns the file that was written.
     */
    fun convertJsonToTumFile(
        json: String,
        anchor: AnchorPose,
        outputFile: File
    ): File {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(convertJsonToTumText(json, anchor))
        return outputFile
    }

    // ---- internals ----

    private data class PlanarSample(
        val timestamp: Double,
        val x: Double,
        val y: Double, // always 0 for planar samples
        val z: Double,
        val yaw: Double
    )

    private fun parseRoot(json: String): Any =
        runCatching { JSONObject(json) }.getOrElse { JSONArray(json) }

    private fun extractRecords(root: Any): JSONArray = when (root) {
        is JSONArray -> root
        is JSONObject -> firstArrayField(root, listOf("frames", "samples", "fois", "pois"))
            ?: throw IllegalArgumentException("trajectory JSON object has no frames/samples/fois/pois array")
        else -> throw IllegalArgumentException("unsupported trajectory JSON root type: ${root::class.simpleName}")
    }

    private fun firstArrayField(obj: JSONObject, keys: List<String>): JSONArray? {
        for (key in keys) {
            val value = obj.opt(key)
            if (value is JSONArray) return value
        }
        return null
    }

    private fun extractPlanarSample(record: JSONObject, fallbackIndex: Int): PlanarSample {
        val timestamp = extractTimestamp(record, fallbackIndex)

        val base: JSONObject? = record.optJSONObject("base")
            ?: record.optJSONObject("poi")?.optJSONObject("base_pose")
            ?: record.optJSONObject("poi")

        val x = firstFinite(record, "baseX") ?: base?.let { firstFinite(it, "x") } ?: 0.0
        val z = firstFinite(record, "baseY") ?: base?.let { firstFinite(it, "y") } ?: 0.0
        val yaw = firstFinite(record, "baseYaw") ?: base?.let { firstFinite(it, "yaw") } ?: 0.0
        return PlanarSample(timestamp, x, 0.0, z, yaw)
    }

    private fun extractTimestamp(record: JSONObject, fallbackIndex: Int): Double {
        firstFinite(record, "timestamp")?.let { return it }
        firstFinite(record, "tSec")?.let { return it }
        firstFinite(record, "timestampMs")?.let { return it / 1000.0 }
        firstFinite(record, "timestamp_ms")?.let { return it / 1000.0 }
        firstFinite(record, "sequence_index")?.let { return it }
        return fallbackIndex.toDouble()
    }

    private fun firstFinite(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        return if (value.isFinite()) value else null
    }

    /**
     * Apply the anchor transform to a planar sample and emit a single TUM line.
     *
     * Sample rotation is represented as a quaternion around Y: `(0, sin(yaw/2), 0, cos(yaw/2))`.
     * Sample position in scene-local frame is `(x, 0, z)`.
     *
     * The anchor is composed as:
     *   world_position = anchor_rotation * sample_position + anchor_translation
     *   world_rotation = anchor_rotation * sample_rotation
     */
    private fun transformAndFormat(sample: PlanarSample, anchor: AnchorPose): String {
        val half = sample.yaw * 0.5
        val sampleQx = 0.0
        val sampleQy = sin(half)
        val sampleQz = 0.0
        val sampleQw = cos(half)

        val (wx, wy, wz) = rotateVectorByQuat(
            sample.x, sample.y, sample.z,
            anchor.qx, anchor.qy, anchor.qz, anchor.qw
        )
        val finalX = wx + anchor.x
        val finalY = wy + anchor.y
        val finalZ = wz + anchor.z

        val (fqx, fqy, fqz, fqw) = multiplyQuat(
            anchor.qx, anchor.qy, anchor.qz, anchor.qw,
            sampleQx, sampleQy, sampleQz, sampleQw
        )

        return buildString {
            append(formatFloat(sample.timestamp))
            append(' '); append(formatFloat(finalX))
            append(' '); append(formatFloat(finalY))
            append(' '); append(formatFloat(finalZ))
            append(' '); append(formatFloat(fqx))
            append(' '); append(formatFloat(fqy))
            append(' '); append(formatFloat(fqz))
            append(' '); append(formatFloat(fqw))
        }
    }

    private fun formatFloat(value: Double): String = "%.6f".format(value)

    /** Rotate a 3D vector by a unit quaternion using `v' = q * v * q^-1`. */
    private fun rotateVectorByQuat(
        vx: Double, vy: Double, vz: Double,
        qx: Double, qy: Double, qz: Double, qw: Double
    ): Triple<Double, Double, Double> {
        // Standard Hamilton convention: v' = q * (0, v) * q_conjugate, expanded.
        val tx = 2.0 * (qy * vz - qz * vy)
        val ty = 2.0 * (qz * vx - qx * vz)
        val tz = 2.0 * (qx * vy - qy * vx)
        val rx = vx + qw * tx + (qy * tz - qz * ty)
        val ry = vy + qw * ty + (qz * tx - qx * tz)
        val rz = vz + qw * tz + (qx * ty - qy * tx)
        return Triple(rx, ry, rz)
    }

    /** Hamilton product `a * b` returning (x, y, z, w). */
    private data class QuatXyzw(val x: Double, val y: Double, val z: Double, val w: Double)

    private fun multiplyQuat(
        ax: Double, ay: Double, az: Double, aw: Double,
        bx: Double, by: Double, bz: Double, bw: Double
    ): QuatXyzw {
        return QuatXyzw(
            x = aw * bx + ax * bw + ay * bz - az * by,
            y = aw * by - ax * bz + ay * bw + az * bx,
            z = aw * bz + ax * by - ay * bx + az * bw,
            w = aw * bw - ax * bx - ay * by - az * bz
        )
    }
}
