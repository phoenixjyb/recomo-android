package com.recomo.common.chat.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SherpaWhisper"
private const val SAMPLE_RATE = 16_000
private const val MIN_AUDIO_SEC = 3 // pad short captures to this floor

/**
 * On-device Whisper STT via sherpa-onnx.
 *
 * Capture model: offline (batch). Whisper is not streaming — the
 * engine decodes the full PCM buffer after capture stops. UI shows
 * "listening…" with an animated indicator during capture, then swaps
 * to the final transcript ~1–2 s after `stop()` for the tiny model.
 *
 * Engine construction is lazy and happens on the first real `start()`
 * after the model is present on disk. [release] tears everything down.
 */
class SherpaWhisperRecognizer(
    private val context: Context,
    private val repository: WhisperModelRepository,
    private val modelId: String = WhisperModelRepository.DEFAULT_MODEL_ID
) : VoiceRecognizer {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineMutex = Mutex()

    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var captureJob: Job? = null
    @Volatile private var samples: ArrayList<Float>? = null

    override fun isAvailable(): Boolean {
        val permission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return permission && repository.hasModel(modelId)
    }

    override fun start(languageTag: String) {
        if (_state.value is VoiceState.Listening) {
            Log.d(TAG, "start() while already Listening — ignored")
            return
        }
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = VoiceState.Error(
                VoiceErrorCode.PERMISSION_DENIED,
                "RECORD_AUDIO permission not granted"
            )
            return
        }
        if (!repository.hasModel(modelId)) {
            _state.value = VoiceState.Error(
                VoiceErrorCode.UNAVAILABLE,
                "Whisper model not downloaded"
            )
            return
        }

        _state.value = VoiceState.Listening("")
        scope.launch {
            try {
                ensureRecognizer(languageTag)
                startCapture()
            } catch (t: Throwable) {
                Log.w(TAG, "start failed: ${t.message}", t)
                _state.value = VoiceState.Error(VoiceErrorCode.UNKNOWN, t.message ?: "start failed")
            }
        }
    }

    override fun stop() {
        val job = captureJob
        val record = audioRecord
        captureJob = null
        audioRecord = null

        if (job == null || record == null) {
            Log.d(TAG, "stop() with no active session")
            return
        }
        scope.launch {
            try {
                job.cancel()
                runCatching { record.stop() }
                runCatching { record.release() }
                var pcm = samples?.toFloatArray() ?: FloatArray(0)
                samples = null
                val rawDuration = pcm.size.toFloat() / SAMPLE_RATE
                Log.i(TAG, "stop: captured ${pcm.size} samples (${rawDuration}s)")
                if (pcm.isEmpty()) {
                    _state.value = VoiceState.Idle
                    return@launch
                }
                // Whisper/SenseVoice need ~2s minimum audio context to
                // produce meaningful output. Pad short captures with
                // silence so the model's attention has enough frames.
                val minSamples = SAMPLE_RATE * MIN_AUDIO_SEC
                if (pcm.size < minSamples) {
                    Log.i(TAG, "padding ${pcm.size} → $minSamples samples (${MIN_AUDIO_SEC}s floor)")
                    pcm = pcm.copyOf(minSamples)
                }
                val engine = recognizer ?: run {
                    _state.value = VoiceState.Error(
                        VoiceErrorCode.UNAVAILABLE,
                        "Recognizer not initialised"
                    )
                    return@launch
                }
                val text = withContext(Dispatchers.Default) {
                    engineMutex.withLock {
                        val stream = engine.createStream()
                        try {
                            stream.acceptWaveform(pcm, SAMPLE_RATE)
                            engine.decode(stream)
                            engine.getResult(stream).text.trim()
                        } finally {
                            stream.release()
                        }
                    }
                }
                Log.i(TAG, "result: '${text.take(80)}' (${text.length} chars, ${rawDuration}s audio)")
                _state.value = if (text.isBlank()) {
                    VoiceState.Error(VoiceErrorCode.NO_MATCH, "Whisper produced no text")
                } else {
                    VoiceState.Final(text)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "decode failed: ${t.message}", t)
                _state.value = VoiceState.Error(VoiceErrorCode.UNKNOWN, t.message ?: "decode failed")
            }
        }
    }

    override fun cancel() {
        captureJob?.cancel()
        captureJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        samples = null
        _state.value = VoiceState.Idle
    }

    override fun reset() {
        _state.value = VoiceState.Idle
    }

    override fun release() {
        cancel()
        runCatching { recognizer?.release() }
        recognizer = null
    }

    // ── Internals ────────────────────────────────────────────────

    private suspend fun ensureRecognizer(languageTag: String) {
        if (recognizer != null) return
        engineMutex.withLock {
            if (recognizer != null) return@withLock
            val model = WhisperModel.fromModelId(modelId)
            val dir: File = repository.modelDir(modelId)
            val tokens = File(dir, "tokens.txt").absolutePath
            val modelConfig = when (model.engineType) {
                SttEngineType.WHISPER -> OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(dir, "encoder.int8.onnx").absolutePath,
                        decoder = File(dir, "decoder.int8.onnx").absolutePath,
                        language = whisperLanguageTag(languageTag),
                        task = "transcribe",
                        tailPaddings = 1000,
                        enableTokenTimestamps = false,
                        enableSegmentTimestamps = false
                    ),
                    tokens = tokens,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
                SttEngineType.SENSEVOICE -> OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = File(dir, "model.int8.onnx").absolutePath,
                        language = senseVoiceLanguageTag(languageTag),
                        useInverseTextNormalization = true
                    ),
                    tokens = tokens,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig
            )
            recognizer = OfflineRecognizer(assetManager = null, config = config)
            Log.i(TAG, "${model.displayName} recognizer ready (model=$modelId)")
        }
    }

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE * 2) // ~1 s buffer floor
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _state.value = VoiceState.Error(
                VoiceErrorCode.UNAVAILABLE,
                "AudioRecord failed to init"
            )
            return
        }
        record.startRecording()
        audioRecord = record
        samples = ArrayList(SAMPLE_RATE * 10) // pre-size for ~10 s typical utterance

        captureJob = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(minBuf / 2)
            while (isActive) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                val sink = samples ?: break
                for (i in 0 until n) {
                    sink.add(buf[i] / 32768f)
                }
            }
        }
    }

    /**
     * Map BCP-47 tags to Whisper's ISO-639-1-ish codes. Whisper accepts
     * "zh" for Chinese; "en" for English. Unknown tags fall back to
     * empty (auto-detect, slower but safe).
     */
    private fun whisperLanguageTag(bcp47: String): String = when {
        bcp47.startsWith("zh", ignoreCase = true) -> "zh"
        bcp47.startsWith("en", ignoreCase = true) -> "en"
        else -> ""
    }

    /** SenseVoice uses full locale tags: "zh", "en", "ja", "ko", "yue". */
    private fun senseVoiceLanguageTag(bcp47: String): String = when {
        bcp47.startsWith("zh", ignoreCase = true) -> "zh"
        bcp47.startsWith("en", ignoreCase = true) -> "en"
        bcp47.startsWith("ja", ignoreCase = true) -> "ja"
        bcp47.startsWith("ko", ignoreCase = true) -> "ko"
        bcp47.startsWith("yue", ignoreCase = true) -> "yue"
        else -> "auto"
    }
}
