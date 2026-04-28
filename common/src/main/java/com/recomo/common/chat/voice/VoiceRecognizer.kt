package com.recomo.common.chat.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Engine-agnostic on-device speech-to-text recognizer.
 *
 * Implementations today: [AndroidVoiceRecognizer] (platform
 * SpeechRecognizer). A future Whisper-based impl would plug in here
 * with no UI churn.
 *
 * Lifecycle contract:
 *   1. `start(languageTag)` → state transitions Idle → Listening("")
 *   2. Engine emits hypotheses → state updates to Listening(partial)
 *   3. User taps mic again OR engine auto-ends → state becomes
 *      Final(text) or Error(code, message)
 *   4. UI reads Final.text, pushes into chat input, then calls
 *      `reset()` → state returns to Idle
 *
 * Implementations MUST be safe to call from the main thread. They
 * MAY internally hop threads but state flows emit on Main/Immediate
 * so Compose collectors don't need wrapping.
 */
interface VoiceRecognizer {

    val state: StateFlow<VoiceState>

    /** True if the engine is available on this device (permission + system service). */
    fun isAvailable(): Boolean

    /** Begin a new recognition session. No-op if already Listening. */
    fun start(languageTag: String = "zh-CN")

    /**
     * Explicitly stop a Listening session and wait for the engine's
     * final hypothesis. Preferred over [cancel] because it gives the
     * user what they actually said.
     */
    fun stop()

    /** Cancel the current session; discard any partial or final result. */
    fun cancel()

    /** Drop back to [VoiceState.Idle]. Call after consuming a Final. */
    fun reset()

    /** Release engine resources. Call from ViewModel.onCleared(). */
    fun release()
}
