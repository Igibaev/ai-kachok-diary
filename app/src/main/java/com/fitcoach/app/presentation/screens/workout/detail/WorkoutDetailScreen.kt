package com.fitcoach.app.presentation.screens.workout.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.l10n.DomainTranslations
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.workoutSubtitle
import com.fitcoach.app.presentation.components.workoutTitle
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

data class WorkoutDetailState(
    val workout: Workout? = null,
    val exercises: List<Pair<String, List<ExerciseSet>>> = emptyList()
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    workoutRepo: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle["workoutId"])

    val state = combine(
        workoutRepo.observeWorkoutById(workoutId),
        workoutRepo.getSetsForWorkout(workoutId)
    ) { workout, sets ->
        WorkoutDetailState(
            workout = workout,
            exercises = sets.groupBy { it.exerciseId }.values
                .map { list -> list.first().exerciseName to list.sortedBy { it.setNumber } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutDetailState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val workout = state.workout
    val df = SimpleDateFormat("d MMMM yyyy, EEEE", DomainTranslations.currentLocale())

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(workout?.let { workoutTitle(it) } ?: stringResource(R.string.workout_default_title), color = FitCoachColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = FitCoachColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (workout != null) {
                item {
                    FitCard {
                        Text(df.format(Date(workout.date)), fontSize = 13.sp, color = FitCoachColors.TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(workoutSubtitle(workout), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Stat(stringResource(R.string.detail_status), stringResource(if (workout.isCompleted) R.string.history_completed else R.string.history_not_completed))
                            workout.durationMinutes?.takeIf { it > 0 }?.let { Stat(stringResource(R.string.detail_time), stringResource(R.string.format_minutes, it)) }
                            if (workout.rpe > 0) Stat(stringResource(R.string.detail_rpe), stringResource(R.string.format_of_ten, workout.rpe))
                            if (workout.painLevel > 0) Stat(stringResource(R.string.detail_discomfort), stringResource(R.string.format_of_ten, workout.painLevel))
                            val volume = WorkoutTitles.volumeKg(state.exercises.flatMap { it.second })
                            if (volume > 0) Stat(stringResource(R.string.detail_volume), stringResource(R.string.format_kg, volume.toString()))
                        }
                    }
                }
            }
            items(state.exercises) { (name, sets) ->
                FitCard {
                    Text(name.tr(), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    sets.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.component_set_number, s.setNumber), fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                            val reps = s.actualReps ?: s.targetReps
                            val actual = s.actualWeight?.let { stringResource(R.string.detail_set_result_weight, reps, formatWeight(it)) }
                                ?: stringResource(R.string.detail_set_result_target, reps, s.targetWeight.tr())
                            Text(actual, fontSize = 13.sp, color = if (s.isDone) FitCoachColors.TextPrimary else FitCoachColors.TextMuted)
                            Text(if (s.isDone) "✓" else "—", color = if (s.isDone) FitCoachColors.Success else FitCoachColors.TextMuted)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = FitCoachColors.TextMuted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FitCoachColors.TextPrimary)
    }
}

private fun formatWeight(w: Float): String = WorkoutTitles.formatWeight(w)
