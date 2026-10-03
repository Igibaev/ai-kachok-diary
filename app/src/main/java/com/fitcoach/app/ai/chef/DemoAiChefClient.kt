package com.fitcoach.app.ai.chef

import com.fitcoach.app.domain.model.Confidence
import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.domain.model.FoodItem
import com.fitcoach.app.domain.model.Ingredient
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.model.MealSlot
import com.fitcoach.app.domain.model.PlanDay
import com.fitcoach.app.domain.model.PlanMeal
import com.fitcoach.app.domain.usecase.chef.FoodMath
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Офлайн-повар для презентаций и работы без прокси/интернета: готовое меню с казахской кухней
 * (3 дня, при запросе 5/7 дней — меню повторяется по кругу) и фиксированный «разбор фото» с пометкой «демо-оценка».
 * Небольшая задержка имитирует запрос, чтобы индикатор прогресса был виден.
 */
@Singleton
class DemoAiChefClient @Inject constructor() : AiChefClient {

    override suspend fun generateMealPlan(req: MealPlanRequest, locale: String): Result<MealPlan> {
        delay(DELAY_MS)
        return Result.success(buildPlan(req.days, req.mealsPerDay, locale))
    }

    override suspend fun analyzeFoodPhoto(jpeg: ByteArray, mediaType: String, hint: String?, locale: String): Result<FoodAnalysis> {
        delay(DELAY_MS)
        return Result.success(demoAnalysis(locale))
    }

    fun buildPlan(days: Int, mealsPerDay: Int, locale: String): MealPlan {
        val kk = isKazakh(locale)
        val count = days.coerceIn(1, 7)
        val planDays = (1..count).map { dayNumber ->
            val menu = DemoChefMenu.days[(dayNumber - 1) % DemoChefMenu.days.size]
            // 3 приёма — без перекуса; 4 — как в меню; 5 — перекус дублируется вторым (айран/фрукт удобно повторить).
            val meals = when {
                mealsPerDay <= 3 -> menu.filter { it.slot != MealSlot.SNACK }
                mealsPerDay >= 5 -> menu + menu.filter { it.slot == MealSlot.SNACK }
                else -> menu
            }.map { m ->
                PlanMeal(
                    slot = m.slot,
                    title = m.title.of(kk),
                    timeMinutes = m.timeMinutes,
                    ingredients = m.ingredients.map { Ingredient(it.name.of(kk), it.grams, it.category) },
                    calories = m.calories,
                    proteinG = m.proteinG,
                    carbsG = m.carbsG,
                    fatG = m.fatG,
                    steps = m.steps.map { it.of(kk) }
                )
            }
            PlanDay(
                day = dayNumber,
                meals = meals,
                totalCalories = meals.sumOf { it.calories },
                totalProteinG = meals.sumOf { it.proteinG.toDouble() }.toFloat(),
                totalCarbsG = meals.sumOf { it.carbsG.toDouble() }.toFloat(),
                totalFatG = meals.sumOf { it.fatG.toDouble() }.toFloat()
            )
        }
        val plan = MealPlan(days = planDays, shopping = emptyList(), notes = DemoChefMenu.notes.of(kk))
        return plan.copy(shopping = FoodMath.buildShopping(plan))
    }

    fun demoAnalysis(locale: String): FoodAnalysis {
        val kk = isKazakh(locale)
        return FoodAnalysis(
            items = listOf(
                FoodItem(if (kk) "Тауық төсі (пешке пісірілген)" else "Куриная грудка (запечённая)", 150f, 248, 46.5f, 0f, 5.4f, Confidence.HIGH),
                FoodItem(if (kk) "Пісірілген қарақұмық" else "Гречка отварная", 180f, 198, 7.6f, 36f, 1.4f, Confidence.MEDIUM),
                FoodItem(if (kk) "Жас көкөніс салаты" else "Салат из свежих овощей", 120f, 60, 1.2f, 5f, 3.5f, Confidence.MEDIUM)
            ),
            note = if (kk) "Демо-бағалау: фотодағы нақты тағамдарды танып білу үшін клубтың AI-қызметін қосыңыз."
            else "Демо-оценка: подключите AI-сервис клуба, чтобы распознавать реальные блюда на фото.",
            isFood = true,
            isDemo = true
        )
    }

    private fun isKazakh(locale: String) = locale.startsWith("kk", ignoreCase = true)

    private companion object {
        const val DELAY_MS = 1_500L
    }
}
