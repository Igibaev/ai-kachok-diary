package com.fitcoach.app.chef

import com.fitcoach.app.ai.chef.DemoAiChefClient
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanPrefs
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.model.MealSlot
import com.fitcoach.app.domain.model.PlanGoals
import com.fitcoach.app.domain.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAiChefClientTest {

    private val client = DemoAiChefClient()
    private val kazakhLetters = setOf('ә', 'і', 'ң', 'ғ', 'ү', 'ұ', 'қ', 'ө', 'һ')
    private fun hasKazakh(s: String) = s.lowercase().any { it in kazakhLetters }

    private fun request(days: Int = 3, meals: Int = 4) =
        MealPlanRequest(days, meals, PlanGoals(1850, 130, 200, 60), UserProfile(), MealPlanPrefs())

    private fun assertValid(plan: MealPlan, days: Int, mealsPerDay: Int) {
        assertEquals(days, plan.days.size)
        assertEquals((1..days).toList(), plan.days.map { it.day })
        plan.days.forEach { day ->
            assertEquals(mealsPerDay, day.meals.size)
            assertTrue("калорийность дня ${day.day} = ${day.totalCalories}", day.totalCalories in 1300..2400)
            assertTrue("белок дня ${day.day} = ${day.totalProteinG}", day.totalProteinG >= 100f)
            day.meals.forEach { m ->
                assertTrue(m.title.isNotBlank())
                assertTrue(m.ingredients.isNotEmpty())
                assertTrue(m.ingredients.all { it.name.isNotBlank() && it.grams > 0f })
                assertTrue("шагов 2–4: ${m.title}", m.steps.size in 2..4)
                assertTrue("≤ 30 мин: ${m.title}", m.timeMinutes in 1..30)
                assertTrue(m.calories > 0)
            }
        }
        assertTrue(plan.shopping.isNotEmpty())
        assertTrue(plan.shopping.all { g -> g.items.all { it.name.isNotBlank() && it.quantity > 0f } })
        assertTrue(plan.notes.isNotBlank())
    }

    @Test
    fun `russian plan is valid and in Russian`() = runTest {
        val plan = client.generateMealPlan(request(), "ru").getOrThrow()
        assertValid(plan, 3, 4)
        val text = plan.days.flatMap { d -> d.meals.flatMap { listOf(it.title) + it.steps + it.ingredients.map { i -> i.name } } }.joinToString(" ")
        assertFalse("RU-план не должен содержать казахских букв", hasKazakh(text))
        assertTrue(text.contains("Бешбармак") && text.contains("Куырдак"))
    }

    @Test
    fun `kazakh plan is valid and in real Kazakh`() = runTest {
        val plan = client.generateMealPlan(request(), "kk").getOrThrow()
        assertValid(plan, 3, 4)
        plan.days.flatMap { it.meals }.forEach { m ->
            assertTrue("название на казахском: ${m.title}", hasKazakh(m.title))
            assertTrue("шаги на казахском: ${m.title}", m.steps.all { hasKazakh(it) })
        }
        assertTrue(hasKazakh(plan.notes))
        assertTrue(plan.shopping.flatMap { it.items }.any { hasKazakh(it.name) })
    }

    @Test
    fun `meals per day and days are respected by cycling the menu`() = runTest {
        val three = client.buildPlan(days = 7, mealsPerDay = 3, locale = "ru")
        assertValid(three, 7, 3)
        assertTrue(three.days.all { d -> d.meals.none { it.slot == MealSlot.SNACK } })
        val five = client.buildPlan(days = 5, mealsPerDay = 5, locale = "ru")
        assertValid(five, 5, 5)
        assertEquals(2, five.days.first().meals.count { it.slot == MealSlot.SNACK })
        assertEquals(three.days[0].meals.map { it.title }, three.days[3].meals.map { it.title })
    }

    @Test
    fun `shopping list is deduplicated across days`() = runTest {
        val plan = client.buildPlan(days = 3, mealsPerDay = 4, locale = "ru")
        val names = plan.shopping.flatMap { g -> g.items.map { it.name.lowercase() } }
        assertEquals("дубликатов нет", names.size, names.toSet().size)
        val beef = plan.shopping.flatMap { it.items }.first { it.name.startsWith("Говядина") }
        assertTrue("говядина суммируется по дням (150+150+120 г)", beef.quantity == 420f)
    }

    @Test
    fun `demo photo analysis is marked as demo on both locales`() = runTest {
        val ru = client.analyzeFoodPhoto(ByteArray(10), "image/jpeg", null, "ru").getOrThrow()
        assertTrue(ru.isDemo && ru.isFood)
        assertEquals(3, ru.items.size)
        assertEquals(506, ru.totalCalories)
        assertTrue(ru.note.startsWith("Демо-оценка"))
        val kk = client.analyzeFoodPhoto(ByteArray(10), "image/jpeg", "бешбармак", "kk").getOrThrow()
        assertTrue(kk.items.all { hasKazakh(it.name) })
        assertTrue(hasKazakh(kk.note))
    }
}
