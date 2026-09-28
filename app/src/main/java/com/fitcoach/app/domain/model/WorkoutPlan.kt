package com.fitcoach.app.domain.model

import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.WorkoutTemplate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Тонкая обёртка над [ProgramCatalog] для обратной совместимости.
 * Расписание теперь гибкое (см. `ProgramCatalog.nextWorkout`), поэтому привязки к дню недели нет.
 */
object WorkoutPlan {

    fun getTemplate(programKey: String, planKey: String): WorkoutTemplate? =
        ProgramCatalog.findTemplate(programKey, planKey)

    fun getTemplate(planKey: String): WorkoutTemplate? = ProgramCatalog.findTemplate("", planKey)

    /** Календарная неделя программы от даты старта (1..12). Для точной недели используйте `ProgramCatalog.weekFor`. */
    fun getCurrentWeek(startDate: LocalDate, today: LocalDate = LocalDate.now()): Int {
        val days = ChronoUnit.DAYS.between(startDate, today).toInt()
        return (days / 7 + 1).coerceIn(1, ProgramCatalog.TOTAL_WEEKS)
    }

    /** Неделя по количеству выполненных тренировок программы. */
    fun currentWeek(profile: UserProfile, completed: List<Workout>): Int {
        val program = ProgramCatalog.getOrDefault(profile.programKey)
        return ProgramCatalog.weekFor(ProgramCatalog.completedInProgram(program, completed), profile.daysPerWeek)
    }

    fun getPhaseName(programKey: String, weekNumber: Int): String {
        val program = ProgramCatalog.getOrDefault(programKey)
        val phase = program.phaseForWeek(weekNumber)
        return "Фаза ${phase.index} — ${phase.name}"
    }

    fun getPhaseName(weekNumber: Int): String = getPhaseName("", weekNumber)
}
