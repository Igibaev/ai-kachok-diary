package com.fitcoach.app.data.local.db.dao

import androidx.room.*
import com.fitcoach.app.data.local.db.entity.ExerciseSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSetDao {
    @Query("SELECT * FROM exercise_sets WHERE workoutId = :workoutId ORDER BY exerciseId, setNumber")
    fun getSetsForWorkout(workoutId: String): Flow<List<ExerciseSetEntity>>

    @Query("SELECT * FROM exercise_sets WHERE workoutId = :workoutId ORDER BY exerciseId, setNumber")
    suspend fun getSetsForWorkoutSync(workoutId: String): List<ExerciseSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: ExerciseSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<ExerciseSetEntity>)

    @Update
    suspend fun updateSet(set: ExerciseSetEntity)

    @Query("DELETE FROM exercise_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: String)

    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId AND isDone = 1 ORDER BY rowid DESC LIMIT :limit")
    suspend fun getLastDoneSetsForExercise(exerciseId: String, limit: Int = 5): List<ExerciseSetEntity>

    /** «Прошлый раз» по названию: id упражнений уникальны на шаблон/фазу (s1a_1 vs s3a_1), а упражнение то же.
     *  Сортировка по дате тренировки, а не по rowid. */
    @Query(
        "SELECT s.* FROM exercise_sets s INNER JOIN workouts w ON w.id = s.workoutId " +
            "WHERE s.exerciseName = :exerciseName AND s.isDone = 1 AND s.workoutId != :excludeWorkoutId " +
            "ORDER BY w.date DESC, s.setNumber DESC LIMIT :limit"
    )
    suspend fun getLastDoneSetsForExerciseName(exerciseName: String, excludeWorkoutId: String, limit: Int = 5): List<ExerciseSetEntity>
}
