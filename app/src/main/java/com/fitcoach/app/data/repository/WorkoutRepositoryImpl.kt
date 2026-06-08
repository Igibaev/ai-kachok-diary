package com.fitcoach.app.data.repository

import com.fitcoach.app.data.local.db.dao.ExerciseSetDao
import com.fitcoach.app.data.local.db.dao.WorkoutDao
import com.fitcoach.app.data.local.db.entity.ExerciseSetEntity
import com.fitcoach.app.data.local.db.entity.WorkoutEntity
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseSetDao: ExerciseSetDao
) : WorkoutRepository {

    override fun getAllWorkouts(): Flow<List<Workout>> =
        workoutDao.getAllWorkouts().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getWorkoutForDate(dateMillis: Long): Workout? {
        val (start, end) = dayBounds(dateMillis)
        return workoutDao.getWorkoutForDate(start, end)?.toDomain()
    }

    override fun observeWorkoutForDate(dateMillis: Long): Flow<Workout?> {
        val (start, end) = dayBounds(dateMillis)
        return workoutDao.observeWorkoutForDate(start, end).map { it?.toDomain() }
    }

    override suspend fun getRecentWorkouts(limit: Int): List<Workout> =
        workoutDao.getRecentWorkouts(limit).map { it.toDomain() }

    override suspend fun saveWorkout(workout: Workout): String {
        val entity = workout.toEntity()
        workoutDao.insertWorkout(entity)
        return entity.id
    }

    override suspend fun updateWorkout(workout: Workout) =
        workoutDao.updateWorkout(workout.toEntity())

    override fun getCompletedWorkoutCount(): Flow<Int> =
        workoutDao.getCompletedWorkoutCount()

    override fun getSetsForWorkout(workoutId: String): Flow<List<ExerciseSet>> =
        exerciseSetDao.getSetsForWorkout(workoutId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun markSetDone(set: ExerciseSet, actualReps: Int?, actualWeight: Float?) {
        val entity = set.toEntity().copy(isDone = true, actualReps = actualReps, actualWeight = actualWeight)
        exerciseSetDao.updateSet(entity)
    }

    override suspend fun insertSets(sets: List<ExerciseSet>) {
        exerciseSetDao.insertSets(sets.map { it.toEntity() })
    }

    private fun dayBounds(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }
}

private fun WorkoutEntity.toDomain() = Workout(
    id = id, date = date, planKey = planKey, phaseName = phaseName,
    weekNumber = weekNumber, isCompleted = isCompleted, durationMinutes = durationMinutes,
    backPainLevel = backPainLevel, notes = notes
)

private fun Workout.toEntity() = WorkoutEntity(
    id = id, date = date, planKey = planKey, phaseName = phaseName,
    weekNumber = weekNumber, isCompleted = isCompleted, durationMinutes = durationMinutes,
    backPainLevel = backPainLevel, notes = notes
)

private fun ExerciseSetEntity.toDomain() = ExerciseSet(
    id = id, workoutId = workoutId, exerciseId = exerciseId, exerciseName = exerciseName,
    setNumber = setNumber, targetReps = targetReps, actualReps = actualReps,
    targetWeight = targetWeight, actualWeight = actualWeight, isDone = isDone, restSeconds = restSeconds
)

private fun ExerciseSet.toEntity() = ExerciseSetEntity(
    id = id, workoutId = workoutId, exerciseId = exerciseId, exerciseName = exerciseName,
    setNumber = setNumber, targetReps = targetReps, actualReps = actualReps,
    targetWeight = targetWeight, actualWeight = actualWeight, isDone = isDone, restSeconds = restSeconds
)
