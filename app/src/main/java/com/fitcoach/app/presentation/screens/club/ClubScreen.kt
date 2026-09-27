package com.fitcoach.app.presentation.screens.club

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.presentation.theme.FitCoachColors

/** ЗАГЛУШКА: вкладку «Клуб» реализует агент «Клуб». */
@Composable
fun ClubScreen(onOpenQrPass: () -> Unit, onOpenChat: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FitCoachColors.Background).padding(16.dp)) {
        Text(BrandConfig.clubName, color = FitCoachColors.TextPrimary)
        Text(BrandConfig.clubAddress, color = FitCoachColors.TextMuted)
    }
}

/** Баннер акции клуба для главной; реализует агент «Клуб». */
@Composable
fun PromoBanner(modifier: Modifier = Modifier) = Unit
