package com.fitcoach.app.presentation.screens.workout.active

import android.os.CountDownTimer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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

@HiltViewModel
class WorkoutActiveViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle["workoutId"])

    private val _state = MutableStateFlow(WorkoutActiveUiState())
    val state = _state.asStateFlow()

    private var restTimer: CountDownTimer? = null

    init {
        loadWorkout()
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            workoutRepo.getSetsForWorkout(workoutId).collect { sets ->
                val workout = workoutRepo.getWorkoutForDate(System.currentTimeMillis())
                val template = workout?.planKey?.let { WorkoutPlan.getTemplate(it) }

                val groupedSets = sets.groupBy { it.exerciseId }
                val exercises = template?.exercises?.map { ex ->
                    ExerciseUiState(
                        exerciseId = ex.id,
                        name = ex.name,
                        sets = groupedSets[ex.id] ?: emptyList(),
                        tip = ex.tip,
                        youtubeQuery = ex.youtubeSearchQuery
                    )
                } ?: emptyList()

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
            workoutRepo.markSetDone(set, actualReps, actualWeight)

            // Find next set to determine rest time
            val currentState = _state.value
            val currentExercise = currentState.exercises.find { it.exerciseId == set.exerciseId }
            val nextSetInExercise = currentExercise?.sets?.firstOrNull { !it.isDone && it.id != set.id }

            val nextExercise = if (nextSetInExercise == null) {
                val currentExIdx = currentState.exercises.indexOfFirst { it.exerciseId == set.exerciseId }
                currentState.exercises.getOrNull(currentExIdx + 1)
            } else null

            val restSeconds = currentExercise?.sets?.firstOrNull()?.restSeconds ?: 90
            val nextName = nextSetInExercise?.exerciseName ?: nextExercise?.name ?: "Заминка"
            val nextSetNum = nextSetInExercise?.setNumber ?: 1

            startRestTimer(restSeconds, nextName, nextSetNum)
        }
    }

    private fun startRestTimer(totalSeconds: Int, nextExerciseName: String, nextSetNumber: Int) {
        restTimer?.cancel()
        _state.update {
            it.copy(phase = WorkoutPhase.RestTimer(totalSeconds, totalSeconds, nextExerciseName, nextSetNumber))
        }

        restTimer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt()
                _state.update { state ->
                    state.copy(phase = WorkoutPhase.RestTimer(remaining, totalSeconds, nextExerciseName, nextSetNumber))
                }
            }

            override fun onFinish() {
                checkAllDone()
            }
        }.start()
    }

    fun skipRest() {
        restTimer?.cancel()
        checkAllDone()
    }

    private fun checkAllDone() {
        val state = _state.value
        val allDone = state.exercises.all { ex -> ex.sets.all { it.isDone } }
        _state.update {
            it.copy(
                phase = if (allDone) WorkoutPhase.Cooldown else WorkoutPhase.ExerciseList
            )
        }
    }

    fun finishCooldown() {
        _state.update { it.copy(phase = WorkoutPhase.BackPainCheck(workoutId)) }
    }

    fun submitBackPain(painLevel: Int) {
        viewModelScope.launch {
            val workout = _state.value.workout ?: return@launch
            val updatedWorkout = workout.copy(
                isCompleted = true,
                backPainLevel = painLevel,
                durationMinutes = (_state.value.elapsedSeconds / 60).toInt()
            )
            workoutRepo.updateWorkout(updatedWorkout)
            _state.update { it.copy(phase = WorkoutPhase.Summary) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        restTimer?.cancel()
    }
}
