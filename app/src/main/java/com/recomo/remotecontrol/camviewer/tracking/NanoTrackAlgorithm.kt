package com.recomo.remotecontrol.camviewer.tracking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * NanoTrack algorithm implementation in pure Kotlin.
 * This is the core tracking algorithm that works with any inference backend.
 * 
 * Algorithm reference: https://github.com/HonglinChu/NanoTrack
 * 
 * Key components:
 * - Backbone: Extract features from template (127x127) and search (255x255) images
 * - Head: Compute correlation between template and search features to predict bbox
 */
class NanoTrackAlgorithm(
    private val backboneInference: BackboneInference,
    private val headInference: HeadInference
) {
    companion object {
        private const val TAG = "NanoTrackAlgorithm"
        
        // Model hyperparameters (same as C++ version)
        const val TEMPLATE_SIZE = 127
        const val SEARCH_SIZE = 255
        const val STRIDE = 16
        const val CONTEXT_AMOUNT = 0.5f
        const val WINDOW_INFLUENCE = 0.40f // Reduced from 0.455f to reduce background stickiness
        const val SCALE_LR = 0.37f
        const val PENALTY_K = 0.15f
    }
    
    // Tracking state
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var targetWidth: Float = 0f
    private var targetHeight: Float = 0f
    private var score: Float = 0f
    
    private var resizeScale: Float = 1f
    private var originWidth: Int = 0
    private var originHeight: Int = 0
    
    private var gridSize: Int = 0
    private lateinit var hanningWindow: FloatArray
    private lateinit var xGrid: FloatArray
    private lateinit var yGrid: FloatArray
    
    private var templateFeatures: FloatArray? = null
    private var isInitialized: Boolean = false
    
    // Reusable buffers to avoid allocation every frame
    private var searchInputBuffer: FloatArray? = null
    private var templateInputBuffer: FloatArray? = null
    
    /**
     * Initialize tracker with a target ROI in the given frame.
     * @param frame BGR bitmap of the frame
     * @param roi Target bounding box in image coordinates
     * @return true if initialization was successful
     */
    fun initialize(frame: Bitmap, roi: RectF): Boolean {
        Log.d(TAG, "Initializing tracker with ROI: $roi")
        
        originWidth = frame.width
        originHeight = frame.height
        
        // Clamp ROI to image bounds
        val clampedRoi = clampRect(roi, originWidth, originHeight)
        if (clampedRoi.width() <= 1f || clampedRoi.height() <= 1f) {
            Log.w(TAG, "ROI too small, skipping initialization")
            return false
        }
        
        // Initialize state
        centerX = clampedRoi.centerX()
        centerY = clampedRoi.centerY()
        targetWidth = clampedRoi.width()
        targetHeight = clampedRoi.height()
        score = 1f
        
        // Calculate grid size and create helper matrices
        gridSize = (SEARCH_SIZE - TEMPLATE_SIZE) / STRIDE + 8
        generateGrid()
        createHanningWindow()
        
        // Extract template patch
        val templatePatch = getSubwindow(frame, TEMPLATE_SIZE)
        
        // Reuse or create buffer
        if (templateInputBuffer == null || templateInputBuffer!!.size != 3 * TEMPLATE_SIZE * TEMPLATE_SIZE) {
            templateInputBuffer = FloatArray(3 * TEMPLATE_SIZE * TEMPLATE_SIZE)
        }
        val templateInput = bitmapToFloatArray(templatePatch, templateInputBuffer!!)
        
        // Run backbone on template
        templateFeatures = backboneInference.runBackbone127(templateInput)
        if (templateFeatures == null) {
            Log.e(TAG, "Failed to compute template features")
            return false
        }
        
        isInitialized = true
        Log.d(TAG, "Tracker initialized successfully")
        return true
    }
    
    /**
     * Track the target in a new frame.
     * @param frame BGR bitmap of the new frame
     * @return Pair of (bounding box, confidence score), or null if tracking failed
     */
    fun track(frame: Bitmap): Pair<RectF, Float>? {
        if (!isInitialized || templateFeatures == null) {
            return null
        }
        
        val t0 = System.currentTimeMillis()
        
        originWidth = frame.width
        originHeight = frame.height
        
        // Extract search patch
        val searchPatch = getSubwindow(frame, SEARCH_SIZE)
        val t1 = System.currentTimeMillis()
        
        // Reuse or create buffer
        if (searchInputBuffer == null || searchInputBuffer!!.size != 3 * SEARCH_SIZE * SEARCH_SIZE) {
            searchInputBuffer = FloatArray(3 * SEARCH_SIZE * SEARCH_SIZE)
        }
        val searchInput = bitmapToFloatArray(searchPatch, searchInputBuffer!!)
        val t2 = System.currentTimeMillis()
        
        // Run backbone on search region
        val searchFeatures = backboneInference.runBackbone255(searchInput) ?: return null
        val t3 = System.currentTimeMillis()
        
        // Run head network
        val headOutput = headInference.runHead(templateFeatures!!, searchFeatures) ?: return null
        val t4 = System.currentTimeMillis()
        
        // Parse head output: scores (2 x grid^2) and bboxes (4 x grid^2)
        val scoreOutput = headOutput.first
        val bboxOutput = headOutput.second
        
        // Apply softmax to scores
        val softmaxScores = softmax(scoreOutput, gridSize * gridSize)
        
        // Decode bounding boxes
        val (predCx, predCy, predW, predH) = decodeBboxes(bboxOutput)
        
        // Calculate scale and ratio penalties
        val targetScale = sizeCal(targetWidth * resizeScale, targetHeight * resizeScale)
        val scPenalty = FloatArray(gridSize * gridSize)
        val rcPenalty = FloatArray(gridSize * gridSize)
        val ratio = targetWidth / targetHeight
        
        for (i in 0 until gridSize * gridSize) {
            val predScale = sizeCal(predW[i], predH[i])
            val sc = predScale / targetScale
            scPenalty[i] = max(sc, 1f / sc)
            
            val predRatio = predW[i] / predH[i]
            val rc = ratio / predRatio
            rcPenalty[i] = max(rc, 1f / rc)
        }
        
        // Calculate penalty
        val penalty = FloatArray(gridSize * gridSize)
        for (i in 0 until gridSize * gridSize) {
            penalty[i] = exp(-(scPenalty[i] * rcPenalty[i] - 1f) * PENALTY_K)
        }
        
        // Apply penalty and window influence
        val pScore = FloatArray(gridSize * gridSize)
        for (i in 0 until gridSize * gridSize) {
            pScore[i] = penalty[i] * softmaxScores[i] * (1f - WINDOW_INFLUENCE) + 
                        hanningWindow[i] * WINDOW_INFLUENCE
        }
        
        // Find best position
        var maxIdx = 0
        var maxScore = pScore[0]
        for (i in 1 until gridSize * gridSize) {
            if (pScore[i] > maxScore) {
                maxScore = pScore[i]
                maxIdx = i
            }
        }
        
        val bestY = maxIdx / gridSize
        val bestX = maxIdx % gridSize
        
        // Update position with scale learning rate
        val cx = predCx[maxIdx] / resizeScale
        val cy = predCy[maxIdx] / resizeScale
        val w = predW[maxIdx] / resizeScale
        val h = predH[maxIdx] / resizeScale
        
        val lr = penalty[maxIdx] * softmaxScores[maxIdx] * SCALE_LR
        
        centerX = centerX + cx
        centerY = centerY + cy
        targetWidth = w * lr + (1f - lr) * targetWidth
        targetHeight = h * lr + (1f - lr) * targetHeight
        
        // Clamp to image bounds
        centerX = centerX.coerceIn(0f, originWidth.toFloat())
        centerY = centerY.coerceIn(0f, originHeight.toFloat())
        targetWidth = targetWidth.coerceIn(10f, originWidth.toFloat())
        targetHeight = targetHeight.coerceIn(10f, originHeight.toFloat())
        
        score = softmaxScores[maxIdx]
        
        val t5 = System.currentTimeMillis()
        
        if (t5 - t0 > 30) {
            Log.w(TAG, "Track perf: crop=${t1-t0}ms, pre=${t2-t1}ms, backbone=${t3-t2}ms, head=${t4-t3}ms, post=${t5-t4}ms, total=${t5-t0}ms")
        }
        
        val bbox = RectF(
            centerX - targetWidth / 2,
            centerY - targetHeight / 2,
            centerX + targetWidth / 2,
            centerY + targetHeight / 2
        )
        
        return Pair(bbox, score)
    }
    
    /**
     * Manually set the center of the tracker.
     * Useful for resetting the tracker position when tracking is lost to prevent drift.
     */
    fun setCenter(x: Float, y: Float) {
        centerX = x
        centerY = y
    }
    
    fun reset() {
        isInitialized = false
        templateFeatures = null
    }
    
    private fun getSubwindow(img: Bitmap, windowSize: Int): Bitmap {
        // Calculate context and crop size
        val contextSz = (targetWidth + targetHeight) * CONTEXT_AMOUNT
        val sz = sqrt((targetWidth + contextSz) * (targetHeight + contextSz))
        
        resizeScale = TEMPLATE_SIZE.toFloat() / sz
        val cropSz = sz * windowSize / TEMPLATE_SIZE
        
        // Create the final output bitmap directly
        val result = Bitmap.createBitmap(windowSize, windowSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // Fill with average color (optimization: use fixed gray to avoid expensive full-image scan)
        val avgColor = Color.rgb(127, 127, 127)
        canvas.drawColor(avgColor)
        
        val halfCrop = (cropSz + 1f) / 2f
        val x1 = (centerX - halfCrop + 0.5f).toInt()
        val y1 = (centerY - halfCrop + 0.5f).toInt()
        
        // Calculate source rect (intersection with image)
        val srcLeft = max(0, x1)
        val srcTop = max(0, y1)
        val srcRight = min(img.width, x1 + cropSz.toInt())
        val srcBottom = min(img.height, y1 + cropSz.toInt())
        
        // If there is an intersection, draw it
        if (srcLeft < srcRight && srcTop < srcBottom) {
            val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
            
            // Calculate dest rect (where it lands in the windowSize x windowSize image)
            // We need to map src coordinates to dst coordinates based on the scaling factor
            val scale = windowSize.toFloat() / cropSz
            
            val dstLeft = (srcLeft - x1) * scale
            val dstTop = (srcTop - y1) * scale
            val dstRight = (srcRight - x1) * scale
            val dstBottom = (srcBottom - y1) * scale
            
            val dstRect = RectF(dstLeft, dstTop, dstRight, dstBottom)
            
            canvas.drawBitmap(img, srcRect, dstRect, null)
        }
        
        return result
    }
    
    private fun bitmapToFloatArray(bitmap: Bitmap, output: FloatArray): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to CHW format (3 x H x W), normalized to [0, 255]
        // output array is reused, so no allocation here
        val channelSize = height * width
        
        for (i in 0 until height * width) {
            val pixel = pixels[i]
            // BGR format (matching the C++ implementation)
            output[i] = ((pixel shr 16) and 0xFF).toFloat()  // R -> treated as B
            output[channelSize + i] = ((pixel shr 8) and 0xFF).toFloat()  // G
            output[2 * channelSize + i] = (pixel and 0xFF).toFloat()  // B -> treated as R
        }
        
        return output
    }
    
    private fun generateGrid() {
        val step = gridSize / 2
        xGrid = FloatArray(gridSize * gridSize)
        yGrid = FloatArray(gridSize * gridSize)
        
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val idx = y * gridSize + x
                xGrid[idx] = ((x - step) * STRIDE).toFloat()
                yGrid[idx] = ((y - step) * STRIDE).toFloat()
            }
        }
    }
    
    private fun createHanningWindow() {
        hanningWindow = FloatArray(gridSize * gridSize)
        val vertical = FloatArray(gridSize)
        val horizontal = FloatArray(gridSize)
        
        for (i in 0 until gridSize) {
            vertical[i] = (0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * i / (gridSize - 1))).toFloat()
            horizontal[i] = (0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * i / (gridSize - 1))).toFloat()
        }
        
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                hanningWindow[y * gridSize + x] = vertical[y] * horizontal[x]
            }
        }
    }
    
    private fun softmax(input: FloatArray, size: Int): FloatArray {
        // Input is 2 x size, we want the second row after softmax
        val output = FloatArray(size)
        for (i in 0 until size) {
            val v0 = input[i]
            val v1 = input[size + i]
            val maxVal = max(v0, v1)
            val exp0 = exp(v0 - maxVal)
            val exp1 = exp(v1 - maxVal)
            output[i] = exp1 / (exp0 + exp1)
        }
        return output
    }
    
    private fun decodeBboxes(bboxOutput: FloatArray): Array<FloatArray> {
        val size = gridSize * gridSize
        val predCx = FloatArray(size)
        val predCy = FloatArray(size)
        val predW = FloatArray(size)
        val predH = FloatArray(size)
        
        for (i in 0 until size) {
            // bboxOutput is 4 x size: [x1, y1, x2, y2] format
            val x1 = xGrid[i] - bboxOutput[i]
            val y1 = yGrid[i] - bboxOutput[size + i]
            val x2 = xGrid[i] + bboxOutput[2 * size + i]
            val y2 = yGrid[i] + bboxOutput[3 * size + i]
            
            predCx[i] = (x1 + x2) / 2
            predCy[i] = (y1 + y2) / 2
            predW[i] = x2 - x1
            predH[i] = y2 - y1
        }
        
        return arrayOf(predCx, predCy, predW, predH)
    }
    
    private fun sizeCal(w: Float, h: Float): Float {
        val pad = (w + h) / 2
        return sqrt((w + pad) * (h + pad))
    }
    
    private fun clampRect(rect: RectF, width: Int, height: Int): RectF {
        val x = rect.left.coerceIn(0f, (width - 1).toFloat())
        val y = rect.top.coerceIn(0f, (height - 1).toFloat())
        val w = rect.width().coerceIn(1f, width.toFloat() - x)
        val h = rect.height().coerceIn(1f, height.toFloat() - y)
        return RectF(x, y, x + w, y + h)
    }
}

/**
 * Interface for backbone network inference.
 * Implementations can use TensorFlow Lite, ONNX Runtime, etc.
 */
interface BackboneInference {
    fun runBackbone127(input: FloatArray): FloatArray?
    fun runBackbone255(input: FloatArray): FloatArray?
}

/**
 * Interface for head network inference.
 * Returns (scores, bboxes) tuple.
 */
interface HeadInference {
    fun runHead(templateFeatures: FloatArray, searchFeatures: FloatArray): Pair<FloatArray, FloatArray>?
}
