package com.recomo.user.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recomo.common.chat.voice.AndroidVoiceRecognizer
import com.recomo.common.chat.voice.VoiceEngine
import com.recomo.common.chat.voice.VoiceRecognizer
import com.recomo.common.chat.voice.VoiceRecognizerFactory
import com.recomo.common.chat.voice.VoiceState
import com.recomo.common.chat.voice.WhisperModelRepository

/**
 * Remembers a [VoiceRecognizer] for the given engine, scoped to the
 * current composition. The recognizer is recreated if [engine]
 * changes (e.g. user flips the Settings toggle); the previous one is
 * released in the DisposableEffect cleanup.
 *
 * [whisperRepository] is only consulted for [VoiceEngine.WHISPER];
 * SYSTEM engine ignores it.
 */
@Composable
fun rememberVoiceRecognizer(
    engine: VoiceEngine = VoiceEngine.SYSTEM,
    whisperRepository: WhisperModelRepository? = null,
    modelId: String = WhisperModelRepository.DEFAULT_MODEL_ID
): VoiceRecognizer {
    val context = LocalContext.current.applicationContext
    val resolvedEngine = if (engine == VoiceEngine.WHISPER && whisperRepository == null) {
        VoiceEngine.SYSTEM
    } else {
        engine
    }
    // Re-create recognizer when engine OR model changes (user may switch
    // from Tiny to Base in Settings without switching engine off).
    val recognizer = remember(resolvedEngine, modelId) {
        VoiceRecognizerFactory.create(
            context = context,
            engine = resolvedEngine,
            whisperRepository = whisperRepository ?: WhisperModelRepository(context),
            modelId = modelId
        )
    }
    DisposableEffect(recognizer) {
        onDispose { recognizer.release() }
    }
    return recognizer
}

/**
 * Mic button for the chat input bar.
 *
 * Behaviour:
 *  - No permission yet → tapping triggers the RECORD_AUDIO permission
 *    request. Once granted, immediately starts listening.
 *  - Idle/Final/Error → tap starts a new session.
 *  - Listening → tap stops capture; [VoiceRecognizer.stop] asks for
 *    the final hypothesis, so we don't lose what the user just said.
 *
 * The listening state pulses (1.0 → 1.15 scale) so the user has a
 * visible cue that the mic is hot — there's no on-screen waveform
 * yet, but this keeps the affordance obvious enough for v1.
 */
@Composable
fun ChatVoiceButton(
    state: VoiceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStart()
        } else {
            // User may have tapped "Deny" or "Don't ask again". We only
            // disable further auto-prompts after the second refusal;
            // Android surfaces that via shouldShowRequestPermissionRationale,
            // but a simple one-shot flag is enough for this button.
            permissionDeniedPermanently = true
        }
    }

    val listening = state is VoiceState.Listening
    val pulse by animateFloatAsState(
        targetValue = if (listening) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 400),
        label = "mic-pulse"
    )

    // Crude "request once per listening attempt" — if the user previously
    // denied and taps again, we still try the launcher so Android's own
    // system dialog (or Settings deep-link) handles the edge case.
    LaunchedEffect(permissionDeniedPermanently) {
        if (permissionDeniedPermanently) {
            // No-op — reserved for future "open app settings" hook.
        }
    }

    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = {
                if (listening) {
                    onStop()
                    return@FilledIconButton
                }
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) onStart() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = enabled,
            modifier = Modifier.scale(pulse).background(Color.Transparent, CircleShape),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (listening) Color(0xFFEF5350) else Color(0xFF3F3F45),
                disabledContainerColor = Color(0xFF2A2A2E)
            )
        ) {
            Icon(
                imageVector = if (state is VoiceState.Error) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (listening) "Stop listening" else "Start voice input",
                tint = Color.White
            )
        }
    }
}
