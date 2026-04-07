package com.recomo.remotecontrol.v3dr.ui.screens.sceneviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.data.model.ModelDownloadProgress
import com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus
import com.recomo.remotecontrol.v3dr.data.model.SceneReconstruction
import com.recomo.remotecontrol.v3dr.data.repository.SceneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for 3D Scene Viewer
 * Manages scene loading, model downloading, and 3D viewer state
 */
@HiltViewModel
class SceneViewerViewModel @Inject constructor(
    application: Application,
    private val sceneRepository: SceneRepository
) : AndroidViewModel(application) {

    private val _sceneReconstruction = MutableStateFlow<SceneReconstruction?>(null)
    val sceneReconstruction: StateFlow<SceneReconstruction?> = _sceneReconstruction.asStateFlow()

    private val _downloadProgress = MutableStateFlow(ModelDownloadProgress(""))
    val downloadProgress: StateFlow<ModelDownloadProgress> = _downloadProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentSessionId: String? = null
    private var currentSceneId: String? = null

    /**
     * Load 3D scene by sceneId
     */
    fun loadScene(sceneId: String) {
        currentSceneId = sceneId
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                sceneRepository.getSceneDetails(sceneId)
                    .onSuccess { scene ->
                        _sceneReconstruction.value = scene
                        
                        // Check if model is cached locally
                        val cachedPath = sceneRepository.getCachedModel(sceneId)?.absolutePath
                        if (cachedPath != null) {
                            _sceneReconstruction.value = scene.copy(localCachePath = cachedPath)
                        }
                    }
                    .onFailure { exception ->
                        Timber.e(exception, "Failed to load scene")
                        _errorMessage.value = "Failed to load scene: ${exception.message}"
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load 3D scene reconstruction for a recording session (for backward compatibility)
     */
    fun loadSceneForSession(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                sceneRepository.getSceneBySession(sessionId)
                    .onSuccess { scene ->
                        _sceneReconstruction.value = scene
                        currentSceneId = scene.sceneId
                        
                        // Check if model is cached locally
                        val cachedPath = sceneRepository.getCachedModel(scene.sceneId)?.absolutePath
                        if (cachedPath != null) {
                            _sceneReconstruction.value = scene.copy(localCachePath = cachedPath)
                        }
                    }
                    .onFailure { exception ->
                        Timber.e(exception, "Failed to load scene for session $sessionId")
                        _errorMessage.value = "Failed to load scene: ${exception.message}"
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Download 3D model file from server
     */
    fun downloadModel() {
        val scene = _sceneReconstruction.value ?: return
        if (scene.status != ReconstructionStatus.COMPLETED) return

        viewModelScope.launch {
            _errorMessage.value = null
            _downloadProgress.value = ModelDownloadProgress(
                sceneId = scene.sceneId,
                isDownloading = true
            )

            try {
                // Use SceneRepository to download the model
                sceneRepository.downloadModel(scene.sceneId)
                    .onSuccess { file: File ->
                        Timber.d("Model downloaded successfully: ${file.absolutePath}")
                        // Update scene with local path
                        _sceneReconstruction.value = scene.copy(localCachePath = file.absolutePath)
                        _downloadProgress.value = _downloadProgress.value.copy(
                            isDownloading = false,
                            isCompleted = true
                        )
                    }
                    .onFailure { exception: Throwable ->
                        Timber.e(exception, "Failed to download model")
                        _errorMessage.value = "Download failed: ${exception.message}"
                        _downloadProgress.value = _downloadProgress.value.copy(
                            isDownloading = false,
                            errorMessage = exception.message
                        )
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Download failed: ${e.message}"
                _downloadProgress.value = _downloadProgress.value.copy(
                    isDownloading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Reset camera to default position
     */
    fun resetCamera() {
        // TODO: Implement camera reset in 3D viewer
        viewModelScope.launch {
            // Send reset command to Filament viewer
        }
    }

    /**
     * Retry loading scene after error
     */
    fun retry() {
        currentSessionId?.let { sessionId ->
            loadSceneForSession(sessionId)
        }
    }

    /**
     * Create mock scene data for testing
     * TODO: Remove when repository is implemented
     */
    private fun createMockScene(sessionId: String): SceneReconstruction {
        // For testing: always return completed status with test model
        // Use the bundled test cube model from assets
        val testModelPath = "test_models/test_cube.glb"
        
        // Simulate different reconstruction states (but mostly completed for demo)
        val status = when (sessionId.hashCode() % 5) {
            0 -> ReconstructionStatus.PENDING
            1 -> ReconstructionStatus.PROCESSING
            4 -> ReconstructionStatus.FAILED
            else -> ReconstructionStatus.COMPLETED // 2 and 3 -> COMPLETED (60% chance)
        }

        return SceneReconstruction(
            sceneId = "scene_${sessionId.takeLast(8)}",
            sessionId = sessionId,
            status = status,
            pointCloudUrl = if (status == ReconstructionStatus.COMPLETED)
                "https://example.com/scenes/${sessionId}/point_cloud.glb" else null,
            meshUrl = if (status == ReconstructionStatus.COMPLETED)
                "https://example.com/scenes/${sessionId}/mesh.glb" else null,
            glbUrl = if (status == ReconstructionStatus.COMPLETED)
                "https://example.com/scenes/${sessionId}/scene.glb" else null,
            splatUrl = null,
            previewUrl = null,
            createdAtMillis = System.currentTimeMillis() - 3600000,
            completedAtMillis = if (status == ReconstructionStatus.COMPLETED)
                System.currentTimeMillis() - 1800000 else null,
            progressPercent = if (status == ReconstructionStatus.COMPLETED) 100 else 50,
            errorMessage = if (status == ReconstructionStatus.FAILED)
                "Reconstruction failed: Insufficient feature matches" else null,
            localCachePath = if (status == ReconstructionStatus.COMPLETED)
                testModelPath else null // Use test model from assets
        )
    }
}
