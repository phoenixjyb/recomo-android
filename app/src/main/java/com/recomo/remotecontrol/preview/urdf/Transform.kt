package com.recomo.remotecontrol.preview.urdf

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Transform(val m: DoubleArray) {
    companion object {
        fun identity(): Transform = Transform(doubleArrayOf(
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        ))

        fun translation(v: Vec3): Transform {
            val out = identity().m
            out[12] = v.x
            out[13] = v.y
            out[14] = v.z
            return Transform(out)
        }

        fun rotationRpy(rpy: Vec3): Transform {
            val rx = rotationX(rpy.x)
            val ry = rotationY(rpy.y)
            val rz = rotationZ(rpy.z)
            return rz.multiply(ry).multiply(rx)
        }

        fun rotationAxis(axis: Vec3, angle: Double): Transform {
            var ax = axis.x
            var ay = axis.y
            var az = axis.z
            val len = sqrt(ax * ax + ay * ay + az * az)
            if (len > 1e-9) {
                ax /= len
                ay /= len
                az /= len
            } else {
                ax = 0.0
                ay = 0.0
                az = 1.0
            }
            val c = cos(angle)
            val s = sin(angle)
            val t = 1.0 - c
            return Transform(doubleArrayOf(
                t * ax * ax + c, t * ax * ay + s * az, t * ax * az - s * ay, 0.0,
                t * ax * ay - s * az, t * ay * ay + c, t * ay * az + s * ax, 0.0,
                t * ax * az + s * ay, t * ay * az - s * ax, t * az * az + c, 0.0,
                0.0, 0.0, 0.0, 1.0
            ))
        }

        fun rotationX(angle: Double): Transform {
            val c = cos(angle)
            val s = sin(angle)
            return Transform(doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, c, s, 0.0,
                0.0, -s, c, 0.0,
                0.0, 0.0, 0.0, 1.0
            ))
        }

        fun rotationY(angle: Double): Transform {
            val c = cos(angle)
            val s = sin(angle)
            return Transform(doubleArrayOf(
                c, 0.0, -s, 0.0,
                0.0, 1.0, 0.0, 0.0,
                s, 0.0, c, 0.0,
                0.0, 0.0, 0.0, 1.0
            ))
        }

        fun rotationZ(angle: Double): Transform {
            val c = cos(angle)
            val s = sin(angle)
            return Transform(doubleArrayOf(
                c, s, 0.0, 0.0,
                -s, c, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            ))
        }
    }

    fun multiply(other: Transform): Transform {
        val a = m
        val b = other.m
        val out = DoubleArray(16)
        for (row in 0..3) {
            for (col in 0..3) {
                out[col * 4 + row] =
                    a[0 * 4 + row] * b[col * 4 + 0] +
                    a[1 * 4 + row] * b[col * 4 + 1] +
                    a[2 * 4 + row] * b[col * 4 + 2] +
                    a[3 * 4 + row] * b[col * 4 + 3]
            }
        }
        return Transform(out)
    }

    fun translation(): Vec3 = Vec3(m[12], m[13], m[14])
}
