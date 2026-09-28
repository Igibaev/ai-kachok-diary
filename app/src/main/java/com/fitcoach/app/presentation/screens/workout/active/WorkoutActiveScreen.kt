package com.fitcoach.app.presentation.screens.workout.active

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun WorkoutActiveScreen(
    workoutId: String,
    onFinished: () -> Unit,
    onOpenChat: () -> Unit = {},
    viewModel: WorkoutActiveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier = Modifier.fillMaxSize().background(FitCoachColors.Background)) {
        when (state.phase) {
            WorkoutPhase.Warmup -> RoutineScreen(
                label = stringResource(R.string.workout_warmup_label),
                title = stringResource(R.string.workout_warmup_title),
                subtitle = state.title.tr().ifBlank { stringResource(R.string.workout_default_title) },
                items = state.warmupItems.map { it.name.tr() to it.durationSeconds },
                buttonText = stringResource(R.string.workout_warmup_start), buttonColor = FitCoachColors.Accent,
                onNext = viewModel::startExercises, onSkip = viewModel::startExercises
            )

            WorkoutPhase.Exercises -> ExercisesContent(
                state = state,
                onSetClick = viewModel::openSetInput,
                onUndoSet = viewModel::undoSet,
                onReplace = viewModel::replaceExercise,
                onOpenYoutube = { query ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                    runCatching { context.startActivity(intent) }
                },
                onFinishEarly = viewModel::finishExercisesEarly,
                onOpenChat = onOpenChat
            )

            WorkoutPhase.Cooldown -> RoutineScreen(
                label = stringResource(R.string.workout_cooldown_label),
                title = stringResource(R.string.workout_cooldown_title),
                subtitle = stringResource(R.string.workout_cooldown_subtitle),
                items = state.cooldownItems.map { it.name.tr() to it.durationSeconds },
                buttonText = stringResource(R.string.workout_cooldown_finish), buttonColor = FitCoachColors.Success,
                onNext = viewModel::finishCooldown, onSkip = viewModel::finishCooldown
            )

            WorkoutPhase.Feedback -> FeedbackScreen(askPain = state.askPain, onSubmit = viewModel::submitFeedback)

            WorkoutPhase.Summary -> state.summary?.let { WorkoutSummaryScreen(summary = it, onFinish = onFinished) }
        }

        state.setInput?.let { input ->
            SetInputDialog(input = input, onDismiss = viewModel::dismissSetInput, onConfirm = viewModel::confirmSet)
        }

        state.rest?.let { rest ->
            RestTimerSheet(
                rest = rest,
                onAdjust = viewModel::adjustRest,
                onSkip = viewModel::skipRest
            )
        }
    }
}

@Composable
private fun ExercisesContent(
    state: WorkoutActiveUiState,
    onSetClick: (com.fitcoach.app.domain.model.ExerciseSet) -> Unit,
    onUndoSet: (com.fitcoach.app.domain.model.ExerciseSet) -> Unit,
    onReplace: (ExerciseUiState, String) -> Unit,
    onOpenYoutube: (String) -> Unit,
    onFinishEarly: () -> Unit,
    onOpenChat: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        WorkoutProgressHeader(
            title = state.title.tr().ifBlank { stringResource(R.string.workout_default_title) },
            doneSets = state.doneSets,
            totalSets = state.totalSets,
            elapsed = WorkoutTitles.formatDuration(state.elapsedSeconds),
            onOpenChat = onOpenChat
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            if (state.isLoading) {
                CircularProgressIndicator(color = FitCoachColors.Accent, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.exercises.forEach { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onSetClick = onSetClick,
                    onUndoSet = onUndoSet,
                    onReplace = { onReplace(exercise, it) },
                    onOpenYoutube = { onOpenYoutube(exercise.youtubeQuery) }
                )
            }
            Text(
                stringResource(R.string.workout_undo_hint),
                fontSize = 12.sp, color = FitCoachColors.TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            OutlinedButton(
                onClick = onFinishEarly,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextSecondary),
                border = BorderStroke(1.dp, FitCoachColors.Border),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.workout_finish_early)) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoutineScreen(
    label: String,
    title: String,
    subtitle: String,
    items: List<Pair<String, Int>>,
    buttonText: String,
    buttonColor: androidx.compose.ui.graphics.Color,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
        Text(subtitle, fontSize = 14.sp, color = FitCoachColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        items.forEach { (name, seconds) ->
            FitCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = FitCoachColors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(WorkoutTitles.formatDuration(seconds.toLong()), color = buttonColor, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (items.isEmpty()) {
            Text(stringResource(R.string.workout_routine_empty), color = FitCoachColors.TextSecondary)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = buttonText, onClick = onNext, containerColor = buttonColor)
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.common_skip), color = FitCoachColors.TextMuted)
        }
    }
}
