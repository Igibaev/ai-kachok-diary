package com.fitcoach.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DashboardUiState(
    val profile: UserProfile = UserProfile(),
    val todayWorkout: Workout? = null,
    val todayWorkoutKey: String? = null,
    val nutritionSummary: NutritionSummary = NutritionSummary(),
    val waterToday: Int = 0,
    val completedWorkouts: Int = 0,
    val currentWeek: Int = 1,
    val streakDays: Int = 0,
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

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        val todayMillis = System.currentTimeMillis()

        viewModelScope.launch {
            combine(
                userRepo.observeProfile(),
                workoutRepo.observeWorkoutForDate(todayMillis),
                nutritionRepo.getNutritionSummaryForDate(todayMillis),
                waterRepo.getTotalForDate(todayMillis),
                workoutRepo.getCompletedWorkoutCount()
            ) { profile, todayWorkout, nutrition, water, completedCount ->
                val p = profile ?: UserProfile()
                val startDate = LocalDate.ofEpochDay(p.programStartDate / 86400000L)
                val currentWeek = WorkoutPlan.getCurrentWeek(startDate)
                val todayKey = WorkoutPlan.getWorkoutForDate(LocalDate.now(), startDate)

                DashboardUiState(
                    profile = p,
                    todayWorkout = todayWorkout,
                    todayWorkoutKey = todayKey,
                    nutritionSummary = nutrition,
                    waterToday = water,
                    completedWorkouts = completedCount,
                    currentWeek = currentWeek,
                    streakDays = 0,
                    isLoading = false
                )
            }.collect { _state.value = it }
        }
    }

    fun startTodayWorkout(onWorkoutCreated: (String) -> Unit) {
        viewModelScope.launch {
            val profile = userRepo.getProfile() ?: return@launch
            val startDate = LocalDate.ofEpochDay(profile.programStartDate / 86400000L)
            val today = LocalDate.now()
            val planKey = WorkoutPlan.getWorkoutForDate(today, startDate) ?: return@launch
            val template = WorkoutPlan.getTemplate(planKey) ?: return@launch
            val currentWeek = WorkoutPlan.getCurrentWeek(startDate)

            val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            var workout = workoutRepo.getWorkoutForDate(todayMillis)

            if (workout == null) {
                val newWorkout = Workout(
                    id = java.util.UUID.randomUUID().toString(),
                    date = todayMillis,
                    planKey = planKey,
                    phaseName = template.phaseName,
                    weekNumber = currentWeek,
                    isCompleted = false,
                    durationMinutes = null,
                    backPainLevel = 0,
                    notes = ""
                )
                val workoutId = workoutRepo.saveWorkout(newWorkout)

                val sets = template.exercises.flatMap { exercise ->
                    exercise.sets.mapIndexed { index, setTemplate ->
                        ExerciseSet(
                            id = java.util.UUID.randomUUID().toString(),
                            workoutId = workoutId,
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            setNumber = index + 1,
                            targetReps = setTemplate.reps,
                            actualReps = null,
                            targetWeight = setTemplate.weight,
                            actualWeight = null,
                            isDone = false,
                            restSeconds = exercise.restSeconds
                        )
                    }
                }
                workoutRepo.insertSets(sets)
                onWorkoutCreated(workoutId)
            } else {
                onWorkoutCreated(workout.id)
            }
        }
    }
}
