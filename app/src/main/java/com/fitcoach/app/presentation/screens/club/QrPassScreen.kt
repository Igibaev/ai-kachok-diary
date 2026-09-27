package com.fitcoach.app.presentation.screens.club

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitcoach.app.presentation.theme.FitCoachColors

/** ЗАГЛУШКА: «Карта участника» (QR + код) реализует агент «Клуб». */
@Composable
fun QrPassScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FitCoachColors.Background).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text("Карта участника", color = FitCoachColors.TextPrimary)
    }
}
