package com.recomo.user.ui.screens.smartfollow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recomo.user.R
import com.recomo.user.ui.theme.StudioChrome

/**
 * Context-dependent control buttons for the Smart Follow screen.
 * Button visibility and enabled state depend on [SmartFollowState].
 */
@Composable
fun SmartFollowControls(
    state: SmartFollowState,
    onStartFollow: () -> Unit,
    onStopFollow: () -> Unit,
    onPauseFollow: () -> Unit,
    onResumeFollow: () -> Unit,
    onReselectTarget: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Re-select: available when tracking, lost, or arrived
        if (state is SmartFollowState.Tracking ||
            state is SmartFollowState.Lost ||
            state is SmartFollowState.LostWhileFollowing ||
            state is SmartFollowState.Arrived ||
            state is SmartFollowState.Paused
        ) {
            OutlinedButton(
                onClick = onReselectTarget,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = StudioChrome.textPrimary
                )
            ) {
                Text(stringResource(R.string.smart_follow_btn_reselect))
            }
        }

        Spacer(Modifier.weight(1f))

        // Start Follow: only when tracker locked on
        if (state is SmartFollowState.Tracking) {
            Button(
                onClick = onStartFollow,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.accentBlue
                )
            ) {
                Text(stringResource(R.string.smart_follow_btn_start))
            }
        }

        // Pause / Resume
        if (state is SmartFollowState.Following) {
            OutlinedButton(
                onClick = onPauseFollow,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = StudioChrome.warning
                )
            ) {
                Text(stringResource(R.string.smart_follow_btn_pause))
            }
        }
        if (state is SmartFollowState.Paused) {
            Button(
                onClick = onResumeFollow,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.accentBlue
                )
            ) {
                Text(stringResource(R.string.smart_follow_btn_resume))
            }
        }

        // Stop: when following or paused
        if (state is SmartFollowState.Following || state is SmartFollowState.Paused) {
            Button(
                onClick = onStopFollow,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioChrome.danger
                )
            ) {
                Text(stringResource(R.string.smart_follow_btn_stop))
            }
        }

        // Exit: always available
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = StudioChrome.textMuted
            )
        ) {
            Text(stringResource(R.string.smart_follow_btn_exit))
        }
    }
}
