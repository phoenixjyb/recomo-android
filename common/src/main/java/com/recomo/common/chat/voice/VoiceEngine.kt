package com.recomo.common.chat.voice

/**
 * Selects which on-device STT backend the chat screen uses.
 *
 * Swappable at app startup (transport-style): ChatViewModel resolves
 * via an injected provider reading user settings. Existing UI reads
 * `VoiceState` alone and doesn't care which engine produced it.
 */
enum class VoiceEngine {
    /** Platform `SpeechRecognizer` (on-device on API 31+; OEM service elsewhere). Zero APK growth. */
    SYSTEM,

    /** sherpa-onnx Whisper. Fully offline. Requires one-time ~75 MB model download. */
    WHISPER
}
