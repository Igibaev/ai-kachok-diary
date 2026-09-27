package com.fitcoach.app.data.repository

import com.fitcoach.app.data.local.db.dao.ExerciseSetDao
import com.fitcoach.app.data.local.db.dao.WorkoutDao
import com.fitcoach.app.data.local.db.entity.ExerciseSetEntity
import com.fitcoach.app.data.local.db.entity.WorkoutEntity
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.util.DayBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseSetDao: ExerciseSetDao
) : WorkoutRepository {

    override fun getAllWorkouts(): Flow<List<Workout>> =
        workoutDao.getAllWorkouts().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getWorkoutForDate(dateMillis: Long): Workout? {
        val (start, end) = DayBounds.of(dateMillis)
        return workoutDao.getWorkoutForDate(start, end)?.toDomain()
    }

    override fun observeWorkoutForDate(dateMillis: Long): Flow<Workout?> {
        val (start, end) = DayBounds.of(dateMillis)
        return workoutDao.observeWorkoutForDate(start, end).map { it?.toDomain() }
    }

    override suspend fun getWorkoutById(id: String): Workout? =
        workoutDao.getWorkoutById(id)?.toDomain()

    override fun observeWorkoutById(id: String): Flow<Workout?> =
        workoutDao.observeWorkoutById(id).map { it?.toDomain() }

    override suspend fun getRecentWorkouts(limit: Int): List<Workout> =
        workoutDao.getRecentWorkouts(limit).map { it.toDomain() }

    override suspend fun getWorkoutsSince(sinceMillis: Long): List<Workout> =
        workoutDao.getWorkoutsSince(sinceMillis).map { it.toDomain() }

    override fun observeCompletedWorkouts(): Flow<List<Workout>> =
        workoutDao.observeCompletedWorkouts().map { list -> list.map { it.toDomain() } }

    override suspend fun saveWorkout(workout: Workout): String {
        val entity = workout.toEntity()
        workoutDao.insertWorkout(entity)
        return entity.id
    }

    override suspend fun updateWorkout(workout: Workout) =
        workoutDao.updateWorkout(workout.toEntity())

    override suspend fun deleteWorkout(workout: Workout) =
        workoutDao.deleteWorkout(workout.toEntity())

    override suspend fun deleteAllWorkouts() = workoutDao.deleteAll()

    override fun getCompletedWorkoutCount(): Flow<Int> =
        workoutDao.getCompletedWorkoutCount()

    override fun getSetsForWorkout(workoutId: String): Flow<List<ExerciseSet>> =
        exerciseSetDao.getSetsForWorkout(workoutId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSetsForWorkoutSync(workoutId: String): List<ExerciseSet> =
        exerciseSetDao.getSetsForWorkoutSync(workoutId).map { it.toDomain() }

    override suspend fun markSetDone(set: ExerciseSet, actualReps: Int?, actualWeight: Float?) {
        val entity = set.toEntity().copy(isDone = true, actualReps = actualReps, actualWeight = actualWeight)
        exerciseSetDao.updateSet(entity)
    }

    override suspend fun updateSet(set: ExerciseSet) = exerciseSetDao.updateSet(set.toEntity())

    override suspend fun insertSets(sets: List<ExerciseSet>) {
        exerciseSetDao.insertSets(sets.map { it.toEntity() })
    }

    override suspend fun getLastResultForExercise(exerciseId: String): ExerciseSet? =
        exerciseSetDao.getLastDoneSetsForExercise(exerciseId, 1).firstOrNull()?.toDomain()
}

fun WorkoutEntity.toDomain() = Workout(
    id = id, date = date, programKey = programKey, planKey = planKey, phaseName = phaseName,
    weekNumber = weekNumber, isCompleted = isCompleted, durationMinutes = durationMinutes,
    painLevel = painLevel, rpe = rpe, notes = notes
)

fun Workout.toEntity() = WorkoutEntity(
    id = id, date = date, programKey = programKey, planKey = planKey, phaseName = phaseName,
    weekNumber = weekNumber, isCompleted = isCompleted, durationMinutes = durationMinutes,
    painLevel = painLevel, rpe = rpe, notes = notes
)

fun ExerciseSetEntity.toDomain() = ExerciseSet(
    id = id, workoutId = workoutId, exerciseId = exerciseId, exerciseName = exerciseName,
    setNumber = setNumber, targetReps = targetReps, actualReps = actualReps,
    targetWeight = targetWeight, actualWeight = actualWeight, isDone = isDone, restSeconds = restSeconds
)

fun ExerciseSet.toEntity() = ExerciseSetEntity(
    id = id, workoutId = workoutId, exerciseId = exerciseId, exerciseName = exerciseName,
    setNumber = setNumber, targetReps = targetReps, actualReps = actualReps,
    targetWeight = targetWeight, actualWeight = actualWeight, isDone = isDone, restSeconds = restSeconds
)
