package com.recomo.remotecontrol.v3dr.recording.arcore

import android.content.Context
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records device poses in real-time using ARCore during video capture.
 * 
 * ARCore provides Visual-Inertial Odometry (VIO) by fusing:
 * - Camera images (visual features)
 * - IMU data (accelerometer + gyroscope)
 * 
 * Output is in TUM format: timestamp tx ty tz qx qy qz qw
 */
class ArCorePoseRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "ArCorePoseRecorder"
        private const val POSE_SAMPLE_INTERVAL_MS = 33L // ~30Hz pose sampling
        const val ALGORITHM_ID = "arcore"
        const val ALGORITHM_VERSION = "1.40.0"
    }

    private var session: Session? = null
    private var poseWriter: BufferedWriter? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var scope: CoroutineScope? = null
    
    // Store poses in memory for backup
    private val poses = CopyOnWriteArrayList<PoseEntry>()
    private var startTimeNs: Long = 0
    private var poseCount = 0

    data class PoseEntry(
        val timestampSec: Double,
        val tx: Float,
        val ty: Float,
        val tz: Float,
        val qx: Float,
        val qy: Float,
        val qz: Float,
        val qw: Float
    )

    /**
     * Check if ARCore is available on this device.
     */
    fun checkAvailability(): ArCoreAvailability {
        return try {
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreAvailability.READY
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArCoreAvailability.NEEDS_INSTALL
                else -> ArCoreAvailability.NOT_SUPPORTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ARCore availability", e)
            ArCoreAvailability.NOT_SUPPORTED
        }
    }

    /**
     * Request ARCore installation if needed.
     * Returns true if ARCore is already installed or installation was triggered.
     */
    fun requestInstall(activity: android.app.Activity): Boolean {
        return try {
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, true)
            installStatus == ArCoreApk.InstallStatus.INSTALLED
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ARCore install", e)
            false
        }
    }

    /**
     * Start ARCore session and begin recording poses.
     * 
     * @param outputFile File to write poses to (TUM format)
     * @param scope CoroutineScope for background pose sampling
     * @return true if session started successfully
     */
    fun start(outputFile: File, scope: CoroutineScope): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        this.outputFile = outputFile
        this.scope = scope
        poses.clear()
        poseCount = 0

        try {
            // Create and configure ARCore session
            session = Session(context).also { sess ->
                val config = Config(sess).apply {
                    // Enable auto-focus for better tracking
                    focusMode = Config.FocusMode.AUTO
                    // Use standard tracking (not instant placement)
                    planeFindingMode = Config.PlaneFindingMode.DISABLED
                    // Light estimation not needed for VIO
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    // Use lowest depth mode (not needed)
                    depthMode = Config.DepthMode.DISABLED
                    // Camera capture not needed (we have our own)
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                sess.configure(config)
            }

            // Open output file
            poseWriter = BufferedWriter(FileWriter(outputFile))
            // Write TUM header comment
            poseWriter?.write("# ARCore trajectory - TUM format: timestamp tx ty tz qx qy qz qw\n")

            // Resume ARCore session
            session?.resume()
            isRecording = true
            startTimeNs = System.nanoTime()

            // Start background pose sampling
            recordingJob = scope.launch(Dispatchers.IO) {
                samplePosesLoop()
            }

            Log.i(TAG, "ARCore pose recording started: ${outputFile.absolutePath}")
            return true

        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "ARCore APK too old", e)
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "ARCore SDK too old", e)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ARCore session", e)
        }

        cleanup()
        return false
    }

    private suspend fun samplePosesLoop() {
        while (isRecording && scope?.isActive == true) {
            try {
                session?.let { sess ->
                    // Update ARCore frame
                    val frame = sess.update()
                    
                    // Only record when tracking is good
                    if (frame.camera.trackingState == TrackingState.TRACKING) {
                        val pose = frame.camera.pose
                        val timestampSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
                        
                        // Extract translation
                        val translation = pose.translation
                        val tx = translation[0]
                        val ty = translation[1]
                        val tz = translation[2]
                        
                        // Extract rotation as quaternion (xyzw)
                        val rotation = pose.rotationQuaternion
                        val qx = rotation[0]
                        val qy = rotation[1]
                        val qz = rotation[2]
                        val qw = rotation[3]
                        
                        // Write to file immediately
                        val line = String.format(
                            java.util.Locale.US,
                            "%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f\n",
                            timestampSec, tx, ty, tz, qx, qy, qz, qw
                        )
                        poseWriter?.write(line)
                        
                        // Also store in memory
                        poses.add(PoseEntry(timestampSec, tx, ty, tz, qx, qy, qz, qw))
                        poseCount++
                        
                        if (poseCount % 100 == 0) {
                            Log.d(TAG, "Recorded $poseCount poses, last at t=${String.format("%.2f", timestampSec)}s")
                            poseWriter?.flush() // Periodic flush
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error sampling pose", e)
            }
            
            delay(POSE_SAMPLE_INTERVAL_MS)
        }
    }

    /**
     * Stop recording and finalize output file.
     * 
     * @return The output file containing poses, or null if recording failed
     */
    fun stop(): File? {
        if (!isRecording) {
            return null
        }

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            // Pause ARCore session
            session?.pause()
            
            // Finalize and close file
            poseWriter?.flush()
            poseWriter?.close()
            poseWriter = null

            val result = outputFile
            val count = poseCount
            Log.i(TAG, "ARCore pose recording stopped: $count poses recorded")
            
            cleanup()
            return if (count > 0) result else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping pose recording", e)
            cleanup()
            return null
        }
    }

    private fun cleanup() {
        try {
            poseWriter?.close()
        } catch (e: Exception) { }
        poseWriter = null
        
        try {
            session?.close()
        } catch (e: Exception) { }
        session = null
        
        outputFile = null
        isRecording = false
    }

    /**
     * Get the recorded poses as a list.
     */
    fun getPoses(): List<PoseEntry> = poses.toList()

    /**
     * Get the number of poses recorded.
     */
    fun getPoseCount(): Int = poseCount

    enum class ArCoreAvailability {
        READY,          // ARCore is installed and ready
        NEEDS_INSTALL,  // ARCore needs to be installed
        NOT_SUPPORTED   // Device doesn't support ARCore
    }
}
