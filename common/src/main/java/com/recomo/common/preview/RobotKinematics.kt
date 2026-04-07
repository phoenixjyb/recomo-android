package com.recomo.common.preview

import android.content.Context
import com.recomo.common.model.RobotProfile
import com.recomo.common.preview.urdf.Pose
import com.recomo.common.preview.urdf.Transform
import com.recomo.common.preview.urdf.UrdfModel
import com.recomo.common.preview.urdf.UrdfParser
import com.recomo.common.preview.urdf.Vec3

data class RobotKinematicsConfig(
    val urdfAsset: String,
    val baseLink: String,
    val cameraLink: String,
    val armJointNames: List<String>,
    val gimbalJointNames: List<String>,
    val chainJointNames: List<String>,
    val gimbalAttitudeIsWorld: Boolean,
    /** Default arm joint angles in degrees [elbow, pitch, yaw]. Used when no trajectory data. */
    val homeArmQDeg: List<Double> = listOf(0.0, 0.0, 0.0),
    /** Default gimbal joint angles in degrees [pitch, roll, yaw]. */
    val homeGimbalQDeg: List<Double> = listOf(0.0, 0.0, 0.0)
)

data class CameraPose(
    val position: Vec3,
    val transform: Transform
)

object RobotKinematics {
    /** Default config — uses Proto1 family (most common). */
    fun defaultConfig(): RobotKinematicsConfig = configForProfile(RobotProfile.RECOMO_PROTO1)

    fun configForProfile(profile: RobotProfile): RobotKinematicsConfig {
        return if (profile.isProto1Family()) {
            RobotKinematicsConfig(
                urdfAsset = "robot_models/recomoProto-190-v3/recomoProto-190-v3.urdf",
                baseLink = "base_link",
                cameraLink = "ee_tool",
                armJointNames = listOf("joint4_elbow_pitch", "joint5_arm_pitch", "joint6_arm_yaw"),
                gimbalJointNames = listOf("joint1_gimbal_pitch", "joint2_gimbal_roll", "joint3_gimbal_yaw"),
                chainJointNames = listOf(
                    "joint6_arm_yaw",
                    "joint5_arm_pitch",
                    "joint4_elbow_pitch",
                    "joint3_gimbal_yaw",
                    "joint2_gimbal_roll",
                    "joint1_gimbal_pitch"
                ),
                gimbalAttitudeIsWorld = true,
                homeArmQDeg = listOf(135.0, 90.0, 0.0),   // elbow, pitch, yaw (ARM-H)
                homeGimbalQDeg = listOf(0.0, 0.0, 0.0)
            )
        } else {
            RobotKinematicsConfig(
                urdfAsset = "robot_models/recomoDemo1.urdf",
                baseLink = "base_link",
                cameraLink = "ee_tool",
                armJointNames = listOf("joint4_elbow_pitch", "joint5_arm_pitch", "joint6_arm_yaw"),
                gimbalJointNames = listOf("joint1_gimbal_roll", "joint2_gimbal_pitch", "joint3_gimbal_yaw"),
                chainJointNames = listOf(
                    "joint6_arm_yaw",
                    "joint5_arm_pitch",
                    "joint4_elbow_pitch",
                    "joint3_gimbal_yaw",
                    "joint2_gimbal_pitch",
                    "joint1_gimbal_roll"
                ),
                gimbalAttitudeIsWorld = false
            )
        }
    }

    fun loadModel(context: Context, config: RobotKinematicsConfig): UrdfModel {
        return UrdfParser.loadFromAssets(context, config.urdfAsset)
    }

    fun computeCameraPose(
        model: UrdfModel,
        config: RobotKinematicsConfig,
        sample: TrajectorySample
    ): CameraPose? {
        val baseTransform = Transform.translation(Vec3(sample.baseX, sample.baseY, 0.0))
            .multiply(Transform.rotationZ(sample.baseYaw))

        val jointValues = mutableMapOf<String, Double>()
        config.armJointNames.forEachIndexed { index, name ->
            jointValues[name] = sample.armQ.getOrNull(index) ?: 0.0
        }
        config.gimbalJointNames.forEachIndexed { index, name ->
            jointValues[name] = if (config.gimbalAttitudeIsWorld) 0.0 else sample.gimbalQ.getOrNull(index) ?: 0.0
        }

        val linkTransforms = computeLinkTransforms(model, config.baseLink, jointValues)
        val cameraLocal = linkTransforms[config.cameraLink] ?: return null
        val cameraWorld = baseTransform.multiply(cameraLocal)

        if (!config.gimbalAttitudeIsWorld) {
            return CameraPose(cameraWorld.translation(), cameraWorld)
        }

        val roll = sample.gimbalQ.getOrNull(0) ?: 0.0
        val pitch = sample.gimbalQ.getOrNull(1) ?: 0.0
        val yaw = sample.gimbalQ.getOrNull(2) ?: 0.0
        val attitude = Transform.rotationZ(yaw)
            .multiply(Transform.rotationY(pitch))
            .multiply(Transform.rotationX(roll))

        val cameraOverride = Transform(
            doubleArrayOf(
                attitude.m[0], attitude.m[1], attitude.m[2], 0.0,
                attitude.m[4], attitude.m[5], attitude.m[6], 0.0,
                attitude.m[8], attitude.m[9], attitude.m[10], 0.0,
                cameraWorld.m[12], cameraWorld.m[13], cameraWorld.m[14], 1.0
            )
        )
        return CameraPose(cameraOverride.translation(), cameraOverride)
    }

    fun computeWorldLinkTransforms(
        model: UrdfModel,
        config: RobotKinematicsConfig,
        sample: TrajectorySample
    ): Map<String, Transform> {
        val baseTransform = Transform.translation(Vec3(sample.baseX, sample.baseY, 0.0))
            .multiply(Transform.rotationZ(sample.baseYaw))

        val jointValues = mutableMapOf<String, Double>()
        config.armJointNames.forEachIndexed { index, name ->
            jointValues[name] = sample.armQ.getOrNull(index) ?: 0.0
        }
        config.gimbalJointNames.forEachIndexed { index, name ->
            jointValues[name] = if (config.gimbalAttitudeIsWorld) 0.0 else sample.gimbalQ.getOrNull(index) ?: 0.0
        }

        val local = computeLinkTransforms(model, config.baseLink, jointValues)
        return local.mapValues { (_, transform) -> baseTransform.multiply(transform) }
    }

    fun computeChainPoints(
        model: UrdfModel,
        config: RobotKinematicsConfig,
        sample: TrajectorySample
    ): List<Vec3> {
        val baseTransform = Transform.translation(Vec3(sample.baseX, sample.baseY, 0.0))
            .multiply(Transform.rotationZ(sample.baseYaw))

        val jointValues = mutableMapOf<String, Double>()
        config.armJointNames.forEachIndexed { index, name ->
            jointValues[name] = sample.armQ.getOrNull(index) ?: 0.0
        }
        config.gimbalJointNames.forEachIndexed { index, name ->
            jointValues[name] = if (config.gimbalAttitudeIsWorld) 0.0 else sample.gimbalQ.getOrNull(index) ?: 0.0
        }

        val chain = mutableListOf<Vec3>()
        var currentTransform = Transform.identity()
        chain.add(baseTransform.translation())

        for (jointName in config.chainJointNames) {
            val joint = model.joints[jointName] ?: continue
            val jointValue = jointValues[jointName] ?: 0.0
            val jointTransform = jointTransform(joint.origin, joint.axis, jointValue)
            currentTransform = currentTransform.multiply(jointTransform)
            val world = baseTransform.multiply(currentTransform)
            chain.add(world.translation())
        }

        return chain
    }

    private fun computeLinkTransforms(
        model: UrdfModel,
        rootLink: String,
        jointValues: Map<String, Double>
    ): Map<String, Transform> {
        val transforms = mutableMapOf<String, Transform>()
        fun dfs(linkName: String, parentTransform: Transform) {
            transforms[linkName] = parentTransform
            val joints = model.jointsByParent[linkName] ?: return
            for (joint in joints) {
                val jointValue = jointValues[joint.name] ?: 0.0
                val local = jointTransform(joint.origin, joint.axis, jointValue)
                dfs(joint.child, parentTransform.multiply(local))
            }
        }
        dfs(rootLink, Transform.identity())
        return transforms
    }

    private fun jointTransform(origin: Pose, axis: Vec3, value: Double): Transform {
        val originT = Transform.translation(origin.xyz)
        val originR = Transform.rotationRpy(origin.rpy)
        val axisR = Transform.rotationAxis(axis, value)
        return originT.multiply(originR).multiply(axisR)
    }
}
