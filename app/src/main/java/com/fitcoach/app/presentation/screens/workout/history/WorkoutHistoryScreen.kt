package com.fitcoach.app.presentation.screens.workout.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    workoutRepo: WorkoutRepository
) : ViewModel() {
    val workouts = workoutRepo.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    onWorkoutClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutHistoryViewModel = hiltViewModel()
) {
    val workouts by viewModel.workouts.collectAsState()
    val df = remember { SimpleDateFormat("dd MMMM, EEE", Locale("ru")) }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("История тренировок", color = FitCoachColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = FitCoachColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(workouts) { workout ->
                WorkoutHistoryCard(workout = workout, df = df, onClick = { onWorkoutClick(workout.id) })
            }
        }
    }
}

@Composable
private fun WorkoutHistoryCard(workout: Workout, df: SimpleDateFormat, onClick: () -> Unit) {
    FitCard(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = df.format(Date(workout.date)),
                    fontSize = 12.sp,
                    color = FitCoachColors.TextMuted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = workout.planKey,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FitCoachColors.Accent
                )
                Text(workout.phaseName, fontSize = 13.sp, color = FitCoachColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (workout.isCompleted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FitCoachColors.Success)
                } else {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = FitCoachColors.TextMuted)
                }
                if (workout.backPainLevel > 0) {
                    Text(
                        text = "Боль: ${workout.backPainLevel}/10",
                        fontSize = 11.sp,
                        color = when {
                            workout.backPainLevel <= 3 -> FitCoachColors.Success
                            workout.backPainLevel <= 6 -> FitCoachColors.Warning
                            else -> FitCoachColors.Error
                        }
                    )
                }
                workout.durationMinutes?.let {
                    Text("${it} мин", fontSize = 11.sp, color = FitCoachColors.TextMuted)
                }
            }
        }
    }
}
