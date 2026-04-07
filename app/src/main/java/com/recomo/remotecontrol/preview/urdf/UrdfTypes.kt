package com.recomo.remotecontrol.preview.urdf

data class Vec3(val x: Double, val y: Double, val z: Double) {
    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}

data class Pose(val xyz: Vec3, val rpy: Vec3) {
    companion object {
        val IDENTITY = Pose(Vec3.ZERO, Vec3.ZERO)
    }
}

data class UrdfJoint(
    val name: String,
    val type: String,
    val parent: String,
    val child: String,
    val origin: Pose,
    val axis: Vec3
)

data class UrdfModel(
    val joints: Map<String, UrdfJoint>,
    val jointsByParent: Map<String, List<UrdfJoint>>,
    val linkMeshes: Map<String, String>,
    val rootLink: String
)
