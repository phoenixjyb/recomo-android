package com.recomo.remotecontrol.v3dr.ui.screens.trajectory

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * View projection type for 2D orthographic views
 */
enum class ViewProjection {
    TOP,    // XZ plane (looking down Y axis)
    FRONT,  // XY plane (looking down Z axis)
    SIDE    // YZ plane (looking down X axis)
}

/**
 * OpenGL renderer for 2D orthographic trajectory views
 */
class TrajectoryRenderer2D(
    private val viewProjection: ViewProjection
) : GLSurfaceView.Renderer {
    
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
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    // Camera parameters
    var zoom = 1.0f
    var panX = 0f
    var panY = 0f
    
    // Shader program
    private var shaderProgram = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    private var pointSizeHandle = 0
    
    // Grid buffers
    private var gridVertexBuffer: FloatBuffer? = null
    private var gridColorBuffer: FloatBuffer? = null
    private var gridVertexCount = 0
    
    // Axis buffers
    private var axisVertexBuffer: FloatBuffer? = null
    private var axisColorBuffer: FloatBuffer? = null
    
    private var aspectRatio = 1f
    
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
        zoom = 1.0f
        panX = 0f
        panY = 0f
    }
    
    fun setTrajectory(data: TrajectoryData) {
        trajectoryData = data
        prepareTrajectoryBuffers(data)
        prepareWaypointBuffers(data)
        prepareMarkerBuffers(data)
    }
    
    private fun project(pose: Pose3D, cx: Float, cy: Float, cz: Float, scale: Float): FloatArray {
        val nx = (pose.x - cx) / scale
        val ny = (pose.y - cy) / scale
        val nz = (pose.z - cz) / scale
        
        return when (viewProjection) {
            ViewProjection.TOP -> floatArrayOf(nx, nz, 0f)
            ViewProjection.FRONT -> floatArrayOf(nx, ny, 0f)
            ViewProjection.SIDE -> floatArrayOf(nz, ny, 0f)
        }
    }
    
    private fun prepareTrajectoryBuffers(data: TrajectoryData) {
        val poses = data.poses
        if (poses.isEmpty()) return
        
        val cx = data.center[0]
        val cy = data.center[1]
        val cz = data.center[2]
        val scale = if (data.scale > 0) data.scale else 1f
        
        val vertices = FloatArray(poses.size * 3)
        val colors = FloatArray(poses.size * 4)
        
        poses.forEachIndexed { i, pose ->
            val projected = project(pose, cx, cy, cz, scale)
            vertices[i * 3] = projected[0]
            vertices[i * 3 + 1] = projected[1]
            vertices[i * 3 + 2] = projected[2]
            
            // Color gradient
            val t = i.toFloat() / (poses.size - 1).coerceAtLeast(1)
            colors[i * 4] = t
            colors[i * 4 + 1] = 0.3f
            colors[i * 4 + 2] = 1f - t
            colors[i * 4 + 3] = 1f
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
            val projected = project(poses[poseIdx], cx, cy, cz, scale)
            vertices[i * 3] = projected[0]
            vertices[i * 3 + 1] = projected[1]
            vertices[i * 3 + 2] = projected[2]
            
            // Yellow waypoints
            colors[i * 4] = 1f
            colors[i * 4 + 1] = 1f
            colors[i * 4 + 2] = 0.5f
            colors[i * 4 + 3] = 1f
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
        
        val startProj = project(poses.first(), cx, cy, cz, scale)
        val endProj = project(poses.last(), cx, cy, cz, scale)
        
        val vertices = floatArrayOf(
            startProj[0], startProj[1], startProj[2],
            endProj[0], endProj[1], endProj[2]
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
    
    private fun prepareGridBuffers() {
        val gridLines = mutableListOf<Float>()
        val gridColors = mutableListOf<Float>()
        val gridSize = 10
        val gridStep = 0.1f
        
        for (i in -gridSize..gridSize) {
            val pos = i * gridStep
            // Horizontal lines
            gridLines.addAll(listOf(-1f, pos, 0f, 1f, pos, 0f))
            // Vertical lines
            gridLines.addAll(listOf(pos, -1f, 0f, pos, 1f, 0f))
            
            repeat(4) {
                gridColors.addAll(listOf(0.25f, 0.25f, 0.3f, 0.5f))
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
    
    private fun prepareAxisBuffers() {
        val (hColor, vColor) = when (viewProjection) {
            ViewProjection.TOP -> Pair(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f))
            ViewProjection.FRONT -> Pair(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))
            ViewProjection.SIDE -> Pair(floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f))
        }
        
        val axisVertices = floatArrayOf(
            // Horizontal axis
            -0.8f, 0f, 0f, 0.8f, 0f, 0f,
            // Vertical axis
            0f, -0.8f, 0f, 0f, 0.8f, 0f
        )
        
        val axisColors = floatArrayOf(
            hColor[0], hColor[1], hColor[2], 0.8f, hColor[0], hColor[1], hColor[2], 0.8f,
            vColor[0], vColor[1], vColor[2], 0.8f, vColor[0], vColor[1], vColor[2], 0.8f
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
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.12f, 0.12f, 0.15f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glLineWidth(1.5f)
        
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
        
        prepareGridBuffers()
        prepareAxisBuffers()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.toFloat()
        updateProjection()
    }
    
    private fun updateProjection() {
        val halfWidth = zoom * aspectRatio
        val halfHeight = zoom
        
        Matrix.orthoM(
            projectionMatrix, 0,
            -halfWidth + panX, halfWidth + panX,
            -halfHeight + panY, halfHeight + panY,
            -1f, 1f
        )
        
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        GLES20.glUseProgram(shaderProgram)
        
        updateProjection()
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(pointSizeHandle, 3f)
        
        drawGrid()
        drawAxes()
        drawTrajectory()
        drawWaypoints()
        drawMarkers()
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
                
                GLES20.glLineWidth(2f)
                GLES20.glDrawArrays(GLES20.GL_LINES, 0, 4)
                GLES20.glLineWidth(1.5f)
                
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
                
                GLES20.glLineWidth(2f)
                GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount)
                GLES20.glLineWidth(1.5f)
                
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
                
                GLES20.glUniform1f(pointSizeHandle, 8f)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, waypointCount)
                GLES20.glUniform1f(pointSizeHandle, 3f)
                
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
                
                GLES20.glUniform1f(pointSizeHandle, 14f)
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 2)
                GLES20.glUniform1f(pointSizeHandle, 3f)
                
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
