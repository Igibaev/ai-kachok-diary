package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.WorkoutTitles

/** Человеческие названия программ и тренировок для промпта — из ProgramCatalog. */
object AiContextTitles {

    fun programTitle(programKey: String): String =
        ProgramCatalog.get(programKey)?.title ?: ProgramCatalog.getOrDefault(programKey).title

    fun workoutTitle(workout: Workout): String = WorkoutTitles.titleFor(workout)
}
