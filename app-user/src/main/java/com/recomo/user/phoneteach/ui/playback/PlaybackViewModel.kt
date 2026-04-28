package com.recomo.user.phoneteach.ui.playback

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.recomo.common.capture.model.ImuDataSet
import com.recomo.common.capture.model.ImuSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Playback view model for a single Phone Teach capture session.
 *
 * Deliberately lean compared to v3dr's PlaybackViewModel: no VIO integration, no offline
 * VIO runs, no dependency checks — just video playback + IMU overlay from the session dir.
 * Phone Teach hands pose inference to the cloud, so this screen is pure review.
 *
 * Session is set via [loadSession] rather than SavedStateHandle because the nav host hoists
 * the selected session dir in Compose state and passes it down explicitly.
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

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

    private val _currentSessionDir = MutableStateFlow<File?>(null)
    val currentSessionDir: StateFlow<File?> = _currentSessionDir.asStateFlow()

    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "PhoneTeachPlayback"
    }

    /**
     * Switch playback to the given session directory. Releases any previous player.
     * Safe to call multiple times — a no-op if already pointed at [sessionDir].
     */
    fun loadSession(sessionDir: File) {
        if (_currentSessionDir.value == sessionDir && _exoPlayer != null) return

        releasePlayer()
        _currentSessionDir.value = sessionDir

        val videoFile = File(sessionDir, "video.mp4")
        if (!videoFile.exists()) {
            Log.e(TAG, "video.mp4 not found in ${sessionDir.absolutePath}")
            _playbackState.value = PlaybackState.Error("video.mp4 not found")
            return
        }

        try {
            _exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_IDLE -> _playbackState.value = PlaybackState.Idle
                            Player.STATE_BUFFERING -> _playbackState.value = PlaybackState.Buffering
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

            startPositionUpdates()
            loadImuData(sessionDir)

            Log.d(TAG, "Player initialized for session ${sessionDir.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player", e)
            _playbackState.value = PlaybackState.Error(e.message ?: "Unknown error")
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (_exoPlayer != null) {
                _exoPlayer?.let { player ->
                    val position = player.currentPosition
                    _currentPosition.value = position
                    _imuData.value?.let { dataset ->
                        _currentImuSample.value = dataset.getSampleAt(position)
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun loadImuData(sessionDir: File) {
        viewModelScope.launch {
            try {
                val imuFile = File(sessionDir, "imu.csv")
                if (!imuFile.exists()) {
                    Log.d(TAG, "No imu.csv in ${sessionDir.absolutePath}")
                    _imuData.value = null
                    return@launch
                }
                val dataset = withContext(Dispatchers.IO) {
                    ImuDataSet.fromCsvFile(imuFile.readText())
                }
                if (dataset != null) {
                    _imuData.value = dataset
                    Log.i(TAG, "Loaded ${dataset.samples.size} IMU samples @ ${dataset.sampleRate.toInt()}Hz")
                } else {
                    Log.w(TAG, "Failed to parse imu.csv")
                    _imuData.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading IMU data", e)
                _imuData.value = null
            }
        }
    }

    fun playPause() {
        _exoPlayer?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
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

    private fun releasePlayer() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        _exoPlayer?.release()
        _exoPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackState.value = PlaybackState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        Log.d(TAG, "ViewModel cleared")
    }

    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Buffering : PlaybackState()
        object Ready : PlaybackState()
        object Ended : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }
}
