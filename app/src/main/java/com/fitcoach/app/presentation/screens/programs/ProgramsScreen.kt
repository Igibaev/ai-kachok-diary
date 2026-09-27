package com.fitcoach.app.presentation.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitcoach.app.presentation.theme.FitCoachColors

/** ЗАГЛУШКА: экран выбора программы реализует агент «Программы/онбординг». */
@Composable
fun ProgramsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(FitCoachColors.Background).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text("Программы", color = FitCoachColors.TextPrimary)
    }
}
