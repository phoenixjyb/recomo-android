package com.recomo.common.chat.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AndroidVoiceRecognizer"

/**
 * Wraps the platform [SpeechRecognizer] into a [VoiceRecognizer].
 *
 * Prefers the on-device recognizer when the OS supports it
 * (API 31+, `createOnDeviceSpeechRecognizer`) — falls back to the
 * networked recognizer otherwise. Users see no difference.
 *
 * Threading: SpeechRecognizer must be created + driven from the main
 * looper. Construct this on the main thread (ViewModel.init is fine).
 */
class AndroidVoiceRecognizer(
    private val context: Context
) : VoiceRecognizer {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var currentLanguage: String = "zh-CN"

    override fun isAvailable(): Boolean {
        // Base availability: platform service is present.
        val platformAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!platformAvailable) return false
        // Permission is required before we can start(); keep the check
        // here so the UI can surface a Settings-nudge early.
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error(
                VoiceErrorCode.UNAVAILABLE,
                "No SpeechRecognizer service on this device"
            )
            return
        }

        currentLanguage = languageTag
        if (recognizer == null) recognizer = createEngine()
        recognizer?.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _state.value = VoiceState.Listening("")
        try {
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "startListening threw: ${t.message}", t)
            _state.value = VoiceState.Error(VoiceErrorCode.UNKNOWN, t.message ?: "start failed")
        }
    }

    override fun stop() {
        // Asks the engine to stop capturing audio and emit its best
        // final hypothesis. onResults() will fire next.
        try {
            recognizer?.stopListening()
        } catch (t: Throwable) {
            Log.w(TAG, "stopListening threw: ${t.message}")
        }
    }

    override fun cancel() {
        try {
            recognizer?.cancel()
        } catch (t: Throwable) {
            Log.w(TAG, "cancel threw: ${t.message}")
        }
        _state.value = VoiceState.Idle
    }

    override fun reset() {
        _state.value = VoiceState.Idle
    }

    override fun release() {
        try {
            recognizer?.destroy()
        } catch (_: Throwable) { /* best effort */ }
        recognizer = null
        _state.value = VoiceState.Idle
    }

    // ── Internals ────────────────────────────────────────────────

    private fun createEngine(): SpeechRecognizer {
        // On-device engine available since Android 12 (API 31).
        // Falls back to networked engine where the on-device path is
        // unsupported. Both implement the same listener surface.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            Log.i(TAG, "using on-device SpeechRecognizer")
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            Log.i(TAG, "on-device unavailable; using networked SpeechRecognizer")
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // Already in Listening("") from start(); nothing to do.
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val hypothesis = partialResults?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )?.firstOrNull().orEmpty()
            if (_state.value is VoiceState.Listening) {
                _state.value = VoiceState.Listening(hypothesis)
            }
        }

        override fun onResults(results: Bundle?) {
            val finalText = results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )?.firstOrNull().orEmpty()
            _state.value = if (finalText.isBlank()) {
                VoiceState.Error(VoiceErrorCode.NO_MATCH, "No speech recognised")
            } else {
                VoiceState.Final(finalText)
            }
        }

        override fun onError(error: Int) {
            val code = when (error) {
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceErrorCode.NETWORK
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceErrorCode.NO_MATCH
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceErrorCode.TIMEOUT
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceErrorCode.BUSY
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceErrorCode.PERMISSION_DENIED
                else -> VoiceErrorCode.UNKNOWN
            }
            // Swallow NO_MATCH quietly — user will likely retry. Surface others.
            if (code == VoiceErrorCode.NO_MATCH) {
                _state.value = VoiceState.Idle
            } else {
                _state.value = VoiceState.Error(code, "SpeechRecognizer error $error")
            }
        }
    }
}
