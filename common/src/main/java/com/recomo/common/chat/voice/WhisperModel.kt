package com.recomo.common.chat.voice

/**
 * Available offline STT models hosted on our GitLab (project 50).
 *
 * Each model knows its download ID, file layout, and which sherpa-onnx
 * recognizer config to use. The Settings UI shows [displayName] +
 * [sizeLabel]; [SherpaWhisperRecognizer] consults [engineType] to pick
 * the right config builder.
 */
enum class WhisperModel(
    val modelId: String,
    val displayName: String,
    val sizeLabel: String,
    val engineType: SttEngineType
) {
    /** Fast, lightweight, OK Chinese. Good for quick prompts. */
    TINY(
        modelId = "whisper-tiny-int8-v1",
        displayName = "Whisper Tiny",
        sizeLabel = "58 MB",
        engineType = SttEngineType.WHISPER
    ),

    /** Better Chinese, moderate speed. Recommended default. */
    BASE(
        modelId = "whisper-base-int8-v1",
        displayName = "Whisper Base",
        sizeLabel = "90 MB",
        engineType = SttEngineType.WHISPER
    ),

    /** Best Chinese quality. Purpose-built for zh/en/ja/ko/yue. */
    SENSEVOICE(
        modelId = "sensevoice-zh-int8-v1",
        displayName = "SenseVoice 中文",
        sizeLabel = "153 MB",
        engineType = SttEngineType.SENSEVOICE
    );

    companion object {
        fun fromModelId(id: String?): WhisperModel =
            entries.firstOrNull { it.modelId == id } ?: TINY
    }
}

/**
 * Which sherpa-onnx config path to use. Whisper models use
 * [OfflineWhisperModelConfig] (encoder+decoder); SenseVoice uses
 * [OfflineSenseVoiceModelConfig] (single model file).
 */
enum class SttEngineType {
    WHISPER,
    SENSEVOICE
}
