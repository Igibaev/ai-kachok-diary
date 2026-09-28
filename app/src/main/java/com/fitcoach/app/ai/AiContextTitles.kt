package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.model.WorkoutPlan

/**
 * Человеческие названия программ и тренировок для промпта.
 * Интегратор: при появлении ProgramCatalog (агент P) заменить на его данные.
 */
object AiContextTitles {

    fun programTitle(programKey: String): String = when (programKey) {
        "START_3", "BEGINNER_3" -> "Старт"
        "SLIM_3" -> "Стройность и тонус"
        "MUSCLE_4" -> "Масса и сила"
        else -> programKey.ifBlank { "Программа клуба" }
    }

    fun workoutTitle(workout: Workout): String = when {
        workout.planKey.startsWith("ACTIVITY_") -> when (workout.planKey.removePrefix("ACTIVITY_")) {
            "GROUP" -> "Групповое занятие"
            "CARDIO" -> "Кардио"
            else -> "Другая активность"
        }
        else -> WorkoutPlan.getTemplate(workout.programKey, workout.planKey)?.phaseName
            ?: workout.phaseName.ifBlank { workout.planKey }
    }
}
