package com.fitcoach.app.presentation.screens.onboarding

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.ProgramRecommendation
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.usecase.GoalCalculator
import com.fitcoach.app.domain.usecase.Goals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep(@StringRes val titleRes: Int) {
    LANGUAGE(R.string.onboarding_step_language),
    NAME(R.string.onboarding_step_name),
    SEX_AGE(R.string.onboarding_step_sex_age),
    BODY(R.string.onboarding_step_body),
    GOAL(R.string.onboarding_step_goal),
    LEVEL(R.string.onboarding_step_level),
    DAYS(R.string.onboarding_step_days),
    RESTRICTIONS(R.string.onboarding_step_restrictions),
    RESULT(R.string.onboarding_step_result);

    val progress: Float get() = (ordinal + 1f) / entries.size
}

data class OnboardingDraft(
    val language: String = BrandConfig.defaultLanguage,
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
    val consent: Boolean = false
) {
    fun toProfile(base: UserProfile): UserProfile = base.copy(
        name = name.trim(),
        sex = sex,
        age = age.toIntOrNull()?.coerceIn(14, 90) ?: 30,
        heightCm = heightCm.toIntOrNull()?.coerceIn(120, 230) ?: 175,
        weightKg = weightKg.replace(',', '.').toFloatOrNull()?.coerceIn(35f, 250f) ?: 75f,
        targetWeightKg = targetWeightKg.replace(',', '.').toFloatOrNull()?.coerceIn(35f, 250f),
        goal = goal,
        level = level,
        daysPerWeek = daysPerWeek,
        restrictions = restrictions,
        language = language
    )
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LANGUAGE,
    val draft: OnboardingDraft = OnboardingDraft(),
    val goals: Goals? = null,
    val recommendation: ProgramRecommendation? = null,
    val saving: Boolean = false
) {
    val canProceed: Boolean
        get() = when (step) {
            OnboardingStep.NAME -> draft.name.isNotBlank()
            OnboardingStep.SEX_AGE -> draft.age.toIntOrNull()?.let { it in 14..90 } == true
            OnboardingStep.BODY -> draft.heightCm.toIntOrNull()?.let { it in 120..230 } == true &&
                draft.weightKg.replace(',', '.').toFloatOrNull()?.let { it in 35f..250f } == true
            OnboardingStep.RESULT -> draft.consent
            else -> true
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val userRepo: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepo.getProfile()?.let { p ->
                if (p.name.isNotBlank()) {
                    _state.update {
                        it.copy(draft = OnboardingDraft(
                            language = p.language, name = p.name, sex = p.sex, age = p.age.toString(),
                            heightCm = p.heightCm.toString(), weightKg = p.weightKg.toString(),
                            targetWeightKg = p.targetWeightKg?.toString() ?: "", goal = p.goal, level = p.level,
                            daysPerWeek = p.daysPerWeek, restrictions = p.restrictions
                        ))
                    }
                }
            }
        }
    }

    fun update(transform: (OnboardingDraft) -> OnboardingDraft) = _state.update { it.copy(draft = transform(it.draft)) }

    fun selectLanguage(lang: String) {
        update { it.copy(language = lang) }
        applyLocale(lang)
    }

    fun toggleRestriction(r: Restriction) = update { d ->
        val set = d.restrictions.toMutableSet()
        if (!set.remove(r)) set.add(r)
        d.copy(restrictions = set)
    }

    fun next() {
        val s = _state.value
        if (!s.canProceed) return
        val idx = s.step.ordinal
        if (idx >= OnboardingStep.entries.lastIndex) return
        val nextStep = OnboardingStep.entries[idx + 1]
        if (nextStep == OnboardingStep.RESULT) {
            val profile = s.draft.toProfile(UserProfile())
            _state.update {
                it.copy(step = nextStep, goals = GoalCalculator.calculate(profile), recommendation = ProgramCatalog.recommendation(profile))
            }
        } else {
            _state.update { it.copy(step = nextStep) }
        }
    }

    fun back() {
        val idx = _state.value.step.ordinal
        if (idx > 0) _state.update { it.copy(step = OnboardingStep.entries[idx - 1]) }
    }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        if (!s.draft.consent || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val base = userRepo.getProfile() ?: UserProfile()
            val profile = s.draft.toProfile(base)
            val goals = s.goals ?: GoalCalculator.calculate(profile)
            val program = (s.recommendation ?: ProgramCatalog.recommendation(profile)).program
            userRepo.saveProfile(
                GoalCalculator.applyTo(profile, goals).copy(
                    programKey = program.key,
                    programStartDate = System.currentTimeMillis(),
                    onboardingCompleted = true
                )
            )
            userRepo.setFlag(FLAG_HEALTH_CONSENT, true)
            onDone()
        }
    }

    companion object {
        const val FLAG_HEALTH_CONSENT = "health_data_consent"

        fun applyLocale(lang: String) {
            runCatching { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang)) }
        }
    }
}
