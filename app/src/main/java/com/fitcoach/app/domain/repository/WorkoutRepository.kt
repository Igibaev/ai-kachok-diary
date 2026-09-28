package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Workout
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun getAllWorkouts(): Flow<List<Workout>>
    suspend fun getWorkoutForDate(dateMillis: Long): Workout?
    fun observeWorkoutForDate(dateMillis: Long): Flow<Workout?>
    suspend fun getWorkoutById(id: String): Workout?
    fun observeWorkoutById(id: String): Flow<Workout?>
    suspend fun getRecentWorkouts(limit: Int = 10): List<Workout>
    suspend fun getWorkoutsSince(sinceMillis: Long): List<Workout>
    fun observeCompletedWorkouts(): Flow<List<Workout>>
    suspend fun saveWorkout(workout: Workout): String
    suspend fun updateWorkout(workout: Workout)
    suspend fun deleteWorkout(workout: Workout)
    suspend fun deleteAllWorkouts()
    fun getCompletedWorkoutCount(): Flow<Int>
    fun getSetsForWorkout(workoutId: String): Flow<List<ExerciseSet>>
    suspend fun getSetsForWorkoutSync(workoutId: String): List<ExerciseSet>
    suspend fun markSetDone(set: ExerciseSet, actualReps: Int?, actualWeight: Float?)
    suspend fun updateSet(set: ExerciseSet)
    suspend fun insertSets(sets: List<ExerciseSet>)
    /** Последний выполненный подход по упражнению (для подстановки прошлого веса). */
    suspend fun getLastResultForExercise(exerciseId: String): ExerciseSet?
    /** То же по названию упражнения из других тренировок — переживает смену фазы (новые exerciseId). */
    suspend fun getLastResultForExerciseName(exerciseName: String, excludeWorkoutId: String): ExerciseSet?
}
