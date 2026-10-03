package com.fitcoach.app.ai.chef

import com.fitcoach.app.domain.model.Confidence
import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.domain.model.FoodItem
import com.fitcoach.app.domain.model.Ingredient
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.model.MealSlot
import com.fitcoach.app.domain.model.PlanDay
import com.fitcoach.app.domain.model.PlanMeal
import com.fitcoach.app.domain.model.ShoppingGroup
import com.fitcoach.app.domain.model.ShoppingLine
import com.fitcoach.app.domain.model.ShoppingUnit
import com.fitcoach.app.domain.usecase.chef.FoodMath
import kotlinx.serialization.Serializable

/*
 * DTO прокси AI-повара (POST /v1/meal-plan, POST /v1/food-photo). Json из AppModule уже с ignoreUnknownKeys,
 * все поля ответа — со значениями по умолчанию, чтобы новая версия прокси не ломала старое приложение.
 * MealPlanDto также используется как формат хранения планa в Room (`meal_plans.planJson`).
 */

// ---------------- запрос плана ----------------

@Serializable
data class MealPlanRequestDto(
    val locale: String,
    val days: Int,
    val mealsPerDay: Int,
    val goals: GoalsDto,
    val profile: ProfileDto,
    val prefs: PrefsDto,
    val deviceId: String
)

@Serializable
data class GoalsDto(val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

@Serializable
data class ProfileDto(val sex: String, val age: Int, val weightKg: Float, val goal: String, val restrictions: List<String>)

@Serializable
data class PrefsDto(
    val cuisine: String,
    val exclusions: List<String>,
    val halal: Boolean,
    val budget: String,
    val batchCooking: Boolean
)

fun MealPlanRequest.toDto(locale: String, deviceId: String) = MealPlanRequestDto(
    locale = locale,
    days = days,
    mealsPerDay = mealsPerDay,
    goals = GoalsDto(goals.calories, goals.proteinG, goals.carbsG, goals.fatG),
    profile = ProfileDto(
        sex = profile.sex.name.lowercase(),
        age = profile.age,
        weightKg = profile.weightKg,
        goal = profile.goal.name.lowercase(),
        restrictions = profile.restrictions.map { it.name.lowercase() }
    ),
    prefs = PrefsDto(prefs.cuisine.wire, prefs.exclusions, prefs.halal, prefs.budget.wire, prefs.batchCooking),
    deviceId = deviceId
)

// ---------------- ответ плана ----------------

@Serializable
data class MealPlanResponseDto(
    val plan: MealPlanDto? = null,
    val model: String = "",
    val error: String? = null,
    val text: String? = null
)

@Serializable
data class MealPlanDto(
    val days: List<PlanDayDto> = emptyList(),
    val shopping: List<ShoppingGroupDto> = emptyList(),
    val notes: String = ""
)

@Serializable
data class PlanDayDto(
    val day: Int = 0,
    val meals: List<PlanMealDto> = emptyList(),
    val totalCalories: Int = 0,
    val totalProteinG: Float = 0f,
    val totalCarbsG: Float = 0f,
    val totalFatG: Float = 0f
)

@Serializable
data class PlanMealDto(
    val slot: String = "snack",
    val title: String = "",
    val timeMinutes: Int = 0,
    val ingredients: List<IngredientDto> = emptyList(),
    val calories: Int = 0,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val steps: List<String> = emptyList()
)

@Serializable
data class IngredientDto(val name: String = "", val grams: Float = 0f, val category: String = "other")

@Serializable
data class ShoppingGroupDto(val category: String = "other", val items: List<ShoppingLineDto> = emptyList())

@Serializable
data class ShoppingLineDto(val name: String = "", val quantity: Float = 0f, val unit: String = "g")

fun MealPlanDto.toDomain(): MealPlan {
    val days = days.filter { it.meals.isNotEmpty() }.mapIndexed { index, d ->
        val meals = d.meals.map { m ->
            PlanMeal(
                slot = MealSlot.fromWire(m.slot),
                title = m.title.trim(),
                timeMinutes = m.timeMinutes,
                ingredients = m.ingredients.filter { it.name.isNotBlank() }
                    .map { Ingredient(it.name.trim(), it.grams, IngredientCategory.fromWire(it.category)) },
                calories = m.calories,
                proteinG = m.proteinG,
                carbsG = m.carbsG,
                fatG = m.fatG,
                steps = m.steps.filter { it.isNotBlank() }
            )
        }
        PlanDay(
            day = if (d.day > 0) d.day else index + 1,
            meals = meals,
            totalCalories = if (d.totalCalories > 0) d.totalCalories else meals.sumOf { it.calories },
            totalProteinG = if (d.totalProteinG > 0f) d.totalProteinG else meals.sumOf { it.proteinG.toDouble() }.toFloat(),
            totalCarbsG = if (d.totalCarbsG > 0f) d.totalCarbsG else meals.sumOf { it.carbsG.toDouble() }.toFloat(),
            totalFatG = if (d.totalFatG > 0f) d.totalFatG else meals.sumOf { it.fatG.toDouble() }.toFloat()
        )
    }
    val shoppingFromServer = shopping.mapNotNull { g ->
        val items = g.items.filter { it.name.isNotBlank() && it.quantity > 0f }
            .map { ShoppingLine(it.name.trim(), it.quantity, ShoppingUnit.fromWire(it.unit)) }
        if (items.isEmpty()) null else ShoppingGroup(IngredientCategory.fromWire(g.category), items)
    }
    val plan = MealPlan(days = days, shopping = shoppingFromServer, notes = notes.trim())
    // Сервер мог не прислать список покупок — собираем сами из ингредиентов.
    return if (shoppingFromServer.isEmpty()) plan.copy(shopping = FoodMath.buildShopping(plan)) else plan
}

fun MealPlan.toDto() = MealPlanDto(
    days = days.map { d ->
        PlanDayDto(
            day = d.day,
            meals = d.meals.map { m ->
                PlanMealDto(
                    slot = m.slot.name.lowercase(), title = m.title, timeMinutes = m.timeMinutes,
                    ingredients = m.ingredients.map { IngredientDto(it.name, it.grams, it.category.wire) },
                    calories = m.calories, proteinG = m.proteinG, carbsG = m.carbsG, fatG = m.fatG, steps = m.steps
                )
            },
            totalCalories = d.totalCalories, totalProteinG = d.totalProteinG, totalCarbsG = d.totalCarbsG, totalFatG = d.totalFatG
        )
    },
    shopping = shopping.map { g -> ShoppingGroupDto(g.category.wire, g.items.map { ShoppingLineDto(it.name, it.quantity, it.unit.wire) }) },
    notes = notes
)

// ---------------- фото еды ----------------

@Serializable
data class FoodPhotoRequestDto(
    val locale: String,
    val imageBase64: String,
    val mediaType: String,
    val hint: String? = null,
    val deviceId: String
)

@Serializable
data class FoodPhotoResponseDto(
    val analysis: FoodAnalysisDto? = null,
    val model: String = "",
    val error: String? = null,
    val text: String? = null
)

@Serializable
data class FoodAnalysisDto(
    val items: List<FoodItemDto> = emptyList(),
    val totalCalories: Int = 0,
    val totalProteinG: Float = 0f,
    val totalCarbsG: Float = 0f,
    val totalFatG: Float = 0f,
    val note: String = "",
    val isFood: Boolean = true
)

@Serializable
data class FoodItemDto(
    val name: String = "",
    val grams: Float = 0f,
    val calories: Int = 0,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val confidence: String = "medium"
)

fun FoodAnalysisDto.toDomain(isDemo: Boolean = false) = FoodAnalysis(
    items = items.filter { it.name.isNotBlank() }.map {
        FoodItem(it.name.trim(), it.grams.coerceAtLeast(0f), it.calories.coerceAtLeast(0), it.proteinG, it.carbsG, it.fatG, Confidence.fromWire(it.confidence))
    },
    note = note.trim(),
    isFood = isFood,
    isDemo = isDemo
)
