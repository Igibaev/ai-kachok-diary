package com.fitcoach.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = FitCoachColors.Accent,
    onPrimary = FitCoachColors.Background,
    primaryContainer = FitCoachColors.AccentDim,
    onPrimaryContainer = FitCoachColors.TextPrimary,
    secondary = FitCoachColors.PhaseBlue,
    onSecondary = FitCoachColors.Background,
    background = FitCoachColors.Background,
    onBackground = FitCoachColors.TextPrimary,
    surface = FitCoachColors.Surface,
    onSurface = FitCoachColors.TextPrimary,
    surfaceVariant = FitCoachColors.Card,
    onSurfaceVariant = FitCoachColors.TextSecondary,
    error = FitCoachColors.Error,
    onError = FitCoachColors.TextPrimary,
    outline = FitCoachColors.Border
)

@Composable
fun FitCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = FitCoachTypography,
        content = content
    )
}
