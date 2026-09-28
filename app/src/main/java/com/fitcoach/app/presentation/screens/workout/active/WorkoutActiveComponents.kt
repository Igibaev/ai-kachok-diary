package com.fitcoach.app.presentation.screens.workout.active

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun WorkoutProgressHeader(
    title: String,
    doneSets: Int,
    totalSets: Int,
    elapsed: String,
    onOpenChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FitCoachColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FitCoachColors.TextPrimary, maxLines = 1)
                Text("$doneSets из $totalSets подходов", fontSize = 12.sp, color = FitCoachColors.TextMuted)
            }
            Text(elapsed, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.Accent)
            IconButton(onClick = onOpenChat) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "AI-тренер", tint = FitCoachColors.TextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (totalSets == 0) 0f else doneSets.toFloat() / totalSets },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = FitCoachColors.Accent,
            trackColor = FitCoachColors.Border
        )
    }
}

@Composable
fun ExerciseCard(
    exercise: ExerciseUiState,
    onSetClick: (ExerciseSet) -> Unit,
    onUndoSet: (ExerciseSet) -> Unit,
    onReplace: (String) -> Unit,
    onOpenYoutube: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val done = exercise.sets.count { it.isDone }
    val allDone = done == exercise.sets.size

    FitCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (exercise.muscleGroup.isNotEmpty()) {
                    Text(exercise.muscleGroup.uppercase(), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                }
                Text(
                    exercise.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = if (allDone) FitCoachColors.Success else FitCoachColors.TextPrimary
                )
                if (exercise.tip.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(exercise.tip, fontSize = 12.sp, color = FitCoachColors.TextMuted)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = FitCoachColors.TextMuted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Техника (YouTube)") },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, null, tint = FitCoachColors.Error) },
                        onClick = { menuOpen = false; onOpenYoutube() }
                    )
                    if (exercise.alternatives.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Заменить упражнение", fontSize = 11.sp, color = FitCoachColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        exercise.alternatives.forEach { alt ->
                            DropdownMenuItem(
                                text = { Text(alt) },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null, tint = FitCoachColors.Accent) },
                                onClick = { menuOpen = false; onReplace(alt) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("$done/${exercise.sets.size} подходов", fontSize = 12.sp, color = FitCoachColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        exercise.sets.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { set ->
                    SetChip(set = set, onClick = { onSetClick(set) }, onUndo = { onUndoSet(set) }, modifier = Modifier.weight(1f))
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetChip(set: ExerciseSet, onClick: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (set.isDone) FitCoachColors.Success.copy(alpha = 0.15f) else FitCoachColors.Surface
    val border = if (set.isDone) FitCoachColors.Success else FitCoachColors.Border
    val text = if (set.isDone) FitCoachColors.Success else FitCoachColors.TextPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { if (!set.isDone) onClick() else onUndo() },
                onLongClick = { if (set.isDone) onUndo() }
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (set.isDone) "✓ Сет ${set.setNumber}" else "Сет ${set.setNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = text)
            val sub = if (set.isDone) {
                buildString {
                    append(set.actualReps ?: set.targetReps)
                    set.actualWeight?.let { append(" × ").append(WorkoutTitles.formatWeight(it)).append(" кг") }
                }
            } else "${set.targetReps} × ${set.targetWeight}"
            Text(sub, fontSize = 12.sp, color = if (set.isDone) FitCoachColors.Success.copy(alpha = 0.8f) else FitCoachColors.TextSecondary)
            if (set.isDone) Text("отменить", fontSize = 10.sp, color = FitCoachColors.TextMuted)
        }
    }
}

@Composable
fun SetInputDialog(input: SetInput, onDismiss: () -> Unit, onConfirm: (reps: Int, weight: Float?) -> Unit) {
    var reps by remember(input) { mutableStateOf(input.defaultReps.toString()) }
    var weight by remember(input) { mutableStateOf(input.defaultWeight?.let { WorkoutTitles.formatWeight(it) } ?: "") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
        focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border,
        cursorColor = FitCoachColors.Accent
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FitCoachColors.Card,
        title = {
            Column {
                Text("Сет ${input.set.setNumber}", color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(input.set.exerciseName, fontSize = 13.sp, color = FitCoachColors.TextSecondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val last = input.lastResult
                Text(
                    text = if (last?.actualWeight != null) {
                        "Прошлый раз: ${WorkoutTitles.formatWeight(last.actualWeight)} кг × ${last.actualReps ?: last.targetReps}"
                    } else "План: ${input.set.targetReps} × ${input.set.targetWeight}",
                    fontSize = 13.sp, color = FitCoachColors.Accent
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reps, onValueChange = { reps = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Повторы", color = FitCoachColors.TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, colors = fieldColors, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("Вес, кг", color = FitCoachColors.TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, colors = fieldColors, modifier = Modifier.weight(1f)
                    )
                }
                Text("Вес можно не указывать (собственный вес, время).", fontSize = 11.sp, color = FitCoachColors.TextMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reps.toIntOrNull() ?: input.defaultReps, weight.toFloatOrNull()) },
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn)
            ) { Text("Готово", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = FitCoachColors.TextMuted) } }
    )
}
