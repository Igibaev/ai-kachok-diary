package com.fitcoach.app.domain.usecase.chef

import com.fitcoach.app.domain.model.FoodItem
import com.fitcoach.app.domain.model.Ingredient
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.ShoppingGroup
import com.fitcoach.app.domain.model.ShoppingLine
import com.fitcoach.app.domain.model.ShoppingUnit
import kotlin.math.roundToInt

/** Чистые расчёты AI-повара: перерасчёт КБЖУ при правке граммов и сборка списка покупок из плана. */
object FoodMath {

    /** Пропорциональный перерасчёт КБЖУ позиции под новые граммы. Нулевые/отрицательные граммы не допускаются. */
    fun rescale(item: FoodItem, newGrams: Float): FoodItem {
        val base = item.grams.takeIf { it > 0f } ?: return item.copy(grams = newGrams.coerceAtLeast(0f))
        val grams = newGrams.coerceAtLeast(0f)
        val k = grams / base
        return item.copy(
            grams = grams,
            calories = (item.calories * k).roundToInt(),
            proteinG = round1(item.proteinG * k),
            carbsG = round1(item.carbsG * k),
            fatG = round1(item.fatG * k)
        )
    }

    /**
     * Список покупок из ингредиентов плана: группировка по отделу, объединение одинаковых названий
     * без учёта регистра и пробелов, граммы > 1000 → кг с округлением до 0,1.
     * Используется демо-клиентом и как запасной вариант, если сервер не прислал `shopping`.
     */
    fun buildShopping(plan: MealPlan): List<ShoppingGroup> =
        aggregate(plan.days.flatMap { d -> d.meals.flatMap { it.ingredients } })

    fun aggregate(ingredients: List<Ingredient>): List<ShoppingGroup> {
        val byCategory = linkedMapOf<IngredientCategory, LinkedHashMap<String, Pair<String, Float>>>()
        ingredients.forEach { ing ->
            val key = ing.name.trim().lowercase()
            if (key.isEmpty()) return@forEach
            val bucket = byCategory.getOrPut(ing.category) { linkedMapOf() }
            val prev = bucket[key]
            bucket[key] = (prev?.first ?: ing.name.trim()) to ((prev?.second ?: 0f) + ing.grams.coerceAtLeast(0f))
        }
        return IngredientCategory.entries.mapNotNull { cat ->
            val bucket = byCategory[cat] ?: return@mapNotNull null
            ShoppingGroup(cat, bucket.values.map { (name, grams) -> toLine(name, grams) })
        }
    }

    /** Граммы → строка списка: ≥ 1000 г → кг с шагом 0,1; иначе целые граммы (минимум 1 г, если что-то было). */
    fun toLine(name: String, grams: Float): ShoppingLine = when {
        grams >= 1000f -> ShoppingLine(name, round1(grams / 1000f), ShoppingUnit.KG)
        else -> ShoppingLine(name, grams.roundToInt().coerceAtLeast(if (grams > 0f) 1 else 0).toFloat(), ShoppingUnit.G)
    }

    /** Человекочитаемое количество: «1,2 кг», «250 г», «3 шт». */
    fun formatQuantity(quantity: Float, unitLabel: String): String {
        val q = if (quantity == quantity.roundToInt().toFloat()) quantity.roundToInt().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", quantity).replace('.', ',')
        return "$q $unitLabel"
    }

    fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f
}
