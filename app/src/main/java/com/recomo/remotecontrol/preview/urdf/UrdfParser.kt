package com.recomo.remotecontrol.preview.urdf

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object UrdfParser {
    fun loadFromAssets(context: Context, assetPath: String): UrdfModel {
        context.assets.open(assetPath).use { input ->
            return parse(input)
        }
    }

    private fun parse(input: InputStream): UrdfModel {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val joints = mutableMapOf<String, UrdfJoint>()
        val linkNames = mutableSetOf<String>()
        val childLinks = mutableSetOf<String>()
        val linkMeshes = mutableMapOf<String, String>()

        var currentJointName: String? = null
        var currentJointType: String? = null
        var currentParent: String? = null
        var currentChild: String? = null
        var currentOrigin = Pose.IDENTITY
        var currentAxis = Vec3(0.0, 0.0, 1.0)
        var currentLinkName: String? = null
        var currentLinkMesh: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "link" -> {
                            val name = parser.getAttributeValue(null, "name")
                            if (!name.isNullOrBlank()) {
                                linkNames.add(name)
                                currentLinkName = name
                                currentLinkMesh = null
                            }
                        }
                        "mesh" -> {
                            if (currentLinkName != null && currentLinkMesh == null) {
                                val filename = parser.getAttributeValue(null, "filename")
                                if (!filename.isNullOrBlank()) {
                                    currentLinkMesh = filename
                                }
                            }
                        }
                        "joint" -> {
                            currentJointName = parser.getAttributeValue(null, "name")
                            currentJointType = parser.getAttributeValue(null, "type")
                            currentParent = null
                            currentChild = null
                            currentOrigin = Pose.IDENTITY
                            currentAxis = Vec3(0.0, 0.0, 1.0)
                        }
                        "parent" -> {
                            currentParent = parser.getAttributeValue(null, "link")
                        }
                        "child" -> {
                            currentChild = parser.getAttributeValue(null, "link")
                        }
                        "origin" -> {
                            val xyz = parseVec3(parser.getAttributeValue(null, "xyz"))
                            val rpy = parseVec3(parser.getAttributeValue(null, "rpy"))
                            currentOrigin = Pose(xyz, rpy)
                        }
                        "axis" -> {
                            currentAxis = parseVec3(parser.getAttributeValue(null, "xyz"))
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "joint" && currentJointName != null &&
                        currentJointType != null && currentParent != null && currentChild != null
                    ) {
                        val joint = UrdfJoint(
                            name = currentJointName!!,
                            type = currentJointType!!,
                            parent = currentParent!!,
                            child = currentChild!!,
                            origin = currentOrigin,
                            axis = currentAxis
                        )
                        joints[joint.name] = joint
                        childLinks.add(joint.child)
                    }
                    if (parser.name == "link" && currentLinkName != null) {
                        if (currentLinkMesh != null) {
                            linkMeshes[currentLinkName!!] = currentLinkMesh!!
                        }
                        currentLinkName = null
                        currentLinkMesh = null
                    }
                }
            }
            event = parser.next()
        }

        val rootLink = linkNames.firstOrNull { it !in childLinks } ?: linkNames.first()
        val jointsByParent = joints.values.groupBy { it.parent }
        return UrdfModel(joints, jointsByParent, linkMeshes, rootLink)
    }

    private fun parseVec3(raw: String?): Vec3 {
        if (raw.isNullOrBlank()) return Vec3.ZERO
        val parts = raw.trim().split("\\s+".toRegex())
        val values = parts.mapNotNull { it.toDoubleOrNull() }
        return Vec3(
            values.getOrElse(0) { 0.0 },
            values.getOrElse(1) { 0.0 },
            values.getOrElse(2) { 0.0 }
        )
    }
}
