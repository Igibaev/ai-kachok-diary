package com.fitcoach.app.domain.program

import com.fitcoach.app.domain.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class StreakCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Almaty")
    // Среда
    private val today: LocalDate = LocalDate.of(2026, 9, 23)

    private fun at(date: LocalDate, completed: Boolean = true) = Workout(
        id = date.toString() + System.nanoTime(),
        date = date.atTime(LocalTime.of(19, 0)).atZone(zone).toInstant().toEpochMilli(),
        programKey = "START_3", planKey = "S1_A", phaseName = "", weekNumber = 1,
        isCompleted = completed, durationMinutes = 45, painLevel = 0, notes = ""
    )

    @Test
    fun `no workouts means zero streak`() {
        assertEquals(0, StreakCalculator.currentStreakWeeks(emptyList(), 3, today, zone))
        assertEquals(0, StreakCalculator.workoutsThisWeek(emptyList(), today, zone))
    }

    private fun d(day: Int) = LocalDate.of(2026, 9, day)

    @Test
    fun `three full weeks including current week`() {
        val list = listOf(
            // текущая неделя (Пн 21.09 – Вс 27.09)
            at(d(22)),
            // прошлая неделя (14–20): 3 тренировки
            at(d(14)), at(d(16)), at(d(18)),
            // позапрошлая (7–13): 2 — один пропуск прощается при 3/нед
            at(d(7)), at(d(9))
        )
        assertEquals(3, StreakCalculator.currentStreakWeeks(list, 3, today, zone))
        assertEquals(1, StreakCalculator.workoutsThisWeek(list, today, zone))
    }

    @Test
    fun `current week without workouts does not break streak`() {
        val list = listOf(at(d(14)), at(d(16)), at(d(7)), at(d(9)))
        assertEquals(2, StreakCalculator.currentStreakWeeks(list, 3, today, zone))
    }

    @Test
    fun `gap week breaks streak`() {
        val list = listOf(
            at(today), // текущая
            // прошлая (14–20) — пусто
            at(d(7)), at(d(9))
        )
        assertEquals(1, StreakCalculator.currentStreakWeeks(list, 3, today, zone))
    }

    @Test
    fun `four days per week requires at least three in past weeks`() {
        val twoLastWeek = listOf(at(d(14)), at(d(16)))
        assertEquals(0, StreakCalculator.currentStreakWeeks(twoLastWeek, 4, today, zone))
        val threeLastWeek = twoLastWeek + at(d(18))
        assertEquals(1, StreakCalculator.currentStreakWeeks(threeLastWeek, 4, today, zone))
    }

    @Test
    fun `incomplete workouts are ignored`() {
        val list = listOf(at(today, completed = false), at(d(16), completed = false))
        assertEquals(0, StreakCalculator.currentStreakWeeks(list, 3, today, zone))
        assertEquals(0, StreakCalculator.workoutsThisWeek(list, today, zone))
    }

    @Test
    fun `week boundary respects Monday start in local zone`() {
        val sunday = LocalDate.of(2026, 9, 20)
        val monday = LocalDate.of(2026, 9, 21)
        val list = listOf(at(sunday), at(monday))
        assertEquals(1, StreakCalculator.workoutsThisWeek(list, today, zone))
        assertTrue(StreakCalculator.hasWorkoutToday(listOf(at(today)), today, zone))
        assertFalse(StreakCalculator.hasWorkoutToday(listOf(at(today.minusDays(1))), today, zone))
    }

    @Test
    fun `consecutive days`() {
        val list = listOf(at(today), at(today.minusDays(1)), at(today.minusDays(2)), at(today.minusDays(5)))
        assertEquals(3, StreakCalculator.consecutiveDays(list, today, zone))
        assertEquals(2, StreakCalculator.consecutiveDays(listOf(at(today.minusDays(1)), at(today.minusDays(2))), today, zone))
    }
}
