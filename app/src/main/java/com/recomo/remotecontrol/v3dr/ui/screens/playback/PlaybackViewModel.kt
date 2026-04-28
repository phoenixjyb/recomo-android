package com.recomo.remotecontrol.v3dr.ui.screens.playback

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.recomo.remotecontrol.v3dr.data.SettingsRepository
import com.recomo.remotecontrol.v3dr.data.VioBackendType
import com.recomo.common.capture.model.ImuDataSet
import com.recomo.common.capture.model.ImuSample
import com.recomo.remotecontrol.v3dr.vio.VioResult
import com.recomo.remotecontrol.v3dr.vio.VioRunner
import com.recomo.remotecontrol.v3dr.vio.VioSessionLoader
import com.recomo.remotecontrol.v3dr.vio.VioStatus
import com.recomo.remotecontrol.v3dr.vio.VioDepsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/**
 * ViewModel for video playback screen
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
    }

    private val recordingId: String = URLDecoder.decode(
        savedStateHandle["recordingId"] ?: "",
        StandardCharsets.UTF_8.toString()
    )

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer?
        get() = _exoPlayer

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _imuData = MutableStateFlow<ImuDataSet?>(null)
    val imuData: StateFlow<ImuDataSet?> = _imuData.asStateFlow()

    private val _currentImuSample = MutableStateFlow<ImuSample?>(null)
    val currentImuSample: StateFlow<ImuSample?> = _currentImuSample.asStateFlow()

    private val _showMetadata = MutableStateFlow(false)
    val showMetadata: StateFlow<Boolean> = _showMetadata.asStateFlow()

    private val _vioState = MutableStateFlow<VioState>(VioState.Idle)
    val vioState: StateFlow<VioState> = _vioState.asStateFlow()

    private val _depsState = MutableStateFlow<VioDepsState>(VioDepsState.Idle)
    val depsState: StateFlow<VioDepsState> = _depsState.asStateFlow()

    private val vioRunner = VioRunner()
    private val sessionLoader = VioSessionLoader()

    init {
        initializePlayer()
        loadImuData()
    }

    private fun initializePlayer() {
        viewModelScope.launch {
            try {
                val videoFile = File(recordingId)
                if (!videoFile.exists()) {
                    Log.e(TAG, "Video file does not exist: $recordingId")
                    _playbackState.value = PlaybackState.Error("Video file not found")
                    return@launch
                }

                _exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(videoFile))
                    setMediaItem(mediaItem)
                    prepare()
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_IDLE -> {
                                    _playbackState.value = PlaybackState.Idle
                                }
                                Player.STATE_BUFFERING -> {
                                    _playbackState.value = PlaybackState.Buffering
                                }
                                Player.STATE_READY -> {
                                    _playbackState.value = PlaybackState.Ready
                                    _duration.value = duration
                                }
                                Player.STATE_ENDED -> {
                                    _playbackState.value = PlaybackState.Ended
                                    _isPlaying.value = false
                                }
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                        }
                    })
                }

                // Start position updates
                startPositionUpdates()

                _playbackState.value = PlaybackState.Ready
                Log.d(TAG, "Player initialized for: ${videoFile.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing player", e)
                _playbackState.value = PlaybackState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (_exoPlayer != null) {
                _exoPlayer?.let { player ->
                    val position = player.currentPosition
                    _currentPosition.value = position
                    
                    // Update current IMU sample based on video position
                    _imuData.value?.let { imuDataSet ->
                        _currentImuSample.value = imuDataSet.getSampleAt(position)
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun loadImuData() {
        viewModelScope.launch {
            try {
                val videoFile = File(recordingId)
                val sessionDir = videoFile.parentFile ?: return@launch
                val imuFile = File(sessionDir, "imu.csv")
                
                Log.d(TAG, "Looking for IMU data: ${imuFile.absolutePath}")
                
                if (!imuFile.exists()) {
                    Log.d(TAG, "No IMU data file found: ${imuFile.absolutePath}")
                    return@launch
                }
                
                // Parse IMU data in background
                val imuDataSet = withContext(Dispatchers.IO) {
                    val csvContent = imuFile.readText()
                    ImuDataSet.fromCsvFile(csvContent)
                }
                
                if (imuDataSet != null) {
                    _imuData.value = imuDataSet
                    Log.i(TAG, "Loaded IMU data: ${imuDataSet.samples.size} samples, " +
                            "${imuDataSet.sampleRate.toInt()} Hz, ${imuDataSet.durationMs}ms")
                } else {
                    Log.w(TAG, "Failed to parse IMU data")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading IMU data", e)
            }
        }
    }

    fun toggleMetadataView() {
        _showMetadata.value = !_showMetadata.value
    }

    fun runOfflineVio() {
        if (_vioState.value is VioState.Running) return
        _vioState.value = VioState.Running
        viewModelScope.launch {
            // Get settings for backend selection
            val settings = settingsRepository.settingsFlow.first()
            vioRunner.setBackend(settings.vioBackend, settings.cloudSfmUrl)
            
            val sessionResult = sessionLoader.loadFromRecordingPath(recordingId)
            if (sessionResult.isFailure) {
                _vioState.value = VioState.Error(sessionResult.exceptionOrNull()?.message ?: "Session load failed")
                return@launch
            }
            val session = sessionResult.getOrNull() ?: run {
                _vioState.value = VioState.Error("Session load failed")
                return@launch
            }
            val result = vioRunner.runOffline(session)
            _vioState.value = VioState.Completed(result)
        }
    }

    /**
     * Run VIO explicitly on cloud, regardless of settings
     */
    fun runCloudVio() {
        if (_vioState.value is VioState.Running) return
        _vioState.value = VioState.Running
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            
            val sessionResult = sessionLoader.loadFromRecordingPath(recordingId)
            if (sessionResult.isFailure) {
                _vioState.value = VioState.Error(sessionResult.exceptionOrNull()?.message ?: "Session load failed")
                return@launch
            }
            val session = sessionResult.getOrNull() ?: run {
                _vioState.value = VioState.Error("Session load failed")
                return@launch
            }
            val result = vioRunner.runOnCloud(session, settings.cloudSfmUrl)
            _vioState.value = VioState.Completed(result)
        }
    }

    fun checkVioDependencies() {
        if (_depsState.value is VioDepsState.Checking) return
        _depsState.value = VioDepsState.Checking
        viewModelScope.launch {
            try {
                val result = vioRunner.checkDependencies()
                _depsState.value = VioDepsState.Completed(result)
            } catch (e: Exception) {
                _depsState.value = VioDepsState.Error(e.message ?: "Deps check failed")
            }
        }
    }

    fun resetDepsStatus() {
        _depsState.value = VioDepsState.Idle
    }

    fun resetVioStatus() {
        _vioState.value = VioState.Idle
    }

    fun playPause() {
        _exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        _exoPlayer?.seekTo(positionMs)
    }

    fun seekForward() {
        _exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + 10_000).coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }

    fun seekBackward() {
        _exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - 10_000).coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
        Log.d(TAG, "Player released")
    }

    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Buffering : PlaybackState()
        object Ready : PlaybackState()
        object Ended : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }

    sealed class VioState {
        object Idle : VioState()
        object Running : VioState()
        data class Completed(val result: VioResult) : VioState()
        data class Error(val message: String) : VioState()

        val status: VioStatus?
            get() = if (this is Completed) result.status else null
    }

    sealed class VioDepsState {
        object Idle : VioDepsState()
        object Checking : VioDepsState()
        data class Completed(val result: VioDepsResult) : VioDepsState()
        data class Error(val message: String) : VioDepsState()
    }
}
