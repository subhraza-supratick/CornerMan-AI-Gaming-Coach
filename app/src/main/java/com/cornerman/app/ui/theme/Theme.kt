package com.cornerman.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — matches the Cornerman emblem
val VioletLight = Color(0xFFD8B4FE)
val VioletCore = Color(0xFF8B5CF6)
val VioletDeep = Color(0xFF4C1D95)
val VioletMuted = Color(0xFF2E1065)
val BgNearBlack = Color(0xFF020617)
val BgCard = Color(0xFF0F172A)
val BgCardBorder = Color(0xFF1E293B)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val DangerRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)
val GoldAccent = Color(0xFFF59E0B)

private val CornermanColorScheme = darkColorScheme(
    primary = VioletCore,
    onPrimary = Color.White,
    secondary = VioletLight,
    background = BgNearBlack,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    error = DangerRed,
    outline = BgCardBorder
)

@Composable
fun CornermanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CornermanColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
