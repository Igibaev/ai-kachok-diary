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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.l10n.DomainTranslations
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
                InfoChip(
                    "🔥",
                    if (state.streakWeeks > 0) pluralStringResource(R.plurals.plural_weeks_in_row, state.streakWeeks, state.streakWeeks)
                    else stringResource(R.string.dashboard_streak_start)
                )
                InfoChip("📅", stringResource(R.string.dashboard_this_week, state.workoutsThisWeek, state.profile.daysPerWeek))
            }

            NextWorkoutCard(
                state = state,
                onStart = { viewModel.startNextWorkout(onStartWorkout) },
                onLogActivity = { activityDialog = true },
                onOpenPrograms = onOpenPrograms
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FitCard(modifier = Modifier.weight(1f).clickable { onOpenWater() }) {
                    SectionLabel(stringResource(R.string.dashboard_water))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgress(
                            value = state.waterToday.toFloat(), max = state.profile.waterGoalMl.toFloat(),
                            color = FitCoachColors.Water, label = stringResource(R.string.unit_ml), size = 90.dp, strokeWidth = 8.dp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.dashboard_water_progress, state.waterToday, state.profile.waterGoalMl), fontSize = 12.sp, color = FitCoachColors.TextMuted)
                    Text(stringResource(R.string.dashboard_water_add), fontSize = 12.sp, color = FitCoachColors.Water, fontWeight = FontWeight.Medium)
                }
                FitCard(modifier = Modifier.weight(1f)) {
                    SectionLabel(stringResource(R.string.dashboard_macros))
                    Spacer(Modifier.height(8.dp))
                    val nut = state.nutritionSummary
                    val p = state.profile
                    LabeledProgressBar(nut.calories.toFloat(), p.calorieGoal.toFloat(), FitCoachColors.Accent, stringResource(R.string.dashboard_macros_kcal), stringResource(R.string.dashboard_macros_ratio, nut.calories, p.calorieGoal))
                    Spacer(Modifier.height(6.dp))
                    LabeledProgressBar(nut.proteinG, p.proteinGoal.toFloat(), FitCoachColors.PhaseBlue, stringResource(R.string.dashboard_macros_protein), stringResource(R.string.dashboard_macros_ratio_g, nut.proteinG.toInt(), p.proteinGoal))
                    Spacer(Modifier.height(6.dp))
                    LabeledProgressBar(nut.carbsG, p.carbsGoal.toFloat(), FitCoachColors.PhaseOrange, stringResource(R.string.dashboard_macros_carbs), stringResource(R.string.dashboard_macros_ratio_g, nut.carbsG.toInt(), p.carbsGoal))
                    Spacer(Modifier.height(6.dp))
                    LabeledProgressBar(nut.fatG, p.fatGoal.toFloat(), FitCoachColors.Warning, stringResource(R.string.dashboard_macros_fat), stringResource(R.string.dashboard_macros_ratio_g, nut.fatG.toInt(), p.fatGoal))
                }
            }

            FitCard(modifier = Modifier.clickable { onOpenChat() }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(FitCoachColors.AccentSoft),
                        contentAlignment = Alignment.Center
                    ) { Text("🤖", fontSize = 20.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_ai_coach, BrandConfig.aiCoachName), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                        Text(stringResource(R.string.dashboard_ai_coach_hint), fontSize = 12.sp, color = FitCoachColors.TextMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${state.completedWorkouts}", stringResource(R.string.dashboard_stat_total))
                StatCard(Modifier.weight(1f), stringResource(R.string.dashboard_stat_week_value, state.currentWeek), stringResource(R.string.dashboard_stat_week))
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
            val greeting = stringResource(
                when (LocalTime.now().hour) {
                    in 5..11 -> R.string.dashboard_greeting_morning
                    in 12..17 -> R.string.dashboard_greeting_day
                    in 18..22 -> R.string.dashboard_greeting_evening
                    else -> R.string.dashboard_greeting_night
                }
            )
            Text(
                stringResource(R.string.dashboard_greeting, greeting, name.ifBlank { stringResource(R.string.dashboard_default_name) }),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary
            )
            val formatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", DomainTranslations.currentLocale()) }
            Text(LocalDate.now().format(formatter).replaceFirstChar { it.uppercase() }, fontSize = 14.sp, color = FitCoachColors.TextMuted)
        }
        IconButton(onClick = onOpenQrPass) {
            Icon(Icons.Default.CreditCard, contentDescription = stringResource(R.string.dashboard_member_card), tint = FitCoachColors.Accent)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.common_settings), tint = FitCoachColors.TextMuted)
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

