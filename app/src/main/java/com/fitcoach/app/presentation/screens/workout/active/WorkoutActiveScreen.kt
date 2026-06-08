package com.fitcoach.app.presentation.screens.workout.active

import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.domain.model.CooldownItem
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.WarmupItem
import com.fitcoach.app.presentation.components.*
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun WorkoutActiveScreen(
    workoutId: String,
    onFinished: () -> Unit,
    viewModel: WorkoutActiveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val view = LocalView.current

    // Keep screen on
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
    ) {
        when (val phase = state.phase) {
            is WorkoutPhase.Warmup -> WarmupScreen(
                items = state.warmupItems,
                onStart = { viewModel.startExercises() }
            )

            is WorkoutPhase.ExerciseList -> ExerciseListScreen(
                exercises = state.exercises,
                totalSets = state.totalSets,
                doneSets = state.doneSets,
                onSetDone = { set -> viewModel.markSetDone(set) },
                onFinishEarly = { viewModel.finishCooldown() }
            )

            is WorkoutPhase.RestTimer -> {
                LaunchedEffect(phase.remainingSeconds) {
                    if (phase.remainingSeconds == 0) {
                        val ctx = view.context
                        ctx.getSystemService<Vibrator>()?.vibrate(
                            VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    }
                }
                RestTimerDialog(
                    remainingSeconds = phase.remainingSeconds,
                    totalSeconds = phase.totalSeconds,
                    nextExercise = phase.nextExerciseName,
                    nextSet = phase.nextSetNumber,
                    onSkip = { viewModel.skipRest() }
                )
            }

            is WorkoutPhase.Cooldown -> CooldownScreen(
                items = state.cooldownItems,
                onFinish = { viewModel.finishCooldown() }
            )

            is WorkoutPhase.BackPainCheck -> BackPainDialog(
                onSubmit = { level -> viewModel.submitBackPain(level) }
            )

            is WorkoutPhase.Summary -> SummaryScreen(
                doneSets = state.doneSets,
                totalSets = state.totalSets,
                onFinish = onFinished
            )
        }
    }
}

@Composable
private fun WarmupScreen(items: List<WarmupItem>, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("РАЗМИНКА", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
        Text("Подготовь тело", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            FitCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.name, color = FitCoachColors.TextPrimary)
                    Text("${item.durationSeconds / 60}:${"%02d".format(item.durationSeconds % 60)}", color = FitCoachColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Начать тренировку", color = FitCoachColors.Background, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ExerciseListScreen(
    exercises: List<ExerciseUiState>,
    totalSets: Int,
    doneSets: Int,
    onSetDone: (ExerciseSet) -> Unit,
    onFinishEarly: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Progress header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FitCoachColors.Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ПРОГРЕСС", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                    Text("$doneSets / $totalSets сетов", style = MaterialTheme.typography.labelMedium, color = FitCoachColors.Accent)
                }
                Spacer(Modifier.height(8.dp))
                LabeledProgressBar(
                    value = doneSets.toFloat(),
                    max = totalSets.toFloat(),
                    color = FitCoachColors.Accent,
                    label = "",
                    sub = ""
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            exercises.forEach { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onSetDone = onSetDone,
                    onOpenYoutube = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(exercise.youtubeQuery)}"))
                        context.startActivity(intent)
                    }
                )
            }

            if (doneSets > 0) {
                OutlinedButton(
                    onClick = onFinishEarly,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextSecondary),
                    border = BorderStroke(1.dp, FitCoachColors.Border)
                ) {
                    Text("Завершить тренировку")
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseUiState,
    onSetDone: (ExerciseSet) -> Unit,
    onOpenYoutube: () -> Unit
) {
    FitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontWeight = FontWeight.SemiBold,
                    color = FitCoachColors.TextPrimary,
                    fontSize = 15.sp
                )
                if (exercise.tip.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(exercise.tip, fontSize = 12.sp, color = FitCoachColors.TextMuted)
                }
            }
            if (exercise.youtubeQuery.isNotEmpty()) {
                TextButton(onClick = onOpenYoutube) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = FitCoachColors.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Техника", color = FitCoachColors.Error, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val doneSetsCount = exercise.sets.count { it.isDone }
        Text(
            text = "$doneSetsCount/${exercise.sets.size} подходов",
            fontSize = 12.sp,
            color = FitCoachColors.TextMuted
        )
        Spacer(Modifier.height(8.dp))

        // Sets grid
        val columns = 2
        exercise.sets.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { set ->
                    SetButton(
                        setNumber = set.setNumber,
                        targetWeight = set.targetWeight,
                        targetReps = set.targetReps,
                        isDone = set.isDone,
                        onClick = { onSetDone(set) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RestTimerDialog(
    remainingSeconds: Int,
    totalSeconds: Int,
    nextExercise: String,
    nextSet: Int,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("ОТДЫХ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)

            CircularProgress(
                value = remainingSeconds.toFloat(),
                max = totalSeconds.toFloat(),
                color = FitCoachColors.Accent,
                label = "сек",
                size = 200.dp,
                strokeWidth = 16.dp
            )

            Text(
                text = "${remainingSeconds / 60}:${"%02d".format(remainingSeconds % 60)}",
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FitCoachColors.TextPrimary
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("СЛЕДУЮЩЕЕ:", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Text(nextExercise, fontSize = 16.sp, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Medium)
                Text("Сет $nextSet", fontSize = 14.sp, color = FitCoachColors.Accent)
            }

            OutlinedButton(
                onClick = onSkip,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextSecondary),
                border = BorderStroke(1.dp, FitCoachColors.Border)
            ) {
                Text("Пропустить")
            }
        }
    }
}

@Composable
private fun CooldownScreen(items: List<CooldownItem>, onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ЗАМИНКА", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
        Text("Восстановление", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)

        items.forEach { item ->
            FitCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.name, color = FitCoachColors.TextPrimary)
                    Text("${item.durationSeconds}с", color = FitCoachColors.PhaseBlue, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Success),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Завершить тренировку", color = FitCoachColors.Background, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun BackPainDialog(onSubmit: (Int) -> Unit) {
    var painLevel by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Оцени боль в спине", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("после тренировки", fontSize = 16.sp, color = FitCoachColors.TextMuted)
        Spacer(Modifier.height(32.dp))

        Text("$painLevel / 10", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
            color = when {
                painLevel <= 3 -> FitCoachColors.Success
                painLevel <= 6 -> FitCoachColors.Warning
                else -> FitCoachColors.Error
            })

        Spacer(Modifier.height(16.dp))

        Slider(
            value = painLevel.toFloat(),
            onValueChange = { painLevel = it.toInt() },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = FitCoachColors.Accent,
                activeTrackColor = FitCoachColors.Accent
            )
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Нет боли", fontSize = 12.sp, color = FitCoachColors.TextMuted)
            Text("Сильная боль", fontSize = 12.sp, color = FitCoachColors.TextMuted)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { onSubmit(painLevel) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Сохранить", color = FitCoachColors.Background, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SummaryScreen(doneSets: Int, totalSets: Int, onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Тренировка завершена!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("$doneSets из $totalSets подходов выполнено", fontSize = 16.sp, color = FitCoachColors.TextMuted)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("На главную", color = FitCoachColors.Background, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
