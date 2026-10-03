package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.Budget
import com.fitcoach.app.domain.model.Cuisine
import com.fitcoach.app.domain.model.MealPlanPrefs
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors

/** Шторка предпочтений перед генерацией плана: дни, приёмы, кухня, халяль, бюджет, исключения, «готовлю на 2 дня». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealPlanPrefsSheet(
    initialDays: Int,
    initialMealsPerDay: Int,
    initialPrefs: MealPlanPrefs,
    onDismiss: () -> Unit,
    onGenerate: (days: Int, mealsPerDay: Int, prefs: MealPlanPrefs) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var days by remember { mutableStateOf(initialDays) }
    var meals by remember { mutableStateOf(initialMealsPerDay) }
    var cuisine by remember { mutableStateOf(initialPrefs.cuisine) }
    var halal by remember { mutableStateOf(initialPrefs.halal) }
    var budget by remember { mutableStateOf(initialPrefs.budget) }
    var batch by remember { mutableStateOf(initialPrefs.batchCooking) }
    var exclusions by remember { mutableStateOf(initialPrefs.exclusions) }
    var exclusionInput by remember { mutableStateOf("") }

    fun addExclusion() {
        val value = exclusionInput.trim().trimEnd(',', '.')
        if (value.isNotEmpty() && exclusions.none { it.equals(value, ignoreCase = true) } && exclusions.size < 10) exclusions = exclusions + value
        exclusionInput = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FitCoachColors.Surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.mealplan_prefs_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary)

            SectionLabel(stringResource(R.string.mealplan_days))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 7).forEach { d -> SelectableChip(text = "$d", selected = days == d, onClick = { days = d }, modifier = Modifier.weight(1f)) }
            }

            SectionLabel(stringResource(R.string.mealplan_meals_per_day))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { m -> SelectableChip(text = "$m", selected = meals == m, onClick = { meals = m }, modifier = Modifier.weight(1f)) }
            }

            SectionLabel(stringResource(R.string.mealplan_cuisine))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Cuisine.entries.forEach { c ->
                    SelectableChip(text = stringResource(cuisineRes(c)), selected = cuisine == c, onClick = { cuisine = c })
                }
            }

            SectionLabel(stringResource(R.string.mealplan_budget))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Budget.entries.forEach { b ->
                    SelectableChip(text = stringResource(budgetRes(b)), selected = budget == b, onClick = { budget = b })
                }
            }

            SwitchRow(stringResource(R.string.mealplan_halal), halal) { halal = it }
            SwitchRow(stringResource(R.string.mealplan_batch), batch) { batch = it }

            SectionLabel(stringResource(R.string.mealplan_exclusions))
            if (exclusions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exclusions.forEach { ex ->
                        SelectableChip(text = "$ex  ✕", selected = true, onClick = { exclusions = exclusions - ex })
                    }
                }
            }
            OutlinedTextField(
                value = exclusionInput,
                onValueChange = { exclusionInput = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.mealplan_exclusion_hint), color = FitCoachColors.TextMuted) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { addExclusion() }, enabled = exclusionInput.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add), tint = if (exclusionInput.isNotBlank()) FitCoachColors.Accent else FitCoachColors.TextMuted)
                    }
                },
                colors = chefTextFieldColors()
            )

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = stringResource(R.string.mealplan_generate),
                onClick = {
                    addExclusion()
                    onGenerate(days, meals, MealPlanPrefs(cuisine, exclusions, halal, budget, batch))
                }
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FitCoachColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = FitCoachColors.AccentOn, checkedTrackColor = FitCoachColors.Accent)
        )
    }
}

fun cuisineRes(c: Cuisine): Int = when (c) {
    Cuisine.KAZAKH -> R.string.cuisine_kazakh
    Cuisine.HOME -> R.string.cuisine_home
    Cuisine.ANY -> R.string.cuisine_any
}

fun budgetRes(b: Budget): Int = when (b) {
    Budget.LOW -> R.string.budget_low
    Budget.MID -> R.string.budget_mid
    Budget.ANY -> R.string.budget_any
}
