package com.fitcoach.app.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.presentation.components.*
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onStartWorkout: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = FitCoachColors.Accent
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                            in 5..11 -> "Доброе утро"
                            in 12..17 -> "Добрый день"
                            in 18..22 -> "Добрый вечер"
                            else -> "Привет"
                        }
                        Text(
                            text = "$greeting, ${state.profile.name.ifEmpty { "Атлет" }} 👊",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FitCoachColors.TextPrimary
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date()),
                            fontSize = 14.sp,
                            color = FitCoachColors.TextMuted
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = FitCoachColors.TextMuted)
                    }
                }

                // Phase chip
                val phase = when {
                    state.currentWeek <= 4 -> 1
                    state.currentWeek <= 8 -> 2
                    else -> 3
                }
                PhaseChip(phase = phase, week = state.currentWeek)

                // Today's workout card
                FitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ТРЕНИРОВКА СЕГОДНЯ",
                                style = MaterialTheme.typography.labelSmall,
                                color = FitCoachColors.TextMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            if (state.todayWorkoutKey != null) {
                                Text(
                                    text = state.todayWorkoutKey!!,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FitCoachColors.Accent
                                )
                                val template = com.fitcoach.app.domain.model.WorkoutPlan.getTemplate(state.todayWorkoutKey!!)
                                Text(
                                    text = template?.phaseName ?: "",
                                    fontSize = 14.sp,
                                    color = FitCoachColors.TextSecondary
                                )
                            } else {
                                Text(
                                    text = "День отдыха",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FitCoachColors.TextSecondary
                                )
                                Text(
                                    text = "Восстановление — тоже часть плана",
                                    fontSize = 12.sp,
                                    color = FitCoachColors.TextMuted
                                )
                            }
                        }
                        if (state.todayWorkout?.isCompleted == true) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(FitCoachColors.Success.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FitCoachColors.Success, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    if (state.todayWorkoutKey != null && state.todayWorkout?.isCompleted != true) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.startTodayWorkout(onStartWorkout) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = FitCoachColors.Background)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.todayWorkout != null) "Продолжить" else "Начать тренировку",
                                color = FitCoachColors.Background,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Water & KBZHU row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Water
                    FitCard(modifier = Modifier.weight(1f)) {
                        Text("ВОДА", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                        Spacer(Modifier.height(8.dp))
                        CircularProgress(
                            value = state.waterToday.toFloat(),
                            max = state.profile.waterGoalMl.toFloat(),
                            color = FitCoachColors.PhaseBlue,
                            label = "мл",
                            size = 90.dp,
                            strokeWidth = 8.dp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${state.waterToday} / ${state.profile.waterGoalMl} мл",
                            fontSize = 12.sp,
                            color = FitCoachColors.TextMuted
                        )
                    }

                    // KBZHU
                    FitCard(modifier = Modifier.weight(1f)) {
                        Text("КБЖУ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                        Spacer(Modifier.height(8.dp))
                        val nut = state.nutritionSummary
                        val profile = state.profile
                        LabeledProgressBar(
                            value = nut.calories.toFloat(),
                            max = profile.calorieGoal.toFloat(),
                            color = FitCoachColors.Accent,
                            label = "Ккал",
                            sub = "${nut.calories}/${profile.calorieGoal}"
                        )
                        Spacer(Modifier.height(6.dp))
                        LabeledProgressBar(
                            value = nut.proteinG,
                            max = profile.proteinGoal.toFloat(),
                            color = FitCoachColors.PhaseBlue,
                            label = "Белок",
                            sub = "${nut.proteinG.toInt()}г"
                        )
                        Spacer(Modifier.height(6.dp))
                        LabeledProgressBar(
                            value = nut.carbsG,
                            max = profile.carbsGoal.toFloat(),
                            color = FitCoachColors.PhaseOrange,
                            label = "Углеводы",
                            sub = "${nut.carbsG.toInt()}г"
                        )
                        Spacer(Modifier.height(6.dp))
                        LabeledProgressBar(
                            value = nut.fatG,
                            max = profile.fatGoal.toFloat(),
                            color = FitCoachColors.Warning,
                            label = "Жиры",
                            sub = "${nut.fatG.toInt()}г"
                        )
                    }
                }

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${state.completedWorkouts}",
                        label = "Тренировок выполнено"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${state.currentWeek}/12",
                        label = "Неделя программы"
                    )
                }

                // Quick chat button
                FitCard(
                    modifier = Modifier.clickable { onOpenChat() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(FitCoachColors.Accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 20.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Тренер", fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                            Text("Спроси о тренировке или питании", fontSize = 12.sp, color = FitCoachColors.TextMuted)
                        }
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, value: String, label: String) {
    FitCard(modifier = modifier) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FitCoachColors.Accent
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = FitCoachColors.TextMuted
        )
    }
}
