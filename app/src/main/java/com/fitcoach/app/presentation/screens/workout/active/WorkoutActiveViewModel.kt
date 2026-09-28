package com.fitcoach.app.presentation.screens.workout.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.Achievements
import com.fitcoach.app.domain.program.CooldownItem
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.StreakCalculator
import com.fitcoach.app.domain.program.WarmupItem
import com.fitcoach.app.domain.program.WorkoutTemplate
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WorkoutPhase { Warmup, Exercises, Cooldown, Feedback, Summary }

/** Таймер отдыха — показывается поверх списка как ModalBottomSheet. */
data class RestState(
    val remainingSeconds: Int,
    val totalSeconds: Int,
    /** null — «Следующее упражнение» (подставляет UI). */
    val nextExerciseName: String?,
    val nextSetNumber: Int,
    /** Дошёл до нуля — UI даёт вибрацию и сигнал. */
    val finished: Boolean = false
)

/** Диалог ввода подхода. */
data class SetInput(
    val set: ExerciseSet,
    val lastResult: ExerciseSet?,
    val defaultReps: Int,
    val defaultWeight: Float?
)

data class ExerciseUiState(
    /** Идентификатор слота в шаблоне (без суффикса замены). */
    val slotId: String,
    val exerciseId: String,
    val name: String,
    val sets: List<ExerciseSet>,
    val tip: String = "",
    val youtubeQuery: String = "",
    val muscleGroup: String = "",
    /** Варианты замены (кроме текущего названия). */
    val alternatives: List<String> = emptyList()
)

/** Личный рекорд: максимальный вес в упражнении выше прошлого лучшего. */
data class PersonalRecord(val exerciseName: String, val weightKg: Float)

data class WorkoutSummary(
    val title: String,
    val durationMinutes: Int,
    val doneSets: Int,
    val totalSets: Int,
    val volumeKg: Int,
    val records: List<PersonalRecord>,
    val streakWeeks: Int,
    val workoutsThisWeek: Int,
    val completedTotal: Int,
    val newBadges: List<Achievements.Badge>,
    val userName: String
)

data class WorkoutActiveUiState(
    val workout: Workout? = null,
    val title: String = "",
    val phase: WorkoutPhase = WorkoutPhase.Warmup,
    val exercises: List<ExerciseUiState> = emptyList(),
    val warmupItems: List<WarmupItem> = emptyList(),
    val cooldownItems: List<CooldownItem> = emptyList(),
    val totalSets: Int = 0,
    val doneSets: Int = 0,
    val isLoading: Boolean = true,
    val elapsedSeconds: Long = 0,
    val rest: RestState? = null,
    val setInput: SetInput? = null,
    val askPain: Boolean = false,
    val summary: WorkoutSummary? = null
)

@HiltViewModel
class WorkoutActiveViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val userRepo: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle["workoutId"])

    private val _state = MutableStateFlow(WorkoutActiveUiState())
    val state = _state.asStateFlow()

    private var restJob: Job? = null
    private var elapsedJob: Job? = null
    private val startedAt = System.currentTimeMillis()
    private var template: WorkoutTemplate? = null
    private var profile: UserProfile = UserProfile()

    /** «Прошлый раз» по exerciseId: запрашиваем один раз, иначе после первого подхода в этой тренировке
     *  последним «выполненным» становится наш же подход и подсказка пропадает. */
    private val lastResultCache = HashMap<String, ExerciseSet?>()

    init {
        loadWorkout()
        startElapsedTimer()
    }

    private fun startElapsedTimer() {
        elapsedJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000) }
                delay(1000)
            }
        }
    }

    private fun loadWorkout() {
        viewModelScope.launch {
            profile = userRepo.getProfile() ?: UserProfile()
            val workout = workoutRepo.getWorkoutById(workoutId)
            template = workout?.let { ProgramCatalog.findTemplate(it.programKey, it.planKey) }
            val title = workout?.let { WorkoutTitles.titleFor(it) } ?: ""
            _state.update {
                it.copy(
                    workout = workout, title = title, askPain = Restriction.BACK in profile.restrictions,
                    warmupItems = template?.warmup ?: emptyList(),
                    cooldownItems = template?.cooldown ?: emptyList()
                )
            }
            workoutRepo.getSetsForWorkout(workoutId).collect { sets ->
                val exercises = buildExercises(sets)
                _state.update {
                    it.copy(
                        exercises = exercises,
                        totalSets = sets.size,
                        doneSets = sets.count { s -> s.isDone },
                        isLoading = false,
                        // Если тренировку продолжают — сразу к упражнениям.
                        phase = if (it.phase == WorkoutPhase.Warmup && sets.any { s -> s.isDone }) WorkoutPhase.Exercises else it.phase
                    )
                }
            }
        }
    }

    private fun buildExercises(sets: List<ExerciseSet>): List<ExerciseUiState> {
        val t = template
        val bySlot = sets.groupBy { ProgramCatalog.baseIdOf(it.exerciseId) }
        val ordered: List<Pair<String, List<ExerciseSet>>> = if (t != null) {
            val fromTemplate = t.exercises.mapNotNull { ex -> bySlot[ex.id]?.let { ex.id to it } }
            val extra = bySlot.filterKeys { k -> t.exercises.none { it.id == k } }.map { it.key to it.value }
            fromTemplate + extra
        } else bySlot.map { it.key to it.value }

        return ordered.map { (slotId, slotSets) ->
            val sorted = slotSets.sortedBy { it.setNumber }
            val current = sorted.firstOrNull { !it.isDone } ?: sorted.last()
            val tpl = t?.exercises?.firstOrNull { it.id == slotId }
            val variants = tpl?.let { ProgramCatalog.variantsFor(it) } ?: emptyList()
            ExerciseUiState(
                slotId = slotId,
                exerciseId = current.exerciseId,
                name = current.exerciseName,
                sets = sorted,
                tip = tpl?.tip ?: "",
                youtubeQuery = tpl?.let { if (current.exerciseName == it.name) it.youtubeSearchQuery else "${current.exerciseName} техника" }
                    ?: "${current.exerciseName} техника",
                muscleGroup = tpl?.muscleGroup ?: "",
                alternatives = variants.filter { it != current.exerciseName }
            )
        }
    }

    fun startExercises() = _state.update { it.copy(phase = WorkoutPhase.Exercises) }

    // ---------- Подходы ----------

    fun openSetInput(set: ExerciseSet) {
        viewModelScope.launch {
            val last = lastResultCache.getOrPut(set.exerciseId) {
                workoutRepo.getLastResultForExercise(set.exerciseId)?.takeIf { it.workoutId != workoutId }
            }
            // Если в этой тренировке уже есть выполненный подход этого упражнения — берём его вес.
            val inWorkout = _state.value.exercises.firstOrNull { it.exerciseId == set.exerciseId }
                ?.sets?.lastOrNull { it.isDone && it.actualWeight != null }
            val weight = inWorkout?.actualWeight ?: last?.actualWeight
            _state.update {
                it.copy(setInput = SetInput(set, last, defaultReps = set.targetReps, defaultWeight = weight))
            }
        }
    }

    fun dismissSetInput() = _state.update { it.copy(setInput = null) }

    fun confirmSet(reps: Int, weight: Float?) {
        val input = _state.value.setInput ?: return
        _state.update { it.copy(setInput = null) }
        viewModelScope.launch {
            val set = input.set
            workoutRepo.markSetDone(set, reps.coerceAtLeast(0), weight)

            val current = _state.value
            val remainingAfter = current.exercises.sumOf { ex -> ex.sets.count { !it.isDone && it.id != set.id } }
            if (remainingAfter == 0) {
                _state.update { it.copy(phase = WorkoutPhase.Cooldown) }
                return@launch
            }

            val exercise = current.exercises.firstOrNull { it.slotId == ProgramCatalog.baseIdOf(set.exerciseId) }
            val nextInExercise = exercise?.sets?.firstOrNull { !it.isDone && it.id != set.id }
            val nextExercise = if (nextInExercise == null) {
                val idx = current.exercises.indexOfFirst { it.slotId == exercise?.slotId }
                current.exercises.drop(idx + 1).firstOrNull { ex -> ex.sets.any { !it.isDone } }
            } else null

            val restSeconds = set.restSeconds.takeIf { it > 0 } ?: 90
            startRest(
                totalSeconds = restSeconds,
                nextExerciseName = nextInExercise?.exerciseName ?: nextExercise?.name,
                nextSetNumber = nextInExercise?.setNumber ?: 1
            )
        }
    }

    fun undoSet(set: ExerciseSet) {
        viewModelScope.launch {
            workoutRepo.updateSet(set.copy(isDone = false, actualReps = null, actualWeight = null))
            if (_state.value.phase == WorkoutPhase.Cooldown) _state.update { it.copy(phase = WorkoutPhase.Exercises) }
        }
    }

    /** «Тренажёр занят»: меняем название и id у невыполненных подходов слота. */
    fun replaceExercise(exercise: ExerciseUiState, newName: String) {
        viewModelScope.launch {
            val tpl = template?.exercises?.firstOrNull { it.id == exercise.slotId }
            val variants = tpl?.let { ProgramCatalog.variantsFor(it) } ?: emptyList()
            val idx = variants.indexOf(newName).coerceAtLeast(0)
            val newId = ProgramCatalog.alternativeId(exercise.slotId, idx)
            exercise.sets.filter { !it.isDone }.forEach { s ->
                workoutRepo.updateSet(s.copy(exerciseId = newId, exerciseName = newName))
            }
        }
    }

    // ---------- Отдых ----------

    private fun startRest(totalSeconds: Int, nextExerciseName: String?, nextSetNumber: Int) {
        restJob?.cancel()
        _state.update { it.copy(rest = RestState(totalSeconds, totalSeconds, nextExerciseName, nextSetNumber)) }
        restJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val rest = _state.value.rest ?: break
                val remaining = rest.remainingSeconds - 1
                if (remaining <= 0) {
                    _state.update { it.copy(rest = rest.copy(remainingSeconds = 0, finished = true)) }
                    delay(1500)
                    _state.update { it.copy(rest = null) }
                    break
                }
                _state.update { it.copy(rest = rest.copy(remainingSeconds = remaining)) }
            }
        }
    }

    fun adjustRest(deltaSeconds: Int) {
        _state.update { s ->
            val rest = s.rest ?: return@update s
            val remaining = (rest.remainingSeconds + deltaSeconds).coerceAtLeast(5)
            val total = maxOf(rest.totalSeconds, remaining)
            s.copy(rest = rest.copy(remainingSeconds = remaining, totalSeconds = total))
        }
    }

    fun skipRest() {
        restJob?.cancel()
        _state.update { it.copy(rest = null) }
    }

    // ---------- Завершение ----------

    fun finishExercisesEarly() = _state.update { it.copy(phase = WorkoutPhase.Cooldown) }

    fun finishCooldown() = _state.update { it.copy(phase = WorkoutPhase.Feedback) }

    fun submitFeedback(rpe: Int, painLevel: Int) {
        viewModelScope.launch {
            val workout = _state.value.workout ?: return@launch
            elapsedJob?.cancel()
            val durationMinutes = ((System.currentTimeMillis() - startedAt) / 60_000).toInt().coerceAtLeast(1)
            val updated = workout.copy(isCompleted = true, rpe = rpe, painLevel = painLevel, durationMinutes = durationMinutes)
            workoutRepo.updateWorkout(updated)
            val summary = buildSummary(updated)
            _state.update { it.copy(workout = updated, summary = summary, phase = WorkoutPhase.Summary) }
        }
    }

    private suspend fun buildSummary(workout: Workout): WorkoutSummary {
        val sets = workoutRepo.getSetsForWorkoutSync(workoutId)
        val volume = WorkoutTitles.volumeKg(sets)
        val completed = workoutRepo.observeCompletedWorkouts().first()
        val previous = completed.filter { it.id != workoutId }

        // Рекорды: максимальный вес в упражнении выше прошлого лучшего (по той же тренировке программы).
        val bestBefore = HashMap<String, Float>()
        var volumeBefore = 0
        for (w in previous) {
            val ws = workoutRepo.getSetsForWorkoutSync(w.id)
            volumeBefore += WorkoutTitles.volumeKg(ws)
            if (w.planKey == workout.planKey) {
                ws.filter { it.isDone && it.actualWeight != null }.forEach { s ->
                    val key = s.exerciseId
                    bestBefore[key] = maxOf(bestBefore[key] ?: 0f, s.actualWeight!!)
                }
            }
        }
        val records = sets.filter { it.isDone && it.actualWeight != null }
            .groupBy { it.exerciseId }
            .mapNotNull { (id, list) ->
                val prev = bestBefore[id] ?: return@mapNotNull null
                val now = list.maxOf { it.actualWeight!! }
                if (now > prev) PersonalRecord(list.first().exerciseName, now) else null
            }

        val days = profile.daysPerWeek
        val streakBefore = StreakCalculator.currentStreakWeeks(previous, days)
        val streakAfter = StreakCalculator.currentStreakWeeks(completed, days)
        val newBadges = Achievements.newlyEarned(
            previous.size, streakBefore, volumeBefore,
            completed.size, streakAfter, volumeBefore + volume
        )
        return WorkoutSummary(
            title = WorkoutTitles.titleFor(workout),
            durationMinutes = workout.durationMinutes ?: 1,
            doneSets = sets.count { it.isDone },
            totalSets = sets.size,
            volumeKg = volume,
            records = records,
            streakWeeks = streakAfter,
            workoutsThisWeek = StreakCalculator.workoutsThisWeek(completed),
            completedTotal = completed.size,
            newBadges = newBadges,
            userName = profile.name
        )
    }

    override fun onCleared() {
        super.onCleared()
        restJob?.cancel()
        elapsedJob?.cancel()
    }
}
