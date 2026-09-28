package com.fitcoach.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.NutritionSummary
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.ActivityType
import com.fitcoach.app.domain.program.NextWorkout
import com.fitcoach.app.domain.program.Program
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.StreakCalculator
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WaterRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.util.DayBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DashboardUiState(
    val profile: UserProfile = UserProfile(),
    val program: Program? = null,
    val next: NextWorkout? = null,
    /** Незавершённая тренировка программы за сегодня — «Продолжить». */
    val inProgress: Workout? = null,
    val completedToday: Boolean = false,
    val nutritionSummary: NutritionSummary = NutritionSummary(),
    val waterToday: Int = 0,
    val completedWorkouts: Int = 0,
    val completedInProgram: Int = 0,
    val currentWeek: Int = 1,
    val streakWeeks: Int = 0,
    val workoutsThisWeek: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val nutritionRepo: NutritionRepository,
    private val waterRepo: WaterRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()

    /** Защита от двойного нажатия «Начать» — иначе создаются две тренировки и два перехода. */
    private var startingWorkout = false

    /** Начало текущего дня; переоценивается раз в минуту, чтобы не «застрять» на вчера после полуночи. */
    private val today = flow {
        while (true) {
            emit(DayBounds.startOfDay())
            delay(60_000)
        }
    }.distinctUntilChanged()

    init {
        observe()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observe() {
        viewModelScope.launch {
            today.flatMapLatest { dayStart ->
                combine(
                    userRepo.observeProfile(),
                    workoutRepo.getAllWorkouts(),
                    nutritionRepo.getNutritionSummaryForDate(dayStart),
                    waterRepo.getTotalForDate(dayStart)
                ) { profile, workouts, nutrition, water ->
                    val p = profile ?: UserProfile()
                    val program = ProgramCatalog.getOrDefault(p.programKey)
                    val completed = workouts.filter { it.isCompleted }
                    val inProgram = ProgramCatalog.completedInProgram(program, completed)
                    val next = ProgramCatalog.nextWorkout(program, inProgram, p.daysPerWeek)
                    val (dayStartMs, dayEndMs) = DayBounds.of(dayStart)
                    val todayWorkouts = workouts.filter { it.date in dayStartMs until dayEndMs }
                    DashboardUiState(
                        profile = p,
                        program = program,
                        next = next,
                        // Не только за сегодня: начатая вечером и не завершённая тренировка после 00:00
                        // иначе «терялась» — создавалась вторая, а старая навсегда оставалась «не завершена».
                        inProgress = workouts.firstOrNull { !it.isCompleted && !ProgramCatalog.isActivity(it) },
                        completedToday = todayWorkouts.any { it.isCompleted },
                        nutritionSummary = nutrition,
                        waterToday = water,
                        completedWorkouts = completed.size,
                        completedInProgram = inProgram,
                        currentWeek = next.weekNumber,
                        streakWeeks = StreakCalculator.currentStreakWeeks(completed, p.daysPerWeek),
                        workoutsThisWeek = StreakCalculator.workoutsThisWeek(completed),
                        isLoading = false
                    )
                }
            }.collect { _state.value = it }
        }
    }

    /** «Начать»/«Продолжить»: создаёт тренировку из следующего шаблона (с учётом ограничений) или открывает незавершённую. */
    fun startNextWorkout(onWorkoutReady: (String) -> Unit) {
        if (startingWorkout) return
        startingWorkout = true
        viewModelScope.launch {
            try {
                startNextWorkoutInternal(onWorkoutReady)
            } finally {
                startingWorkout = false
            }
        }
    }

    private suspend fun startNextWorkoutInternal(onWorkoutReady: (String) -> Unit) {
        val s = _state.value
        s.inProgress?.let { onWorkoutReady(it.id); return }

        val profile = userRepo.getProfile() ?: return
        val program = ProgramCatalog.getOrDefault(profile.programKey)
        val next = s.next ?: ProgramCatalog.nextWorkout(program, 0, profile.daysPerWeek)
        val template = ProgramCatalog.applyRestrictions(next.template, profile.restrictions)

        val workout = Workout(
            id = UUID.randomUUID().toString(),
            date = System.currentTimeMillis(),
            programKey = program.key,
            planKey = template.key,
            phaseName = template.phaseName,
            weekNumber = next.weekNumber,
            isCompleted = false,
            durationMinutes = null,
            painLevel = 0,
            rpe = 0,
            notes = ""
        )
        val id = workoutRepo.saveWorkout(workout)
        val sets = template.exercises.flatMap { ex ->
            ex.sets.mapIndexed { i, st ->
                ExerciseSet(
                    id = UUID.randomUUID().toString(), workoutId = id,
                    exerciseId = ex.id, exerciseName = ex.name, setNumber = i + 1,
                    targetReps = st.reps, actualReps = null, targetWeight = st.weight, actualWeight = null,
                    isDone = false, restSeconds = ex.restSeconds
                )
            }
        }
        workoutRepo.insertSets(sets)
        onWorkoutReady(id)
    }

    /** «Отметить активность»: групповое / кардио / другое, сразу как выполненная. */
    fun logActivity(type: ActivityType, minutes: Int) {
        viewModelScope.launch {
            val s = _state.value
            val workout = Workout(
                id = UUID.randomUUID().toString(),
                date = System.currentTimeMillis(),
                programKey = s.program?.key ?: s.profile.programKey,
                planKey = type.planKey,
                phaseName = type.title,
                weekNumber = s.currentWeek,
                isCompleted = true,
                durationMinutes = minutes.coerceIn(5, 300),
                painLevel = 0,
                rpe = 0,
                notes = ""
            )
            workoutRepo.saveWorkout(workout)
        }
    }
}
