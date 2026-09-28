package com.fitcoach.app.domain.program

import com.fitcoach.app.domain.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Стрик по неделям (Пн–Вс). Неделя «в зачёте», если в ней было не меньше
 * `max(1, daysPerWeek - 1)` выполненных тренировок (один пропуск прощается).
 * Текущая неделя стрик не ломает, пока не закончилась: если в ней уже есть тренировка — прибавляется.
 */
object StreakCalculator {

    fun currentStreakWeeks(
        completed: List<Workout>,
        daysPerWeek: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val perWeek = completed.filter { it.isCompleted }
            .groupingBy { weekStart(it.date, zone) }
            .eachCount()
        if (perWeek.isEmpty()) return 0

        val minPerWeek = (daysPerWeek - 1).coerceAtLeast(1)
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        var streak = 0
        var week = thisWeek
        // Текущая неделя: считается, если есть хоть одна тренировка; иначе просто пропускаем её.
        if ((perWeek[week] ?: 0) >= 1) streak++
        week = week.minusWeeks(1)
        while ((perWeek[week] ?: 0) >= minPerWeek) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    fun workoutsThisWeek(
        completed: List<Workout>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return completed.count { it.isCompleted && weekStart(it.date, zone) == thisWeek }
    }

    /** Есть ли выполненная тренировка сегодня. */
    fun hasWorkoutToday(
        completed: List<Workout>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = completed.any { it.isCompleted && toLocalDate(it.date, zone) == today }

    /** Дней подряд с тренировкой (для чипа на главной). */
    fun consecutiveDays(
        completed: List<Workout>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val days = completed.filter { it.isCompleted }.map { toLocalDate(it.date, zone) }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }

    fun weeksBetween(startMillis: Long, endMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.WEEKS.between(toLocalDate(startMillis, zone), toLocalDate(endMillis, zone)).toInt()

    private fun weekStart(millis: Long, zone: ZoneId): LocalDate =
        toLocalDate(millis, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun toLocalDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
