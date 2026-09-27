package com.fitcoach.app.data.local.db.seed

import com.fitcoach.app.data.local.db.entity.NutritionEntryEntity
import java.util.UUID

/**
 * База продуктов-шаблонов (mealType = TEMPLATE). Значения на 100 г, если не указано иное.
 * Расширяется агентом «Клуб/питание» (казахская и центральноазиатская кухня).
 */
object FoodSeed {
    private data class F(val name: String, val kcal: Int, val p: Float, val c: Float, val f: Float, val grams: Float = 100f)

    private val items = listOf(
        F("Куриная грудка варёная", 165, 31f, 0f, 3.6f),
        F("Гречка варёная", 92, 3.4f, 19.9f, 0.6f),
        F("Творог 5%", 121, 17f, 3f, 5f),
        F("Яйцо куриное", 78, 6f, 0.5f, 5.3f, 55f),
        F("Овсянка сухая", 367, 13f, 62f, 7f),
        F("Молоко 2.5%", 52, 2.8f, 4.7f, 2.5f),
        F("Рис варёный", 130, 2.7f, 28.2f, 0.3f),
        F("Хек запечённый", 86, 18f, 0f, 1.4f),
        F("Говядина тушёная", 193, 25f, 0f, 10f),
        F("Банан", 105, 1.3f, 27f, 0.4f, 120f),
        F("Греческий йогурт 2%", 59, 10f, 3.6f, 0.4f),
        F("Кефир 1%", 40, 3.4f, 4.7f, 1f),
        F("Картофель варёный", 83, 2f, 17f, 0.4f),
        F("Хлеб цельнозерновой", 63, 3f, 11f, 0.9f, 30f),
        F("Оливковое масло", 45, 0f, 0f, 5f, 5f)
    )

    fun templates(): List<NutritionEntryEntity> = items.map {
        NutritionEntryEntity(
            id = UUID.randomUUID().toString(),
            date = 0L,
            mealType = "TEMPLATE",
            name = it.name,
            calories = it.kcal,
            proteinG = it.p,
            carbsG = it.c,
            fatG = it.f,
            grams = it.grams
        )
    }
}
