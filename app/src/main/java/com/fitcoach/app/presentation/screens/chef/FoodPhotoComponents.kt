package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.Confidence
import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.domain.model.FoodItem
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.theme.FitCoachColors

fun confidenceRes(c: Confidence): Int = when (c) {
    Confidence.LOW -> R.string.confidence_low
    Confidence.MEDIUM -> R.string.confidence_medium
    Confidence.HIGH -> R.string.confidence_high
}

/** Результат разбора: позиции с редактируемыми граммами, итог, заметка, выбор приёма пищи и кнопка добавления. */
@Composable
fun FoodAnalysisResult(
    analysis: FoodAnalysis,
    items: List<FoodItem>,
    totals: FoodAnalysis,
    isDemo: Boolean,
    selectedMeal: MealType,
    onGramsChange: (index: Int, grams: Float) -> Unit,
    onSelectMeal: (MealType) -> Unit,
    onAdd: () -> Unit,
    onRetake: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!analysis.isFood) {
            FitCard {
                Text("🤔 " + stringResource(R.string.foodphoto_not_food_title), fontWeight = FontWeight.SemiBold, color = FitCoachColors.Warning)
                Spacer(Modifier.height(6.dp))
                Text(analysis.note.ifBlank { stringResource(R.string.foodphoto_not_food_text) }, fontSize = 14.sp, color = FitCoachColors.TextSecondary, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = stringResource(R.string.foodphoto_retake), onClick = onRetake)
            }
            return
        }

        FitCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.foodphoto_result_title), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                if (isDemo) DemoBadge(stringResource(R.string.foodphoto_demo_badge))
            }
            HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            items.forEachIndexed { index, item ->
                FoodItemRow(item = item, onGramsChange = { onGramsChange(index, it) })
                if (index < items.lastIndex) HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            }
            HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.foodphoto_total, totals.totalCalories, totals.totalProteinG.toInt(), totals.totalCarbsG.toInt(), totals.totalFatG.toInt()),
                fontWeight = FontWeight.Bold, color = FitCoachColors.Accent, fontSize = 15.sp
            )
            if (analysis.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(analysis.note, fontSize = 13.sp, color = FitCoachColors.TextSecondary, lineHeight = 18.sp)
            }
        }

        SectionLabel(stringResource(R.string.foodphoto_choose_meal))
        MealTypeChips(selected = selectedMeal, onSelect = onSelectMeal)
        PrimaryButton(text = stringResource(R.string.foodphoto_add), onClick = onAdd, enabled = items.any { it.grams > 0f })
        androidx.compose.material3.TextButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.foodphoto_retake), color = FitCoachColors.TextMuted)
        }
    }
}

@Composable
private fun FoodItemRow(item: FoodItem, onGramsChange: (Float) -> Unit) {
    var gramsText by remember(item.name) { mutableStateOf(item.grams.toInt().toString()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                stringResource(R.string.mealplan_meal_macros, item.calories, item.proteinG.toInt(), item.carbsG.toInt(), item.fatG.toInt()),
                fontSize = 11.sp, color = FitCoachColors.TextMuted
            )
            Text(stringResource(confidenceRes(item.confidence)), fontSize = 11.sp, color = confidenceColor(item.confidence))
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = gramsText,
            onValueChange = { v ->
                val clean = v.filter { it.isDigit() }.take(5)
                gramsText = clean
                clean.toFloatOrNull()?.let(onGramsChange)
            },
            label = { Text(stringResource(R.string.foodphoto_grams_label), color = FitCoachColors.TextMuted, fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = chefTextFieldColors(),
            modifier = Modifier.width(96.dp)
        )
    }
}

private fun confidenceColor(c: Confidence) = when (c) {
    Confidence.HIGH -> FitCoachColors.Success
    Confidence.MEDIUM -> FitCoachColors.Warning
    Confidence.LOW -> FitCoachColors.Error
}
