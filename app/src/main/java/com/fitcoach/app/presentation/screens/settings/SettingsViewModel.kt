package com.fitcoach.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.service.DemoDataSeeder
import com.fitcoach.app.domain.usecase.GoalCalculator
import com.fitcoach.app.presentation.screens.onboarding.OnboardingViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Редактируемая форма настроек (строки — чтобы поля ввода не «прыгали»). */
data class SettingsForm(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val age: String = "30",
    val heightCm: String = "175",
    val weightKg: String = "75",
    val targetWeightKg: String = "",
    val goal: Goal = Goal.GENERAL_FITNESS,
    val level: Level = Level.BEGINNER,
    val daysPerWeek: Int = 3,
    val restrictions: Set<Restriction> = emptySet(),
    val waterGoal: String = "2500",
    val calorieGoal: String = "2000",
    val proteinGoal: String = "130",
    val carbsGoal: String = "220",
    val fatGoal: String = "65",
    val language: String = "ru"
) {
    companion object {
        fun from(p: UserProfile) = SettingsForm(
            name = p.name, sex = p.sex, age = p.age.toString(), heightCm = p.heightCm.toString(),
            weightKg = p.weightKg.toString(), targetWeightKg = p.targetWeightKg?.toString() ?: "",
            goal = p.goal, level = p.level, daysPerWeek = p.daysPerWeek, restrictions = p.restrictions,
            waterGoal = p.waterGoalMl.toString(), calorieGoal = p.calorieGoal.toString(),
            proteinGoal = p.proteinGoal.toString(), carbsGoal = p.carbsGoal.toString(), fatGoal = p.fatGoal.toString(),
            language = p.language
        )
    }

    fun applyTo(p: UserProfile): UserProfile = p.copy(
        name = name.trim(), sex = sex,
        age = age.toIntOrNull()?.coerceIn(14, 90) ?: p.age,
        heightCm = heightCm.toIntOrNull()?.coerceIn(120, 230) ?: p.heightCm,
        weightKg = weightKg.replace(',', '.').toFloatOrNull()?.coerceIn(35f, 250f) ?: p.weightKg,
        targetWeightKg = targetWeightKg.replace(',', '.').toFloatOrNull(),
        goal = goal, level = level, daysPerWeek = daysPerWeek, restrictions = restrictions,
        waterGoalMl = waterGoal.toIntOrNull() ?: p.waterGoalMl,
        calorieGoal = calorieGoal.toIntOrNull() ?: p.calorieGoal,
        proteinGoal = proteinGoal.toIntOrNull() ?: p.proteinGoal,
        carbsGoal = carbsGoal.toIntOrNull() ?: p.carbsGoal,
        fatGoal = fatGoal.toIntOrNull() ?: p.fatGoal,
        language = language
    )
}

data class SettingsUiState(
    val form: SettingsForm = SettingsForm(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val demoSeeder: DemoDataSeeder
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = userRepo.getProfile() ?: UserProfile()
            _state.update { it.copy(form = SettingsForm.from(p), loaded = true) }
        }
    }

    fun edit(transform: (SettingsForm) -> SettingsForm) = _state.update { it.copy(form = transform(it.form)) }

    fun toggleRestriction(r: Restriction) = edit { f ->
        val set = f.restrictions.toMutableSet()
        if (!set.remove(r)) set.add(r)
        f.copy(restrictions = set)
    }

    fun setLanguage(lang: String) {
        edit { it.copy(language = lang) }
        OnboardingViewModel.applyLocale(lang)
        viewModelScope.launch {
            val p = userRepo.getProfile() ?: return@launch
            userRepo.saveProfile(p.copy(language = lang))
        }
    }

    /** Пересчёт КБЖУ и воды по текущим полям формы (без сохранения — пользователь нажмёт «Сохранить»). */
    fun recalculateGoals() {
        val profile = _state.value.form.applyTo(UserProfile())
        val goals = GoalCalculator.calculate(profile)
        edit {
            it.copy(
                calorieGoal = goals.calories.toString(), proteinGoal = goals.proteinG.toString(),
                carbsGoal = goals.carbsG.toString(), fatGoal = goals.fatG.toString(), waterGoal = goals.waterMl.toString()
            )
        }
        _state.update { it.copy(message = "Цели пересчитаны — не забудь сохранить") }
    }

    fun save() = viewModelScope.launch {
        val base = userRepo.getProfile() ?: UserProfile()
        userRepo.saveProfile(_state.value.form.applyTo(base))
        _state.update { it.copy(message = "Сохранено") }
    }

    fun seedDemo() = runBusy("Демо-данные загружены: 3 недели истории") { demoSeeder.seedThreeWeeks() }

    fun clearAll() = runBusy("Все данные очищены") { demoSeeder.clearAllUserData() }

    fun restartOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        val p = userRepo.getProfile() ?: UserProfile()
        userRepo.saveProfile(p.copy(onboardingCompleted = false))
        onDone()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun runBusy(successMessage: String, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        val result = runCatching { block() }
        _state.update {
            it.copy(busy = false, message = if (result.isSuccess) successMessage else "Не удалось: ${result.exceptionOrNull()?.message ?: "ошибка"}")
        }
    }
}
