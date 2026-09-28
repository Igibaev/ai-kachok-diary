package com.fitcoach.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.program.ActivityType
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.l10n.tr

/** Локализованное название тренировки для истории/деталей/шапки. */
@Composable
fun workoutTitle(workout: Workout): String = WorkoutTitles.titleFor(workout).tr()

/** «Старт · Фаза 2 · Неделя 5» или «Вне программы» — собирается на UI-слое из ресурсов. */
@Composable
fun workoutSubtitle(workout: Workout): String {
    if (ActivityType.fromPlanKey(workout.planKey) != null) return stringResource(R.string.workout_subtitle_outside)
    val program = ProgramCatalog.get(workout.programKey)
    val phase = program?.phaseForWeek(workout.weekNumber)
    val parts = buildList {
        if (program != null) add(program.title.tr())
        if (phase != null) add(stringResource(R.string.workout_subtitle_phase, phase.index))
        add(stringResource(R.string.workout_subtitle_week, workout.weekNumber))
    }
    return parts.joinToString(" · ")
}
