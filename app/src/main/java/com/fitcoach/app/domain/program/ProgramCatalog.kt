package com.fitcoach.app.domain.program

import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout

/**
 * Каталог программ и гибкий график: тренировки идут по кругу внутри фазы,
 * без привязки к дню недели. Неделя = выполнено / daysPerWeek + 1 (максимум 12).
 */
object ProgramCatalog {

    const val START_3 = "START_3"
    const val SLIM_3 = "SLIM_3"
    const val MUSCLE_4 = "MUSCLE_4"
    const val TOTAL_WEEKS = 12

    /** Ключи тренировок-активностей вне программы («Отметить активность»). */
    const val ACTIVITY_PREFIX = "ACTIVITY_"

    val programs: List<Program> = listOf(ProgramStart.program, ProgramSlim.program, ProgramMuscle.program)

    /** Старые ключи из прежних версий профиля. */
    private val aliases = mapOf("BEGINNER_3" to START_3, "" to START_3)

    fun get(key: String): Program? {
        val resolved = aliases[key] ?: key
        return programs.firstOrNull { it.key == resolved }
    }

    fun getOrDefault(key: String): Program = get(key) ?: ProgramStart.program

    fun findTemplate(programKey: String, planKey: String): WorkoutTemplate? =
        get(programKey)?.findTemplate(planKey)
            ?: programs.asSequence().mapNotNull { it.findTemplate(planKey) }.firstOrNull()

    fun recommendation(profile: UserProfile): ProgramRecommendation {
        val pregnancy = Restriction.PREGNANCY_POSTPARTUM in profile.restrictions
        val program = when {
            pregnancy -> ProgramStart.program
            profile.goal == Goal.BACK_HEALTH -> ProgramStart.program
            profile.level == Level.BEGINNER && profile.goal != Goal.FAT_LOSS && profile.goal != Goal.TONE -> ProgramStart.program
            profile.goal == Goal.FAT_LOSS || profile.goal == Goal.TONE -> ProgramSlim.program
            profile.goal == Goal.MUSCLE_GAIN -> ProgramMuscle.program
            else -> ProgramStart.program
        }
        return ProgramRecommendation(program, needsTrainerConsult = pregnancy)
    }

    fun recommend(profile: UserProfile): Program = recommendation(profile).program

    fun isActivity(workout: Workout): Boolean = workout.planKey.startsWith(ACTIVITY_PREFIX)

    /** Выполненные тренировки именно этой программы (без активностей). */
    fun completedInProgram(program: Program, workouts: List<Workout>): Int =
        workouts.count { it.isCompleted && !isActivity(it) && get(it.programKey)?.key == program.key }

    fun weekFor(completedCount: Int, daysPerWeek: Int): Int =
        (completedCount / daysPerWeek.coerceAtLeast(1) + 1).coerceIn(1, TOTAL_WEEKS)

    fun nextWorkout(program: Program, completedCount: Int, daysPerWeek: Int = program.daysPerWeek): NextWorkout {
        val week = weekFor(completedCount, daysPerWeek)
        val phase = program.phaseForWeek(week)
        // Внутри фазы идём по кругу; смещение считаем от начала фазы, чтобы новая фаза начиналась с первой тренировки.
        val phaseStartCount = (phase.weeks.first - 1) * daysPerWeek.coerceAtLeast(1)
        val offset = (completedCount - phaseStartCount).coerceAtLeast(0)
        val template = phase.templates[offset % phase.templates.size]
        return NextWorkout(template, week, phase, completedCount + 1)
    }

    fun nextWorkout(program: Program, completedWorkouts: List<Workout>, daysPerWeek: Int = program.daysPerWeek): NextWorkout =
        nextWorkout(program, completedInProgram(program, completedWorkouts), daysPerWeek)

    /** Замена упражнений, противопоказанных при ограничениях пользователя, на безопасную альтернативу. */
    fun applyRestrictions(template: WorkoutTemplate, restrictions: Set<Restriction>): WorkoutTemplate {
        if (restrictions.isEmpty()) return template
        return template.copy(exercises = template.exercises.map { ex ->
            val hit = ex.avoidFor.any { it in restrictions }
            val alt = if (hit) safeAlternative(ex, restrictions) else null
            if (alt != null) ex.copy(
                id = alternativeId(ex.id, ex.alternatives.indexOf(alt) + 1),
                name = alt,
                youtubeSearchQuery = "$alt техника",
                tip = "Замена из-за ограничения: ${ex.name}"
            ) else ex
        })
    }

    /**
     * Первая альтернатива, не противоречащая ограничению: при «Плечи» AI-тренер запрещает жимы над головой,
     * поэтому «Жим гантелей сидя» → не «Жим в тренажёре на плечи», а подъём/разведение.
     */
    private fun safeAlternative(ex: ExerciseTemplate, restrictions: Set<Restriction>): String? {
        if (Restriction.SHOULDERS in restrictions && Restriction.SHOULDERS in ex.avoidFor) {
            ex.alternatives.firstOrNull { !isPress(it) }?.let { return it }
        }
        return ex.alternatives.firstOrNull()
    }

    private fun isPress(name: String): Boolean = name.contains("жим", ignoreCase = true)

    /** Идентификатор замены: `<baseId>~<n>`; `n = 0` — исходное упражнение. */
    fun alternativeId(baseId: String, index: Int): String =
        if (index <= 0) baseIdOf(baseId) else "${baseIdOf(baseId)}~$index"

    fun baseIdOf(exerciseId: String): String = exerciseId.substringBefore('~')

    /** Все варианты названия для слота упражнения: исходное + альтернативы. */
    fun variantsFor(ex: ExerciseTemplate): List<String> = listOf(ex.name) + ex.alternatives
}
