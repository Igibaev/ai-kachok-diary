package com.fitcoach.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.fitcoach.app.brand.BrandConfig

/**
 * Палитра приложения. Брендовые цвета (акцент, фон, поверхности) приходят из BrandConfig,
 * семантические (успех/ошибка/предупреждение) — общие для всех брендов.
 */
object FitCoachColors {
    val Background: Color = BrandConfig.background
    val Surface: Color = BrandConfig.surface
    val Card: Color = BrandConfig.card
    val Border: Color = Color.White.copy(alpha = 0.10f).compositeOver(Card)

    val Accent: Color = BrandConfig.accent
    val AccentOn: Color = BrandConfig.accentOn
    val AccentDim: Color = Accent.copy(alpha = 0.7f).compositeOver(Background)
    val AccentSoft: Color = Accent.copy(alpha = 0.15f)

    val PhaseBlue = Color(0xFF4FC3F7)
    val PhaseOrange = Color(0xFFFF9800)
    val PhaseAccent: Color = Accent

    val Success = Color(0xFF00E676)
    val Error = Color(0xFFFF3B3B)
    val Warning = Color(0xFFFFB300)
    val Water = Color(0xFF4FC3F7)

    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFAAAAAA)
    val TextMuted = Color(0xFF777777)
}
