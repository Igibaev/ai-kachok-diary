package com.fitcoach.app.ai.chef

import com.fitcoach.app.ai.AiErrors
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.MealPlanPrefs
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.model.PlanGoals
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.MealPlanRepository
import com.fitcoach.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Генерация плана питания живёт вне ViewModel экрана: запрос к повару идёт 30–90 секунд, и пользователь может
 * уйти с экрана — план всё равно сохранится в Room, а экран при возврате покажет прогресс или результат.
 */
@Singleton
class MealPlanGenerator @Inject constructor(
    private val selector: AiChefSelector,
    private val mealPlanRepo: MealPlanRepository,
    private val userRepo: UserRepository
) {
    sealed interface State {
        data object Idle : State
        data object Running : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(days: Int, mealsPerDay: Int, prefs: MealPlanPrefs) {
        if (_state.value is State.Running) return
        _state.value = State.Running
        job = scope.launch {
            var locale = BrandConfig.defaultLanguage
            try {
                val profile = userRepo.getProfile() ?: UserProfile()
                locale = profile.language.ifBlank { BrandConfig.defaultLanguage }
                val request = MealPlanRequest(days, mealsPerDay, PlanGoals.from(profile), profile, prefs)
                val mode = selector.currentMode()
                val result = selector.current().generateMealPlan(request, locale)
                result.fold(
                    onSuccess = { plan ->
                        mealPlanRepo.savePlan(plan, request, isDemo = mode == AiMode.DEMO)
                        _state.value = State.Idle
                    },
                    onFailure = { e -> _state.value = State.Failed(e.message ?: AiErrors.serverUnavailable(locale)) }
                )
            } catch (e: CancellationException) {
                _state.value = State.Idle
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(AiErrors.serverUnavailable(locale))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    fun clearError() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }
}
