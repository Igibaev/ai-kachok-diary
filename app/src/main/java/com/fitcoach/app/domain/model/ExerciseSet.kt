package com.fitcoach.app.domain.model

data class ExerciseSet(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val targetReps: Int,
    val actualReps: Int?,
    val targetWeight: String,
    val actualWeight: Float?,
    val isDone: Boolean,
    val restSeconds: Int
)
