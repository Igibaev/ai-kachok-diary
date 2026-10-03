package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import com.fitcoach.app.domain.model.MealSlot
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.domain.model.PlanMeal
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.theme.FitCoachColors

fun MealSlot.emoji(): String = when (this) {
    MealSlot.BREAKFAST -> "🍳"
    MealSlot.LUNCH -> "🍲"
    MealSlot.DINNER -> "🍽"
    MealSlot.SNACK -> "🥛"
}

/** Карточка приёма пищи в плане: слот, название, КБЖУ, время. Тап — детали. */
@Composable
fun PlanMealCard(meal: PlanMeal, onClick: () -> Unit) {
    FitCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meal.slot.emoji(), fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(meal.slot.toMealType().displayName.tr(), fontSize = 11.sp, color = FitCoachColors.TextMuted)
                Text(meal.title, fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mealplan_meal_macros, meal.calories, meal.proteinG.toInt(), meal.carbsG.toInt(), meal.fatG.toInt()),
                    fontSize = 12.sp, color = FitCoachColors.TextSecondary
                )
            }
            if (meal.timeMinutes > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = FitCoachColors.TextMuted, modifier = Modifier.width(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.mealplan_minutes, meal.timeMinutes), fontSize = 12.sp, color = FitCoachColors.TextMuted)
                }
            }
        }
    }
}

/** Детали блюда: ингредиенты с граммами, шаги, выбор приёма пищи и «Добавить в дневник». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanMealDetailSheet(meal: PlanMeal, onDismiss: () -> Unit, onAddToDiary: (MealType) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mealType by remember { mutableStateOf(meal.slot.toMealType()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FitCoachColors.Surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${meal.slot.emoji()} ${meal.title}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.mealplan_meal_macros, meal.calories, meal.proteinG.toInt(), meal.carbsG.toInt(), meal.fatG.toInt()),
                    fontSize = 13.sp, color = FitCoachColors.TextSecondary
                )
                if (meal.timeMinutes > 0) Text("⏱ " + stringResource(R.string.mealplan_minutes, meal.timeMinutes), fontSize = 13.sp, color = FitCoachColors.TextSecondary)
            }

            if (meal.ingredients.isNotEmpty()) {
                SectionLabel(stringResource(R.string.mealplan_ingredients))
                FitCard {
                    meal.ingredients.forEachIndexed { i, ing ->
                        if (i > 0) HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ing.name, color = FitCoachColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.mealplan_ingredient_grams, ing.grams.toInt()), color = FitCoachColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }

            if (meal.steps.isNotEmpty()) {
                SectionLabel(stringResource(R.string.mealplan_steps))
                FitCard {
                    meal.steps.forEachIndexed { i, step ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${i + 1}.", color = FitCoachColors.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                            Text(step, color = FitCoachColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.mealplan_choose_meal))
            MealTypeChips(selected = mealType, onSelect = { mealType = it })
            PrimaryButton(text = stringResource(R.string.mealplan_add_to_diary), onClick = { onAddToDiary(mealType) })
        }
    }
}
