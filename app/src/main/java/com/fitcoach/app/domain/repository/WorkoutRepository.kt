package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Workout
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun getAllWorkouts(): Flow<List<Workout>>
    suspend fun getWorkoutForDate(dateMillis: Long): Workout?
    fun observeWorkoutForDate(dateMillis: Long): Flow<Workout?>
    suspend fun getRecentWorkouts(limit: Int = 10): List<Workout>
    suspend fun saveWorkout(workout: Workout): String
    suspend fun updateWorkout(workout: Workout)
    fun getCompletedWorkoutCount(): Flow<Int>
    fun getSetsForWorkout(workoutId: String): Flow<List<ExerciseSet>>
    suspend fun markSetDone(set: ExerciseSet, actualReps: Int?, actualWeight: Float?)
    suspend fun insertSets(sets: List<ExerciseSet>)
}
