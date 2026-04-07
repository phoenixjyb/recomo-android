package com.recomo.remotecontrol.v3dr.ui.scenes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.remotecontrol.v3dr.data.model.ModelDownloadProgress
import com.recomo.remotecontrol.v3dr.data.model.ReconstructionStatus
import com.recomo.remotecontrol.v3dr.data.model.SceneReconstruction
import com.recomo.remotecontrol.v3dr.data.repository.SceneRepository
import com.recomo.remotecontrol.v3dr.data.repository.V3DRServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for Scene List
 */
sealed class SceneListUiState {
    object Loading : SceneListUiState()
    data class Success(val scenes: List<SceneReconstruction>) : SceneListUiState()
    data class Error(val message: String, val canStartServer: Boolean = false) : SceneListUiState()
}

/**
 * ViewModel for Scene List Screen
 */
@HiltViewModel
class SceneListViewModel @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val serviceRepository: V3DRServiceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SceneListViewModel"
        private const val AUTO_REFRESH_INTERVAL_MS = 10000L // 10 seconds
    }

    // Scenes list from repository
    val scenes: StateFlow<List<SceneReconstruction>> = sceneRepository.scenes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Loading state
    val isLoading: StateFlow<Boolean> = sceneRepository.isLoading
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Error state
    val error: StateFlow<String?> = sceneRepository.error
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Download progress
    val downloadProgress: StateFlow<Map<String, ModelDownloadProgress>> = 
        sceneRepository.downloadProgress
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    // Server control state
    private val _serverControlAvailable = MutableStateFlow(false)
    val serverControlAvailable: StateFlow<Boolean> = _serverControlAvailable.asStateFlow()

    private val _isStartingServer = MutableStateFlow(false)
    val isStartingServer: StateFlow<Boolean> = _isStartingServer.asStateFlow()

    private val _serverMessage = MutableStateFlow<String?>(null)
    val serverMessage: StateFlow<String?> = _serverMessage.asStateFlow()

    // UI State combining scenes and loading
    val uiState: StateFlow<SceneListUiState> = combine(
        scenes,
        isLoading,
        error,
        _serverControlAvailable
    ) { scenesList, loading, errorMsg, canControl ->
        when {
            loading && scenesList.isEmpty() -> SceneListUiState.Loading
            errorMsg != null && scenesList.isEmpty() -> SceneListUiState.Error(errorMsg, canStartServer = canControl)
            else -> SceneListUiState.Success(scenesList)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SceneListUiState.Loading
    )

    // Selected filter
    private val _selectedFilter = MutableStateFlow<ReconstructionStatus?>(null)
    val selectedFilter: StateFlow<ReconstructionStatus?> = _selectedFilter.asStateFlow()

    // Auto-refresh enabled
    private val _autoRefreshEnabled = MutableStateFlow(false)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    init {
        // Load scenes on init
        refresh()
        // Check if server control is available
        checkServerControlAvailable()
    }

    /**
     * Check if service control API is available
     */
    private fun checkServerControlAvailable() {
        viewModelScope.launch {
            _serverControlAvailable.value = serviceRepository.isServiceControlAvailable()
            Timber.d("Server control available: ${_serverControlAvailable.value}")
        }
    }

    /**
     * Start the V3DR Lake server remotely
     */
    fun startServer(pin: String = "") {
        viewModelScope.launch {
            _isStartingServer.value = true
            _serverMessage.value = "Starting server..."
            
            serviceRepository.startServer(pin)
                .onSuccess { response ->
                    _serverMessage.value = response.message
                    Timber.d("Server started: ${response.message}")
                    // Wait a moment and then retry fetching scenes
                    delay(2000)
                    refresh()
                }
                .onFailure { exception ->
                    _serverMessage.value = "Failed to start server: ${exception.message}"
                    Timber.e(exception, "Failed to start server")
                }
            
            _isStartingServer.value = false
        }
    }

    /**
     * Clear server message
     */
    fun clearServerMessage() {
        _serverMessage.value = null
    }

    /**
     * Refresh scene list from server
     */
    fun refresh() {
        viewModelScope.launch {
            Timber.d("Refreshing scene list")
            sceneRepository.fetchScenes(
                statusFilter = _selectedFilter.value
            ).onFailure { exception ->
                Timber.e(exception, "Failed to refresh scenes")
            }
        }
    }

    /**
     * Apply status filter
     */
    fun filterByStatus(status: ReconstructionStatus?) {
        _selectedFilter.value = status
        refresh()
    }

    /**
     * Download model for a scene
     */
    fun downloadAsset(sceneId: String, asset: String = "glb") {
        viewModelScope.launch {
            Timber.d("Downloading asset for scene: $sceneId, asset: $asset")
            sceneRepository.downloadAsset(sceneId, asset)
                .onSuccess { file ->
                    Timber.d("Model downloaded successfully: ${file.absolutePath}")
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to download model")
                }
        }
    }

    /**
     * Download thumbnail for a scene
     */
    fun downloadThumbnail(sceneId: String) {
        viewModelScope.launch {
            Timber.d("Downloading thumbnail for scene: $sceneId")
            sceneRepository.downloadThumbnail(sceneId)
                .onSuccess { file ->
                    Timber.d("Thumbnail downloaded: ${file.absolutePath}")
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to download thumbnail")
                }
        }
    }

    /**
     * Delete a scene
     */
    fun deleteScene(sceneId: String) {
        viewModelScope.launch {
            Timber.d("Deleting scene: $sceneId")
            sceneRepository.deleteScene(sceneId)
                .onSuccess {
                    Timber.d("Scene deleted successfully")
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to delete scene")
                }
        }
    }

    /**
     * Check reconstruction status for processing scenes
     */
    fun checkStatus(sceneId: String) {
        viewModelScope.launch {
            sceneRepository.checkReconstructionStatus(sceneId)
                .onSuccess { status ->
                    Timber.d("Scene $sceneId status: $status")
                    // If status changed, refresh list
                    if (status == ReconstructionStatus.COMPLETED || status == ReconstructionStatus.FAILED) {
                        refresh()
                    }
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to check status")
                }
        }
    }

    /**
     * Toggle auto-refresh for processing scenes
     */
    fun toggleAutoRefresh() {
        _autoRefreshEnabled.value = !_autoRefreshEnabled.value
        
        if (_autoRefreshEnabled.value) {
            startAutoRefresh()
        }
    }

    /**
     * Start auto-refresh loop
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (_autoRefreshEnabled.value) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                
                // Check if there are any processing scenes
                val processingScenes = scenes.value.filter { 
                    it.status == ReconstructionStatus.PROCESSING || 
                    it.status == ReconstructionStatus.PENDING 
                }
                
                if (processingScenes.isNotEmpty()) {
                    Timber.d("Auto-refreshing ${processingScenes.size} processing scenes")
                    processingScenes.forEach { scene ->
                        checkStatus(scene.sceneId)
                    }
                }
            }
        }
    }

    /**
     * Get cached model file path
     */
    fun getCachedModelPath(sceneId: String): String? {
        return sceneRepository.getCachedModel(sceneId)?.absolutePath
    }

    /**
     * Check if model is cached locally
     */
    fun isModelCached(sceneId: String): Boolean {
        return sceneRepository.getCachedModel(sceneId) != null
    }

    /**
     * Clear error state
     */
    fun clearError() {
        sceneRepository.clearError()
    }

    /**
     * Clear all cached models
     */
    fun clearCache() {
        viewModelScope.launch {
            sceneRepository.clearCache()
                .onSuccess {
                    Timber.d("Cache cleared successfully")
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to clear cache")
                }
        }
    }
}
