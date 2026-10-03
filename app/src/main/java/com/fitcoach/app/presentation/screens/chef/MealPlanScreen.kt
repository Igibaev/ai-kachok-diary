package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.domain.model.PlanMeal
import com.fitcoach.app.domain.model.SavedMealPlan
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun MealPlanScreen(
    onBack: () -> Unit,
    onOpenShoppingList: () -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showPrefs by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf<PlanMeal?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.messageRes) {
        val res = state.messageRes ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(res))
        viewModel.consumeMessage()
    }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = { ChefTopBar(title = stringResource(R.string.mealplan_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(FitCoachColors.Background)) {
            val saved = state.saved
            when {
                state.loading -> CircularProgressIndicator(color = FitCoachColors.Accent, modifier = Modifier.align(Alignment.Center))
                state.generating -> Column(modifier = Modifier.padding(16.dp)) {
                    ChefProgressCard(
                        title = stringResource(R.string.mealplan_progress_title),
                        text = stringResource(R.string.mealplan_progress_text),
                        onCancel = { viewModel.cancelGeneration() }
                    )
                }
                state.error != null -> Column(modifier = Modifier.padding(16.dp)) {
                    ChefErrorCard(
                        title = stringResource(R.string.mealplan_error_title),
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.clearError(); showPrefs = true },
                        onDismiss = { viewModel.clearError() }
                    )
                }
                saved == null || saved.plan.days.isEmpty() -> Column(modifier = Modifier.padding(16.dp)) {
                    ChefEmptyState(
                        emoji = "🍽",
                        title = stringResource(R.string.mealplan_empty_title),
                        text = stringResource(R.string.mealplan_empty_text),
                        buttonText = stringResource(R.string.mealplan_create),
                        onClick = { showPrefs = true }
                    )
                }
                else -> PlanContent(
                    saved = saved,
                    goalCalories = state.profile.calorieGoal,
                    isDemo = saved.isDemo || state.mode == AiMode.DEMO,
                    selectedDay = state.selectedDay,
                    onSelectDay = viewModel::selectDay,
                    onMealClick = { selectedMeal = it },
                    onOpenShoppingList = onOpenShoppingList,
                    onRegenerate = { showPrefs = true },
                    onDelete = { confirmDelete = true }
                )
            }
        }
    }

    if (showPrefs) {
        MealPlanPrefsSheet(
            initialDays = viewModel.lastDays(),
            initialMealsPerDay = viewModel.lastMealsPerDay(),
            initialPrefs = viewModel.lastPrefs(),
            onDismiss = { showPrefs = false },
            onGenerate = { days, meals, prefs ->
                showPrefs = false
                viewModel.generate(days, meals, prefs)
            }
        )
    }

    selectedMeal?.let { meal ->
        PlanMealDetailSheet(
            meal = meal,
            onDismiss = { selectedMeal = null },
            onAddToDiary = { type ->
                viewModel.addMealToDiary(meal, type, R.string.mealplan_added)
                selectedMeal = null
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = FitCoachColors.Card,
            title = { Text(stringResource(R.string.mealplan_delete_title), color = FitCoachColors.TextPrimary) },
            text = { Text(stringResource(R.string.mealplan_delete_text), color = FitCoachColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deletePlan() }) { Text(stringResource(R.string.common_delete), color = FitCoachColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel), color = FitCoachColors.TextMuted) }
            }
        )
    }
}

@Composable
private fun PlanContent(
    saved: SavedMealPlan,
    goalCalories: Int,
    isDemo: Boolean,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onMealClick: (PlanMeal) -> Unit,
    onOpenShoppingList: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit
) {
    val plan = saved.plan
    val day = plan.days.firstOrNull { it.day == selectedDay } ?: plan.days.first()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FitCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.mealplan_goal_vs_plan, goalCalories, plan.averageCalories),
                        fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f)
                    )
                    if (isDemo) DemoBadge(stringResource(R.string.mealplan_demo_badge))
                }
                if (plan.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.mealplan_notes), fontSize = 11.sp, color = FitCoachColors.TextMuted)
                    Text(plan.notes, fontSize = 13.sp, color = FitCoachColors.TextSecondary, lineHeight = 18.sp)
                }
            }
        }

        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.days.forEach { d ->
                    SelectableChip(text = stringResource(R.string.mealplan_day_chip, d.day), selected = d.day == day.day, onClick = { onSelectDay(d.day) })
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.mealplan_day_totals, day.totalCalories, day.totalProteinG.toInt(), day.totalCarbsG.toInt(), day.totalFatG.toInt()),
                fontSize = 12.sp, color = FitCoachColors.TextMuted
            )
        }

        items(day.meals) { meal -> PlanMealCard(meal = meal, onClick = { onMealClick(meal) }) }

        item {
            Spacer(Modifier.height(4.dp))
            PrimaryButton(text = "🛒 " + stringResource(R.string.chef_btn_shopping), onClick = onOpenShoppingList)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mealplan_regenerate), color = FitCoachColors.TextPrimary)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mealplan_delete), color = FitCoachColors.Error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
