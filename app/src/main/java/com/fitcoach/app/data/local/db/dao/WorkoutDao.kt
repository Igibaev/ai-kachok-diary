package com.fitcoach.app.data.local.db.dao

import androidx.room.*
import com.fitcoach.app.data.local.db.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE date >= :startOfDay AND date < :endOfDay LIMIT 1")
    suspend fun getWorkoutForDate(startOfDay: Long, endOfDay: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE date >= :startOfDay AND date < :endOfDay LIMIT 1")
    fun observeWorkoutForDate(startOfDay: Long, endOfDay: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkoutById(id: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentWorkouts(limit: Int = 10): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE date >= :since ORDER BY date ASC")
    suspend fun getWorkoutsSince(since: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE isCompleted = 1 ORDER BY date ASC")
    fun observeCompletedWorkouts(): Flow<List<WorkoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM workouts WHERE isCompleted = 1")
    fun getCompletedWorkoutCount(): Flow<Int>

    @Query("SELECT * FROM workouts WHERE weekNumber = :week ORDER BY date ASC")
    suspend fun getWorkoutsByWeek(week: Int): List<WorkoutEntity>
}
