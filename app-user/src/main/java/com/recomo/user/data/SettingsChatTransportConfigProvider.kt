package com.recomo.user.data

import com.recomo.common.chat.AnchorPoseDto
import com.recomo.common.chat.ChatTransportConfig
import com.recomo.common.chat.ChatTransportConfigProvider
import com.recomo.common.chat.ChatTransportMode
import com.recomo.common.chat.SceneRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the latest direct-transport preferences from DataStore and
 * produces a [ChatTransportConfig] for [com.recomo.common.chat.ChatViewModel].
 *
 * Synchronous: uses `runBlocking { flow.first() }` because the
 * ViewModel graph initialises transports at construction time. The
 * settings are tiny and already in memory after first read, so the
 * blocking call is negligible. Revisit if ViewModel init becomes
 * perf-sensitive.
 */
@Singleton
class SettingsChatTransportConfigProvider @Inject constructor(
    private val settings: UserSettingsRepository
) : ChatTransportConfigProvider {

    override fun currentConfig(): ChatTransportConfig = runBlocking {
        val directEnabled = settings.chatDirectEnabled.first()
        if (!directEnabled) {
            ChatTransportConfig(mode = ChatTransportMode.WS_BRIDGE)
        } else {
            ChatTransportConfig(
                mode = ChatTransportMode.DIRECT_REST,
                directBaseUrl = settings.chatDirectBaseUrl.first(),
                directAuthToken = settings.chatDirectAuthToken.first(),
                defaultScene = DEFAULT_SCENE
            )
        }
    }

    companion object {
        /**
         * T8-lobby SPZ hosted on our GitLab (project 50 Generic Packages).
         * Injected into candidates that lack a cloud-provided `scene` so
         * SceneViewer has a 3D background. The anchor pose matches the
         * bridge's `default_anchor.tum`.
         */
        private val DEFAULT_SCENE = SceneRef(
            sceneId = "T8-lobby",
            spzUrl = "http://115.190.112.4/api/v4/projects/50/packages/generic/scenes/v1/T8-lobby.spz",
            anchorPose = AnchorPoseDto(
                x = 0.0, y = 0.0, z = 0.0,
                qx = -0.999976, qy = 0.000011, qz = 0.001667, qw = 0.006667
            )
        )
    }
}
