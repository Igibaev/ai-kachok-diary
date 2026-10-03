package com.fitcoach.app.domain.model

/**
 * Доменные модели AI-повара: план питания, список покупок, разбор фото еды.
 * Контракт полей — proxy/README.md (`/v1/meal-plan`, `/v1/food-photo`).
 */

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK;

    /** Приём пищи дневника, в который по умолчанию добавляется блюдо плана. */
    fun toMealType(): MealType = when (this) {
        BREAKFAST -> MealType.BREAKFAST
        LUNCH -> MealType.LUNCH
        DINNER -> MealType.DINNER
        SNACK -> MealType.SNACK
    }

    companion object {
        fun fromWire(value: String): MealSlot = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SNACK
    }
}

/** Отдел магазина; порядок — порядок групп в списке покупок. */
enum class IngredientCategory(val wire: String) {
    MEAT_FISH("meat_fish"), DAIRY("dairy"), GRAINS("grains"), PRODUCE("produce"), OTHER("other");

    companion object {
        fun fromWire(value: String): IngredientCategory = entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: OTHER
    }
}

enum class ShoppingUnit(val wire: String) {
    G("g"), KG("kg"), PCS("pcs"), ML("ml"), L("l");

    companion object {
        fun fromWire(value: String): ShoppingUnit = entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: G
    }
}

data class Ingredient(
    val name: String,
    val grams: Float,
    val category: IngredientCategory = IngredientCategory.OTHER
)

data class PlanMeal(
    val slot: MealSlot,
    val title: String,
    val timeMinutes: Int,
    val ingredients: List<Ingredient>,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val steps: List<String>
) {
    val totalGrams: Float get() = ingredients.sumOf { it.grams.toDouble() }.toFloat()
}

data class PlanDay(
    val day: Int,
    val meals: List<PlanMeal>,
    val totalCalories: Int,
    val totalProteinG: Float,
    val totalCarbsG: Float,
    val totalFatG: Float
)

data class ShoppingGroup(
    val category: IngredientCategory,
    val items: List<ShoppingLine>
)

/** Позиция списка покупок в ответе сервера (без состояния «куплено»). */
data class ShoppingLine(
    val name: String,
    val quantity: Float,
    val unit: ShoppingUnit
)

data class MealPlan(
    val days: List<PlanDay>,
    val shopping: List<ShoppingGroup>,
    val notes: String = ""
) {
    /** Средняя калорийность дня по плану — для сравнения с целью профиля. */
    val averageCalories: Int get() = if (days.isEmpty()) 0 else days.sumOf { it.totalCalories } / days.size
}

/** Сохранённый план (Room `meal_plans`). */
data class SavedMealPlan(
    val id: String,
    val createdAt: Long,
    val days: Int,
    val mealsPerDay: Int,
    val prefs: MealPlanPrefs,
    val plan: MealPlan,
    val goals: PlanGoals,
    val isActive: Boolean,
    val isDemo: Boolean = false
)

/** Позиция списка покупок с состоянием (Room `shopping_items`). */
data class ShoppingItem(
    val id: String,
    val planId: String,
    val category: IngredientCategory,
    val name: String,
    val quantity: Float,
    val unit: ShoppingUnit,
    val checked: Boolean,
    val sortOrder: Int
)

// ---------------- запрос плана ----------------

enum class Cuisine(val wire: String) { KAZAKH("kazakh"), HOME("home"), ANY("any") }
enum class Budget(val wire: String) { LOW("low"), MID("mid"), ANY("any") }

data class MealPlanPrefs(
    val cuisine: Cuisine = Cuisine.KAZAKH,
    val exclusions: List<String> = emptyList(),
    val halal: Boolean = true,
    val budget: Budget = Budget.MID,
    val batchCooking: Boolean = false
)

data class PlanGoals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
) {
    companion object {
        fun from(profile: UserProfile) = PlanGoals(profile.calorieGoal, profile.proteinGoal, profile.carbsGoal, profile.fatGoal)
    }
}

data class MealPlanRequest(
    val days: Int,
    val mealsPerDay: Int,
    val goals: PlanGoals,
    val profile: UserProfile,
    val prefs: MealPlanPrefs
)

// ---------------- фото еды ----------------

enum class Confidence { LOW, MEDIUM, HIGH;

    companion object {
        fun fromWire(value: String): Confidence = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

data class FoodItem(
    val name: String,
    val grams: Float,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val confidence: Confidence
)

data class FoodAnalysis(
    val items: List<FoodItem>,
    val note: String,
    val isFood: Boolean,
    val isDemo: Boolean = false
) {
    val totalCalories: Int get() = items.sumOf { it.calories }
    val totalProteinG: Float get() = items.sumOf { it.proteinG.toDouble() }.toFloat()
    val totalCarbsG: Float get() = items.sumOf { it.carbsG.toDouble() }.toFloat()
    val totalFatG: Float get() = items.sumOf { it.fatG.toDouble() }.toFloat()
}

/** Настройки напоминания «пора за покупками» (хранятся в prefs). */
data class ShoppingReminder(
    val enabled: Boolean = false,
    /** 1 = понедельник … 7 = воскресенье (ISO). */
    val dayOfWeek: Int = 6,
    val hour: Int = 18,
    val minute: Int = 0
)
