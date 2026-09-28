package com.fitcoach.app.domain.program

/**
 * Бейджи за тренировки. Минимальная версия от агента «Клуб» (API совпадает с версией агента «Программы»;
 * интегратор оставляет одну).
 */
object Achievements {
    data class Badge(val id: String, val title: String, val emoji: String, val description: String)

    val all: List<Badge> = listOf(
        Badge("first_workout", "Первый шаг", "🎯", "Первая завершённая тренировка"),
        Badge("workouts_5", "Разогрев", "🔥", "5 тренировок"),
        Badge("workouts_10", "В ритме", "⚡", "10 тренировок"),
        Badge("workouts_25", "Стабильность", "🏅", "25 тренировок"),
        Badge("workouts_50", "Полсотни", "🏆", "50 тренировок"),
        Badge("streak_2", "Две недели", "📅", "2 недели подряд без пропусков"),
        Badge("streak_4", "Месяц силы", "💪", "4 недели подряд"),
        Badge("streak_8", "Железная привычка", "🛡️", "8 недель подряд"),
        Badge("volume_5t", "5 тонн", "🏋️", "Суммарно поднято 5 000 кг"),
        Badge("volume_20t", "20 тонн", "🚚", "Суммарно поднято 20 000 кг"),
        Badge("volume_50t", "50 тонн", "🚀", "Суммарно поднято 50 000 кг")
    )

    fun evaluate(completedCount: Int, streakWeeks: Int, totalVolumeKg: Int): List<Badge> = all.filter { badge ->
        when (badge.id) {
            "first_workout" -> completedCount >= 1
            "workouts_5" -> completedCount >= 5
            "workouts_10" -> completedCount >= 10
            "workouts_25" -> completedCount >= 25
            "workouts_50" -> completedCount >= 50
            "streak_2" -> streakWeeks >= 2
            "streak_4" -> streakWeeks >= 4
            "streak_8" -> streakWeeks >= 8
            "volume_5t" -> totalVolumeKg >= 5_000
            "volume_20t" -> totalVolumeKg >= 20_000
            "volume_50t" -> totalVolumeKg >= 50_000
            else -> false
        }
    }
}
