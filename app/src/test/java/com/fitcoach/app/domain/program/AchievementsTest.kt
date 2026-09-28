package com.fitcoach.app.domain.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    @Test
    fun `nothing earned at zero`() {
        assertTrue(Achievements.evaluate(0, 0, 0).isEmpty())
    }

    @Test
    fun `first workout earns first badge only`() {
        val badges = Achievements.evaluate(1, 1, 0).map { it.id }
        assertEquals(listOf("first_workout"), badges)
    }

    @Test
    fun `thresholds are cumulative`() {
        val ids = Achievements.evaluate(10, 4, 10_000).map { it.id }
        assertTrue(ids.containsAll(listOf("first_workout", "workouts_5", "workouts_10", "streak_2", "streak_4", "volume_1t", "volume_10t")))
        assertTrue("workouts_25" !in ids)
        assertTrue("streak_8" !in ids)
        assertTrue("volume_50t" !in ids)
    }

    @Test
    fun `all badges are unique and evaluate is subset of all`() {
        val ids = Achievements.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val earned = Achievements.evaluate(1000, 52, 1_000_000)
        assertEquals(Achievements.all, earned)
    }

    @Test
    fun `newlyEarned returns only the difference`() {
        val new = Achievements.newlyEarned(4, 1, 900, 5, 2, 1_100).map { it.id }
        assertEquals(setOf("workouts_5", "streak_2", "volume_1t"), new.toSet())
        assertTrue(Achievements.newlyEarned(5, 2, 1_100, 5, 2, 1_100).isEmpty())
    }
}
