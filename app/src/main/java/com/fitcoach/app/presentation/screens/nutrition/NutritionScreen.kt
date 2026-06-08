package com.fitcoach.app.presentation.screens.nutrition

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.presentation.components.*
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NutritionUiState(
    val summary: NutritionSummary = NutritionSummary(),
    val profile: UserProfile = UserProfile()
)

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val nutritionRepo: NutritionRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                nutritionRepo.getNutritionSummaryForDate(System.currentTimeMillis()),
                userRepo.observeProfile()
            ) { summary, profile ->
                NutritionUiState(summary, profile ?: UserProfile())
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
                Text("Питание", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
            }

            item {
                FitCard {
                    Text("КБЖУ ЗА ДЕНЬ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CircularProgress(nut.calories.toFloat(), profile.calorieGoal.toFloat(), FitCoachColors.Accent, "ккал")
                        CircularProgress(nut.proteinG, profile.proteinGoal.toFloat(), FitCoachColors.PhaseBlue, "белок г")
                    }
                    Spacer(Modifier.height(12.dp))
                    LabeledProgressBar(nut.carbsG, profile.carbsGoal.toFloat(), FitCoachColors.PhaseOrange, "Углеводы", "${nut.carbsG.toInt()}/${profile.carbsGoal}г")
                    Spacer(Modifier.height(8.dp))
                    LabeledProgressBar(nut.fatG, profile.fatGoal.toFloat(), FitCoachColors.Warning, "Жиры", "${nut.fatG.toInt()}/${profile.fatGoal}г")
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
                Text(mealType.displayName, fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                if (entries.isNotEmpty()) {
                    Text(
                        text = "${entries.sumOf { it.calories }} ккал · Б${entries.sumOf { it.proteinG.toDouble() }.toInt()}г",
                        fontSize = 12.sp,
                        color = FitCoachColors.TextMuted
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Добавить", tint = FitCoachColors.Accent)
            }
        }

        if (entries.isNotEmpty()) {
            Divider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, fontSize = 14.sp, color = FitCoachColors.TextPrimary)
                        Text("${entry.calories} ккал · Б${entry.proteinG.toInt()}/У${entry.carbsG.toInt()}/Ж${entry.fatG.toInt()}г",
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
