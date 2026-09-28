package com.fitcoach.app.presentation.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.domain.program.ActivityType
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun NextWorkoutCard(
    state: DashboardUiState,
    onStart: () -> Unit,
    onLogActivity: () -> Unit,
    onOpenPrograms: () -> Unit
) {
    val next = state.next
    FitCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(if (state.inProgress != null) "Тренировка в процессе" else "Следующая тренировка", Modifier.weight(1f))
            TextButton(onClick = onOpenPrograms, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text(state.program?.title ?: "Программа", fontSize = 12.sp, color = FitCoachColors.Accent)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (next != null) {
            Text(next.template.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.Accent)
            Text(
                "${next.template.exercises.size} упражнений · ~${next.template.estimatedMinutes} мин · Фаза ${next.phase.index}, неделя ${next.weekNumber}",
                fontSize = 13.sp, color = FitCoachColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                next.template.exercises.take(4).joinToString(" · ") { it.name } + if (next.template.exercises.size > 4) " …" else "",
                fontSize = 12.sp, color = FitCoachColors.TextMuted, maxLines = 2
            )
        }
        if (state.completedToday && state.inProgress == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "✅ Сегодня уже была активность. Можно отдохнуть — восстановление тоже часть плана. Но если есть силы — начинай.",
                fontSize = 12.sp, color = FitCoachColors.Success
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.inProgress != null) "Продолжить" else "Начать", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLogActivity,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextPrimary),
            border = BorderStroke(1.dp, FitCoachColors.Border),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Отметить активность") }
    }
}

@Composable
fun LogActivityDialog(onDismiss: () -> Unit, onConfirm: (ActivityType, Int) -> Unit) {
    var type by remember { mutableStateOf(ActivityType.GROUP) }
    var minutes by remember { mutableStateOf("45") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FitCoachColors.Card,
        title = { Text("Отметить активность", color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Групповое занятие, кардио или другая тренировка вне программы.", fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                ActivityType.entries.forEach { t ->
                    SelectableChip(text = t.title, leading = t.emoji, selected = type == t, onClick = { type = t }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Минут", color = FitCoachColors.TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                        focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(type, minutes.toIntOrNull() ?: 45) },
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn)
            ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = FitCoachColors.TextMuted) } }
    )
}
