package com.recomo.remotecontrol.camviewer.tracking

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

/**
 * ONNX Runtime based inference for NanoTrack.
 * 
 * Advantages over TensorFlow Lite:
 * - Directly loads ONNX models (no conversion needed)
 * - Better GPU/NNAPI support on Android
 * - Smaller APK size
 * - Same models as desktop/Orin version
 * 
 * Model files required in assets:
 * - nanotrack_backbone_sim.onnx (shared backbone)
 * - nanotrack_head_sim.onnx (correlation head)
 * 
 * Automatically uses GPU (NNAPI) when available, falls back to CPU.
 */
class OnnxNanoTrackInference(private val context: Context) : BackboneInference, HeadInference {
    
    companion object {
        private const val TAG = "OnnxNanoTrack"
        
        // Model file names (same as recomo_camera_tracker)
        private const val BACKBONE_MODEL = "nanotrack_backbone_dy_sim.onnx"
        private const val HEAD_MODEL = "nanotrack_head_sim.onnx"
        
        // Feature dimensions
        private const val FEATURE_CHANNELS = 48
        private const val TEMPLATE_FEATURE_SIZE = 8
        private const val SEARCH_FEATURE_SIZE = 16
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var backboneSessionTemplate: OrtSession? = null // For 127x127
    private var backboneSessionSearch: OrtSession? = null   // For 255x255
    private var headSession: OrtSession? = null
    
    private var accelerationType: AccelerationType = AccelerationType.CPU
    
    enum class AccelerationType {
        NNAPI,  // Neural Networks API (GPU/NPU/DSP)
        CPU
    }
    
    /**
     * Initialize ONNX Runtime sessions with best available acceleration.
     * @return true if initialization succeeded
     */
    fun initialize(): Boolean {
        Log.d(TAG, "Initializing ONNX Runtime inference...")
        
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            
            // Try NNAPI first (uses GPU/NPU/DSP when available)
            val sessionOptions = createSessionOptions()
            
            // Copy models from assets to cache directory
            val backbonePath = copyAssetToCache(BACKBONE_MODEL)
            val headPath = copyAssetToCache(HEAD_MODEL)
            
            if (backbonePath == null || headPath == null) {
                Log.e(TAG, "Failed to copy model files from assets")
                return false
            }
            
            // Create sessions
            // We create two separate sessions for backbone to handle different input sizes (127 vs 255)
            // This helps NNAPI/compilers that might struggle with dynamic shapes in a single session
            backboneSessionTemplate = ortEnv!!.createSession(backbonePath, sessionOptions)
            backboneSessionSearch = ortEnv!!.createSession(backbonePath, sessionOptions)
            headSession = ortEnv!!.createSession(headPath, sessionOptions)
            
            Log.i(TAG, "ONNX Runtime initialized with $accelerationType acceleration")
            logModelInfo()
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
            
            // Fallback to CPU if NNAPI failed
            if (accelerationType != AccelerationType.CPU) {
                Log.w(TAG, "NNAPI failed, falling back to CPU")
                accelerationType = AccelerationType.CPU
                cleanup()
                return initializeCpuOnly()
            }
            
            return false
        }
    }
    
    private fun initializeCpuOnly(): Boolean {
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            
            val backbonePath = copyAssetToCache(BACKBONE_MODEL)
            val headPath = copyAssetToCache(HEAD_MODEL)
            
            if (backbonePath == null || headPath == null) {
                return false
            }
            
            backboneSessionTemplate = ortEnv!!.createSession(backbonePath, options)
            backboneSessionSearch = ortEnv!!.createSession(backbonePath, options)
            headSession = ortEnv!!.createSession(headPath, options)
            
            Log.i(TAG, "Models loaded with CPU-only inference")
            true
        } catch (e: Exception) {
            Log.e(TAG, "CPU initialization also failed", e)
            false
        }
    }
    
    private fun createSessionOptions(): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        
        try {
            // Try to enable NNAPI for hardware acceleration
            // NNAPI can utilize GPU, NPU (Neural Processing Unit), or DSP
            options.addNnapi()
            accelerationType = AccelerationType.NNAPI
            Log.d(TAG, "NNAPI provider added")
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI not available, using CPU: ${e.message}")
            accelerationType = AccelerationType.CPU
        }
        
        // Common optimizations
        // Use 1 thread for small models to avoid overhead
        options.setIntraOpNumThreads(1) 
        options.setInterOpNumThreads(1)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        
        return options
    }
    
    private fun copyAssetToCache(assetName: String): String? {
        return try {
            val cacheFile = File(context.cacheDir, assetName)

            // Check if cached file matches current asset size (detects app updates with new models)
            val assetSize = context.assets.open(assetName).use { it.available().toLong() }
            if (cacheFile.exists() && cacheFile.length() > 0 && cacheFile.length() == assetSize) {
                return cacheFile.absolutePath
            }

            context.assets.open(assetName).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Copied $assetName to cache (${cacheFile.length() / 1024} KB)")
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset $assetName", e)
            null
        }
    }
    
    private fun logModelInfo() {
        backboneSessionSearch?.let { session ->
            Log.d(TAG, "Backbone inputs: ${session.inputNames}")
            try {
                val inputInfo = session.inputInfo
                for ((name, info) in inputInfo) {
                    Log.d(TAG, "Input $name info: $info")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get input info", e)
            }
            Log.d(TAG, "Backbone outputs: ${session.outputNames}")
        }
        headSession?.let { session ->
            Log.d(TAG, "Head inputs: ${session.inputNames}")
            Log.d(TAG, "Head outputs: ${session.outputNames}")
        }
    }
    
    override fun runBackbone127(input: FloatArray): FloatArray? {
        return runBackbone(input, 127, backboneSessionTemplate)
    }
    
    override fun runBackbone255(input: FloatArray): FloatArray? {
        return runBackbone(input, 255, backboneSessionSearch)
    }
    
    private fun runBackbone(input: FloatArray, size: Int, session: OrtSession?): FloatArray? {
        val env = ortEnv ?: return null
        if (session == null) return null
        
        return try {
            // Create input tensor: [1, 3, size, size]
            val shape = longArrayOf(1, 3, size.toLong(), size.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape)
            
            // Run inference
            val inputName = session.inputNames.first()
            val results = session.run(mapOf(inputName to inputTensor))
            
            // Get output
            val outputName = session.outputNames.first()
            val outputTensor = results[outputName].get() as OnnxTensor
            val output = outputTensor.floatBuffer.array()
            
            // Cleanup
            inputTensor.close()
            results.close()
            
            output
        } catch (e: Exception) {
            Log.e(TAG, "Backbone inference failed (size=$size)", e)
            null
        }
    }
    
    override fun runHead(templateFeatures: FloatArray, searchFeatures: FloatArray): Pair<FloatArray, FloatArray>? {
        val session = headSession ?: return null
        val env = ortEnv ?: return null
        
        return try {
            // Create input tensors
            // input1: template features [1, 48, 8, 8]
            // input2: search features [1, 48, 16, 16]
            val templateShape = longArrayOf(1, FEATURE_CHANNELS.toLong(), 
                TEMPLATE_FEATURE_SIZE.toLong(), TEMPLATE_FEATURE_SIZE.toLong())
            val searchShape = longArrayOf(1, FEATURE_CHANNELS.toLong(), 
                SEARCH_FEATURE_SIZE.toLong(), SEARCH_FEATURE_SIZE.toLong())
            
            val templateTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(templateFeatures), templateShape)
            val searchTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(searchFeatures), searchShape)
            
            // Build inputs map (names from the ONNX model)
            val inputNames = session.inputNames.toList()
            val inputs = if (inputNames.size >= 2) {
                mapOf(
                    inputNames[0] to templateTensor,
                    inputNames[1] to searchTensor
                )
            } else {
                Log.e(TAG, "Head model should have 2 inputs, found: $inputNames")
                templateTensor.close()
                searchTensor.close()
                return null
            }
            
            // Run inference
            val results = session.run(inputs)
            
            // Get outputs: scores and bboxes
            val outputNames = session.outputNames.toList()
            if (outputNames.size < 2) {
                Log.e(TAG, "Head model should have 2 outputs, found: $outputNames")
                return null
            }
            
            val scoresTensor = results[outputNames[0]].get() as OnnxTensor
            val bboxTensor = results[outputNames[1]].get() as OnnxTensor
            
            val scores = scoresTensor.floatBuffer.array()
            val bboxes = bboxTensor.floatBuffer.array()
            
            // Cleanup
            templateTensor.close()
            searchTensor.close()
            results.close()
            
            Pair(scores, bboxes)
        } catch (e: Exception) {
            Log.e(TAG, "Head inference failed", e)
            null
        }
    }
    
    fun getAccelerationType(): AccelerationType = accelerationType
    
    fun cleanup() {
        try {
            backboneSessionTemplate?.close()
            backboneSessionSearch?.close()
            headSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
        backboneSessionTemplate = null
        backboneSessionSearch = null
        headSession = null
        ortEnv = null
    }
}
