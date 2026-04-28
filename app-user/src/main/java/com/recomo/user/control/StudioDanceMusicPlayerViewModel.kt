package com.recomo.user.control

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.recomo.user.data.studiodance.StudioDanceMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "StudioDanceMusicPlayer"
private const val PROGRESS_POLL_MS = 50L

data class StudioDanceMusicState(
    val isPlaying: Boolean = false,
    val isPrepared: Boolean = false,
    val progress: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val musicFileName: String? = null
) {
    val timeLabel: String
        get() {
            if (durationMs <= 0) return "--"
            val cur = formatMmSs(currentPositionMs)
            val total = formatMmSs(durationMs)
            return "$cur / $total"
        }
}

private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@HiltViewModel
class StudioDanceMusicPlayerViewModel @Inject constructor(
    private val musicRepository: StudioDanceMusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(StudioDanceMusicState())
    val state: StateFlow<StudioDanceMusicState> = _state.asStateFlow()

    /** Prepare a music file for playback. Does NOT start playing. */
    fun prepare(musicFile: File) {
        release()
        try {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(musicFile)))
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
                prepare()
            }
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _state.value = _state.value.copy(
                                isPrepared = true,
                                durationMs = player.duration.coerceAtLeast(0),
                                musicFileName = musicFile.name
                            )
                        }
                        Player.STATE_ENDED -> {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                progress = 1f,
                                currentPositionMs = _state.value.durationMs
                            )
                            stopProgressTracking()
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    _state.value = _state.value.copy(
                        error = error.message ?: "Playback error",
                        isPlaying = false
                    )
                    stopProgressTracking()
                }
            })
            exoPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare music", e)
            _state.value = _state.value.copy(error = "Failed to load: ${e.message}")
        }
    }

    /** Prepare from the music repository by file name. */
    fun prepareByName(fileName: String) {
        if (!musicRepository.isMusicAvailable(fileName)) {
            _state.value = _state.value.copy(
                error = "Music file not downloaded: $fileName",
                musicFileName = fileName
            )
            return
        }
        prepare(musicRepository.musicFile(fileName))
    }

    /**
     * Prepare and auto-play as soon as ready.
     * Use this instead of separate prepareByName() + play() calls —
     * ExoPlayer.prepare() is async, so calling play() immediately after
     * prepare() races and may produce silence.
     */
    fun prepareAndPlay(fileName: String, offsetMs: Long = 0L) {
        Log.d(TAG, "prepareAndPlay: fileName=$fileName offsetMs=$offsetMs")
        if (!musicRepository.isMusicAvailable(fileName)) {
            Log.e(TAG, "Music not available: $fileName")
            _state.value = _state.value.copy(
                error = "Music file not downloaded: $fileName",
                musicFileName = fileName
            )
            return
        }
        val file = musicRepository.musicFile(fileName)
        Log.d(TAG, "Music file: ${file.absolutePath} exists=${file.exists()} size=${file.length()}")
        release()
        try {
            val player = ExoPlayer.Builder(context).build()
            // Listener MUST be added before prepare() to catch STATE_READY
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.d(TAG, "onPlaybackStateChanged: $playbackState (READY=3, ENDED=4)")
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d(TAG, "STATE_READY: duration=${player.duration}ms, playing=${player.isPlaying}")
                            _state.value = _state.value.copy(
                                isPrepared = true,
                                isPlaying = player.isPlaying,
                                durationMs = player.duration.coerceAtLeast(0),
                                musicFileName = file.name
                            )
                            if (player.isPlaying) startProgressTracking()
                        }
                        Player.STATE_ENDED -> {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                progress = 1f,
                                currentPositionMs = _state.value.durationMs
                            )
                            stopProgressTracking()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                    if (isPlaying) startProgressTracking() else stopProgressTracking()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    _state.value = _state.value.copy(
                        error = error.message ?: "Playback error",
                        isPlaying = false
                    )
                    stopProgressTracking()
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.playWhenReady = true
            if (offsetMs > 0) player.seekTo(offsetMs)
            player.prepare()
            exoPlayer = player
            _state.value = _state.value.copy(error = null, musicFileName = file.name)
            Log.d(TAG, "ExoPlayer created, playWhenReady=true, preparing...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare+play music", e)
            _state.value = _state.value.copy(error = "Failed to load: ${e.message}")
        }
    }

    /** Start playback, optionally seeking to an offset first. */
    fun play(offsetMs: Long = 0L) {
        val player = exoPlayer ?: return
        if (offsetMs > 0) player.seekTo(offsetMs)
        player.play()
        _state.value = _state.value.copy(isPlaying = true, error = null)
        startProgressTracking()
    }

    fun pause() {
        exoPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
        stopProgressTracking()
    }

    fun resume() {
        exoPlayer?.play()
        _state.value = _state.value.copy(isPlaying = true)
        startProgressTracking()
    }

    fun stop() {
        exoPlayer?.let {
            it.pause()
            it.seekTo(0)
        }
        _state.value = _state.value.copy(
            isPlaying = false,
            progress = 0f,
            currentPositionMs = 0L
        )
        stopProgressTracking()
    }

    fun release() {
        stopProgressTracking()
        exoPlayer?.release()
        exoPlayer = null
        _state.value = StudioDanceMusicState()
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val player = exoPlayer ?: break
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = player.duration.coerceAtLeast(1)
                _state.value = _state.value.copy(
                    currentPositionMs = pos,
                    progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                )
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
