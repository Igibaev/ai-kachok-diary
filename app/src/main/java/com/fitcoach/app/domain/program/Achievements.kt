package com.fitcoach.app.domain.program

/** Бейджи за прогресс. Чистая функция — используется на экране итогов и на экране «Прогресс». */
object Achievements {

    data class Badge(
        val id: String,
        val title: String,
        val emoji: String,
        val description: String
    )

    private data class Rule(val badge: Badge, val check: (completed: Int, streakWeeks: Int, volumeKg: Int) -> Boolean)

    private val rules: List<Rule> = listOf(
        Rule(Badge("first_workout", "Первый шаг", "🥇", "Первая тренировка выполнена")) { c, _, _ -> c >= 1 },
        Rule(Badge("workouts_5", "Разогрев", "🔥", "5 тренировок")) { c, _, _ -> c >= 5 },
        Rule(Badge("workouts_10", "Десятка", "🔟", "10 тренировок")) { c, _, _ -> c >= 10 },
        Rule(Badge("workouts_25", "Постоянство", "🏅", "25 тренировок")) { c, _, _ -> c >= 25 },
        Rule(Badge("workouts_50", "Полсотни", "🏆", "50 тренировок")) { c, _, _ -> c >= 50 },
        Rule(Badge("workouts_100", "Сотня", "👑", "100 тренировок")) { c, _, _ -> c >= 100 },
        Rule(Badge("streak_2", "Две недели", "📆", "2 недели подряд без пропусков")) { _, s, _ -> s >= 2 },
        Rule(Badge("streak_4", "Месяц в ритме", "🗓️", "4 недели подряд")) { _, s, _ -> s >= 4 },
        Rule(Badge("streak_8", "Железная привычка", "⚙️", "8 недель подряд")) { _, s, _ -> s >= 8 },
        Rule(Badge("streak_12", "Программа пройдена", "🎓", "12 недель подряд")) { _, s, _ -> s >= 12 },
        Rule(Badge("volume_1t", "Тонна", "🐘", "1 000 кг поднято суммарно")) { _, _, v -> v >= 1_000 },
        Rule(Badge("volume_10t", "Десять тонн", "🚛", "10 000 кг поднято")) { _, _, v -> v >= 10_000 },
        Rule(Badge("volume_50t", "Полсотни тонн", "🚀", "50 000 кг поднято")) { _, _, v -> v >= 50_000 },
        Rule(Badge("volume_100t", "Сто тонн", "🌋", "100 000 кг поднято")) { _, _, v -> v >= 100_000 }
    )

    val all: List<Badge> = rules.map { it.badge }

    fun evaluate(completedCount: Int, streakWeeks: Int, totalVolumeKg: Int): List<Badge> =
        rules.filter { it.check(completedCount, streakWeeks, totalVolumeKg) }.map { it.badge }

    /** Бейджи, полученные при переходе from → to. */
    fun newlyEarned(
        completedBefore: Int, streakBefore: Int, volumeBefore: Int,
        completedAfter: Int, streakAfter: Int, volumeAfter: Int
    ): List<Badge> {
        val before = evaluate(completedBefore, streakBefore, volumeBefore).map { it.id }.toSet()
        return evaluate(completedAfter, streakAfter, volumeAfter).filter { it.id !in before }
    }
}
