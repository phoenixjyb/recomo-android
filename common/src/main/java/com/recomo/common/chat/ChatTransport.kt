package com.recomo.common.chat

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport-layer abstraction for the AI chat pipeline.
 *
 * The ViewModel / Repository layer talks to `ChatTransport`; concrete
 * impls decide how v2 events are sourced:
 *
 *  - `ChatRepository` (today's impl) — WebSocket to the Termux bridge,
 *    which forwards to wanqiang's REST over an SSH tunnel. Used while
 *    Termux pipeline is the primary channel.
 *  - `DirectRestTransport` (upcoming) — plain HTTPS to the public cloud
 *    endpoint, synthesises v2 events from one-shot REST responses.
 *    Retires the Termux bridge in two rollout phases.
 *  - A hypothetical future `DirectWsTransport` — drop-in for when cloud
 *    speaks our v2 WS protocol natively (Option C). Same interface.
 *
 * UI / ChatViewModel should never depend on the concrete impl.
 */
interface ChatTransport {

    /** Coarse connection state exposed to the UI. */
    val connectionState: StateFlow<ChatConnectionState>

    /** Conversation id, populated by the backend on `connect` handshake. */
    val conversationId: StateFlow<String?>

    /** Stream of v2 server events (chunks, done, candidate_set, etc.). */
    val incomingEvents: SharedFlow<ServerEvent>

    /** Capabilities the backend advertised on the handshake. */
    val serverCapabilities: ChatCapabilities

    fun connect(
        url: String,
        deviceId: String,
        existingConversationId: String? = null,
        robotProfile: String? = null,
        locationId: String? = null
    )

    fun disconnect()

    suspend fun sendMessage(
        content: String,
        context: ChatContext? = null,
        attachments: UserAttachments? = null
    )

    suspend fun cancelGeneration()

    suspend fun selectCandidate(messageId: String, candidateId: String)

    suspend fun refinePrompt(parentMessageId: String, refinement: String)
}
