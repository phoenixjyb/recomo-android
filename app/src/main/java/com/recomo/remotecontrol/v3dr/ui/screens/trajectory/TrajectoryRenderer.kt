package com.recomo.remotecontrol.v3dr.ui.screens.trajectory

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * OpenGL renderer for 3D trajectory visualization
 */
class TrajectoryRenderer : GLSurfaceView.Renderer {
    
    private var trajectoryData: TrajectoryData? = null
    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var vertexCount = 0
    
    // Waypoint buffers
    private var waypointVertexBuffer: FloatBuffer? = null
    private var waypointColorBuffer: FloatBuffer? = null
    private var waypointCount = 0
    
    // Start/End marker buffers
    private var markerVertexBuffer: FloatBuffer? = null
    private var markerColorBuffer: FloatBuffer? = null
    
    // Matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    // Camera parameters - zoom works on distance from center, not scale
    var rotationX = 30f
    var rotationY = 45f
    var distance = 3.0f  // Distance from look-at point
    var panX = 0f
    var panY = 0f
    
    // Shader program
    private var shaderProgram = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var pointSizeHandle = 0
    
    // Axis buffers
    private var axisVertexBuffer: FloatBuffer? = null
    private var axisColorBuffer: FloatBuffer? = null
    
    // Grid buffers
    private var gridVertexBuffer: FloatBuffer? = null
    private var gridColorBuffer: FloatBuffer? = null
    private var gridVertexCount = 0
    
    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aColor;
        uniform mat4 uMVPMatrix;
        uniform float uPointSize;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            gl_PointSize = uPointSize;
            vColor = aColor;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()
    
    /** Reset view to default */
    fun resetView() {
        rotationX = 30f
        rotationY = 45f
        distance = 3.0f
        panX = 0f
        panY = 0f
    }
    
    /** Fit trajectory to view */
    fun fitToView() {
        resetView()
        // Trajectory is already normalized to [-0.5, 0.5] range, so distance=3 fits well
    }
    
    fun setTrajectory(data: TrajectoryData) {
        trajectoryData = data
        prepareTrajectoryBuffers(data)
        prepareWaypointBuffers(data)
        prepareMarkerBuffers(data)
    }
    
    private fun prepareTrajectoryBuffers(data: TrajectoryData) {
        val poses = data.poses
        if (poses.isEmpty()) return
        
        // Normalize to center
        val cx = data.center[0]
        val cy = data.center[1]
        val cz = data.center[2]
        val scale = if (data.scale > 0) data.scale else 1f
        
        // Create vertex data (line strip)
        val vertices = FloatArray(poses.size * 3)
        val colors = FloatArray(poses.size * 4)
        
        poses.forEachIndexed { i, pose ->
            // Normalize position to roughly [-0.5, 0.5] range
            vertices[i * 3] = (pose.x - cx) / scale
            vertices[i * 3 + 1] = (pose.y - cy) / scale
            vertices[i * 3 + 2] = (pose.z - cz) / scale
            
            // Color gradient from blue to red based on time
            val t = i.toFloat() / (poses.size - 1).coerceAtLeast(1)
            colors[i * 4] = t           // R
            colors[i * 4 + 1] = 0.3f    // G
            colors[i * 4 + 2] = 1f - t  // B
            colors[i * 4 + 3] = 1f      // A
        }
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
        
        vertexCount = poses.size
    }
    
    private fun prepareWaypointBuffers(data: TrajectoryData) {
        val poses = data.poses
        if (poses.size < 10) {
            waypointCount = 0
            return
        }
        
        // Sample waypoints every ~10% of the trajectory
        val step = (poses.size / 10).coerceAtLeast(1)
        val waypointIndices = (step until poses.size - step step step).toList()
        
        if (waypointIndices.isEmpty()) {
            waypointCount = 0
            return
        }
        
        val cx = data.center[0]
        val cy = data.center[1]
        val cz = data.center[2]
        val scale = if (data.scale > 0) data.scale else 1f
        
        val vertices = FloatArray(waypointIndices.size * 3)
        val colors = FloatArray(waypointIndices.size * 4)
        
        waypointIndices.forEachIndexed { i, poseIdx ->
            val pose = poses[poseIdx]
            vertices[i * 3] = (pose.x - cx) / scale
            vertices[i * 3 + 1] = (pose.y - cy) / scale
            vertices[i * 3 + 2] = (pose.z - cz) / scale
            
            // White/yellow waypoints
            colors[i * 4] = 1f      // R
            colors[i * 4 + 1] = 1f  // G
            colors[i * 4 + 2] = 0.5f // B
            colors[i * 4 + 3] = 1f  // A
        }
        
        waypointVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        waypointColorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
        
        waypointCount = waypointIndices.size
    }
    
    private fun prepareMarkerBuffers(data: TrajectoryData) {
        val poses = data.poses
        if (poses.size < 2) return
        
        val cx = data.center[0]
        val cy = data.center[1]
        val cz = data.center[2]
        val scale = if (data.scale > 0) data.scale else 1f
        
        val startPose = poses.first()
        val endPose = poses.last()
        
        // Start (green) and End (red) markers
        val vertices = floatArrayOf(
            (startPose.x - cx) / scale, (startPose.y - cy) / scale, (startPose.z - cz) / scale,
            (endPose.x - cx) / scale, (endPose.y - cy) / scale, (endPose.z - cz) / scale
        )
        
        val colors = floatArrayOf(
            0f, 1f, 0f, 1f,  // Start: Green
            1f, 0f, 0f, 1f   // End: Red
        )
        
        markerVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        markerColorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
    }
    
    private fun prepareAxisBuffers() {
        // X, Y, Z axes
        val axisVertices = floatArrayOf(
            // X axis (red)
            0f, 0f, 0f, 0.5f, 0f, 0f,
            // Y axis (green)
            0f, 0f, 0f, 0f, 0.5f, 0f,
            // Z axis (blue)
            0f, 0f, 0f, 0f, 0f, 0.5f
        )
        
        val axisColors = floatArrayOf(
            // X axis (red)
            1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f,
            // Y axis (green)
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f,
            // Z axis (blue)
            0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f
        )
        
        axisVertexBuffer = ByteBuffer.allocateDirect(axisVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(axisVertices)
                position(0)
            }
        
        axisColorBuffer = ByteBuffer.allocateDirect(axisColors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(axisColors)
                position(0)
            }
    }
    
    private fun prepareGridBuffers() {
        val gridLines = mutableListOf<Float>()
        val gridColors = mutableListOf<Float>()
        val gridSize = 10
        val gridStep = 0.1f
        
        // XZ plane grid
        for (i in -gridSize..gridSize) {
            val pos = i * gridStep
            // Line along X
            gridLines.addAll(listOf(-1f, 0f, pos, 1f, 0f, pos))
            // Line along Z
            gridLines.addAll(listOf(pos, 0f, -1f, pos, 0f, 1f))
            
            // Gray color
            repeat(4) {
                gridColors.addAll(listOf(0.3f, 0.3f, 0.3f, 0.5f))
            }
        }
        
        gridVertexBuffer = ByteBuffer.allocateDirect(gridLines.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(gridLines.toFloatArray())
                position(0)
            }
        
        gridColorBuffer = ByteBuffer.allocateDirect(gridColors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(gridColors.toFloatArray())
                position(0)
            }
        
        gridVertexCount = gridLines.size / 3
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glLineWidth(2f)
        
        // Compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
        
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(shaderProgram, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        pointSizeHandle = GLES20.glGetUniformLocation(shaderProgram, "uPointSize")
        
        prepareAxisBuffers()
        prepareGridBuffers()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 100f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        GLES20.glUseProgram(shaderProgram)
        
        // Setup camera
        updateViewMatrix()
        
        // Model matrix (identity for now)
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Calculate MVP
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
        
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, 4f)
        
        // Draw grid
        drawGrid()
        
        // Draw axes
        drawAxes()
        
        // Draw trajectory
        drawTrajectory()
        
        // Draw waypoints
        drawWaypoints()
        
        // Draw start/end markers
        drawMarkers()
    }
    
    private fun updateViewMatrix() {
        val radX = Math.toRadians(rotationX.toDouble())
        val radY = Math.toRadians(rotationY.toDouble())
        
        // Camera orbits around (panX, panY, 0) at 'distance'
        val eyeX = (distance * cos(radX) * sin(radY)).toFloat() + panX
        val eyeY = (distance * sin(radX)).toFloat() + panY
        val eyeZ = (distance * cos(radX) * cos(radY)).toFloat()
        
        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,  // Eye position
            panX, panY, 0f,    // Look at center (trajectory is centered at origin)
            0f, 1f, 0f         // Up vector
        )
    }
    
    private fun drawGrid() {
        gridVertexBuffer?.let { vb ->
            gridColorBuffer?.let { cb ->
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(colorHandle)
                
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)
                
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount)
                
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(colorHandle)
            }
        }
    }
    
    private fun drawAxes() {
        axisVertexBuffer?.let { vb ->
            axisColorBuffer?.let { cb ->
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(colorHandle)
                
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)
                
                GLES20.glLineWidth(3f)
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, 6)
                GLES20.glLineWidth(2f)
                
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(colorHandle)
            }
        }
    }
    
    private fun drawTrajectory() {
        vertexBuffer?.let { vb ->
            colorBuffer?.let { cb ->
                if (vertexCount < 2) return
                
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(colorHandle)
                
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)
                
                // Draw line strip
                GLES20.glLineWidth(2f)
                GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount)
                
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(colorHandle)
            }
        }
    }
    
    private fun drawWaypoints() {
        waypointVertexBuffer?.let { vb ->
            waypointColorBuffer?.let { cb ->
                if (waypointCount < 1) return
                
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(colorHandle)
                
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)
                
                // Draw waypoints as larger points
                GLES20.glUniform1f(pointSizeHandle, 10f)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, waypointCount)
                GLES20.glUniform1f(pointSizeHandle, 4f)
                
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(colorHandle)
            }
        }
    }
    
    private fun drawMarkers() {
        markerVertexBuffer?.let { vb ->
            markerColorBuffer?.let { cb ->
                GLES20.glEnableVertexAttribArray(positionHandle)
                GLES20.glEnableVertexAttribArray(colorHandle)
                
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vb)
                GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)
                
                // Draw start/end as large points
                GLES20.glUniform1f(pointSizeHandle, 20f)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 2)
                GLES20.glUniform1f(pointSizeHandle, 4f)
                
                GLES20.glDisableVertexAttribArray(positionHandle)
                GLES20.glDisableVertexAttribArray(colorHandle)
            }
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
