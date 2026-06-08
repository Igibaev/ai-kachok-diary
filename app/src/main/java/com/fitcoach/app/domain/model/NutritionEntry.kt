package com.fitcoach.app.domain.model

data class NutritionEntry(
    val id: String,
    val date: Long,
    val mealType: MealType,
    val name: String,
    val calories: Int,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val grams: Float?
)

enum class MealType(val displayName: String) {
    BREAKFAST("Завтрак"),
    LUNCH("Обед"),
    SNACK("Перекус"),
    DINNER("Ужин"),
    TEMPLATE("Шаблон")
}

data class NutritionSummary(
    val calories: Int = 0,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val entriesByMeal: Map<MealType, List<NutritionEntry>> = emptyMap()
)
