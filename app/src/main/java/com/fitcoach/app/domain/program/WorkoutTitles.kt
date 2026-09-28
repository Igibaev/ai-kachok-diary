package com.fitcoach.app.domain.program

import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.Workout

/** Типы активностей вне программы («Отметить активность»). */
enum class ActivityType(val planKey: String, val title: String, val emoji: String) {
    GROUP("ACTIVITY_GROUP", "Групповое занятие", "🧘"),
    CARDIO("ACTIVITY_CARDIO", "Кардио", "🏃"),
    OTHER("ACTIVITY_OTHER", "Другая активность", "⚡");

    companion object {
        fun fromPlanKey(key: String): ActivityType? = entries.firstOrNull { it.planKey == key }
    }
}

/** Человеческие названия тренировок для истории, напоминаний и карточек. */
object WorkoutTitles {

    fun titleFor(workout: Workout): String {
        ActivityType.fromPlanKey(workout.planKey)?.let { return it.title }
        return ProgramCatalog.findTemplate(workout.programKey, workout.planKey)?.title
            ?: workout.phaseName.substringAfter("— ", workout.phaseName).ifBlank { workout.planKey }
    }

    /** Суммарный объём: Σ actualWeight × actualReps по выполненным подходам, кг. */
    fun volumeKg(sets: List<ExerciseSet>): Int =
        sets.filter { it.isDone }.sumOf { s ->
            val w = s.actualWeight ?: 0f
            val r = s.actualReps ?: 0
            (w * r).toDouble()
        }.toInt()

    fun formatWeight(w: Float): String =
        if (w % 1f == 0f) w.toInt().toString() else String.format(java.util.Locale.US, "%.1f", w)

    fun formatDuration(totalSeconds: Long): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }
}
