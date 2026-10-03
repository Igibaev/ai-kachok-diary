package com.fitcoach.app.presentation.screens.chef

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.ai.chef.AiChefSelector
import com.fitcoach.app.ai.chef.MealPlanGenerator
import com.fitcoach.app.domain.model.MealPlanPrefs
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.domain.model.NutritionEntry
import com.fitcoach.app.domain.model.PlanMeal
import com.fitcoach.app.domain.model.SavedMealPlan
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.MealPlanRepository
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MealPlanUiState(
    val loading: Boolean = true,
    val saved: SavedMealPlan? = null,
    val generating: Boolean = false,
    val error: String? = null,
    val mode: AiMode = AiMode.DEMO,
    val profile: UserProfile = UserProfile(),
    val selectedDay: Int = 1,
    /** Одноразовое сообщение для snackbar (ресурс строки); сбрасывается через [consumeMessage]. */
    val messageRes: Int? = null
)

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val mealPlanRepo: MealPlanRepository,
    private val nutritionRepo: NutritionRepository,
    private val userRepo: UserRepository,
    private val generator: MealPlanGenerator,
    private val selector: AiChefSelector
) : ViewModel() {

    private val _state = MutableStateFlow(MealPlanUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mealPlanRepo.observeActivePlan(), userRepo.observeProfile(), generator.state) { saved, profile, gen ->
                Triple(saved, profile ?: UserProfile(), gen)
            }.collect { (saved, profile, gen) ->
                _state.update {
                    it.copy(
                        loading = false,
                        saved = saved,
                        profile = profile,
                        generating = gen is MealPlanGenerator.State.Running,
                        error = (gen as? MealPlanGenerator.State.Failed)?.message,
                        selectedDay = if (saved?.plan?.days?.any { d -> d.day == it.selectedDay } == true) it.selectedDay else 1
                    )
                }
            }
        }
        viewModelScope.launch { _state.update { it.copy(mode = selector.currentMode()) } }
    }

    /** Предпочтения для шторки: последние сохранённые или значения по умолчанию. */
    fun lastPrefs(): MealPlanPrefs = _state.value.saved?.prefs ?: MealPlanPrefs()
    fun lastDays(): Int = _state.value.saved?.days ?: 3
    fun lastMealsPerDay(): Int = _state.value.saved?.mealsPerDay ?: 4

    fun generate(days: Int, mealsPerDay: Int, prefs: MealPlanPrefs) {
        viewModelScope.launch { _state.update { it.copy(mode = selector.currentMode()) } }
        generator.start(days, mealsPerDay, prefs)
    }

    fun cancelGeneration() = generator.cancel()

    fun clearError() = generator.clearError()

    fun selectDay(day: Int) = _state.update { it.copy(selectedDay = day) }

    fun deletePlan() {
        viewModelScope.launch { mealPlanRepo.deleteActivePlan() }
    }

    /** Приём пищи плана → одна запись дневника за сегодня: суммарные КБЖУ, название блюда, граммы = сумма ингредиентов. */
    fun addMealToDiary(meal: PlanMeal, mealType: MealType, messageRes: Int) {
        viewModelScope.launch {
            nutritionRepo.addEntry(
                NutritionEntry(
                    id = UUID.randomUUID().toString(),
                    date = System.currentTimeMillis(),
                    mealType = mealType,
                    name = meal.title,
                    calories = meal.calories,
                    proteinG = meal.proteinG,
                    carbsG = meal.carbsG,
                    fatG = meal.fatG,
                    grams = meal.totalGrams.takeIf { it > 0f },
                    source = NutritionEntry.SOURCE_PLAN
                )
            )
            _state.update { it.copy(messageRes = messageRes) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(messageRes = null) }
}
