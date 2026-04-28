package com.recomo.common.chat.voice

import android.content.Context

/**
 * Picks the concrete [VoiceRecognizer] for a given [VoiceEngine].
 *
 * The Whisper path needs a [WhisperModelRepository]; the System path
 * does not. Higher layers construct one factory, pass it the engine
 * choice, consume the returned recognizer, and release it when the
 * composition leaves.
 */
object VoiceRecognizerFactory {

    fun create(
        context: Context,
        engine: VoiceEngine,
        whisperRepository: WhisperModelRepository,
        modelId: String = WhisperModelRepository.DEFAULT_MODEL_ID
    ): VoiceRecognizer = when (engine) {
        VoiceEngine.SYSTEM -> AndroidVoiceRecognizer(context)
        VoiceEngine.WHISPER -> SherpaWhisperRecognizer(context, whisperRepository, modelId)
    }
}
