package com.fitcoach.app.domain.model

data class Workout(
    val id: String,
    val date: Long,
    val programKey: String,
    val planKey: String,
    val phaseName: String,
    val weekNumber: Int,
    val isCompleted: Boolean,
    val durationMinutes: Int?,
    /** Дискомфорт/боль после тренировки, 0–10. */
    val painLevel: Int,
    /** Субъективная тяжесть тренировки (RPE), 1–10; 0 = не указано. */
    val rpe: Int = 0,
    val notes: String,
    val sets: List<ExerciseSet> = emptyList()
)
