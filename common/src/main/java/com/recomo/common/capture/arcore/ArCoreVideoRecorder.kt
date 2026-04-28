package com.recomo.common.capture.arcore

import android.app.Activity
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.opengles.GL10

/**
 * ARCore-based video recorder with real-time pose tracking.
 * 
 * This class provides both video recording (using ARCore's camera frames) and
 * simultaneous pose tracking in TUM format. It uses a GLSurfaceView for AR
 * rendering and EGL for encoding frames to video.
 * 
 * Usage:
 * 1. Create instance and attach to GLSurfaceView
 * 2. Call startRecording() to begin
 * 3. Call stopRecording() to finalize
 * 4. Call release() when done
 */
class ArCoreVideoRecorder(
    private val activity: Activity,
    private val glSurfaceView: GLSurfaceView
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ArCoreVideoRecorder"
        const val ALGORITHM_ID = "arcore"
        const val ALGORITHM_VERSION = "1.40.0"
        
        // Video encoding settings
        private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val VIDEO_BITRATE = 6_000_000  // 6 Mbps
        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_I_FRAME_INTERVAL = 1
        
        // Quad coordinates for texture transformation
        private val QUAD_COORDS = floatArrayOf(
            -1f, -1f,  // bottom-left
             1f, -1f,  // bottom-right
            -1f,  1f,  // top-left
             1f,  1f   // top-right
        )
    }

    // ARCore session
    private var session: Session? = null
    private var cameraTextureId: Int = -1
    
    // Video encoding
    private var videoEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex: Int = -1
    private var muxerStarted = false
    private var encoderSurface: Surface? = null
    
    // EGL for encoder surface
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglEncoderSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT
    
    // Background rendering shader
    private var backgroundProgram: Int = 0
    private var backgroundPositionAttrib: Int = 0
    private var backgroundTexCoordAttrib: Int = 0
    private var backgroundTextureUniform: Int = 0
    private var quadVertexBuffer: FloatBuffer? = null
    private var quadTexCoordBuffer: FloatBuffer? = null
    
    // Pose recording
    private var poseWriter: BufferedWriter? = null
    private val poseCount = AtomicInteger(0)
    private var recordingStartTimeNs: Long = 0
    
    // State
    private val isRecording = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var videoFile: File? = null
    private var poseFile: File? = null
    private var videoWidth: Int = 1920
    private var videoHeight: Int = 1080
    
    // Callback
    var onSessionReady: (() -> Unit)? = null
    var onTrackingStateChanged: ((TrackingState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * Check ARCore availability
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
     * Set video resolution
     */
    fun setVideoResolution(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
    }

    /**
     * Start recording video and poses
     */
    fun startRecording(outputDir: File): Boolean {
        if (!isInitialized.get()) {
            Log.e(TAG, "Not initialized")
            return false
        }
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return false
        }

        try {
            outputDir.mkdirs()
            
            videoFile = File(outputDir, "video.mp4")
            poseFile = File(outputDir, "trajectory_${ALGORITHM_ID}_tum.txt")
            
            // Initialize pose writer
            poseWriter = BufferedWriter(FileWriter(poseFile))
            poseWriter?.write("# ARCore trajectory - TUM format\n")
            poseWriter?.write("# algorithm: $ALGORITHM_ID\n")
            poseWriter?.write("# version: $ALGORITHM_VERSION\n")
            poseWriter?.write("# timestamp tx ty tz qx qy qz qw\n")
            
            // Initialize video encoder
            initializeEncoder()
            
            poseCount.set(0)
            recordingStartTimeNs = System.nanoTime()
            isRecording.set(true)
            
            Log.i(TAG, "Recording started: ${videoFile?.absolutePath}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            return false
        }
    }

    /**
     * Stop recording
     */
    fun stopRecording(): Pair<File?, File?> {
        if (!isRecording.get()) {
            return Pair(null, null)
        }
        
        isRecording.set(false)
        
        try {
            // Finalize encoder
            drainEncoder(true)
            
            videoEncoder?.stop()
            videoEncoder?.release()
            videoEncoder = null
            
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
            muxer = null
            muxerStarted = false
            
            encoderSurface?.release()
            encoderSurface = null
            
            // Release EGL encoder surface
            if (eglEncoderSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglEncoderSurface)
                eglEncoderSurface = EGL14.EGL_NO_SURFACE
            }
            
            // Finalize pose file
            poseWriter?.flush()
            poseWriter?.close()
            poseWriter = null
            
            val count = poseCount.get()
            Log.i(TAG, "Recording stopped: $count poses, video: ${videoFile?.absolutePath}")
            
            return Pair(videoFile, if (count > 0) poseFile else null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            return Pair(null, null)
        }
    }

    /**
     * Resume ARCore session (call in Activity.onResume)
     */
    fun resume() {
        session?.let { sess ->
            try {
                sess.resume()
                Log.d(TAG, "ARCore session resumed")
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Camera not available", e)
                onError?.invoke("Camera not available")
            }
        }
    }

    /**
     * Pause ARCore session (call in Activity.onPause)
     */
    fun pause() {
        session?.pause()
    }

    /**
     * Release all resources
     */
    fun release() {
        isRecording.set(false)
        isInitialized.set(false)
        
        stopRecording()
        
        session?.close()
        session = null
        
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
        
        Log.i(TAG, "Released")
    }

    fun getPoseCount(): Int = poseCount.get()
    fun isRecording(): Boolean = isRecording.get()
    fun getSession(): Session? = session

    // GLSurfaceView.Renderer implementation

    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        Log.d(TAG, "GL surface created")
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        
        // Save shared context for encoder surface
        sharedContext = EGL14.eglGetCurrentContext()
        eglDisplay = EGL14.eglGetCurrentDisplay()
        
        // Create external texture for ARCore camera
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        
        // Initialize background shader for rendering camera to encoder
        initializeBackgroundShader()
        
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
                sess.resume()
                
                isInitialized.set(true)
                Log.i(TAG, "ARCore session created and resumed")
                onSessionReady?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARCore session", e)
            onError?.invoke("ARCore initialization failed: ${e.message}")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(activity.windowManager.defaultDisplay.rotation, width, height)
        Log.d(TAG, "Surface changed: ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        val sess = session ?: return
        
        try {
            sess.setCameraTextureName(cameraTextureId)
            val frame = sess.update()
            val camera = frame.camera
            
            // Draw camera background to preview
            if (camera.trackingState == TrackingState.TRACKING) {
                drawCameraBackground(frame)
            }
            
            // Notify tracking state changes
            onTrackingStateChanged?.invoke(camera.trackingState)
            
            // Record if active
            if (isRecording.get()) {
                // Record pose
                if (camera.trackingState == TrackingState.TRACKING) {
                    recordPose(camera.displayOrientedPose)
                }
                
                // Encode video frame
                encodeFrame(frame)
            }
            
        } catch (e: CameraNotAvailableException) {
            Log.w(TAG, "Camera not available", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error in onDrawFrame", e)
        }
    }

    private fun initializeBackgroundShader() {
        val vertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()
        
        val fragmentShaderCode = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """.trimIndent()
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        backgroundProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        
        backgroundPositionAttrib = GLES20.glGetAttribLocation(backgroundProgram, "a_Position")
        backgroundTexCoordAttrib = GLES20.glGetAttribLocation(backgroundProgram, "a_TexCoord")
        backgroundTextureUniform = GLES20.glGetUniformLocation(backgroundProgram, "u_Texture")
        
        // Fullscreen quad vertices
        val quadVertices = floatArrayOf(
            -1f, -1f,  // bottom-left
             1f, -1f,  // bottom-right
            -1f,  1f,  // top-left
             1f,  1f   // top-right
        )
        quadVertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        quadVertexBuffer?.position(0)
        
        // Texture coordinates (flipped for camera)
        val quadTexCoords = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
        quadTexCoordBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadTexCoords)
        quadTexCoordBuffer?.position(0)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun drawCameraBackground(frame: Frame) {
        // Transform texture coordinates based on display rotation
        val transformedTexCoords = FloatArray(8)
        frame.transformCoordinates2d(
            com.google.ar.core.Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            QUAD_COORDS,
            com.google.ar.core.Coordinates2d.TEXTURE_NORMALIZED,
            transformedTexCoords
        )
        
        // Update texture coordinate buffer with transformed coords
        quadTexCoordBuffer?.clear()
        quadTexCoordBuffer?.put(transformedTexCoords)
        quadTexCoordBuffer?.position(0)
        
        GLES20.glUseProgram(backgroundProgram)
        
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(backgroundTextureUniform, 0)
        
        GLES20.glEnableVertexAttribArray(backgroundPositionAttrib)
        GLES20.glVertexAttribPointer(backgroundPositionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)
        
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttrib)
        GLES20.glVertexAttribPointer(backgroundTexCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoordBuffer)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES20.glDisableVertexAttribArray(backgroundPositionAttrib)
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttrib)
    }

    private fun recordPose(pose: com.google.ar.core.Pose) {
        val timestampSec = (System.nanoTime() - recordingStartTimeNs) / 1_000_000_000.0
        
        val translation = pose.translation
        val rotation = pose.rotationQuaternion
        
        val line = String.format(
            Locale.US,
            "%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f\n",
            timestampSec,
            translation[0], translation[1], translation[2],
            rotation[0], rotation[1], rotation[2], rotation[3]
        )
        
        synchronized(this) {
            poseWriter?.write(line)
        }
        
        val count = poseCount.incrementAndGet()
        if (count % 100 == 0) {
            Log.d(TAG, "Recorded $count poses")
            synchronized(this) {
                poseWriter?.flush()
            }
        }
    }

    private fun initializeEncoder() {
        // Create MediaCodec encoder
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        }
        
        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderSurface = createInputSurface()
            start()
        }
        
        // Create EGL surface for encoder
        createEncoderEglSurface()
        
        // Create muxer
        muxer = MediaMuxer(videoFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoTrackIndex = -1
        muxerStarted = false
        
        Log.d(TAG, "Encoder initialized: ${videoWidth}x${videoHeight}")
    }

    private fun createEncoderEglSurface() {
        val surface = encoderSurface ?: return
        
        // Create EGL context that shares with the GLSurfaceView context
        val attrib3List = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        
        // Find a suitable config
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        
        if (numConfigs[0] == 0) {
            Log.e(TAG, "No EGL config found for encoder")
            return
        }
        
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], sharedContext, attrib3List, 0)
        
        // Create window surface for encoder
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglEncoderSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        
        Log.d(TAG, "Encoder EGL surface created")
    }

    private fun encodeFrame(frame: Frame) {
        if (eglEncoderSurface == EGL14.EGL_NO_SURFACE) return
        
        // Save current EGL state
        val savedDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val savedReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val savedContext = EGL14.eglGetCurrentContext()
        
        try {
            // Switch to encoder surface
            EGL14.eglMakeCurrent(eglDisplay, eglEncoderSurface, eglEncoderSurface, eglContext)
            
            GLES20.glViewport(0, 0, videoWidth, videoHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            
            // Draw camera background to encoder
            drawCameraBackground(frame)
            
            // Set presentation time and swap
            val presentationTimeNs = System.nanoTime() - recordingStartTimeNs
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglEncoderSurface, presentationTimeNs)
            EGL14.eglSwapBuffers(eglDisplay, eglEncoderSurface)
            
            // Drain encoder
            drainEncoder(false)
            
        } finally {
            // Restore previous EGL state
            EGL14.eglMakeCurrent(eglDisplay, savedDrawSurface, savedReadSurface, savedContext)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val encoder = videoEncoder ?: return
        
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }
        
        val bufferInfo = MediaCodec.BufferInfo()
        
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    // Wait for more output when ending
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        Log.w(TAG, "Format changed after muxer started")
                    } else {
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                        muxer?.start()
                        muxerStarted = true
                        Log.d(TAG, "Muxer started, track index: $videoTrackIndex")
                    }
                }
                outputIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    
                    if (bufferInfo.size > 0 && muxerStarted && outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer?.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                    }
                    
                    encoder.releaseOutputBuffer(outputIndex, false)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
                else -> break
            }
        }
    }

    private fun cleanup() {
        try { poseWriter?.close() } catch (e: Exception) { }
        poseWriter = null
        
        try { videoEncoder?.release() } catch (e: Exception) { }
        videoEncoder = null
        
        try { muxer?.release() } catch (e: Exception) { }
        muxer = null
        muxerStarted = false
    }

    enum class ArCoreAvailability {
        READY,
        NEEDS_INSTALL,
        NOT_SUPPORTED
    }
}
