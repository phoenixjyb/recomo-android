package com.recomo.remotecontrol.v3dr.vio.openvins

import android.util.Log

object OpenVinsNative {
    private const val TAG = "OpenVinsNative"

    @Volatile
    private var loaded = false

    init {
        try {
            System.loadLibrary("openvins_jni")
            loaded = true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load openvins_jni", t)
            loaded = false
        }
    }

    fun isLoaded(): Boolean = loaded

    external fun nativeVersion(): String

    /**
     * Returns 0 when OpenCV/Boost/Eigen are available and linked.
     * 2 means dependencies are missing, 3 means runtime error.
     */
    external fun nativeSmokeTest(): Int

    /**
     * Create a native OpenVINS session and return a handle (0 on failure).
     */
    external fun nativeInit(
        configPath: String,
        outputDir: String
    ): Long

    /**
     * Feed a single IMU sample (timestamp in ns since boot).
     */
    external fun nativeFeedImu(
        handle: Long,
        timestampNs: Long,
        gx: Float,
        gy: Float,
        gz: Float,
        ax: Float,
        ay: Float,
        az: Float
    ): Int

    /**
     * Feed a grayscale camera frame (row-major).
     */
    external fun nativeFeedFrame(
        handle: Long,
        timestampNs: Long,
        width: Int,
        height: Int,
        grayBytes: ByteArray
    ): Int

    /**
     * Finalize and flush outputs.
     */
    external fun nativeFinalize(handle: Long): Int

    /**
     * Release native resources.
     */
    external fun nativeRelease(handle: Long)
}
