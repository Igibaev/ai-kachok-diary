package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ExerciseSetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val workoutId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val targetReps: Int,
    val actualReps: Int? = null,
    val targetWeight: String,
    val actualWeight: Float? = null,
    val isDone: Boolean = false,
    val restSeconds: Int
)
