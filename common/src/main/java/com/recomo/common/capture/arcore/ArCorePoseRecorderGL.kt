package com.recomo.common.capture.arcore

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * ARCore pose recorder that uses a minimal hidden GLSurfaceView for OpenGL context.
 * 
 * ARCore requires an OpenGL context for session.update() to work properly.
 * This implementation creates a tiny 1x1 pixel GLSurfaceView that runs ARCore
 * tracking without displaying anything, purely for pose extraction.
 * 
 * Note: This runs ARCore's camera separately from Camera2, so both cameras
 * cannot be active simultaneously on most devices. This recorder should be
 * used as an alternative to Camera2 recording, not alongside it.
 * 
 * For simultaneous operation, consider ARCore's SharedCamera API instead.
 */
class ArCorePoseRecorderGL(private val activity: Activity) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ArCorePoseRecorderGL"
        const val ALGORITHM_ID = "arcore"
        const val ALGORITHM_VERSION = "1.40.0"
    }

    private var glSurfaceView: GLSurfaceView? = null
    private var session: Session? = null
    private var poseWriter: BufferedWriter? = null
    private var outputFile: File? = null
    
    private val isRecording = AtomicBoolean(false)
    private val poseCount = AtomicInteger(0)
    private var startTimeNs: Long = 0
    
    // Texture ID for ARCore camera (required even if not displayed)
    private var cameraTextureId: Int = -1

    /**
     * Check if ARCore is available on this device.
     */
    fun checkAvailability(): ArCoreAvailability {
        return try {
            when (ArCoreApk.getInstance().checkAvailability(activity)) {
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
     * Start ARCore pose recording.
     * Must be called from the main/UI thread.
     * 
     * @param outputFile File to write poses to (TUM format)
     * @return true if started successfully
     */
    fun start(outputFile: File): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return false
        }

        this.outputFile = outputFile
        poseCount.set(0)

        try {
            // Open output file
            poseWriter = BufferedWriter(FileWriter(outputFile))
            poseWriter?.write("# ARCore trajectory - TUM format: timestamp tx ty tz qx qy qz qw\n")

            // Create hidden GLSurfaceView
            glSurfaceView = GLSurfaceView(activity).apply {
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(this@ArCorePoseRecorderGL)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }

            // Add as a tiny invisible view
            activity.runOnUiThread {
                val params = FrameLayout.LayoutParams(1, 1)
                (activity.window.decorView as? ViewGroup)?.addView(glSurfaceView, params)
            }

            startTimeNs = System.nanoTime()
            isRecording.set(true)
            
            Log.i(TAG, "ARCore pose recording started: ${outputFile.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ARCore pose recording", e)
            cleanup()
            return false
        }
    }

    /**
     * Stop recording and finalize output file.
     * Must be called from the main/UI thread.
     * 
     * @return The output file containing poses, or null if recording failed
     */
    fun stop(): File? {
        if (!isRecording.get()) {
            return null
        }

        isRecording.set(false)

        try {
            // Pause and close ARCore session
            session?.pause()
            session?.close()
            session = null

            // Remove GLSurfaceView
            activity.runOnUiThread {
                glSurfaceView?.let { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                }
                glSurfaceView = null
            }

            // Finalize file
            poseWriter?.flush()
            poseWriter?.close()
            poseWriter = null

            val count = poseCount.get()
            Log.i(TAG, "ARCore pose recording stopped: $count poses recorded")

            return if (count > 0) outputFile else null

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping pose recording", e)
            cleanup()
            return null
        }
    }

    fun getPoseCount(): Int = poseCount.get()

    // GLSurfaceView.Renderer callbacks

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "GL surface created")
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // Create texture for ARCore camera
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]

        // Create ARCore session
        try {
            session = Session(activity).also { sess ->
                val arConfig = Config(sess).apply {
                    focusMode = Config.FocusMode.AUTO
                    planeFindingMode = Config.PlaneFindingMode.DISABLED
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    depthMode = Config.DepthMode.DISABLED
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                sess.configure(arConfig)
            }
            session?.resume()
            Log.i(TAG, "ARCore session created and resumed")
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "ARCore APK too old", e)
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "ARCore SDK too old", e)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available for ARCore", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARCore session", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(activity.windowManager.defaultDisplay.rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (!isRecording.get()) return

        val sess = session ?: return

        try {
            // Set camera texture (required for ARCore to work)
            sess.setCameraTextureName(cameraTextureId)
            
            // Update ARCore frame
            val frame = sess.update()
            val camera = frame.camera

            // Only record when tracking is good
            if (camera.trackingState == TrackingState.TRACKING) {
                val pose = camera.pose
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

                // Write to file
                val line = String.format(
                    Locale.US,
                    "%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f\n",
                    timestampSec, tx, ty, tz, qx, qy, qz, qw
                )
                
                synchronized(this) {
                    poseWriter?.write(line)
                }
                
                val count = poseCount.incrementAndGet()
                if (count % 100 == 0) {
                    Log.d(TAG, "Recorded $count poses, t=${String.format("%.2f", timestampSec)}s")
                    synchronized(this) {
                        poseWriter?.flush()
                    }
                }
            }
        } catch (e: CameraNotAvailableException) {
            Log.w(TAG, "Camera not available", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error in onDrawFrame", e)
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

        activity.runOnUiThread {
            glSurfaceView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
            glSurfaceView = null
        }

        outputFile = null
        isRecording.set(false)
    }

    enum class ArCoreAvailability {
        READY,
        NEEDS_INSTALL,
        NOT_SUPPORTED
    }
}
