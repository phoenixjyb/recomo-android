package com.recomo.user.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object StudioChrome {
    val background = Color(0xFF080808)
    val backgroundElevated = Color(0xFF060912)
    val backgroundDeep = Color(0xFF0D1014)
    val panel = Color(0xCC0C0C0C)
    val panelSoft = Color(0x0FFFFFFF)
    val panelMuted = Color(0x110F141A)
    val topBar = Color(0xAA030405)
    val panelBorder = Color(0x14FFFFFF)
    val panelBorderStrong = Color(0x1DFFFFFF)
    val panelBorderActive = Color(0x2600A3FF)
    val textPrimary = Color(0xFFE9EDF2)
    val textSecondary = Color(0x99E3E7EC)
    val textTertiary = Color(0x668C98A7)
    val textMuted = Color(0x558C98A7)
    val textStrong = Color(0xFFE7EFF6)
    val brandBlue = Color(0xFF0084E8)
    val accentBlue = Color(0xFF00A3FF)
    val accentPurple = Color(0xFF7B5EFF)
    val success = Color(0xFF1BC47D)
    val warning = Color(0xFFF59E0B)
    val danger = Color(0xFFEF4444)
    val dangerSoft = Color(0xFFFF6B6B)
    val surfaceScrim = Color(0xA0101319)
    val screenBackgroundBrush = Brush.linearGradient(
        colors = listOf(background, backgroundElevated, background)
    )
    val screenBackgroundVerticalBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF050507), background, backgroundDeep)
    )
    val brandGradient = Brush.linearGradient(
        colors = listOf(accentBlue, accentPurple)
    )

    val radiusXs = 8.dp
    val radiusSm = 10.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 20.dp
    val radiusSheet = 24.dp
}
