package com.fitcoach.app.domain.program

import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction

/** Подсказка по нагрузке одного подхода. Вес не в кг — словесно («лёгкий», «средний», «тяжёлый», «б/в», «RPE 7»). */
data class SetTemplate(
    val reps: Int,
    val weight: String
)

data class ExerciseTemplate(
    val id: String,
    val name: String,
    val sets: List<SetTemplate>,
    val restSeconds: Int,
    val tip: String = "",
    val youtubeSearchQuery: String = "$name техника",
    val muscleGroup: String = "",
    /** Две замены на случай «тренажёр занят». */
    val alternatives: List<String> = emptyList(),
    /** При этих ограничениях упражнение автоматически меняется на первую альтернативу. */
    val avoidFor: Set<Restriction> = emptySet()
)

data class WarmupItem(val name: String, val durationSeconds: Int)
data class CooldownItem(val name: String, val durationSeconds: Int)

data class WorkoutTemplate(
    /** Ключ тренировки внутри программы — сохраняется в Workout.planKey. */
    val key: String,
    /** Человеческое название: «Верх тела», «Full-body A». */
    val title: String,
    /** Название фазы для истории: «Фаза I — База». */
    val phaseName: String,
    val exercises: List<ExerciseTemplate>,
    val warmup: List<WarmupItem> = DefaultRoutines.warmup,
    val cooldown: List<CooldownItem> = DefaultRoutines.cooldown
) {
    val totalSets: Int get() = exercises.sumOf { it.sets.size }

    /** Оценка длительности: ~45 сек на подход + отдых, плюс 8 мин разминки/заминки. */
    val estimatedMinutes: Int
        get() {
            val work = exercises.sumOf { ex -> ex.sets.size * (45 + ex.restSeconds) }
            return (work / 60 + 8).coerceAtLeast(15)
        }
}

data class ProgramPhase(
    /** 1..3 */
    val index: Int,
    val name: String,
    val weeks: IntRange,
    val templates: List<WorkoutTemplate>
)

data class Program(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val emoji: String,
    val level: Level,
    val daysPerWeek: Int,
    val focus: List<String>,
    val phases: List<ProgramPhase>
) {
    val totalWeeks: Int get() = phases.maxOfOrNull { it.weeks.last } ?: 12

    fun phaseForWeek(week: Int): ProgramPhase =
        phases.firstOrNull { week in it.weeks } ?: phases.last()

    fun findTemplate(planKey: String): WorkoutTemplate? =
        phases.asSequence().flatMap { it.templates.asSequence() }.firstOrNull { it.key == planKey }
}

/** Результат `ProgramCatalog.nextWorkout`. */
data class NextWorkout(
    val template: WorkoutTemplate,
    val weekNumber: Int,
    val phase: ProgramPhase,
    /** Порядковый номер тренировки в программе (с 1). */
    val ordinal: Int
)

data class ProgramRecommendation(
    val program: Program,
    /** Беременность / послеродовой период — рекомендуем консультацию тренера клуба. */
    val needsTrainerConsult: Boolean
)

object DefaultRoutines {
    val warmup = listOf(
        WarmupItem("Ходьба или велотренажёр в лёгком темпе", 180),
        WarmupItem("Круговые движения руками и плечами", 60),
        WarmupItem("Наклоны и повороты корпуса", 60),
        WarmupItem("Вращение таза, махи ногами", 60),
        WarmupItem("Приседания без веса", 60)
    )

    val cooldown = listOf(
        CooldownItem("Растяжка квадрицепса", 60),
        CooldownItem("Растяжка задней поверхности бедра", 60),
        CooldownItem("Поза ребёнка", 60),
        CooldownItem("Кошка-корова", 60),
        CooldownItem("Растяжка грудных у стены", 60)
    )

    val cardioCooldown = listOf(
        CooldownItem("Ходьба в спокойном темпе", 120),
        CooldownItem("Растяжка икр и бёдер", 60),
        CooldownItem("Растяжка спины лёжа", 60),
        CooldownItem("Дыхание 4-7-8", 60)
    )
}

/** Компактные конструкторы для описания программ. */
internal fun s(reps: Int, weight: String) = SetTemplate(reps, weight)

internal fun sets(count: Int, reps: Int, weight: String): List<SetTemplate> = List(count) { SetTemplate(reps, weight) }

internal fun ex(
    id: String,
    name: String,
    muscle: String,
    sets: List<SetTemplate>,
    rest: Int,
    tip: String,
    alts: List<String>,
    avoid: Set<Restriction> = emptySet(),
    yt: String = "$name техника"
) = ExerciseTemplate(
    id = id, name = name, sets = sets, restSeconds = rest, tip = tip,
    youtubeSearchQuery = yt, muscleGroup = muscle, alternatives = alts, avoidFor = avoid
)

/** Подсказки нагрузки. */
internal object W {
    const val BW = "б/в"
    const val LIGHT = "лёгкий"
    const val MEDIUM = "средний"
    const val HEAVY = "тяжёлый"
    const val RPE7 = "RPE 7"
    const val RPE8 = "RPE 8"
    const val RPE9 = "RPE 9"
}
