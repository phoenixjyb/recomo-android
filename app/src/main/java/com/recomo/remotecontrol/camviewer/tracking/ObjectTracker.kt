package com.recomo.remotecontrol.camviewer.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Main object tracker that manages the NanoTrack algorithm and inference.
 * Handles tracking lifecycle, state management, and provides thread-safe API.
 * 
 * Usage:
 * 1. Create instance with Context
 * 2. Call initialize() to load models
 * 3. Call setTargetRoi() to start tracking a region
 * 4. Call processFrame() with each new frame
 * 5. Read trackingStatus for current state
 * 6. Call shutdown() when done
 */
class ObjectTracker(context: Context) {
    
    companion object {
        private const val TAG = "ObjectTracker"
        
        // Default tracking parameters
        private const val DEFAULT_SCORE_THRESHOLD = 0.65f // Threshold to KEEP tracking
        private const val DEFAULT_RECOVERY_THRESHOLD = 0.70f // Reduced from 0.75 to improve re-selection speed
        private const val DEFAULT_MAX_LOST_FRAMES = 3
        private const val DEFAULT_SMOOTHING_FACTOR = 0.60f // Increased from 0.15 to reduce lag
    }
    
    // Tracking parameters (can be adjusted)
    var scoreThreshold: Float = DEFAULT_SCORE_THRESHOLD
    var recoveryThreshold: Float = DEFAULT_RECOVERY_THRESHOLD
    var maxLostFrames: Int = DEFAULT_MAX_LOST_FRAMES
    var smoothingFactor: Float = DEFAULT_SMOOTHING_FACTOR
    
    // Inference backend - ONNX Runtime for efficient on-device inference
    private val inference = OnnxNanoTrackInference(context)
    private var algorithm: NanoTrackAlgorithm? = null
    private val sender = TrackingSender()
    
    // State management
    private val mutex = Mutex()
    private var isInitialized = false
    private var trackingActive = false
    private var trackIdCounter = 1
    private var activeTrackId = 0
    private var lostFrames = 0
    
    // Last tracking result
    private var lastBbox: RectF = RectF()
    private var lastConfidence: Float = 0f
    private var lastState: TrackingState = TrackingState.PENDING
    
    // Pending ROI for initialization
    private var pendingRoi: TargetRoi? = null
    private var hasPendingRoi = false
    private var pendingIsNew = false
    private var lastRequestedRoi: TargetRoi? = null
    
    /**
     * Initialize the tracker by loading models.
     * Must be called before processFrame().
     * @return true if initialization succeeded
     */
    suspend fun initialize(): Boolean = mutex.withLock {
        if (isInitialized) return true
        
        Log.d(TAG, "Initializing ObjectTracker...")
        
        val success = withContext(Dispatchers.IO) {
            inference.initialize()
        }
        
        if (success) {
            algorithm = NanoTrackAlgorithm(inference, inference)
            isInitialized = true
            sender.connect()
            Log.i(TAG, "ObjectTracker initialized with ${inference.getAccelerationType()} acceleration")
        } else {
            Log.e(TAG, "Failed to initialize ObjectTracker")
        }
        
        return success
    }
    
    /**
     * Set a target ROI to start or reinitialize tracking.
     * The tracker will initialize on the next processFrame() call.
     * 
     * @param roi Target region of interest
     * @param forceNew If true, always create a new track ID
     */
    suspend fun setTargetRoi(roi: TargetRoi, forceNew: Boolean = false) = mutex.withLock {
        if (roi.isEmpty()) {
            Log.w(TAG, "Ignoring empty ROI")
            return@withLock
        }
        
        // Check if this is the same as the last requested ROI
        val sameAsLast = lastRequestedRoi?.let { last ->
            last.xOffset == roi.xOffset && last.yOffset == roi.yOffset &&
            last.width == roi.width && last.height == roi.height
        } ?: false
        
        // Ignore identical ROIs while tracking is healthy
        if (trackingActive && sameAsLast && !forceNew) {
            return@withLock
        }
        
        pendingRoi = roi
        hasPendingRoi = true
        pendingIsNew = !sameAsLast || activeTrackId == 0 || forceNew
        lastRequestedRoi = roi
        
        Log.i(TAG, "Target ROI set: x=${roi.xOffset}, y=${roi.yOffset}, " +
                "w=${roi.width}, h=${roi.height}, new=$pendingIsNew")
    }
    
    /**
     * Stop the current tracking session.
     */
    suspend fun stopTracking() = mutex.withLock {
        trackingActive = false
        lastState = TrackingState.PENDING
        lastBbox = RectF() // Clear bbox so UI hides it
        lastConfidence = 0f
        algorithm?.reset()
        Log.d(TAG, "Tracking stopped")
    }
    
    /**
     * Process a frame and update tracking state.
     * 
     * @param frame The input frame as a Bitmap (ARGB_8888)
     * @return Current TrackingStatus
     */
    suspend fun processFrame(frame: Bitmap): TrackingStatus = mutex.withLock {
        if (!isInitialized) {
            Log.w(TAG, "Tracker not initialized")
            return@withLock createStatus()
        }
        
        val algo = algorithm
        if (algo == null) {
            return@withLock createStatus()
        }
        
        // Handle pending ROI initialization
        if (hasPendingRoi) {
            val roi = pendingRoi
            if (roi != null) {
                val success = withContext(Dispatchers.Default) {
                    algo.initialize(frame, roi.toRect())
                }
                
                if (success) {
                    trackingActive = true
                    lostFrames = 0
                    lastBbox = roi.toRect()
                    lastConfidence = 1f
                    lastState = TrackingState.TRACKING
                    
                    if (pendingIsNew || activeTrackId == 0) {
                        activeTrackId = trackIdCounter++
                    }
                    
                    Log.i(TAG, "Tracker initialized with track_id=$activeTrackId")
                } else {
                    Log.w(TAG, "Failed to initialize tracker")
                    // If initialization failed for a new request, stop tracking old object
                    if (pendingIsNew) {
                        trackingActive = false
                        lastState = TrackingState.LOST
                        lastBbox = RectF()
                    }
                }
            }
            hasPendingRoi = false
            pendingIsNew = false
        }
        
        // If not actively tracking, return pending status
        if (!trackingActive) {
            val status = createStatus()
            sender.send(status)
            return@withLock status
        }
        
        // Run tracking
        val result = withContext(Dispatchers.Default) {
            algo.track(frame)
        }
        
        // Determine threshold based on current state (Hysteresis)
        // If we were tracking, use lower threshold. If we were lost, use higher threshold.
        val currentThreshold = if (lastState == TrackingState.TRACKING) scoreThreshold else recoveryThreshold
        
        if (result == null || result.second < currentThreshold) {
            // Tracking lost
            lostFrames++
            if (lostFrames > maxLostFrames) {
                trackingActive = false
                lastState = TrackingState.LOST
                lastBbox = RectF() // Clear bbox to indicate lost
                Log.d(TAG, "Tracking lost after $lostFrames frames (score=${result?.second})")
            } else {
                lastState = TrackingState.LOST
                // Prevent drift: Reset algorithm center to last known good position
                if (trackingActive) {
                    algo.setCenter(lastBbox.centerX(), lastBbox.centerY())
                }
            }
            lastConfidence = result?.second ?: 0f
            val status = createStatus()
            sender.send(status)
            return@withLock status
        }
        
        // Successful tracking
        lostFrames = 0
        val (bbox, score) = result
        
        // Apply smoothing
        val smoothedBbox = smoothRect(bbox)
        
        lastBbox = smoothedBbox
        lastConfidence = score
        lastState = TrackingState.TRACKING
        
        val status = createStatus()
        sender.send(status)
        return@withLock status
    }
    
    /**
     * Get current tracking status without processing a new frame.
     */
    fun getCurrentStatus(): TrackingStatus {
        return createStatus()
    }
    
    /**
     * Check if the tracker is ready to process frames.
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Get the acceleration type being used.
     */
    fun getAccelerationType(): OnnxNanoTrackInference.AccelerationType {
        return inference.getAccelerationType()
    }
    
    /**
     * Shutdown and release resources.
     * Acquires mutex to wait for any in-flight processFrame() to complete.
     */
    suspend fun shutdown() = mutex.withLock {
        inference.cleanup()
        algorithm = null
        isInitialized = false
        sender.close()
        Log.d(TAG, "ObjectTracker shutdown")
    }
    
    private fun createStatus(): TrackingStatus {
        return TrackingStatus(
            trackingId = activeTrackId.toString(),
            state = lastState,
            bbox = TrackingBoundingBox(
                xOffset = lastBbox.left,
                yOffset = lastBbox.top,
                width = lastBbox.width(),
                height = lastBbox.height()
            ),
            confidence = lastConfidence
        )
    }
    
    private fun smoothRect(newRect: RectF): RectF {
        if (smoothingFactor <= 0f || lastBbox.isEmpty) {
            return newRect
        }
        
        val alpha = smoothingFactor
        return RectF(
            alpha * newRect.left + (1f - alpha) * lastBbox.left,
            alpha * newRect.top + (1f - alpha) * lastBbox.top,
            alpha * newRect.right + (1f - alpha) * lastBbox.right,
            alpha * newRect.bottom + (1f - alpha) * lastBbox.bottom
        )
    }
}
