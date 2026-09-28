package com.fitcoach.app.domain.usecase

import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    private val male = UserProfile(sex = Sex.MALE, age = 30, heightCm = 175, weightKg = 75f, daysPerWeek = 3, goal = Goal.GENERAL_FITNESS)
    private val female = UserProfile(sex = Sex.FEMALE, age = 28, heightCm = 165, weightKg = 60f, daysPerWeek = 3, goal = Goal.GENERAL_FITNESS)

    @Test
    fun `bmr follows Mifflin-St Jeor`() {
        // 10*75 + 6.25*175 - 5*30 + 5 = 1698.75
        assertEquals(1698.75, GoalCalculator.bmr(male), 0.01)
        // 10*60 + 6.25*165 - 5*28 - 161 = 1330.25
        assertEquals(1330.25, GoalCalculator.bmr(female), 0.01)
    }

    @Test
    fun `male general fitness 3 days`() {
        val g = GoalCalculator.calculate(male)
        assertEquals(2336, g.calories) // 1698.75 * 1.375
        assertEquals(120, g.proteinG) // 1.6 g/kg
        assertEquals(78, g.fatG) // 30% kcal / 9
        assertEquals(289, g.carbsG) // (2336 - 480 - 702) / 4 = 288.5 → 289
        assertEquals(2450, g.waterMl) // 33*75 = 2475 → 2450 (шаг 50)
    }

    @Test
    fun `female fat loss cuts 15 percent and uses 2 g protein per kg`() {
        val g = GoalCalculator.calculate(female.copy(goal = Goal.FAT_LOSS))
        // 1330.25 * 1.375 * 0.85 = 1554.7
        assertEquals(1555, g.calories)
        assertEquals(120, g.proteinG)
        assertEquals(52, g.fatG)
        assertEquals(152, g.carbsG)
    }

    @Test
    fun `water is rounded down to 50 ml and clamped`() {
        assertEquals(1950, GoalCalculator.calculate(female).waterMl)
        assertEquals(1500, GoalCalculator.calculate(female.copy(weightKg = 40f)).waterMl)
        assertEquals(4000, GoalCalculator.calculate(male.copy(weightKg = 140f)).waterMl)
    }

    @Test
    fun `muscle gain adds 10 percent, 4 days uses 1_55 factor`() {
        val g = GoalCalculator.calculate(male.copy(goal = Goal.MUSCLE_GAIN, daysPerWeek = 4))
        // 1698.75 * 1.55 * 1.10 = 2896.4
        assertEquals(2896, g.calories)
        assertEquals(150, g.proteinG)
        assertEquals(80, g.fatG) // 25%
    }

    @Test
    fun `calories never drop below sex minimum`() {
        val tiny = UserProfile(sex = Sex.FEMALE, age = 60, heightCm = 150, weightKg = 42f, goal = Goal.FAT_LOSS)
        assertEquals(1200, GoalCalculator.calculate(tiny).calories)
        val tinyMale = UserProfile(sex = Sex.MALE, age = 70, heightCm = 155, weightKg = 48f, goal = Goal.FAT_LOSS)
        assertEquals(1500, GoalCalculator.calculate(tinyMale).calories)
    }

    @Test
    fun `macros add up to calories within rounding`() {
        listOf(male, female, male.copy(goal = Goal.FAT_LOSS), female.copy(goal = Goal.MUSCLE_GAIN, daysPerWeek = 4)).forEach { p ->
            val g = GoalCalculator.calculate(p)
            val kcal = g.proteinG * 4 + g.carbsG * 4 + g.fatG * 9
            assertTrue("kcal mismatch for $p: $kcal vs ${g.calories}", kotlin.math.abs(kcal - g.calories) <= 10)
        }
    }

    @Test
    fun `applyTo writes goals into profile`() {
        val p = GoalCalculator.applyTo(male)
        assertEquals(2336, p.calorieGoal)
        assertEquals(120, p.proteinGoal)
        assertEquals(2450, p.waterGoalMl)
    }
}
