package com.recomo.common.sceneviewer

import kotlinx.serialization.Serializable

/**
 * SE(3) pose used to align a trajectory with an SPZ scene's world frame.
 *
 * Applied to each trajectory sample as: `world = anchor_rotation * sample + anchor_translation`.
 * Quaternion is stored in (x, y, z, w) order — the same convention used by the rest of
 * the app and the TUM trajectory format.
 */
@Serializable
data class AnchorPose(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val qx: Double = 0.0,
    val qy: Double = 0.0,
    val qz: Double = 0.0,
    val qw: Double = 1.0
) {
    companion object {
        val IDENTITY = AnchorPose()
    }
}
