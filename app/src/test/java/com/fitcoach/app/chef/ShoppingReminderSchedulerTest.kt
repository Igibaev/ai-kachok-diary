package com.fitcoach.app.chef

import com.fitcoach.app.workers.ShoppingReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ShoppingReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Almaty")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun zdt(millis: Long): ZonedDateTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)

    // 2026-10-03 — суббота.
    private val saturdayNoon = at(2026, 10, 3, 12, 0)

    @Test
    fun `same day later time triggers today`() {
        val next = ShoppingReminderScheduler.nextTrigger(6, 18, 30, saturdayNoon, zone)
        assertEquals(at(2026, 10, 3, 18, 30), next)
    }

    @Test
    fun `same day earlier time rolls to next week`() {
        val next = ShoppingReminderScheduler.nextTrigger(6, 9, 0, saturdayNoon, zone)
        assertEquals(at(2026, 10, 10, 9, 0), next)
    }

    @Test
    fun `exact now rolls to next week (strictly after now)`() {
        val next = ShoppingReminderScheduler.nextTrigger(6, 12, 0, saturdayNoon, zone)
        assertEquals(at(2026, 10, 10, 12, 0), next)
    }

    @Test
    fun `other weekday picks the nearest future occurrence`() {
        // Понедельник после субботы 3 октября — 5 октября.
        assertEquals(at(2026, 10, 5, 7, 15), ShoppingReminderScheduler.nextTrigger(1, 7, 15, saturdayNoon, zone))
        // Пятница — 9 октября (через 6 дней).
        assertEquals(at(2026, 10, 9, 20, 0), ShoppingReminderScheduler.nextTrigger(5, 20, 0, saturdayNoon, zone))
        // Воскресенье — завтра.
        assertEquals(at(2026, 10, 4, 10, 0), ShoppingReminderScheduler.nextTrigger(7, 10, 0, saturdayNoon, zone))
    }

    @Test
    fun `result is always in the future and on the requested weekday`() {
        var now = at(2026, 1, 1, 0, 0)
        val weekMs = 7 * ShoppingReminderScheduler.DAY_MS
        for (step in 0 until 60) {
            for (day in 1..7) {
                val next = ShoppingReminderScheduler.nextTrigger(day, 18, 0, now, zone)
                assertTrue(next > now)
                assertTrue(next - now <= weekMs)
                assertEquals(DayOfWeek.of(day), zdt(next).dayOfWeek)
                assertEquals(18, zdt(next).hour)
            }
            now += 11 * 60 * 60 * 1000L + 7 * 60 * 1000L // шаг 11 ч 7 мин — покрывает все времена суток
        }
    }

    @Test
    fun `out of range values are clamped`() {
        val next = ShoppingReminderScheduler.nextTrigger(9, 25, 70, saturdayNoon, zone)
        val z = zdt(next)
        assertEquals(DayOfWeek.SUNDAY, z.dayOfWeek)
        assertEquals(23, z.hour)
        assertEquals(59, z.minute)
    }

    @Test
    fun `month and year boundaries`() {
        // Четверг 31 декабря 2026, 23:00 → пятница 1 января 2027.
        val next = ShoppingReminderScheduler.nextTrigger(5, 8, 0, at(2026, 12, 31, 23, 0), zone)
        assertEquals(at(2027, 1, 1, 8, 0), next)
    }
}
