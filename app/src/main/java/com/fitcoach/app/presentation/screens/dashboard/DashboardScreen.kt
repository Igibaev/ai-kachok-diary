package com.fitcoach.app.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.presentation.components.CircularProgress
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.InfoChip
import com.fitcoach.app.presentation.components.LabeledProgressBar
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.screens.club.PromoBanner
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onStartWorkout: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWater: () -> Unit = {},
    onOpenQrPass: () -> Unit = {},
    onOpenPrograms: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var activityDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(FitCoachColors.Background)) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = FitCoachColors.Accent)
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(name = state.profile.name, onOpenSettings = onOpenSettings, onOpenQrPass = onOpenQrPass)

            // Акции клуба (реализует агент «Клуб»; скрыт, если акций нет)
            PromoBanner()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("🔥", if (state.streakWeeks > 0) "${state.streakWeeks} ${weeksWord(state.streakWeeks)} подряд" else "Начни стрик")
                InfoChip("📅", "${state.workoutsThisWeek}/${state.profile.daysPerWeek} на этой неделе")
            }

            NextWorkoutCard(
                state = state,
                onStart = { viewModel.startNextWorkout(onStartWorkout) },
                onLogActivity = { activityDialog = true },
                onOpenPrograms = onOpenPrograms
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FitCard(modifier = Modifier.weight(1f).clickable { onOpenWater() }) {
                    SectionLabel("Вода")
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgress(
                            value = state.waterToday.toFloat(), max = state.profile.waterGoalMl.toFloat(),
                            color = FitCoachColors.Water, label = "мл", size = 90.dp, strokeWidth = 8.dp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${state.waterToday} / ${state.profile.waterGoalMl} мл", fontSize = 12.sp, color = FitCoachColors.TextMuted)
                    Text("+ добавить", fontSize = 12.sp, color = FitCoachColors.Water, fontWeight = FontWeight.Medium)
                }
                FitCard(modifier = Modifier.weight(1f)) {
                    SectionLabel("КБЖУ")
                    Spacer(Modifier.height(8.dp))
                    val nut = state.nutritionSummary
                    val p = state.profile
                    LabeledProgressBar(nut.calories.toFloat(), p.calorieGoal.toFloat(), FitCoachColors.Accent, "Ккал", "${nut.calories}/${p.calorieGoal}")
                    Spacer(Modifier.height(6.dp))
                    LabeledProgressBar(nut.proteinG, p.proteinGoal.toFloat(), FitCoachColors.PhaseBlue, "Белок", "${nut.proteinG.toInt()}/${p.proteinGoal} г")
                    Spacer(Modifier.height(6.dp))
                    LabeledProgressBar(nut.carbsG, p.carbsGoal.toFloat(), FitCoachColors.PhaseOrange, "Углеводы", "${nut.carbsG.toInt()}/${p.carbsGoal} г")
                    Spacer(Modifier.height(6.dp))
                    LabeledProgressBar(nut.fatG, p.fatGoal.toFloat(), FitCoachColors.Warning, "Жиры", "${nut.fatG.toInt()}/${p.fatGoal} г")
                }
            }

            FitCard(modifier = Modifier.clickable { onOpenChat() }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(FitCoachColors.AccentSoft),
                        contentAlignment = Alignment.Center
                    ) { Text("🤖", fontSize = 20.sp) }
                    Column(Modifier.weight(1f)) {
                        Text("AI-тренер ${BrandConfig.aiCoachName}", fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                        Text("Спроси о тренировке, питании или технике", fontSize = 12.sp, color = FitCoachColors.TextMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${state.completedWorkouts}", "тренировок всего")
                StatCard(Modifier.weight(1f), "${state.currentWeek}/12", "неделя программы")
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    if (activityDialog) {
        LogActivityDialog(
            onDismiss = { activityDialog = false },
            onConfirm = { type, minutes -> viewModel.logActivity(type, minutes); activityDialog = false }
        )
    }
}

@Composable
private fun Header(name: String, onOpenSettings: () -> Unit, onOpenQrPass: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            val greeting = when (LocalTime.now().hour) {
                in 5..11 -> "Доброе утро"
                in 12..17 -> "Добрый день"
                in 18..22 -> "Добрый вечер"
                else -> "Привет"
            }
            Text("$greeting, ${name.ifBlank { "Атлет" }} 👊", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary)
            val formatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru")) }
            Text(LocalDate.now().format(formatter).replaceFirstChar { it.uppercase() }, fontSize = 14.sp, color = FitCoachColors.TextMuted)
        }
        IconButton(onClick = onOpenQrPass) {
            Icon(Icons.Default.CreditCard, contentDescription = "Карта участника", tint = FitCoachColors.Accent)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = FitCoachColors.TextMuted)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    FitCard(modifier = modifier) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.Accent)
        Text(label, fontSize = 12.sp, color = FitCoachColors.TextMuted)
    }
}

private fun weeksWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "неделя"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "недели"
    else -> "недель"
}
