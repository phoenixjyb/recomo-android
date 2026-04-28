package com.recomo.common.chat.voice

/**
 * UI-facing state of the voice-input pipeline.
 *
 * Kept minimal on purpose — concrete engines (SpeechRecognizer today,
 * Whisper-on-device later) should squash implementation-specific
 * events down to this shape so the UI layer doesn't churn when we
 * swap engines.
 */
sealed class VoiceState {
    /** Not listening. Default. */
    data object Idle : VoiceState()

    /** Mic is hot. `partial` accumulates as the engine emits hypotheses. */
    data class Listening(val partial: String = "") : VoiceState()

    /**
     * Engine produced a final transcript. UI writes `text` into the
     * chat input field; the user still owns the Send button.
     */
    data class Final(val text: String) : VoiceState()

    /**
     * Something went wrong. `code` is an engine-agnostic classifier so
     * higher layers can pick the right user-facing message.
     */
    data class Error(val code: VoiceErrorCode, val message: String) : VoiceState()
}

enum class VoiceErrorCode {
    PERMISSION_DENIED,
    NETWORK,
    NO_MATCH,
    TIMEOUT,
    BUSY,
    UNAVAILABLE,
    UNKNOWN
}
