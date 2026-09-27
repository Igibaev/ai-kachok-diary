package com.fitcoach.app.presentation.screens.workout.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WorkoutPhase {
    object Warmup : WorkoutPhase()
    object ExerciseList : WorkoutPhase()
    data class RestTimer(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val nextExerciseName: String,
        val nextSetNumber: Int
    ) : WorkoutPhase()
    object Cooldown : WorkoutPhase()
    data class BackPainCheck(val workoutId: String) : WorkoutPhase()
    object Summary : WorkoutPhase()
}

data class ExerciseUiState(
    val exerciseId: String,
    val name: String,
    val sets: List<ExerciseSet>,
    val tip: String = "",
    val youtubeQuery: String = ""
)

data class WorkoutActiveUiState(
    val workout: Workout? = null,
    val phase: WorkoutPhase = WorkoutPhase.Warmup,
    val exercises: List<ExerciseUiState> = emptyList(),
    val warmupItems: List<WarmupItem> = emptyList(),
    val cooldownItems: List<CooldownItem> = emptyList(),
    val totalSets: Int = 0,
    val doneSets: Int = 0,
    val isLoading: Boolean = true,
    val elapsedSeconds: Long = 0
)

/**
 * Базовая версия (агент «Программы» заменит на полную: ввод веса/повторов, отмена сета, bottom-sheet таймер).
 * Исправлено относительно исходника: загрузка тренировки по id, честный таймер длительности.
 */
@HiltViewModel
class WorkoutActiveViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle["workoutId"])

    private val _state = MutableStateFlow(WorkoutActiveUiState())
    val state = _state.asStateFlow()

    private var restJob: Job? = null
    private var elapsedJob: Job? = null

    init {
        loadWorkout()
        startElapsedTimer()
    }

    private fun startElapsedTimer() {
        elapsedJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            val workout = workoutRepo.getWorkoutById(workoutId)
            val template = workout?.let { WorkoutPlan.getTemplate(it.programKey, it.planKey) }
            workoutRepo.getSetsForWorkout(workoutId).collect { sets ->
                val groupedSets = sets.groupBy { it.exerciseId }
                val exercises = template?.exercises?.map { ex ->
                    ExerciseUiState(
                        exerciseId = ex.id,
                        name = ex.name,
                        sets = groupedSets[ex.id] ?: emptyList(),
                        tip = ex.tip,
                        youtubeQuery = ex.youtubeSearchQuery
                    )
                } ?: groupedSets.map { (id, list) -> ExerciseUiState(id, list.first().exerciseName, list) }

                _state.update {
                    it.copy(
                        workout = workout,
                        exercises = exercises,
                        warmupItems = template?.warmup ?: emptyList(),
                        cooldownItems = template?.cooldown ?: emptyList(),
                        totalSets = sets.size,
                        doneSets = sets.count { s -> s.isDone },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun startExercises() {
        _state.update { it.copy(phase = WorkoutPhase.ExerciseList) }
    }

    fun markSetDone(set: ExerciseSet, actualReps: Int? = null, actualWeight: Float? = null) {
        viewModelScope.launch {
            workoutRepo.markSetDone(set, actualReps ?: set.targetReps, actualWeight)

            val currentState = _state.value
            val currentExercise = currentState.exercises.find { it.exerciseId == set.exerciseId }
            val nextSetInExercise = currentExercise?.sets?.firstOrNull { !it.isDone && it.id != set.id }
            val nextExercise = if (nextSetInExercise == null) {
                val idx = currentState.exercises.indexOfFirst { it.exerciseId == set.exerciseId }
                currentState.exercises.drop(idx + 1).firstOrNull { ex -> ex.sets.any { !it.isDone } }
            } else null

            val remainingAfter = currentState.exercises.sumOf { ex -> ex.sets.count { !it.isDone && it.id != set.id } }
            if (remainingAfter == 0) {
                _state.update { it.copy(phase = WorkoutPhase.Cooldown) }
                return@launch
            }

            val restSeconds = set.restSeconds.takeIf { it > 0 } ?: 90
            val nextName = nextSetInExercise?.exerciseName ?: nextExercise?.name ?: "Заминка"
            val nextSetNum = nextSetInExercise?.setNumber ?: 1
            startRestTimer(restSeconds, nextName, nextSetNum)
        }
    }

    private fun startRestTimer(totalSeconds: Int, nextExerciseName: String, nextSetNumber: Int) {
        restJob?.cancel()
        _state.update { it.copy(phase = WorkoutPhase.RestTimer(totalSeconds, totalSeconds, nextExerciseName, nextSetNumber)) }
        restJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                _state.update { s -> s.copy(phase = WorkoutPhase.RestTimer(remaining, totalSeconds, nextExerciseName, nextSetNumber)) }
            }
            if (isActive) checkAllDone()
        }
    }

    fun skipRest() {
        restJob?.cancel()
        checkAllDone()
    }

    private fun checkAllDone() {
        val allDone = _state.value.exercises.all { ex -> ex.sets.all { it.isDone } }
        _state.update { it.copy(phase = if (allDone) WorkoutPhase.Cooldown else WorkoutPhase.ExerciseList) }
    }

    fun finishCooldown() {
        _state.update { it.copy(phase = WorkoutPhase.BackPainCheck(workoutId)) }
    }

    fun submitBackPain(painLevel: Int) {
        viewModelScope.launch {
            val workout = _state.value.workout ?: return@launch
            elapsedJob?.cancel()
            val updated = workout.copy(
                isCompleted = true,
                painLevel = painLevel,
                durationMinutes = (_state.value.elapsedSeconds / 60).toInt().coerceAtLeast(1)
            )
            workoutRepo.updateWorkout(updated)
            _state.update { it.copy(workout = updated, phase = WorkoutPhase.Summary) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        restJob?.cancel()
        elapsedJob?.cancel()
    }
}
