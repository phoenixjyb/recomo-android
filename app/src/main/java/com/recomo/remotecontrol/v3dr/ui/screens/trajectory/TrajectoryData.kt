package com.recomo.remotecontrol.v3dr.ui.screens.trajectory

import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 3D pose from TUM format trajectory
 */
data class Pose3D(
    val timestamp: Double,
    val x: Float,
    val y: Float,
    val z: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float
)

/**
 * Trajectory data parsed from TUM format file
 */
data class TrajectoryData(
    val poses: List<Pose3D>,
    val algorithm: String?,
    val version: String?,
    val minBounds: FloatArray,  // [minX, minY, minZ]
    val maxBounds: FloatArray,  // [maxX, maxY, maxZ]
    val center: FloatArray      // [centerX, centerY, centerZ]
) {
    val duration: Double
        get() = if (poses.isNotEmpty()) poses.last().timestamp - poses.first().timestamp else 0.0
    
    val poseCount: Int
        get() = poses.size
    
    val scale: Float
        get() {
            val dx = maxBounds[0] - minBounds[0]
            val dy = maxBounds[1] - minBounds[1]
            val dz = maxBounds[2] - minBounds[2]
            return max(max(dx, dy), dz)
        }
    
    companion object {
        /**
         * Parse TUM format trajectory file
         * Format: timestamp tx ty tz qx qy qz qw
         */
        fun fromTumFile(file: File): TrajectoryData? {
            if (!file.exists()) return null
            
            val poses = mutableListOf<Pose3D>()
            var algorithm: String? = null
            var version: String? = null
            
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var maxZ = Float.MIN_VALUE
            
            file.forEachLine { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("# algorithm:") -> {
                        algorithm = trimmed.substringAfter("# algorithm:").trim()
                    }
                    trimmed.startsWith("# version:") -> {
                        version = trimmed.substringAfter("# version:").trim()
                    }
                    trimmed.startsWith("#") || trimmed.isEmpty() -> {
                        // Skip comments and empty lines
                    }
                    else -> {
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 8) {
                            try {
                                val pose = Pose3D(
                                    timestamp = parts[0].toDouble(),
                                    x = parts[1].toFloat(),
                                    y = parts[2].toFloat(),
                                    z = parts[3].toFloat(),
                                    qx = parts[4].toFloat(),
                                    qy = parts[5].toFloat(),
                                    qz = parts[6].toFloat(),
                                    qw = parts[7].toFloat()
                                )
                                poses.add(pose)
                                
                                // Update bounds
                                minX = min(minX, pose.x)
                                minY = min(minY, pose.y)
                                minZ = min(minZ, pose.z)
                                maxX = max(maxX, pose.x)
                                maxY = max(maxY, pose.y)
                                maxZ = max(maxZ, pose.z)
                            } catch (e: NumberFormatException) {
                                // Skip malformed lines
                            }
                        }
                    }
                }
            }
            
            if (poses.isEmpty()) return null
            
            return TrajectoryData(
                poses = poses,
                algorithm = algorithm,
                version = version,
                minBounds = floatArrayOf(minX, minY, minZ),
                maxBounds = floatArrayOf(maxX, maxY, maxZ),
                center = floatArrayOf(
                    (minX + maxX) / 2f,
                    (minY + maxY) / 2f,
                    (minZ + maxZ) / 2f
                )
            )
        }
        
        /**
         * Find trajectory file in session directory
         * Prioritizes: arcore > cloud_sfm > openvins > generic
         */
        fun findTrajectoryFile(sessionDir: File): File? {
            val candidates = listOf(
                File(sessionDir, "trajectory_arcore_tum.txt"),
                File(sessionDir, "results/trajectory_cloud_sfm_tum.txt"),
                File(sessionDir, "results/trajectory_openvins_tum.txt"),
                File(sessionDir, "results/trajectory_tum.txt")
            )
            return candidates.firstOrNull { it.exists() }
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TrajectoryData
        return poses == other.poses
    }
    
    override fun hashCode(): Int = poses.hashCode()
}
