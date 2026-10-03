package com.fitcoach.app.chef

import com.fitcoach.app.domain.model.Confidence
import com.fitcoach.app.domain.model.FoodItem
import com.fitcoach.app.domain.model.Ingredient
import com.fitcoach.app.domain.model.IngredientCategory
import com.fitcoach.app.domain.model.ShoppingUnit
import com.fitcoach.app.domain.usecase.chef.FoodMath
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodMathTest {

    private val chicken = FoodItem("Куриная грудка", 150f, 248, 46.5f, 0f, 5.4f, Confidence.HIGH)

    @Test
    fun `rescale is proportional and rounds macros to one decimal`() {
        val doubled = FoodMath.rescale(chicken, 300f)
        assertEquals(300f, doubled.grams)
        assertEquals(496, doubled.calories)
        assertEquals(93f, doubled.proteinG)
        assertEquals(10.8f, doubled.fatG, 0.001f)

        val third = FoodMath.rescale(chicken, 50f)
        assertEquals(83, third.calories)
        assertEquals(15.5f, third.proteinG, 0.001f)
    }

    @Test
    fun `rescale handles zero and negative grams without infinities`() {
        val zero = FoodMath.rescale(chicken, 0f)
        assertEquals(0f, zero.grams)
        assertEquals(0, zero.calories)
        val negative = FoodMath.rescale(chicken, -20f)
        assertEquals(0f, negative.grams)

        val zeroBase = FoodMath.rescale(chicken.copy(grams = 0f), 100f)
        assertEquals("нулевая база — граммы меняем, КБЖУ не трогаем", 248, zeroBase.calories)
        assertEquals(100f, zeroBase.grams)
    }

    @Test
    fun `aggregate merges duplicates case-insensitively and groups by category in fixed order`() {
        val groups = FoodMath.aggregate(
            listOf(
                Ingredient("Лук репчатый", 40f, IngredientCategory.PRODUCE),
                Ingredient("Говядина", 150f, IngredientCategory.MEAT_FISH),
                Ingredient("лук репчатый ", 60f, IngredientCategory.PRODUCE),
                Ingredient("Говядина", 150f, IngredientCategory.MEAT_FISH),
                Ingredient("Мёд", 10f, IngredientCategory.OTHER),
                Ingredient("", 100f, IngredientCategory.OTHER)
            )
        )
        assertEquals(listOf(IngredientCategory.MEAT_FISH, IngredientCategory.PRODUCE, IngredientCategory.OTHER), groups.map { it.category })
        val beef = groups[0].items.single()
        assertEquals(300f, beef.quantity)
        assertEquals(ShoppingUnit.G, beef.unit)
        val onion = groups[1].items.single()
        assertEquals("Лук репчатый", onion.name)
        assertEquals(100f, onion.quantity)
        assertEquals(1, groups[2].items.size)
    }

    @Test
    fun `grams above 1000 become kilograms rounded to 0_1`() {
        assertEquals(ShoppingUnit.KG, FoodMath.toLine("Картофель", 1000f).unit)
        assertEquals(1f, FoodMath.toLine("Картофель", 1000f).quantity)
        assertEquals(1.3f, FoodMath.toLine("Картофель", 1250f).quantity, 0.001f)
        assertEquals(1.2f, FoodMath.toLine("Картофель", 1249f).quantity, 0.001f)
        val grams = FoodMath.toLine("Соль", 999.6f)
        assertEquals(ShoppingUnit.G, grams.unit)
        assertEquals(1000f, grams.quantity)
    }

    @Test
    fun `formatQuantity prints integers plainly and decimals with comma`() {
        assertEquals("250 г", FoodMath.formatQuantity(250f, "г"))
        assertEquals("1,2 кг", FoodMath.formatQuantity(1.2f, "кг"))
        assertEquals("1 кг", FoodMath.formatQuantity(1f, "кг"))
    }
}
