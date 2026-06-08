package com.fitcoach.app.domain.model

data class Workout(
    val id: String,
    val date: Long,
    val planKey: String,
    val phaseName: String,
    val weekNumber: Int,
    val isCompleted: Boolean,
    val durationMinutes: Int?,
    val backPainLevel: Int,
    val notes: String,
    val sets: List<ExerciseSet> = emptyList()
)
