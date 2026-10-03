package com.fitcoach.app.presentation.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.MealPlanRepository
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.*
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import com.fitcoach.app.domain.util.DayBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NutritionUiState(
    val summary: NutritionSummary = NutritionSummary(),
    val profile: UserProfile = UserProfile(),
    /** Незакрытых позиций в списке покупок AI-повара (бейдж на карточке). */
    val shoppingLeft: Int = 0
)

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepo: NutritionRepository,
    private val userRepo: UserRepository,
    private val mealPlanRepo: MealPlanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionUiState())
    val state = _state.asStateFlow()

    /** Начало текущего дня; переоценивается раз в минуту — VM вкладки живёт весь процесс, иначе после полуночи показывали бы вчера. */
    private val today = flow {
        while (true) {
            emit(DayBounds.startOfDay())
            delay(60_000)
        }
    }.distinctUntilChanged()

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            today.flatMapLatest { day ->
                combine(
                    nutritionRepo.getNutritionSummaryForDate(day),
                    userRepo.observeProfile(),
                    mealPlanRepo.observeUncheckedCount()
                ) { summary, profile, left ->
                    NutritionUiState(summary, profile ?: UserProfile(), left)
                }
            }.collect { _state.value = it }
        }
    }

    fun deleteEntry(entry: NutritionEntry) {
        viewModelScope.launch { nutritionRepo.deleteEntry(entry) }
    }
}

@Composable
fun NutritionScreen(
    onAddFood: (String) -> Unit,
    onOpenWater: () -> Unit = {},
    onOpenFoodPhoto: () -> Unit = {},
    onOpenMealPlan: () -> Unit = {},
    onOpenShoppingList: () -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val nut = state.summary
    val profile = state.profile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(stringResource(R.string.nutrition_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
            }

            item {
                AiChefCard(
                    shoppingLeft = state.shoppingLeft,
                    onOpenFoodPhoto = onOpenFoodPhoto,
                    onOpenMealPlan = onOpenMealPlan,
                    onOpenShoppingList = onOpenShoppingList
                )
            }

            item {
                FitCard {
                    Text(stringResource(R.string.nutrition_daily), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CircularProgress(nut.calories.toFloat(), profile.calorieGoal.toFloat(), FitCoachColors.Accent, stringResource(R.string.nutrition_kcal))
                        CircularProgress(nut.proteinG, profile.proteinGoal.toFloat(), FitCoachColors.PhaseBlue, stringResource(R.string.nutrition_protein_g))
                    }
                    Spacer(Modifier.height(12.dp))
                    LabeledProgressBar(nut.carbsG, profile.carbsGoal.toFloat(), FitCoachColors.PhaseOrange, stringResource(R.string.nutrition_carbs), stringResource(R.string.nutrition_ratio_g, nut.carbsG.toInt(), profile.carbsGoal))
                    Spacer(Modifier.height(8.dp))
                    LabeledProgressBar(nut.fatG, profile.fatGoal.toFloat(), FitCoachColors.Warning, stringResource(R.string.nutrition_fat), stringResource(R.string.nutrition_ratio_g, nut.fatG.toInt(), profile.fatGoal))
                }
            }

            MealType.values().filter { it != MealType.TEMPLATE }.forEach { mealType ->
                item {
                    val entries = nut.entriesByMeal[mealType] ?: emptyList()
                    MealCard(
                        mealType = mealType,
                        entries = entries,
                        onAdd = { onAddFood(mealType.name) },
                        onDelete = { viewModel.deleteEntry(it) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun MealCard(
    mealType: MealType,
    entries: List<NutritionEntry>,
    onAdd: () -> Unit,
    onDelete: (NutritionEntry) -> Unit
) {
    FitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(mealType.displayName.tr(), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                if (entries.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.nutrition_meal_summary, entries.sumOf { it.calories }, entries.sumOf { it.proteinG.toDouble() }.toInt()),
                        fontSize = 12.sp,
                        color = FitCoachColors.TextMuted
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add), tint = FitCoachColors.Accent)
            }
        }

        if (entries.isNotEmpty()) {
            HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.name, fontSize = 14.sp, color = FitCoachColors.TextPrimary, modifier = Modifier.weight(1f, fill = false))
                            when (entry.source) {
                                NutritionEntry.SOURCE_PHOTO -> SourceIcon(Icons.Default.PhotoCamera, stringResource(R.string.nutrition_source_photo))
                                NutritionEntry.SOURCE_PLAN -> SourceIcon(Icons.Default.RestaurantMenu, stringResource(R.string.nutrition_source_plan))
                            }
                        }
                        Text(stringResource(R.string.nutrition_entry_macros, entry.calories, entry.proteinG.toInt(), entry.carbsG.toInt(), entry.fatG.toInt()),
                            fontSize = 11.sp, color = FitCoachColors.TextMuted)
                    }
                    IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Spacer(Modifier.width(6.dp))
    Icon(icon, contentDescription = description, tint = FitCoachColors.Accent, modifier = Modifier.size(14.dp))
}

/** Карточка AI-повара: фото еды → КБЖУ, план питания, список покупок (+ бейдж «осталось купить»). */
@Composable
private fun AiChefCard(
    shoppingLeft: Int,
    onOpenFoodPhoto: () -> Unit,
    onOpenMealPlan: () -> Unit,
    onOpenShoppingList: () -> Unit
) {
    FitCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("👨‍🍳", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.chef_card_title), fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary, fontSize = 16.sp)
                Text(stringResource(R.string.chef_card_subtitle), fontSize = 12.sp, color = FitCoachColors.TextMuted, lineHeight = 16.sp)
            }
        }
        if (shoppingLeft > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "🛒 " + pluralStringResource(R.plurals.plural_shopping_left, shoppingLeft, shoppingLeft),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = FitCoachColors.Warning,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(FitCoachColors.Warning.copy(alpha = 0.15f))
                    .border(1.dp, FitCoachColors.Warning.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenShoppingList)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = "📷 " + stringResource(R.string.chef_btn_photo), onClick = onOpenFoodPhoto)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(text = stringResource(R.string.chef_btn_plan), selected = false, onClick = onOpenMealPlan, modifier = Modifier.weight(1f), leading = "🍽")
            SelectableChip(text = stringResource(R.string.chef_btn_shopping), selected = shoppingLeft > 0, onClick = onOpenShoppingList, modifier = Modifier.weight(1f), leading = "🛒")
        }
    }
}
