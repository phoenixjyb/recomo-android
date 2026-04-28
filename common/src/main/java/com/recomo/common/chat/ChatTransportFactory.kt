package com.recomo.common.chat

/**
 * Creates the [ChatTransport] the UI should use for a given run-time
 * configuration. Callers (typically an app-layer ViewModel or DI
 * module) read settings, build a [ChatTransportConfig], then ask this
 * factory for the right impl.
 *
 * Exists so [ChatViewModel] doesn't need to know about
 * [DirectRestTransport] vs [ChatRepository] — it only touches the
 * [ChatTransport] interface.
 */
object ChatTransportFactory {

    fun create(config: ChatTransportConfig): ChatTransport = when (config.mode) {
        ChatTransportMode.WS_BRIDGE -> ChatRepository()
        ChatTransportMode.DIRECT_REST -> DirectRestTransport(
            DirectRestConfig(
                baseUrl = config.directBaseUrl.ifBlank { DEFAULT_DIRECT_BASE },
                authToken = config.directAuthToken.ifBlank { null },
                defaultScene = config.defaultScene
            )
        )
    }

    /** Placeholder until wanqiang's public endpoint is confirmed. */
    const val DEFAULT_DIRECT_BASE: String = "http://PLACEHOLDER:9999"
}

enum class ChatTransportMode {
    /** Today's default — WebSocket to the local Termux bridge. */
    WS_BRIDGE,

    /** Direct HTTPS to the cloud REST endpoint. Ships dark until enabled. */
    DIRECT_REST
}

data class ChatTransportConfig(
    val mode: ChatTransportMode = ChatTransportMode.WS_BRIDGE,
    /** Used when mode == DIRECT_REST. Empty string → factory uses the placeholder. */
    val directBaseUrl: String = "",
    /** Used when mode == DIRECT_REST. Empty string → no Authorization header. */
    val directAuthToken: String = "",
    /** Injected into candidates that lack a cloud-provided scene. Null disables. */
    val defaultScene: SceneRef? = null
)

/**
 * Resolves the current [ChatTransportConfig] at ViewModel init time.
 * App-layer Hilt modules supply an impl that reads user settings.
 */
interface ChatTransportConfigProvider {
    fun currentConfig(): ChatTransportConfig
}
