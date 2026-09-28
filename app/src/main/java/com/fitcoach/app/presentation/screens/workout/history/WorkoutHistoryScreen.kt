package com.fitcoach.app.presentation.screens.workout.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.ActivityType
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.l10n.DomainTranslations
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.workoutSubtitle
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HistoryItem(
    val workout: Workout,
    /** Каноническое (русское) название — переводится в UI через `.tr()`. */
    val title: String,
    val volumeKg: Int,
    val doneSets: Int,
    val totalSets: Int
)

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(workoutRepo: WorkoutRepository) : ViewModel() {
    val items = workoutRepo.getAllWorkouts()
        .map { list ->
            list.map { w ->
                val sets = if (ActivityType.fromPlanKey(w.planKey) == null) workoutRepo.getSetsForWorkoutSync(w.id) else emptyList()
                HistoryItem(
                    workout = w,
                    title = WorkoutTitles.titleFor(w),
                    volumeKg = WorkoutTitles.volumeKg(sets),
                    doneSets = sets.count { it.isDone },
                    totalSets = sets.size
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    onWorkoutClick: (String) -> Unit,
    onOpenPrograms: () -> Unit = {},
    viewModel: WorkoutHistoryViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("d MMMM, EE", DomainTranslations.currentLocale()) }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), color = FitCoachColors.TextPrimary) },
                actions = {
                    TextButton(onClick = onOpenPrograms) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = FitCoachColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.history_programs), color = FitCoachColors.Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🏋️", fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.history_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.history_empty_text),
                    fontSize = 14.sp, color = FitCoachColors.TextSecondary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onOpenPrograms) { Text(stringResource(R.string.history_empty_programs), color = FitCoachColors.Accent) }
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)
        ) {
            items(items, key = { it.workout.id }) { item ->
                HistoryCard(
                    item = item,
                    date = Instant.ofEpochMilli(item.workout.date).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter),
                    onClick = { onWorkoutClick(item.workout.id) }
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, date: String, onClick: () -> Unit) {
    val w = item.workout
    val isActivity = ActivityType.fromPlanKey(w.planKey)
    FitCard(modifier = Modifier.clickable { onClick() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(date, fontSize = 12.sp, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    (isActivity?.emoji?.let { "$it " } ?: "") + item.title.tr(),
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (w.isCompleted) FitCoachColors.Accent else FitCoachColors.TextSecondary
                )
                Text(workoutSubtitle(w), fontSize = 12.sp, color = FitCoachColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    w.durationMinutes?.takeIf { it > 0 }?.let { Meta(stringResource(R.string.history_duration, it)) }
                    if (item.totalSets > 0) Meta(stringResource(R.string.history_sets, item.doneSets, item.totalSets))
                    if (item.volumeKg > 0) Meta(stringResource(R.string.format_kg, item.volumeKg.toString()))
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (w.isCompleted) Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.history_completed), tint = FitCoachColors.Success)
                else Icon(Icons.Default.RadioButtonUnchecked, contentDescription = stringResource(R.string.history_not_completed), tint = FitCoachColors.TextMuted)
                if (w.painLevel > 0) {
                    Text(
                        stringResource(R.string.history_discomfort, w.painLevel), fontSize = 11.sp,
                        color = when { w.painLevel <= 3 -> FitCoachColors.Success; w.painLevel <= 6 -> FitCoachColors.Warning; else -> FitCoachColors.Error }
                    )
                }
            }
        }
    }
}

@Composable
private fun Meta(text: String) = Text(text, fontSize = 12.sp, color = FitCoachColors.TextMuted)
