package com.fitcoach.app.presentation.screens.workout.active

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.service.ShareService
import com.fitcoach.app.domain.service.WorkoutShareData
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutShareViewModel @Inject constructor(private val shareService: ShareService) : ViewModel() {
    fun share(context: android.content.Context, data: WorkoutShareData, onFailure: () -> Unit) {
        viewModelScope.launch {
            shareService.shareWorkoutCard(context, data).onFailure { onFailure() }
        }
    }
}

/** Самочувствие: 3 эмодзи → RPE 4/7/9; слайдер боли только при ограничении «спина». */
@Composable
fun FeedbackScreen(askPain: Boolean, onSubmit: (rpe: Int, pain: Int) -> Unit) {
    var rpe by remember { mutableIntStateOf(0) }
    var pain by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Как прошла тренировка?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeelingOption("😌", "Легко", selected = rpe == 4) { rpe = 4 }
            FeelingOption("💪", "Норм", selected = rpe == 7) { rpe = 7 }
            FeelingOption("🥵", "Тяжело", selected = rpe == 9) { rpe = 9 }
        }
        if (askPain) {
            Spacer(Modifier.height(32.dp))
            Text("Дискомфорт в спине после тренировки", fontSize = 15.sp, color = FitCoachColors.TextSecondary)
            Text(
                "$pain / 10", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                color = when { pain <= 3 -> FitCoachColors.Success; pain <= 6 -> FitCoachColors.Warning; else -> FitCoachColors.Error }
            )
            Slider(
                value = pain.toFloat(), onValueChange = { pain = it.toInt() }, valueRange = 0f..10f, steps = 9,
                colors = SliderDefaults.colors(thumbColor = FitCoachColors.Accent, activeTrackColor = FitCoachColors.Accent)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Нет", fontSize = 12.sp, color = FitCoachColors.TextMuted)
                Text("Сильная боль", fontSize = 12.sp, color = FitCoachColors.TextMuted)
            }
            if (pain >= 6) {
                Text("При сильной боли — сделай паузу и обратись к врачу или тренеру клуба.", fontSize = 12.sp, color = FitCoachColors.Warning, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(32.dp))
        PrimaryButton(text = "Сохранить", enabled = rpe > 0, onClick = { onSubmit(rpe, pain) })
    }
}

@Composable
private fun FeelingOption(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) FitCoachColors.AccentSoft else FitCoachColors.Card)
            .border(1.dp, if (selected) FitCoachColors.Accent else FitCoachColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 32.sp)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = if (selected) FitCoachColors.Accent else FitCoachColors.TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onFinish: () -> Unit,
    shareViewModel: WorkoutShareViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = FitCoachColors.Background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎉", fontSize = 56.sp)
            Text("Тренировка завершена!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary, textAlign = TextAlign.Center)
            Text(summary.title, fontSize = 15.sp, color = FitCoachColors.TextSecondary)

            FitCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SummaryStat("${summary.durationMinutes}", "мин")
                    SummaryStat("${summary.doneSets}/${summary.totalSets}", "подходов")
                    SummaryStat(if (summary.volumeKg > 0) "${summary.volumeKg}" else "—", "кг объём")
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = FitCoachColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SummaryStat("${summary.streakWeeks}", if (summary.streakWeeks == 1) "неделя подряд" else "недель подряд")
                    SummaryStat("${summary.workoutsThisWeek}", "на этой неделе")
                    SummaryStat("${summary.completedTotal}", "всего")
                }
            }

            if (summary.records.isNotEmpty()) {
                FitCard {
                    Text("🏆 РЕКОРДЫ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.Accent)
                    Spacer(Modifier.height(6.dp))
                    summary.records.forEach { Text(it, color = FitCoachColors.TextPrimary, fontSize = 14.sp) }
                }
            }

            summary.newBadges.forEach { badge ->
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(badge.emoji, fontSize = 32.sp)
                        Column {
                            Text("Новый бейдж: ${badge.title}", fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary)
                            Text(badge.description, fontSize = 12.sp, color = FitCoachColors.TextMuted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    shareViewModel.share(
                        context,
                        WorkoutShareData(
                            userName = summary.userName, workoutTitle = summary.title,
                            doneSets = summary.doneSets, totalSets = summary.totalSets,
                            durationMinutes = summary.durationMinutes, volumeKg = summary.volumeKg,
                            streakWorkouts = summary.streakWeeks, personalRecords = summary.records.size,
                            referralCode = ""
                        )
                    ) { scope.launch { snackbar.showSnackbar("Скоро: карточка для Stories") } }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextPrimary),
                border = BorderStroke(1.dp, FitCoachColors.Accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = FitCoachColors.Accent)
                Spacer(Modifier.width(8.dp))
                Text("Поделиться в Stories", fontWeight = FontWeight.Bold)
            }
            PrimaryButton(text = "На главную", onClick = onFinish)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.Accent)
        Text(label, fontSize = 11.sp, color = FitCoachColors.TextMuted)
    }
}
