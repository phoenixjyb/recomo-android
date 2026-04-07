package com.recomo.remotecontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.recomo.remotecontrol.v3dr.ui.navigation.V3DRNavHost
import com.recomo.remotecontrol.v3dr.ui.theme.V3DRTheme

@Composable
fun PhoneMocoOverlay(modifier: Modifier = Modifier) {
    V3DRTheme {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.background
        ) {
            V3DRNavHost()
        }
    }
}
